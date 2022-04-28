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
  @Grab(group='net.sf.opencsv', module='opencsv', version='2.0'),
  @Grab(group='commons-io', module='commons-io', version='2.5'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])


import static groovyx.net.http.ContentType.XML
import static groovyx.net.http.Method.GET
import static groovy.json.JsonOutput.*

import groovy.json.JsonSlurper
import groovyx.net.http.*
import java.util.Properties
import java.security.MessageDigest
import javax.mail.*
import javax.mail.search.*

import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.IOUtils
import org.apache.commons.net.ftp.*
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.ByteArrayBody
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody

import com.gargoylesoftware.htmlunit.*
import au.com.bytecode.opencsv.CSVReader


def config = null

cfg_file = new File('./sync_ezbID_config.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text)
}
else {
  config=[:]
  config.packageData=[:]
}
println("Using config from file: ${config}")

config.uploadUser = System.console().readLine ('Enter your username: ').toString()
config.uploadPass = System.console().readPassword ('Enter your password: ').toString()
// config.uploadUser = "You can also set an automated username here."
// config.uploadPass = "You can also set an automated password here."

def httpbuilder = new HTTPBuilder( "http://localhost:${config.port}" )
httpbuilder.auth.basic config.uploadUser, config.uploadPass

println("Enriching EZB IDs by CSV file ... ")
enrichEzb(config, httpbuilder, args[0])
println("Enriching EZB IDs by CSV file ... done.")




def enrichEzb(config, httpbuilder, issnfile) {
  File f = new File(args[0])
  def charset = 'UTF-8'
  def csv = new CSVReader(new InputStreamReader(
                            new org.apache.commons.io.input.BOMInputStream(
                              new FileInputStream(issnfile),
                                ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE,ByteOrderMark.UTF_8),
                            java.nio.charset.Charset.forName(charset)),'\t' as char,'\0' as char)

  String[] header = csv.readNext()
  println(header)
  String[] nl=csv.readNext()

  int rownum = 1

  while (nl != null) {
    if (rownum % 5000 == 0) {
      synchronized(this) {
        println("Sleeping 6 seconds") // seems to be good habit?!
        Thread.sleep(6000)
      }
    }

    // println("${rownum} ${nl}")

    def identifiers = []
    def title
    def zdbId

    def cell = 0
    nl.each {
      if ( cell == 0 ) {
		zdbId = it
        identifiers.add( [ type:'zdb', value:it ] )
      }
      else if ( cell == 1 ) {
        identifiers.add( [ type:'ezb', value:it ] )
      }
      else if ( cell == 2 ) {
		title = it
      }
      cell++
    }
    rownum++

    def gokbEntry = queryZdbId(httpbuilder, config, zdbId)

    println "Get response: ${gokbEntry}"

/*
 *  TODO
 *  addToGoKB(false, httpbuilder, 'Unknown Title '+ nl[0],'Serial',null,identifiers)
    rownum++
*/
    nl=csv.readNext()
  }
}



def queryZdbId(def httpbuilder, def config, def zdbId){
	def url = "${config.path}/api/find?componentType=Journal&identifier=${zdbId}"
	println "URI: ${url}"
	httpbuilder.request(Method.GET) { req ->
      uri.path=url
      accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
      // requestContentType = ContentType.JSON

      response.success = { resp, json ->
        return json
      }
      response.failure = { resp ->
        return []
      }
    }
}



def addToGoKB(dryrun, gokb, title, type, publisher, ids) {

  /*
   * TODO
   *
    def title_data = [e
    type:type,
    title:title,
    publisher:publisher,
    identifiers:ids,
    status:'Expected'
  ]

  if ( dryrun ) {
    println("add title : ${title} ${type} ${publisher} ${ids}")
  }
  else {
    gokb.request(Method.POST) { req ->
      uri.path='${config.path}/integration/crossReferenceTitle'
      body = title_data
      requestContentType = ContentType.JSON

      response.success = { resp ->
        println "Success! ${resp.status}"
      }

      response.failure = { resp ->
        println "Request failed with status ${resp.status}"
      }
    }
  } */

}
