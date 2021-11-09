package org.gokb

import org.gokb.cred.*;

import static groovyx.net.http.Method.*
import groovyx.net.http.*
import grails.converters.JSON



class TitleAugmentService {

  def grailsApplication
  def componentLookupService
  def reviewRequestService
  def zdbAPIService
  def ezbAPIService

  def augmentZdb(titleInstance) {
    log.debug("Augment ZDB - TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    CuratoryGroup editorialGroup = grailsApplication.config.gokb.editorialAdmin?.journals ? CuratoryGroup.findByNameIlike(grailsApplication.config.gokb.editorialAdmin.journals) : null

    if ( titleInstance.niceName == 'Journal' ) {
      def rr_no_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'No ZDB Results')
      def rr_type = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple ZDB Results')
      def rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'ZDB Title Overlap')
      def existing_rr = ReviewRequest.executeQuery("select rr.id from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc IN (:types)", [ti: titleInstance, types: [rr_type, rr_in_use]])

      if (existing_rr.size() == 0) {
        def candidates = zdbAPIService.lookup(titleInstance.name, titleInstance.ids)
        RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

        if (candidates.size() == 1) {
          def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', candidates[0].id)
          def conflicts = Combo.executeQuery("from Combo as c where c.fromComponent IN (select ti from TitleInstance as ti where ti.status != :deleted) and c.fromComponent != :tic and c.toComponent = :idc and c.type = :ctype", [deleted: status_deleted, tic: titleInstance, idc: new_id, ctype: idComboType])

          if (conflicts.size() > 0) {
            log.debug("Matched ZDB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${new_id.identifiedComponents}")
            if (conflicts.size() == 1) {
              setNewTitleInfo(conflicts[0].fromComponent, candidates[0])
            }

            def additionalInfo = [
              otherComponents: conflicts.collect { [id: it.fromComponent.id, name: it.fromComponent.name, oid: it.fromComponent.logEntityId, uuid: it.fromComponent.uuid] }
            ]

            reviewRequestService.raise(
              titleInstance,
              "Review all titles for possible discrepancies",
              "Matched ZDB-ID is already linked to another title instance.",
              null,
              null,
              (additionalInfo as JSON).toString(),
              rr_in_use,
              editorialGroup
            )
          }
          else {
            log.debug("Adding new ZDB-ID ${new_id}")
            new Combo(fromComponent: titleInstance, toComponent: new_id, type: idComboType).save(flush: true, failOnError: true)
          }

          setNewTitleInfo(titleInstance, candidates[0])
        }
        else if (candidates.size == 0){
          if (titleInstance.ids.collect { it.namespace.value == 'issn' || it.namespace.value == 'eissn' }) {
            log.debug("No ZDB result for ids of title ${titleInstance} (${titleInstance.ids.collect { it.value }})")

            if (titleInstance.reviewRequests.collect { it.stdDesc == rr_no_results}.size() == 0) {
              reviewRequestService.raise(
                titleInstance,
                "Check for reference ID",
                "No ZDB matches for linked ISSNs",
                null,
                null,
                null,
                rr_no_results,
                editorialGroup
              )
            }
          }
        }
        else {
          log.debug("Multiple ZDB-ID candidates for title ${titleInstance}")

          def name_candidates = []

          candidates.each {
            if (it.title == titleInstance.name) {
              name_candidates.add (it)
            }
          }

          if (name_candidates.size() == 1) {
            def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', name_candidates[0].id)
            def conflicts = Combo.executeQuery("from Combo as c where c.fromComponent IN (select ti from TitleInstance as ti where ti.status != :deleted) and c.fromComponent != :tic and c.toComponent = :idc and c.type = :ctype", [deleted: status_deleted, tic: titleInstance, idc: new_id, ctype: idComboType])

            if (conflicts.size() > 0) {
              log.debug("Matched ZDB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${new_id.identifiedComponents}")
              if (conflicts.size() == 1) {
                setNewTitleInfo(conflicts[0].fromComponent, candidates[0])
              }

              def additionalInfo = [
                otherComponents: new_id.identifiedComponents.collect { [id: it.id, name: it.name, oid: it.logEntityId, uuid: it.uuid] }
              ]

              reviewRequestService.raise(
                titleInstance,
                "Review all titles for possible discrepancies",
                "Matched ZDB-ID is already linked to another title instance.",
                null,
                null,
                (additionalInfo as JSON).toString(),
                rr_in_use,
                editorialGroup
              )
            }
            else {
              log.debug("Adding new ZDB-ID ${new_id}")
              new Combo(fromComponent: titleInstance, toComponent: new_id, type: idComboType).save(flush: true, failOnError: true)
            }
          }
          else {
            def additionalInfo = [
              candidates: candidates
            ]

            reviewRequestService.raise(
              titleInstance,
              "Choose the correct ZDB-ID from the list of candidates",
              "Multiple ZDB-IDs found for ISSN ids",
              null,
              null,
              (additionalInfo as JSON).toString(),
              rr_type,
              editorialGroup
            )
          }
        }
      }
      else {
        log.debug("Skipping title with existing RR ..")
      }
    }
  }

  def augmentEzb(titleInstance) {
    log.debug("Augment EZB - TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    CuratoryGroup editorialGroup = grailsApplication.config.gokb.editorialAdmin?.journals ? CuratoryGroup.findByNameIlike(grailsApplication.config.gokb.editorialAdmin.journals) : null

    if ( titleInstance.niceName == 'Journal' ) {
      def rr_no_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'No EZB Results')
      def rr_type = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple EZB Results')
      def rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'EZB Title Overlap')
      def existing_rr = ReviewRequest.executeQuery("select rr.id from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc IN (:types)", [ti: titleInstance, types: [rr_type, rr_in_use]])

      if (existing_rr.size() == 0) {
        def candidates = ezbAPIService.lookup(titleInstance.name, titleInstance.ids)
        RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

        if (candidates.size() == 1) {
          def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb', candidates[0].id)
          def conflicts = Combo.executeQuery("from Combo as c where c.fromComponent IN (select ti from TitleInstance as ti where ti.status != :deleted) and c.fromComponent != :tic and c.toComponent = :idc and c.type = :ctype", [deleted: status_deleted, tic: titleInstance, idc: new_id, ctype: idComboType])

          if (conflicts.size() > 0) {
            log.debug("Matched EZB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${new_id.identifiedComponents}")
            if (conflicts.size() == 1) {
              setNewTitleInfo(conflicts[0].fromComponent, candidates[0])
            }
            def additionalInfo = [
                otherComponents: conflicts.collect { [id: it.fromComponent.id, name: it.fromComponent.name, oid: it.fromComponent.logEntityId, uuid: it.fromComponent.uuid] }
            ]
            reviewRequestService.raise(
                titleInstance,
                "Review all titles for possible discrepancies",
                "Matched EZB-ID is already linked to another title instance.",
                null,
                null,
                (additionalInfo as JSON).toString(),
                rr_in_use,
                editorialGroup
            )
          }
          else {
            log.debug("Adding new EZB-ID ${new_id}")
            new Combo(fromComponent: titleInstance, toComponent: new_id, type: idComboType).save(flush: true, failOnError: true)
          }
          setNewTitleInfo(titleInstance, candidates[0])
        }
        else if (candidates.size == 0){
          if (titleInstance.ids.collect { it.namespace.value == 'issn' || it.namespace.value == 'eissn' }) {
            log.debug("No EZB result for ids of title ${titleInstance} (${titleInstance.ids.collect { it.value }})")
            if (titleInstance.reviewRequests.collect { it.stdDesc == rr_no_results}.size() == 0) {
              reviewRequestService.raise(
                  titleInstance,
                  "Check for reference ID",
                  "No EZB matches for linked ISSNs",
                  null,
                  null,
                  null,
                  rr_no_results,
                  editorialGroup
              )
            }
          }
        }
        else {
          log.debug("Multiple EZB-ID candidates for title ${titleInstance}")
          def name_candidates = []
          candidates.each {
            if (it.title == titleInstance.name) {
              name_candidates.add (it)
            }
          }
          if (name_candidates.size() == 1) {
            def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb', name_candidates[0].id)
            def conflicts = Combo.executeQuery("from Combo as c where c.fromComponent IN (select ti from TitleInstance as ti where ti.status != :deleted) and c.fromComponent != :tic and c.toComponent = :idc and c.type = :ctype", [deleted: status_deleted, tic: titleInstance, idc: new_id, ctype: idComboType])
            if (conflicts.size() > 0) {
              log.debug("Matched EZB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${new_id.identifiedComponents}")
              if (conflicts.size() == 1) {
                setNewTitleInfo(conflicts[0].fromComponent, candidates[0])
              }
              def additionalInfo = [
                  otherComponents: new_id.identifiedComponents.collect { [id: it.id, name: it.name, oid: it.logEntityId, uuid: it.uuid] }
              ]
              reviewRequestService.raise(
                  titleInstance,
                  "Review all titles for possible discrepancies",
                  "Matched EZB-ID is already linked to another title instance.",
                  null,
                  null,
                  (additionalInfo as JSON).toString(),
                  rr_in_use,
                  editorialGroup
              )
            }
            else {
              log.debug("Adding new EZB-ID ${new_id}")
              new Combo(fromComponent: titleInstance, toComponent: new_id, type: idComboType).save(flush: true, failOnError: true)
            }
          }
          else {
            def additionalInfo = [
                candidates: candidates
            ]
            reviewRequestService.raise(
                titleInstance,
                "Choose the correct EZB-ID from the list of candidates",
                "Multiple EZB-IDs found for ISSN ids",
                null,
                null,
                (additionalInfo as JSON).toString(),
                rr_type,
                editorialGroup
            )
          }
        }
      }
      else {
        log.debug("Skipping title with existing RR ..")
      }
    }
  }

  private void setNewTitleInfo(titleInstance, info) {
    if (!titleInstance.publishedFrom && info.publishedFrom) {
      log.debug("Adding new start journal start date ..")
      com.k_int.ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(info.publishedFrom), titleInstance, 'publishedFrom')
    }
    if (!titleInstance.publishedTo && info.publishedTo) {
      log.debug("Adding new start journal end date ..")
      com.k_int.ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(info.publishedTo), titleInstance, 'publishedTo')
    }

    if (!titleInstance.currentPublisher && info.publisher) {
      RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")
      def pub_obj = Org.findByNameAndStatusNot(info.publisher, status_deleted)

      if (!pub_obj) {
        def variant_normname = GOKbTextUtils.normaliseString(info.publisher)
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

    def full_title = info.subtitle ? "${info.title}: ${info.subtitle}" : info.title

    if (titleInstance.normname != KBComponent.generateNormname(full_title)) {
      def old_title = titleInstance.name
      titleInstance.name = full_title
      titleInstance.addVariantTitle(old_title)
      titleInstance.save()
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
