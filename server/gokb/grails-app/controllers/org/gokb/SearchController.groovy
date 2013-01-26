package org.gokb

import grails.converters.*

class SearchController {

  def genericOIDService

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
    def c = target_class.getClazz().createCriteria()

    def count_result = c.get {
      and {
        qbetemplate.qbeConfig.qbeForm.each { ap ->
          log.debug("testing ${ap} : ${params[ap.qparam]}");
          if ( ( params[ap.qparam] != null ) && ( params[ap.qparam].length() > 0 ) ) {
            processContextTree(owner, ap.contextTree, params[ap.qparam], ap.property)
          }
        }
      }
      projections {
        rowCount()
      }
    }
    result.reccount = count_result;
    log.debug("criteria result: ${count_result}");

    c = target_class.getClazz().createCriteria()
    result.recset = c.list(max: result.max, offset: result.offset) {
      and {
        qbetemplate.qbeConfig.qbeForm.each { ap ->
          log.debug("testing ${ap} : ${params[ap.qparam]}");
          if ( ( params[ap.qparam] != null ) && ( params[ap.qparam].length() > 0 ) ) {
            // addParamInContext(owner,ap,params[ap.qparam],ap.contextTree)
            processContextTree(owner, ap.contextTree, params[ap.qparam], ap.property)
          }
        }
      }
    }
  }

  def  processContextTree(qry, contextTree, value, paramdef) {
    if ( contextTree ) {
      switch ( contextTree.ctxtp ) {
        case 'assoc':
          qry."${contextTree.prop}" {
            processContextTree(delegate, contextTree.children, value, paramdef)
            contextTree.filters.each { f ->
              qry.ilike(f.field,f.value)
            }
          }
          break;
        case 'filter':
          qry.ilike(contextTree.prop,contextTree.value)
          break;
        case 'qry':
          qry.ilike(contextTree.prop,value)
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
        // Until we need them tho, they are omitted. qbeForm entries with no explit scope are at the root object.
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
            contextTree:[['ctxtp':'property','prop':'title','children':[
                           'ctxtp':'qry', 'prop':'name']]]
          ],
          [
            prompt:'Content Provider',
            qparam:'qp_cp_name',
            placeholder:'Content Provider Name',
            contextTree:['ctxtp':'assoc','prop':'pkg','children':[
                          ['ctxtp':'assoc','prop':'incomingCombos', 'children':[
                            ['ctxtp':'assoc','prop':'type','children':[
                              ['ctxtp':'assoc','prop':'owner','children':[
                                ['ctxtp':'filter', 'prop':'desc', 'value':'Combo.Type']]],
                              ['ctxtp':'filter', 'prop':'value', 'value':'ContentProvider']]],
                            ['ctxtp':'assoc','prop':'fromComponent', 'children':[
                              ['ctxtp':'qry', 'prop':'name']]]]]]]
          ],
          [
            prompt:'Content Provider ID',
            qparam:'qp_cp_name_id',
            placeholder:'Content Provider ID',
            contextTree:['ctxtp':'assoc','prop':'pkg','children':[
                          ['ctxtp':'assoc','prop':'incomingCombos', 'children':[
                            ['ctxtp':'assoc','prop':'type','children':[
                              ['ctxtp':'assoc','prop':'owner','children':[
                                ['ctxtp':'filter', 'prop':'desc', 'value':'Combo.Type']]],
                              ['ctxtp':'filter', 'prop':'value', 'value':'ContentProvider']]],
                            ['ctxtp':'assoc','prop':'fromComponent', 'children':[
                              ['ctxtp':'qry', 'prop':'id']]]]]]]
          ],
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Title', property:'title.name']
        ]
      ]
    ]
  ]


  // Types: staticgsp: under views/templates, dyngsp: in database, dynamic:full dynamic generation, other...
  def globalDisplayTemplates = [
    'org.gokb.cred.Package': [ type:'staticgsp', rendername:'package' ],
    'org.gokb.cred.Org': [ type:'staticgsp', rendername:'org' ],
    'org.gokb.cred.Platform': [ type:'staticgsp', rendername:'platform' ],
    'org.gokb.cred.TitleInstance': [ type:'staticgsp', rendername:'title' ],
    'org.gokb.cred.TitleInstancePackagePlatform': [ type:'staticgsp', rendername:'tipp' ],
    'org.gokb.refine.Rule': [ type:'staticgsp', rendername:'rule' ],
    'org.gokb.refine.RefineProject': [ type:'staticgsp', rendername:'project' ]
  ]
}
