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


config = null;
cfg_file = new File('./sync-gokb-sources-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
}
else {
  config=[:]
  config.packageData=[:]
}

println("Using config ${config}");

def httpbuilder = new HTTPBuilder( 'http://localhost:8080' )
httpbuilder.auth.basic config.uploadUser, config.uploadPass


println("Pulling latest messages");
pullLatest(config, httpbuilder, cfg_file)
println("All done");

def pullLatest(config,httpbuilder, cfg_file) {
  importSources('http://gokb.openlibraryfoundation.org', httpbuilder, config, cfg_file);
}

def importSources(host, gokb, config, cfg_file) {

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
        resourceFieldMap['name'] = r.metadata.gokb.name.text()
        resourceFieldMap['shortcode'] = r.metadata.gokb.shortcode.text()
        resourceFieldMap['editStatus'] = r.metadata.gokb.editStatus.text()
        resourceFieldMap['url']= r.metadata.gokb.url.text()
        resourceFieldMap['defaultAccessURL']= r.metadata.gokb.defaultAccessURL.text()
        resourceFieldMap['explanationAtSource']= r.metadata.gokb.explanationAtSource.text()
        resourceFieldMap['contextualNotes']= r.metadata.gokb.contextualNotes.text()
        resourceFieldMap['frequency']= r.metadata.gokb.frequency.text()
        resourceFieldMap['ruleset']= r.metadata.gokb.ruleset.text()
        resourceFieldMap['defaultSupplyMethod']= r.metadata.gokb.defaultSupplyMethod.text()
        resourceFieldMap['defaultDataFormat']= r.metadata.gokb.defaultDataFormat.text()
        resources << resourceFieldMap
      }
    }

    response.error = { err ->
      println "Failed http request"
      println(err)
    }
  }

  println("Fetched ${resources.size()} orgs in oai page");
  [resources, resumptionToken]
}

private static URL gokbUrl(host, resumptionToken = null) {
  final path = '/gokb/oai/sources', prefix = 'gokb'

  def qry = [verb: 'ListRecords', metadataPrefix: prefix]

  if(resumptionToken) qry.resumptionToken = resumptionToken

  new URIBuilder(host)
            .setPath(path)
            .addQueryParams(qry)
            .toURL()
}



def addToGoKB(dryrun, gokb, source_data) {
  
  try {
    if ( dryrun ) {
      println(source_data)
    }
    else {
      gokb.request(Method.POST) { req ->

        uri.path='/gokb/integration/assertSource'
        body = source_data
        requestContentType = ContentType.JSON

        response.success = { resp, data ->
          println "Success! ${resp.status} ${data.message}"
        }

        response.failure = { resp ->
          println "Request failed with status ${resp.status}"
          println (source_data);
        }
      }
    }
  }
  catch ( Exception e ) {
    println("Fatal error loading ${source_data}");
    e.printStackTrace();
    System.exit(0);
  }

}
