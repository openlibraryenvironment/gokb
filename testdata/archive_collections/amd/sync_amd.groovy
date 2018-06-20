#!groovy

@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.5'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.5.5'),
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
import groovyx.net.http.HTTPBuilder
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody
import groovyx.net.http.*
import org.apache.http.entity.mime.MultipartEntityBuilder /* we'll use the new builder strategy */
import org.apache.http.entity.mime.content.ByteArrayBody /* this will encapsulate our file uploads */
import org.apache.http.entity.mime.content.StringBody /* this will encapsulate string params */
import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter

config = null;
cfg_file = new File('./sync-amd-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
}
else {
  config=[:]
  config.packageData=[:]
}

println("Using config ${config}");

println("Pulling latest messages");
// Adam Matthew Digital https://www.amdigital.co.uk/products/products

pullLatest(config,'https://www.amdigital.co.uk/products/products');
println("All done");

println("Updating config");
cfg_file.delete()
cfg_file << toJson(config);



def pullLatest(config, url) {
  println("Get URL ${url}");
  client = new WebClient()
  client.getOptions().setThrowExceptionOnFailingStatusCode(false);
  client.getOptions().setThrowExceptionOnScriptError(false);
  client.getOptions().setJavaScriptEnabled(true);
  client.getOptions().setRedirectEnabled(true);
  client.getOptions().setCssEnabled(false);
  client.getOptions().setTimeout(600000);
  client.waitForBackgroundJavaScript(8000);
  client.setAjaxController(new NicelyResynchronizingAjaxController());
  client.getCookieManager().setCookiesEnabled(true);

  // Added as HtmlUnit had problems with the JavaScript
  // client.javaScriptEnabled = true
  html = client.getPage(url);
  
  boolean next_page = true;
  int page_count = 0;

  def httpbuilder = new HTTPBuilder( 'http://localhost:8080' )
  httpbuilder.auth.basic 'admin', 'admin'

  while(next_page) {
    page_count++

    List<?> products = html.getByXPath("//a[@class='product']");
    println("Processing ${products.size()} products");
    products.each { product ->

      def baos = new ByteArrayOutputStream();
      CSVWriter out_writer = new CSVWriter( new OutputStreamWriter( baos, java.nio.charset.Charset.forName('UTF-8') ), '\t' as char)

      List out_header = []
      out_header.add('online_identifier');
      out_header.add('publication_title');
      out_header.add('title_url');
      out_header.add('publisher');
      out_header.add('notes');
      out_writer.writeNext((String[])(out_header.toArray()))

      List nl=[]
      def product_title = product.getFirstByXPath("div/h3/text()")?.toString()
      def product_url = product.getFirstByXPath("@href").getValue();
      def product_excerpt = product.getFirstByXPath("div/div[@class='excerpt']/text()").toString()

      def descr = product_excerpt.toString()+'\n'+product_url

      // nl.add(product_url);
      // nl.add(product_title);
      // nl.add(product_url);
      // nl.add('Adam Matthew Digital');
      // nl.add(product_excerpt);
      // out_writer.writeNext((String[])(nl.toArray()))

      out_writer.close()

      def csv_data = new String(baos.toByteArray());
      pushToGokb(product_title,descr,csv_data, httpbuilder);
    }

    // Each product seems to have content and marc records:: https://www.amdigital.co.uk/primary-sources/african-american-communities

    // def next_page_links = []
    // if ( next_page_links.size() > 0 ) {
    //   html = next_page_links[0].click();
    // }
    // else {
      next_page = false;
    // }
  }
  

  println("Done ${page_count} pages");
}

def pushToGokb(name, description, data, http) {
  // curl -v --user admin:admin -X POST \
  //   $GOKB_HOST/gokb/packages/deposit

  println("** uploading ${name}");

  http.request(Method.POST) { req ->
    uri.path="/packages/deposit"

    MultipartEntityBuilder multiPartContent = new MultipartEntityBuilder()
    // Adding Multi-part file parameter "imageFile"
    multiPartContent.addPart("content", new ByteArrayBody( data.getBytes(), name.toString()))

    multiPartContent.addPart("source", new StringBody("AMD"))
    multiPartContent.addPart("fmt", new StringBody("DAC"))
    multiPartContent.addPart("pkg", new StringBody(name))
    multiPartContent.addPart("platformUrl", new StringBody("https://www.amdigital.co.uk"));
    multiPartContent.addPart("format", new StringBody("JSON"));
    multiPartContent.addPart("providerName", new StringBody("AMD"));
    multiPartContent.addPart("providerIdentifierNamespace", new StringBody("uri"));
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

