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

  CSVReader r = new CSVReader( new InputStreamReader( new FileInputStream(file), java.nio.charset.Charset.forName('UTF-8') ), '\t' as char)

  // Load header line
  List header = r.readNext();
  List next_line = r.readNext();
  while(next_line) {
    println(next_line)
    next_line = r.readNext();
  }
}



def processTitleListUrl(client, link, title, description, report_line, gokb) {

  println("processTitleListUrl... ${link}");

  // The link we have has the form
  // http://tls.search.proquest.com/titlelist/jsp/list/tlsSingle.jsp?productId=1006728
  // And we need a link like this:
  // http://tls.search.proquest.com/titlelist/ListForward?productId=1006728&format=tab&IDString=1006728&all=all&keyTitle=keyTitle&ft=Y&citAbs=Y&other=Y&issn=Y&isbn=Y&peer=Y&pubId=Y&additionalTitle=additionalTitle&ftDetail=Y&citAbsDetail=Y&otherDetail=Y&gaps=Y&subject=Y&language=Y&changes=Y

  // matches = link =~ /(?:\?|&|;)([^=]+)=([^&|;]+)/

  // Get the page that allows us to specify what details we want.
  def html = client.getPage(link);

  // If the response page contains a select element with the name subProductIdList then this package is
  // a collection of other packages and we should create a GOKb package for each component as well as the parent product
  // HtmlSelect si = (HtmlSelect) html.getElementByName("subProductIdList");
  HtmlSelect si = null; //(HtmlSelect) html.getFirstByXPath('//SELECT[NAME="subProductIdList"]');
  try {
    si = html.getElementByName("subProductIdList");
  }
  catch ( Exception e ) {
  }

  if ( si != null ) {
    report_line.type='MultiCollection'
    println("Product is composed of multiple collections - enumerate them");

    // Push the parent package
    pushToGokb(title,description.toString(),makeTSV([]),gokb)


    si.getOptions().each { opt ->
      println("  --> ${opt}");
      // now push the child objects
    }
  }
  else {
    // It's a simple package...
    report_line.type='SingleCollection'
    println("Product is single collection");

    pushToGokb(title,description.toString(),makeTSV([]),gokb)

    // Click the checkbox :  <INPUT TYPE="checkbox" NAME="all" VALUE="all" onclick="changeAll();"/>
    HtmlCheckBoxInput i = (HtmlCheckBoxInput) html.getElementByName("all");
    i.setChecked(true);
  
    // Click the link : <A HREF="javascript: void 0" onClick="return checkFormat('tab');">
    // ScriptResult result = html.executeJavaScript("return checkFormat('tab');");

    // We actually don't want the title list at this stage - just the package data.
    // def tsv_response = result.getNewPage()
    // println("Result of execute tab: ${tsv_response}");
  }


  // DAC Ingest Columns::    [field: 'notes', kbart:'notes'],
  //              [field: 'online_identifier', kbart: 'online_identifier'],
  //              [field: 'publication_title', kbart: 'publication_title'],
  //              [field: 'publisher', kbart:'publisher'],
  //              [field: 'title_url', kbart:'title_url']

  // URL title_list = new URL("http://tls.search.proquest.com/titlelist/ListForward?productId=${product_id}&format=tab&IDString=${product_id}&all=all&keyTitle=keyTitle&ft=Y&citAbs=Y&other=Y&issn=Y&isbn=Y&peer=Y&pubId=Y&additionalTitle=additionalTitle&ftDetail=Y&citAbsDetail=Y&otherDetail=Y&gaps=Y&subject=Y&language=Y&changes=Y")
  // BufferedReader is = new BufferedReader(new InputStreamReader(title_list.openStream()));
  // CSVReader reader = new CSVReader(is);
  // String[] header = null;
  // String[] row = reader.readNext()  // Package name
  // if ( row ) { row = reader.readNext() }  // Accurare as of line
  // if ( row ) { row = reader.readNext() }  // actual header line
  // header = row;
  // if ( row ) { row=reader.readNext() } // First line of data
  // while ( row ) {
  //   println(row)
  //   row = reader.readNext()
  // }
}

def pushToGokb(name, description, data, http) {
  // curl -v --user admin:admin -X POST \
  //   $GOKB_HOST/gokb/packages/deposit

  http.request(Method.POST) { req ->
    uri.path="/packages/deposit"

    MultipartEntityBuilder multiPartContent = new MultipartEntityBuilder()
    // Adding Multi-part file parameter "imageFile"
    multiPartContent.addPart("content", new ByteArrayBody( data, name.toString()))

    // Adding another string parameter "city"
    multiPartContent.addPart("source", new StringBody("PROQUEST"))
    multiPartContent.addPart("fmt", new StringBody("DAC"))
    multiPartContent.addPart("pkg", new StringBody(name.toString()))
    multiPartContent.addPart("platformUrl", new StringBody("https://www.proquest.com"));
    multiPartContent.addPart("format", new StringBody("JSON"));
    multiPartContent.addPart("providerName", new StringBody("PROQUEST"));
    multiPartContent.addPart("providerIdentifierNamespace", new StringBody("PROQUEST"));
    multiPartContent.addPart("reprocess", new StringBody("Y"));
    multiPartContent.addPart("description", new StringBody(description));
    multiPartContent.addPart("synchronous", new StringBody("Y"));
    multiPartContent.addPart("curatoryGroup", new StringBody("Jisc"));
    multiPartContent.addPart("flags", new StringBody("+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs"));
    
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

