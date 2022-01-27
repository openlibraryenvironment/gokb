package org.gokb

import org.gokb.refine.*
import org.gokb.cred.*

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
      def rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'ZDB Title Overlap')
      def status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
      def status_closed = RefdataCategory.lookup("ReviewRequest.Status", "Closed")
      def existing_inuse = ReviewRequest.executeQuery("from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc = :type and rr.status = :status", [ti: titleInstance, type: rr_in_use, status: status_open])

      if (existing_inuse?.size() == 0) {
        def rr_no_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'No ZDB Results')
        def rr_multiple = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple ZDB Results')
        def existing_noresults = ReviewRequest.executeQuery("from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc = :type", [ti: titleInstance, type: rr_no_results])
        def existing_multiple = ReviewRequest.executeQuery("from ReviewRequest as rr where rr.componentToReview = :ti and rr.stdDesc = :type", [ti: titleInstance, type: rr_multiple])
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

          setNewTitleInfo(titleInstance, candidates[0])
        }
        else if (candidates.size == 0){
          if (existing_noresults.size() == 0 && titleInstance.ids.collect { it.namespace.value == 'issn' || it.namespace.value == 'eissn' }) {
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
        else if (existing_multiple.size() == 0) {
          log.debug("Multiple ZDB-ID candidates for title ${titleInstance}")

          def name_candidates = []

          candidates.each {
            if (KBComponent.generateNormname(it.title) == titleInstance.normname) {
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
          else if (candidates.size() > 1) {
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
    }

    titleInstance.save(flush: true)
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

  def TitleInstance addPerson (person_name, role, ti, user = null, project = null) {
    if (person_name && person_name.trim()) {
      def norm_person_name = KBComponent.generateNormname(person_name)
      def person = org.gokb.cred.Person.findAllByNormname(norm_person_name)
      // log.debug("this was found for person: ${person}");
      switch (person.size()) {
        case 0:
          // log.debug("Person lookup yielded no matches.")
          def the_person = new Person(name: person_name, normname: norm_person_name)

          if (the_person.save(failOnError: true, flush: true)) {
            // log.debug("saved ${the_person.name}")
            person << the_person
            ReviewRequest.raise(
            ti,
            "'${the_person}' added as ${role.value} of '${ti.name}'.",
              "This person did not exist before, so has been newly created",
            user, project)
          }
          else {
            the_person.errors.each { error ->
              log.error("problem saving ${the_person.name}:${error}")
            }
          }
        case 1:
          def people = ti.getPeople() ?: []
          // log.debug("ti.getPeople ${people}")
          // Has the person ever existed in the list against this title.
          boolean done = false;
          for (cp in people) {
            if (!done && cp.person.id == person[0].id && cp.role.id == role.id) {
              done = true;
            }
          }

          if (!done) {
            def componentPerson = new ComponentPerson(component: ti, person: person, role: role)

            // log.debug("people did not contain this person")
            // First person added?

            boolean not_first = people.size() > 0
            boolean added = componentPerson.save(failOnError: true, flush: true)

            if (!added) {
              componentPerson.errors.each { error ->
                log.error("problem saving ${componentPerson}:${error}")
              }
            }

            // Raise a review request, if needed.
            if (not_first && added) {
              ReviewRequest.raise( ti,
                      "Added '${person.name}' as ${role.value} on '${ti.name}'.",
                      "Person supplied in ingested file is additional to any already present on BI.",
                      user,
                      project)
            }
          }
          break
        default:
        // log.debug ("Person lookup yielded ${person.size()} matches. Not really sure which person to use, so not using any.")
          break
      }
    }
    ti
  }

  def TitleInstance addSubjects(the_subjects, the_title) {
    if (the_subjects) {
      for (the_subject in the_subjects) {
        def norm_subj_name = KBComponent.generateNormname(the_subject)
        def subject = Subject.findAllByNormname(norm_subj_name) //no alt names for subjects
        // log.debug("this was found for subject: ${subject}")
        if (!subject) {
          // log.debug("subject not found, creating a new one")
          subject = new Subject(name: the_subject, normname: norm_subj_name)
          subject.save(failOnError: true, flush: true)
        }
        boolean done = false
        def componentSubjects = the_title.subjects ?: []

        for (cs in componentSubjects) {
          if (!done && cs.subject.id == subject.id) {
            done = true
          }
        }
        if (!done) {
          def cs = new ComponentSubject(component: the_title, subject: subject)
          cs.save(failOnError: true, flush: true)
        }
      }
    }
    the_title
  }

  def TitleInstance addPublisher (publisher_name, ti, user = null, project = null) {
    if (publisher_name != null && publisher_name.trim()) {
      log.debug("Add publisher \"${publisher_name}\"")
      Org publisher = Org.findByName(publisher_name)
      def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      def norm_pub_name = Org.generateNormname(publisher_name)

      if (!publisher) {
        // Lookup using norm name.
        log.debug("Using normname \"${norm_pub_name}\" for lookup")
        publisher = Org.findByNormname(norm_pub_name)
      }

      if (!publisher || publisher.status == status_deleted) {
        def variant_normname = GOKbTextUtils.normaliseString(publisher_name)
        def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status <> ?", [variant_normname, status_deleted])

        if (candidate_orgs.size() == 1) {
          publisher = candidate_orgs[0]
        }
        else if (candidate_orgs.size() == 0) {
          publisher = new Org(name: publisher_name, normname: norm_pub_name).save(flush: true, failOnError: true)
        }
        else {
          log.error("Unable to match unique pub")
        }
      }

      // Found a publisher.
      if (publisher) {
        log.debug("Found publisher ${publisher}")
        def orgs = ti.getPublisher()

        // Has the publisher ever existed in the list against this title.
        if (!orgs.contains(publisher)) {

          // First publisher added?
          boolean not_first = orgs.size() > 0

          // Added a publisher?
          ti.changePublisher(publisher)
        }
      }
    }

    ti
  }
}
