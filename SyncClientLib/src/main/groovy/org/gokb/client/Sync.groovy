package org.gokb.client

import groovyx.net.http.*
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

public class Sync {

  public static void main(String[] args) {
    Sync sc = new Sync()
    if ( args.length > 0 )
      sc.doSync(args[0])
    else
      sc.doSync('http://localhost:8080')
  }

  public doSync(host) {
    println("Get latest changes");

    def http = new HTTPBuilder( host )

    def more = true
    println("Attempt get...");
    def resumption=null

    // perform a GET request, expecting JSON response data
    while ( more ) {
      println("Make http request..");

      http.request( GET, XML ) {
        uri.path = '/gokb/oai/packages'
        if ( resumption ) {
          uri.query = [ verb:'ListRecords', resumptionToken: resumption ]
        }
        else {
          uri.query = [ verb:'ListRecords', metadataPrefix: 'gokb' ]
        }
  
        // response handler for a success response code:
        response.success = { resp, xml ->
          println resp.statusLine
  
          xml.'ListRecords'.'record'.each { r ->
            println("Record id...${r.'header'.'identifier'}");
            println("Package Name: ${r.metadata.package.packageName}");
            println("Package Id: ${r.metadata.package.packageId}");
            r.metadata.package.packageTitles.TIP.each { pt ->
              println("Title: ${pt.title}");
            }
          }

          if ( xml.'ListRecords'.'resumptionToken'.size() == 1 ) {
            resumption=xml.'ListRecords'.'resumptionToken'.text()
            println("Iterate with resumption : ${resumption}");
          }
          else {
            more = false
          }
        }

        // handler for any failure status code:
        response.failure = { resp ->
          println "Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}"
        }
      }
      println("Endloop");
    }

    println("All done");
  }

}
