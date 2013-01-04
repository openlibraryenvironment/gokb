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
    result.recset = c.list(max: 20) {
      and {
        qbetemplate.qbeForm.each { ap ->
          if ( ( params[ap.qparam] != null ) && ( params[ap.qparam].length() > 0 ) ) {
            // if ( ap.proptype=='string' ) {
              like(ap.property,params[ap.qparam])
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
      qbeConfig:[
        qbeForm:[
        ],
        qbeResults:[]
      ]
    ],
    'platforms':[
      baseclass:'org.gokb.cred.Platform',
      qbeConfig:[
        qbeForm:[
        ],
        qbeResults:[]
      ]
    ],
    'titles':[
      baseclass:'org.gokb.cred.Title',
      qbeConfig:[
        qbeForm:[
        ],
        qbeResults:[]
      ]
    ]
  ]


  // Types: staticgsp: under views/templates, dyngsp: in database, dynamic:full dynamic generation, other...
  def globalDisplayTemplates = [
    'org.gokb.cred.Package': [
      type:'staticgsp',
      rendername:'package'
    ]
  ]
}
