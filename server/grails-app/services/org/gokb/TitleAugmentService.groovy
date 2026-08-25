package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.*

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDateTime

import org.gokb.DomainClassExtender
import org.gokb.cred.*
import org.gokb.GOKbTextUtils

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


  public Map augmentZdb(titleInstance) {
    log.debug("Augment ZDB - TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    Map result = [result: 'OK']
    RefdataValue status_active = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_ACTIVE)
    String group_name = grailsApplication.config.getProperty('gokb.zdbAugment.rrCurators')
    CuratoryGroup editorialGroup = group_name ? (CuratoryGroup.findByNameIlike(group_name) ?: new CuratoryGroup(name: group_name).save(flush: true)) : null
    int num_existing_zdb_ids = Identifier.executeQuery('''select count(*) from Identifier as ido
                                                          where namespace.value = 'zdb'
                                                          and exists (
                                                            select 1 from ComponentIdentifier
                                                            where component = :ti
                                                            and identifier = ido
                                                            and status = :ca
                                                          )'''
                                                          [ti: titleInstance, ca: status_active])[0]

    if (titleInstance.niceName == 'Journal') {
      RefdataValue rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'ZDB Title Overlap')
      RefdataValue rr_status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
      RefdataValue rr_status_closed = RefdataCategory.lookup("ReviewRequest.Status", "Closed")
      RefdataValue rr_status_deleted = RefdataCategory.lookup("ReviewRequest.Status", "Deleted")
      List existing_inuse = ReviewRequest.executeQuery('''from ReviewRequest as rr
                                                          where rr.componentToReview = :ti
                                                          and rr.stdDesc = :type
                                                          and rr.status = :status''',
                                                          [ti: titleInstance, type: rr_in_use, status: rr_status_open])

      if (existing_inuse.size() == 0 && num_existing_zdb_ids <= 1) {
        RefdataValue rr_no_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'No ZDB Results')
        RefdataValue rr_multiple = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple ZDB Results')
        RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

        List existing_noresults = ReviewRequest.executeQuery('''from ReviewRequest as rr
                                                            where rr.componentToReview = :ti
                                                            and rr.stdDesc = :type
                                                            and rr.status != :sd ''',
                                                            [ti: titleInstance, type: rr_no_results, sd: rr_status_deleted])
        List existing_multiple = ReviewRequest.executeQuery('''from ReviewRequest as rr
                                                            where rr.componentToReview = :ti
                                                            and rr.stdDesc = :type''',
                                                            [ti: titleInstance, type: rr_multiple])

        List ids = Identifier.executeQuery('''from Identifier as ido
                                              where exists (
                                                select 1 from ComponentIdentifier
                                                where component = :ti
                                                and identifier = ido
                                                and status = :sca
                                              )''', [ti: titleInstance, sca: status_active])

        Map lookup_result = zdbAPIService.lookup(titleInstance.name, ids)

        if (lookup_result.result.startsWith('ERROR')) {
          result = lookup_result

          return result
        }

        List candidates = lookup_result.candidates

        if (candidates.size() == 1) {
          if (num_existing_zdb_ids == 0) {
            Identifier new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', candidates[0].id)
            List conflicts = Combo.executeQuery('''from ComponentIdentifier as c
                                                    where exists (
                                                      select ti from JournalInstance as ti
                                                      where ti.status != :deleted
                                                      and ti.id = c.component.id
                                                    )
                                                    and c.component != :tic
                                                    and c.identifier = :idc
                                                    and c.status = :cstatus''',
                                                    [deleted: status_deleted, tic: titleInstance, idc: new_id, cstatus: status_active])

            if (conflicts.size() > 0) {
              log.debug("Matched ZDB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${conflicts*.component}")

              if (conflicts.size() == 1) {
                setNewTitleInfo(JournalInstance.get(conflicts[0].component.id), candidates[0])
              }

              Map additionalInfo = [
                otherComponents: conflicts.collect {
                  [
                    id: it.component.id,
                    name: it.component.name,
                    oid: it.component.logEntityId,
                    uuid: it.component.uuid
                  ]
                }
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

              result.result = 'SKIPPED_NEW_REVIEW_IN_USE'
              result.new_review = rr_in_use.value
            }
            else if (new_id) {
              log.debug("Adding new ZDB-ID ${new_id}")
              new ComponentIdentifier(component: titleInstance, identifier: new_id).save(flush: true)

              touchTitleTipps(titleInstance)

              existing_noresults.each {
                it.status = rr_status_closed
                it.save()
              }

              existing_multiple.each {
                it.status = rr_status_closed
                it.save()
              }

              result.result = 'ID_LINKED'
            }
            else {
              log.error("Unable to get ZDB-ID to link!")
            }
          }
          else {
            log.debug("Found record for existing ZDB-ID..")
            result.result = 'MATCH_UPDATED'
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

            result.result = 'NO_MATCH'
          }
        }
        else {
          log.debug("Multiple ZDB-ID candidates for title ${titleInstance}")

          List name_candidates = []

          candidates.each {
            if (KBComponent.generateNormname(it.title) == titleInstance.normname) {
              name_candidates.add (it)
            }
          }

          if (name_candidates.size() == 1) {
            Identifier new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('zdb', name_candidates[0].id)
            List conflicts = ComponentIdentifier.executeQuery('''from ComponentIdentifier as c
                                                    where exists (
                                                      select ti from JournalInstance as ti
                                                      where ti.status != :deleted
                                                      and ti.id = c.component.id
                                                    )
                                                    and c.component != :tic
                                                    and c.identifier = :idc''',
                                                    [deleted: status_deleted, tic: titleInstance, idc: new_id])

            if (conflicts.size() > 0) {
              log.debug("Matched ZDB-ID ${new_id.namespace.value}:${new_id.value} is already connected to other instances: ${conflicts*.component}")

              if (conflicts.size() == 1) {
                setNewTitleInfo(JournalInstance.get(conflicts[0].component.id), name_candidates[0])
              }

              Map additionalInfo = [
                otherComponents: conflicts.collect {
                  [
                    id: it.component.id,
                    name: it.component.name,
                    oid: it.component.logEntityId,
                    uuid: it.component.uuid
                  ]
                }
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

              result.result = 'SKIPPED_NEW_REVIEW_IN_USE'
              result.new_review = rr_in_use.value
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
            Map additionalInfo = [
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

            result.result = 'SKIPPED_NEW_REVIEW_MULTIPLE_CANDIDATES'
            result.new_review = rr_multiple.value
          }
          else {
            result.result = 'SKIPPED_EXISTING_REVIEW_MULTIPLE_CANDIDATES'
          }
        }
      }
      else if (num_existing_zdb_ids > 1) {
        log.debug("Skipping title with multiple existing ZDB-IDs ..")
        RefdataValue rr_merged = RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Merged ZDB titles')
        List existing_review = ReviewRequest.executeQuery('''from ReviewRequest as rr
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

          result.result = 'SKIPPED_NEW_REVIEW_MERGED_IDS'
          result.new_review = rr_merged.value
        }
        else {
          result.result = 'SKIPPED_EXISTING_REVIEW_MERGED_IDS'
        }
      }
      else {
        log.debug("Skipping title with existing RR ..")
        result.result = 'SKIPPED_EXISTING_REVIEW_IN_USE'
      }
    }
    else {
      result.result = 'ERROR'
      result.message = "Skipped ZDB augment for ${titleInstance}"
    }

    result
  }

  public void touchTitleTipps (ti, boolean onlyCurrent = true, boolean skipPackageUpdate = false) {
    Date current_ts = new Date()
    RefdataValue combo_title = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')
    RefdataValue combo_package = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    Map qry_params = [now: current_ts, ct: combo_title, title: ti]
    String qry_string = '''update TitleInstancePackagePlatform as tipp set lastUpdated = :now where tipp.title = :title'''

    if (onlyCurrent) {
      qry_string += ' and status = :sc'
      qry_params.sc = status_current
    }
    else {
      qry_string += ' and status != :sd'
      qry_params.sd = status_deleted
    }

    TitleInstancePackagePlatform.executeUpdate(qry_string, qry_params)

    if (!skipPackageUpdate) {
      if (onlyCurrent) {
        Package.executeUpdate('''update Package as pkg
                                  set lastUpdated = :now
                                  where exists (
                                    select 1 from TitleInstancePackagePlatform as tipp
                                    where tipp.title = :title
                                    and status = :sc
                                  )''',
                                  qry_params)
      }
      else {
        Package.executeUpdate('''update Package as pkg
                                  set lastUpdated = :now
                                  where exists (
                                    select 1 from TitleInstancePackagePlatform as tipp
                                    where tipp.title = :title
                                    and status != :sd
                                  )''',
                                  qry_params)
      }
    }
  }

  public void augmentEzb(titleInstance) {
    log.debug("Augment EZB - TitleInstance: ${titleInstance.niceName} - ${titleInstance.class?.name}")
    String group_name = grailsApplication.config.getProperty('gokb.ezbAugment.rrCurators')
    CuratoryGroup editorialGroup = group_name ? (CuratoryGroup.findByNameIlike(group_name) ?: new CuratoryGroup(name: group_name).save(flush: true)) : null

    if ( titleInstance.niceName == 'Journal' ) {
      RefdataValue rr_multi_results = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple EZB Results')
      RefdataValue rr_in_use = RefdataCategory.lookup('ReviewRequest.StdDesc', 'EZB Title Overlap')
      RefdataValue rr_info = RefdataCategory.lookup('ReviewRequest.StdDesc', 'No EZB Results')
      List existing_rr = ReviewRequest.executeQuery('''select rr.id from ReviewRequest as rr
                                                    where rr.componentToReview = :ti
                                                    and rr.stdDesc IN (:types)''',
                                                    [ti: titleInstance, types: [rr_multi_results, rr_in_use]])

      if (existing_rr.size() == 0) {
        List ezbCandidates = ezbAPIService.lookup(titleInstance.name, titleInstance.ids)
        RefdataValue statusDeleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")
        String ezbId

        if (ezbCandidates.size() == 1) {
          // 1 EZB match ==> create link from ReviewRequest to EZB identifier
          ezbId = EzbAPIService.getJourId(ezbCandidates[0])
          Identifier new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb', ezbId)
          titleInstance.addIdentifier(new_id)
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
          List nameCandidates = []

          ezbCandidates.each {
            if (it.title == titleInstance.name) {
              nameCandidates.add (it)
            }
          }

          if (nameCandidates.size() == 1) {
            // found 1 EZB match by name matching
            ezbId = EzbAPIService.getJourId(nameCandidates[0])
            Identifier new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb', ezbId)
            titleInstance.addIdentifier(new_id)

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

  private void setNewTitleInfo(TitleInstance ti, info) {
    TitleInstance titleInstance = KBComponent.deproxy(ti)

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
      Org pub_obj = Org.findByNameAndStatusNot(info.publisher, status_deleted)

      if (!pub_obj) {
        String variant_normname = GOKbTextUtils.normaliseString(info.publisher)
        List var_candidates = Org.executeQuery('''select distinct p from Org as p
                                              join p.variantNames as v
                                              where v.normVariantName = :nvn
                                              and p.status <> :sd''',
                                              [nvn: variant_normname, sd: status_deleted])

        if (var_candidates.size() == 1) {
          pub_obj = var_candidates[0]
        }
      }

      if (pub_obj) {
        new TitlePublisher(title: ti, publisher: pub_obj).save(flush: true, failOnError: true)

        ti.lastUpdateComment = "Added title publisher ${pub_obj}"
        ti.save()
      }
    }

    try {
      info.history.each { he ->
        if (validationService.checkZdbId(he.zdbId)) {
          def candidates = Identifier.findByValueIlikeAndNamespace(he.zdbId, IdentifierNamespace.findByValue('zdb'))?.getActiveIdentifiedComponents('JournalInstance')

          if (candidates?.size() == 1 && candidates[0] != titleInstance) {
            LocalDateTime parsedLocal = he.prev ? GOKbTextUtils.completeDateString(info.publishedFrom) : GOKbTextUtils.completeDateString(he.publishedFrom ?: info.publishedTo)
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

    try {
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
    }
    catch (Exception e) {
      log.error("Error while processing ZDB ddc subject:", e)
    }

    if (titleInstance.name.toLowerCase() != info.title.toLowerCase()) {
      log.debug("Updating title name ${titleInstance.name} -> ${info.title}")
      def old_title = titleInstance.name
      titleInstance.name = info.title
      addVariantName(old_title, titleInstance)
    }

    titleInstance.save(flush: true)
  }

  public boolean editMonographFields(ti, updatedInfo, boolean onlyNew = false) {
    def book_changed = false

    ["editionDifferentiator",
     "editionStatement", "volumeNumber",
     "summaryOfContent", "firstAuthor",
     "firstEditor"].each { stringPropertyName ->
      if (updatedInfo[stringPropertyName] && updatedInfo[stringPropertyName].toString().trim() && (!onlyNew || !ti[stringPropertyName])) {
        book_changed |= ClassUtils.setStringIfDifferent(ti, stringPropertyName, updatedInfo[stringPropertyName])
      }
    }


    if (!onlyNew || !ti.dateFirstInPrint) {
      def dfip = null

      if (updatedInfo.dateFirstInPrint instanceof Date) {
        dfip = updatedInfo.dateFirstInPrint
      }
      else {
        dfip = GOKbTextUtils.completeDateString(updatedInfo.dateFirstInPrint)
      }

      book_changed |= ClassUtils.setDateIfPresent(dfip, ti, 'dateFirstInPrint')
    }

    if (!onlyNew || !ti.dateFirstOnline) {
      def dfo = null

      if (updatedInfo.dateFirstOnline instanceof Date) {
        dfo = updatedInfo.dateFirstOnline
      }
      else {
        dfo = GOKbTextUtils.completeDateString(updatedInfo.dateFirstOnline, false)
      }

      book_changed |= ClassUtils.setDateIfPresent(dfo, ti, 'dateFirstOnline')
    }

    book_changed
  }


  public Map syncZdbInfo(Job j = null, boolean unlinkedOnly = false, LocalDateTime created_since = null) {
    Map result = [result: 'OK', counts:[:]]

    JournalInstance.withNewSession { lsession ->
      RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      RefdataValue ci_active = RefdataCategory.lookup("ComponentIdentifier.Status", "Active")
      IdentifierNamespace zdbNs = IdentifierNamespace.findByValue('zdb')
      Float reduced_rate = null
      int offset = 0
      int batchSize = 50
      String queryString = "from JournalInstance as ti where ti.status = :current and "
      Date date_filter = created_since ? Date.from(created_since.atZone(ZoneOffset.UTC).toInstant()) : null

      Map params = [
        current: status_current,
        active: ci_active,
        ns: zdbNs
      ]

      if (!unlinkedOnly) {
        queryString += '''exists (
                          select ci from ComponentIdentifier as ci
                          where ci.type = :ctype
                          and ci.component = ti
                          and ci.identifier.namespace = :ns
                          and ci.status = :active
                        )'''
      }
      else {
        params.issns = [IdentifierNamespace.findByValue('issn'), IdentifierNamespace.findByValue('eissn')]

        queryString += '''not exists (
                          Select ci from ComponentIdentifier as ci
                          where ci.status = :active
                          and ci.component = ti
                          and ci.identifier.namespace = :ns
                        )
                        and exists (
                          Select ci from ComponentIdentifier as ci
                          where ci.status = :active
                          and ci.component = ti
                          and ci.identifier.namespace IN (:issns)
                        )'''
      }

      if (date_filter) {
        params.date = date_filter
        queryString += " and ti.dateCreated > :date"
      }

      result.total = JournalInstance.executeQuery("select count(ti.id) ${queryString}".toString(), params)
      List id_list = JournalInstance.executeQuery("select ti.id ${queryString}".toString(), params)

      result.total = id_list.size()

      log.debug("syncZdbInfo :: Processing ${result.total} journals ..")
      j.message("Processing ${result.total} journals ..".toString())

      // find the next 100 titles that do have a ZDB-ID

      for (ti_id in id_list) {
        TitleInstance ti = TitleInstance.get(ti_id)
        log.debug("Attempting augment on ${ti.id} ${ti.name}")

        Map augment_result = augmentZdb(ti)

        if (!result.counts[augment_result.result]) {
          result.counts[augment_result.result] = 1
        }
        else {
          result.counts[augment_result.result]++
        }

        if (augment_result.result == 'ERROR_RESPONSE') {
          if (augment_result.status == 503) {
            result.result = 'CANCELLED_UNAVAILABLE'
            break
          }
          else if (augment_result.status == 429 && augment_result.rate_limit) {

            try {
              reduced_rate = Float.parseFloat(augment_result.rate_limit)
            }
            catch (Exception e) {
              log.error("Unable to parse rate limit ${augment_result.rate_limit}!")
              result.result = 'CANCELLED_RATE_LIMIT_PARSE_ERROR'
              break
            }

            if (reduced_rate > 1) {
              result.result = 'CANCELLED_MAX_RATE_LIMIT'
              break
            }
          }
        }

        offset++

        j?.setProgress(offset, result.total)

        if (offset % 50 == 0) {
          lsession.flush()
          lsession.clear()
        }

        if (Thread.currentThread().isInterrupted() || j?.isCancelled()) {
          result.result = 'INTERRUPTED'
          break
        }
      }

      j?.endTime = new Date()
      j?.message('syncZdbInfo :: Finished processing')
      result.endTime = new Date()

      result
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

      RefdataValue ti_language = titleDTO.language ? RefdataCategory.lookup('KBComponent.Language', titleDTO.language) : null

      if (ti_language){
        result.language = ti_language
      }

      log.debug("Result of upsertDTO: ${result}")
    }
    result
  }

  public TitleInstance addPerson (person_name, role, ti, user = null, project = null) {
    if (person_name && person_name.trim()) {
      String norm_person_name = KBComponent.generateNormname(person_name)
      List person = org.gokb.cred.Person.findAllByNormname(norm_person_name)
      // log.debug("this was found for person: ${person}");
      switch (person.size()) {
        case 0:
          // log.debug("Person lookup yielded no matches.")
          Person the_person = new Person(name: person_name, normname: norm_person_name)

          if (the_person.save(failOnError: true, flush: true)) {
            // log.debug("saved ${the_person.name}")
            person << the_person
          }
          else {
            the_person.errors.each { error ->
              log.error("problem saving ${the_person.name}:${error}")
            }
          }
        case 1:
          List people = ti.getPeople() ?: []
          // log.debug("ti.getPeople ${people}")
          // Has the person ever existed in the list against this title.
          boolean done = false;

          for (cp in people) {
            if (!done && cp.person.id == person[0].id && cp.role.id == role.id) {
              done = true;
            }
          }

          if (!done) {
            ComponentPerson componentPerson = new ComponentPerson(component: ti, person: person, role: role)

            // log.debug("people did not contain this person")
            // First person added?

            boolean not_first = people.size() > 0
            boolean added = componentPerson.save(failOnError: true, flush: true)

            if (!added) {
              componentPerson.errors.each { error ->
                log.error("problem saving ${componentPerson}:${error}")
              }
            }

            if (not_first && added) {
              log.debug("Not adding duplicate person..")
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

  /**
   * Close off any existing publisher relationships and add a new one for this publiser
   */
  public boolean changePublisher(ti, new_publisher, boolean null_start = false) {

    if (new_publisher != null) {

      Org current_publisher = ti.currentPublisher

      if ((current_publisher != null) && (current_publisher.id == new_publisher.id)) {
        // no change... leave it be
        return false
      }
      else {
        List publisher_links = ti.publisherLinks

        publisher_links.each { pc ->
          if (pc.endDate == null) {
            pc.endDate = new Date()
            pc.save()
          }
        }

        // Now create a new TitlePublisher
        new TitlePublisher(title: ti, publisher: new_publisher, startDate: (null_start ? null : new Date())).save(flush:true)
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
      String norm_pub_name = Org.generateNormname(publisher_name);
      RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      RefdataValue status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

      if (!publisher) {
        // Lookup using norm name.
        log.debug("Using normname ${norm_pub_name} for lookup")
        publisher = Org.findByNormnameAndStatus(norm_pub_name, status_current)
      }

      if (!publisher || publisher.status == status_deleted) {
        String variant_normname = GOKbTextUtils.normaliseString(publisher_name)
        List candidate_orgs = Org.executeQuery('''select distinct o from Org as o join o.variantNames as v
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

      List existing_links = TitlePublisher.executeQuery("from TitlePublisher where title = :ti and publisher = :pub", [ti: ti, pub: publisher])

      if (publisher && existing_links.size() == 0) {
        new TitlePublisher(title: ti, publisher: publisher).save(flush: true, failOnError: true)
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

  public def addMissingDoiFromTipps(ti) {
    log.debug("addMissingDoiFromTipps for ${ti}")
    Map result = [result: 'OK', candidates: []]

    RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
    IdentifierNamespace doi_ns = IdentifierNamespace.findByValue('doi')

    List tipps = TitleInstancePackagePlatform.executeQuery('''from TitleInstancePackagePlatform as tipp
                                                              where status = :sc
                                                              and title = :ti''',
                                                              [
                                                                sc: status_current,
                                                                ti: ti
                                                              ])

    log.debug("Checking ${tipps.size()} tipps ..")

    tipps.each { tipp ->
      if (tipp.importId && !result.candidates.contains(tipp.importId) && validationService.checkIdForNamespace(tipp.importId, doi_ns)) {
        result.candidates << tipp.importId
      }
    }

    if (result.candidates.size() == 1) {
      log.debug("Found a single candidate DOI ..")
      Identifier new_id = componentLookupService.lookupOrCreateCanonicalIdentifier('doi', result.candidates[0])
      List linked_titles = new_id.getActiveIdentifiedComponents('TitleInstance')

      if (linked_titles.size() == 0) {
        new ComponentIdentifier(component: ti, identifier: new_id).save(flush: true)

        ti.lastUpdateComment = "Added new identifier ${new_id}"
        ti.save(flush: true, failOnError: true)

        touchTitleTipps(ti)

        result.result = 'LINKED'
      }
      else {
        if (linked_titles.contains(ti)) {
          log.debug("Not adding duplicate DOI id!")
          result.result = 'SKIPPED_ALREADY_LINKED'
        }
        else {
          log.debug("Found DOI is already linked to another TI ${linked_titles}!")
          result.otherLinks = linked_titles
          result.result = 'SKIPPED_EXISTING_LINKS'
        }
      }
    }
    else if (result.candidates.size() > 1) {
      log.debug("Found different DOIs (${result.candidates}) linked to TIPPs of ${ti}!")
      result.result = 'SKIPPED_MULTIPLE_CANDIDATES'
    }
    else {
      log.debug("No DOI candidates found ..")
      result.result = 'SKIPPED_NO_CANDIDATES'
    }

    result
  }

  @Transactional
  public Map mergeTitles(TitleInstance title_to_delete, TitleInstance merge_target_title, params) {
    log.debug("Starting title merge .. ${title_to_delete} -> ${merge_target_title}")
    Map errors = [:]
    RefdataValue status_active = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_ACTIVE)

    if (params.list('ids')?.size() > 0) {
      List unused_ids = title_to_delete.ids.collect { it.id }

      params.list('ids').each { tid ->
        Identifier idObj = Identifier.get(Long.valueOf(tid))

        if (idObj) {
          boolean is_duplicate = ComponentIdentifier.executeQuery('''Select c.id from ComponentIdentifier as c
                                                        where c.identifier = :ido
                                                        and c.component = :nt''',
                                                        [
                                                          ido: idObj,
                                                          nt: merge_target_title
                                                        ]).size() > 0

          if (!is_duplicate) {
            merge_target_title(idObj)
          }
          else {
            log.warn("merge :: Not adding multiple links between title ${merge_target_title} and ID ${idObj}!")
          }

          unused_ids.removeAll(idObj.id)
        }
        else {
          if (!errors.ids) {
            errors.ids = []
          }

          errors.ids << [message: 'Unable to reference ID object!', baddata: tid]
        }
      }

      // Transfer other ids and mark them as deleted

      // unused_ids.each { unused_id ->
      //   boolean is_active = ComponentIdentifier.executeQuery("select id from ComponentIdentifier where identifier.id = :unid and component = :ttd and status = :sa", [unid: unused_id, ttd: title_to_delete, status: status_active]).size() > 0

      //   if (is_active) {
      //     boolean is_duplicate = ComponentIdentifier.executeQuery("Select c.id from ComponentIdentifier as c where c.identifier.id = :ido and c.component = :nt and c.type = :ct", [ido: unused_id, nt: merge_target_title]).size() > 0

      //     if (!is_duplicate) {
      //       log.debug("Adding deselected Identifier ${unused_id} to ${merge_target_title} as deleted id.")
      //       Identifier inactive_id = Identifier.get(unused_id)

      //       new ComponentIdentifier(component: merge_target_title, identifier: inactive_id, status: status_active).save(flush: true, failOnError: true)
      //     }
      //   }
      // }
    }
    else if (params.boolean('mergeIds')) {
      title_to_delete.ids.each { old_id ->
        ComponentIdentifier old_link = ComponentIdentifier.findByFromComponentAndToComponent(title_to_delete, old_id)
        boolean is_duplicate = ComponentIdentifier.executeQuery('''Select c.id from ComponentIdentifier as c
                                                                    where c.toComponent = :ido
                                                                    and c.fromComponent = :nt
                                                                    and c.type = :ct''',
                                                                    [
                                                                      ido: old_id,
                                                                      nt: merge_target_title,
                                                                      ct: id_combo_type
                                                                    ]).size() > 0

        if (!is_duplicate){
          log.debug("Adding Identifier ${old_id} to ${merge_target_title}")
          new ComponentIdentifier(identifier: old_id, component: merge_target_title, status: old_link.status).save(flush: true, failOnError: true)
        }
        else{
          log.debug("Identifier ${old_id} is already connected to ${merge_target_title}..")
        }
      }
    }

    titleHistoryService.transferEvents(title_to_delete, merge_target_title)

    title_to_delete.refresh()

    if (params.list('tipps')?.size() > 0) {
      params.list('tipps').each { tipp ->
        TitleInstancePackagePlatform tippObj = TitleInstancePackagePlatform.get(Long.valueOf(tipp))

        if (tippObj.title == title_to_delete) {
          tippObj.title = merge_target_title
          tippObj.save(flush: true)
        }
      }
    }
    else if (params.boolean('mergeTipps')) {
      title_to_delete.tipps.each { tipp ->
        TitleInstancePackagePlatform tippObj = TitleInstancePackagePlatform.get(tipp.id)

        tippObj.title = merge_target_title
        tippObj.save(flush: true)
        merge_target_title.save(flush: true)

        log.debug("Changed TIPP title to ${tippObj.title}")
      }
    }

    if (params.boolean('transferName')) {
      merge_target_title.ensureVariantName(merge_target_title.name)
      merge_target_title.name = title_to_delete.name
      merge_target_title.save(flush: true)
    }

    title_to_delete.subjects.each { cs ->
      ComponentSubject existing = ComponentSubject.findByComponentAndSubject(merge_target_title, cs.subject)

      if (!existing) {
        new ComponentSubject(component: merge_target_title, subject: cs.subject).save(flush: true, failOnError: true)
      }
    }

    log.debug("Deleting stale title ${title_to_delete}")
    title_to_delete.deleteSoft()
    title_to_delete.save(flush: true)

    log.debug("Title is ${title_to_delete.status.value}!")

    errors
  }
}
