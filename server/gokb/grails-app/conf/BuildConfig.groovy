grails.servlet.version = "2.5" // Change depending on target container compliance (2.5 or 3.0)
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.6
grails.project.source.level = 1.6

//grails.project.war.file = "target/${appName}-${appVersion}.war"
//grails.project.dependency.resolver = "maven"
// grails.project.dependency.resolver = "maven"

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

        // uncomment these (or add new ones) to enable remote dependency resolution from public Maven repositories
        //mavenRepo "http://snapshots.repository.codehaus.org"
        //mavenRepo "http://repository.codehaus.org"
        //mavenRepo "http://download.java.net/maven/2/"
        //mavenRepo "http://repository.jboss.com/maven2/"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        runtime 'mysql:mysql-connector-java:5.1.25'
        // To allow us to un-tgz uploaded data files
        runtime 'org.apache.commons:commons-compress:1.4.1'
        runtime 'org.apache.tika:tika-core:1.4'
        runtime 'xalan:xalan:2.7.1'
        runtime 'org.elasticsearch:elasticsearch-lang-groovy:1.4.0'
    }

    plugins {
        runtime ':hibernate:3.6.10.2'
        runtime ":jquery:1.8.3"
        runtime ":resources:1.2"
        runtime ':gsp-resources:0.4.4'

        // Uncomment these (or add new ones) to enable additional resources capabilities
        //runtime ":zipped-resources:1.0"
        //runtime ":cached-resources:1.0"
        //runtime ":yui-minify-resources:0.1.4"

        build ':tomcat:7.0.40.1'

        runtime ":database-migration:1.3.3"

        compile ':cache:1.0.1'
		
      	// Joda time to handle the ISO dates.
      	compile ":joda-time:1.4"

        compile ":spring-security-core:1.2.7.3"
        compile ":spring-security-ui:0.2"
        compile ":spring-security-acl:1.1.1"

        compile ':mail:1.0.1', {
           excludes 'spring-test'
        }
        
        // Font awesome for font based icons.
        compile ":font-awesome-resources:3.2.1"
    }
}
