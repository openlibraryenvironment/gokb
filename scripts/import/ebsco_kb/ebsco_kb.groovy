#!/usr/bin/groovy

// @GrabResolver(name='es', root='https://oss.sonatype.org/content/repositories/releases')
@Grapes([
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

while ((nl = r.readNext()) != null) {
  println("Row: "+nl);

  println("Process ${nl[0]}/EBSCO_${nl[0]}_${nl[2]}_${args[1]}.csv");
}
