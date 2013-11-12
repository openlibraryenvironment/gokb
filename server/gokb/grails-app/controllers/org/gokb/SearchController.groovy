package org.gokb

import grails.converters.*
import grails.plugins.springsecurity.Secured

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*

class SearchController {

  def genericOIDService
  def springSecurityService
  def classExaminationService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    User user = springSecurityService.currentUser

    log.debug("enter SearchController::index...");
    def result = [:]

    result.max = params.max ? Integer.parseInt(params.max) : ( user.defaultPageSize ?: 10 );
    result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

    if ( params.det )
      result.det = Integer.parseInt(params.det)

    if ( params.qbe ) {
      if ( params.qbe.startsWith('g:') ) {
        // Global template, look in config
        def global_qbe_template_shortcode = params.qbe.substring(2,params.qbe.length());
        log.debug("Looking up global template ${global_qbe_template_shortcode}");
        result.qbetemplate = grailsApplication.config.globalSearchTemplates[global_qbe_template_shortcode]
        log.debug("Using template: ${result.qbetemplate}");
      }

      // Looked up a template from somewhere, see if we can execute a search
      if ( result.qbetemplate ) {
        log.debug("Execute query");
        doQuery(result.qbetemplate, params, result)
        result.lasthit = result.offset + result.max > result.reccount ? result.reccount : ( result.offset + result.max )
      }
      else {
        log.debug("no template");
      }

      if ( result.det && result.recset ) {
        log.debug("Trying to display record - config is ${grailsApplication.config.globalDisplayTemplates}");
        int recno = result.det - result.offset - 1
        if ( ( recno < 0 ) || ( recno > result.max ) ) {
          recno = 0;
        }
        result.displayobj = result.recset.get(recno)
        result.displayobjclassname = result.displayobj.class.name
        result.displaytemplate = grailsApplication.config.globalDisplayTemplates[result.displayobjclassname]
        result.__oid = "${result.displayobjclassname}:${result.displayobj.id}"
    
  // Add any refdata property names for this class to the result.
  result.refdata_properties = classExaminationService.getRefdataPropertyNames(result.displayobjclassname)
  result.displayobjclassname_short = result.displayobj.class.simpleName
  result.isComponent = (result.displayobj instanceof KBComponent)
    
        if ( result.displaytemplate == null ) {
          log.error("Unable to locate display template for class ${result.displayobjclassname} (oid ${params.displayoid})");
        }
        else {
          log.debug("Got display template ${result.displaytemplate} for rec ${result.det} - class is ${result.displayobjclassname}");
        }
      }
    }

    def apiresponse = null
    if ( ( response.format == 'json' ) || ( response.format == 'xml' ) ) {
      apiresponse = [:]
      apiresponse.count = result.reccount
      apiresponse.max = result.max
      apiresponse.offset = result.offset
      apiresponse.records = []
      result.recset.each { r ->
        def response_record = [:]
        response_record.oid = "${r.class.name}:${r.id}"
        result.qbetemplate.qbeConfig.qbeResults.each { rh ->
          response_record[rh.heading] = groovy.util.Eval.x(r, 'x.' + rh.property)
        }
        apiresponse.records.add(response_record);
      }
    }

    log.debug("leaving SearchController::index...");

    withFormat {
      html result
      json { render apiresponse as JSON }
      xml { render apiresponse as XML }
    }

  }


  def doQuery(qbetemplate, params, result) {
    def target_class = grailsApplication.getArtefact("Domain",qbetemplate.baseclass);

    log.debug("Iterate over form components: ${qbetemplate.qbeConfig.qbeForm}");
    def c = ComboCriteria.createFor(target_class.getClazz().createCriteria())

    def count_result = c.get {
      and {
        // Add any global 
        qbetemplate.qbeConfig.qbeGlobals?.each { ap ->
          processContextTree(c, ap, ap.value, ap)
        }
    
        // Each form element needs to be acted upon.
        qbetemplate.qbeConfig.qbeForm.each { ap ->
          if ( ( params[ap.qparam] != null ) && ( params[ap.qparam].length() > 0 ) ) {
            processContextTree(c, ap.contextTree, params[ap.qparam], ap)
          }
        }
      }
      projections {
        rowCount()
      }
    }
    result.reccount = count_result;
    log.debug("criteria result: ${count_result}");

    c = ComboCriteria.createFor(target_class.getClazz().createCriteria())


    result.recset = c.list(max: result.max, offset: result.offset) {
      and {
    
        // Add any global.
        qbetemplate.qbeConfig.qbeGlobals?.each { ap ->
          processContextTree(c, ap, ap.value, ap)
        }
    
        // Form elements.
        qbetemplate.qbeConfig.qbeForm.each { ap ->
          log.debug("testing ${ap} : ${params[ap.qparam]}");
          if ( ( params[ap.qparam] != null ) && ( params[ap.qparam].length() > 0 ) ) {
            // addParamInContext(owner,ap,params[ap.qparam],ap.contextTree)
            processContextTree(c, ap.contextTree, params[ap.qparam], ap)
          }
        }
      }
      if ( params.sort ) {
        order(params.sort,params.order)
      }
    }
    // Look at create alias as a means of supporting sorting within a scope
    // http://stackoverflow.com/questions/3594301/sorting-query-results-according-to-parent-property-in-grails
  }

  private def  processContextTree = { qry, tree, value, paramdef, Class the_class = null ->
    if ( tree ) {
      
      // Turn it into a list.
      if (!(tree instanceof Iterable)) {
        tree = [tree]
      } 
      
      def the_value = value
      if ( paramdef.type == 'lookup' ) {
        log.debug("Processing a lookup.. value from form was ${value}");
        the_value = genericOIDService.resolveOID2(value)
      }
    
      // Each item in the tree.
      tree.each { contextTree ->
  
          switch ( contextTree.ctxtp ) {
            case 'filter':
    
              // Filters work in the same way as queries,
              // but the value is in the contextTree instead of the submitted value.
              the_value = contextTree.value
        
            case 'qry':
              // Use our custom criteria builder to compare the values.
              if (contextTree.type) {
                // Try and parse the number.
                the_value = the_value.asType(Class.forName("${contextTree.type}"));
              }
        
              // Check the negation.
              if (contextTree.negate) {
                qry."not" {
                  qry.add(contextTree.prop, contextTree.comparator, the_value)
                }
              } else {
                 log.debug("Adding ${contextTree.prop} ${contextTree.comparator} ${the_value}");
                 qry.add(contextTree.prop, contextTree.comparator, the_value)
              }
              
              break;
          }
      }
    }
  }
}
