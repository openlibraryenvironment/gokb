// locations to search for config files that get merged into the main config;
// config files can be ConfigSlurper scripts, Java properties files, or classes
// in the classpath in ConfigSlurper format


import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.gokb.IngestService
import org.gokb.validation.types.*

grails.config.locations = [ "classpath:${appName}-config.properties",
                            "classpath:${appName}-config.groovy",
                            "file:${userHome}/.grails/${appName}-config.properties",
                            "file:${userHome}/.grails/${appName}-config.groovy"]

// if (System.properties["${appName}.config.location"]) {
//    grails.config.locations << "file:" + System.properties["${appName}.config.location"]
// }

project_dir = new java.io.File(org.codehaus.groovy.grails.io.support.GrailsResourceUtils.GRAILS_APP_DIR + "/../project-files/").getCanonicalPath() + "/"

refine_min_version = "0.8"

grails.project.groupId = appName // change this to alter the default package name and Maven publishing destination
grails.mime.file.extensions = true // enables the parsing of file extensions from URLs into the request format
grails.mime.use.accept.header = false
grails.mime.types = [
    all:           '*/*',
    atom:          'application/atom+xml',
    css:           'text/css',
    csv:           'text/csv',
    form:          'application/x-www-form-urlencoded',
    html:          ['text/html','application/xhtml+xml'],
    js:            'text/javascript',
    json:          ['application/json', 'text/json'],
    multipartForm: 'multipart/form-data',
    rss:           'application/rss+xml',
    text:          'text/plain',
    xml:           ['text/xml', 'application/xml']
]

// URL Mapping Cache Max Size, defaults to 5000
//grails.urlmapping.cache.maxsize = 1000

// What URL patterns should be processed by the resources plugin
grails.resources.adhoc.patterns = ['/images/*', '/css/*', '/js/*', '/plugins/*']

// The default codec used to encode data with ${}
grails.views.default.codec = "none" // none, html, base64
grails.views.gsp.encoding = "UTF-8"
grails.converters.encoding = "UTF-8"
// enable Sitemesh preprocessing of GSP pages
grails.views.gsp.sitemesh.preprocess = true
// scaffolding templates configuration
grails.scaffolding.templates.domainSuffix = 'Instance'

// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart=false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.hibernate.cache.queries = false

environments {
    development {
        grails.logging.jul.usebridge = true
    }
    production {
        grails.logging.jul.usebridge = false
        // TODO: grails.serverURL = "http://www.changeme.com"
    }
}

// log4j configuration
log4j = {
    // Example of changing the log pattern for the default console appender:
    //
    //appenders {
    //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
    //}
    appenders {
        console name: "stdout", threshold: org.apache.log4j.Level.ALL
    }

    error  'org.codehaus.groovy.grails.web.servlet',        // controllers
           'org.codehaus.groovy.grails.web.pages',          // GSP
           'org.codehaus.groovy.grails.web.sitemesh',       // layouts
           'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
           'org.codehaus.groovy.grails.web.mapping',        // URL mapping
           'org.codehaus.groovy.grails.commons',            // core / classloading
           'org.codehaus.groovy.grails.plugins',            // plugins
           'org.codehaus.groovy.grails.orm.hibernate',      // hibernate integration
           'org.springframework',
           'org.hibernate',
           'net.sf.ehcache.hibernate'
		   
   debug  'grails.app.controllers',
          'grails.app.service',
          'grails.app.services',
          'grails.app.domain',
          'grails.app.tagLib',
          'grails.app.filters',
          // 'grails.app.conf',
          'grails.app.jobs' // ,
          
//   debug  'org.gokb.DomainClassExtender'
   
   // Enable Hibernate SQL logging with param values
//   trace 'org.hibernate.type'
//   debug 'org.hibernate.SQL'

}

// Added by the Spring Security Core plugin:
grails.plugins.springsecurity.userLookup.userDomainClassName = 'org.gokb.cred.User'
grails.plugins.springsecurity.userLookup.authorityJoinClassName = 'org.gokb.cred.UserRole'
grails.plugins.springsecurity.authority.className = 'org.gokb.cred.Role'

//Enable Basic Auth Filter
grails.plugins.springsecurity.useBasicAuth = true
grails.plugins.springsecurity.basic.realmName = "GOKb API Authentication Required"
//Exclude normal controllers from basic auth filter. Just the JSON API is included
grails.plugins.springsecurity.filterChain.chainMap = [
'/api/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
'/**': 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter'
]


appDefaultPrefs {
  globalDateFormat='dd MMMM yyyy'
}

validationRules = [
  [ type:'must', rule:'ContainOneOfTheFollowingColumns', colnames:[ 'title.identifier.issn'] ],
  [ type:'must', rule:'ContainOneOfTheFollowingColumns', colnames:[ 'title.identifier.eissn'] ],
  [ type:'must', rule:'ContainOneOfTheFollowingColumns', colnames:[ 'publicationtitle'] ],
  [ type:'must', rule:'ContainOneOfTheFollowingColumns', colnames:[ 'platform.host.name'] ],
  [ type:'must', rule:'ContainOneOfTheFollowingColumns', colnames:[ 'platform.host.url'] ] ,
  [ type:'must', rule:'ContainOneOfTheFollowingColumns', colnames:[ 'org.publisher.name'] ] 
]

validation.regex.issn = "^\\d{4}\\-\\d{3}[\\dX]\$"
validation.regex.isbn = "^(97(8|9))?\\d{9}[\\dX]\$"
validation.regex.uri = "^(f|ht)tp(s?)://([a-zA-Z\\d\\-\\.)+(:\\d{1,4})?(/[a-zA-Z\\d\\-\\._~/\\?\\#\\[\\]@\\!\\\$\\&'\\(\\)\\*\\+,;=]*)?\$"

validation.rules = [
  "${IngestService.PUBLICATION_TITLE}" : [
	[ type: ColumnRequired	, severity: A_ValidationRule.SEVERITY_ERROR ],
	[ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  "${IngestService.PRINT_IDENTIFIER}" : [
	[ type: ColumnRequired	, severity: A_ValidationRule.SEVERITY_ERROR ],
	[
	  type: CellMatches,
	  severity: A_ValidationRule.SEVERITY_ERROR,
	  args: [
		"${validation.regex.issn}",
		"One or more rows do not conform to the format 'XXXX-XXXX' for the column \"${IngestService.PRINT_IDENTIFIER}\"",
		"and (isNonBlank(value), value.match(/${validation.regex.issn}/) == null)",
	  ]
	],
	[ type: HasDuplicates	, severity: A_ValidationRule.SEVERITY_WARNING ]
  ],

  "${IngestService.ONLINE_IDENTIFIER}" : [
	[ type: ColumnRequired	, severity: A_ValidationRule.SEVERITY_ERROR ],
	[
	  type: CellMatches,
	  severity: A_ValidationRule.SEVERITY_ERROR,
	  args: [
		"${validation.regex.issn}",
		"One or more rows do not conform to the format 'XXXX-XXXX' for the column \"${IngestService.ONLINE_IDENTIFIER}\"",
		"and (isNonBlank(value), value.match(/${validation.regex.issn}/) == null)",
	  ]
	],
	[ type: HasDuplicates	, severity: A_ValidationRule.SEVERITY_WARNING ]
  ],

  "${IngestService.HOST_PLATFORM_URL}" : [
	[ type: ColumnRequired	, severity: A_ValidationRule.SEVERITY_ERROR ],
	[ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_ERROR ],
	[
	  type: CellMatches,
	  severity: A_ValidationRule.SEVERITY_ERROR,
	  args: [
		"${validation.regex.uri}",
		"One or more rows contain invlid URIs in the column \"${IngestService.HOST_PLATFORM_URL}\"",
		"and (isNonBlank(value), value.match(/${validation.regex.uri}/) == null)",
	  ]
	],
  ],

  "${IngestService.HOST_PLATFORM_NAME}" : [
	[ type: ColumnRequired	, severity: A_ValidationRule.SEVERITY_ERROR ],
	[ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  "${IngestService.DATE_FIRST_PACKAGE_ISSUE}" : [
	[ type: ColumnRequired	, severity: A_ValidationRule.SEVERITY_ERROR ],
	[ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  "${IngestService.PACKAGE_NAME}" : [
	[ type: ColumnRequired	, severity: A_ValidationRule.SEVERITY_ERROR ],
	[ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  // Custom ISBN.
  "title.identifier.isbn" : [
	[
	  type: CellMatches,
	  severity: A_ValidationRule.SEVERITY_ERROR,
	  args: [
		"${validation.regex.isbn}",
		"One or more rows do not contain valid ISBNs in the column \"title.identifier.isbn\". Note the ISBN should be entered without dashes.",
		"and (isNonBlank(value), value.match(/${validation.regex.isbn}/) == null)",
	  ]
	],
	[ type: HasDuplicates	, severity: A_ValidationRule.SEVERITY_WARNING ]
  ],
]

auditLog {
  actorClosure = { request, session ->

    if (request.applicationContext.springSecurityService.principal instanceof java.lang.String){
      return request.applicationContext.springSecurityService.principal
    }

    def username = request.applicationContext.springSecurityService.principal?.username

    if (SpringSecurityUtils.isSwitched()){
      username = SpringSecurityUtils.switchedUserOriginalUsername+" AS "+username
    }

    return username
  }  
}
grails.gorm.default.constraints = {
  '*'(nullable: true, blank:false)
}
//grails.gorm.failOnError=true



globalSearchTemplates = [
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
          [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ]
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
          [heading:'Name', property:'name', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']],
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
          [heading:'Name/Title', property:'name', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']]
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
          [heading:'Name/Title', property:'name', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']]
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
          [heading:'Name/Title', property:'name', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']]
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
          [heading:'Description', property:'description', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']]
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
          [heading:'Title', property:'title.name'],
          [heading:'Package', property:'pkg.name']
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
globalDisplayTemplates = [
  'org.gokb.cred.Package': [ type:'staticgsp', rendername:'package' ],
  'org.gokb.cred.Org': [ type:'staticgsp', rendername:'org' ],
  'org.gokb.cred.Platform': [ type:'staticgsp', rendername:'platform' ],
  'org.gokb.cred.TitleInstance': [ type:'staticgsp', rendername:'title' ],
  'org.gokb.cred.TitleInstancePackagePlatform': [ type:'staticgsp', rendername:'tipp' ],
  'org.gokb.refine.Rule': [ type:'staticgsp', rendername:'rule' ],
  'org.gokb.refine.RefineProject': [ type:'staticgsp', rendername:'project' ],
  'org.gokb.cred.RefdataCategory': [ type:'staticgsp', rendername:'rdc' ]
]

grails.plugins.springsecurity.ui.password.minLength = 6
grails.plugins.springsecurity.ui.password.maxLength = 64
grails.plugins.springsecurity.ui.password.validationRegex = '^.*$'
