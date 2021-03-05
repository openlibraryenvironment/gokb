import java.text.SimpleDateFormat

// Added by the Spring Security Core plugin:
grails.plugin.springsecurity.userLookup.userDomainClassName = 'org.gokb.cred.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'org.gokb.cred.UserRole'
grails.plugin.springsecurity.authority.className = 'org.gokb.cred.Role'

grails.plugin.springsecurity.ui.forgotPassword.emailFrom = "GOKb<no-reply@gokb.org>"

grails.mime.file.extensions=false

grails.gorm.default.mapping = {
    autowire true
}
grails.plugin.springsecurity.ui.register.postRegisterUrl = '/public/index'

grails {
    plugin {
        auditLog {
            auditDomainClassName = "org.gokb.cred.AuditLogEvent"
            logFullClassName = false
        }
    }
}

grails.plugin.springsecurity.filterChain.chainMap = [
        [pattern: '/login/auth',          filters: 'none'],
        [pattern: '/assets/**',           filters: 'none'],
        [pattern: '/**/js/**',            filters: 'none'],
        [pattern: '/**/css/**',           filters: 'none'],
        [pattern: '/**/images/**',        filters: 'none'],
        [pattern: '/**/favicon.ico',      filters: 'none'],
        [pattern: '/error',               filters: 'none'],
        [pattern: '/ajaxSupport/**',      filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/fwk/**',              filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/api/**',              filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/integration/**',      filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/admin/bulkLoadUsers', filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/rest/login',          filters: 'JOINED_FILTERS,-exceptionTranslationFilter,-basicAuthenticationFilter,-basicExceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'],
        [pattern: '/rest/**/**',          filters: 'JOINED_FILTERS,-exceptionTranslationFilter,-basicAuthenticationFilter,-basicExceptionTranslationFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'],
        [pattern: '/rest/**',             filters: 'JOINED_FILTERS,-exceptionTranslationFilter,-basicAuthenticationFilter,-basicExceptionTranslationFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'],
        [pattern: '/oauth/**',            filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/**',                  filters: 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter,-restTokenValidationFilter,-restExceptionTranslationFilter'],
]

grails.plugin.springsecurity.controllerAnnotations.staticRules = [
  [pattern: '/admin/**',                access: ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/file/**',                 access: ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/info',                    access: ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/monitoring/**',           access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/login/auth',              access: ['permitAll']],
  [pattern: '/greenmail/**',            access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/',                        access: ['permitAll']],
  [pattern: '/index',                   access: ['permitAll']],
  [pattern: '/notFound',                access: ['permitAll']],
  [pattern: '/index.gsp',               access: ['permitAll']],
  [pattern: '/register/**',             access: ['permitAll']],
  [pattern: '/public/**',               access: ['permitAll']],
  [pattern: '/package/**',              access: ['permitAll']],
  [pattern: '/packages/**',             access: ['permitAll']],
  [pattern: '/component/identifierConflicts', access: ['ROLE_EDITOR', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/public',                  access: ['permitAll']],
  [pattern: '/error',                   access: ['permitAll']],
  [pattern: '/error/**',                access: ['permitAll']],
  [pattern: '/globalSearch/**',         access: ['ROLE_USER']],
  [pattern: '/home/**',                 access: ['ROLE_USER']],
  [pattern: '/assets/**',               access: ['permitAll']],
  [pattern: '/**/js/**',                access: ['permitAll']],
  [pattern: '/**/css/**',               access: ['permitAll']],
  [pattern: '/**/images/**',            access: ['permitAll']],
  [pattern: '/**/favicon.ico',          access: ['permitAll']],
  [pattern: '/api/find',                access: ['permitAll']],
  [pattern: '/api/scroll',              access: ['permitAll']],
  [pattern: '/api/suggest',             access: ['permitAll']],
  [pattern: '/api/esconfig',            access: ['permitAll']],
  [pattern: '/api/capabilities',        access: ['permitAll']],
  [pattern: '/api/downloadUpdate',      access: ['permitAll']],
  [pattern: '/api/checkUpdate',         access: ['permitAll']],
  [pattern: '/api/isUp',                access: ['permitAll']],
  [pattern: '/api/userData',            access: ['permitAll']],
  [pattern: '/api/refdata',             access: ['ROLE_USER']],
  [pattern: '/api/show',                access: ['ROLE_USER']],
  [pattern: '/api/namespaces',          access: ['permitAll']],
  [pattern: '/api/groups',              access: ['permitAll']],
  [pattern: '/api/elasticsearchTunnel', access: ['permitAll']],
  [pattern: '/api/retrieveZdbCandidates', access: ['permitAll']],
  [pattern: '/integration/**',          access: ['permitAll']],
  [pattern: '/fwk/**',                  access: ['ROLE_USER']],
  [pattern: '/user/**',                 access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/user/search',             access: ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/user/edit/**',            access: ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/role/**',                 access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/securityInfo/**',         access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/registrationCode/**',     access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/aclClass/**',             access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/aclSid/**',               access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/aclObjectIdentity/**',    access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/aclEntry/**',             access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/oai',                     access: ['permitAll']],
  [pattern: '/oai/**',                  access: ['permitAll']],
  [pattern: '/rest/login',              access: ['permitAll']],
 // [pattern: '/rest/roles',              access: ['permitAll']],
  //[pattern: '/rest/curatoryGroups',     access: ['permitAll']],
  //[pattern: '/rest/curatoryGroups/**',  access: ['permitAll']],
  [pattern: '/rest/refdata',            access: ['permitAll']],
  [pattern: '/rest/refdata/**',         access: ['permitAll']],
  [pattern: '/rest/**',                 access: ['ROLE_USER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/oauth/**',                access: ['permitAll']],
  [pattern: '/coreference/**',          access: ['permitAll']]
]


appDefaultPrefs {
  globalDateFormat='yyyy-MM-dd'
}

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
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'KBComponent.Status',
          prompt:'Status',
          qparam:'qp_status',
          placeholder:'Component Status',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'status'],
          // II: Default not yet implemented
          default:[ type:'query', query:'select r from RefdataValue where r.value=:v and r.owner.description=:o', params:['Current','KBComponent.Status'] ]
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'KBComponent.EditStatus',
          prompt:'Edit Status',
          qparam:'qp_editstatus',
          placeholder:'Component Edit Status',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'editStatus']
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Type', property:'niceName'],
        [heading:'Name/Title', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status?.value',sort:'status'],
        [heading:'Edit Status', property:'editStatus?.value',sort:'editStatus'],
      ]
    ]
  ],
  '1packages':[
    baseclass:'org.gokb.cred.Package',
    title:'Packages',
    group:'Secondary',
    defaultSort:'name',
    defaultOrder:'asc',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name of Package',
          qparam:'qp_name',
          placeholder:'Package Name',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name', 'wildcard':'B', normalise:false]
        ],
        [
          prompt:'Identifier',
          qparam:'qp_identifier',
          placeholder:'Identifier Value',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'ids.value'],
          hide:false
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'KBComponent.Status',
          prompt:'Status',
          qparam:'qp_status',
          placeholder:'Component Status',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'status'],
          // II: Default not yet implemented
          default:[ type:'query', query:'select r from RefdataValue where r.value=:v and r.owner.description=:o', params:['Current','KBComponent.Status'] ]
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'Package.ListStatus',
          prompt:'List Status',
          qparam:'qp_liststatus',
          placeholder:'List Status',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'listStatus'],
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'Package.Global',
          prompt:'Availability',
          qparam:'qp_global',
          placeholder:'Availability',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'global'],
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'Package.ContentType',
          prompt:'Content Type',
          qparam:'qp_content',
          placeholder:'Content Type',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'contentType'],
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Org',
          prompt:'Provider',
          qparam:'qp_provider',
          placeholder:'Provider',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'provider'],
          hide:false
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.CuratoryGroup',
          prompt:'Curatory Group',
          qparam:'qp_curgroup',
          placeholder:'Curatory Group',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'curatoryGroups'],
          hide:false
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Platform',
          prompt:'Platform',
          qparam:'qp_platform',
          placeholder:'Platform',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'nominalPlatform'],
          hide:false
        ],
        [
          prompt:'Platform ID',
          qparam:'qp_platform_id',
          placeholder:'Platform ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'nominalPlatform.id', 'type' : 'java.lang.Long'],
          hide:true
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Provider', property:'provider?.name', link:true],
        [heading:'Name', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Nominal Platform', property:'nominalPlatform?.name'],
        [heading:'Content Type', property:'contentType?.value', sort:'contentType'],
        [heading:'Availability', property:'global', sort:'global'],
        [heading:'List Status', property:'listStatus?.value',sort:'listStatus'],
        [heading:'Last Updated', property:'lastUpdated',sort:'lastUpdated'],
        [heading:'Status', property:'status?.value',sort:'status'],
      ],
      actions:[
        [name:'Register Web Hook for all Packages', code:'general::registerWebhook', iconClass:'glyphicon glyphicon-link']
      ]
    ]
  ],
  '2orgs':[
    baseclass:'org.gokb.cred.Org',
    title:'Organizations',
    group:'Secondary',
    defaultSort:'name',
    defaultOrder:'asc',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name', 'wildcard':'R']
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status.value', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on']
      ],
      qbeResults:[
        [heading:'Name', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', sort:'status', property:'status?.value'],
        [heading:'Last Updated', property:'lastUpdated',sort:'lastUpdated'],
      ]
    ]
  ],
  '1platforms':[
    baseclass:'org.gokb.cred.Platform',
    title:'Platforms',
    group:'Secondary',
    defaultSort:'name',
    defaultOrder:'asc',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'KBComponent.Status',
          prompt:'Status',
          qparam:'qp_status',
          placeholder:'Component Status',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'status'],
          // II: Default not yet implemented
          default:[ type:'query', query:'select r from RefdataValue where r.value=:v and r.owner.description=:o', params:['Current','KBComponent.Status'] ]
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name', sort:'name',link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Primary URL', property:'primaryUrl'],
        [heading:'Status', property:'status?.value',sort:'status'],
      ]
    ]
  ],
  '1titles':[
    baseclass:'org.gokb.cred.TitleInstance',
    title:'Titles (General)',
    group:'Secondary',
    // defaultSort:'name',
    // defaultOrder:'asc',
    // useDistinct: true,
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          // contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name','wildcard':'R']
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name','wildcard':'R'] // , normalise:true
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Org',
          prompt:'Publisher',
          qparam:'qp_pub',
          placeholder:'Publisher',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'publisher'],
          hide:false
        ],
        [
          prompt:'Identifier',
          qparam:'qp_identifier',
          placeholder:'Identifier Value',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'ids.value'],
          hide:false
        ],
        [
          prompt:'Title Publisher ID',
          qparam:'qp_pub_id',
          placeholder:'Title Publisher ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'publisher.id', 'type' : 'java.lang.Long'],
          hide:true
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'TitleInstance.Medium',
          prompt:'Type',
          qparam:'qp_medium',
          placeholder:'Medium of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'medium'],
          // II: Default not yet implemented
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'KBComponent.Status',
          prompt:'Status',
          qparam:'qp_status',
          placeholder:'Status of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'status'],
          // II: Default not yet implemented
          default:[ type:'query', query:'select r from RefdataValue where r.value=:v and r.owner.description=:o', params:['Current','KBComponent.Status'] ]
        ],

        // In order for this to work as users expect, we're going to need a unique clause at the root context, or we get
        // repeated rows where a wildcard matches multiple titles. [That or this clause needs to be an "exists" caluse]
        // [
        //   prompt:'Identifier',
        //   qparam:'qp_identifier',
        //   placeholder:'Any identifier',
        //   contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'ids.value','wildcard':'B']
        // ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'ID', property:'id', link:[controller:'resource',action:'show',id:'x.r?.class?.name+\':\'+x.r?.id'],sort:'name' ],
        [heading:'Name/Title', property:'name', sort:'name', link:[controller:'resource',action:'show',id:'x.r?.class?.name+\':\'+x.r?.id'] ],
        [heading:'Type', property:'medium?.value', sort:'name'],
        [heading:'Status', property:'status?.value',sort:'status'],
        [heading:'Date Created', property:'dateCreated',sort:'dateCreated'],
        [heading:'Last Updated', property:'lastUpdated',sort:'lastUpdated'],
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
        [heading:'Name', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Provider', sort:'provider.name', property:'provider?.name'],
        [heading:'Status', sort:'status', property:'status?.value'],
      ]
    ]
  ],
  '3tipps':[
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
          prompt:'Title ID',
          qparam:'qp_title_id',
          placeholder:'Title ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'title.id', 'type' : 'java.lang.Long'],
          hide:true
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'TitleInstance.Medium',
          prompt:'Type',
          qparam:'qp_medium',
          placeholder:'Medium of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'title.medium'],
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
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'title.publisher.id', 'type' : 'java.lang.Long'],
          hide:true
        ],
        [
          prompt:'Package ID',
          qparam:'qp_pkg_id',
          placeholder:'Package ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'pkg.id', 'type' : 'java.lang.Long'],
          hide:true
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
          prompt:'Platform ID',
          qparam:'qp_plat_id',
          placeholder:'Platform ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'hostPlatform.id', 'type' : 'java.lang.Long'],
          hide:true
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Platform',
          prompt:'Platform',
          qparam:'qp_plat',
          placeholder:'Platform',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'hostPlatform']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'KBComponent.Status',
          prompt:'Status',
          qparam:'qp_status',
          placeholder:'Status',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'status'],
          // II: Default not yet implemented
          default:[ type:'query', query:'select r from RefdataValue where r.value=:v and r.owner.description=:o', params:['Current','KBComponent.Status'] ]
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'TIPP Persistent Id', property:'persistentId', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Title', qpEquiv:'qp_title_id', property:'title?.name', link:[controller:'resource',action:'show',id:'x.r.title?.class?.name+\':\'+x.r.title?.id'] ],
        [heading:'Type', qpEquiv:'qp_title_id', property:'title?.medium?.value'],
        [heading:'Package', qpEquiv:'qp_pkg_id', property:'pkg?.name', link:[controller:'resource',action:'show',id:'x.r.pkg?.class?.name+\':\'+x.r.pkg?.id'] ],
        [heading:'Platform', qpEquiv:'qp_plat_id', property:'hostPlatform?.name', link:[controller:'resource',action:'show',id:'x.r.hostPlatform?.class?.name+\':\'+x.r.hostPlatform?.id'] ],
        [heading:'Status', property:'status?.value', sort:'status']
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
        [heading:'Description', sort:'desc',property:'desc',  link:[controller:'resource',action:'show',id:'x.r.className+\':\'+x.r.id']],
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
          prompt:'Cause',
          qparam:'qp_cause',
          placeholder:'Cause',
          contextTree:['ctxtp':'qry', 'comparator' : 'like', 'prop':'descriptionOfCause']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'ReviewRequest.StdDesc',
          prompt:'Type',
          qparam:'qp_desc',
          placeholder:'Standard description',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'stdDesc']
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
        [heading:'Timestamp', property:'dateCreated', sort:'dateCreated'],
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
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status?.value',sort:'status'],
      ]
    ]
  ],
  'Macros':[
    baseclass:'org.gokb.cred.Macro',
    title:'Macros',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name or title of Macro',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status?.value',sort:'status'],
      ]
    ]
  ],
  'CuratoryGroups':[
    baseclass:'org.gokb.cred.CuratoryGroup',
    title:'Curatory Groups',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name or Title',
          qparam:'qp_name',
          placeholder:'Name of Curatory Group',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name', sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status?.value',sort:'status'],
        [heading:'Edit Status', property:'editStatus?.value', sort:'editStatus']
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
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Name/Title', property:'name', sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status?.value',sort:'status'],
      ]
    ]
  ],
  'Users':[
    baseclass:'org.gokb.cred.User',
    title:'Users',
    group:'Admin',
    defaultSort:'id',
    defaultOrder:'asc',
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
        [heading:'Enabled', property:'enabled'],
        [heading:'Contributor', property:'contributorStatus'],
        [heading:'Editor', property:'editorStatus'],
        [heading:'API-User', property:'apiUserStatus'],
        [heading:'Admin', property:'adminStatus']
        // [heading:'Username', property:'username', link:[controller:'search',action:'index',params:'x.params+[\'det\':x.counter]']]
      ]
    ]
  ],
  'UserOrganisation':[
    baseclass:'org.gokb.cred.UserOrganisation',
    title:'User Organisations',
    group:'Secondary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Name',
          qparam:'qp_name',
          placeholder:'Username',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'username']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name', property:'displayName', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
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
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'ID', property:'id', sort:'id', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Name/Title', property:'name', sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Url', property:'url',sort:'url'],
        [heading:'Status', property:'status?.value',sort:'status'],
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
        [heading:'Property Name', property:'propertyName',sort:'propertyName', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
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
        [heading:'Name', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Created On', property:'dateCreated',sort:'dateCreated'],
        [heading:'Mime Type', property:'uploadMimeType',sort:'uploadMimeType'],
        [heading:'Status', property:'status?.value',sort:'status'],
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
        [heading:'Sort Key', property:'dcSortOrder'],
        [heading:'Type', property:'type?.value'],
      ]
    ]
  ],
  'imprints':[
    baseclass:'org.gokb.cred.Imprint',
    title:'Imprints',
    defaultSort:'name',
    defaultOrder:'asc',
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
        [heading:'Name', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status?.value',sort:'status'],
      ]
    ]
  ],
  'Namespaces':[
    baseclass:'org.gokb.cred.IdentifierNamespace',
    title:'Namespaces',
    group:'Tertiary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Namespace',
          qparam:'qp_value',
          placeholder:'value',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'value', 'wildcard':'B']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Value', property:'value', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Name', property:'name' ],
        [heading:'RDF Datatype', property:'datatype?.value'],
        [heading:'Category', property:'family'],
        [heading:'Target Type', property:'targetType.value']
      ]
    ]
  ],
  'DSCategory':[
    baseclass:'org.gokb.cred.DSCategory',
    title:'DS Categories',
    group:'Tertiary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Description',
          qparam:'qp_descr',
          placeholder:'Description',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'description', 'wildcard':'B']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Code', property:'code', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Description', property:'description', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
      ]
    ]
  ],
  'DSCriterion':[
    baseclass:'org.gokb.cred.DSCriterion',
    title:'DS Criterion',
    group:'Tertiary',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Description',
          qparam:'qp_descr',
          placeholder:'Description',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'description', 'wildcard':'B']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Category', property:'owner.description', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Title', property:'title'],
        [heading:'Description', property:'description'],
      ]
    ]
  ],
  '1eBooks':[
    baseclass:'org.gokb.cred.BookInstance',
    title:'eBooks',
    group:'Secondary',
    defaultSort:'name',
    defaultOrder:'asc',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Book Title',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name','wildcard':'R']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Org',
          prompt:'Publisher',
          qparam:'qp_pub',
          placeholder:'Publisher',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'publisher'],
          hide:true
        ],
        [
          prompt:'Identifier',
          qparam:'qp_identifier',
          placeholder:'Identifier Value',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'ids.value'],
          hide:false
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Person',
          prompt:'Person',
          qparam:'qp_person',
          placeholder:'Person',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'people.person'],
          hide:true
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Subject',
          prompt:'Subject',
          qparam:'qp_subject',
          placeholder:'Subject',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'subjects.subject'],
          hide:true
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'],sort:'name' ],
        [heading:'Status', property:'status?.value',sort:'status'],
        [heading:'Date Created', property:'dateCreated',sort:'dateCreated'],
        [heading:'Last Updated', property:'lastUpdated',sort:'lastUpdated'],
      ]
    ]
  ],
  '1eJournals':[
    baseclass:'org.gokb.cred.JournalInstance',
    title:'Journals',
    group:'Secondary',
    defaultSort:'name',
    defaultOrder:'asc',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Journal Title',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name','wildcard':'R']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Org',
          prompt:'Publisher',
          qparam:'qp_pub',
          placeholder:'Publisher',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'publisher'],
          hide:false
        ],
        [
          prompt:'Identifier',
          qparam:'qp_identifier',
          placeholder:'Identifier Value',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'ids.value'],
          hide:false
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'KBComponent.Status',
          prompt:'Status',
          qparam:'qp_status',
          placeholder:'Status of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'status'],
          // II: Default not yet implemented
          default:[ type:'query', query:'select r from RefdataValue where r.value=:v and r.owner.description=:o', params:['Current','KBComponent.Status'] ]
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'],sort:'name' ],
        [heading:'Status', property:'status?.value',sort:'status'],
        [heading:'Date Created', property:'dateCreated',sort:'dateCreated'],
        [heading:'Last Updated', property:'lastUpdated',sort:'lastUpdated'],
      ]
    ]
  ],
  '1eDatabases':[
    baseclass:'org.gokb.cred.DatabaseInstance',
    title:'Databases',
    group:'Secondary',
    defaultSort:'name',
    defaultOrder:'asc',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Database Title',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name','wildcard':'R']
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Org',
          prompt:'Publisher',
          qparam:'qp_pub',
          placeholder:'Publisher',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'publisher'],
          hide:true
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'],sort:'name' ],
        [heading:'Status', property:'status?.value',sort:'status'],
        [heading:'Date Created', property:'dateCreated',sort:'dateCreated'],
        [heading:'Last Updated', property:'lastUpdated',sort:'lastUpdated'],
      ]
    ]
  ],
  '1aWorks':[
    baseclass:'org.gokb.cred.Work',
    title:'Works',
    group:'Primary',
    defaultSort:'name',
    defaultOrder:'asc',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Title',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name','wildcard':'R']
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'],sort:'name' ],
        [heading:'Bucket Hash', property:'bucketHash'],
        [heading:'Status', property:'status?.value',sort:'status'],
      ]
    ]
  ],
  'UserWatchedComponents':[
    baseclass:'org.gokb.cred.ComponentWatch',
    title:'My Components',
    group:'Tertiary',
    defaultSort:'component.lastUpdated',
    defaultOrder:'desc',
    qbeConfig:[
      qbeForm:[
        [
          prompt:'Component Name',
          qparam:'qp_name',
          placeholder:'Name or title of item',
          contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'component.name','wildcard':'R']
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'user.id', 'comparator' : 'eq', 'value':'__USERID', 'default':'on', 'qparam':'qp_user', 'type':'java.lang.Long', 'hidden':true]
      ],
      qbeResults:[
        [heading:'Name', property:'component.name', link:[controller:'resource',action:'show',id:'x.r.component.class.name+\':\'+x.r.component.id'] ],
        [heading:'Type', property:'component.niceName'],
        [heading:'Last Update on', property:'component.lastUpdated'],
        [heading:'Last Update by', property:'component.lastUpdatedBy?.username'],
        [heading:'Last Update Comment', property:'component.lastUpdateComment']
      ]
    ]
  ],
  'folderContents':[
    baseclass:'org.gokb.cred.FolderEntry',
    title:'Folder Contents',
    group:'Secondary',
    defaultSort:'id',
    defaultOrder:'asc',
    qbeConfig:[
      qbeForm:[
       [
          prompt:'Folder ID',
          qparam:'qp_folder_id',
          placeholder:'Folder ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'folder.id', 'type' : 'java.lang.Long']
        ],
      ],
      qbeGlobals:[
      ],
      qbeResults:[
        [heading:'Name/Title', property:'displayName', link:[controller:'resource', action:'show',      id:'x.r.linkedItem.class.name+\':\'+x.r.linkedItem.id'] ],
        [heading:'Availability', property:'linkedItem.tipps?.size()?:"none"'],
      ]
    ]
  ],
  'JobResult':[
    baseclass:'org.gokb.cred.JobResult',
    title:'Job Results',
    group:'Secondary',
    defaultSort:'id',
    defaultOrder:'desc',
    qbeConfig:[
      qbeForm:[
       [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'Job.Type',
          prompt:'Type',
          qparam:'qp_type',
          placeholder:'Type of Job',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'type']
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'ownerId', 'comparator' : 'eq', 'value':'__USERID', 'default':'on', 'qparam':'qp_owner', 'type':'java.lang.Long', 'hidden':true]
      ],
      qbeResults:[
        [heading:'Description', property:'description', link:[controller:'resource', action:'show', id:'x.r.uuid'] ],
        [heading:'Type', property:'type?.value', sort:'type'],
        [heading:'Status', property:'statusText'],
        [heading:'Start Time', property:'startTime',sort:'startTime'],
        [heading:'End Time', property:'endTime',sort:'endTime'],
      ]
    ]
  ],

]

possible_date_formats = [
    new SimpleDateFormat('yyyy/MM/dd'),
    new SimpleDateFormat('yyyy-MM-dd'),
    new SimpleDateFormat('dd/MM/yyyy'),
    new SimpleDateFormat('dd.MM.yyyy'),
    new SimpleDateFormat('dd/MM/yy'),
    new SimpleDateFormat('yyyy/MM'),
    new SimpleDateFormat('yyyy')
];

isxn_formatter = { issn_string ->
      def result = issn_string
      def trimmed = (issn_string?:'').trim()
      if ( trimmed.length() == 8 ) {
        result = issn_string.substring(0,4)+"-"+issn_string.substring(4,8)
      }
      return result;
    }


identifiers = [
  'class_ones' : [
    'issn',
    'eissn',
    'doi',
    'isbn',
    'issnl',
    'zdb',
    'uri'
  ],

  // Class ones that need to be cross-checked. If an Identifier supplied as an ISSN,
  // is found against a title but as an eISSN we still treat this as a match
  'cross_checks' : [
    ['issn', 'eissn'],
    ['issn', 'issnl'],
    ['eissn', 'issn'],
    ['eissn', 'issnl'],
    ['issnl', 'issn'],
    ['issnl', 'eissn']
  ],

  formatters : [
    'issn' : isxn_formatter,
    'eissn' : isxn_formatter
  ]
]

