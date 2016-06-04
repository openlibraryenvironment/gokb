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


// Example full record http://gokb.kuali.org/gokb/oai/titles?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:309298

// Alternate names
// Example full record http://gokb.kuali.org/gokb/oai/titles?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:232360

// Publisher example
// Example full record http://gokb.kuali.org/gokb/oai/titles?verb=GetRecord&metadataPrefix=gokb&identifier=org.gokb.cred.TitleInstance:14290

config = null;
cfg_file = new File('./sync-gokb-titles-cfg.json')
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
pullLatest(config, httpbuilder)
println("All done");

println("Updating config");
cfg_file.delete()
cfg_file << toJson(config);


def pullLatest(config,httpbuilder) {
  importJournals('http://gokb.kuali.org', httpbuilder);
}

def importJournals(host, gokb) {
  def resumptionToken, resourcesFromPage

  def moredata = true;

  while ( moredata ) {

    (resourcesFromPage, resumptionToken) = getResourcesFromGoKBByPage(gokbUrl(host, resumptionToken))

    resourcesFromPage.each {
      println(it);
      addToGoKB(gokb, it.title, it.medium?:'Journal', it.publisher, it.identifiers)
    }

    if ( resumptionToken ) moredata = true else moredata = false;
  }
}

private static getResourcesFromGoKBByPage(URL url) {
  println "Retrieving: ${url}"

  def http = new HTTPBuilder(url, XML)

  http.headers = [Accept: 'application/xml']

  def resources = []
  def resumptionToken = null

  http.request(GET, XML) { req ->
    response.success = { resp, body ->
      resumptionToken = body?.ListRecords?.resumptionToken.text()

      body?.'ListRecords'?.'record'.each { r ->
        def resourceFieldMap = [:]
        resourceFieldMap['name'] = r.metadata.gokb.title.name.text()
        resourceFieldMap['medium'] = r.metadata.gokb.title.medium.text()
        resourceFieldMap['identifiers'] = []
        resourceFieldMap['publishedFrom'] = r.metadata.gokb.title.publishedFrom?.text()
        resourceFieldMap['publishedTo'] = r.metadata.gokb.title.publishedTo?.text()
        resourceFieldMap['continuingSeries'] = r.metadata.gokb.title.continuingSeries?.text()
        resourceFieldMap['OAStatus'] = r.metadata.gokb.title.OAStatus?.text()
        resourceFieldMap['imprint'] = r.metadata.gokb.title.imprint?.text()
        resourceFieldMap['issuer'] = r.metadata.gokb.title.issuer?.text()
        resourceFieldMap['variantNames'] = []
        resourceFieldMap['historyEvents'] = []

        r.metadata.gokb.title.identifiers.identifier.each {
          if ( ['issn', 'eissn', 'DOI', 'isbn'].contains(it.'@namespace') )
            resourceFieldMap.identifiers.add( [ namespace:it.'@namespace'.text(),value:it.'@value'.text() ] )
        }

        if ( r.metadata.gokb.title.publisher?.name ) {
          resourceFieldMap['publisher'] = r.metadata.gokb.title.publisher.name.text()
        }

        r.metadata.gokb.title.variantNames?.variantName.each { vn ->
          resourceFieldMap['variantNames'].add(vn.text());
        }

        r.metadata.gokb.title.history?.historyEvent.each { he ->
          def history_event = 
            [
              from:[ ],
              to:[ ]
            ];

          he.from.each { fr ->
            history_event.from.add(convertHistoryEvent(fr));
          }

          he.to.each { to ->
            history_event.to.add(convertHistoryEvent(to));
          }

          history_event.date = he.date;

          resourceFieldMap['historyEvents'].add (history_event);
        }

        resources << resourceFieldMap
      }
    }

    response.error = { err ->
      println "Failed http request"
      println(err)
    }
  }
  [resources, resumptionToken]
}

def convertHistoryEvent(evt) {
  // convert the evt structure to a json object and add to lst
  def result = [:]
  result.title.evt=title.text()
  result.identifiers=[]
  evt.identifiers.each { id ->
    result.ids.add( [ type: id.'@namespace'.text(), value: id.'@value'.text() ] );
  }
  result
}

private static URL gokbUrl(host, resumptionToken = null) {
  final path = '/gokb/oai/titles', prefix = 'gokb'

  def qry = [verb: 'ListRecords', metadataPrefix: prefix]

  if(resumptionToken) qry.resumptionToken = resumptionToken

  new URIBuilder(host)
            .setPath(path)
            .addQueryParams(qry)
            .toURL()
}



def addToGoKB(gokb, title, type, publisher, ids) {
  def title_data = [
    type:type,
    title:title,
    publisher:publisher,
    identifiers:[
    ]
  ]

  ids.each {
    title_data.identifiers.add([type:'issn',value:it])
  }

  gokb.request(Method.POST) { req ->
    uri.path='/gokb/integration/crossReferenceTitle'
    body = title_data
    requestContentType = ContentType.JSON

    response.success = { resp ->
      println "Success! ${resp.status}"
    }

    response.failure = { resp ->
      println "Request failed with status ${resp.status}"
    }
  }

}
