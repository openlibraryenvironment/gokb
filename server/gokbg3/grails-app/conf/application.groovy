// Added by the Spring Security Core plugin:
grails.plugin.springsecurity.userLookup.userDomainClassName = 'org.gokb.cred.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'org.gokb.cred.UserRole'
grails.plugin.springsecurity.authority.className = 'org.gokb.cred.Role'

//Enable Basic Auth Filter
grails.plugin.springsecurity.useBasicAuth = true
grails.plugin.springsecurity.basic.realmName = "GOKb API Authentication Required"
//Exclude normal controllers from basic auth filter. Just the JSON API is included

grails.plugin.springsecurity.filterChain.chainMap = [
        [pattern: '/assets/**',           filters: 'none'],
        [pattern: '/**/js/**',            filters: 'none'],
        [pattern: '/**/css/**',           filters: 'none'],
        [pattern: '/**/images/**',        filters: 'none'],
        [pattern: '/**/favicon.ico',      filters: 'none'],
        [pattern: '/error',               filters: 'none'],
        [pattern: '/api/**',              filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/packages/deposit',    filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/admin/bulkLoadUsers', filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
        [pattern: '/**',                  filters: 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter'],
]

grails.plugin.springsecurity.controllerAnnotations.staticRules = [
  [pattern: '/admin/**',                access: ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/file/**',                 access: ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/monitoring/**',           access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/',                        access: ['permitAll']],
  [pattern: '/index',                   access: ['permitAll']],
  [pattern: '/index.gsp',               access: ['permitAll']],
  [pattern: '/register/**',             access: ['permitAll']],
  [pattern: '/packages/**',             access: ['permitAll']],
  [pattern: '/public/**',               access: ['permitAll']],
  [pattern: '/globalSearch/**',         access: ['ROLE_USER']],
  [pattern: '/home/**',                 access: ['ROLE_USER']],
  [pattern: '/assets/**',               access: ['permitAll']],
  [pattern: '/**/js/**',                access: ['permitAll']],
  [pattern: '/**/css/**',               access: ['permitAll']],
  [pattern: '/**/images/**',            access: ['permitAll']],
  [pattern: '/**/favicon.ico',          access: ['permitAll']],
  [pattern: '/api/find',                access: ['permitAll']],
  [pattern: '/api/suggest',             access: ['permitAll']],
  [pattern: '/api/esconfig',            access: ['permitAll']],
  [pattern: '/api/capabilities',        access: ['permitAll']],
  [pattern: '/api/downloadUpdate',      access: ['permitAll']],
  [pattern: '/api/checkUpdate',         access: ['permitAll']],
  [pattern: '/api/isUp',                access: ['permitAll']],
  [pattern: '/api/userData',            access: ['permitAll']],
  [pattern: '/fwk/**',                  access: ['ROLE_USER']],
  [pattern: '/user/**',                 access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/role/**',                 access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/securityInfo/**',         access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/registrationCode/**',     access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/aclClass/**',             access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/aclSid/**',               access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/aclObjectIdentity/**',    access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/aclEntry/**',             access: ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY']],
  [pattern: '/oai',                     access: ['permitAll']],
  [pattern: '/oai/**',                  access: ['permitAll']],
  [pattern: '/coreference/**',          access: ['permitAll']]
]


appDefaultPrefs {
  globalDateFormat='dd MMMM yyyy'
}
