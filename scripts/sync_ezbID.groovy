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

import groovy.json.JsonOutput
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

println("Enriching EZB IDs by CSV file ... ")
enrichEzb(config, args[0])
println("Enriching EZB IDs by CSV file ... done.")



def enrichEzb(config, issnfile) {
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

  while (nl != null) {
    if (rownum % 5000 == 0) {
      synchronized(this) {
        // println("Sleeping 6 seconds") // seems to be good habit?!
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
    // println("Got records: ${records}")

    for (def rec in records){
      def ids = rec.identifiers
      ids.add( [ namespace:"ezb", value:"${ezbId}", namespaceName:"EZB-ID" ] )
      rec.identifiers = ids
      addToGoKB(rec, config)
    }

    rownum++
    nl=csv.readNext()
    if (config?.maximumLines && rownum > Integer.valueOf(config.maximumLines)){
      break
    }
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
  Map searchHits = getSearchResult(config, zdbId)
  return searchHits.records
}



Map getSearchResult(def config, def zdbId){
  def url = "${config.base}"
  def path = "${config.path}/api/find?componentType=Journal&identifier=${zdbId}"

  // println ("Getting record by URL: ${url.concat(path)}")
  http = new HTTPBuilder(url.concat(path))
  http.get(contentType : ContentType.TEXT) { resp, reader ->
    def text = reader.getText()
    def jsonSlurper = new JsonSlurper()
    def result = jsonSlurper.parseText(text)
    return result
  }
}



def addToGoKB(rec, config){
  def shortRecord = [:]
  shortRecord.put("publicationType", "Serial")
  identifiers = []
  for (def id in rec.identifiers){
    if (id.namespace in ["ezb", "zdb"]){
      identifiers.add(id)
    }
  }
  shortRecord.put("identifiers", identifiers)
  shortRecord.put("name", rec.name)

  def url = "${config.base}"
  def path = "${config.path}/integration/crossReferenceTitle"
  // println("Setting HTTPBuilder with URL: ${url.concat(path)}")
  def http = new HTTPBuilder(url.concat(path))
  http.auth.basic config.uploadUser, config.uploadPass
  def postID = http.request( Method.POST, ContentType.JSON ) { req ->
    send ContentType.JSON, JsonOutput.toJson(shortRecord)
    response.success = { resp, json ->
      println "Added EZB ID for record: ${shortRecord}"
      /*
      println "... response status was: ${resp.status}"
      println "... response status code was: ${resp.getStatusLine().getStatusCode()}"
      println "... response reason was: ${resp.getStatusLine().getReasonPhrase()}"
      println "... response entity was: ${resp.getEntity().getContent()}"
      println "... response header was: ${resp.headers}"
      println "... response data was: ${resp.responseData}"
      println "... response was: ${resp.responseBase}"
      println "... response status: ${resp.statusLine}"
      println "... content Length: ${resp.headers['Content-Length']?.value}"
      println "... JSON: ${json}"
      */
      return json
    }


    response.failure = { resp ->
      println "Couldn't add EZB ID for record: ${shortRecord}"
      /*
      println "... response status was: ${resp.status}"
      println "... response status code was: ${resp.getStatusLine().getStatusCode()}"
      println "... response reason was: ${resp.getStatusLine().getReasonPhrase()}"
      println "... response header was: ${resp.headers}"
      println "... response data was: ${resp.responseData}"
      println "... response was: ${resp.responseBase}"
      */
    }
  }
}
