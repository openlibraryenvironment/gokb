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
import au.com.bytecode.opencsv.CSVReader
import org.apache.commons.io.ByteOrderMark



println("Load ISSN-L (ISSN-L-to-ISSN) file ${args[0]}");

config = null;
cfg_file = new File('./sync-issnl-cfg.json')
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
pullLatest(config, httpbuilder, args[0])
println("All done");

println("Updating config");
cfg_file.delete()
cfg_file << toJson(config);

def pullLatest(config,httpbuilder, issnfile) {
  File f = new File(args[0])
  def charset = 'UTF-8'
  def csv = new CSVReader(new InputStreamReader(
                            new org.apache.commons.io.input.BOMInputStream(
                              new FileInputStream(issnfile),
                                ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE,ByteOrderMark.UTF_8),
                            java.nio.charset.Charset.forName(charset)),'\t' as char,'\0' as char)

  String[] header = csv.readNext()
  println(header)

  // Blank line
  String[] blank = csv.readNext()

  String[] nl=csv.readNext()

  int rownum = 0;

  while(nl!=null) {
    if ( rownum % 5000 == 0 ) {
      synchronized(this) {
        println("Sleeping 5");
        Thread.sleep(6000);
      }
    }

      println("${rownum} ${nl}")

      def identifiers = []
      def first = true
      nl.each {
        if ( first ) {
          identifiers.add( [ type:'issnl', value:it ] )
          first=false
        }
        else {
          identifiers.add( [ type:'issn', value:it ] )
        }
      }

      addToGoKB(false, httpbuilder, 'Unknown Title '+ nl[0],'Serial',null,identifiers);
      rownum++;

    nl=csv.readNext()
  }
}


/**
 *    {
 *      title:'sss',
 *      identifiers:[
 *        { namespace:'sss', value:'qqq'},
 *        { namespace:'sss', value:'qqq'},
 *      ]
 *    }
 *
 */
def addToGoKB(dryrun, gokb, title, type, publisher, ids) {

  def title_data = [
    type:type,
    title:title,
    publisher:publisher,
    identifiers:ids,
    status:'Expected'
  ]

  if ( dryrun ) {
    println("add title : ${title} ${type} ${publisher} ${ids}");
  }
  else {
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

}
