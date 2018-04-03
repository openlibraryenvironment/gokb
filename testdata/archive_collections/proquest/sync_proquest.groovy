#!groovy

@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.2'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.5.2'),
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

println("Using config ${config}");

println("Pulling latest messages");
// ProQuest http://www.proquest.com/libraries/academic/primary-sources/?&page=1

pullLatest(config,'http://www.proquest.com/libraries/academic/primary-sources/?&page=1');
println("All done");

println("Updating config");
cfg_file.delete()
cfg_file << toJson(config);



def pullLatest(config, url) {
  def result = false;

  println("Get URL ${url}");
  client = new WebClient()
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
  
  boolean next_page = true;
  int page_count = 0;
  int package_count = 0;

  def httpbuilder = new HTTPBuilder( 'http://localhost:8080' )
  httpbuilder.auth.basic 'admin', 'admin'

  def page_url = url;

  while(next_page) {

    html = client.getPage(page_url);

    page_count++

    List<?> links = html.getByXPath("//div[@class='container categoryBlock']");
    println("Processing ${links.size()} links");
    links.each { link ->
      def title = link.getFirstByXPath('div/h2/text()')
      def details_link = link.getFirstByXPath('div/div/div/p/a[text()="LEARN MORE"]/@href').getValue();
      def abst = link.getFirstByXPath('div/div/div/p/text()')
      println(title)
      println(details_link)
      println(abst)

      // Each details link is a page that may contain a link to the title list at tls.search.proquest.com - Usually with the text "View Title List"
      considerPage(title, details_link, abst, client);
    }
  
    def last_page_link = html.getFirstByXPath("//a[text()='Last']/@href").getValue().trim();
    def next_page_link = html.getFirstByXPath("//i[@class='fa fa-chevron-right']/../@href").getValue().trim();
    def next_page_url = 'http://www.proquest.com'+next_page_link

    println("\n\n\nLast page: ${last_page_link} Next page: ${next_page_link} ${last_page_link.equals(next_page_link)}");

    // Are we currently processing the last page? (If the next page link is exactly the same as the page we have just processed
    if ( ! next_page_url.equals(page_url) ) {
      next_page = true;
      page_url = next_page_url
    }
    else {
      next_page = false;
    }
  }
  
  println("Done ${page_count} pages");
  println("Done ${package_count} packages");
}

def considerPage(title, details_link, abst, client) {

  println("\n\n\nconsiderPage(...${details_link}...)");

  def html = client.getPage(details_link);

  def title_list_elem = html.getFirstByXPath('//a[starts-with(@href,"http://tls")]/@href');
  if ( title_list_elem ) {
    def title_list_link = title_list_elem.getValue()
    println("  --> title_list: ${title_list_link}");
  }
  else {
    println("  --> NO title list link found");
  }
}

def pushToGokb(name, data, http) {
  // curl -v --user admin:admin -X POST \
  //   $GOKB_HOST/gokb/packages/deposit

  http.request(Method.POST) { req ->
    uri.path="/gokb/packages/deposit"

    MultipartEntityBuilder multiPartContent = new MultipartEntityBuilder()
    // Adding Multi-part file parameter "imageFile"
    multiPartContent.addPart("content", new ByteArrayBody( data.getBytes(), name.toString()))

    // Adding another string parameter "city"
    multiPartContent.addPart("source", new StringBody("JSTOR"))
    multiPartContent.addPart("fmt", new StringBody("springer-kbart"))
    multiPartContent.addPart("pkg", new StringBody(name.toString()))
    multiPartContent.addPart("platformUrl", new StringBody("http://www.jstor.org/"));
    multiPartContent.addPart("format", new StringBody("JSON"));
    multiPartContent.addPart("providerName", new StringBody("JSTOR"));
    multiPartContent.addPart("providerIdentifierNamespace", new StringBody("doi"));
    multiPartContent.addPart("reprocess", new StringBody("Y"));
    multiPartContent.addPart("synchronous", new StringBody("Y"));
    multiPartContent.addPart("curtoryGroup", new StringBody("Jisc"));
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

