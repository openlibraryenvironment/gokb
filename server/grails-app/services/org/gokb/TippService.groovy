package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.*

import org.gokb.cred.*
import org.gokb.rest.TippController
import org.grails.web.json.JSONObject

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TippService {
  def componentUpdateService
  def componentLookupService
  def titleLookupService
  def titleAugmentService
  def sessionFactory
  def reviewRequestService
  def autoTimestampEventListener
  def validationService

  def validateDTO(tipp_dto) {
    def result = [valid: true]
    def errors = [:]
    def pkgLink = tipp_dto.pkg ?: tipp_dto.package
    def pltLink = tipp_dto.hostPlatform ?: tipp_dto.platform
    def tiLink = tipp_dto.title

    if (!pkgLink) {
      result.valid = false
      errors.pkg = [[message: "Missing package link!", baddata: pkgLink]]
    }
    else {
      def pkg = null

      if (pkgLink instanceof Map) {
        pkg = Package.get(pkgLink.id ?: pkgLink.internalId)
      }
      else {
        pkg = Package.get(pkgLink)
      }

      if (!pkg) {
        result.valid = false
        errors.pkg = [[message: "Could not resolve package id!", baddata: pkgLink, code: 404]]
      }
    }

    if (!pltLink) {
      result.valid = false
      errors.hostPlatform = [[message: "Missing platform link!", baddata: pltLink]]
    }
    else {
      def plt = null

      if (pltLink instanceof Map) {
        plt = Platform.get(pltLink.id ?: pltLink.internalId)
      }
      else {
        plt = Platform.get(pltLink)
      }

      if (!plt) {
        result.valid = false
        errors.hostPlatform = [[message: "Could not resolve platform id!", baddata: pltLink, code: 404]]
      }
    }

    // since a tipp is valid without a title connection, the validation of the tipp should drop this
    // precondition too
    if (tiLink) {
      def ti = null

      if (tiLink instanceof Map) {
        ti = TitleInstance.get(tiLink.id ?: tiLink.internalId)
      }
      else {
        ti = TitleInstance.get(tiLink)
      }

      if (!ti) {
        result.valid = false
        errors.title = [
          [
            message: "Could not resolve title id!",
            baddata: tiLink,
            code: 404
          ]
        ]
      }
    }

    def ids_list = tipp_dto.ids ?: tipp_dto.identifiers

    if (ids_list) {
      ids_list.each { idobj ->
        def ns_val = idobj.type ?: idobj.namespace

        if (ns_val) {
          def namespace = null

          if (ns_val instanceof String) {
            namespace = IdentifierNamespace.findByValueIlike(ns_val)
          }
          else if (ns_val instanceof Map) {
            namespace = IdentifierNamespace.findByValueIlike(ns_val.value)
          }
          else if (ns_val instanceof Integer) {
            namespace = IdentifierNamespace.get(ns_val)
          }

          if (namespace) {
            def valid_val = validationService.checkIdForNamespace(idobj.value, namespace)

            if (!valid_val) {
              if (!errors.ids) {
                errors.ids = []
              }

              errors.ids << [
                message: "Invalid identifier value ${namespace.value}:${idobj.value}!",
                baddata: idobj,
                messageCode: 'component.identifier.validation.value'
              ]
            }
          }
          else {
            if (!errors.ids) {
              errors.ids = []
            }

            errors.ids << [
              message: "Unable to reference namespace ${ns_val} for identifier value ${idobj.value}!",
              baddata: idobj,
              messageCode: 'component.identifier.validation.namespace',
              code: 400
            ]
          }
        }
        else {
          if (!errors.ids) {
            errors.ids = []
          }

          errors.ids << [
            message: "Missing namespace info for ID value ${idobj.value}",
            baddata: idobj,
            code: 400
          ]
        }
      }
    }

    if (tipp_dto.coverageStatements && !tipp_dto.coverage) {
      tipp_dto.coverage = tipp_dto.coverageStatements
    }

    for (def coverage : tipp_dto.coverage) {
      LocalDateTime parsedStart = GOKbTextUtils.completeDateString(coverage.startDate)
      LocalDateTime parsedEnd = GOKbTextUtils.completeDateString(coverage.endDate, false)

      if (coverage.startDate && !parsedStart) {
        if (!errors.startDate) {
          errors.startDate = []
        }

        result.valid = false
        errors.startDate << [message: "Unable to parse coverage start date ${coverage.startDate}!", baddata: coverage.startDate]
      }

      if (coverage.endDate && !parsedEnd) {
        if (!errors.endDate) {
          errors.endDate = []
        }

        result.valid = false
        errors.endDate << [message: "Unable to parse coverage end date ${coverage.endDate}!", baddata: coverage.endDate]
      }

      if (!coverage.coverageDepth) {
        if (!errors.coverageDepth) {
          errors.coverageDepth = []
        }
        coverage.coverageDepth = "fulltext"
        errors.coverageDepth << [message: "Missing value for coverage depth: set to fulltext", baddata: coverage.coverageDepth]
      }
      else {
        if (coverage.coverageDepth instanceof String && !['fulltext', 'selected articles', 'abstracts'].contains(coverage.coverageDepth?.toLowerCase())) {
          if (!errors.coverageDepth) {
            errors.coverageDepth = []
          }

          result.valid = false
          errors.coverageDepth << [message: "Unrecognized value '${coverage.coverageDepth}' for coverage depth", baddata: coverage.coverageDepth]
        }
        else if (coverage.coverageDepth instanceof Integer) {
          try {
            def candidate = RefdataValue.get(coverage.coverageDepth)

            if (!candidate && candidate.owner.label == "TIPPCoverageStatement.CoverageDepth") {
              if (!errors.coverageDepth) {
                errors.coverageDepth = []
              }

              result.valid = false
              errors.coverageDepth << [message: "Illegal value '${coverage.coverageDepth}' for coverage depth", baddata: coverage.coverageDepth]
            }
          } catch (Exception e) {
            log.error("Exception $e caught in TIPP.validateDTO while coverageDepth instanceof Integer")
          }
        }
        else if (coverage.coverageDepth instanceof Map) {
          if (coverage.coverageDepth.id) {
            try {
              def candidate = RefdataValue.get(coverage.coverageDepth.id)

              if (!candidate && candidate.owner.label == "TIPPCoverageStatement.CoverageDepth") {
                if (!errors.coverageDepth) {
                  errors.coverageDepth = []
                }

                result.valid = false
                errors.coverageDepth << [message: "Illegal ID value '${coverage.coverageDepth.id}' for coverage depth", baddata: coverage.coverageDepth]
              }
            } catch (Exception e) {
              log.error("Exception $e caught in TIPP.validateDTO while coverageDepth instanceof Map")
            }
          }
          else if (coverage.coverageDepth.value || coverage.coverageDepth.name) {
            if (!['fulltext', 'selected articles', 'abstracts'].contains(coverage.coverageDepth?.toLowerCase())) {
              if (!errors.coverageDepth) {
                errors.coverageDepth = []
              }

              result.valid = false
              errors.coverageDepth << [message: "Unrecognized value '${coverage.coverageDepth}' for coverage depth", baddata: coverage.coverageDepth]
            }
          }
        }
      }

      if (parsedStart && parsedEnd && (parsedEnd < parsedStart)) {
        result.valid = false
        errors.endDate = [[message: "Coverage end date must not be prior to its start date!", baddata: coverage.endDate]]
      }
    }

    if (tipp_dto.medium) {
      def ref = determineMediumRef(tipp_dto.medium)
      if (ref == null)
        errors.put('medium', [message: "unknown", baddata: tipp_dto.remove('medium')])
      else
        tipp_dto.medium = ref.value
    }

    if (tipp_dto.publicationType) {
      def type = determinePubTypeRef(tipp_dto.publicationType)
      if (type == null)
        errors.put('publicationType', [message: "unknown", baddata: tipp_dto.remove('publicationType')])
      else
        tipp_dto.publicationType = type.value
    }

    if (tipp_dto.dateFirstInPrint) {
      LocalDateTime dfip = GOKbTextUtils.completeDateString(tipp_dto.dateFirstInPrint, false)
      if (!dfip) {
        errors.put('dateFirstInPrint', [message: "Unable to parse", baddata: tipp_dto.remove('dateFirstInPrint')])
      }
    }

    if (tipp_dto.dateFirstOnline) {
      LocalDateTime dfo = GOKbTextUtils.completeDateString(tipp_dto.dateFirstOnline, false)
      if (!dfo) {
        errors.put('dateFirstOnline', [message: "Unable to parse", baddata: tipp_dto.remove('dateFirstOnline')])
      }
    }

    if (tipp_dto.lastChangedExternal) {
      LocalDateTime lce = GOKbTextUtils.completeDateString(tipp_dto.lastChangedExternal, false)
      if (!lce) {
        errors.put('lastChangedExternal', [message: "Unable to parse", baddata: tipp_dto.remove('lastChangedExternal')])
      }
    }

    if (!result.valid) {
      log.warn("Tipp failed validation: ${tipp_dto} - pkg:${pkgLink} plat:${pltLink} ti:${tiLink} -- Errors: ${errors}")
    }

    if (errors.size() > 0) {
      result.errors = errors
    }
    return result
  }

  public static RefdataValue determineMediumRef(def mediumType) {
    if (mediumType instanceof String) {
      def rdv = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_MEDIUM, mediumType)

      if (rdv) {
        return rdv
      }
    }
    else if (mediumType instanceof Integer) {
      def rdv = RefdataValue.get(mediumType)

      if (rdv && rdv.owner == RefdataCategory.findByLabel(TitleInstancePackagePlatform.RD_MEDIUM)) {
        return rdv
      }
    }
    else if (mediumType instanceof Map && mediumType.id) {
      def rdv = RefdataValue.get(mediumType.id)

      if (rdv && rdv.owner == RefdataCategory.findByLabel(TitleInstancePackagePlatform.RD_MEDIUM)) {
        return rdv
      }
    }

    return null
  }

  public static RefdataValue determinePubTypeRef(def someType) {
    if (someType instanceof String) {
      RefdataValue pubType = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, someType)

      if (pubType) {
        return pubType
      }
    }
    else if (someType instanceof Integer) {
      RefdataValue pubType = RefdataValue.get(someType)

      if (pubType && pubType.owner == RefdataCategory.findByLabel(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE)) {
        return pubType
      }
    }
    else if (someType instanceof Map && someType.id) {
      RefdataValue pubType = RefdataValue.get(someType.id)

      if (pubType && pubType.owner == RefdataCategory.findByLabel(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE)) {
        return pubType
      }
    }
    return null
  }

  /**
   * updating the coverage of this TIPP with the coverageData in reqBody
   *
   * @param tipp the TIPP to be updated
   * @param reqBody data extracted from JSON
   * @return the updated TIPP
   */
  public def updateCoverage(tipp, reqBody) {
    def cov_list = reqBody.coverageStatements ?: reqBody.coverage
    def stale_coverage_ids = tipp.coverageStatements.collect { it.id }

    def changed = false

    cov_list?.each { c ->
      def parsedStart = GOKbTextUtils.completeDateString(c.startDate)
      def parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)

      def cs_match = false
      def startAsDate = (parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
      def endAsDate = (parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
      def conflict = false
      def conflicting_statements = []

      if (c.id) {
        def idMatch = TIPPCoverageStatement.findByOwnerAndId(tipp, c.id)

        if (idMatch) {
          log.debug("Matched statement by id")
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'startIssue', c.startIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'startVolume', c.startVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'endVolume', c.endVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'endIssue', c.endIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'embargo', c.embargo)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'coverageNote', c.coverageNote)
          changed |= com.k_int.ClassUtils.updateDateField(parsedStart, idMatch, 'startDate')
          changed |= com.k_int.ClassUtils.updateDateField(parsedEnd, idMatch, 'endDate')
          changed |= com.k_int.ClassUtils.setRefdataIfPresent(c.coverageDepth, idMatch, 'coverageDepth', 'TIPPCoverageStatement.CoverageDepth')

          cs_match = true
          stale_coverage_ids.removeAll(idMatch.id)
        }
        else {
          log.debug("No ID match for statement!")
        }
      }
      else {
        tipp.coverageStatements?.each { tcs ->
          if (!cs_match) {
            if (tcs.startVolume && tcs.startVolume == c.startVolume) {
              log.debug("Matched CoverageStatement by startVolume")
              cs_match = true
            }
            else if (tcs.startDate && tcs.startDate == startAsDate) {
              log.debug("Matched CoverageStatement by startDate")
              cs_match = true
            }
            else if (!tcs.startVolume && !tcs.startDate && !tcs.endVolume && !tcs.endDate) {
              log.debug("Matched CoverageStatement with unspecified values")
              cs_match = true
            }
            else if (tcs.startDate && tcs.endDate) {
              if (startAsDate && startAsDate > tcs.startDate && startAsDate < tcs.endDate) {
                conflict = true
                log.debug("Found conflicting statement: new start ${startAsDate} vs ${tcs.startDate} - ${tcs.endDate}")
              }
              else if (endAsDate && endAsDate > tcs.startDate && endAsDate < tcs.endDate) {
                conflict = true
                log.debug("Found conflicting statement: new end ${endAsDate} vs ${tcs.startDate} - ${tcs.endDate}")
              }
            }

            if (conflict) {
              conflicting_statements.add(tcs.id)
            }
            else if (cs_match) {
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startIssue', c.startIssue)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startVolume', c.startVolume)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endVolume', c.endVolume)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endIssue', c.endIssue)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'embargo', c.embargo)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'coverageNote', c.coverageNote)
              changed |= com.k_int.ClassUtils.updateDateField(parsedStart, tcs, 'startDate')
              changed |= com.k_int.ClassUtils.updateDateField(parsedEnd, tcs, 'endDate')
              changed |= com.k_int.ClassUtils.setRefdataIfPresent(c.coverageDepth, tipp, 'coverageDepth', 'TIPPCoverageStatement.CoverageDepth')

              stale_coverage_ids.removeAll(tcs.id)
            }
            else {
              log.debug("No Match ..")
            }
          }
          else {
            log.debug("Already found a match ..")
          }
        }
      }

      for (def cst : conflicting_statements) {
        tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(cst))
        changed = true
      }

      if (!c.id && !cs_match) {
        def cov_depth = null

        if (c.coverageDepth instanceof String) {
          cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', c.coverageDepth)
        }
        else if (c.coverageDepth instanceof Integer) {
          cov_depth = RefdataValue.get(c.coverageDepth)
        }
        else if (c.coverageDepth instanceof Map) {
          if (c.coverageDepth.id) {
            cov_depth = RefdataValue.get(c.coverageDepth.id)
          }
          else {
            cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', (c.coverageDepth.name ?: c.coverageDepth.value))
          }
        }

        if (!cov_depth) {
          cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', "Fulltext")
        }

        def coverage_item = [
          'startVolume': c.startVolume,
          'startIssue': c.startIssue,
          'endVolume': c.endVolume,
          'endIssue': c.endIssue,
          'embargo': c.embargo,
          'coverageDepth': cov_depth,
          'coverageNote': c.coverageNote,
          'startDate': startAsDate,
          'endDate ': endAsDate
        ]

        tipp.addToCoverageStatements(coverage_item)
        changed = true
      }
    }

    stale_coverage_ids.each {
      tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(it))
      changed = true
    }

    if (changed) {
      tipp.lastSeen = System.currentTimeMillis()
    }

    tipp
  }

  @Transactional
  def matchPackage(pkgId, def job = null) {
    log.debug("Matching titles for package ${pkgId}")
    def result = [matched: 0, created: 0, unmatched: 0, error: 0, reviews: 0, result: 'OK']
    def more = true
    int offset = 0
    int total = 0
    def tippIDs = []

    try {
      tippIDs = TitleInstancePackagePlatform.executeQuery(
        'select tipp.id from TitleInstancePackagePlatform as tipp where exists (' +
            'from Combo as c1 where c1.fromComponent.id=:pkg and c1.toComponent=tipp) ' +
            'and not exists (from Combo as cmb where cmb.toComponent=tipp and cmb.type=:ctt)',
        [
            pkg : pkgId,
            ctt: RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipps')
        ]
      )

      total = tippIDs.size()

      log.debug("Found ${total} detached TIPPs in package")

      while (tippIDs.size() > 0) {
        def batchSize = tippIDs.size() > 50 ? 50 : tippIDs.size()
        def batch = tippIDs.take(batchSize)
        tippIDs = tippIDs.drop(batchSize)

        batch.each { tid ->
          def matchResult = matchTitle(tid, (job?.groupId ?: null))
          result[matchResult.status]++

          if (result.reviewCreated) {
            result.reviews++
          }

          offset++
          job?.setProgress(offset, total)
        }

        if (Thread.currentThread().isInterrupted() || job?.isCancelled()) {
          job?.message("Job cancelled!")
          log.debug("cancelling package title matching for job #${job?.uuid}")
          result.result = 'CANCELLED'
          more = false
          break
        }
      }

      if (job) {
        job.setProgress(100)
        job.message("Finished package title matching.")
        job.endTime = new Date()
      }

      log.debug("Finished title matching for ${total} Titles")
    } catch (Exception e) {
      log.error("Error matching package titles!", e)
      result.result = 'ERROR'
    }

    result
  }

  def matchTitle(tippId, def groupId = null) {
    def result = [status: 'matched', reviewCreated: false]

    def tipp = TitleInstancePackagePlatform.findById(tippId)

    if (tipp) {
      CuratoryGroup group = groupId ? CuratoryGroup.findById(groupId) : null
      def found
      final IdentifierNamespace ZDB_NS = IdentifierNamespace.findByValue('zdb')
      def pkg = Package.executeQuery("from Package as pkg where exists (select 1 from Combo where fromComponent = pkg and toComponent = :tipp)", [tipp: tipp])[0]

      if (pkg && !group) {
        group = CuratoryGroup.deproxy(pkg.curatoryGroups[0])
      }

      // remap Identifiers
      def tipp_ids = Identifier.executeQuery("from Identifier as i where exists (select 1 from Combo where fromComponent = :tipp and toComponent = i)", [tipp: tipp])
      def my_ids = tipp_ids.collect { [value: it.value, type: it.namespace.value] }
      def pubType = tipp.publicationType?.value ?: null

      log.debug("TIPP Ids: ${my_ids} (by query: tipp_ids.size())")

      if (!pubType && my_ids.find { it.type == 'issn' || it.type == 'eissn' }) {
        pubType = 'Serial'
      }
      else if (!pubType && my_ids.find { it.type == 'isbn' || it.type == 'isbn' }) {
        pubType = 'Monograph'
      }

      def title_class_name = TitleInstance.determineTitleClass(pubType)

      if (title_class_name) {
        TitleInstance ti = null

        found = titleLookupService.find(
            tipp.name,
            tipp.getPublisherName(),
            my_ids,
            title_class_name
        )

        log.debug("Lookup returned ${found}")

        if (found.invalid) {
          log.debug("Skipping Invalid..")
        }
        else if (found.to_create == true) {
          log.debug("No existing title matched, creating ${tipp.name}")
          ti = createTitleFromTippData(tipp, tipp_ids)
          result.status = 'created'
        }
        else if (found.matches.size() == 1) {
          // exactly one match
          ti = found.matches[0].object
          log.debug("Matched title ${ti} for ${tipp}!")
          TIPPCoverageStatement currentCov = latest(tipp.coverageStatements)

          if (currentCov && (
              (ti.publishedFrom && currentCov.startDate && currentCov.startDate < ti.publishedFrom) ||
              (ti.publishedTo && currentCov.endDate && currentCov.endDate > ti.publishedTo)
          )) {
            result.reviewCreated = true
            reviewRequestService.raise(
                tipp,
                "TIPP coverage conflicts title publishing data",
                "TIPP ${tipp.name} was linked, check coverage",
                null,
                null,
                [otherComponents: ti] as JSON,
                RefdataCategory.lookup("ReviewRequest.StdDesc", "Coverage Mismatch"),
                componentLookupService.findCuratoryGroupOfInterest(tipp, null, group)
            )
          }
        }
        else if (found.matches.size() > 1 && tipp.coverageStatements?.size() > 0) {
          coverageCheck(tipp, found)

          if (found.matches.size() == 1) {
            ti = found.matches[0].object
          }
          else if (found.matches.size() == 0) {
            log.debug("No matches after coverage check.. creating new title ${tipp.name}")
            ti = createTitleFromTippData(tipp, tipp_ids)
            result.status = 'created'
          }
        }
        else {
          log.debug("No new title and no match, ensuring correct review is attached to the TIPP..")
        }

        if (ti) {
          if (result.status == 'matched') {
            titleAugmentService.addIdentifiers(tipp_ids, ti)
            titleAugmentService.addPublisher(tipp.publisherName, ti)
          }

          new Combo(fromComponent: ti, toComponent: tipp, type: RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')).save(flush: true)

          log.debug("linked TIPP $tipp with TitleInstance $ti")
        }
        else {
          log.debug("Unable to match title!")

          Package p = Package.get(pkg.id)

          if (p.listStatus == RefdataCategory.lookup('Package.ListStatus', 'Checked')) {
            p.listStatus = RefdataCategory.lookup('Package.ListStatus', 'In Progress')
            p.save(flush: true)
          }

          result.status = 'unmatched'
        }

        if (found.matches?.size() > 0 || found.conflicts?.size() > 0)
          result.reviewCreated = handleFindConflicts(tipp, found, group)

        result
      }
      else {
        log.warn("Unable to determine Title class to match!")
        result.status = 'unmatched'
        result
      }
    }
    else {
      log.error("Unable to reference TIPP for ID ${tippId}!")
      result.status = 'error'
      result
    }
  }

  private def createTitleFromTippData(tipp, tipp_ids) {

    def title_class_name = TitleInstance.determineTitleClass(tipp.publicationType?.value ?: 'Serial')
    def ti = Class.forName(title_class_name).newInstance()
    def title_changed = false
    ti.name = tipp.name

    log.debug("Set name ${ti.name} ..")
    ti.save(flush: true)
    titleAugmentService.addPublisher(tipp.publisherName, ti)
    log.debug("Transfering new ti ids: ${tipp_ids}")
    titleAugmentService.addIdentifiers(tipp_ids, ti)

    title_changed |= componentUpdateService.setAllRefdata([
        'medium', 'language'
    ], tipp, ti)

    def firstInPrint = tipp.dateFirstInPrint ? GOKbTextUtils.completeDateString(tipp.dateFirstInPrint.format('yyyy-MM-dd')) : null
    def firstOnline = tipp.dateFirstOnline ? GOKbTextUtils.completeDateString(tipp.dateFirstOnline.format('yyyy-MM-dd')) : null

    title_changed |= ti.hasProperty('dateFirstInPrint') ? ClassUtils.updateDateField(firstInPrint, ti, 'dateFirstInPrint') : false
    title_changed |= ti.hasProperty('dateFirstOnline') ? ClassUtils.updateDateField(firstOnline, ti, 'dateFirstOnline') : false

    if (title_class_name == 'org.gokb.cred.BookInstance') {
      log.debug("Adding Monograph fields for ${ti.class.name}: ${ti}")
      title_changed |= ti.addMonographFields(new JSONObject([//editionNumber        : null,
                                                              //editionDifferentiator: null,
                                                              editionStatement: tipp.editionStatement,
                                                              volumeNumber    : tipp.volumeNumber,
                                                              //summaryOfContent     : null,
                                                              firstAuthor     : tipp.firstAuthor,
                                                              firstEditor     : tipp.firstEditor]))
    }
    ti.save(flush: true)
    ti
  }

  def copyTitleData(ConcurrencyManagerService.Job job = null) {
    if (job != null) {
      TitleInstance.withNewSession {
        scanTIPPs(job)
      }
    }
    else {
      TitleInstance.withSession {
        scanTIPPs(null)
      }
    }
  }

  def statusUpdate() {
    def result = [retired: 0, activated: 0]
    log.info("Updating TIPP status via access dates..")
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
    RefdataValue status_retired = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
    RefdataValue status_expected = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_EXPECTED)

    String update_retire_str = "update TitleInstancePackagePlatform tipp set tipp.status = :retired, tipp.lastUpdated = :today where tipp.status = :current and accessEndDate < :today"
    String update_current_str = "update TitleInstancePackagePlatform tipp set tipp.status = :current, tipp.lastUpdated = :today where tipp.status = :expected and accessStartDate <= :today"

    result.retired = TitleInstancePackagePlatform.executeUpdate(update_retire_str, [retired: status_retired, current: status_current, today: new Date()])
    log.info("Retired ${result.retired} TIPPs.")

    result.activated = TitleInstancePackagePlatform.executeUpdate(update_current_str, [expected: status_expected, current: status_current, today: new Date()])
    log.info("Activated ${result.activated} TIPPs.")

    result
  }

  def scanTIPPs(Job job = null) {
    RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    RefdataValue combo_ids = RefdataCategory.lookup(Combo.RD_TYPE, 'KBComponent.Ids')
    String tipp_crit = 'select t.id from TitleInstancePackagePlatform as t where t.status != :status and (t.name is null or not exists (select 1 from Combo where fromComponent = t and type = :idc))'

    autoTimestampEventListener.withoutLastUpdated {
      int index = 0
      boolean cancelled = false
      def tippIDs = TitleInstancePackagePlatform.executeQuery(tipp_crit, [status: status_deleted, idc: combo_ids])
      log.debug("found ${tippIDs.size()} TIPPs")
      def tippIDit = tippIDs.iterator()
      def session = sessionFactory.currentSession

      while (tippIDit.hasNext() && !cancelled) {
        TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tippIDit.next())
        index++

        if (tipp.title) {
          tipp.title.ids.each { data ->
            if (['isbn', 'pisbn', 'issn', 'eissn', 'issnl', 'doi', 'zdb', 'isil'].contains(data.namespace.value)) {
              if (!tipp.ids*.namespace.contains(data.namespace)) {
                tipp.ids << data
                log.debug("added ID $data in TIPP $tipp")
              }
            }
          }

          if (!tipp.name || tipp.name == '') {
            tipp.name = tipp.title.name
            log.debug("set TIPP name to $tipp.name")
          }

          if (tipp.isDirty()) {
            tipp.save(flush: true)
            log.debug("save $index")
          }

          log.debug("destroy #$index: $tipp")
          tipp.finalize()
        }

        job?.setProgress(index, tippIDs.size())

        if (job?.isCancelled()) {
          cancelled = true
        }

        if (index % 100 == 0) {
          log.debug("Clean up GORM")
          // Get the current session.
          // flush and clear the session.
          session.flush()
          session.clear()
        }
      }
      // one last flush
      session.flush()
      session.clear()
      job?.endTime = new Date()
    }
  }

  private void coverageCheck(tipp, found) {
    // find the latest coverage
    TIPPCoverageStatement latest = latest(tipp.coverageStatements)
    if (latest && found.matches.size > 1) {
      // too many identifier matches
      def covMatch = []
      for (def comp : found.matches) {
        if (JournalInstance.isInstance(comp.object)) {
          if (// starts too early OR
              (comp.object.publishedFrom && latest.startDate && latest.startDate < comp.object.publishedFrom) ||
              // ends too late
              (comp.object.publishedTo && latest.endDate && latest.endDate > comp.object.publishedTo)) {
            log.debug("Excluded title match ${comp} based on coverage conflicts.")
            // no match
            break
          }
          else {
            covMatch << comp
          }
        }
        else {
          log.debug("Skipping title match with class ${comp?.object?.class}")
        }
      }
      if (covMatch.size() == 1)
        found.matches = covMatch
    }
  }

  private TIPPCoverageStatement latest(def covStmts) {
    def latest = null
    if (covStmts?.size() > 0) {
      def today = LocalDate.now()
      covStmts.each {
        if (latest == null ||
            // a valid date beats a null
            !latest.startDate && it.startDate && today.isAfter(it.startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) ||
            // a valid date beats a prior date
            latest.startDate && it.startDate && today.isAfter(it.startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) && latest.startDate < it.startDate
        ) {
          latest = it
        }
      }
    }
    return latest
  }

  private boolean handleFindConflicts(tipp, def found, CuratoryGroup activeCg = null) {
    def result = false

    if (found.invalid) {
      result = true
      def additionalInfo = [invalidIds: found.invalid]

      reviewRequestService.raise(
          tipp,
          "Invalid identifiers found",
          "Check Component Identifiers.".toString(),
          null,
          null,
          (additionalInfo as JSON).toString(),
          RefdataCategory.lookup("ReviewRequest.StdDesc", "Invalid Indentifiers"),
          componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
      )
    }
    else if (found.matches.size > 1 && !tipp.title) {
      result = true
      def additionalInfo = [otherComponents: []]
      found.matches.each { comp ->
        additionalInfo.otherComponents << [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name, id: comp.object.id, uuid: comp.object.uuid, conflicts: comp.conflicts]
      }
      reviewRequestService.raise(
          tipp,
          "TIPP matched several titles",
          "TIPP ${tipp.name} coudn't be linked.".toString(),
          null,
          null,
          (additionalInfo as JSON).toString(),
          RefdataCategory.lookup("ReviewRequest.StdDesc", "Ambiguous Title Matches"),
          componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
      )
    }
    else if (found.matches.size() == 1 && found.matches[0].conflicts?.size() > 0) {
      found.matches.each { comp ->
        def otherComponent = [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name, id: comp.object.id, uuid: comp.object.uuid]
        def mismatches = []

        comp.conflicts.each { conflict ->
          if (conflict.field == "identifier.namespace") {
            log.debug("Creating RR for namespace conflict ${conflict}..")
            result = true
            def additionalInfo = [otherComponents: [otherComponent], conflict: conflict]

            reviewRequestService.raise(
              tipp,
              conflict.message,
              "Check Title identifiers",
              null,
              null,
              (additionalInfo as JSON).toString(),
              RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Namespace Conflict'),
              componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
            )
          }
          else if (conflict.field == "identifier.value") {
            def id_map = [:]
            id_map[conflict.namespace] = conflict.value

            mismatches << id_map
          }
        }

        if (mismatches.size() > 0 && found.to_create) {
          log.debug("Creating RR on new title ${tipp.title} for id conflicts ${mismatches}")
          result = true
          def additionalInfo = [
            otherComponents: [otherComponent],
            mismatches: mismatches,
            vars: [comp.object.name, mismatches]
          ]

          reviewRequestService.raise(
            tipp.title,
            "A new title has been created because of conflicts with an existing match!",
            "Title ${comp.object.name} matched, but ingest identifiers ${mismatches} differ from existing ones in the same namespaces.",
            null,
            null,
            (additionalInfo as JSON).toString(),
            RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Critical Identifier Conflict'),
            componentLookupService.findCuratoryGroupOfInterest(tipp.title, null, activeCg)
          )
        }
        else if (mismatches.size() > 0 && !found.to_create) {
          log.debug("Creating RR on tipp for id conflicts ${mismatches}")

          result = true
          def additionalInfo = [
            otherComponents: [otherComponent],
            mismatches: mismatches,
            vars: [comp.object.name, mismatches]
          ]

          def review = reviewRequestService.raise(
            tipp,
            "There have been conflicts while linking the TIPP to an existing title!",
            "Check Title identifiers",
            null,
            null,
            (additionalInfo as JSON).toString(),
            RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Secondary Identifier Conflict'),
            componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
          )
        }
      }
    }
    else if (tipp.title == null) {
      def additionalInfo = [otherComponents: []]
      found.matches.each { comp ->
        additionalInfo.otherComponents << [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name, id: comp.object.id, uuid: comp.object.uuid]
      }
      result = true

      reviewRequestService.raise(
          tipp,
          "TIPP conflicts",
          "TIPP ${tipp.name} conflicts with other titles.".toString(),
          null,
          null,
          (additionalInfo as JSON).toString(),
          RefdataCategory.lookup("ReviewRequest.StdDesc", "Generic Matching Conflict"),
          componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
      )
    }

    if (found?.conflicts?.size > 0) {
      def additionalInfo = [otherComponents: []]
      result = true

      found.conflicts.each { comp ->
        additionalInfo.otherComponents << [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name, id: comp.object.id, uuid: comp.object.uuid]
      }
      reviewRequestService.raise(
          tipp,
          "TIPP conflicts",
          "TIPP ${tipp.name} conflicts with other titles.".toString(),
          null,
          null,
          (additionalInfo as JSON).toString(),
          RefdataCategory.lookup("ReviewRequest.StdDesc", "Generic Matching Conflict"),
          componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
      )
    }
    result
  }

  def crossCheckIds(def current_tipps, tippInfo) {
    def namespaces = [
      serial: ['zdb', 'eissn', 'issn'],
      monograph: ['isbn', 'doi', 'pisbn']
    ]
    def typeString = tippInfo.publicationType ?: tippInfo.type
    def combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)

    def result = [full_matches: [], failed_matches: []]

    def jsonIdMap = [:]
    tippInfo.identifiers.each { jsonId ->
      jsonIdMap[jsonId.type] = jsonId.value
    }
    if (jsonIdMap.size() == 0) {
      tippInfo.title.identifiers.each { jsonId ->
        jsonIdMap[jsonId.type] = jsonId.value
      }
    }

    current_tipps.each { ctipp ->
      def tipp_ids = Identifier.executeQuery("from Identifier as i where exists (select 1 from Combo where fromComponent = :tipp and toComponent = i and status = :ca)", [tipp: ctipp, ca: combo_active]).collect { ido -> [type: ido.namespace.value, value: ido.value, normname: ido.normname]}
      log.debug("Checking against existing IDs: ${tipp_ids}")
      def tipp_id_match_results = []
      boolean has_conflicts = false

      if (tippInfo.titleId == ctipp.importId) {
        tipp_id_match_results << [namespace: 'title_id', value: tippInfo.titleId, match: 'OK']
      }

      namespaces[typeString.toLowerCase()].eachWithIndex { plns, idx ->
        if (jsonIdMap[plns] != null) {
          log.debug("Check incoming id: ${jsonIdMap[plns]}")
          boolean unmatched = true

          tipp_ids.each { tid ->
            if (tid.type == plns) {
              if (Identifier.normalizeIdentifier(jsonIdMap[tid.type]) != tid.normname) {
                tipp_id_match_results << [namespace: plns, value: jsonIdMap[tid.type], match: 'FAIL']
                has_conflicts = true
              }
              else {
                tipp_id_match_results << [namespace: plns, value: jsonIdMap[tid.type], match: 'OK']
              }
              unmatched = false
            }
          }

          if (unmatched) {
            tipp_id_match_results << [namespace: plns, value: jsonIdMap[plns], match: 'NEW']
          }
        }
      }

      if (has_conflicts) {
        log.debug("Failed Match for current ${ctipp}!")
        result.failed_matches << [item: ctipp, matchResults: tipp_id_match_results]
      }
      else {
        log.debug("Full match for ${ctipp}")
        result.full_matches << ctipp
      }
    }

    result
  }

  def restLookup(tippInfo) {
    def result = [:]
    def tipps = []
    def pkgInfo = tippInfo.pkg ?: tippInfo.package
    def typeString = tippInfo.publicationType ?: tippInfo.type

    if (pkgInfo?.id && tippInfo.hostPlatform?.id) {
      RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      // remap JSON Identifiers to [type: value]
      def jsonIdMap = [:]
      tippInfo.identifiers.each { jsonId ->
        jsonIdMap[jsonId.type] = jsonId.value
      }
      if (jsonIdMap.size() == 0) {
        tippInfo.title.identifiers.each { jsonId ->
          jsonIdMap[jsonId.type] = jsonId.value
        }
      }
      def titleId = tippInfo.titleId ?: tippInfo.importId

      if (titleId) {
        tipps = TitleInstancePackagePlatform.executeQuery('''select tipp from TitleInstancePackagePlatform as tipp
            where exists (select 1 from Combo
              where fromComponent.id = :pkg
              and toComponent = tipp
              and type = :typ1
            )
            and exists (select 1 from Combo
              where fromComponent.id = :plt
              and toComponent = tipp
              and type = :typ2
            )
            and tipp.importId = :tid
            and tipp.status = :tStatus''',
            [pkg   : pkgInfo.id,
            typ1   : RefdataCategory.lookup(Combo.RD_TYPE, 'Package.Tipps'),
            plt    : tippInfo.hostPlatform.id,
            typ2   : RefdataCategory.lookup(Combo.RD_TYPE, 'Platform.HostedTipps'),
            tid    : titleId,
            tStatus: status_current]
        )
      }

      if (tipps.size() == 0) {
        // search for other Identifiers, depending on publicationType
        log.debug("Going through ids: ${jsonIdMap}")

        if ("SERIAL".equalsIgnoreCase(typeString)) {
          // Journal
          ['zdb', 'eissn', 'issn', 'doi'].each { ns_value ->
            if (jsonIdMap[ns_value]) {
              def found = TitleInstancePackagePlatform.lookupAllByIO(ns_value, jsonIdMap[ns_value])
              if (found.size() > 0) {
                found.each {
                  if (TitleInstancePackagePlatform.isInstance(it)
                      && !tipps.contains(it)
                      && it.pkg?.id == pkgInfo.id
                      && it.status == status_current
                      && it.hostPlatform?.id == tippInfo.hostPlatform.id
                      && (!titleId || !it.importId)) {
                    tipps.add(it)
                  }
                }
              }
            }
          }
          if (tipps.size() > 0) {
            log.debug("found by journal identifier set")
          }
          else {
            log.debug("No results for journal identifiers!")
          }
        }
        else if ("MONOGRAPH".equalsIgnoreCase(typeString)) {
          // Book
          ['isbn', 'doi'].each { ns_value ->
            if (jsonIdMap[ns_value]) {
              def found = TitleInstancePackagePlatform.lookupAllByIO(ns_value, jsonIdMap[ns_value])
              if (found.size() > 0) {
                found.each {
                  if (TitleInstancePackagePlatform.isInstance(it)
                      && !tipps.contains(it)
                      && it.pkg?.id == pkgInfo.id
                      && it.status == status_current
                      && it.hostPlatform?.id == tippInfo.hostPlatform.id
                      && (!titleId || !it.importId)) {
                    tipps.add(it)
                  }
                }
              }
            }
          }
          if (tipps.size() > 0) {
            log.debug("found by monograph identifier set")
          }
          else {
            log.debug("No results for monograph identifiers!")
          }
        }
      }
      else {
        log.debug("Got titleId matches: ${tipps}")
      }

      result = crossCheckIds(tipps, tippInfo)
    }
    else {
      log.error("restLookup :: Missing package/platform info!")
      result.result = 'ERROR'
    }
    result
  }

  def convertCoverageItem(c) {
    def parsedStart = GOKbTextUtils.completeDateString(c.startDate)
    def parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)
    def startAsDate = (parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
    def endAsDate = (parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
    def cov_depth = null

    log.debug("StartDate: ${parsedStart} -> ${startAsDate}, EndDate: ${parsedEnd} -> ${endAsDate}")

    if (c.coverageDepth instanceof String) {
      cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', c.coverageDepth)
    }
    else if (c.coverageDepth instanceof Integer) {
      cov_depth = RefdataValue.get(c.coverageDepth)
    }
    else if (c.coverageDepth instanceof Map) {
      if (c.coverageDepth.id) {
        cov_depth = RefdataValue.get(c.coverageDepth.id)
      }
      else {
        cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', (c.coverageDepth.name ?: c.coverageDepth.value))
      }
    }

    if (!cov_depth) {
      cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', "Fulltext")
    }

    def coverage_item = [
      'startVolume': c.startVolume,
      'startIssue': c.startIssue,
      'endVolume': c.endVolume,
      'endIssue': c.endIssue,
      'embargo': c.embargo,
      'coverageDepth': cov_depth,
      'coverageNote': c.coverageNote,
      'startDate': startAsDate,
      'endDate': endAsDate
    ]

    coverage_item
  }

  public Boolean existsCoverage(tipp, coverage) {
    Boolean result = false
    def mapped_statement = convertCoverageItem(coverage)

    tipp.coverageStatements.each { cs ->
      boolean matching = true

      mapped_statement.each { k, v ->
        if (cs[k] != v) {
          matching = false
        }
      }

      if (matching) {
        result = true
      }
    }

    result
  }


  public TitleInstancePackagePlatform updateTippFields(tipp, tippInfo, User user = null, boolean create_coverage = true) {
    componentUpdateService.updateIdentifiers(tipp, tippInfo.identifiers, user, null, true)

    if (create_coverage) {
      def cov_list = tippInfo.coverageStatements ?: tippInfo.coverage

      cov_list.each { c ->
        tipp.addToCoverageStatements(convertCoverageItem(c))
      }
    }

    log.debug("Update simple fields: ${tippInfo}")

    ['name', 'parentPublicationTitleId', 'precedingPublicationTitleId', 'firstAuthor', 'publisherName',
    'volumeNumber', 'editionStatement', 'firstEditor', 'url', 'subjectArea', 'series'].each { propName ->
      if (tippInfo[propName] && tippInfo[propName].trim() != tipp[propName]) {
        tipp[propName] = tippInfo[propName].trim()
      }
    }

    if (!tipp.importId) {
      tipp.importId = tippInfo.importId ?: tippInfo.titleId
    }

    log.debug("Updated info (${tipp.id}): ${tipp.url} ${tipp.name}")

    if (tippInfo.dateFirstInPrint) {
      ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(tippInfo.dateFirstInPrint), tipp, 'dateFirstInPrint')
    }
    else {
      log.debug("No dateFirstInPrint -> ${tippInfo.dateFirstInPrint}")
    }

    LocalDateTime access_start_ldt = GOKbTextUtils.completeDateString(tippInfo.accessStartDate)
    LocalDateTime date_first_online = GOKbTextUtils.completeDateString(tippInfo.dateFirstOnline)

    if (access_start_ldt) {
      ClassUtils.setDateIfPresent(access_start_ldt, tipp, 'accessStartDate')
    }

    if (date_first_online) {
      ClassUtils.setDateIfPresent(date_first_online, tipp, 'dateFirstOnline')
    }

    if (tippInfo.accessEndDate) {
      ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(tippInfo.accessEndDate), tipp, 'accessEndDate')
    }

    if (tipp.accessEndDate < new Date()) {
      tipp.status = RefdataCategory.lookup('KBComponent.Status', 'Retired')
    }
    else if (date_first_online && date_first_online > LocalDateTime.now()) {
      tipp.status = RefdataCategory.lookup('KBComponent.Status', 'Expected')
      ClassUtils.setDateIfPresent(date_first_online, tipp, 'accessStartDate')
    }
    else if (access_start_ldt && access_start_ldt > LocalDateTime.now()) {
      tipp.status = RefdataCategory.lookup('KBComponent.Status', 'Expected')
    }

    ClassUtils.setRefdataIfPresent(tippInfo.medium, tipp, 'medium')
    ClassUtils.setRefdataIfPresent(tippInfo.language, tipp, 'language')

    if (tippInfo.paymentType in ['F', 'f']) {
      ClassUtils.setRefdataIfPresent('OA', tipp, 'paymentType')
    } else if (tippInfo.paymentType in ['P', 'p']) {
      ClassUtils.setRefdataIfPresent('Paid', tipp, 'paymentType')
    }

    ClassUtils.setRefdataIfPresent(tippInfo.publicationType, tipp, 'publicationType')

    tipp.save(flush:true)

    tipp
  }
}