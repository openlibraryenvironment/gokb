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

  def next_page_url = url;

  while(next_page) {

    html = client.getPage(next_page_url);

    page_count++

    List<?> links = html.getByXPath("//div[@class='container categoryBlock']");
    println("Processing ${links.size()} links");
    links.each { link ->
      def title = link.getFirstByXPath('div/h2/text()');
      println(title)
    }
  
    def last_page_link = html.getFirstByXPath("//a[text()='Last']/@href").getValue().trim();
    def next_page_link = html.getFirstByXPath("//i[@class='fa fa-chevron-right']/../@href").getValue().trim();

    println("Last page: ${last_page_link} Next page: ${next_page_link} ${last_page_link.equals(next_page_link)}");

    if ( ! last_page_link.equals(next_page_link) ) {
      next_page_url = 'http://www.proquest.com'+next_page_link
      next_page = true;
    }
    else {
      next_page = false;
    }
  }
  
  println("Done ${page_count} pages");
  println("Done ${package_count} packages");
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

