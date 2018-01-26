grails.servlet.version = "3.0" // Change depending on target container compliance (2.5 or 3.0)
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.6
grails.project.source.level = 1.6

//grails.project.war.file = "target/${appName}-${appVersion}.war"
//grails.project.dependency.resolver = "maven"
// grails.project.dependency.resolver = "maven"

switch ("${System.getProperty('grails.env')}") {
  case "development":
  case "test":
    if (new File("/${basedir}/src/templates/war/web_dev.xml").exists()) {
        grails.config.base.webXml = "file:${basedir}/src/templates/war/web_dev.xml"
    }
    break;
  default:
    if (new File("/${basedir}/src/templates/war/web.xml").exists()) {
        grails.config.base.webXml = "file:${basedir}/src/templates/war/web.xml"
    }
    break;
}

grails.project.dependency.resolver = "maven"

def gebVersion = "0.9.3"
// def seleniumVersion = "2.53.0"
def seleniumVersion = "2.53.1"
def seleniumHtmlunitDriverVersion = "2.52.0"

grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // specify dependency exclusions here; for example, uncomment this to disable ehcache:
        // excludes 'ehcache'
    }
    log "error" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    checksums true // Whether to verify checksums on resolve

    repositories {
        inherits true // Whether to inherit repository definitions from plugins
        grailsPlugins()
        grailsHome()
        grailsCentral()
        mavenLocal()
        mavenCentral()
        mavenRepo "http://central.maven.org/maven2/"
        mavenRepo "http://repo.spring.io/milestone/"
        mavenRepo "http://nexus.k-int.com/content/repositories/releases"

        // uncomment these (or add new ones) to enable remote dependency resolution from public Maven repositories
        //mavenRepo "http://snapshots.repository.codehaus.org"
        //mavenRepo "http://repository.codehaus.org"
        //mavenRepo "http://download.java.net/maven/2/"
        //mavenRepo "http://repository.jboss.com/maven2/"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        // r untime 'mysql:mysql-connector-java:5.1.38'
        // runtime 'org.mariadb.jdbc:mariadb-java-client:1.4.5'
        runtime  group: 'org.postgresql', name: 'postgresql', version: '9.4-1206-jdbc4'
        // To allow us to un-tgz uploaded data files
        runtime 'org.apache.commons:commons-compress:1.9'
        // runtime 'commons-collections:commons-collections:3.2.2'
        // runtime 'org.apache.commons:commons-collections4:4.1'
        runtime 'org.apache.tika:tika-core:1.17'
        
        // Must get parsers as well as core or we can only detect generic types.
        runtime 'org.apache.tika:tika-parsers:1.17'
        

        compile 'org.elasticsearch:elasticsearch:2.4.6'
        runtime ('org.elasticsearch:elasticsearch-groovy:2.1.2') {
          excludes "org.codehaus.groovy:groovy-all"
        }
        runtime 'com.vividsolutions:jts:1.13'

        // runtime ('org.elasticsearch:elasticsearch:1.7.1')
        // runtime ('org.elasticsearch:elasticsearch-groovy:1.7.0') {
        //     excludes "org.codehaus.groovy:groovy-all:2.4.3"
        // }

        runtime 'xalan:xalan:2.7.1'
        runtime 'net.sf.opencsv:opencsv:2.0'
        
        // Gant. Matched the version that ships with grails bootstrap in Grails 2.3.11
        runtime 'org.codehaus.gant:gant_groovy1.8:1.9.10'
        
        //compile 'com.github.sommeri:less4j:1.8.2'
        
        compile 'org.ajoberstar:grgit:0.2.3' // 0.3.0 is Groovy >=2.3 and breaks for me.
        build 'org.apache.httpcomponents:httpcore:4.4.4'

        test("org.seleniumhq.selenium:selenium-htmlunit-driver:$seleniumHtmlunitDriverVersion")
        // test("org.seleniumhq.selenium:selenium-htmlunit-driver:$seleniumHtmlunitDriverVersion") {
        //     excludes 'xml-apis', 'commons-collections'
        // }
        test "org.seleniumhq.selenium:selenium-firefox-driver:$seleniumVersion"
        test "org.seleniumhq.selenium:selenium-support:$seleniumVersion"

        test "org.spockframework:spock-grails-support:0.7-groovy-2.0"
        test "org.gebish:geb-spock:$gebVersion"

    }

    plugins {
      
      compile ":spring-security-core:2.0.0"
      compile ":spring-security-ui:1.0-RC3"
      compile ":spring-security-acl:2.0.1"

      runtime( ":cors:1.1.8" ) { 
        excludes 'spring-security-core' 
        excludes 'spring-security-web' 
      }
      
      compile ":grails-melody:1.52.0"

      /* Grails 2.4 Upgrade */
      build ':tomcat:7.0.55' // plugins for the compile step compile
      
      // plugins for the compile step
      compile ':cache:1.1.8'
      
      compile ':asset-pipeline:2.9.1'
      
      // Allows the use of groovy code in css and js files by suffixing with '-gtpl'.
      // Injects grailsApplication and config for easy access in your files.
      
      // LESS compiler
      //compile ":less-asset-pipeline:2.9.1", {
      //  excludes 'less4j'
      //}
      compile ":less-asset-pipeline:2.9.1"
      
      runtime ':hibernate:3.6.10.19'  // II trying .19 over .16
      runtime ':database-migration:1.4.1-SNAPSHOT'  // II: updated here due to ehCache problem - see https://github.com/grails-plugins/grails-spring-security-core/issues/152
      
      compile ":file-viewer:0.3"
      
      // This should give us events
      compile "org.grails.plugins:platform-core:1.0.0"

      /*************************************/
      
      runtime ":jquery:1.11.1"
      runtime ':jquery-ui:1.10.4'

      // Uncomment these (or add new ones) to enable additional resources capabilities
      //runtime ":zipped-resources:1.0"
      //runtime ":cached-resources:1.0"
      //runtime ":yui-minify-resources:0.1.4"

  
      // Joda time to handle the ISO dates.
      compile ":joda-time:1.5"


      compile ':mail:1.0.7', {
         excludes 'spring-test'
      }
      
      // Font awesome for font based icons.
      compile ":font-awesome-resources:4.2.0.0"
      
      // Job scheduler plugin.
      compile ":quartz:1.0.1"

      /** Moved plugins from the properties file to here **/
      compile ':audit-logging:1.1.3'
      compile ':executor:0.3'
      compile ':famfamfam:1.0.1'
      compile ':rest:0.8'
      compile ":twitter-bootstrap:3.3.4"

      compile ":gson:1.1.4"
      
      // Advanced caching control.
      compile ":cache-headers:1.1.7"

      // compile ":profiler:0.5"

      // Spock is included by default now - adding the dependency runs spock tests twice, which is nice
      // test ":spock:0.7", {
      //   exclude "spock-grails-support"
      // }
      test ":geb:$gebVersion"

    }
}
