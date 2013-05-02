package org.gokb

import groovy.util.slurpersupport.GPathResult
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import org.apache.http.*
import org.apache.http.protocol.*

class EdinaPublicationsAPIService {

  static transactional = false
  def endpoint
  def target_service
  def grailsApplication

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Initialising rest endpoint for edina publications service...");
    endpoint = grailsApplication.config.publicationService.baseurl ?: "http://knowplus.edina.ac.uk:2012/kbplus/api"
    target_service = new RESTClient(endpoint)
  }

  def lookup(title) {
    // http://knowplus.edina.ac.uk:2012/kbplus/api?title=ACS%20Applied%20Materials%20and%20Interfaces

    def result = null

    try {
      target_service.request(GET, ContentType.XML) { request ->
        // uri.path='/'
        uri.query = [
          title:title
        ]

        response.success = { resp, data ->
          // data is the xml document
          // ukfam = data;
          result = data;
        }
        response.failure = { resp ->
          log.error("Error - ${resp}");
        }
      }
    }
    catch ( Exception e ) {
      e.printStackTrace();
    } 
    finally {
    }


    result
  }
}
