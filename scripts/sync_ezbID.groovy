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

import groovy.json.JsonSlurper
import groovyx.net.http.*
import org.apache.commons.io.*
import au.com.bytecode.opencsv.*

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
  println "Found header: ${header}"
  String[] nl=csv.readNext()

  int rownum = 1
  int enrichedRecords = 0

  while (nl != null) {
    if (rownum % 5000 == 0) {
      synchronized(this) {
        println("Sleeping 6 seconds") // seems to be good habit?!
        Thread.sleep(6000)
      }
    }

    println("Row: ${rownum}")

    def title
    def zdbId
    def edbId

    def cell = 0
    nl.each {
      if ( cell == 0 ) {
        zdbId = it
      }
      else if ( cell == 1 ) {
        ezbId = it
      }
      else if ( cell == 2 ) {
        title = it
      }
      cell++
    }

    if (String.valueOf(zdbId).length() == 7){
      zdbId = String.valueOf(zdbId).concat("-").concat(getZdbCheckDigit(zdbId))
    }

    def records = getRecords(config, zdbId)

    println("Row number: ${rownum}")
    println("Got records: ${records}")
    println("Enriched records: ${enrichedRecords}")

    for (def rec in records){
      def ids = rec.identifiers
      ids.add( [ type:'ezb', value:${ezbId} ] )
      rec.identifiers = ids
      addToGoKB(httpbuilder, rec, config)
      enrichedRecords++
    }

    if (enrichedRecords >= 1){
      break
    }
    rownum++
    nl=csv.readNext()
  }
}


/**
 * in accordance with FIZE-164
 */
String getZdbCheckDigit(def zdbId) {
  if (!((String.valueOf(zdbId)).matches("[\\d]{7}"))){
    return null
  }
  int number = Integer.valueOf(zdbId)
  String x = 'X'
  int checkDigit = 0
  def factor = 2
  while (number > 0){
    checkDigit += (number % 10) * factor
    number = number / 10
    factor++
  }
  checkDigit %= 11
  return String.valueOf (( checkDigit == 10) ? x : checkDigit)
}



List getRecords(def config, def zdbId){
  def url = "http://localhost:${config.port}"
  def path = "${config.path}/api/find?componentType=Journal&identifier=${zdbId}"

  println ("Getting record by URL: ${url.concat(path)}")

  http = new HTTPBuilder(url.concat(path))
  http.get(contentType : ContentType.TEXT) { resp, reader ->
    def text = reader.getText()
    def jsonSlurper = new JsonSlurper()
    def map = jsonSlurper.parseText(text)
    println ("... got: ${map}")
    return map
  }
  return []
}



def addToGoKB(httpbuilder, rec, config){

  println("Requesting crossReferenceTitle for record: ${rec}")

  httpbuilder.request(Method.POST) { req ->
    uri.path='${config.path}/integration/crossReferenceTitle'
    body = rec
    requestContentType = ContentType.JSON

    println("Adding record: ${rec}")

    response.success = { resp ->
      println "Added EZB ID for record: ${rec}"
    }

    response.failure = { resp ->
      println "Couldn't add EZB ID for record: ${rec}"
      println "... status was: ${resp.status}"
    }
  }
}
