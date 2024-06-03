package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import grails.converters.JSON
import java.time.ZoneId
import org.gokb.DomainClassExtender
import org.gokb.cred.*
import org.gokb.GOKbTextUtils
import grails.gorm.transactions.*
import com.k_int.ClassUtils

class TitleAugmentService {

  def grailsApplication
  def componentLookupService
  def reviewRequestService
  def tippService
  def titleHistoryService
  def titleLookupService
  def validationService
  def zdbAPIService
  def ezbAPIService


  def augmentZdb(titleInstance) {
    log.debug("Augment ZDB - TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
    RefdataValue status_active = DomainClassExtender.comboStatusActive
    def group_name = grailsApplication.config.getProperty('gokb.zdbAugment.rrCurators')
    CuratoryGroup editorialGroup = group_name ? (CuratoryGroup.findByNameIlike(group_name) ?: new CuratoryGroup(name: group_name).save(flush: true)) : null
    int num_existing_zdb_ids = Combo.executeQuery('''select count(*) from Combo
                                                  where fromComponent = :ti
                                                  and type = :tid
                                                  and status = :ca
                                                  and toComponent in (
                                                    select ido from Identifier as ido
                                                    where namespace.value = 'zdb'
                                                  )''',
                                                  [ti: titleInstance, tid: idComboType, ca: status_active])[0]

    if (titleInstance.niceName == 'Journal') {
      RefdataValue rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'ZDB Title Overlap')
      RefdataValue rr_status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
      RefdataValue rr_status_closed = RefdataCategory.lookup("ReviewRequest.Status", "Closed")
      RefdataValue rr_status_deleted = RefdataCategory.lookup("ReviewRequest.Status", "Deleted")
      def existing_inuse = ReviewRequest.executeQuery('''from ReviewRequest as rr
                                                      where rr.componentToReview = :ti
                                                      and rr.stdDesc = :type
                                                      and rr.status = :status''',
                                                      [ti: titleInstance, type: rr_in_use, status: rr_status_open])

      if (existing_inuse.size() == 0 && num_existing_zdb_ids <= 1) {
        RefdataValue rr_no_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'No ZDB Results')
        RefdataValue rr_multiple = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple ZDB Results')
        RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

        def existing_noresults = ReviewRequest.executeQuery('''from ReviewRequest as rr
                                                            where rr.componentToReview = :ti
                                                            and rr.stdDesc = :type
                                                            and rr.status != :sd ''',
                                                            [ti: titleInstance, type: rr_no_results, sd: rr_status_deleted])
        def existing_multiple = ReviewRequest.executeQuery('''from ReviewRequest as rr
                                                            where rr.componentToReview = :ti
                                                            and rr.stdDesc = :type''',
                                                            [ti: titleInstance, type: rr_multiple])

        def ids = Identifier.executeQuery('''from Identifier as ido
                                          where exists (
                                            select 1 from Combo
                                            where fromComponent = :ti
                                            and toComponent = ido
                                            and status = :sca
                                          )''', [ti: titleInstance, sca: status_active])

        def candidates = zdbAPIService.lookup(titleInstance.name, ids)

        if (candidates.size() == 1) {
          if (num_existing_zdb_ids == 0) {
            def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', candidates[0].id)
            def conflicts = Combo.executeQuery('''from Combo as c
                                                where exists (
                                                  select ti from JournalInstance as ti
                                                  where ti.status != :deleted
                                                  and ti.id = c.fromComponent.id
                                                )
                                                and c.fromComponent != :tic
                                                and c.toComponent = :idc
                                                and c.status = :cstatus
                                                and c.type = :ctype''',
                                                [deleted: status_deleted, tic: titleInstance, idc: new_id, ctype: idComboType, cstatus: status_active])

            if (conflicts.size() > 0) {
              log.debug("Matched ZDB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${conflicts*.fromComponent}")

              if (conflicts.size() == 1) {
                setNewTitleInfo(JournalInstance.get(conflicts[0].fromComponent.id), candidates[0])
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
            else if (new_id) {
              log.debug("Adding new ZDB-ID ${new_id}")
              new Combo(fromComponent: titleInstance, toComponent: new_id, type: idComboType).save(flush: true)

              titleInstance.tipps.each {
                def tobj = TitleInstancePackagePlatform.get(it.id)
                tobj.lastSeen = new Date().getTime()
                tobj.save()

                tippService.touchPackage(tobj)
              }

              existing_noresults.each {
                it.status = rr_status_closed
                it.save()
              }

              existing_multiple.each {
                it.status = rr_status_closed
                it.save()
              }
            }
            else {
              log.error("Unable to get ZDB-ID to link!")
            }
          }

          setNewTitleInfo(titleInstance, candidates[0])
        }
        else if (candidates.size() == 0){
          if (existing_noresults.size() == 0 && ids.findAll { it.namespace.value == 'issn' || it.namespace.value == 'eissn' || it.namespace.value == 'zdb' }.size() > 0) {
            log.debug("No ZDB result for ids of title ${titleInstance} (${ids.collect { it.value }})")

            // if (titleInstance.reviewRequests.findAll { it.stdDesc == rr_no_results}.size() == 0) {
            //   reviewRequestService.raise(
            //     titleInstance,
            //     "Check for reference ID",
            //     "No ZDB matches for linked IDs",
            //     null,
            //     null,
            //     null,
            //     rr_no_results,
            //     editorialGroup
            //   )
            // }
          }
        }
        else {
          log.debug("Multiple ZDB-ID candidates for title ${titleInstance}")

          def name_candidates = []

          candidates.each {
            if (KBComponent.generateNormname(it.title) == titleInstance.normname) {
              name_candidates.add (it)
            }
          }

          if (name_candidates.size() == 1) {
            Identifier new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', name_candidates[0].id)
            def conflicts = Combo.executeQuery('''from Combo as c
                                                where exists (
                                                  select ti from JournalInstance as ti
                                                  where ti.status != :deleted
                                                  and ti.id = c.fromComponent.id
                                                )
                                                and c.fromComponent != :tic
                                                and c.toComponent = :idc
                                                and c.type = :ctype''',
                                                [deleted: status_deleted, tic: titleInstance, idc: new_id, ctype: idComboType])

            if (conflicts.size() > 0) {
              log.debug("Matched ZDB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${conflicts*.fromComponent}")

              if (conflicts.size() == 1) {
                setNewTitleInfo(JournalInstance.get(conflicts[0].fromComponent.id), name_candidates[0])
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
              if (num_existing_zdb_ids == 0) {
                titleInstance.refresh()
                titleInstance.ids << new_id
                titleInstance.save(flush: true)

                touchTitleTipps(titleInstance)

                existing_noresults.each {
                  it.status = rr_status_closed
                  it.save()
                }

                existing_multiple.each {
                  it.status = rr_status_closed
                  it.save()
                }
              }

              setNewTitleInfo(titleInstance, name_candidates[0])
            }
          }
          else if (existing_multiple.size() == 0) {
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
        def existing_review = ReviewRequest.executeQuery('''from ReviewRequest as rr
                                                          where rr.componentToReview = :ti
                                                          and rr.stdDesc = :type
                                                          and rr.status != :sd ''',
                                                          [ti: titleInstance, type: rr_merged, sd: rr_status_deleted])

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

  public void touchTitleTipps (ti, boolean onlyCurrent = true) {
    Date current_ts = new Date()
    RefdataValue combo_title = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    def qry_params = [now: current_ts, ct: combo_title, title: ti]
    def qry_string = '''update TitleInstancePackagePlatform as tipp
        set lastUpdated = :now
        where exists (
          select 1 from Combo
          where type = :ct
          and fromComponent = :title
          and toComponent = tipp
        )'''

    if (onlyCurrent) {
      qry_string += ' and status = :sc'
      qry_params.sc = status_current
    }
    else {
      qry_string += ' and status != :sd'
      qry_params.sd = status_deleted
    }

    TitleInstancePackagePlatform.executeUpdate(qry_string, qry_params)
  }

  def augmentEzb(titleInstance) {
    log.debug("Augment EZB - TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    def group_name = grailsApplication.config.getProperty('gokb.ezbAugment.rrCurators')
    CuratoryGroup editorialGroup = group_name ? (CuratoryGroup.findByNameIlike(group_name) ?: new CuratoryGroup(name: group_name).save(flush: true)) : null

    if ( titleInstance.niceName == 'Journal' ) {
      def rr_multi_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple EZB Results')
      def rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'EZB Title Overlap')
      def rr_info = RefdataCategory.lookup('ReviewRequest.StdDesc', 'No EZB Results')
      def existing_rr = ReviewRequest.executeQuery('''select rr.id from ReviewRequest as rr
                                                    where rr.componentToReview = :ti
                                                    and rr.stdDesc IN (:types)''',
                                                    [ti: titleInstance, types: [rr_multi_results, rr_in_use]])

      if (existing_rr.size() == 0) {
        def ezbCandidates = ezbAPIService.lookup(titleInstance.name, titleInstance.ids)
        RefdataValue comboTypeId = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        RefdataValue statusDeleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")
        String ezbId
        if (ezbCandidates.size() == 1) {
          // 1 EZB match ==> create Combo from ReviewRequest to EZB identifier
          ezbId = EzbAPIService.getJourId(ezbCandidates[0])
          def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb', ezbId)
          titleInstance.ids << new_id
          titleInstance.save(flush: true)
        }
        else if (ezbCandidates.size() == 0){
          // no EZB match ==> raise ReviewRequest with type Information
          if (titleInstance.ids.findAll { it.namespace.value == 'issn' || it.namespace.value == 'eissn' }) {
            log.debug("No EZB result for ids of title ${titleInstance} (${titleInstance.ids.collect { it.value }})")
            if (titleInstance.reviewRequests.findAll {it.stdDesc == rr_info}.size() == 0) {
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
            def new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb', ezbId)
            titleInstance.ids << new_id
            titleInstance.save(flush: true)

            touchTitleTipps(titleInstance)
            log.debug("Adding new EZB-ID ${new_id}")
          }
          else if (nameCandidates.size() == 0) {
            // found multiple matches by ID matching but 0 EZB match by name matching (very unlikely)
            if (titleInstance.ids.findAll { it.namespace.value == 'issn' || it.namespace.value == 'eissn' }) {
              log.debug("Multiple EZB results for ID, but no EZB result for names of title ${titleInstance} (${titleInstance.ids.collect { it.value }})")
              if (titleInstance.reviewRequests.findAll {it.stdDesc == rr_info}.size() == 0) {
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
          else if (titleInstance.reviewRequests.findAll {it.stdDesc == rr_multi_results}.size() == 0) {
            reviewRequestService.raise(
                titleInstance,
                "No action required.",
                "Multiple EZB-IDs found for ISSN and title name",
                null,
                null,
                ([candidates: ezbCandidates] as JSON).toString(),
                rr_multi_results,
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

  private void setNewTitleInfo(ti, info) {
    def titleInstance = KBComponent.deproxy(ti)

    if (!titleInstance.publishedFrom && info.publishedFrom) {
      log.debug("Adding new start journal start date ..")
      ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(info.publishedFrom), titleInstance, 'publishedFrom')
    }
    if (!titleInstance.publishedTo && info.publishedTo) {
      log.debug("Adding new start journal end date ..")
      ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(info.publishedTo, false), titleInstance, 'publishedTo')
    }

    if (!titleInstance.currentPublisher && info.publisher) {
      RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")
      def pub_obj = Org.findByNameAndStatusNot(info.publisher, status_deleted)

      if (!pub_obj) {
        def variant_normname = GOKbTextUtils.normaliseString(info.publisher)
        def var_candidates = Org.executeQuery('''select distinct p from Org as p
                                              join p.variantNames as v
                                              where v.normVariantName = :nvn
                                              and p.status <> :sd''',
                                              [nvn: variant_normname, sd: status_deleted])

        if (var_candidates.size() == 1) {
          pub_obj = var_candidates[0]
        }
      }

      if (pub_obj) {
        def publisher_combo = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Publisher')
        titleInstance.publisher << pub_obj
      }
    }

    try {
      info.history.each { he ->
        if (validationService.checkZdbId(he.zdbId)) {
          def candidates = Identifier.findByValueIlikeAndNamespace(he.zdbId, IdentifierNamespace.findByValue('zdb'))?.getActiveIdentifiedComponents('JournalInstance')

          if (candidates?.size() == 1 && candidates[0] != titleInstance) {
            def parsedLocal = he.prev ? GOKbTextUtils.completeDateString(info.publishedFrom) : GOKbTextUtils.completeDateString(he.publishedFrom ?: info.publishedTo)
            Date event_date = null

            if (parsedLocal) {
              event_date = Date.from(parsedLocal.atZone(ZoneId.systemDefault()).toInstant())
            }

            titleHistoryService.addDirectEvent((he.prev ? candidates[0] : titleInstance), (he.prev ? titleInstance : candidates[0]), event_date)
          } else {
            log.debug("No usable candidates in ${candidates}..")
          }
        }
        else {
          log.debug("Skipping item with illegal ID value ${he.zdbId}!")
        }
      }
    }
    catch (Exception e) {
      log.error("Error while processing ZDB history event:", e)
    }

    RefdataValue scheme_ddc = RefdataCategory.lookup("Subject.Scheme", "ddc")

    info.ddc.each { notation ->
      if (notation ==~ /^\d{3}$/) {
        Subject subject = Subject.findBySchemeAndHeading(scheme_ddc, notation)

        if (!subject) {
          log.debug("Creating new subject for ${scheme_ddc} : $notation ..")
          subject = new Subject(scheme: scheme_ddc, heading: notation).save(flush: true)
        }

        ComponentSubject existing_link = ComponentSubject.findByComponentAndSubject(titleInstance, subject)

        if (!existing_link) {
          log.debug("Linking subject ${scheme_ddc} : $notation ..")
          new ComponentSubject(component:titleInstance, subject: subject).save(flush: true)
        } else {
          log.debug("Subject is already linked!")
        }
      }
      else {
        log.debug("ZDB augment :: Skipping linkage from ${titleInstance} to detailed DDC notation ${notation}!")
      }
    }

    if (titleInstance.name.toLowerCase() != info.title.toLowerCase()) {
      log.debug("Updating title name ${titleInstance.name} -> ${info.title}")
      def old_title = titleInstance.name
      titleInstance.name = info.title
      addVariantName(old_title, titleInstance)
    }

    titleInstance.save(flush: true)
  }

  public boolean editMonographFields(ti, updatedInfo) {
    def book_changed = false

    ["editionDifferentiator",
     "editionStatement", "volumeNumber",
     "summaryOfContent", "firstAuthor",
     "firstEditor"].each { stringPropertyName ->
      if (updatedInfo[stringPropertyName] && updatedInfo[stringPropertyName].toString().trim()) {
        book_changed |= ClassUtils.setStringIfDifferent(ti, stringPropertyName, updatedInfo[stringPropertyName])
      }
    }

    def dfip = GOKbTextUtils.completeDateString(updatedInfo.dateFirstInPrint)
    book_changed |= ClassUtils.setDateIfPresent(dfip, ti, 'dateFirstInPrint')

    def dfo = GOKbTextUtils.completeDateString(updatedInfo.dateFirstOnline, false)
    book_changed |= ClassUtils.setDateIfPresent(dfo, ti, 'dateFirstOnline')

    book_changed
  }


  def syncZdbInfo(Job j = null) {
    JournalInstance.withNewSession { lsession ->
      RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      RefdataValue combo_active = DomainClassExtender.comboStatusActive
      RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
      IdentifierNamespace zdbNs = IdentifierNamespace.findByValue('zdb')
      int offset = 0
      int batchSize = 50
      def count_journals_with_zdb_id
      def queryString = '''from JournalInstance as ti where ti.status = :current and exists (
                          select ci from Combo as ci
                          where ci.type = :ctype
                          and ci.fromComponent = ti
                          and ci.toComponent.namespace = :ns
                          and ci.status = :active
                        )'''
      def params = [current: status_current, active: combo_active, ctype: idComboType, ns: zdbNs]

      count_journals_with_zdb_id = JournalInstance.executeQuery("select count(ti.id) ${queryString}".toString(), params)[0]

      // find the next 100 titles that do have a ZDB-ID
      while (offset < count_journals_with_zdb_id) {
        def journals_with_zdb_id = JournalInstance.executeQuery("select ti.id ${queryString}".toString(), params, [offset: offset, max: batchSize])

        log.debug("Processing ${count_journals_with_zdb_id}")

        journals_with_zdb_id.each { ti_id ->
          def ti = TitleInstance.get(ti_id)
          log.debug("Attempting augment on ${ti.id} ${ti.name}")
          augmentZdb(ti)
        }

        offset += batchSize
        j?.setProgress(offset, count_journals_with_zdb_id)

        if (Thread.currentThread().isInterrupted() || j?.isCancelled()) {
          break
        }

        lsession.flush()
        lsession.clear()
      }
      j?.endTime = new Date()
    }
  }

  public TitleInstance upsertDTO(titleLookupService, titleDTO, user = null, fullsync = false) {
    def result = null;
    def type = null

    if (titleDTO.type) {
      switch (titleDTO.type.toLowerCase()) {
        case "serial":
        case "journal":
          type = "org.gokb.cred.JournalInstance"
          break;
        case "monograph":
        case "book":
          type = "org.gokb.cred.BookInstance"
          break;
        case "database":
          type = "org.gokb.cred.DatabaseInstance"
          break;
        case "other":
          type = "org.gokb.cred.OtherInstance"
          break;
        default:
          log.warn("Missing type for title!")
          break;
      }
    }

    if (type) {
      result = titleLookupService.findOrCreate(titleDTO.name,
        titleDTO.publisher,
        titleDTO.identifiers,
        user,
        null,
        type,
        titleDTO.uuid,
        fullsync
      )
      if (titleDTO.medium) {
        result.medium = determineMediumRef(titleDTO)
      }

      def ti_language = titleDTO.language ? RefdataCategory.lookup('KBComponent.Language', titleDTO.language) : null
      if (ti_language){
        result.language = ti_language
      }

      log.debug("Result of upsertDTO: ${result}")
    }
    result
  }

  public TitleInstance addPerson (person_name, role, ti, user = null, project = null) {
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

  public TitleInstance addSubjects(the_subjects, the_title) {
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
    the_title.save(flush: true)
    the_title
  }

  /**
   * Close off any existing publisher relationships and add a new one for this publiser
   */
  def changePublisher(ti, new_publisher, boolean null_start = false) {

    if (new_publisher != null) {

      def current_publisher = ti.currentPublisher
      def combo_active = DomainClassExtender.comboStatusActive

      if ((current_publisher != null) && (current_publisher.id == new_publisher.id)) {
        // no change... leave it be
        return false
      }
      else {
        def publisher_combos = getCombosByPropertyName('publisher')
        publisher_combos.each { pc ->
          if (pc.endDate == null) {
            pc.endDate = new Date();
          }
        }

        // Now create a new Combo
        RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, getComboTypeValue('publisher'))
        Combo combo = new Combo(
          type: (type),
          status: combo_active,
          startDate: (null_start ? null : new Date())
        )

        // Depending on where the combo is defined we need to add a combo.
        if (isComboReverse('publisher')) {
          combo.fromComponent = new_publisher
          addToIncomingCombos(combo)
        }
        else {
          combo.toComponent = new_publisher
          addToOutgoingCombos(combo)
        }
        combo.save(flush:true)
        //this.publisher.add(new_publisher)
        ti.save(flush:true)
        return true
      }
    }

    // Returning false if we get here implies the publisher has not been changed.
    return false
  }

  public void addPublisher (publisher_name, ti, boolean create = false) {
    if (publisher_name != null && publisher_name.trim()) {
      log.debug("Add publisher ${publisher_name}")
      Org publisher = Org.findByName(publisher_name)
      def norm_pub_name = Org.generateNormname(publisher_name);
      def status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

      if (!publisher) {
        // Lookup using norm name.
        log.debug("Using normname ${norm_pub_name} for lookup")
        publisher = Org.findByNormname(norm_pub_name)
      }

      if (!publisher || publisher.status == status_deleted) {
        def variant_normname = GOKbTextUtils.normaliseString(publisher_name)
        def candidate_orgs = Org.executeQuery('''select distinct o from Org as o join o.variantNames as v
                                              where v.normVariantName = :nvn
                                              and o.status != :sd''',
                                              [nvn: variant_normname, sd: status_deleted])

        if (candidate_orgs.size() == 1) {
          publisher = candidate_orgs[0]
        } else {
          log.debug("Unable to match unique pub ${publisher_name}")
        }
      }

      log.debug("Found publisher ${publisher}")
      def orgs = ti.getPublisher()
      log.debug("Check for dupes in ${orgs}")

      if (publisher && !orgs.contains(publisher)) {
        ti.publisher << publisher
        ti.save(flush: true)
        log.debug("Added new publisher ..")
      } else {
        log.debug("Not adding dupe")
      }
    }
    else {
      log.debug("Not adding empty string..")
    }
  }

  public void addVariantName(variant, ti) {
    if (variant.trim()) {

      // Variant names use different normalisation method.
      def variant_normname = GOKbTextUtils.normaliseString(variant)

      // not already a name
      // Make sure not already a variant name
      if (!KBComponentVariantName.findByOwnerAndNormVariantName(ti, variant_normname)) {
        new KBComponentVariantName(owner: ti, variantName: variant).save(flush: true)
      }
      else {
        log.debug("Unable to add ${variant} as an alternate name to ${ti} - it's already an alternate name....");
      }
    }
    else {
      log.error("No viable variant name supplied!")
    }
  }

  public void addIdentifiers(ids, ti) {
    ids.each { new_id ->
      def existing_combo = Combo.executeQuery("from Combo where fromComponent = :ti and toComponent = :nid", [ti: ti, nid: new_id])

      if (existing_combo.size() == 0) {
        ti.ids.add(ClassUtils.deproxy(new_id))
      } else {
        log.debug("Not adding duplicate ID ${new_id}..")
      }
    }
  }
}
