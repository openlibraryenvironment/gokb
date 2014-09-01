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

identifiers.class_ones = [
  "issn",
  "eissn",
  "doi",
  "isbn"
] as Set

// if (System.properties["${appName}.config.location"]) {
//    grails.config.locations << "file:" + System.properties["${appName}.config.location"]
// }

project_dir = new java.io.File(org.codehaus.groovy.grails.io.support.GrailsResourceUtils.GRAILS_APP_DIR + "/../project-files/").getCanonicalPath() + "/"

refine_min_version = "3.1"

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
      'grails.app.conf',
      'grails.app.jobs',
      'com.k_int'

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
validation.regex.uri = "^(f|ht)tp(s?):\\/\\/([a-zA-Z\\d\\-\\.])+(:\\d{1,4})?(\\/[a-zA-Z\\d\\-\\._~\\/\\?\\#\\[\\]@\\!\\%\\:\\\$\\&'\\(\\)\\*\\+,;=]*)?\$"
validation.regex.date = "^[1-9][0-9]{3,3}\\-(0[1-9]|1[0-2])\\-(0[1-9]|[1-2][0-9]|3[0-1])\$"
validation.regex.kbartembargo = "^[RP]\\d+[DMY]\$"
validation.regex.kbartcoveragedepth = "^(\\Qfulltext\\E|\\Qselected articles\\E|\\Qabstracts\\E)\$"

validation.rules = [
  "${IngestService.PUBLICATION_TITLE}" : [
    [ type: ColumnMissing			, severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty			, severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  // All platforms
  "platform.*.*" : [
    [
      type: MustMatchRefdataValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        /platform\.([^\.]*)\..*/,
        "Platform.Roles"
      ]
    ]
  ],

  "${IngestService.HOST_PLATFORM_URL}" : [
    [ type: ColumnMissing	, severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: CellMatches,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${validation.regex.uri}",
        "One or more rows contain invalid URIs in the column \"${IngestService.HOST_PLATFORM_URL}\"",
        "if (and (isNonBlank(value), (value.match(/${validation.regex.uri}/) == null)), 'invalid', null)",
      ]
    ],
  ],

  "${IngestService.HOST_PLATFORM_NAME}" : [
    [ type: ColumnMissing	, severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: LookedUpValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [ org.gokb.cred.Platform ]
    ]
  ],

  "${IngestService.DATE_FIRST_PACKAGE_ISSUE}" : [
    [ type: ColumnMissing	, severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: EnsureDate		, severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  "${IngestService.DATE_LAST_PACKAGE_ISSUE}" : [
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: EnsureDate		, severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  "${IngestService.PACKAGE_NAME}" : [
    [ type: ColumnMissing	, severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_ERROR ],
    //		[
    //			type: IsSimilar,
    //			severity: A_ValidationRule.SEVERITY_WARNING,
    //			args: [
    //				org.gokb.cred.Package,
    //				9
    //			]
    //		]
    [
      type: LookedUpValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [ org.gokb.cred.Package ]
    ]
  ],

  "${IngestService.PUBLISHER_NAME}" : [
    [ type: ColumnMissing	, severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty	, severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: LookedUpValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [ org.gokb.cred.Org ]
    ]
  ],

  "${IngestService.EMBARGO_INFO}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: CellMatches,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${validation.regex.kbartembargo}",
        "Data in the column \"${IngestService.EMBARGO_INFO}\" must follow the <a target='_blank' href='http://www.uksg.org/kbart/s5/guidelines/data_fields#embargo' >KBART guidelines for an embargo</a>.",
        "if (and (isNonBlank(value), (value.match(/${validation.regex.kbartembargo}/) == null)), 'invalid', null)",
      ]
    ]
  ],

  "${IngestService.COVERAGE_DEPTH}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: CellMatches,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${validation.regex.kbartcoveragedepth}",
        "Data in the column \"${IngestService.COVERAGE_DEPTH}\" must follow the <a target='_blank' href='http://www.uksg.org/kbart/s5/guidelines/data_fields#coverage_depth' >KBART guidelines for an coverage depth</a>.",
        "if (and(isNonBlank(value), (value.match(/${validation.regex.kbartcoveragedepth}/) == null)), 'invalid', null)",
      ]
    ]
  ],

  "${IngestService.DELAYED_OA}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: IsOneOf,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        ["Yes", "No", "Unknown"]
      ]
    ]
  ],

  "${IngestService.DELAYED_OA_EMBARGO}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: CellMatches,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${validation.regex.kbartembargo}",
        "Data in the column \"${IngestService.DELAYED_OA_EMBARGO}\" must follow the <a target='_blank' href='http://www.uksg.org/kbart/s5/guidelines/data_fields#embargo' >KBART guidelines for an embargo</a>.",
        "if (and(isNonBlank(value), (value.match(/${validation.regex.kbartembargo}/) == null)), 'invalid', null)",
      ]
    ]
  ],

  "${IngestService.HYBRID_OA}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: IsOneOf,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        ["Yes", "No", "Unknown"]
      ]
    ]
  ],

  "${IngestService.HYBRID_OA_URL}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: CellMatches,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${validation.regex.uri}",
        "One or more rows contain invalid URIs in the column \"${IngestService.HYBRID_OA_URL}\"",
        "if (and (isNonBlank(value), (value.match(/${validation.regex.uri}/) == null)), 'invalid', null)",
      ]
    ]
  ],

  "${IngestService.PRIMARY_TIPP}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: IsOneOf,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        ["Yes", "No"]
      ]
    ]
  ],

  "${IngestService.TIPP_PAYMENT}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: IsOneOf,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        ["Complimentary", "Limited Promotion", "Paid", "Opt Out Promotion", "Uncharged", "Unknown"]
      ]
    ]
  ],

  "${IngestService.TIPP_STATUS}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: IsOneOf,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        ["Current", "Retired", "Expected"]
      ]
    ]
  ],

  // All Identifiers
  "${IngestService.IDENTIFIER_PREFIX}*" : [
    [ type: HasDuplicates , severity: A_ValidationRule.SEVERITY_WARNING ]
  ],

  // ISSN
  "${IngestService.IDENTIFIER_PREFIX}issn" : [
    [ type: ColumnMissing	, severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: CellMatches,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${validation.regex.issn}",
        "One or more rows do not conform to the format 'XXXX-XXXX' for the column \"${IngestService.IDENTIFIER_PREFIX}issn\"",
        "if (and (isNonBlank(value), value.match(/${validation.regex.issn}/) == null), 'invalid', null)",
      ]
    ],
    [
      type: CellAndOtherNotEmpty,
      severity: A_ValidationRule.SEVERITY_WARNING,
      args: ["${IngestService.IDENTIFIER_PREFIX}eissn"]
    ]
  ],

  "${IngestService.IDENTIFIER_PREFIX}eissn" : [
    [ type: ColumnMissing	, severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: CellMatches,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${validation.regex.issn}",
        "One or more rows do not conform to the format 'XXXX-XXXX' for the column \"${IngestService.IDENTIFIER_PREFIX}eissn\"",
        "if (and (isNonBlank(value), value.match(/${validation.regex.issn}/) == null), 'invalid', null)",
      ]
    ],
  ],

  // Custom ISBN.
  "${IngestService.IDENTIFIER_PREFIX}isbn" : [
    [
      type: CellMatches,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${validation.regex.isbn}",
        "One or more rows do not contain valid ISBNs in the column \"${IngestService.IDENTIFIER_PREFIX}isbn\". Note the ISBN should be entered without dashes.",
        "if (and (isNonBlank(value), value.match(/${validation.regex.isbn}/) == null), 'invalid', null)",
      ]
    ],
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

grails.gorm.autoFlush=true

//grails.gorm.failOnError=true



// https://github.com/k-int/gokb-phase1/blob/2853396eb1176a8ae94747810b2ec589847f8557/server/gokb/grails-app/controllers/org/gokb/SearchController.groovy

globalSearchTemplates = [
  'components':[
    baseclass:'org.gokb.cred.KBComponent',
    title:'Components',
    group:'Secondary',
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
        ],
        [
          prompt:'SID',
          qparam:'qp_sid',
          placeholder:'SID for item',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'ids.value']
        ],
      ],
      qbeResults:[
        [heading:'Type', property:'class.simpleName'],
        [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'packages':[
    baseclass:'org.gokb.cred.Package',
    title:'Packages',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name of Package',
          qparam:'qp_name',
          placeholder:'Package Name',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name', 'wildcard':'B']
        ]
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status.value', 'comparator' : 'eq', 'value':'Deleted', 'negate' : true]
      ],
      qbeResults:[
        [heading:'Name', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Nominal Platform', property:'nominalPlatform?.name'],
        [heading:'Status', property:'status.value'],
      ],
      actions:[
        [name:'Register Web Hook for all Packages', code:'general::registerWebhook', iconClass:'glyphicon glyphicon-link']
      ]
    ]
  ],
  'orgs':[
    baseclass:'org.gokb.cred.Org',
    title:'Organizations',
    group:'Secondary',
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
        [heading:'Name', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'platforms':[
    baseclass:'org.gokb.cred.Platform',
    title:'Platforms',
    group:'Secondary',
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
        [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'titles':[
    baseclass:'org.gokb.cred.TitleInstance',
    title:'Titles',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name','wildcard':'R']
        ],
        [
          prompt:'Content Provider',
          qparam:'qp_prov_id',
          placeholder:'Content Provider Id',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'publisher.id','type' : 'java.lang.Long']
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status.value', 'comparator' : 'eq', 'value':'Deleted', 'negate' : true]
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'],sort:'name' ],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'rules':[
    baseclass:'org.gokb.refine.Rule',
    title:'Rules',
    group:'Secondary',
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
        [heading:'Fingerprint', property:'fingerprint'],
        [heading:'Description', property:'description', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
      ]
    ]
  ],
  'projects':[
    baseclass:'org.gokb.refine.RefineProject',
    title:'Projects',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name',
          qparam:'qp_name',
          placeholder:'Project Name',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name', 'wildcard':'B']
        ],
      ],
      qbeResults:[
        [heading:'Name', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Provider', property:'provider?.name'],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'tipps':[
    baseclass:'org.gokb.cred.TitleInstancePackagePlatform',
    title:'TIPPs',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Title',
          qparam:'qp_title',
          placeholder:'Title',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'title.name'],
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Org',
          prompt:'Content Provider',
          qparam:'qp_cp',
          placeholder:'Content Provider',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'pkg.provider']
        ],
        [
          prompt:'Title Publisher ID',
          qparam:'qp_pub_id',
          placeholder:'Title Publisher ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'title.publisher.id', 'type' : 'java.lang.Long']
        ],
        [
          prompt:'Package ID',
          qparam:'qp_pkg_id',
          placeholder:'Package ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'pkg.id', 'type' : 'java.lang.Long']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Package',
          prompt:'Package',
          qparam:'qp_pkg',
          placeholder:'Package',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'pkg']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Platform',
          prompt:'Platform',
          qparam:'qp_plat',
          placeholder:'Platform',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'hostPlatform']
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status.value', 'comparator' : 'eq', 'value':'Deleted', 'negate' : true]
      ],
      qbeResults:[
        [heading:'TIPP Persistent Id', property:'persistentId', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Title', property:'title.name', link:[controller:'resource',action:'show',id:'x.r.title.class.name+\':\'+x.r.title.id'] ],
        [heading:'Status', property:'status.value'],
        [heading:'Package', property:'pkg.name', link:[controller:'resource',action:'show',id:'x.r.pkg.class.name+\':\'+x.r.pkg.id'] ],
        [heading:'Platform', property:'hostPlatform.name', link:[controller:'resource',action:'show',id:'x.r.hostPlatform.class.name+\':\'+x.r.hostPlatform.id'] ],
      ]
    ]
  ],
  'refdataCategories':[
    baseclass:'org.gokb.cred.RefdataCategory',
    title:'Refdata Categories ',
    group:'Secondary',
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
        [heading:'Description', property:'desc',  link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id']],
      ]
    ]
  ],
  'reviewRequests':[
    baseclass:'org.gokb.cred.ReviewRequest',
    title:'Requests For Review',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'ReviewRequest.Status',
          prompt:'Status',
          qparam:'qp_status',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'status']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.User',
          prompt:'Raised By',
          qparam:'qp_raisedby',
          placeholder:'Raised By',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'raisedBy']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.User',
          prompt:'Allocated To',
          qparam:'qp_allocatedto',
          placeholder:'Allocated To',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'allocatedTo']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Cause', property:'descriptionOfCause', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id']],
        [heading:'Request', property:'reviewRequest'],
        [heading:'Status', property:'status?.value'],
        [heading:'Raised By', property:'raisedBy?.username'],
        [heading:'Allocated To', property:'allocatedTo?.username'],
        [heading:'Timestamp', property:'dateCreated'],
      ]
    ]
  ],
  'Offices':[
    baseclass:'org.gokb.cred.Office',
    title:'Offices',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name or title of Office',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'Territories':[
    baseclass:'org.gokb.cred.Territory',
    title:'Territories',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name of Territory',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'Licenses':[
    baseclass:'org.gokb.cred.License',
    title:'Licenses',
    group:'Secondary',
    message:'Please contact nisohq@niso.org for more information on license downloads',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name of License',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'Users':[
    baseclass:'org.gokb.cred.User',
    title:'Users',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Username',
          qparam:'qp_name',
          placeholder:'Username',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'username']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Username', property:'username', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        // [heading:'Username', property:'username', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']]
      ]
    ]
  ],
  'Sources':[
    baseclass:'org.gokb.cred.Source',
    title:'Source',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name of Source',
          qparam:'qp_name',
          placeholder:'Name of Source',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        // [heading:'Name/Title', property:'name', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']],
        [heading:'Url', property:'url'],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'additionalPropertyDefinitions':[
    baseclass:'org.gokb.cred.AdditionalPropertyDefinition',
    title:'Additional Property Definitions',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Property Name',
          qparam:'qp_name',
          placeholder:'Property Name',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'propertyName']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Property Name', property:'propertyName', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        // [heading:'Property Name', property:'propertyName', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']]
      ]
    ]
  ],
  'dataFiles':[
    baseclass:'org.gokb.cred.DataFile',
    title:'Data Files',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'File Name',
          qparam:'qp_name',
          placeholder:'Name',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Created On', property:'dateCreated'],
        [heading:'Mime Type', property:'uploadMimeType'],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
  'domains':[
    baseclass:'org.gokb.cred.KBDomainInfo',
    title:'Domains',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name',
          qparam:'qp_name',
          placeholder:'Name',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'dcName', 'wildcard':'B']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name', property:'dcName', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Display Name', property:'displayName'],
        [heading:'Type', property:'type?.value'],
      ]
    ]
  ],
  'imprints':[
    baseclass:'org.gokb.cred.Imprint',
    title:'Imprints',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name',
          qparam:'qp_name',
          placeholder:'Name',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name', 'wildcard':'B']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status.value'],
      ]
    ]
  ],
]


// Types: staticgsp: under views/templates, dyngsp: in database, dynamic:full dynamic generation, other...
globalDisplayTemplates = [
  'org.gokb.cred.AdditionalPropertyDefinition': [ type:'staticgsp', rendername:'addpropdef' ],
  'org.gokb.cred.Package': [ type:'staticgsp', rendername:'package' ],
  'org.gokb.cred.Org': [ type:'staticgsp', rendername:'org' ],
  'org.gokb.cred.Platform': [ type:'staticgsp', rendername:'platform' ],
  'org.gokb.cred.TitleInstance': [ type:'staticgsp', rendername:'title' ],
  'org.gokb.cred.TitleInstancePackagePlatform': [ type:'staticgsp', rendername:'tipp' ],
  'org.gokb.refine.Rule': [ type:'staticgsp', rendername:'rule' ],
  'org.gokb.refine.RefineProject': [ type:'staticgsp', rendername:'project' ],
  'org.gokb.cred.RefdataCategory': [ type:'staticgsp', rendername:'rdc' ],
  'org.gokb.cred.ReviewRequest': [ type:'staticgsp', rendername:'revreq' ],
  'org.gokb.cred.Office': [ type:'staticgsp', rendername:'office' ],
  'org.gokb.cred.Territory': [ type:'staticgsp', rendername:'territory' ],
  'org.gokb.cred.License': [ type:'staticgsp', rendername:'license' ],
  'org.gokb.cred.User': [ type:'staticgsp', rendername:'user' ],
  'org.gokb.cred.Source': [ type:'staticgsp', rendername:'source' ],
  'org.gokb.cred.DataFile': [ type:'staticgsp', rendername:'datafile' ],
  'org.gokb.cred.KBDomainInfo': [ type:'staticgsp', rendername:'domainInfo' ],
  'org.gokb.cred.Imprint': [ type:'staticgsp', rendername:'imprint' ]
]

permNames = [
  1 : [name:'Read', inst:org.springframework.security.acls.domain.BasePermission.READ],
  2 : [name:'Write', inst:org.springframework.security.acls.domain.BasePermission.WRITE],
  4 : [name:'Create', inst:org.springframework.security.acls.domain.BasePermission.CREATE],
  8 : [name:'Delete', inst:org.springframework.security.acls.domain.BasePermission.DELETE],
  16 : [name:'Administration', inst:org.springframework.security.acls.domain.BasePermission.ADMINISTRATION],
]

grails.plugins.springsecurity.ui.password.minLength = 6
grails.plugins.springsecurity.ui.password.maxLength = 64
grails.plugins.springsecurity.ui.password.validationRegex = '^.*$'

//configure register
grails.plugins.springsecurity.ui.register.emailFrom = "GOKb<no-reply@gokb.k-int.com>"
grails.plugins.springsecurity.ui.register.emailSubject = 'Welcome to GOKb'
grails.plugins.springsecurity.ui.register.defaultRoleNames = [
  "ROLE_USER"
]
// The following 2 entries make the app use basic auth by default
grails.plugins.springsecurity.useBasicAuth = true
grails.plugins.springsecurity.basic.realmName = "gokb"

// This stanza then says everything should use form apart from /api
// More info: http://stackoverflow.com/questions/7065089/how-to-configure-grails-spring-authentication-scheme-per-url
grails.plugins.springsecurity.filterChain.chainMap = [
  '/integration/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
  '/api/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
  '/**': 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter'
  // '/soap/deposit': 'JOINED_FILTERS,-exceptionTranslationFilter',
  // '/rest/**': 'JOINED_FILTERS,-exceptionTranslationFilter'
  // '/rest/**': 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter'

]

cosine.good_threshold = 0.75

serverUrl = 'https://github.com/k-int/gokb-phase1/wiki/GOKb-Refine-Extensions'

grails.converters.json.circular.reference.behaviour = 'INSERT_NULL'

/**
 * We need to disable springs password encoding as we handle this in our domain model.
 */
grails.plugins.springsecurity.ui.encodePassword = false

defaultOaiConfig = [
  lastModified:'lastUpdated',
  schemas:[
    'oai_dc':[
      type:'method',
      methodName:'toOaiDcXml',
      schema:'http://www.openarchives.org/OAI/2.0/oai_dc.xsd',
      metadataNamespaces: [
        '_default_' : 'http://www.openarchives.org/OAI/2.0/oai_dc/',
        'dc'        : "http://purl.org/dc/elements/1.1/"
      ]],
    'gokb':[
      type:'method',
      methodName:'toGoKBXml',
      schema:'http://www.gokb.org/schemas/oai_metadata.xsd',
      metadataNamespaces: [
        '_default_': 'http://www.gokb.org/oai_metadata/'
      ]],
  ]
]

apiClasses = [
  "com.k_int.apis.SecurityApi",
  "com.k_int.apis.GrailsDomainHelpersApi"
]


// cors.headers = ['Access-Control-Allow-Origin': '*']
// 'Access-Control-Allow-Origin': 'http://xissn.worldcat.org'
//     'My-Custom-Header': 'some value'

// Uncomment and edit the following lines to start using Grails encoding & escaping improvements

/* remove this line 
 // GSP settings
 grails {
 views {
 gsp {
 encoding = 'UTF-8'
 htmlcodec = 'xml' // use xml escaping instead of HTML4 escaping
 codecs {
 expression = 'html' // escapes values inside null
 scriptlet = 'none' // escapes output from scriptlets in GSPs
 taglib = 'none' // escapes output from taglibs
 staticparts = 'none' // escapes output from static template parts
 }
 }
 // escapes all not-encoded output at final stage of outputting
 filteringCodecForContentType {
 //'text/html' = 'html'
 }
 }
 }
 remove this line */
