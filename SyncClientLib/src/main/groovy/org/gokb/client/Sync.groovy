package org.gokb.client

import groovyx.net.http.*
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

public class Sync {

  public static void main(String[] args) {
    println("Sync");

    def http = new HTTPBuilder( 'http://localhost:8080' )

    println("Attempt get...");
    // perform a GET request, expecting JSON response data
    http.request( GET, XML ) {
      uri.path = '/gokb/oai/packages'
      uri.query = [ verb:'ListRecords', metadataPrefix: 'oai' ]

      // response handler for a success response code:
      response.success = { resp, json ->
        println resp.statusLine

        // parse the JSON response object:
        json.responseData.results.each {
          println "  ${it.titleNoFormatting} : ${it.visibleUrl}"
        }
      }

      // handler for any failure status code:
      response.failure = { resp ->
        println "Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}"
      }
    }

    println("All done");
  }
}
