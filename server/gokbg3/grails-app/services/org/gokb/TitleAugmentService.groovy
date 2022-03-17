package org.gokb

import org.gokb.cred.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import grails.converters.JSON
import com.k_int.ConcurrencyManagerService.Job
import org.gokb.GOKbTextUtils


class TitleAugmentService {

  def grailsApplication
  def componentLookupService
  def reviewRequestService
  def zdbAPIService
  def ezbAPIService


  def augmentZdb(titleInstance) {
    log.debug("Augment ZDB - TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    CuratoryGroup editorialGroup = grailsApplication.config.gokb.zdbAugment?.rrCurators ?
        (CuratoryGroup.findByNameIlike(grailsApplication.config.gokb.zdbAugment.rrCurators) ?: new CuratoryGroup(name: grailsApplication.config.gokb.zdbAugment.rrCurators).save(flush: true)) : null
    int num_existing_zdb_ids = titleInstance.ids.findAll { it.namespace.value == 'zdb' }.size()

    if (titleInstance.niceName == 'Journal') {
      RefdataValue rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'ZDB Title Overlap')
      RefdataValue status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
      RefdataValue status_closed = RefdataCategory.lookup("ReviewRequest.Status", "Closed")
      def existing_inuse = ReviewRequest.executeQuery("from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc = :type and rr.status = :status", [ti: titleInstance, type: rr_in_use, status: status_open])

      if (existing_inuse.size() == 0 && num_existing_zdb_ids <= 1) {
        RefdataValue rr_no_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'No ZDB Results')
        RefdataValue rr_multiple = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple ZDB Results')
        RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

        def existing_noresults = ReviewRequest.executeQuery("from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc = :type", [ti: titleInstance, type: rr_no_results])
        def existing_multiple = ReviewRequest.executeQuery("from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc = :type", [ti: titleInstance, type: rr_multiple])
        def candidates = zdbAPIService.lookup(titleInstance.name, titleInstance.ids)

        if (candidates.size() == 1) {
          if (num_existing_zdb_ids == 0) {
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

              titleInstance.tipps.each {
                it.lastSeen = new Date().getTime()
                it.save()
              }

              existing_noresults.each {
                it.status = status_closed
                it.save()
              }

              existing_multiple.each {
                it.status = status_closed
                it.save()
              }
            }
          }

          setNewTitleInfo(titleInstance, candidates[0])
        }
        else if (candidates.size() == 0){
          if (existing_noresults.size() > 0 && titleInstance.ids.findAll { it.namespace.value == 'issn' || it.namespace.value == 'eissn' || it.namespace.value == 'zdb' }.size() > 0) {
            log.debug("No ZDB result for ids of title ${titleInstance} (${titleInstance.ids.collect { it.value }})")

            if (titleInstance.reviewRequests.findAll { it.stdDesc == rr_no_results}.size() == 0) {
              reviewRequestService.raise(
                titleInstance,
                "Check for reference ID",
                "No ZDB matches for linked IDs",
                null,
                null,
                null,
                rr_no_results,
                editorialGroup
              )
            }
          }
        }
        else if (existing_multiple.size() == 0) {
          log.debug("Multiple ZDB-ID candidates for title ${titleInstance}")

          def name_candidates = []

          candidates.each {
            if (KBComponent.generateNormname(it.title) == titleInstance.normname) {
              name_candidates.add (it)
            }
          }

          if (name_candidates.size() == 1) {
            Identifier new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', name_candidates[0].id)
            def conflicts = Combo.executeQuery("from Combo as c where c.fromComponent IN (select ti from TitleInstance as ti where ti.status != :deleted) and c.fromComponent != :tic and c.toComponent = :idc and c.type = :ctype", [deleted: status_deleted, tic: titleInstance, idc: new_id, ctype: idComboType])

            if (conflicts) {
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

              titleInstance.tipps.each {
                it.lastSeen = new Date().getTime()
                it.save()
              }

              existing_noresults.each {
                it.status = status_closed
                it.save()
              }

              existing_multiple.each {
                it.status = status_closed
                it.save()
              }
            }
          }
          else {
            log.debug("Multiple ZDB-ID candidates, but no unique name match!")
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
              rr_multiple,
              editorialGroup
            )
          }
        }
      }
      else if (num_existing_zdb_ids > 1) {
        log.debug("Skipping title with multiple existing ZDB-IDs ..")
        RefdataValue rr_merged = RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Merged ZDB titles')
        def existing_review = ReviewRequest.executeQuery("from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc = :type", [ti: titleInstance, type: rr_merged])

        if (!existing_review) {
          reviewRequestService.raise(
            titleInstance,
            "Choose the correct ZDB-ID from the list of candidates",
            "Multiple ZDB-IDs connected to a single title",
            null,
            null,
            null,
            rr_merged,
            editorialGroup
          )
        }
      }
      else {
        log.debug("Skipping title with existing RR ..")
      }
    }
  }

  def augmentEzb(titleInstance) {
    log.debug("Augment EZB - TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    CuratoryGroup editorialGroup = grailsApplication.config.gokb.ezbAugment?.rrCurators ?
        (CuratoryGroup.findByNameIlike(grailsApplication.config.gokb.ezbAugment.rrCurators) ?: new CuratoryGroup(name: grailsApplication.config.gokb.ezbAugment.rrCurators).save(flush: true)) : null

    if ( titleInstance.niceName == 'Journal' ) {
      def rr_multi_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple EZB Results')
      def rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'EZB Title Overlap')
      def rr_info = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Information')
      def existing_rr = ReviewRequest.executeQuery("select rr.id from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc IN (:types)", [ti: titleInstance, types: [rr_multi_results, rr_in_use]])

      if (existing_rr.size() == 0) {
        def ezbCandidates = ezbAPIService.lookup(titleInstance.name, titleInstance.ids)
        RefdataValue comboTypeId = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        RefdataValue statusDeleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")
        String ezbId
        if (ezbCandidates.size() == 1) {
          // 1 EZB match ==> create Combo from ReviewRequest to EZB identifier
          ezbId = EzbAPIService.getJourId(ezbCandidates[0])
          def newOrExistingEzbId = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb', ezbId)
          new Combo(fromComponent: titleInstance, toComponent: newOrExistingEzbId, type: comboTypeId).save(flush: true, failOnError: true)
          log.debug("Added new EZB-ID ${newOrExistingEzbId} .")
        }
        else if (ezbCandidates.size() == 0){
          // no EZB match ==> raise ReviewRequest with type Information
          if (titleInstance.ids.collect { it.namespace.value == 'issn' || it.namespace.value == 'eissn' }) {
            log.debug("No EZB result for ids of title ${titleInstance} (${titleInstance.ids.collect { it.value }})")
            if (titleInstance.reviewRequests.collect {it.stdDesc == rr_info}.size() == 0) {
              reviewRequestService.raise(
                  titleInstance,
                  "Check for reference ID",
                  "No EZB matches for linked ISSNs",
                  null,
                  null,
                  null,
                  rr_info,
                  editorialGroup
              )
            }
          }
        }
        else {
          log.debug("Multiple EZB-ID candidates for title ${titleInstance}")
          def nameCandidates = []
          ezbCandidates.each {
            if (it.title == titleInstance.name) {
              nameCandidates.add (it)
            }
          }
          if (nameCandidates.size() == 1) {
            // found 1 EZB match by name matching
            ezbId = EzbAPIService.getJourId(nameCandidates[0])
            def newOrExistingEzbId = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb', ezbId)
            log.debug("Adding new EZB-ID ${newOrExistingEzbId}")
            new Combo(fromComponent: titleInstance, toComponent: newOrExistingEzbId, type: comboTypeId).save(flush: true, failOnError: true)
          }
          else if (nameCandidates.size() == 0) {
            // found multiple matches by ID matching but 0 EZB match by name matching (very unlikely)
            if (titleInstance.ids.collect { it.namespace.value == 'issn' || it.namespace.value == 'eissn' }) {
              log.debug("Multiple EZB results for ID, but no EZB result for names of title ${titleInstance} (${titleInstance.ids.collect { it.value }})")
              if (titleInstance.reviewRequests.collect {it.stdDesc == rr_info}.size() == 0) {
                reviewRequestService.raise(
                    titleInstance,
                    "No action required.",
                    "No EZB matches for title name",
                    null,
                    null,
                    null,
                    rr_info,
                    editorialGroup
                )
              }
            }
          }
          else {
            reviewRequestService.raise(
                titleInstance,
                "No action required.",
                "Multiple EZB-IDs found for ISSN and title name",
                null,
                null,
                ([candidates: ezbCandidates] as JSON).toString(),
                rr_info,
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

    if (titleInstance.name != info.title) {
      log.debug("Updating title name ${titleInstance.name} -> ${info.title}")
      def old_title = titleInstance.name
      titleInstance.name = info.title
      titleInstance.addVariantTitle(old_title)
    }

    titleInstance.save(flush: true)
  }


  def syncZdbInfo(Job j = null) {
    JournalInstance.withNewSession { session ->
      RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      RefdataValue combo_active = RefdataCategory.lookup("Combo.Status", "Active")
      RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
      IdentifierNamespace zdbNs = IdentifierNamespace.findByValue('zdb')
      int offset = 0
      int batchSize = 50
      def queryString = "from JournalInstance as ti where ti.status = :current and exists " +
                              "(Select ci from Combo as ci where ci.type = :ctype " +
                              "and ci.fromComponent = ti and ci.toComponent.namespace = :ns " +
                              "and ci.status = :active)"
      def params = [current: status_current, active: combo_active, ctype: idComboType, ns: zdbNs]
      def count_journals_with_zdb_id = JournalInstance.executeQuery("select count(ti.id) ${queryString}".toString(), params)[0]

      // find the next 100 titles that do have a ZDB-ID
      while (offset < count_journals_with_zdb_id) {
        def journals_with_zdb_id = JournalInstance.executeQuery("select ti.id ${queryString}".toString(), params, [offset: offset, max: batchSize])

        log.debug("Processing ${count_journals_with_zdb_id}")

        journals_with_zdb_id.each { ti_id ->
          def ti = TitleInstance.get(ti_id)
          log.debug("Attempting augment on ${ti.id} ${ti.name}")
          augmentZdb(ti)
        }

        session.flush()
        session.clear()

        offset += batchSize
        j?.setProgress(offset, count_journals_with_zdb_id)

        if (Thread.currentThread().isInterrupted()) {
          break
        }
      }
      j?.endTime = new Date()
    }
  }
}
