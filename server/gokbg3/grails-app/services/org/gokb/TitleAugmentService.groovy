package org.gokb

import org.gokb.refine.*;
import org.gokb.cred.*;

import groovy.util.slurpersupport.GPathResult
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import org.apache.http.*
import org.apache.http.protocol.*



class TitleAugmentService {

  def grailsApplication
  def componentLookupService
  def reviewRequestService
  def zdbAPIService
  RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")

  def augment(titleInstance) {
    if ( titleInstance.class.name == 'JournalInstance' ) {
      def candidates = zdbAPIService.lookup(titleInstance.name, titleInstance.ids)

      if (candidates.size() == 1) {
        def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', candidates[0])

        if (new_id.identifiedComponents?.size() > 0) {

        }
        else {
          new Combo(fromComponent: titleInstance, toComponent: new_id, type: idComboType).save(flush: true, failOnError: true)
        }
      }
    }
  }

  def doEnrichment() {
    // Iterate though titles touched since last cursor
    // def title_ids = TitleInstance.executeQuery("select id from TitleInstance");
    // title_ids.each { ti_id ->
    //   def title = TitleInstance.get(ti_id);
    //   console.log("Process ${ti_id} ${title.name}");
    //   synchronized(this) {
    //     this.sleep(1000);
    //   }
    // }
    crossrefSync();
  }

  def crossrefSync() {
    def endpoint = 'http://api.crossref.org';
    def target_service = new RESTClient(endpoint)
    def offset = 0;
    def num_processed = 1;

    while ( num_processed > 0 ) {
      num_processed = 0;
      try {
        target_service.request(GET) { request ->
          uri.path='/journals'
          uri.query = [
            offset:offset,
            rows:100
          ]

          response.success = { resp, data ->
            data.message.items.each { item ->
              log.debug("item:: ${item.title}");
              offset++;
            }
            num_processed = data.message.'total-results'
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

      synchronized(this) {
        Thread.sleep(5000);
      }
    }



    // {"status":"ok","message-type":"journal-list","message-version":"1.0.0","message":{"items":[],"total-results":39790,"query":{"search-terms":null,"start-index":70000},"items-per-page":20}}
  }
}
