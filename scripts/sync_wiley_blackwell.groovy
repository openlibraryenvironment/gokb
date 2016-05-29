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

import org.apache.commons.net.ftp.*

config = null;
cfg_file = new File('./sync-wiley-blackwell-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
}
else {
  config=[:]
  config.packageData=[:]
}

println("Using config ${config}");

println("Pulling latest messages");
pullLatest(config,'http://link.springer.com/lists');
println("All done");

println("Updating config");
cfg_file.delete()
cfg_file << toJson(config);



def pullLatest(config, url) {
  int package_count = 0;

  // see https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPClient.html
  FTPClient ftp = new FTPClient();
  FTPClientConfig ftp_config = new FTPClientConfig();
  // config.setXXX(YYY); // change required options
  // for example config.setServerTimeZoneId("Pacific/Pitcairn")
  ftp.configure(ftp_config );
  boolean error = false;
  try {
    int reply;
    String server = config.ftphost
    ftp.connect(server);
    ftp.login(config.ftpuser, config.ftppass);
    System.out.println("Connected to " + server + ".");
    System.out.print(ftp.getReplyString());

    reply = ftp.getReplyCode();

    if(!FTPReply.isPositiveCompletion(reply)) {
        ftp.disconnect();
        System.err.println("FTP server refused connection.");
        System.exit(1);
    }
      // ... // transfer files
      ftp.logout();
  } catch(IOException e) {
      error = true;
      e.printStackTrace();
  } finally {
    if(ftp.isConnected()) {
      try {
        ftp.disconnect();
      } catch(IOException ioe) {
          // do nothing
      }
    }
  }
  
  println("Done ${package_count} packages");
}

def processFile(official_package_name, link, config, http) {
  println("\n\nfetching ${official_package_name} - ${link}");

  def package_data = new URL(link).getText()


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

  println("Hash for ${link} is ${md5sumHex}");

  if ( config.packageData[official_package_name] == null ) {
    config.packageData[official_package_name] = [ cksum:0 ];
  }

  if ( md5sumHex == config.packageData[official_package_name].cksum ) {
    println("Checksum not changed - Skipping");
  }
  else {
    println("Checksum changed - process file");
    pushToGokb(official_package_name, package_data, http);
    config.packageData[official_package_name].cksum = md5sumHex
    config.packageData[official_package_name].lastProcessed = System.currentTimeMillis()
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
    multiPartContent.addPart("source", new StringBody("SPRINGER"))
    multiPartContent.addPart("fmt", new StringBody("springer-kbart"))
    multiPartContent.addPart("pkg", new StringBody(name.toString()))
    multiPartContent.addPart("platformUrl", new StringBody("http://link.springer.com"));
    multiPartContent.addPart("format", new StringBody("JSON"));
    multiPartContent.addPart("providerName", new StringBody("springer"));
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

