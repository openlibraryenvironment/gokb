#!groovy

@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.2'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.2'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.5.2'),
  @Grab(group='commons-net', module='commons-net', version='3.5'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])


import javax.mail.*
import javax.mail.search.*
import static groovy.json.JsonOutput.*
import groovy.json.JsonSlurper
import com.gargoylesoftware.htmlunit.*
import groovyx.net.http.HTTPBuilder
import org.apache.http.entity.mime.content.StringBody
import groovyx.net.http.*
import org.apache.commons.net.ftp.*
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.URIBuilder
import static groovyx.net.http.ContentType.XML
import static groovyx.net.http.Method.GET
import java.util.Base64


String fileName = "${this.class.getSimpleName().replaceAll(/\_/, "-")}-cfg.json"
def cfg_file = new File("./${fileName}")

def config = null
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text)
}
else {
  println("No config found please supply authentication details.")
  config = [
    uploadUser: System.console().readLine ('Enter your username: ').toString(),
    uploadPass: System.console().readPassword ('Enter your password: ').toString()
  ]
  
  // Save to the file.
  cfg_file << toJson(config)
  
  println("Saved config file to ${fileName}")
}

def httpbuilder = new HTTPBuilder( 'http://localhost:8080' )
httpbuilder.auth.basic config.uploadUser, config.uploadPass


println("Pulling latest messages");
pullLatest(config, httpbuilder, cfg_file)
println("All done");

def pullLatest(config, httpbuilder, cfg_file) {
  importLicenses('http://gokb.openlibraryfoundation.org', httpbuilder, config, cfg_file);
}

def importLicenses(host, gokb, config, cfg_file) {
  def resumptionToken = config.resumptionToken
  def resourcesFromPage
  

  def moredata = true;

  while ( moredata ) {
    def first_resource = false;
    def ctr = 0;
    println("Request resources...");
    (resourcesFromPage, resumptionToken) = getResourcesFromGoKBByPage(gokbUrl(host, resumptionToken))
    println("Got resources, processing...");

    resourcesFromPage.each { gt ->
      ctr++
      if ( first_resource ) {
        // println(gt);
        first_resource = false;
      }
      else {
        // println(gt);
      }

      addToGoKB(true, gokb, gt)
      synchronized(this) {
        Thread.sleep(3000);
      }
    }

    if ( resumptionToken ) {
      println("Requesting another page...");
      moredata = true
      config.resumptionToken = resumptionToken
    }
    else {
      moredata = false;
      resumptionToken = null;
      config.resumptionToken = null;
    }

    println("Updating config - processed ${ctr} records");
    cfg_file.delete()
    cfg_file << toJson(config)

    synchronized(this) {
      println("Quick Sleep");
      Thread.sleep(4000);
    }
  }
}


private static getResourcesFromGoKBByPage(URL url) {
  println "Retrieving: ${url}"

  def http = new HTTPBuilder(url, XML)

  http.headers = [Accept: 'application/xml']

  def resources = []
  def resumptionToken = null
  def ctr = 0

  http.request(GET, XML) { req ->
    response.success = { resp, body ->
      resumptionToken = body?.ListRecords?.resumptionToken.text()
      
      resources = body?.ListRecords?.record.collect { r ->
        
        r = r.metadata.gokb
        
        // Construct each entry
        println("Record ${ctr++}")
        def resourceFieldMap = [:]
        
        // Add basic text properties.
        ['name','url','file','type', 'licensor', 'licensee', 
          'previous', 'successor', 'model'].each {
            def val
            if ((val = r."${it}".text())) resourceFieldMap[it] = val
        }
        
        // Summary statement.
        resourceFieldMap['summaryStatement'] = r.summaryStatement.yieldUnescaped.text()
        
        // Attatched files need special attention.
        resourceFieldMap['fileAttachments'] = r.fileAttachments.fileAttachment.collect { fa ->
          
          def fileMap = [:]
          ['guid','md5', 'uploadName', 'uploadMimeType', 'filesize', 'doctype'].each {
            def val
            if ((val = fa."${it}".text())) fileMap[it] = val
          }
          
          // Handle the actual file content by reading it as a byte[]
          fileMap['content'] = Base64.getEncoder().encodeToString (fa.content.yieldUnescaped.text().trim().replaceAll(/\<\!\[CDATA\[\[(.*)\]\]\]\>/, '$1').split(/\,\s*/).collect ({ String s ->
            s.toInteger().byteValue()
          }) as byte[])
          
          fileMap
        }
        
        // Curatory groups.
        resourceFieldMap['curatoryGroups'] = r.'curatoryGroups'.'group'
        
        resourceFieldMap
      } ?: []
      
      
    }

    response.error = { err ->
      println "OAI GET Failed http request"
      println(err)
    }
  }

  println("Fetched ${resources.size()} packages in oai page");
  [resources, resumptionToken]
}

private static URL gokbUrl(host, resumptionToken = null) {
  final path = '/gokb/oai/licenses', prefix = 'gokb'

  def qry = [verb: 'ListRecords', metadataPrefix: prefix]

  if(resumptionToken) qry.resumptionToken = resumptionToken

  new URIBuilder(host)
      .setPath(path)
      .addQueryParams(qry)
      .toURL()
}



def addToGoKB(dryrun, gokb, title_data) {

  try {
    println("addToGoKB..... ${new Date()}");
    if ( dryrun ) {
      println(toJson(title_data))
    }
    else {
      gokb.request(Method.POST) { req ->
        uri.path='/gokb/integration/crossReferenceLicense'
        body = title_data
        requestContentType = ContentType.JSON

        response.success = { resp, data ->
          println "Success! ${resp.status} ${data.message}"
        }

        response.failure = { resp ->
          println "GOKB crossReferenceLicense Request failed with status ${resp.status}"
          // println (title_data);
        }
      }
    }
  }
  catch ( Exception e ) {
    println("Fatal error loading ${title_data}\nNot loaded");
    e.printStackTrace();
    System.exit(0);
  }
  finally {
    println("addToGoKB complete ${new Date()}");
  }


}
