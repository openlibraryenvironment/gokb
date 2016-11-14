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
import java.util.Properties
import static groovy.json.JsonOutput.*
import groovy.json.JsonSlurper
import java.security.MessageDigest
import com.gargoylesoftware.htmlunit.*
import groovyx.net.http.HTTPBuilder
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody
import groovyx.net.http.*
import org.apache.http.entity.mime.MultipartEntityBuilder /* we'll use the new builder strategy */
import org.apache.http.entity.mime.content.ByteArrayBody /* this will encapsulate our file uploads */
import org.apache.http.entity.mime.content.StringBody /* this will encapsulate string params */
import org.apache.commons.io.IOUtils
import org.apache.commons.net.ftp.*
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.URIBuilder
import static groovyx.net.http.ContentType.XML
import static groovyx.net.http.Method.GET


// Example full record http://gokb.openlibraryfoundation.org/gokb/oai/platforms?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:309298

// Alternate names
// Example full record http://gokb.openlibraryfoundation.org/gokb/oai/platforms?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:232360

// Publisher example
// Example full record http://gokb.openlibraryfoundation.org/gokb/oai/platforms?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:14290

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

println("Using config ${config}");

def httpbuilder = new HTTPBuilder( 'http://localhost:8080' )
httpbuilder.auth.basic config.uploadUser, config.uploadPass


println("Pulling latest messages");
pullLatest(config, httpbuilder, cfg_file)
println("All done");

def pullLatest(config,httpbuilder, cfg_file) {
  importJournals('http://gokb.openlibraryfoundation.org', httpbuilder, config, cfg_file);
}

def importJournals(host, gokb, config, cfg_file) {
  def resumptionToken
  def resourcesFromPage

  resumptionToken = config.resumptionToken

  def moredata = true;

  while ( moredata ) {
    def first_resource = false;
    def ctr = 0;
    (resourcesFromPage, resumptionToken) = getResourcesFromGoKBByPage(gokbUrl(host, resumptionToken))

    resourcesFromPage.each { gt ->
      ctr++
      if ( first_resource ) {
        println(gt);
        first_resource = false;
      }
      else {
        println(gt);
      }

      addToGoKB(false, gokb, gt)
    }

    if ( resumptionToken ) {
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
    cfg_file << toJson(config);
  }
}

private static getResourcesFromGoKBByPage(URL url) {
  println "Retrieving: ${url}"

  def http = new HTTPBuilder(url, XML)

  http.headers = [Accept: 'application/xml']

  def resources = []
  def resumptionToken = null
  def ctr = 0;

  http.request(GET, XML) { req ->
    response.success = { resp, body ->
      resumptionToken = body?.ListRecords?.resumptionToken.text()

      body?.'ListRecords'?.'record'.each { r ->

        println("Record ${ctr++}");

        def resourceFieldMap = [:]
        
        // Core fields come first.
        resourceFieldMap['name'] = r.metadata.gokb.platform.name?.text()
        resourceFieldMap['status'] =  r.metadata.gokb.platform.status?.text()
        resourceFieldMap['editStatus'] = r.metadata.gokb.platform.editStatus?.text()
        resourceFieldMap['shortcode'] = r.metadata.gokb.platform.shortcode?.text()

        // Identifiers
        resourceFieldMap['identifiers'] = []
        r.metadata.gokb.platform.identifiers?.identifier?.each {
          if ( !['originEditUrl'].contains(it.'@namespace') )
            resourceFieldMap.identifiers.add( [ type:it.'@namespace'.text(),value:it.'@value'.text() ] )
        }
        
        // Additional properties
        resourceFieldMap['additionalProperties'] = []
        r.metadata.gokb.platform.additionalProperties?.additionalProperty?.each {
          resourceFieldMap.additionalProperties.add( [ name:it.'@name'.text(),value:it.'@value'.text() ] )
        }
        
        // Variant names
        resourceFieldMap['variantNames'] = []
        r.metadata.gokb.platform.variantNames?.variantName?.each { vn ->
          resourceFieldMap['variantNames'].add(vn.text());
        }
        
        resourceFieldMap['platformName'] = r.metadata.gokb.platform.name.text()
        resourceFieldMap['platformUrl'] = r.metadata.gokb.platform.primaryUrl.text()
        resourceFieldMap['authentication'] = r.metadata.gokb.platform.authentication.text()
        resourceFieldMap['software'] = r.metadata.gokb.platform.software.text()
        resourceFieldMap['service'] = r.metadata.gokb.platform.service.text()
//        resourceFieldMap['status'] = r.metadata.gokb.status.authentication.text()

        resources << resourceFieldMap
      }
    }

    response.error = { err ->
      println "Failed http request"
      println(err)
    }
  }

  println("Fetched ${resources.size()} platforms in oai page");
  [resources, resumptionToken]
}

private static URL gokbUrl(host, resumptionToken = null) {
  final path = '/gokb/oai/platforms', prefix = 'gokb'

  def qry = [verb: 'ListRecords', metadataPrefix: prefix]

  if(resumptionToken) qry.resumptionToken = resumptionToken

  new URIBuilder(host)
            .setPath(path)
            .addQueryParams(qry)
            .toURL()
}



def addToGoKB(dryrun, gokb, title_data) {
  
  try {
    if ( dryrun ) {
      println(title_data)
    }
    else {
      gokb.request(Method.POST) { req ->
        uri.path='/gokb/integration/crossReferencePlatform'
        body = title_data
        requestContentType = ContentType.JSON

        response.success = { resp, data ->
          println "Success! ${resp.status} ${data.message}"
        }

        response.failure = { resp ->
          println "Request failed with status ${resp.status}"
          println (title_data);
        }
      }
    }
  }
  catch ( Exception e ) {
    println("Fatal error loading ${title_data}");
    e.printStackTrace();
    System.exit(0);
  }

}
