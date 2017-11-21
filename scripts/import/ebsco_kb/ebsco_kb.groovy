#!/usr/bin/groovy

// @GrabResolver(name='central', root='http://central.maven.org/maven2/')
@Grapes([
  // @GrabResolver(name='es', root='https://oss.sonatype.org/content/repositories/releases'),
  @Grab(group='net.sf.opencsv', module='opencsv', version='2.0'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.2'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.0'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.0'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.2')
])


import groovy.util.slurpersupport.GPathResult
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import org.apache.http.*
import org.apache.http.protocol.*
import org.apache.log4j.*
import au.com.bytecode.opencsv.CSVReader
import java.text.SimpleDateFormat


if ( args.length != 3 ) {
  println("usage: groovy  -Dgroovy.grape.autoDownload=false  ebsco_kb.groovy <<KB base dir>> <<date>> <<Target URL>>")
  println("  where KB base dir contains ProviderIndex.csv");
  println("  Example: groovy  -Dgroovy.grape.autoDownload=false  ebsco_kb.groovy /tmp/ebsco \"2016-01-27\" http://localhost:8080/gokb");
  System.exit(1)
}

def target_service = new HTTPBuilder(args[1])

println("Processing EBSCO data using base dir ${args[0]}");
// Open the ProviderIndex File
CSVReader r = new CSVReader( new InputStreamReader(new FileInputStream(args[0]+'/ProviderIndex.csv'),java.nio.charset.Charset.forName('UTF-8')) )

String [] nl;

int rownum = 0;

// Read column headings
nl = r.readNext()
println("Column heads: ${nl}");
// "Provider_ID",
// "Provider_Name",
// "Package_ID",
// "Package_Name",
// "Package_Type_ID",
// "Package_Type",
// "Package_Content_Type_ID",
// "Package_Content_Type",
// "Package_URL",
// "Access_Type",
// "Consortia",
// "Vendor_Token_Prompt",
// "Vendor_Token",
// "Vendor_Token_Help",
// "Package_Token_Prompt",
// "Package_Token",
// "Package_Token_Help"

def total = 0;
def start_time = System.currentTimeMillis()

while ((nl = r.readNext()) != null) {
  // println("Row: "+nl);

  println("Process ${nl[0]}/EBSCO_${nl[0]}_${nl[2]}_${args[1]}.csv");
  def ingest_file = args[0]+'/'+"${nl[0]}/EBSCO_${nl[0]}_${nl[2]}_${args[1]}.csv"

  // 
  // curl -v --user admin:admin -X POST \
  //   --form content=@./$cufts_file \
  //   --form source="CUFTS" \
  //   --form fmt="cufts" \
  //   --form pkg="$cufts_file" \
  //   --form platformUrl="http://lib-code.lib.sfu.ca/projects/CUFTS/" \
  //   --form format="JSON" \
  //   http://localhost:8080/gokb/packages/deposit

  def cmd = ["curl", "-v", "--user", "admin:admin", "-X", "POST",
              "--form", "content=@${ingest_file}",
              "--form", "source=EBSCO",
              "--form", "fmt=ebsco",
              "--form", "pkg=${nl[3]}",
              "--form", "platformUrl=${nl[8]}",
              "--form", "format=JSON",
              "--form", "synchronous=Y",
              "http://localhost:8080/gokb/packages/deposit"]

  def sout = new StringBuffer(), serr = new StringBuffer()
  def proc = cmd.execute()
  proc.consumeProcessOutput(sout, serr)
  proc.waitFor()
  println "out> $sout err> $serr"

  def elapsed = System.currentTimeMillis() - start_time
  total += elapsed
  println("This File : ${elapsed} Total: ${total}");

  synchronized(this) {
    Thread.sleep(2000);
  }
}

println("Elapsed : ${total}");
