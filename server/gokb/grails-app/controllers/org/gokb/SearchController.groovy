package org.gokb

class SearchController {

  def genericOIDService

  def index() { 
    log.debug("index...");
    def result = [:]
    if ( params.qbe ) {
      if ( params.qbe.startsWith('g:') ) {
        // Global template, look in config
        def global_qbe_template_shortcode = params.qbe.substring(2,params.qbe.length());
        log.debug("Looking up global template ${global_qbe_template_shortcode}");
        result.qbetemplate = globalSearchTemplates[global_qbe_template_shortcode]
      }

      // Looked up a template from somewhere, see if we can execute a search
      if ( result.qbetemplate ) {
        result.hits = doQuery(result.qbetemplate, params, result)
      }

      if ( params.displayoid ) {
        log.debug("Attempt to retrieve ${params.displayoid} and find a global template for it");
        result.displayobj = genericOIDService.resolveOID(params.displayoid)
        if ( result.displayobj ) {
          result.displayobjclassname = result.displayobj.class.name
          result.displaytemplate = globalDisplayTemplates[result.displayobjclassname]
        }
      }
    }
    result
  }

  def doQuery(qbetemplate, params, result) {
    // We are going to build up a critera query based on the base class specified in the config, and the properties listed
    def target_class = grailsApplication.getArtefact("Domain",qbetemplate.baseclass);
    
    def c = target_class.getClazz().createCriteria()

    log.debug("Created criteria against target classs: ${c}")

    // reuse from sip : ./sip/grails-app/controllers/com/k_int/sim/SearchController.groovy
    // def recset = c.list(max: 5, offset: 10) {
    log.debug("Iterate over form components: ${qbetemplate.qbeConfig.qbeForm}");
    result.recset = c.list(max: 20) {
      and {
        qbetemplate.qbeConfig.qbeForm.each { ap ->
          log.debug("testing ${ap}");
          if ( ( params[ap.qparam] != null ) && ( params[ap.qparam].length() > 0 ) ) {
            // if ( ap.proptype=='string' ) {
              ilike(ap.property,params[ap.qparam])
            // }
            // else if ( ap.proptype=='long' ) {
            //   eq(ap.propname,new Long(Long.parseLong(params[ap.propname])))
            // }
            // else {
            //   eq(ap.propname,"${params[ap.propname]}")
            // }
          }
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
        // Until we need them tho, they are omitted. qbeForm entries with no explit scope are at the root object.
        qbeForm:[
          [
            prompt:'Name or Title',
            property:'name',
            qparam:'qp_name',
            placeholder:'Name or title of item'
          ],
          [ 
            prompt:'ID',
            property:'id',
            qparam:'qp_id',
            placeholder:'ID of item'
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
            property:'name',
            qparam:'qp_name',
            placeholder:'Package Name'
          ]
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Type', property:'class.name'],
          [heading:'Name/Title', property:'name']
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
            property:'name',
            qparam:'qp_name',
            placeholder:'Name or title of item'
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
            property:'name',
            qparam:'qp_name',
            placeholder:'Name or title of item'
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
            property:'name',
            qparam:'qp_name',
            placeholder:'Name or title of item'
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
            property:'description',
            qparam:'qp_description',
            placeholder:'Rule Description'
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
            property:'name',
            qparam:'qp_name',
            placeholder:'Project Name'
          ],
        ],
        qbeResults:[
          [heading:'Id', property:'id'],
          [heading:'Name', property:'name'],
          [heading:'Provider', property:'provider.name']
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
