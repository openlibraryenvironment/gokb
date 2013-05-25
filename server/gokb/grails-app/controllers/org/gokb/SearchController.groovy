package org.gokb

import grails.converters.*
import grails.plugins.springsecurity.Secured

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*

class SearchController {

  def genericOIDService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    log.debug("enter SearchController::index...");
    def result = [:]

    result.max = params.max ? Integer.parseInt(params.max) : 10;
    result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

    if ( params.det )
      result.det = Integer.parseInt(params.det)

    if ( params.qbe ) {
      if ( params.qbe.startsWith('g:') ) {
        // Global template, look in config
        def global_qbe_template_shortcode = params.qbe.substring(2,params.qbe.length());
        log.debug("Looking up global template ${global_qbe_template_shortcode}");
        result.qbetemplate = globalSearchTemplates[global_qbe_template_shortcode]
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

      if ( params.displayoid ) {
        log.debug("Attempt to retrieve ${params.displayoid} and find a global template for it");
        result.displayobj = genericOIDService.resolveOID(params.displayoid)
        if ( result.displayobj ) {
          result.displayobjclassname = result.displayobj.class.name
          result.displaytemplate = globalDisplayTemplates[result.displayobjclassname]
        }
      }

      if ( result.det && result.recset ) {
        int recno = result.det - result.offset - 1
        if ( ( recno < 0 ) || ( recno > result.max ) ) {
          recno = 0;
        }
        result.displayobj = result.recset.get(recno)
        result.displayobjclassname = result.displayobj.class.name
        result.displaytemplate = globalDisplayTemplates[result.displayobjclassname]
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
		  processContextTree(c, ap, ap.value, ap.property)
		}
		
		// Each form element needs to be acted upon.
        qbetemplate.qbeConfig.qbeForm.each { ap ->
          if ( ( params[ap.qparam] != null ) && ( params[ap.qparam].length() > 0 ) ) {
            processContextTree(c, ap.contextTree, params[ap.qparam], ap.property)
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
		  processContextTree(c, ap, ap.value, ap.property)
		}
		
		// Form elements.
        qbetemplate.qbeConfig.qbeForm.each { ap ->
          log.debug("testing ${ap} : ${params[ap.qparam]}");
          if ( ( params[ap.qparam] != null ) && ( params[ap.qparam].length() > 0 ) ) {
            // addParamInContext(owner,ap,params[ap.qparam],ap.contextTree)
            processContextTree(c, ap.contextTree, params[ap.qparam], ap.property)
          }
        }
      }
    }
  }

  private def  processContextTree = { qry, tree, value, paramdef, Class the_class = null ->
    if ( tree ) {
	  
	  // Turn it into a list.
	  if (!(tree instanceof Iterable)) {
		tree = [tree]
	  } 
	  
	  // Each item in the tree.
	  tree.each { contextTree ->

        def the_value = value
  
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
 			  qry.add(contextTree.prop, contextTree.comparator, the_value)
			}
            
            break;
        }
	  }
    }
  }

  def globalSearchTemplates = [
    'components':[
      baseclass:'org.gokb.cred.KBComponent',
      title:'Component Search',
      qbeConfig:[
        // For querying over associations and joins, here we will need to set up scopes to be referenced in the qbeForm config
        // Until we need them tho, they are omitted. qbeForm entries with no explicit scope are at the root object.
        qbeForm:[
          [
            prompt:'Name or Title',
            qparam:'qp_name',
            placeholder:'Name or title of item',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
          ],
          [
            prompt:'ID',
            qparam:'qp_id',
            placeholder:'ID of item',
            contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'id', 'type' : 'java.lang.Long']
          ]
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Type', property:'class.name'],
          [heading:'Name/Title', property:'name']
        ]
      ]
    ],
    'packages':[
      baseclass:'org.gokb.cred.Package',
      title:'Package Search',
      qbeConfig:[
        qbeForm:[
          [
            prompt:'Name of Package',
            qparam:'qp_name',
            placeholder:'Package Name',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
          ]
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Name/Identifier', property:'name'],
          [heading:'Nominal Platform', property:'nominalPlatform?.name']
        ]
      ]
    ],
    'orgs':[
      baseclass:'org.gokb.cred.Org',
      title:'Organisation Search',
      qbeConfig:[
        qbeForm:[
          [
            prompt:'Name or Title',
            qparam:'qp_name',
            placeholder:'Name or title of item',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
          ],
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Type', property:'class.name'],
          [heading:'Name/Title', property:'name']
        ]
      ]
    ],
    'platforms':[
      baseclass:'org.gokb.cred.Platform',
      title:'Platform Search',
      qbeConfig:[
        qbeForm:[
          [
            prompt:'Name or Title',
            qparam:'qp_name',
            placeholder:'Name or title of item',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
          ],
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Type', property:'class.name'],
          [heading:'Name/Title', property:'name']
        ]
      ]
    ],
    'titles':[
      baseclass:'org.gokb.cred.TitleInstance',
      title:'Title Search',
      qbeConfig:[
        qbeForm:[
          [
            prompt:'Name or Title',
            qparam:'qp_name',
            placeholder:'Name or title of item',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
          ],
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Type', property:'class.name'],
          [heading:'Name/Title', property:'name']
        ]
      ]
    ],
    'rules':[
      baseclass:'org.gokb.refine.Rule',
      title:'Rule Search',
      qbeConfig:[
        qbeForm:[
          [
            prompt:'Description',
            qparam:'qp_description',
            placeholder:'Rule Description',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'description']
          ],
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Fingerprint', property:'fingerprint'],
          [heading:'Description', property:'description']
        ]
      ]
    ],
    'projects':[
      baseclass:'org.gokb.refine.RefineProject',
      title:'Import Project Search',
      qbeConfig:[
        qbeForm:[
          [
            prompt:'Name',
            qparam:'qp_name',
            placeholder:'Project Name',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
          ],
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Name', property:'name'],
          [heading:'Provider', property:'provider?.name']
        ]
      ]
    ],
    'tipps':[
      baseclass:'org.gokb.cred.TitleInstancePackagePlatform',
      title:'TIPP Search',
      qbeConfig:[
        qbeForm:[
          [
            prompt:'Title',
            qparam:'qp_title',
            placeholder:'Title',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'title.name'],
          ],
//          [
//            prompt:'Content Provider',
//            qparam:'qp_cp_name',
//            placeholder:'Content Provider Name',
//            contextTree:['ctxtp' : 'qry', 'comparator' : 'ilike', 'prop' : 'pkg.provider.name']
//          ],
//          [
//            prompt:'Content Provider ID',
//            qparam:'qp_cp_id',
//            placeholder:'Content Provider ID',
//            contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'pkg.provider.id', 'type' : 'java.lang.Long']
//          ],
          [
            prompt:'Package ID',
            qparam:'qp_pkg_id',
            placeholder:'Package ID',
            contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'pkg.id', 'type' : 'java.lang.Long']
          ],
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Title', property:'title.name']
        ]
      ]
    ],
    'refdataCategories':[
      baseclass:'org.gokb.cred.RefdataCategory',
      title:'Refdata Search',
      qbeConfig:[
        qbeForm:[
          [
            prompt:'Description',
            qparam:'qp_desc',
            placeholder:'Category Description',
            contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'desc']
          ],
        ],
	  	qbeGlobals:[
		  ['ctxtp':'filter', 'prop':'desc', 'comparator' : 'ilike', 'value':'Combo.%', 'negate' : true]
		],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Description', property:'desc']
        ]
      ]
    ],

  ]


  // Types: staticgsp: under views/templates, dyngsp: in database, dynamic:full dynamic generation, other...
  def globalDisplayTemplates = [
    'org.gokb.cred.Package': [ type:'staticgsp', rendername:'package' ],
    'org.gokb.cred.Org': [ type:'staticgsp', rendername:'org' ],
    'org.gokb.cred.Platform': [ type:'staticgsp', rendername:'platform' ],
    'org.gokb.cred.TitleInstance': [ type:'staticgsp', rendername:'title' ],
    'org.gokb.cred.TitleInstancePackagePlatform': [ type:'staticgsp', rendername:'tipp' ],
    'org.gokb.refine.Rule': [ type:'staticgsp', rendername:'rule' ],
    'org.gokb.refine.RefineProject': [ type:'staticgsp', rendername:'project' ],
    'org.gokb.cred.RefdataCategory': [ type:'staticgsp', rendername:'rdc' ]
  ]
}
