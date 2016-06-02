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

import org.apache.commons.io.IOUtils
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

def httpbuilder = new HTTPBuilder( 'http://localhost:8080' )
httpbuilder.auth.basic config.uploadUser, config.uploadPass



println("Pulling latest messages");
pullLatest(config, httpbuilder)
println("All done");

println("Updating config");
cfg_file.delete()
cfg_file << toJson(config);



def pullLatest(config, http) {
  int package_count = 0;

  // see https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPClient.html
  FTPClient ftp = new FTPClient();
  FTPClientConfig ftp_config = new FTPClientConfig();
  // config.setXXX(YYY); // change required options
  // for example config.setServerTimeZoneId("Pacific/Pitcairn")
  ftp.configure(ftp_config );
  ftp.setControlKeepAliveTimeout(150);
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

    ftp.enterLocalPassiveMode();
    ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);

    // FTPFile[] files = ftp.listFiles('/2016 data/obook Collection');
    FTPFile[] files = ftp.listFiles('/');
    files.each { ftpfile ->
      println("file: ${ftpfile}");
      if ( ftpfile.name.startsWith('20') && 
           ( ftpfile.name.length() >= 9 ) && 
           ( ftpfile.name.substring(4,9)==' data' ) && 
           ( ftpfile.type == FTPFile.DIRECTORY_TYPE ) ) {
        println("Master loop - recurse into /${ftpfile.name}");
        recurse(ftp, '/'+ftpfile.name, http);
      }
    }

    println("All done");
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


def recurse(ftp, dir, http) {
  println("\n\nrecurse(ftp, \"${dir}\")");
  
  def candidates = [:]

  FTPFile[] files = ftp.listFiles(dir);

  println("${files.size()} entries in ${dir}");

  files.each { file ->
    println("Consider dir entry : ${file.name}");
    if ( ( file.type == FTPFile.DIRECTORY_TYPE ) &&
         ( file.name != '.' ) &&
         ( file.name != '..' ) ) {
      println("Directory - recurse");
      recurse(ftp, dir+'/'+file.name,http);
    }
    else {
      println("File - consider");
      if ( file.name.length() > 4 ) {
        if( file.name.substring(file.name.length()-4, file.name.length()) == '.txt' ) {
          println("Candidate : ${file.name}");
          //  wiley_all_obooks_2016-03-01.txt
          if ( file.name ==~ /(.*)_(\d{4}-[01]\d-[0-3]\d).txt/ ) {

            java.util.regex.Matcher file_info = file.name =~ /(.*)_(\d{4}-[01]\d-[0-3]\d).txt/
            println(file_info[0][1])
            println(file_info[0][2])

            if ( candidates[file_info[0][1]] == null ) {
              // First time we have seen this file...
              println("Adding to candidates - first time seen");
              candidates[file_info[0][1]] = [ ts:file_info[0][2], path:dir+'/'+file.name ]
            }
            else {
              println("Checking if ${file_info[0][2]} is more recent than ${candidates[file_info[0][1]].ts}");
              if ( file_info[0][2] > candidates[file_info[0][1]].ts ) {
                println("  -> Yes, use");
                candidates[file_info[0][1]].ts = file_info[0][2]
                candidates[file_info[0][1]].path = dir+'/'+file.name
                println("  -> New candidate for ${file_info[0][1]} : ${candidates[file_info[0][1]]}");
              }
              else {
                println("  -> Nope, skipping");
              }
            }
          }
          else {
            println("Candidate ${file.name} does not match regex name_yyyy_mm_dd.txt");
          }
        }
        else {
          println("  -> Does not end in .txt");
        }
      }
      else {
        println("  -> Not long enough");
      }
    }
  }

  println("After recursing into ${dir} the following candidate files are found:");

  candidates.each { k,v ->
    println("${k} -> ${v}");
    processFile(k, v.path, config, ftp, http);
  }
}



def processFile(official_package_name, link, config, ftp, http) {
  println("\n\nfetching ${official_package_name} - ${link}");

  // def package_data = new URL(link).getText()
  ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
  def fis = ftp.retrieveFileStream(link)
  byte[] bytes = IOUtils.toByteArray(fis);
  fis.close()

  if(!ftp.completePendingCommand()) {
    System.err.println("File transfer failed.");
    ftp.logout();
    ftp.disconnect();
    System.exit(1);
  }

  MessageDigest md5_digest = MessageDigest.getInstance("MD5");
  InputStream md5_is = new ByteArrayInputStream(bytes);

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
    pushToGokb(official_package_name, bytes, http);
    config.packageData[official_package_name].cksum = md5sumHex
    config.packageData[official_package_name].lastProcessed = System.currentTimeMillis()
  }

}

def pushToGokb(name, data, http) {
  // curl -v --user admin:admin -X POST \
  //   $GOKB_HOST/gokb/packages/deposit

  try {
    http.request(Method.POST) { req ->
      uri.path="/gokb/packages/deposit"

      MultipartEntityBuilder multiPartContent = new MultipartEntityBuilder()
      // Adding Multi-part file parameter "imageFile"
      multiPartContent.addPart("content", new ByteArrayBody( data, name.toString()))

      // Adding another string parameter "city"
      multiPartContent.addPart("source", new StringBody("WILEY"))
      multiPartContent.addPart("fmt", new StringBody("wiley-blackwell-kbart"))
      multiPartContent.addPart("pkg", new StringBody(name.toString()))
      multiPartContent.addPart("platformUrl", new StringBody("http://onlinelibrary.wiley.com"));
      multiPartContent.addPart("format", new StringBody("JSON"));
      multiPartContent.addPart("providerName", new StringBody("wiley"));
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
  catch ( Throwable t ) {
    println("ERROR Submitting file",t);
  }
}

