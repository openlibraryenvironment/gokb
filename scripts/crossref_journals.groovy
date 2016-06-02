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

config = null;
cfg_file = new File('./sync-crossref-cfg.json')
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
  def crossref = new HTTPBuilder( 'http://api.crossref.org/journals' )

  def last_reccount = 1;
  def offset = 0;
  while(last_reccount > 0) {
    println("\n\nRecords ${offset} to ${offset}+100");
    crossref.get( path:'/journals', query:[offset:offset,rows:100]) { resp, json ->
      last_reccount = 0
      json.message.items.each { item ->
        println("${item.title} ${item.publisher} - ${item.ISSN}");
        addToGoKB(httpbuilder, item.title, item.publisher, item.ISSN)
        last_reccount++;
      }
      offset += last_reccount
    }
  }
}


def addToGoKB(gokb, title, publisher, ids) {
  def title_data = [
    type:'Serial',
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
