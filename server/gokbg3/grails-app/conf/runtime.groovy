// locations to search for config files that get merged into the main config;
// config files can be ConfigSlurper scripts, Java properties files, or classes
// in the classpath in ConfigSlurper format

import com.k_int.TextUtils

kbart2.mappings= [
  // Digital Archival Collections Ingest Format
    DAC : [
               quoteChar:'"',
               charset:'UTF-8',
               defaultTypeName:'org.gokb.cred.OtherInstance',
               identifierMap:[ 'online_identifier':'uri'],
               defaultMedium:'Other',
               rules:[
                [field: 'notes', kbart:'notes'],
                [field: 'online_identifier', kbart: 'online_identifier'],
                [field: 'publication_title', kbart: 'publication_title'],
                [field: 'publisher', kbart:'publisher'],
                [field: 'title_url', kbart:'title_url']
               ]
    ],
    ingram : [
               // defaultType:org.gokb.cred.BookInstance.class,
               defaultTypeName:'org.gokb.cred.BookInstance',
               identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn', ],
               defaultMedium:'Book',
               rules:[
                [field: 'Title', kbart: 'publication_title'],
                [field: 'Title ID', kbart: 'print_identifier'],
                [field: 'Authors', kbart: 'first_author', separator: ';', additional: 'additional_authors'],
                [field: 'Hardcover EAN ISBN', additional: 'additional_isbns'],  //another ISBN
                [field: 'Paper EAN ISBN', additional: 'additional_isbns'],   //another ISBN
                [field: 'Pub EAN ISBN', kbart: 'online_identifier'],
                [field: 'MIL EAN ISBN', additional: 'additional_isbns'],  //another ISBN
                [field: 'Publisher', kbart: 'publisher_name'],
                [field: 'URL', kbart: 'title_url'],
                [field: 'PubDate', kbart: 'date_monograph_published_online'],
              ]
         ],
    askews : [
               defaultTypeName:'org.gokb.cred.BookInstance',
               // defaultType:org.gokb.cred.BookInstance.class,
               identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn', ],
               quoteChar:'"',
               defaultMedium:'Book',
               rules:[
                [field: 'publication_title', kbart: 'publication_title'],
                [field: 'print_identifier', kbart: 'print_identifier'],
                [field: 'online_identifier', kbart: 'online_identifier'],
                [field: 'Authors', kbart: 'first_author', separator: ';', additional: 'additional_authors'],
                [field: 'PubDate', kbart: 'date_monograph_published_online'],
                [field: 'title_image', kbart: 'title_image'],
              ]
         ],
    ybp : [
               defaultTypeName:'org.gokb.cred.BookInstance',
               // defaultType:org.gokb.cred.BookInstance.class,
               identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn', ],
               defaultMedium:'Book',
               quoteChar:'"',
               charset:'ISO-8859-1',
               rules:[
                [field: 'Title #', kbart: 'title_id'],
                [field: 'Title', kbart: 'publication_title'],
                [field: 'ISBN', kbart: 'online_identifier'],
                [field: 'Author', kbart: 'first_author'],
                [field: 'Editor', kbart: 'first_editor'],
                [field: 'Publisher', kbart: 'publisher_name'],
                [field: 'Pub_Year', kbart: 'date_monograph_published_online'],
                [field: 'Edition', kbart: 'monograph_edition'],
                [field: 'LC/NLM/Dewey_Class', additional: 'subjects']
              ]
     ],
     cufts:[
               rules:[
                [field: 'title', kbart: 'publication_title'],
                [field: 'issn', kbart: 'print_identifier'],
                [field: 'e_issn', kbart: 'online_identifier'],
                [field: 'ft_start_date', kbart: 'date_first_issue_online'],
                [field: 'ft_end_date', kbart: 'date_last_issue_online'],
                //[field: 'cit_start_date', kbart: ''],
                //[field: 'cit_end_date', kbart: ''],
                [field: 'vol_ft_start', kbart: 'num_first_vol_online'],
                [field: 'vol_ft_end', kbart: 'num_last_vol_online'],
                [field: 'iss_ft_start', kbart: 'num_first_issue_online'],
                [field: 'iss_ft_end', kbart: 'num_last_issue_online'],
                [field: 'db_identifier', kbart: 'title_id'],
                [field: 'journal_url', kbart: 'title_url'],
                [field: 'embargo_days', kbart: 'embargo_info'],
                [field: 'embargo_months', kbart: 'embargo_info'],
                [field: 'publisher', kbart: 'publisher_name'],
                //[field: 'abbreviation', kbart: ''],
                //[field: 'current_months', kbart: ''],
              ]
     ],
     cuftsBJM:[
               rules:[
                [field: 'title', kbart: 'publication_title'],
                [field: 'issn', kbart: 'print_identifier'],
                [field: 'e_issn', kbart: 'online_identifier'],
                [field: 'ft_start_date', kbart: 'date_first_issue_online'],
                [field: 'ft_end_date', kbart: 'date_last_issue_online'],
                //[field: 'cit_start_date', kbart: ''],
                //[field: 'cit_end_date', kbart: ''],
                [field: 'vol_ft_start', kbart: 'num_first_vol_online'],
                [field: 'vol_ft_end', kbart: 'num_last_vol_online'],
                [field: 'iss_ft_start', kbart: 'num_first_issue_online'],
                [field: 'iss_ft_end', kbart: 'num_last_issue_online'],
                [field: 'db_identifier', kbart: 'title_id'],
                [field: 'journal_url', kbart: 'title_url'],
                [field: 'embargo_days', kbart: 'embargo_info'],
                [field: 'embargo_months', kbart: 'embargo_info'],
                [field: 'publisher', kbart: 'publisher_name'],
              ]
     ],
     ebsco:[
              quoteChar:'"',
              separator:',',
              charset:'UTF-8',
              // doDistanceMatch=true, // To enable full string title matching
              rules:[
                [field: 'publication_title', kbart: 'publication_title'],
                [field: 'print_identifier', kbart: 'print_identifier'],
                [field: 'online_identifier', kbart: 'online_identifier'],
                [field: 'date_first_issue_online', kbart: 'date_first_issue_online'],
                [field: 'date_last_issue_online', kbart: 'date_last_issue_online'],
                [field: 'num_first_vol_online', kbart: 'num_first_vol_online'],
                [field: 'num_last_vol_online', kbart: 'num_last_vol_online'],
                [field: 'num_first_issue_online', kbart: 'num_first_issue_online'],
                [field: 'num_last_issue_online', kbart: 'num_last_issue_online'],
                [field: 'title_id', kbart: 'title_id'],
                [field: 'title_url', kbart: 'title_url'],
                [field: 'embargo_info', kbart: 'embargo_info'],
                [field: 'publisher_name', kbart: 'publisher_name'],
                [field:'first_author', kbart:"first_author"],
                [field:'coverage_depth', kbart:"coverage_depth"],  // GOKb coverageDepth is refdata -- Investigating
                [field:'notes', kbart:"notes"],
                [field:'publisher_name', kbart:"publisher_name"],
                [field:'publication_type', kbart:"publication_type"],
                [field:'date_monograph_published_print', kbart:"date_monograph_published_print"],
                [field:'date_monograph_published_online', kbart:"date_monograph_published_online"],
                [field:'monograph_volume', kbart:"monograph_volume"],
                [field:'monograph_edition', kbart:"monograph_edition"],
                [field:'first_editor', kbart:"first_editor"],
                [field:'parent_publication_title_id', kbart:"parent_publication_title_id"],
                [field:'preceding_publication_title_id', kbart:"preceding_publication_title_id"],
                [field:'access_type', kbart:"access_type"],
                [field:'package_name', kbart:"package_name"],
                // [field:'', kbart:"package_ID"],
                // [field:'', kbart:"ProviderID"],
                // [field:'', kbart:"EBSCO_Resource_Type"],
                // [field:'', kbart:"EBSCO_Resource_TypeID"],
              ]
     ],
     elsevier:[
              quoteChar:'"',
              // separator:',',
              charset:'UTF-8',
              defaultTypeName:'org.gokb.cred.BookInstance',
              identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
              defaultMedium:'Book',
              discriminatorColumn:'publication_type',
              polymorphicRows:[
                'Serial':[
                  identifierMap:[ 'print_identifier':'issn', 'online_identifier':'eissn' ],
                  defaultMedium:'Serial',
                  defaultTypeName:'org.gokb.cred.JournalInstance'
                 ],
                'Monograph':[
                  identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
                  defaultMedium:'Book',
                  defaultTypeName:'org.gokb.cred.BookInstance'
                ]
              ],
              // doDistanceMatch=true, // To enable full string title matching
              rules:[
                [field: 'publication_title', kbart: 'publication_title'],
                [field: 'print_identifier', kbart: 'print_identifier'],
                [field: 'online_identifier', kbart: 'online_identifier'],
                [field: 'date_first_issue_online', kbart: 'date_first_issue_online'],
                [field: 'date_last_issue_online', kbart: 'date_last_issue_online'],
                [field: 'num_first_vol_online', kbart: 'num_first_vol_online'],
                [field: 'num_last_vol_online', kbart: 'num_last_vol_online'],
                [field: 'num_first_issue_online', kbart: 'num_first_issue_online'],
                [field: 'num_last_issue_online', kbart: 'num_last_issue_online'],
                [field: 'title_id', kbart: 'title_id'],
                [field: 'title_url', kbart: 'title_url'],
                [field: 'embargo_info', kbart: 'embargo_info'],
                [field: 'publisher_name', kbart: 'publisher_name'],
                [field:'first_author', kbart:"first_author"],
                [field:'coverage_depth', kbart:"coverage_depth"],  // GOKb coverageDepth is refdata -- Investigating
                [field:'notes', kbart:"notes"],
                [field:'publisher_name', kbart:"publisher_name"],
                [field:'publication_type', kbart:"publication_type"],
                [field:'date_monograph_published_print', kbart:"date_monograph_published_print"],
                [field:'date_monograph_published_online', kbart:"date_monograph_published_online"],
                [field:'monograph_volume', kbart:"monograph_volume"],
                [field:'monograph_edition', kbart:"monograph_edition"],
                [field:'first_editor', kbart:"first_editor"],
                [field:'parent_publication_title_id', kbart:"parent_publication_title_id"],
                [field:'preceding_publication_title_id', kbart:"preceding_publication_title_id"],
                [field:'access_type', kbart:"access_type"],
                [field:'package_name', kbart:"package_name"],
                // [field:'', kbart:"package_ID"],
                // [field:'', kbart:"ProviderID"],
                // [field:'', kbart:"EBSCO_Resource_Type"],
                // [field:'', kbart:"EBSCO_Resource_TypeID"],
              ]
     ],
     'springer-kbart':[
              quoteChar:'\0',  // No quote char - some springer rows start with " for unknown reasons - CRITICALLY IMPORTANT!
              // separator:',',
              charset:'UTF-8',
              defaultTypeName:'org.gokb.cred.BookInstance',
              identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
              defaultMedium:'Book',
              discriminatorColumn:'publication_type',
              polymorphicRows:[
                'Serial':[
                  identifierMap:[ 'print_identifier':'issn', 'online_identifier':'eissn' ],
                  defaultMedium:'Serial',
                  defaultTypeName:'org.gokb.cred.JournalInstance'
                 ],
                'Monograph':[
                  identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
                  defaultMedium:'Book',
                  defaultTypeName:'org.gokb.cred.BookInstance'
                ]
              ],
              // doDistanceMatch=true, // To enable full string title matching
              rules:[
                [field: 'publication_title', kbart: 'publication_title'],
                [field: 'print_identifier', kbart: 'print_identifier'],
                [field: 'online_identifier', kbart: 'online_identifier'],
                [field: 'date_first_issue_online', kbart: 'date_first_issue_online'],
                [field: 'date_last_issue_online', kbart: 'date_last_issue_online'],
                [field: 'num_first_vol_online', kbart: 'num_first_vol_online'],
                [field: 'num_last_vol_online', kbart: 'num_last_vol_online'],
                [field: 'num_first_issue_online', kbart: 'num_first_issue_online'],
                [field: 'num_last_issue_online', kbart: 'num_last_issue_online'],
                [field: 'title_id', kbart: 'title_id'],
                [field: 'title_url', kbart: 'title_url'],
                [field: 'embargo_info', kbart: 'embargo_info'],
                [field: 'publisher_name', kbart: 'publisher_name'],
                [field:'first_author', kbart:"first_author"],
                [field:'coverage_depth', kbart:"coverage_depth"],  // GOKb coverageDepth is refdata -- Investigating
                [field:'notes', kbart:"notes"],
                [field:'publisher_name', kbart:"publisher_name"],
                [field:'publication_type', kbart:"publication_type"],
                [field:'date_monograph_published_print', kbart:"date_monograph_published_print"],
                [field:'date_monograph_published_online', kbart:"date_monograph_published_online"],
                [field:'monograph_volume', kbart:"monograph_volume"],
                [field:'monograph_edition', kbart:"monograph_edition"],
                [field:'first_editor', kbart:"first_editor"],
                [field:'parent_publication_title_id', kbart:"parent_publication_title_id"],
                [field:'preceding_publication_title_id', kbart:"preceding_publication_title_id"],
                [field:'access_type', kbart:"access_type"],
                [field:'package_name', kbart:"package_name"],
              ]
     ],
     'wiley-blackwell-kbart':[
              quoteChar:'\0',  // No quote char - some springer rows start with " for unknown reasons - CRITICALLY IMPORTANT!
              // separator:',', // tab is the default
              charset:'UTF-8',
              defaultTypeName:'org.gokb.cred.BookInstance',
              identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
              defaultMedium:'Book',
              discriminatorFunction: { rowdata ->
                def result = 'Monograph';
                if ( rowdata['title_url']?.contains('journal') ) {
                  result='Serial';
                }
                return result
              },
              polymorphicRows:[
                'Serial':[
                  identifierMap:[ 'print_identifier':'issn', 'online_identifier':'eissn' ],
                  defaultMedium:'Serial',
                  defaultTypeName:'org.gokb.cred.JournalInstance'
                 ],
                'Monograph':[
                  identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
                  defaultMedium:'Book',
                  defaultTypeName:'org.gokb.cred.BookInstance'
                ]
              ],
              // Wiley have form for adding titles using the new ISSN and the title of previous
              // journals in the history. If a normalised title comes in which is sufficiently
              // different to the name currently allocated to the jornal, assume it's a title
              // history case.
              "inconsistent_title_id_behaviour":"AddToTitleHistory",
              // doDistanceMatch=true, // To enable full string title matching
              rules:[
                [field: 'publication_title', kbart: 'publication_title'],
                [field: 'print_identifier', kbart: 'print_identifier'],
                [field: 'online_identifier', kbart: 'online_identifier'],
                [field: 'date_first_issue_online', kbart: 'date_first_issue_online'],
                [field: 'date_last_issue_online', kbart: 'date_last_issue_online'],
                [field: 'num_first_vol_online', kbart: 'num_first_vol_online'],
                [field: 'num_last_vol_online', kbart: 'num_last_vol_online'],
                [field: 'num_first_issue_online', kbart: 'num_first_issue_online'],
                [field: 'num_last_issue_online', kbart: 'num_last_issue_online'],
                [field: 'title_id', kbart: 'title_id'],
                [field: 'title_url', kbart: 'title_url'],
                [field: 'embargo_info', kbart: 'embargo_info'],
                [field: 'publisher_name', kbart: 'publisher_name'],
                [field:'first_author', kbart:"first_author"],
                [field:'coverage_depth', kbart:"coverage_depth"],  // GOKb coverageDepth is refdata -- Investigating
                [field:'notes', kbart:"notes"],
                [field:'publisher_name', kbart:"publisher_name"],
                [field:'publication_type', kbart:"publication_type"],
                [field:'date_monograph_published_print', kbart:"date_monograph_published_print"],
                [field:'date_monograph_published_online', kbart:"date_monograph_published_online"],
                [field:'monograph_volume', kbart:"monograph_volume"],
                [field:'monograph_edition', kbart:"monograph_edition"],
                [field:'first_editor', kbart:"first_editor"],
                [field:'parent_publication_title_id', kbart:"parent_publication_title_id"],
                [field:'preceding_publication_title_id', kbart:"preceding_publication_title_id"],
                [field:'access_type', kbart:"access_type"],
                [field:'package_name', kbart:"package_name"],
              ]
     ],

]

kbart2.personCategory='SPR'
kbart2.authorRole='Author'
kbart2.editorRole='Editor'

project_dir = new java.io.File(org.grails.io.support.GrailsResourceUtils.GRAILS_APP_DIR + "/../project-files/").getCanonicalPath() + "/"

refine_min_version = "3.0.0"

// ftupdate_enabled = true

// Config for the refine extension build process.
refine = [
  refineRepoURL           : "https://github.com/OpenRefine/OpenRefine.git",
  refineRepoBranch        : "master",
  refineRepoTagPattern    : /\Q2.6-rc.2\E/,
  refineRepoPath          : "gokb-build/refine",
  gokbRepoURL             : "https://github.com/k-int/gokb-phase1.git",
  gokbRepoBranch          : "release",
  gokbRepoTagPattern      : "\\QCLIENT_\\E(${TextUtils.VERSION_REGEX})",
  gokbRepoTestURL         : "https://github.com/k-int/gokb-phase1.git",
  gokbRepoTestBranch      : "test",
  gokbRepoTestTagPattern  : "\\QTEST_CLIENT_\\E(${TextUtils.VERSION_REGEX})",
  extensionRepoPath       : "gokb-build/extension",
  gokbExtensionPath       : "refine/extensions/gokb",
  gokbExtensionTarget     : "extensions/gokb/",
  refineBuildFile         : "build.xml",
  refineBuildTarget       : null,
  extensionBuildFile      : "build.xml",
  extensionBuildTarget    : "dist",
]

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

grails.plugins.twitterbootstrap.fixtaglib = true

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
  test {
    grails.serverURL = "http://localhost:$server.port/$appName"
  }
}

// Log directory/created in current working dir if tomcat var not found.
def logWatchFile

// First lets see if we have a log file present.
def base = System.getProperty("catalina.base")
if (base) {
   logWatchFile = new File ("${base}/logs/catalina.out")

   if (!logWatchFile.exists()) {

     // Need to create one in current context.
     base = false;
   }
}

if (!base) {
  logWatchFile = new File("logs/gokb.log")
}

// Log file variable.
def logFile = logWatchFile.canonicalPath

// Also add it as config value too.
log_location = logFile

grails {
  fileViewer {
    locations = ["${logFile}".toString()]
    linesCount = 250
    areDoubleDotsAllowedInFilePath = false
  }
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
validation.regex.eissn = "^\\d{4}\\-\\d{3}[\\dX]\$"
validation.regex.isbn = "^(97(8|9))?\\d{9}[\\dX]\$"
validation.regex.uri = "^(f|ht)tp(s?):\\/\\/([a-zA-Z\\d\\-\\.])+(:\\d{1,4})?(\\/[a-zA-Z\\d\\-\\._~\\/\\?\\#\\[\\]@\\!\\%\\:\\\$\\&'\\(\\)\\*\\+,;=]*)?\$"
validation.regex.date = "^[1-9][0-9]{3,3}\\-(0[1-9]|1[0-2])\\-(0[1-9]|[1-2][0-9]|3[0-1])\$"
validation.regex.kbartembargo = "^([RP]\\d+[DMY](;?))+\$"
validation.regex.kbartcoveragedepth = "^(\\Qfulltext\\E|\\Qselected articles\\E|\\Qabstracts\\E)\$"
validation.regex.zdb = "^\\d+\\-[\\dX]\$"

// class_one_cols = [:]
// identifiers.class_ones.each { name ->
//   class_one_cols[name] = "${IngestService.IDENTIFIER_PREFIX}${name}"
// }

grails.gorm.default.constraints = {
  '*'(nullable: true, blank:false)
}

grails.gorm.autoFlush=true

//grails.gorm.failOnError=true

permNames = [
  1 : [name:'Read', inst:org.springframework.security.acls.domain.BasePermission.READ],
  2 : [name:'Write', inst:org.springframework.security.acls.domain.BasePermission.WRITE],
  4 : [name:'Create', inst:org.springframework.security.acls.domain.BasePermission.CREATE],
  8 : [name:'Delete', inst:org.springframework.security.acls.domain.BasePermission.DELETE],
  16 : [name:'Administration', inst:org.springframework.security.acls.domain.BasePermission.ADMINISTRATION],
]

grails.plugin.springsecurity.ui.password.minLength = 6
grails.plugin.springsecurity.ui.password.maxLength = 64
grails.plugin.springsecurity.ui.password.validationRegex = '^.*$'

//configure register
grails.plugin.springsecurity.ui.register.emailFrom = "GOKb<no-reply@gokb.org>"
grails.plugin.springsecurity.ui.register.emailSubject = 'Welcome to GOKb'
grails.plugin.springsecurity.ui.register.defaultRoleNames = [
  "ROLE_USER"
]
grails.plugin.springsecurity.ui.register.postRegisterUrl = '/home/index'
// The following 2 entries make the app use basic auth by default
grails.plugin.springsecurity.useBasicAuth = true
grails.plugin.springsecurity.basic.realmName = "gokb"

cosine.good_threshold = 0.75

extensionDownloadUrl = 'https://github.com/k-int/gokb-phase1/wiki/GOKb-Refine-Extensions'

grails.converters.json.circular.reference.behaviour = 'INSERT_NULL'

/**
 * We need to disable springs password encoding as we handle this in our domain model.
 */
grails.plugin.springsecurity.ui.encodePassword = false

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

/** Less config **/
/** Config duplicated here and in build.groovy for alternate run paths */
grails.assets.less.compiler = 'less4j'
grails.assets.excludes = [ '**/_*.less', 'gokb/themes/*.less', 'gokb/themes/**/*.less', 'bootstrap/*.less', 'bootstrap/**/*.less' ]
grails.assets.includes = [ 'webfonts/*', 'gokb/themes/*/theme.less', 'bootstrap/bootstrap.less' ]
grails.assets.plugin."twitter-bootstrap".excludes = ["**/*.less"]
grails.assets.plugin."font-awesome-resources".excludes = ["**/*.less"]
grails.assets.plugin."jquery".excludes = ["**", "*.*"]
grails.assets.minifyJs = false

gokb.theme = "yeti"


waiting {
  timeout = 60
  retryInterval = 0.5
}

cache.headers.presets = [
  "none": false,
  "until_changed": [shared:true, validFor: (3600 * 12)] // cache content for 12 hours.
]

globalSearch = [
  'indices'     : ['gokbtitles', 'gokbtipps', 'gokborgs', 'gokbpackages', 'gokbplatforms'],
  'types'       : 'component',
  'typingField' : 'componentType',
  'port'        : 9300
]

searchApi = [
  'path'        : '/',
  'indices'     : ['gokbtitles', 'gokbtipps', 'gokborgs', 'gokbpackages', 'gokbplatforms'],
  'types'       : 'component',
  'typingField' : 'componentType',
  'port'        : 9200
]

//concurrency.pools = [
//  "smallJobs" : [
//    type: 'SingleThreadExecutor'
//  ]
//]

//beans {
//  executorService {
//    executor = Executors.newFixedThreadPool(100)
//  }
//}

// cors.headers = ['Access-Control-Allow-Origin': '*']
// 'Access-Control-Allow-Origin': 'http://xissn.worldcat.org'
//     'My-Custom-Header': 'some value'

// Uncomment and edit the following lines to start using Grails encoding & escaping improvements
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
//   filteringCodecForContentType {
//   //'text/html' = 'html'
//   }
 }
}

fileViewer.grails.views.gsp.codecs.expression = "none"

