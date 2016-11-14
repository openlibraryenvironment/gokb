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


// Example full record http://gokb.openlibraryfoundation.org/gokb/oai/orgs?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:309298

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
  importOrgs('http://gokb.openlibraryfoundation.org', httpbuilder, config, cfg_file);
}

def importOrgs(host, gokb, config, cfg_file) {
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

private static cleanText(String text) {
  text?.trim()?.replaceAll(/\s{2,}/, ' ')
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
//        resourceFieldMap['name'] = cleanText(r.metadata.gokb.org.name.text())
        
        // Core fields come first.
        resourceFieldMap['name'] = r.metadata.gokb.org.name?.text()
        resourceFieldMap['status'] =  r.metadata.gokb.org.status?.text()
        resourceFieldMap['editStatus'] = r.metadata.gokb.org.editStatus?.text()
        resourceFieldMap['shortcode'] = r.metadata.gokb.org.shortcode?.text()

        // Identifiers
        resourceFieldMap['customIdentifiers'] = []
        r.metadata.gokb.org.identifiers?.identifier?.each {
          if ( !['originEditUrl'].contains(it.'@namespace') )
            resourceFieldMap.customIdentifiers.add( [ type:it.'@namespace'.text(),value:it.'@value'.text() ] )
        }
        
        // Additional properties
        resourceFieldMap['additionalProperties'] = []
        r.metadata.gokb.org.additionalProperties?.additionalProperty?.each {
          resourceFieldMap.additionalProperties.add( [ name:it.'@name'.text(),value:it.'@value'.text() ] )
        }
        
        // Variant names
        resourceFieldMap['variantNames'] = []
        r.metadata.gokb.org.variantNames?.variantName?.each { vn ->
          resourceFieldMap['variantNames'].add(vn.text());
        }
        
        resourceFieldMap['homepage'] = cleanText(r.metadata.gokb.org.homepage.text())
        resourceFieldMap['mission'] = cleanText(r.metadata.gokb.org.mission.text())
//        resourceFieldMap['customIdentifiers'] = []
//        resourceFieldMap['variantNames'] = []
//
//        r.metadata.gokb.org.identifiers.identifier.each {
//           resourceFieldMap['customIdentifiers'].add([identifierType:cleanText(it.text()),identifierValue: cleanText(it."@namespace".text())])
//        }
//
//        r.metadata.gokb.org.variantNames.variantName.each {
//          resourceFieldMap['variantNames'].add([variantName:cleanText(it.text())]);
//        }
        
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
  final path = '/gokb/oai/orgs', prefix = 'gokb'

  def qry = [verb: 'ListRecords', metadataPrefix: prefix]

  if(resumptionToken) qry.resumptionToken = resumptionToken

  new URIBuilder(host)
            .setPath(path)
            .addQueryParams(qry)
            .toURL()
}



def addToGoKB(dryrun, gokb, org_data) {
  
  try {
    if ( dryrun ) {
      println(org_data)
    }
    else {
      gokb.request(Method.POST) { req ->

        //      [
        //         name:National Association of Corrosion Engineers, 
        //         description:National Association of Corrosion Engineers,
        //         parent:
        //         customIdentifers:[[identifierType:"idtype", identifierValue:"value"]], 
        //         combos:[[linkTo:[identifierType:"ncsu-internal", identifierValue:"ncsu:61929"], linkType:"HasParent"]], 
        //         flags:[[flagType:"Org Role", flagValue:"Content Provider"],
        //                [flagType:"Org Role", flagValue:"Publisher"], 
        //                [flagType:"Authorized", flagValue:"N"]]
        //      ]

        uri.path='/gokb/integration/assertOrg'
        body = org_data
        requestContentType = ContentType.JSON

        response.success = { resp, data ->
          println "Success! ${resp.status} ${data.message}"
        }

        response.failure = { resp ->
          println "Request failed with status ${resp.status}"
          println (org_data);
        }
      }
    }
  }
  catch ( Exception e ) {
    println("Fatal error loading ${org_data}");
    e.printStackTrace();
    System.exit(0);
  }

}
