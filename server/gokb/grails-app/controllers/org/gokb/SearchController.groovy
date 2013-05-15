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
        qbetemplate.qbeConfig.qbeForm.each 
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

  private def  processContextTree = { qry, contextTree, value, paramdef, Class the_class = null ->
    if ( contextTree ) {
	  
	  def the_value = value
	  
      switch ( contextTree.ctxtp ) {
        case 'assoc':
          qry."${contextTree.prop}" {
            processContextTree(qry, contextTree.children, value, paramdef)
            contextTree.filters.each { f ->
              qry.ilike(f.field,f.value)
            }
          }
          break;
        case 'filter':
          
		  // Filters work in the same way as queries,
		  // but the value is in the contextTree instead of the submitted value.
//          qry.ilike(contextTree.prop,contextTree.value)
		  the_value = contextTree.value
        case 'qry':
          
//		  // Start class as the target class of the query builder, if none supplied.
//          the_class = the_class ?: qry.targetClass;
//		  
//		  // Get all the combo properties defined on the class.
//		  Map allProps = KBComponent.getAllComboPropertyDefinitionsFor(the_class)
//		  
//		  // Split the property and go through each property as needed.
//		  List props = contextTree.prop.split("\\.")
//		  
//		  // If props length > 1 then we need to first add all the necessary associations.
//		  if (props.size() > 1) {
//			
//			// Pop the first element from the list.
//			String prop = props.remove(0)
//			
//			// Set the property value to the new list minus the head.
//			def newCtxtTree = contextTree.clone()
//			newCtxtTree.prop = props.join(".")
//			
//			// Get the type that the property maps to (check combo props first).
//			Class target_class = allProps[prop]
//			
//			// Combo property?
//			if (target_class) {
//			  boolean incoming = KBComponent.lookupComboMappingFor (the_class, Combo.MAPPED_BY, prop)
//			  
//			  // Combo property... Let's add the association.
//			  if (incoming) {
//				// Use incoming combos.
//				qry."incomingCombos" {
//				  and {
//					eq (
//					  "type",
//					  RefdataCategory.lookupOrCreate (
//						"Combo.Type",
//						the_class.getComboTypeValueFor (the_class, prop)
//					  )
//					)
//					fromComponent {
//                      processContextTree(delegate, newCtxtTree, value, paramdef, target_class)
//					}
//				  }
//				}
//				
//			  } else {
//				// Outgoing
//				qry."outgoingCombos" {
//				  and {
//					eq (
//					  "type",
//					  RefdataCategory.lookupOrCreate (
//						"Combo.Type",
//						the_class.getComboTypeValueFor (the_class, prop)
//					  )
//					)
//					toComponent {
//                      processContextTree(delegate, newCtxtTree, value, paramdef, target_class)
//					}
//				  }
//				}
//			  }  
//			} else {
//				// Normal groovy/grails property.
//				target_class = GrailsClassUtils.getPropertyType(the_class, prop)
//				
//				// Add the association here.
//				qry."prop" {
//				  processContextTree(delegate, newCtxtTree, value, paramdef, target_class)
//				}
//			}
//			
//		  } else {
//  		  	   
//		  	// We need to do the comparison.
//		         
//            // Check if this is a combo property.
//            if (allProps[contextTree.prop]) {
//              
//              // Add association using either incoming or outgoing properties.
//              boolean incoming = KBComponent.lookupComboMappingFor (the_class, Combo.MAPPED_BY, contextTree.prop)
//              
//              if (incoming) {
//                // Use incoming combos.
//                qry."incomingCombos" {
//                  and {
//                    eq (
//                      "type",
//                      RefdataCategory.lookupOrCreate (
//                        "Combo.Type",
//                        the_class.getComboTypeValueFor (the_class, contextTree.prop)
//                      )
//                    )
//                    fromComponent {
//  //                    processContextTree(delegate, contextTree.children, value, paramdef)
//  						ilike(contextTree.prop,the_value)
//                    }
//                  }
//                }
//                
//              } else {
//                // Outgoing
//                qry."outgoingCombos" {
//                  and {
//                    eq (
//                      "type",
//                      RefdataCategory.lookupOrCreate (
//                        "Combo.Type",
//                        the_class.getComboTypeValueFor (the_class, contextTree.prop)
//                      )
//                    )
//                    toComponent {
//  //                    processContextTree(delegate, contextTree.children, value, paramdef)
//  						ilike(contextTree.prop,the_value)
//                    }
//                  }
//                }
//              }
//            } else {
//              // Normal grails property.
//              qry.ilike(contextTree.prop,the_value)
//            }
//		  }
			// Use our custom criteria builder to compare the values.
			qry.add(contextTree.prop, "ilike", the_value)
         break;
      }
    }
  }

  def addParamInContext(qry,paramdef,value,contextTree) {
    // log.debug("addParamInContext ${qry.persistentEntity?.name} qry=${qry.toString()}: ${indent}");
    if ( ( contextTree ) && ( contextTree.size() > 0 ) ) {
      def new_tree = []
      new_tree.addAll(contextTree)
      def head_of_tree = new_tree.remove(0)
      // log.debug("Add context ${head_of_tree} - tail = ${new_tree}");
      // log.debug("Looking for property called ${head_of_tree.prop} of context class ${qry.persistentEntity?.name}");

      qry."${head_of_tree.prop}" {
        addParamInContext(delegate,paramdef,value,new_tree)
        head_of_tree.qualifiers.each { q ->
          // qry.ilike(q.field,q.value)
        }
      }
    }
    else {
      // log.debug("${indent} - addParamInContext(${paramdef.property},${value}) class of delegate is ${qry.persistentEntity?.name}");
      qry.ilike(paramdef.property,value)
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
            contextTree:['ctxtp':'qry', 'prop':'name']
          ],
          [ 
            prompt:'ID',
            qparam:'qp_id',
            placeholder:'ID of item',
            contextTree:['ctxtp':'qry', 'prop':'id']
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
            contextTree:['ctxtp':'qry', 'prop':'name']
          ]
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Package Name', property:'name'],
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
            contextTree:['ctxtp':'qry', 'prop':'name']
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
            contextTree:['ctxtp':'qry', 'prop':'name']
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
            contextTree:['ctxtp':'qry', 'prop':'name']
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
            contextTree:['ctxtp':'qry', 'prop':'description']
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
            contextTree:['ctxtp':'qry', 'prop':'name']
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
            contextTree:[
			  'ctxtp':'qry',
			  'prop':'title.name'
			],
          ],
          [
            prompt:'Content Provider',
            qparam:'qp_cp_name',
            placeholder:'Content Provider Name',
			contextTree:['ctxtp' : 'qry', 'prop' : 'pkg.provider.name']
//            contextTree:['ctxtp':'property','prop':'pkg','children':[
//                          ['ctxtp':'assoc','prop':'incomingCombos', 'children':[
//                            ['ctxtp':'assoc','prop':'type','children':[
//                              ['ctxtp':'assoc','prop':'owner','children':[
//                                ['ctxtp':'filter', 'prop':'desc', 'value':'Combo.Type']]],
//                              ['ctxtp':'filter', 'prop':'value', 'value':'ContentProvider']]],
//                            ['ctxtp':'assoc','prop':'fromComponent', 'children':[
//                              ['ctxtp':'qry', 'prop':'name']]]]]]]
          ],
          [
            prompt:'Content Provider ID',
            qparam:'qp_cp_id',
            placeholder:'Content Provider ID',
			contextTree:['ctxtp' : 'qry', 'prop' : 'pkg.provider.id']
//            contextTree:['ctxtp':'assoc','prop':'pkg','children':[
//                          ['ctxtp':'assoc','prop':'incomingCombos', 'children':[
//                            ['ctxtp':'assoc','prop':'type','children':[
//                              ['ctxtp':'assoc','prop':'owner','children':[
//                                ['ctxtp':'filter', 'prop':'desc', 'value':'Combo.Type']]],
//                              ['ctxtp':'filter', 'prop':'value', 'value':'ContentProvider']]],
//                            ['ctxtp':'assoc','prop':'fromComponent', 'children':[
//                              ['ctxtp':'qry', 'prop':'id']]]]]]]
          ],
          [
            prompt:'Package ID',
            qparam:'qp_pkg_id',
            placeholder:'Package ID',
			contextTree:['ctxtp' : 'qry', 'prop' : 'pkg.id']
//            contextTree:['ctxtp':'property','prop':'pkg','children':[
//                              ['ctxtp':'qry', 'prop':'id']]]
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
            contextTree:['ctxtp':'qry', 'prop':'desc']
          ],
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
