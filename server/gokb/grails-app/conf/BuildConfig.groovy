grails.servlet.version = "2.5" // Change depending on target container compliance (2.5 or 3.0)
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.6
grails.project.source.level = 1.6

//grails.project.war.file = "target/${appName}-${appVersion}.war"
//grails.project.dependency.resolver = "maven"
// grails.project.dependency.resolver = "maven"

//switch ("${System.getProperty('grails.env')}") {
//  case "development":
//    if (new File("/${basedir}/src/templates/war/web_dev.xml").exists()) {
//        grails.config.base.webXml = "file:${basedir}/src/templates/war/web_dev.xml"
//    }
//    break;
//  default:
//    if (new File("/${basedir}/src/templates/war/web.xml").exists()) {
//        grails.config.base.webXml = "file:${basedir}/src/templates/war/web.xml"
//    }
//    break;
// }

grails.project.dependency.resolver = "maven"


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
        
        // Custom repo that points to the public nexus repo. Used for elastic search client as there are no "official" ones.
        mavenRepo "http://repo.spring.io/milestone/"
        mavenRepo "http://nexus.k-int.com/content/repositories/releases"

        // uncomment these (or add new ones) to enable remote dependency resolution from public Maven repositories
        //mavenRepo "http://snapshots.repository.codehaus.org"
        //mavenRepo "http://repository.codehaus.org"
        //mavenRepo "http://download.java.net/maven/2/"
        //mavenRepo "http://repository.jboss.com/maven2/"
        mavenRepo "http://central.maven.org/maven2/"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        runtime 'mysql:mysql-connector-java:5.1.32'
        // runtime "postgresql:postgresql:8.3-603.jdbc3"
        // To allow us to un-tgz uploaded data files
        runtime 'org.apache.commons:commons-compress:1.4.1'
        runtime 'org.apache.tika:tika-core:1.4'
        runtime 'xalan:xalan:2.7.1'
        runtime 'org.elasticsearch:elasticsearch:1.3.2'
        runtime 'org.elasticsearch:elasticsearch-client-groovy:1.3.2'
        runtime 'net.sf.opencsv:opencsv:2.0'
        
        compile 'com.github.sommeri:less4j:1.8.2'
    }

    plugins {
      
      
      /* Grails 2.4 Upgrade */
      build ':tomcat:7.0.54' // plugins for the compile step compile
      
      // plugins for the compile step
//      compile ':scaffolding:2.1.0'
      compile ':cache:1.1.3'
      
//      compile ":spring-security-core:2.0-RC4"
//      compile ":spring-security-acl:2.0-RC1"
//      compile ":spring-security-ui:1.0-RC2"
      
      compile ':asset-pipeline:1.9.9'
      
      // Allows the use of groovy code in css and js files by suffixing with '-gtpl'.
      // Injects grailsApplication and config for easy access in your files.
//      compile ":groovy-template-grails-asset-pipeline:0.4"
//      compile ":groovy-asset-pipeline:1.2"
      
      // LESS compiler
      compile ":less-asset-pipeline:1.11.0", {
        excludes 'less4j'
      }
      
      runtime ':hibernate:3.6.10.2'
      // runtime ':hibernate:3.6.10.14' - this pukes forme
      runtime ':database-migration:1.4.0'
      
      /*************************************/
      
//      runtime ':hibernate:3.6.10.2'
      runtime ":jquery:1.11.1"
      runtime ':jquery-ui:1.10.3'

      // Uncomment these (or add new ones) to enable additional resources capabilities
      //runtime ":zipped-resources:1.0"
      //runtime ":cached-resources:1.0"
      //runtime ":yui-minify-resources:0.1.4"
//      build ':tomcat:7.0.40.1'

//      runtime ":database-migration:1.3.3"
      
//      compile ':cache:1.0.1'
  
      // Joda time to handle the ISO dates.
      compile ":joda-time:1.4"

      compile ":spring-security-core:1.2.7.3"
      compile ":spring-security-ui:0.2"
      compile ":spring-security-acl:1.1.1"

      compile ':mail:1.0.1', {
         excludes 'spring-test'
      }
      
      // Font awesome for font based icons.
      compile ":font-awesome-resources:4.2.0.0"
      
      // Job scheduler plugin.
      compile ":quartz:1.0.1"

      // II: Added.. Groping around in the dark a bit..
      // compile ":compass-sass:0.7" - OK this causes an exception
      
      /** Moved plugins from the properties file to here **/
      compile ':audit-logging:0.5.4' // SO: Tried upgrading to 0.5.5.3, but this caused a null pointer to be thrown.
      compile ':executor:0.3'
      compile ':famfamfam:1.0.1'
      compile ':rest:0.7'
      // compile ':twitter-bootstrap:2.3.2'
      compile ":twitter-bootstrap:3.2.0.2"
    }
}
