// locations to search for config files that get merged into the main config;
// config files can be ConfigSlurper scripts, Java properties files, or classes
// in the classpath in ConfigSlurper format

import com.k_int.TextUtils
import org.apache.log4j.DailyRollingFileAppender
import org.apache.log4j.RollingFileAppender
import grails.plugin.springsecurity.SpringSecurityUtils
import org.gokb.IngestService
import org.gokb.cred.KBComponent
import org.gokb.validation.types.*
import java.text.SimpleDateFormat
import java.util.concurrent.Executors

grails.config.locations = [ "classpath:${appName}-config.properties",
  "classpath:${appName}-config.groovy",
  "file:${userHome}/.grails/${appName}-config.properties",
  "file:${userHome}/.grails/${appName}-config.groovy"]

kbart2.mappings= [
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


possible_date_formats = [
    new SimpleDateFormat('yyyy/MM/dd'),
    new SimpleDateFormat('dd/MM/yyyy'),
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
  "class_ones" : [
    "issn",
    "eissn",
    "doi",
    "isbn",
    "issnl",
    "zdb"
  ],

  // Class ones that need to be cross-checked. If an Identifier supplied as an ISSN,
  // is found against a title but as an eISSN we still treat this as a match
  "cross_checks" : [
    ["issn", "eissn"],
    ["issn", "issnl"],
    ["eissn", "issn"],
    ["eissn", "issnl"],
    ["issnl", "issn"],
    ["issnl", "eissn"]
  ],

  formatters : [
    'issn' : isxn_formatter,
    'eissn' : isxn_formatter
  ]
]
// if (System.properties["${appName}.config.location"]) {
//    grails.config.locations << "file:" + System.properties["${appName}.config.location"]
// }

project_dir = new java.io.File(org.codehaus.groovy.grails.io.support.GrailsResourceUtils.GRAILS_APP_DIR + "/../project-files/").getCanonicalPath() + "/"

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
    grails.serverURL = "http://localhost:${ System.getProperty("server.port")?:'8080' }/${appName}"
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

log.info("Using log file location: ${logFile}")

// Also add it as config value too.
log_location = logFile

grails {
  fileViewer {
    locations = ["${logFile}".toString()]
    linesCount = 250
    areDoubleDotsAllowedInFilePath = false
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
    if (!base) {
      appender new RollingFileAppender(
          name: 'dailyAppender',
          fileName: (logFile),
          layout: pattern(conversionPattern:'%d [%t] %-5p %c{2} %x - %m%n')
      )
    }
  }

  root {
    if (!base) {
      error 'stdout', 'dailyAppender'
    } else {
      error 'stdout'
    }
  }

  error  'org.codehaus.groovy.grails.web.servlet',     // controllers
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

  environments {
    development {
      debug 'grails.app.controllers',
            'grails.app.service',
            'grails.app.services',
            'grails.app.domain',
            'grails.app.domains',
            'grails.app.tagLib',
            'grails.app.filters',
            'grails.app.conf',
            'grails.app.jobs',
            'com.k_int',
            'org.gokb.cred.RefdataCategory',
            'org.gokb.cred.TitleInstancePackagePlatform',
            'org.gokb.cred.Platform',
            'org.gokb.IntegrationController',
            'com.k_int.apis',
            'com.k_int.asset.pipeline.groovy',
            'asset.pipeline.less.compilers',
            'org.gokb.validation.types.CompareToTiDateField',
            'org.gokb.validation',
            'grails.app.domain.org.gokb.cred.CuratoryGroup'
    }
    test {
      debug 'grails.app.controllers',
            'grails.app.services',
            'grails.app.domain'
    }
  }

  // debug  'org.gokb.DomainClassExtender'

  // Enable Hibernate SQL logging with param values
  // trace 'org.hibernate.type'
  // debug 'org.hibernate.SQL'

}

// Added by the Spring Security Core plugin:
grails.plugin.springsecurity.userLookup.userDomainClassName = 'org.gokb.cred.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'org.gokb.cred.UserRole'
grails.plugin.springsecurity.authority.className = 'org.gokb.cred.Role'

//Enable Basic Auth Filter
grails.plugin.springsecurity.useBasicAuth = true
grails.plugin.springsecurity.basic.realmName = "GOKb API Authentication Required"
//Exclude normal controllers from basic auth filter. Just the JSON API is included
grails.plugin.springsecurity.filterChain.chainMap = [
  '/api/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
  '/packages/deposit': 'JOINED_FILTERS,-exceptionTranslationFilter',
  '/admin/bulkLoadUsers': 'JOINED_FILTERS,-exceptionTranslationFilter',
  '/**': 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter'
]

grails.plugin.springsecurity.controllerAnnotations.staticRules = [
  '/admin/**':                ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'],
  '/file/**':                 ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'],
  '/monitoring/**':           ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/':                        ['permitAll'],
  '/index':                   ['permitAll'],
  '/index.gsp':               ['permitAll'],
  '/register/**':             ['permitAll'],
  '/packages/**':             ['permitAll'],
  '/public/**':               ['permitAll'],
  '/globalSearch/**':         ['ROLE_USER'],
  '/home/**':                 ['ROLE_USER'],
  '/assets/**':               ['permitAll'],
  '/**/js/**':                ['permitAll'],
  '/**/css/**':               ['permitAll'],
  '/**/images/**':            ['permitAll'],
  '/**/favicon.ico':          ['permitAll'],
  '/api/find':                ['permitAll'],
  '/api/suggest':             ['permitAll'],
  '/api/esconfig':            ['permitAll'],
  '/api/capabilities':        ['permitAll'],
  '/api/downloadUpdate':      ['permitAll'],
  '/api/checkUpdate':         ['permitAll'],
  '/api/isUp':                ['permitAll'],
  '/api/userData':            ['permitAll'],
  '/fwk/**':                  ['ROLE_USER'],
  '/user/**':                 ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/role/**':                 ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/securityInfo/**':         ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/registrationCode/**':     ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/aclClass/**':             ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/aclSid/**':               ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/aclObjectIdentity/**':    ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/aclEntry/**':             ['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'],
  '/oai':                     ['permitAll'],
  '/oai/**':                  ['permitAll'],
  '/coreference/**':          ['permitAll']
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
validation.regex.kbartembargo = "^([RP]\\d+[DMY](;?))+\$"
validation.regex.kbartcoveragedepth = "^(\\Qfulltext\\E|\\Qselected articles\\E|\\Qabstracts\\E)\$"

class_one_cols = [:]
identifiers.class_ones.each { name ->
  class_one_cols[name] = "${IngestService.IDENTIFIER_PREFIX}${name}"
}
validation.rules = [
  "${IngestService.PUBLICATION_TITLE}" : [
    [ type: ColumnMissing     , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty      , severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  // All platforms
  "platform.*.*" : [
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: ColNameMustMatchRefdataValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        /platform\.([^\.]*)\..*/,
        "Platform.Roles"
      ]
    ]
  ],

  "${IngestService.HOST_PLATFORM_URL}" : [
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty  , severity: A_ValidationRule.SEVERITY_ERROR ],
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
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty  , severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: LookedUpValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [ org.gokb.cred.Platform ]
    ]
  ],

  "${IngestService.DATE_FIRST_PACKAGE_ISSUE}" : [
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty  , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: EnsureDate    ,severity: A_ValidationRule.SEVERITY_ERROR ],
    [ 
      type: CompareToTiDateField,
      severity: A_ValidationRule.SEVERITY_WARNING,
      args: [
        class_one_cols,
        "publishedFrom",
        CompareToTiDateField.GTE
      ]
    ]
  ],

  "${IngestService.DATE_LAST_PACKAGE_ISSUE}" : [
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: EnsureDate,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: ["value.gokbDateCeiling()"]
    ],
    [ 
      type: CompareToTiDateField,
      severity: A_ValidationRule.SEVERITY_WARNING,
      args: [
        class_one_cols,
        "publishedTo",
        CompareToTiDateField.LTE
      ]
    ]
  ],

  "${IngestService.PACKAGE_NAME}" : [
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty  , severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: LookedUpValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [ org.gokb.cred.Package ]
    ]
  ],

  "${IngestService.PUBLISHER_NAME}" : [
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [ type: CellNotEmpty  , severity: A_ValidationRule.SEVERITY_WARNING ],
    [
      type: LookedUpValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [ org.gokb.cred.Org ]
    ]
  ],

  "${IngestService.EMBARGO_INFO}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
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
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
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

  "${IngestService.TITLE_OA_STATUS}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: IsOneOfRefdata,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "TitleInstance.OAStatus"
      ]
    ]
  ],

  "${IngestService.TITLE_IMPRINT}" : [
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: LookedUpValue,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [ org.gokb.cred.Imprint ]
    ]
  ],

  "${IngestService.TIPP_PAYMENT}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: IsOneOfRefdata,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "TitleInstancePackagePlatform.PaymentType"
      ]
    ]
  ],

  "${IngestService.TIPP_STATUS}" : [
    [ type: ColumnMissing      , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
    [
      type: IsOneOfRefdata,
      severity: A_ValidationRule.SEVERITY_ERROR,
      args: [
        "${KBComponent.RD_STATUS}"
      ]
    ]
  ],

  // All Identifiers
  "${IngestService.IDENTIFIER_PREFIX}*" : [
    [ type: HasDuplicates , severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ]
  ],

  // ISSN
  "${IngestService.IDENTIFIER_PREFIX}issn" : [
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_ERROR ],
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
    [ type: ColumnMissing , severity: A_ValidationRule.SEVERITY_ERROR ],
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


  // Other columns we know about that need warnings if not present.
  "${IngestService.VOLUME_FIRST_PACKAGE_ISSUE}" : [
    [ type: ColumnMissing, severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
  ],

  "${IngestService.VOLUME_LAST_PACKAGE_ISSUE}" : [
    [ type: ColumnMissing, severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
  ],

  "${IngestService.NUMBER_FIRST_PACKAGE_ISSUE}" : [
    [ type: ColumnMissing, severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
  ],

  "${IngestService.NUMBER_LAST_PACKAGE_ISSUE}" : [
    [ type: ColumnMissing, severity: A_ValidationRule.SEVERITY_WARNING ],
    [ type: ColumnUnique      , severity: A_ValidationRule.SEVERITY_ERROR ],
  ],
]

auditLog {

  auditDomainClassName = "org.gokb.cred.AuditLogEvent"

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
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Type', property:'class.simpleName'],
        [heading:'Name/Title', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Status', property:'status.value',sort:'status'],
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
          baseClass:'org.gokb.cred.Org',
          prompt:'Provider',
          qparam:'qp_provider',
          placeholder:'Provider',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'provider'],
          hide:false
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Provider', property:'provider?.name'],
        [heading:'Name', property:'name',sort:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'Nominal Platform', property:'nominalPlatform?.name'],
        [heading:'Last Updated', property:'lastUpdated',sort:'lastUpdated'],
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Status', sort:'status', property:'status.value'],
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
        [heading:'Status', property:'status.value',sort:'status'],
      ]
    ]
  ],
  '1titles':[
    baseclass:'org.gokb.cred.TitleInstance',
    title:'Titles',
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
          type:'lookup',
          baseClass:'org.gokb.cred.Org',
          prompt:'Publisher',
          qparam:'qp_prov_id',
          placeholder:'Content Provider',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'pkg.provider'],
          hide:true
        ],
        [
          type:'lookup',
          baseClass:'org.gokb.cred.RefdataValue',
          filter1:'KBComponent.Status',
          prompt:'Status',
          qparam:'qp_status',
          placeholder:'Name or title of item',
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
        [heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r?.class?.name+\':\'+x.r?.id'],sort:'name' ],
        [heading:'Type', property:'class?.simpleName'],
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Status', sort:'status', property:'status.value'],
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
          prompt:'Platform ID',
          qparam:'qp_plat_id',
          placeholder:'Platform ID',
          contextTree:['ctxtp' : 'qry', 'comparator' : 'eq', 'prop' : 'hostPlatform.id', 'type' : 'java.lang.Long']
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
        [heading:'Title', property:'title?.name',link:[controller:'resource',action:'show',id:'x.r.title?.class.name+\':\'+x.r.title?.id'] ],
        [heading:'Status', property:'status.value'],
        [heading:'Package', property:'pkg?.name', link:[controller:'resource',action:'show',id:'x.r.pkg?.class.name+\':\'+x.r.pkg.id'] ],
        [heading:'Platform', property:'hostPlatform?.name', link:[controller:'resource',action:'show',id:'x.r.hostPlatform?.class?.name+\':\'+x.r.hostPlatform?.id'] ],
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
          type:'lookup',
          baseClass:'org.gokb.refine.RefineProject',
          prompt:'Project',
          qparam:'qp_project',
          placeholder:'Project',
          contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'refineProject']
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
        [
          prompt:'Cause',
          qparam:'qp_cause',
          placeholder:'Cause',
          contextTree:['ctxtp':'qry', 'comparator' : 'like', 'prop':'descriptionOfCause']
        ]
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
        [heading:'Project', property:'refineProject?.name', link:[controller:'resource', action:'show', id:'x.r.refineProject?.class?.name+\':\'+x.r.refineProject?.id']],
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
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Name', property:'value', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ],
        [heading:'RDF Datatype', property:'datatype?.value'],
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
        [
          type:'lookup',
          baseClass:'org.gokb.cred.Org',
          prompt:'Content Provider',
          qparam:'qp_prov_id',
          placeholder:'Content Provider',
          contextTree:[ 'ctxtp':'qry', 'comparator' : 'eq', 'prop':'pkg.provider'],
          hide:true
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'],sort:'name' ],
        [heading:'Status', property:'status.value',sort:'status'],
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
          hide:true
        ],
      ],
      qbeGlobals:[
        ['ctxtp':'filter', 'prop':'status', 'comparator' : 'eq', 'value':'Current', 'negate' : false, 'prompt':'Only Current',
         'qparam':'qp_onlyCurrent', 'default':'on', 'cat':'KBComponent.Status', 'type': 'java.lang.Object']
      ],
      qbeResults:[
        [heading:'Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'],sort:'name' ],
        [heading:'Status', property:'status.value',sort:'status'],
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
        [heading:'Status', property:'status.value',sort:'status'],
      ]
    ]
  ],
  'UserWatchedComponents':[
    baseclass:'org.gokb.cred.ComponentWatch',
    title:'My Components',
    group:'Tertiary',
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
        [heading:'Type', property:'component.getNiceName()'],
        [heading:'Last Update on', property:'component.lastUpdated'],
        [heading:'Last Update by', property:'component.lastUpdatedBy?.displayName'],
        [heading:'Source', property:'component.lastUpdateComment']
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

]


// Types: staticgsp: under views/templates, dyngsp: in database, dynamic:full dynamic generation, other...
globalDisplayTemplates = [
  'org.gokb.cred.AdditionalPropertyDefinition': [ type:'staticgsp', rendername:'addpropdef' ],
  'org.gokb.cred.Package': [ type:'staticgsp', rendername:'package' ],
  'org.gokb.cred.Org': [ type:'staticgsp', rendername:'org' ],
  'org.gokb.cred.Platform': [ type:'staticgsp', rendername:'platform' ],
  'org.gokb.cred.TitleInstance': [ type:'staticgsp', rendername:'title' ],
  'org.gokb.cred.BookInstance': [ type:'staticgsp', rendername:'book' ],
  'org.gokb.cred.JournalInstance': [ type:'staticgsp', rendername:'journal' ],
  'org.gokb.cred.TitleInstancePackagePlatform': [ type:'staticgsp', rendername:'tipp' ],
  'org.gokb.refine.Rule': [ type:'staticgsp', rendername:'rule' ],
  'org.gokb.refine.RefineProject': [ type:'staticgsp', rendername:'project' ],
  'org.gokb.cred.RefdataCategory': [ type:'staticgsp', rendername:'rdc' ],
  'org.gokb.cred.ReviewRequest': [ type:'staticgsp', rendername:'revreq' ],
  'org.gokb.cred.Office': [ type:'staticgsp', rendername:'office' ],
  'org.gokb.cred.CuratoryGroup': [ type:'staticgsp', rendername:'curatory_group' ],
  'org.gokb.cred.License': [ type:'staticgsp', rendername:'license' ],
  'org.gokb.cred.User': [ type:'staticgsp', rendername:'user' ],
  'org.gokb.cred.Source': [ type:'staticgsp', rendername:'source' ],
  'org.gokb.cred.DataFile': [ type:'staticgsp', rendername:'datafile' ],
  'org.gokb.cred.KBDomainInfo': [ type:'staticgsp', rendername:'domainInfo' ],
  'org.gokb.cred.Imprint': [ type:'staticgsp', rendername:'imprint' ],
  'org.gokb.cred.IdentifierNamespace': [ type:'staticgsp', rendername:'identifier_namespace' ],
  'org.gokb.cred.Macro': [ type:'staticgsp', rendername:'macro' ],
  'org.gokb.cred.DSCategory': [ type:'staticgsp', rendername:'ds_category' ],
  'org.gokb.cred.DSCriterion': [ type:'staticgsp', rendername:'ds_criterion' ],
  'org.gokb.cred.Subject': [ type:'staticgsp', rendername:'subject' ],
  'org.gokb.cred.Person': [ type:'staticgsp', rendername:'person' ],
  'org.gokb.cred.UserOrganisation': [ type:'staticgsp', rendername:'user_org' ],
  'org.gokb.cred.Folder': [ type:'staticgsp', rendername:'folder' ],
  'org.gokb.cred.Work': [ type:'staticgsp', rendername:'work' ],
]

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
// The following 2 entries make the app use basic auth by default
grails.plugin.springsecurity.useBasicAuth = true
grails.plugin.springsecurity.basic.realmName = "gokb"

// This stanza then says everything should use form apart from /api
// More info: http://stackoverflow.com/questions/7065089/how-to-configure-grails-spring-authentication-scheme-per-url
grails.plugin.springsecurity.filterChain.chainMap = [
  '/integration/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
  '/api/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
  '/packages/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
  '/**': 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter'
  // '/soap/deposit': 'JOINED_FILTERS,-exceptionTranslationFilter',
  // '/rest/**': 'JOINED_FILTERS,-exceptionTranslationFilter'
  // '/rest/**': 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter'

]

cosine.good_threshold = 0.75

extensionDownloadUrl = 'https://github.com/k-int/gokb-phase1/wiki/GOKb-Refine-Extensions'

grails.converters.json.circular.reference.behaviour = 'INSERT_NULL'

a
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
grails.assets.less.compiler = 'less4j'
grails.assets.excludes = ["**/*.less"]
grails.assets.includes = ["gokb/themes/**/theme.less", "jquery/*.js"]


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
  'indices'     : 'gokb',
  'types'       : 'component',
  'typingField' : 'componentType',
  'port'        : 9300
]

searchApi = [
  'path'        : '/',
  'indices'     : 'gokb',
  'types'       : 'component',
  'typingField' : 'componentType',
  'port'        : 9200
]

concurrency.pools = [
  "smallJobs" : [
    type: 'SingleThreadExecutor'
  ]
]

beans {
  executorService {
    executor = Executors.newFixedThreadPool(100)
  }
}

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


// Added by the Audit-Logging plugin:
// auditLog.auditDomainClassName = 'org.gokb.cred.AuditLogEvent'

