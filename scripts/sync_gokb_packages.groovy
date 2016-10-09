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


// Example full record http://gokb.openlibraryfoundation.org/gokb/oai/packages?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:309298

// Alternate names
// Example full record http://gokb.openlibraryfoundation.org/gokb/oai/packages?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:232360

// Publisher example
// Example full record http://gokb.openlibraryfoundation.org/gokb/oai/packages?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:14290

config = null;
cfg_file = new File('./sync-gokb-packages-cfg.json')
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
        resourceFieldMap.packageHeader = [:]
        resourceFieldMap.packageHeader.scope = r.metadata.gokb.package.scope.text()
        resourceFieldMap.packageHeader.listStatus = r.metadata.gokb.package.listStatus.text()
        resourceFieldMap.packageHeader.status = r.metadata.gokb.package.status.text()
        resourceFieldMap.packageHeader.breakable = r.metadata.gokb.package.breakable.text()
        resourceFieldMap.packageHeader.consistent = r.metadata.gokb.package.consistent.text()
        resourceFieldMap.packageHeader.fixed = r.metadata.gokb.package.fixed.text()
        resourceFieldMap.packageHeader.paymentType = r.metadata.gokb.package.paymentType.text()
        resourceFieldMap.packageHeader.global = r.metadata.gokb.package.global.text()
        resourceFieldMap.packageHeader.name = r.metadata.gokb.package.name.text()
        resourceFieldMap.packageHeader.listVerifier = r.metadata.gokb.package.listVerifier.text()
        resourceFieldMap.packageHeader.userListVerifier = r.metadata.gokb.package.userListVerifier.text()
        resourceFieldMap.packageHeader.nominalPlatform = r.metadata.gokb.package.nominalPlatform.text()
        resourceFieldMap.packageHeader.nominalProvider = r.metadata.gokb.package.nominalProvider.text()
        resourceFieldMap.packageHeader.listVerifierDate = r.metadata.gokb.package.listVerifierDate.text()
        resourceFieldMap.packageHeader.source = [url:r.metadata.gokb.package.source?.url.text()]

        resourceFieldMap.packageHeader.curatoryGroups = []
        r.metadata.gokb.package.curatoryGroups.each {
          resourceFieldMap.packageHeader.curatoryGroups.add([curatoryGroup:it.curatoryGroup.text()])
        }
        resourceFieldMap.packageHeader.variantNames = []
        r.metadata.gokb.package.variantNames.each {
          resourceFieldMap.packageHeader.variantNames.add([variantName:it.variantName.text()]);
        }
        resourceFieldMap.tipps = []

        r.metadata.gokb.package.TIPPs.TIPP.each { xmltipp ->
          def newtipp = [:]
          newtipp.status = xmltipp.status.text()
          newtipp.medium = xmltipp.medium.text()
          newtipp.accessStart = xmltipp.access.'@start'.text()
          newtipp.accessEnd = xmltipp.access.'@end'.text()
          newtipp.coverage = []
          newtipp.coverage.add([startDate:xmltipp.coverage.'@startDate'.text(),
                                startVolume:xmltipp.coverage.'@startVolume'.text(),
                                startIssue:xmltipp.coverage.'@startIssue'.text(),
                                endDate:xmltipp.coverage.'@endDate'.text(),
                                endVolume:xmltipp.coverage.'@endVolume'.text(),
                                endIssue:xmltipp.coverage.'@endIssue'.text(),
                                coverageDepth:xmltipp.coverage.'@coverageDepth'.text(),
                                coverageNote:xmltipp.coverage.'@coverageNote'.text(),
                                embargo:xmltipp.coverage.'@embargo'.text()]);
          newtipp.url = xmltipp.url.text();
          newtipp.title = [:]
          newtipp.title.name = xmltipp.title.name.text();
          newtipp.title.identifiers = []
          xmltipp.title.identifiers.identifier.each { id ->
            newtipp.title.identifiers.add([type:id.'@namespace'.text(), value:id.'@value'.text()]);
          }
          newtipp.platform = [:]
          newtipp.platform.name = xmltipp.platform.name.text();
          newtipp.platform.primaryUrl = xmltipp.platform.primaryUrl.text();

          resourceFieldMap['tipps'].add(newtipp);
        }

        resources << resourceFieldMap
      }
    }

    response.error = { err ->
      println "Failed http request"
      println(err)
    }
  }

  println("Fetched ${resources.size()} packages in oai page");
  [resources, resumptionToken]
}

private static URL gokbUrl(host, resumptionToken = null) {
  final path = '/gokb/oai/packages', prefix = 'gokb'

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
        uri.path='/gokb/integration/crossReferencePackage'
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

