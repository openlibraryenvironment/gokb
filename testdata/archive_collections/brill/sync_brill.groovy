#!groovy

@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.2'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.5.2'),
  @Grab(group='net.sf.opencsv', module='opencsv', version='2.3'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])


import javax.mail.*
import javax.mail.search.*
import java.util.Properties
import static groovy.json.JsonOutput.*
import groovy.json.JsonSlurper
import java.security.MessageDigest
import com.gargoylesoftware.htmlunit.*
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput
import com.gargoylesoftware.htmlunit.html.HtmlSelect
import groovyx.net.http.HTTPBuilder
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody
import groovyx.net.http.*
import org.apache.http.entity.mime.MultipartEntityBuilder /* we'll use the new builder strategy */
import org.apache.http.entity.mime.content.ByteArrayBody /* this will encapsulate our file uploads */
import org.apache.http.entity.mime.content.StringBody /* this will encapsulate string params */
import java.io.BufferedReader
import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import java.nio.charset.Charset


java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.OFF); 
java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(java.util.logging.Level.OFF);


config = null;
cfg_file = new File('./sync-jstor-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
}
else {
  config=[:]
  config.packageData=[:]
}

def report = []

println("Using config ${config}");

println("Pulling latest messages");
// ProQuest http://www.proquest.com/libraries/academic/primary-sources/?&page=1

// pullLatest(config,'http://www.proquest.com/libraries/academic/primary-sources/?&page=1');
ingest(config,'./brill.tsv',report);
println("All done");

println("Updating config");
cfg_file.delete()
cfg_file << toJson(config);



def ingest(config, file, report) {
  def result = false;

  boolean next_page = true;
  int page_count = 0;
  int package_count = 0;

  def httpbuilder = new HTTPBuilder( 'http://localhost:8080' )
  // def httpbuilder = new HTTPBuilder( 'https://dac.k-int.com' )
  httpbuilder.auth.basic 'admin', 'admin'
  httpbuilder.encoders.charset = Charset.forName('UTF-8');

  CSVReader r = new CSVReader( new InputStreamReader( new FileInputStream(file), java.nio.charset.Charset.forName('UTF-8') ), '\t' as char)

  // Load header line
  // The brill xlsx has lines of extra info at the top we don't want - lets read past them
  List header = r.readNext();

  // The real header line:
  header = r.readNext();

  println('header:');
  println(header);
  println('---------------------------------------------');

  List next_line = r.readNext();
  while(next_line) {
    println(next_line)
    def description = next_line.get(0)+' '+next_line.get(1)+'\n'+next_line.get(2)+'/'+next_line.get(3)+' '+next_line.get(4)
    pushToGokb(next_line.get(1), 
               description, 
               makeTSV(new java.util.ArrayList()), 
               httpbuilder,
               sanitiseCurrency(next_line.get(5)), 
               sanitiseCurrency(next_line.get(6)), 
               sanitiseCurrency(next_line.get(7)),
               next_line.get(4))
    println(next_line.get(1)+' '+next_line.get(7))
    next_line = r.readNext();
  }
}

public String sanitiseCurrency(String value) {
  String v2 = value.trim();
  String result;

  if ( v2.equals('-') ) {
    result = ''
  }
  else {
    if (v2.startsWith('€')) {
      result = v2.substring(1,v2.length()).replaceAll(',','').trim()+' EUR'
    }
    else {
      result = v2
    }
  }

  return result;
}

def pushToGokb(name, description, data, http, price_std, price_perpetual, price_topup, url) {
  // curl -v --user admin:admin -X POST \
  //   $GOKB_HOST/gokb/packages/deposit

  println("pushToGokb($name,$description,data,http,$price_std,$price_topup,$price_perpetual");

  http.request(Method.POST) { req ->
    uri.path="/packages/deposit"

    MultipartEntityBuilder multiPartContent = new MultipartEntityBuilder()
    // Adding Multi-part file parameter "imageFile"
    multiPartContent.addPart("content", new ByteArrayBody( data, name.toString()))

    // Adding another string parameter "city"
    multiPartContent.addPart("source", new StringBody("BRILL",Charset.forName('UTF-8')))
    multiPartContent.addPart("fmt", new StringBody("DAC",Charset.forName('UTF-8')))
    multiPartContent.addPart("pkg", new StringBody(name.toString(),Charset.forName('UTF-8')))
    multiPartContent.addPart("platformUrl", new StringBody("https://www.brill.com",Charset.forName('UTF-8')));
    multiPartContent.addPart("format", new StringBody("JSON",Charset.forName('UTF-8')));
    multiPartContent.addPart("providerName", new StringBody("BRILL",Charset.forName('UTF-8')));
    multiPartContent.addPart("providerIdentifierNamespace", new StringBody("BRILL",Charset.forName('UTF-8')));
    multiPartContent.addPart("reprocess", new StringBody("Y",Charset.forName('UTF-8')));
    multiPartContent.addPart("description", new StringBody(description,Charset.forName('UTF-8')));
    multiPartContent.addPart("synchronous", new StringBody("Y",Charset.forName('UTF-8')));
    multiPartContent.addPart("curatoryGroup", new StringBody("Jisc",Charset.forName('UTF-8')));
    multiPartContent.addPart("pkg.price", new StringBody(price_std,Charset.forName('UTF-8')));
    multiPartContent.addPart("pkg.price.topup", new StringBody(price_topup,Charset.forName('UTF-8')));
    multiPartContent.addPart("pkg.price.perpetual", new StringBody(price_perpetual,Charset.forName('UTF-8')));
    multiPartContent.addPart("pkg.descriptionURL", new StringBody(url,Charset.forName('UTF-8')));

    multiPartContent.addPart("flags", new StringBody("+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs",Charset.forName('UTF-8')));
    
    req.entity = multiPartContent.build()

    response.success = { resp, rdata ->
      if (resp.statusLine.statusCode == 200) {
        // response handling
        println("OK");
      }
    }
  }
}

def makeTSV(data_rows) {
  def baos = new ByteArrayOutputStream();
  CSVWriter out_writer = new CSVWriter( new OutputStreamWriter( baos, java.nio.charset.Charset.forName('UTF-8') ), '\t' as char)

  List out_header = []
  out_header.add('online_identifier');
  out_header.add('publication_title');
  out_header.add('title_url');
  out_header.add('publisher');
  out_header.add('notes');
  out_writer.writeNext((String[])(out_header.toArray()))


  data_rows.each { dr ->
  }

  out_writer.close()

  return baos.toByteArray();

}

