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


if ( args.length != 2 ) {
  println("usage: ncsu_orgs_add_variant_names.groovy csv_orgs_file url of add variant name service");
  println("Examples: ./ncsu_orgs_add_variant_names.groovy ./ncsu-auth-orgs-roles-2013-01-11.csv \"http://localhost:8080/gokb/integration/registerVariantName\"");
  println("Examples: ./ncsu_orgs_add_variant_names.groovy ./ncsu-auth-orgs-roles-2013-01-11.csv \"http://gokb.k-int.com/gokb/integration/registerVariantName\"");
  System.exit(1)
}
// Load the fam reconcilliation data
// def target_service = new HTTPBuilder('http://localhost:8080/gokb/integration/assertOrg')
def target_service = new HTTPBuilder(args[1])

// try {
//   target_service.request(GET, ContentType.XML) { request ->
//     uri.path='/ukfederation-metadata.xml'
//     response.success = { resp, data ->
//       // data is the xml document
//       ukfam = data;
//     }
//     response.failure = { resp ->
//       println("Error - ${resp.status}");
//       System.out << resp
//     }
//   }
// }
// catch ( Exception e ) {
//   e.printStackTrace();
// }

// To clear down the gaz: curl -XDELETE 'http://localhost:9200/gaz'
// CSVReader r = new CSVReader( new InputStreamReader(getClass().classLoader.getResourceAsStream("./IEEE_IEEEIEL_2012_2012.csv")))
println("Processing ${args[0]}");
CSVReader r = new CSVReader( new InputStreamReader(new FileInputStream(args[0]),java.nio.charset.Charset.forName('UTF-8')) )

String [] nl;

int rownum = 0;

// Read column headings
nl = r.readNext()
println("Column heads: ${nl}");

while ((nl = r.readNext()) != null) {
  // println("Process line ${nl}");
  // Internal ID,ParentOrg. ID,Authorized Name,Organization Name,Provider,Vendor,Publisher,Licensor
  def variant_name_request = [:]

  if ( ( nl[0] != nl [1] ) && ( nl[2] == 'N' ) ) {
    println("Add a variant name (${nl[3]}) for the org with ns:ncsu-internal, identifier ncsu:${nl[1]}. Authorized status of this name is ${nl[2]}");
    
    variant_name_request.idns = 'ncsu-internal'
    variant_name_request.idvalue = "ncsu:${nl[1]}".toString()
    variant_name_request.name = nl[3]

    variant_name_request.variantidns = 'ncsu-internal'
    variant_name_request.variantidvalue = "ncsu:${nl[0]}".toString()

    println(variant_name_request)

    // Post the json document
    target_service.request( POST, JSON ) { req ->
      body = variant_name_request
      response.success = { resp, json ->
        println(json);
      }
    }
  }
  else {
  }
}
