package org.gokb

import grails.converters.*
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.access.annotation.Secured;
import com.k_int.apis.SecurityApi
import org.springframework.security.acls.model.Permission

import grails.util.GrailsClassUtils
import org.gokb.cred.*

class SearchController {

  def genericOIDService
  def springSecurityService
  def classExaminationService
  def gokbAclService
  def displayTemplateService


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    User user = springSecurityService.currentUser
    def start_time = System.currentTimeMillis();

    log.debug("Entering SearchController:index ${params}");

    def result = [:]

    if ( params.init ) {
      result.init = true
    }

    def cleaned_params = params.findAll { it.value && it.value != "" }

    log.debug("Cleaned: ${cleaned_params}");

    if ( params.refOid && !params.refOid.endsWith('null')) {
      result.refOid = params.refOid

      result.refName = KBComponent.get(Long.valueOf(params.refOid.split(':')[1])).name
    }

    result.max = params.max ? Integer.parseInt(params.max) : ( user.defaultPageSize ?: 10 );
    result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

    if( params.inline && !params.max) {
      result.max = 10
    }

    if ( params.jumpToPage ) {
      result.offset = ( ( Integer.parseInt(params.jumpToPage) - 1 ) * result.max )
    }
    params.remove('jumpToPage')
    params.offset = result.offset
    result.hide = params.list("hide") ?: []

    if ( params.searchAction == 'save' ) {
      log.debug("Saving query... ${params.searchName}");
      def defn = [:] << params
      defn.remove('searchAction')

      try {
        log.debug("Saving..");
        def saved_search = new SavedSearch(name:params.searchName,owner:user,searchDescriptor:(defn as JSON).toString())
        saved_search.save(flush:true, failOnError:true)
        log.debug("Saved.. ${saved_search.id}");
      }
      catch ( Exception e ) {
        log.error("Problem",e);
      }
    }

    if ( params.det )
      result.det = Integer.parseInt(params.det)

    if ( params.qbe) {
      if ( params.qbe.startsWith('g:') ) {
        // Global template, look in config
        def global_qbe_template_shortcode = params.qbe.substring(2,params.qbe.length());
        // log.debug("Looking up global template ${global_qbe_template_shortcode}");
        result.qbetemplate = grailsApplication.config.globalSearchTemplates[global_qbe_template_shortcode]
        // log.debug("Using template: ${result.qbetemplate}");
      }

      // Looked up a template from somewhere, see if we can execute a search
      if ( result.qbetemplate) {
      
        Class target_class = Class.forName(result.qbetemplate.baseclass);
        def read_perm = target_class.isTypeReadable()
        
        if (read_perm && !params.init) {
        
          log.debug("Execute query");
          doQuery(result.qbetemplate, cleaned_params, result)
          log.debug("Query complete");
          result.lasthit = result.offset + result.max > result.reccount ? result.reccount : ( result.offset + result.max )
          
          // Add the page information.
          result.page_current = (result.offset / result.max) + 1
          result.page_total = (result.reccount / result.max).toInteger() + (result.reccount % result.max > 0 ? 1 : 0)
          
        }else if (!read_perm){
          response.sendError(403);
        }
      }
      else {
        log.error("no template ${result?.qbetemplate}");
      }

      
      if ( result.det && result.recset ) {

        log.debug("Got details page");

        int recno = result.det - result.offset - 1

        if ( result.recset.size() >= recno) {

          if ( recno >= result.recset.size() ) {
            recno = result.recset.size() - 1;
            result.det = result.reccount;
          }
          else if ( recno < 0 ) {
            recno = 0;
            result.det = 0;
          }
  
          // log.debug("Trying to display record ${recno}");

          result.displayobj = result.recset.get(recno)
          
          def display_start_time = System.currentTimeMillis();
          if ( result.displayobj != null ) {

            if ( result.displayobj.class.name == "org.gokb.cred.ComponentWatch"  && result.displayobj.component?.id ) {
              result.displayobj = KBComponent.get(result.displayobj.component?.id)
            }
  
            result.displayobjclassname = result.displayobj.class.name
            result.displaytemplate = displayTemplateService.getTemplateInfo(result.displayobjclassname)
            result.__oid = "${result.displayobjclassname}:${result.displayobj.id}"
      
            // Add any refdata property names for this class to the result.
            result.refdata_properties = classExaminationService.getRefdataPropertyNames(result.displayobjclassname)
            result.displayobjclassname_short = result.displayobj.class.simpleName
            result.isComponent = (result.displayobj instanceof KBComponent)
            
            result.acl = gokbAclService.readAclSilently(result.displayobj)
        
            if ( result.displaytemplate == null ) {
              log.error("Unable to locate display template for class ${result.displayobjclassname} (oid ${params.displayoid})");
            }
            else {
              // log.debug("Got display template ${result.displaytemplate} for rec ${result.det} - class is ${result.displayobjclassname}");
            }
          }
          else { 
            log.error("Result row for display was NULL");
          }
          log.debug("Display completed after ${System.currentTimeMillis() - display_start_time}");
        }
        else {
          log.error("Record display request out of range");
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
    } else {
      result.new_recset = []
      log.debug("Create new recset..")
      result.recset.each { r ->
        def response_record = [:]
        response_record.oid = "${r.class.name}:${r.id}"
        response_record.obj = r
        response_record.cols = []
        result.qbetemplate.qbeConfig.qbeResults.each { rh ->
          def ppath = rh.property.split(/\./)
          def cobj = r
          def final_oid = "${cobj.class.name}:${cobj.id}"

          if (!params.hide || !params.hide.contains(rh.qpEquiv)) {

            ppath.eachWithIndex { prop, idx ->
              def sp = prop.minus('?')

              if( cobj?.class?.name == 'org.gokb.cred.RefdataValue' ) {
                cobj = cobj.value
              }
              else {
                if ( cobj && KBComponent.has(cobj, sp)) {
                  cobj = cobj[sp]

                  if (ppath.size() > 1 && idx == ppath.size()-2) {
                    if (cobj && sp != 'class') {
                      final_oid = "${cobj.class.name}:${cobj.id}"
                    }
                    else {
                      final_oid = null
                    }
                  }
                }
                else {
                  cobj = null
                }
              }
            }
            response_record.cols.add([link: (rh.link ? (final_oid ?: response_record.oid ) : null), value: (cobj ?: '-Empty-')])
          }
        }
        result.new_recset.add(response_record)
      }
      log.debug("Finished new recset!")
    }

    result.withoutJump = cleaned_params
    result.remove('jumpToPage');
    result.withoutJump.remove('jumpToPage');

    // log.debug("leaving SearchController::index...");
    log.debug("Search completed after ${System.currentTimeMillis() - start_time}");

    withFormat {
      html result
      json { render apiresponse as JSON }
      xml { render apiresponse as XML }
    }
  }

  def doQuery (qbetemplate, params, result) {
    def target_class = grailsApplication.getArtefact("Domain",qbetemplate.baseclass);
    com.k_int.HQLBuilder.build(grailsApplication, qbetemplate, params, result, target_class, genericOIDService)
  }

}
