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
import grails.converters.JSON



class TitleAugmentService {

  def grailsApplication
  def componentLookupService
  def reviewRequestService
  def zdbAPIService

  def augment(titleInstance) {
    log.debug("TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    CuratoryGroup editorialGroup = grailsApplication.config.gokb.editorialAdmin?.journals ? CuratoryGroup.findByNameIlike(grailsApplication.config.gokb.editorialAdmin.journals) : null

    if ( titleInstance.niceName == 'Journal' ) {
      def candidates = zdbAPIService.lookup(titleInstance.name, titleInstance.ids)
      RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
      RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

      if (candidates.size() == 1) {
        def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', candidates[0].id)
        def conflicts = Combo.executeQuery("from Combo as c where c.fromComponent IN (select ti from TitleInstance as ti where ti.status != :deleted) and c.fromComponent != :tic and c.toComponent = :idc and c.type = :ctype", [deleted: status_deleted, tic: titleInstance, idc: new_id, ctype: idComboType])

        if (conflicts.size() > 0) {
          log.debug("Matched ZDB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${new_id.identifiedComponents}")

          conflicts.each { cc ->
            if (!cc.fromComponent.publishedFrom && candidates[0].publishedFrom) {
              log.debug("Adding new start journal start date ..")
              com.k_int.ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(candidates[0].publishedFrom), cc.fromComponent, 'publishedFrom')
            }
            if (!cc.fromComponent.publishedTo && candidates[0].publishedTo) {
              log.debug("Adding new start journal end date ..")
              com.k_int.ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(candidates[0].publishedTo), cc.fromComponent, 'publishedTo')
            }
          }
        }
        else {
          log.debug("Adding new ZDB-ID ${new_id}")
          new Combo(fromComponent: titleInstance, toComponent: new_id, type: idComboType).save(flush: true, failOnError: true)
        }

        if (!titleInstance.publishedFrom && candidates[0].publishedFrom) {
          log.debug("Adding new start journal start date ..")
          com.k_int.ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(candidates[0].publishedFrom), titleInstance, 'publishedFrom')
        }
        if (!titleInstance.publishedTo && candidates[0].publishedTo) {
          log.debug("Adding new start journal end date ..")
          com.k_int.ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(candidates[0].publishedTo), titleInstance, 'publishedTo')
        }

        if (!titleInstance.currentPublisher && candidates[0].publisher) {
          def pub_obj = Org.findByNameAndStatusNot(candidates[0].publisher, status_deleted)

          if (!pub_obj) {
            def variant_normname = GOKbTextUtils.normaliseString(candidates[0].publisher)
            def var_candidates = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted])

            if (var_candidates.size() == 1) {
              pub_obj = var_candidates[0]
            }
          }

          if (pub_obj) {
            def publisher_combo = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Publisher')
            new Combo(fromComponent: titleInstance, toComponent: pub_obj, type: publisher_combo).save(flush: true, failOnError: true)
          }
        }
      }
      else if (candidates.size == 0){
        log.debug("No ZDB result for ids of title ${titleInstance}")
      }
      else {
        log.debug("Multiple ZDB-ID candidates for title ${titleInstance}")

        def additionalInfo = [
          candidates: candidates
        ]

        reviewRequestService.raise(
          titleInstance,
          "Choose correct the ZDB-ID from the list of candidates",
          "Multiple ZDB-IDs found for ISSN ids",
          null,
          null,
          (additionalInfo as JSON).toString(),
          RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple ZDB Results'),
          editorialGroup
        )
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
