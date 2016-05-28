#!groovy

@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])



import javax.mail.*
import javax.mail.search.*
import java.util.Properties
import static groovy.json.JsonOutput.*
import groovy.json.JsonSlurper
import java.security.MessageDigest
import com.gargoylesoftware.htmlunit.*

println "Hello";

config = null;
cfg_file = new File('./handler-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
}
else {
  config=[:]
  config.packageData=[:]
}

println("Using config ${config}");

println("Pulling latest messages");
pullLatest(config,'http://holdings.sciencedirect.com/ehr/manageProductReports.url');
println("All done");

println("Updating config");
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
  html = client.getPage(url);
  
  boolean next_page = true;
  int page_count = 0;
  int package_count = 0;

  while(next_page) {
    page_count++
    // List<?> links = page.getByXPath("//div[@class='generate']/@href");
    List<?> links = html.getByXPath("//a/@href");
    println("Processing ${links.size()} links");
    links.each { link ->
      if ( link.value.startsWith('../holdings/productReport.url') ) {
        def package_name = link.getOwnerElement().getParentNode().getByXPath('../td[@class="report"]/text()');
        processFile(package_name[0],link.value, config);
        package_count++;
      }
    }
  
    def next_page_links = html.getByXPath("//a[text()='Next >']")
    if ( next_page_links.size() > 0 ) {
      html = next_page_links[0].click();
    }
    else {
      next_page = false;
    }
  }
  
  println("Done ${page_count} pages");
  println("Done ${package_count} packages");
}

def processFile(official_package_name, link, config) {
  def url_to_fecth = "http://holdings.sciencedirect.com/"+link.substring(3,link.length())
  println("fetching ${official_package_name} - ${url_to_fecth}");
  def package_data = new URL(url_to_fecth).getText()


  MessageDigest md5_digest = MessageDigest.getInstance("MD5");
  InputStream md5_is = new ByteArrayInputStream(package_data.getBytes());

  int filesize = 0;
  byte[] md5_buffer = new byte[8192];
  int md5_read = 0;
  while( (md5_read = md5_is.read(md5_buffer)) >= 0) {
    md5_digest.update(md5_buffer, 0, md5_read);
    filesize += md5_read
  }
  md5_is.close();
  byte[] md5sum = md5_digest.digest();
  def md5sumHex = new BigInteger(1, md5sum).toString(16);

  // println("Hash for ${link} is ${md5sumHex}");

  if ( config.packageData[official_package_name] == null ) {
    config.packageData[official_package_name] = [ cksum:0 ];
  }

  if ( md5sumHex == config.packageData[official_package_name].cksum ) {
    println("Checksum not changed - Skipping");
  }
  else {
    println("Checksum changed - process file");
    config.packageData[official_package_name].cksum = md5sumHex
    config.packageData[official_package_name].lastProcessed = System.currentTimeMillis()
  }

}

