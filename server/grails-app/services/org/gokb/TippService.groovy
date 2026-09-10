package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.*

import org.gokb.cred.*
import org.hibernate.Session

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
  def restMappingService
  def dateFormatService

  public Map validateDTO(tipp_dto) {
    Map result = [valid: true]
    Map errors = [:]
    def pkgLink = tipp_dto.pkg ?: tipp_dto.package
    def pltLink = tipp_dto.hostPlatform ?: tipp_dto.platform
    def tiLink = tipp_dto.title

    if (!pkgLink) {
      result.valid = false
      errors.pkg = [
        [
          message: "Missing package link!",
          baddata: pkgLink
        ]
      ]
    }
    else {
      Package pkg = null

      if (pkgLink instanceof Map) {
        pkg = Package.get(pkgLink.id ?: pkgLink.internalId)
      }
      else {
        pkg = Package.get(pkgLink)
      }

      if (!pkg) {
        result.valid = false
        errors.pkg = [
          [
            message: "Could not resolve package id!",
            baddata: pkgLink,
            code: 404
          ]
        ]
      }
    }

    if (!pltLink) {
      result.valid = false
      errors.hostPlatform = [
        [
          message: "Missing platform link!",
          baddata: pltLink
        ]
      ]
    }
    else {
      Platform plt = null

      if (pltLink instanceof Map) {
        plt = Platform.get(pltLink.id ?: pltLink.internalId)
      }
      else {
        plt = Platform.get(pltLink)
      }

      if (!plt) {
        result.valid = false
        errors.hostPlatform = [
          [
            message: "Could not resolve platform id!",
            baddata: pltLink,
            code: 404
          ]
        ]
      }
    }

    // since a tipp is valid without a title connection, the validation of the tipp should drop this
    // precondition too
    if (tiLink) {
      TitleInstance ti = null

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

    List ids_list = tipp_dto.ids ?: tipp_dto.identifiers

    if (ids_list) {
      ids_list.each { idobj ->
        def ns_val = idobj.type ?: idobj.namespace

        if (ns_val) {
          IdentifierNamespace namespace = null

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
            String valid_val = validationService.checkIdForNamespace(idobj.value, namespace)

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

    LocalDateTime parsedAccessStart = GOKbTextUtils.completeDateString(tipp_dto.accessStartDate)
    LocalDateTime parsedAccessEnd = GOKbTextUtils.completeDateString(tipp_dto.accessEndDate)

    if (tipp_dto.accessStartDate && !parsedAccessStart) {
      if (!errors.accessStartDate) {
        errors.accessStartDate = []
      }

      result.valid = false
      errors.accessStartDate << [
        message: "Unable to parse access start date ${tipp_dto.accessStartDate}!",
        messageCode: 'validation.dateFormat',
        baddata: tipp_dto.accessStartDate
      ]
    }

    if (tipp_dto.accessEndDate && !parsedAccessEnd) {
      if (!errors.accessEndDate) {
        errors.accessEndDate = []
      }

      result.valid = false
      errors.accessEndDate << [
        message: "Unable to parse access end date ${tipp_dto.accessEndDate}!",
        messageCode: 'validation.dateFormat',
        baddata: tipp_dto.accessEndDate
      ]
    }

    if (tipp_dto.coverageStatements && !tipp_dto.coverage) {
      tipp_dto.coverage = tipp_dto.coverageStatements
    }

    if (parsedAccessStart && parsedAccessEnd && (parsedAccessEnd < parsedAccessStart)) {
      result.valid = false

      if (!errors.accessEndDate) {
        errors.accessEndDate = []
      }

      errors.accessEndDate << [
        message: "Access end date must not be prior to its start date!",
        messageCode: 'validation.dateRange',
        baddata: tipp_dto.accessEndDate
      ]
    }

    tipp_dto.coverage?.eachWithIndex { coverage, idx ->
      LocalDateTime parsedStart = GOKbTextUtils.completeDateString(coverage.startDate)
      LocalDateTime parsedEnd = GOKbTextUtils.completeDateString(coverage.endDate, false)
      Map statement_errors = [:]


      if (coverage.startDate && !parsedStart) {
        if (!statement_errors.startDate) {
          statement_errors.startDate = []
        }

        result.valid = false
        statement_errors.startDate << [
          message: "Unable to parse coverage start date ${coverage.startDate}!",
          messageCode: 'validation.dateFormat',
          baddata: coverage.startDate
        ]
      }

      if (coverage.endDate && !parsedEnd) {
        if (!statement_errors.endDate) {
          statement_errors.endDate = []
        }

        result.valid = false
        statement_errors.endDate << [
          message: "Unable to parse coverage end date ${coverage.endDate}!",
          messageCode: 'validation.dateFormat',
          baddata: coverage.endDate
        ]
      }

      if (!coverage.coverageDepth) {
        if (!statement_errors.coverageDepth) {
          statement_errors.coverageDepth = []
        }

        coverage.coverageDepth = "fulltext"
        statement_errors.coverageDepth << [
          message: "Missing value for coverage depth: set to fulltext",
          baddata: coverage.coverageDepth,
          messageCode: 'validation.missingValue'
        ]
      }
      else {
        if (coverage.coverageDepth instanceof String && !['fulltext', 'selected articles', 'abstracts'].contains(coverage.coverageDepth?.toLowerCase())) {
          if (!statement_errors.coverageDepth) {
            statement_errors.coverageDepth = []
          }

          result.valid = false
          statement_errors.coverageDepth << [
            message: "Unrecognized value '${coverage.coverageDepth}' for coverage depth",
            baddata: coverage.coverageDepth,
            messageCode: 'validation.refdataLookup'
          ]
        }
        else if (coverage.coverageDepth instanceof Integer) {
          try {
            RefdataValue candidate = RefdataValue.get(coverage.coverageDepth)

            if (!candidate && candidate.owner.label == "TIPPCoverageStatement.CoverageDepth") {
              if (!statement_errors.coverageDepth) {
                statement_errors.coverageDepth = []
              }

              result.valid = false
              statement_errors.coverageDepth << [
                message: "Illegal value '${coverage.coverageDepth}' for coverage depth",
                baddata: coverage.coverageDepth,
                messageCode: 'validation.refdataLookup'
              ]
            }
          } catch (Exception e) {
            log.error("Exception $e caught in TIPP.validateDTO while coverageDepth instanceof Integer")
          }
        }
        else if (coverage.coverageDepth instanceof Map) {
          if (coverage.coverageDepth.id) {
            try {
              RefdataValue candidate = RefdataValue.get(coverage.coverageDepth.id)

              if (!candidate && candidate.owner.label == "TIPPCoverageStatement.CoverageDepth") {
                if (!statement_errors.coverageDepth) {
                  statement_errors.coverageDepth = []
                }

                result.valid = false
                statement_errors.coverageDepth << [
                  message: "Illegal ID value '${coverage.coverageDepth.id}' for coverage depth",
                  baddata: coverage.coverageDepth,
                  messageCode: 'validation.refdataLookup'
                ]
              }
            } catch (Exception e) {
              log.error("Exception $e caught in TIPP.validateDTO while coverageDepth instanceof Map")
            }
          }
          else if (coverage.coverageDepth.value || coverage.coverageDepth.name) {
            if (!['fulltext', 'selected articles', 'abstracts'].contains(coverage.coverageDepth?.toLowerCase())) {
              if (!statement_errors.coverageDepth) {
                statement_errors.coverageDepth = []
              }

              result.valid = false
              statement_errors.coverageDepth << [
                message: "Unrecognized value '${coverage.coverageDepth}' for coverage depth",
                baddata: coverage.coverageDepth,
                messageCode: 'validation.refdataLookup'
              ]
            }
          }
        }
      }

      if (parsedStart && parsedEnd && (parsedEnd < parsedStart)) {
        result.valid = false

        if (!statement_errors.endDate) {
          statement_errors.endDate = []
        }

        statement_errors.endDate << [
          message: "Coverage end date must not be prior to its start date!",
          messageCode: 'validation.dateRange',
          baddata: coverage.endDate
        ]
      }

      if (statement_errors.size() > 0) {
        if (!errors.coverageStatements) {
          errors.coverageStatements = [:]
        }

        errors.coverageStatements["${idx}"] = statement_errors
      }
    }

    if (tipp_dto.medium) {
      RefdataValue ref = determineMediumRef(tipp_dto.medium)

      if (ref == null) {
        errors.put('medium', [
          message: "unknown",
          baddata: tipp_dto.remove('medium'),
          messageCode: 'validation.refdataLookup'
        ])
      }
      else
        tipp_dto.medium = ref.value
    }

    if (tipp_dto.publicationType) {
      RefdataValue type = determinePubTypeRef(tipp_dto.publicationType)

      if (type == null) {
        errors.put('publicationType', [
          message: "unknown",
          baddata: tipp_dto.remove('publicationType'),
          messageCode: 'validation.refdataLookup'
        ])
      }
      else
        tipp_dto.publicationType = type.value
    }

    if (tipp_dto.dateFirstInPrint) {
      LocalDateTime dfip = GOKbTextUtils.completeDateString(tipp_dto.dateFirstInPrint, false)

      if (!dfip) {
        errors.put('dateFirstInPrint', [
          message: "Unable to parse date!",
          messageCode: 'validation.dateFormat',
          baddata: tipp_dto.remove('dateFirstInPrint')
        ])
      }
    }

    if (tipp_dto.dateFirstOnline) {
      LocalDateTime dfo = GOKbTextUtils.completeDateString(tipp_dto.dateFirstOnline, false)

      if (!dfo) {
        errors.put('dateFirstOnline', [
          message: "Unable to parse date!",
          messageCode: 'validation.dateFormat',
          baddata: tipp_dto.remove('dateFirstOnline')
        ])
      }
    }

    if (tipp_dto.lastChangedExternal) {
      LocalDateTime lce = GOKbTextUtils.completeDateString(tipp_dto.lastChangedExternal, false)

      if (!lce) {
        errors.put('lastChangedExternal', [
          message: "Unable to parse date!",
          messageCode: 'validation.dateFormat',
          baddata: tipp_dto.remove('lastChangedExternal')
        ])
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
    RefdataCategory rdc = RefdataCategory.findByLabel(TitleInstancePackagePlatform.RD_MEDIUM)

    if (mediumType instanceof String) {
      RefdataValue rdv = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_MEDIUM, mediumType)

      if (rdv) {
        return rdv
      }
    }
    else if (mediumType instanceof Integer) {
      RefdataValue rdv = RefdataValue.get(mediumType)

      if (rdv && rdc) {
        return rdv
      }
    }
    else if (mediumType instanceof Map && mediumType.id) {
      RefdataValue rdv = RefdataValue.get(mediumType.id)

      if (rdv && rdc) {
        return rdv
      }
    }

    return null
  }

  public static RefdataValue determinePubTypeRef(def someType) {
    RefdataCategory rdc = RefdataCategory.findByLabel(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE)

    if (someType instanceof String) {
      RefdataValue pubType = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, someType)

      if (pubType) {
        return pubType
      }
    }
    else if (someType instanceof Integer) {
      RefdataValue pubType = RefdataValue.get(someType)

      if (pubType && pubType.owner == rdc) {
        return pubType
      }
    }
    else if (someType instanceof Map && someType.id) {
      RefdataValue pubType = RefdataValue.get(someType.id)

      if (pubType && pubType.owner == rdc) {
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

  @Transactional
  public Boolean updateCoverage(tipp, reqBody) {
    List cov_list = reqBody.coverageStatements ?: reqBody.coverage
    List stale_coverage_ids = tipp.coverageStatements.collect { it.id }

    Boolean changed = false

    cov_list?.each { c ->
      String parsedStart = GOKbTextUtils.completeDateString(c.startDate)
      String parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)

      Boolean cs_match = false
      Date startAsDate = (parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
      Date endAsDate = (parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
      Boolean conflict = false
      List conflicting_statements = []

      if (c.id) {
        TIPPCoverageStatement idMatch = TIPPCoverageStatement.findByOwnerAndId(tipp, c.id)

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
          stale_coverage_ids.removeAll { it == idMatch.id }
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

              stale_coverage_ids.removeAll { it == tcs.id }
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

      for (Long cst : conflicting_statements) {
        tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(cst))
        changed = true
      }

      if (!c.id && !cs_match) {
        RefdataValue cov_depth = null

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

        Map coverage_item = [
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

    changed
  }

  public Map matchUnlinkedTipps(Job job = null) {
    Map result = [
      matched: 0,
      created: 0,
      unmatched: 0,
      reviews: 0,
      error: 0
    ]
    Integer count = 0

    TitleInstancePackagePlatform.withNewSession { session ->
      List tippIDs = TitleInstancePackagePlatform.executeQuery(
          "select id from TitleInstancePackagePlatform tipp where status != :sdel and tipp.title = null",
          [sdel : RefdataCategory.lookup('KBComponent.Status', 'Deleted')])

      result.total = tippIDs.size()
      log.info("${result.total} detached TIPPs to check")

      for (Long tippID : tippIDs) {
        log.debug("Begin ti match for tipp ${tippID}")
        count++
        TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tippID)

        if (tipp) {
          RefdataValue status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
          RefdataValue rr_type_atm = RefdataCategory.lookup("ReviewRequest.StdDesc", "Ambiguous Title Matches")
          List rrList = ReviewRequest.findAllByComponentToReviewAndStatusAndStdDesc(tipp, status_open, rr_type_atm)

          if (rrList.size() == 0) {
            log.debug("match tipp $tipp")
            Package tipp_pkg = Package.get(tipp.pkg.id)
            Long groupId = tipp_pkg.curatoryGroups?.size() > 0 ? tipp_pkg.curatoryGroups[0].id : null
            Map match_result = matchTitle(tipp.id, groupId)

            result[match_result.status]++

            if (match_result.reviewCreated) {
              result.reviews++
            }
          }
          else {
            log.debug("Checking for resolved ambiguous matches in ${rrList.size()} reviews for TIPP $tipp ..")
            reviewAmbiguousMatches(tipp, rrList)
          }
        }
        log.debug("End ti match for tipp ${tippID}")

        if (count % 50 == 0) {
          session.flush()
          session.clear()
          job?.setProgress(count, result.total)
        }

        if (Thread.currentThread().isInterrupted() || job?.isCancelled()) {
          break
        }
      }
    }

    result
  }

  @Transactional
  private void reviewAmbiguousMatches(tipp, reviews) {
    RefdataValue rr_status_closed = RefdataCategory.lookup("ReviewRequest.Status", "Closed")
    RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")

    for (rr_atm in reviews) {
      if (!tipp.title) {
        Map additionalInfo = rr_atm.getAdditional()
        List total_matches = additionalInfo instanceof Map ? (additionalInfo?.otherComponents ?: []) : []
        List current_matches = []

        for (ttl in total_matches) {
          TitleInstance matched_ti = TitleInstance.get(ttl.id)

          if (matched_ti && matched_ti.status == status_current) {
            current_matches << matched_ti
          }
        }

        if (current_matches.size() <= 1) {
          rr_atm.status = rr_status_closed
          rr_atm.save(flush: true)

          if (current_matches.size() == 1) {
            tipp.title = current_matches[0]
            tipp.save(flush: true)
            touchPackage(tipp)
          }
        }
      }
      else {
        rr_atm.status = rr_status_closed
        rr_atm.save(flush: true)
      }
    }
  }

  public Map matchPackage(pkgId, Job job = null, Job parentJob = null) {
    Map result = [:]
    boolean new_session = false
    Session session

    try {
      session = sessionFactory.currentSession
    }
    catch (Exception e) {
      new_session = true
    }

    if (new_session) {
      Package.withNewSession { nsession ->
        result = processMatchPackage(pkgId, nsession, job, parentJob)
      }
    }
    else {
      result = processMatchPackage(pkgId, session, job, parentJob)
    }

    result
  }


  private Map processMatchPackage(pkgId, session, Job job = null, Job parentJob = null) {
    log.debug("Matching titles for package ${pkgId}")
    Map result = [
      result: 'OK',
      matched: 0,
      created: 0,
      unmatched: 0,
      error: 0,
      reviews: 0
    ]

    Boolean more = true
    int offset = 0
    int total = 0
    List tippIDs = []

    try {
      tippIDs = TitleInstancePackagePlatform.executeQuery('''select tipp.id from TitleInstancePackagePlatform as tipp
                                                              where tipp.pkg.id = :pkg and tipp.title = null)''',
                                                              [pkg : pkgId])

      total = tippIDs.size()

      log.debug("Found ${total} detached TIPPs in package")

      while (tippIDs.size() > 0) {
        int batchSize = tippIDs.size() > 50 ? 50 : tippIDs.size()
        List batch = tippIDs.take(batchSize)
        tippIDs = tippIDs.drop(batchSize)

        batch.each { tid ->
          Map matchResult = matchTitle(tid, (job?.groupId ?: null))
          result[matchResult.status]++

          if (result.reviewCreated) {
            result.reviews++
          }

          offset++
          job?.setProgress(offset, total)
        }

        session.flush()
        session.clear()

        if (Thread.currentThread().isInterrupted() || job?.isCancelled()) {
          job?.message("Job cancelled!")
          log.debug("cancelling package title matching for job #${job?.uuid}")
          result.result = 'CANCELLED'
          more = false
          break
        }
      }

      session.flush()
      session.clear()

      if (job && (!parentJob || !parentJob.ownerId) && !hasOpenReviews(pkgId)) {
        Package pkg = Package.get(pkgId)
        pkg.listStatus = RefdataCategory.lookup('Package.ListStatus', 'Checked')
        pkg.save(flush: true)
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

  public Boolean hasOpenReviews(pid) {
    int total = 0

    ReviewRequest.withNewSession {
      RefdataValue status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
      RefdataValue manual_review_type = RefdataCategory.lookup("ReviewRequest.StdDesc", 'Manual Request')

      def qry = '''select count(*) from ReviewRequest as rr
                    where ((
                      rr.componentToReview.id = :pid
                      and rr.stdDesc != :mr
                    )
                    or (
                      exists (
                        select 1 from TitleInstancePackagePlatform as t
                        where t.pkg.id = :pid
                        and t.id = rr.componentToReview.id
                      )
                    ))
                    and rr.status = :so'''

      total = ReviewRequest.executeQuery(qry, [pid: pid, mr: manual_review_type, so: status_open])[0]
    }

    return total > 0
  }

  @Transactional
  public Map matchTitle(Long tippId, Long groupId = null) {
    Map result = [status: 'matched', reviewCreated: false]
    RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")

    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.findById(tippId)

    if (tipp) {
      log.debug("Matching TIPP ${tipp.name} ..")
      CuratoryGroup group = groupId ? CuratoryGroup.findById(groupId) : null
      final IdentifierNamespace ZDB_NS = IdentifierNamespace.findByValue('zdb')
      Package pkg = Package.deproxy(tipp.pkg)

      if (pkg && !group) {
        group = CuratoryGroup.deproxy(pkg.curatoryGroups[0])
      }

      // remap Identifiers
      List tipp_ids = tipp.activeIdInfo.collect { [type: it.namespace, value: it.value] }
      String pubType = tipp.publicationType?.value ?: null

      log.debug("TIPP Ids: ${tipp_ids} (by query: tipp_ids.size())")

      if (!pubType && tipp_ids.find { it.type == 'issn' || it.type == 'eissn' }) {
        pubType = 'Serial'
        tipp.publicationType = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, pubType)
        tipp.save(flush: true)
      }
      else if (!pubType && tipp_ids.find { it.type == 'isbn' || it.type == 'pisbn' }) {
        pubType = 'Monograph'
        tipp.publicationType = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, pubType)
        tipp.save(flush: true)
      }

      String title_class_name = TitleInstance.determineTitleClass(pubType)

      if (title_class_name) {
        TitleInstance ti = null

        log.debug("TI Lookup ..")

        Map found = titleLookupService.find(
            tipp.name,
            tipp.publisherName,
            tipp_ids,
            title_class_name
        )

        log.debug("Lookup returned ${found}")

        if (found.invalid) {
          log.debug("Skipping Invalid..")
        }
        else if (found.to_create == true) {
          if (tipp.name) {
            log.debug("No existing title matched, creating ${tipp.name}")
            ti = createTitleFromTippData(tipp, tipp_ids)
            result.status = 'created'
          }
          else if (found.matches.size() == 0) {
            log.warn("No name for unmatched tipp ${tipp} ..")
            RefdataValue type_mtn = RefdataCategory.lookup('ReviewRequest.StdDesc', "Missing TIPP Name")
            ReviewRequest existing_mtn = ReviewRequest.findByStdDescAndComponentToReview(type_mtn, tipp)

            if (existing_mtn) {
              log.debug("Unmatched ${tipp} already has a review ..")
            }
            else {
              reviewRequestService.raise(
                tipp,
                "The TIPP could not be linked to an existing title, and cannot create a new one due to a missing name!",
                "Supply a name for the TIPP or delete it.",
                null,
                type_mtn,
                componentLookupService.findCuratoryGroupOfInterest(tipp, null, group)
              )
            }
          }
        }
        else if (found.matches.size() == 1) {
          // exactly one match
          log.debug("Matched title ${found.matches[0]} for ${tipp}!")
          ti = found.matches[0].object
          // TIPPCoverageStatement currentCov = latest(tipp.coverageStatements)

          // if (currentCov && (!ti.publishedFrom ||
          //     (ti.publishedFrom && currentCov.startDate && currentCov.startDate < ti.publishedFrom) ||
          //     (ti.publishedTo && currentCov.endDate && currentCov.endDate > ti.publishedTo)
          // )) {
          //   result.reviewCreated = true

          //   def coverage_dates = "${dateFormatService.formatDate(currentCov.startDate)} - ${dateFormatService.formatDate(currentCov.endDate)}"
          //   def ti_pub_dates = "${dateFormatService.formatDate(ti.publishedFrom)} - ${dateFormatService.formatDate(ti.publishedTo)}"

          //   RefdataValue type_cmc = RefdataCategory.lookup("ReviewRequest.StdDesc", "Coverage Matching Conflict")
          //   RefdataValue status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")

          //   def additionalInfo = [
          //     vars: [coverage_dates, ti_pub_dates],
          //     coverageMismatch: true,
          //     otherComponents: [
          //       [
          //         oid: "${tipp.class.name}:${ti.id}",
          //         name: tipp.name,
          //         id: tipp.id,
          //         uuid: tipp.uuid,
          //         conflicts: found.matches[0].conflicts
          //       ]
          //     ]
          //   ]

          //   def existing_cmc = ReviewRequest.executeQuery("select count(*) from ReviewRequest where componentToReview = :tid and stdDesc = :type and status = :so", [tid: tipp, type: type_cmc, so: status_open])

          //   if (!existing_cmc) {
          //     reviewRequestService.raise(
          //         ti,
          //         "TIPP coverage is in conflict with linked title publishing data.",
          //         "Title publishing dates and correct them if necessary.",
          //         null,
          //         (additionalInfo as JSON).toString(),
          //         type_cmc,
          //         componentLookupService.findCuratoryGroupOfInterest(tipp, null, group)
          //     )
          //   }
          // }
        }
        else if (found.matches.size() > 1 && tipp.coverageStatements?.size() > 0) {
          List coverage_match = coverageCheck(tipp, found)

          if (coverage_match.size() == 1) {
            ti = coverage_match[0].object
          }
          else if (coverage_match.size() == 0) {
            log.debug("No match via coverage info ..")
          }
          else {
            log.debug("Multiple matches on coverage ..")
          }
        }
        else {
          log.debug("No new title and no match, ensuring correct review is attached to the TIPP..")
        }

        if (ti) {
          tipp.title = ti
          tipp.save(flush: true, failOnError: true)

          if (result.status == 'matched') {
            boolean ti_changed = componentUpdateService.updateIdentifiers(ti, tipp_ids)

            if (ti_changed) {
              ti.lastSeen = new Date().getTime()
              ti = ti.merge(flush: true, failOnError: true)
            }

            if (!ti.currentPublisher) {
              titleAugmentService.addPublisher(tipp.publisherName, ti)

              if (ti.currentPublisher) {
                ti_changed = true
              }
            }

            if (title_class_name == 'org.gokb.cred.BookInstance') {
              def mono_string_info = [
                editionStatement: tipp.editionStatement,
                volumeNumber    : tipp.volumeNumber,
                firstAuthor     : tipp.firstAuthor,
                firstEditor     : tipp.firstEditor
              ]

              ti_changed |= titleAugmentService.editMonographFields(ti, mono_string_info, true)
            }

            if (ti_changed) {
              ti.save(flush: true)
            }
          }

          tipp.lastSeen = System.currentTimeMillis()
          tipp.save(flush: true)

          ensureTipl(ti, tipp.hostPlatform, tipp.url)

          pkg.lastSeen = new Date().getTime()
          pkg.save()

          log.debug("linked TIPP $tipp with TitleInstance $ti")
        }
        else {
          log.debug("Unable to match title!")

          result.status = 'unmatched'
        }

        if (found.matches?.size() > 0 || found.conflicts?.size() > 0) {
          result.reviewCreated = handleFindConflicts(tipp, found, group)

          if (result.reviewCreated && pkg.listStatus == RefdataCategory.lookup('Package.ListStatus', 'Checked')) {
            pkg.listStatus = RefdataCategory.lookup('Package.ListStatus', 'In Progress')
            pkg.save(flush: true)
          }
        }

        result
      }
      else {
        log.warn("Unable to determine Title class to match for $tipp!")
        result.status = 'error'
        result
      }
    }
    else {
      log.error("Unable to reference TIPP for ID ${tippId}!")
      result.status = 'error'
      result
    }
  }

  public boolean revertCheckedListStatusFor(pkgId) {
    boolean changed = false



    changed
  }

  private TitleInstance createTitleFromTippData(TitleInstancePackagePlatform tipp, List tipp_ids) {
    String title_class_name = TitleInstance.determineTitleClass(tipp.publicationType?.value ?: 'Serial')
    TitleInstance ti = Class.forName(title_class_name).newInstance()
    Boolean title_changed = false
    ti.name = tipp.name

    log.debug("Set name ${ti.name} ..")
    ti.save(flush: true, failOnError: true)

    titleAugmentService.addPublisher(tipp.publisherName, ti)
    ti.save(flush: true, failOnError: true)

    log.debug("Transfering new ti ids: ${tipp_ids}")
    componentUpdateService.updateIdentifiers(ti, tipp_ids)
    ti.refresh()

    title_changed |= componentUpdateService.setAllRefdata([
        'medium', 'language'
    ], tipp, ti)

    if (title_class_name == 'org.gokb.cred.BookInstance') {
      log.debug("Adding Monograph fields for ${ti.class.name}: ${ti}")
      Map mono_string_info = [
        editionStatement: tipp.editionStatement,
        volumeNumber    : tipp.volumeNumber,
        firstAuthor     : tipp.firstAuthor,
        firstEditor     : tipp.firstEditor,
        dateFirstInPrint: tipp.dateFirstInPrint,
        dateFirstOnline : tipp.dateFirstOnline
      ]

      title_changed |= titleAugmentService.editMonographFields(ti, mono_string_info)
    }

    ti.save(flush: true, failOnError: true)
    ti
  }

  public Map statusUpdate() {
    log.info("Updating TIPP status via access dates..")
    Map result = [result: 'OK', retired: 0, activated: 0]
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
    RefdataValue status_retired = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
    RefdataValue status_expected = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_EXPECTED)

    String update_retire_str = '''from TitleInstancePackagePlatform tipp
                                where tipp.status = :current
                                and accessEndDate < :today'''
    String update_current_str = '''from TitleInstancePackagePlatform tipp
                                where tipp.status = :expected
                                and accessStartDate <= :today'''

    List to_retire = TitleInstancePackagePlatform.executeQuery(update_retire_str, [current: status_current, today: new Date()])
    List to_activate = TitleInstancePackagePlatform.executeQuery(update_current_str, [expected: status_expected, today: new Date()])

    for (tipp in to_retire) {
      tipp.status = status_retired
      tipp.save()

      result.retired++

      touchPackage(tipp)

      if (Thread.currentThread().isInterrupted()) {
        log.info("Cancelling TIPP matching job ..")
        result.result = 'CANCELLED'
        more = false
        break
      }
    }

    if (result.result != 'CANCELLED') {
      for (tipp in to_activate) {
        tipp.status = status_current
        tipp.save()

        result.activated++

        touchPackage(tipp)

        if (Thread.currentThread().isInterrupted()) {
          log.info("Cancelling TIPP matching job ..")
          result.result = 'CANCELLED'
          more = false
          break
        }
      }
    }

    log.info("Retired ${result.retired} TIPPs.")
    log.info("Activated ${result.activated} TIPPs.")

    result
  }

  @Transactional
  public Map copyTitleData(Job job = null) {
    Map result = [status:'OK', total: 0]

    TitleInstancePackagePlatform.withNewSession { session ->
      RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
      String tipp_crit = 'select t.id from TitleInstancePackagePlatform as t where t.status != :status and (t.name is null or not exists (select 1 from ComponentIdentifier where component = t))'

      autoTimestampEventListener.withoutLastUpdated (TitleInstancePackagePlatform) {
        int index = 0
        boolean cancelled = false
        List tippIDs = TitleInstancePackagePlatform.executeQuery(tipp_crit, [status: status_deleted])
        log.debug("found ${tippIDs.size()} TIPPs")
        result.total = tippIDs.size()
        Iterator tippIDit = tippIDs.iterator()

        while (tippIDit.hasNext() && !cancelled) {
          TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tippIDit.next())
          index++

          if (tipp.title) {
            tipp.title.activeIds.each { data ->
              Identifier idobj = Identifier.get(data.id)

              if (['isbn', 'pisbn', 'issn', 'eissn'].contains(idobj.namespace.value)) {
                if (!tipp.activeIds*.namespace.contains(idobj.namespace)) {
                  new ComponentIdentifier(component: tipp, identifier: idobj).save(flush: true, failOnError: true)
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
            result.result = 'CANCELLED'
          }

          if (index % 100 == 0) {
            log.debug("Clean up GORM")
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
    result
  }

  private List coverageCheck(TitleInstancePackagePlatform tipp, Map found) {
    // find the latest coverage
    List result = []
    TIPPCoverageStatement latest = latest(tipp.coverageStatements)

    if (latest && found.matches.size() > 1) {
      List matches = []
      // too many identifier matches
      for (Map comp : found.matches) {
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
            result << comp
          }
        }
        else {
          log.debug("Skipping title match with class ${comp?.object?.class}")
        }
      }
    }

    result
  }

  private TIPPCoverageStatement latest(List covStmts) {
    TIPPCoverageStatement latest = null

    if (covStmts?.size() > 0) {
      LocalDate today = LocalDate.now()

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

  private Boolean handleFindConflicts(TitleInstancePackagePlatform tipp, Map found, CuratoryGroup activeCg = null) {
    Boolean result = false
    RefdataValue status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
    RefdataValue type_cic = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Critical Identifier Conflict')

    if (found.invalid) {
      result = true

      Map additionalInfo = [invalidIds: found.invalid]
      RefdataValue type_ii = RefdataCategory.lookup("ReviewRequest.StdDesc", "Invalid Indentifiers")
      int num_existing = ReviewRequest.executeQuery("select count(*) from ReviewRequest where componentToReview = :tid and stdDesc = :type", [tid: tipp, type: type_ii])[0]

      if (num_existing == 0) {
        reviewRequestService.raise(
            tipp,
            "Invalid identifiers found",
            "Check Component Identifiers.".toString(),
            null,
            (additionalInfo as JSON).toString(),
            type_ii,
            componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
        )
      }
    }
    else if (found.matches.size() > 1 && !tipp.title) {
      result = true
      RefdataValue type_atm = RefdataCategory.lookup("ReviewRequest.StdDesc", "Ambiguous Title Matches")
      int num_existing = ReviewRequest.executeQuery("select count(*) from ReviewRequest where componentToReview = :tid and stdDesc = :type and status = :so", [tid: tipp, type: type_atm, so: status_open])[0]

      if (num_existing == 0) {
        Map additionalInfo = [otherComponents: []]

        found.matches.each { comp ->
          additionalInfo.otherComponents << [
            oid: "${comp.object.class.name}:${comp.object.id}",
            name: comp.object.name,
            id: comp.object.id,
            uuid: comp.object.uuid,
            conflicts: comp.conflicts
          ]
        }
        reviewRequestService.raise(
            tipp,
            "TIPP matched several titles",
            "TIPP ${tipp.name} coudn't be linked.".toString(),
            null,
            (additionalInfo as JSON).toString(),
            type_atm,
            componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
        )
      }

      log.debug("Creating RR on existing title for id conflicts")
      List tipp_id_list = tipp.ids.collect { "${it.namespace.value}:${it.value}" }
      TitleInstance component_to_review = found.matches.removeLast().object

      List ctc_existing = ReviewRequest.executeQuery("select count(*) from ReviewRequest where componentToReview = :tid and stdDesc = :type and status = :so", [tid: component_to_review, type: type_cic, so: status_open])[0]

      if (ctc_existing == 0) {
        List other_objects = found.matches.collect {
                              [
                                oid: "${it.object.class.name}:${it.object.id}",
                                name: it.object.name,
                                id: it.object.id,
                                uuid: it.object.uuid,
                                conflicts: it.conflicts
                              ]
                            }

        result = true
        Map additionalInfo = [
          otherComponents: other_objects,
          referenceIds: tipp_id_list,
          vars: [component_to_review.name, ""]
        ]

        reviewRequestService.raise(
          component_to_review,
          "Multiple titles have been matched by identifiers ${tipp_id_list}!".toString(),
          "Check Titles for duplicates!",
          null,
          (additionalInfo as JSON).toString(),
          type_cic,
          componentLookupService.findCuratoryGroupOfInterest(component_to_review, null, activeCg)
        )
      }
    }
    else if (found.matches.size() > 0 && found.matches[0].conflicts?.size() > 0) {
      boolean rt_review_created = false
      RefdataValue type_nc = RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Namespace Conflict')
      RefdataValue type_sic = RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Secondary Identifier Conflict')

      found.matches.each { comp ->
        Map otherComponent = [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name, id: comp.object.id, uuid: comp.object.uuid]
        List mismatches = []

        comp.conflicts.each { conflict ->
          if (conflict.field == "identifier.namespace") {
            log.debug("Creating RR for namespace conflict ${conflict}..")
            result = true
            Map additionalInfo = [otherComponents: [otherComponent], conflict: conflict]

            reviewRequestService.raise(
              tipp,
              conflict.message,
              "Check Title identifiers",
              null,
              (additionalInfo as JSON).toString(),
              type_nc,
              componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
            )
          }
          else if (conflict.field == "identifier.value") {
            Map id_map = [:]
            id_map[conflict.namespace] = conflict.value

            mismatches << id_map
          }
        }

        if (mismatches.size() > 0 && found.to_create && !rt_review_created) {
          log.debug("Creating RR on new title ${tipp.title} for id conflicts ${mismatches}")
          rt_review_created = true
          result = true
          Map additionalInfo = [
            otherComponents: [otherComponent],
            mismatches: mismatches,
            vars: [comp.object.name, mismatches]
          ]

          reviewRequestService.raise(
            tipp.title,
            "A new title has been created because of conflicts with an existing match!",
            "Title ${comp.object.name} matched, but ingest identifiers ${mismatches} differ from existing ones in the same namespaces.",
            null,
            (additionalInfo as JSON).toString(),
            type_cic,
            componentLookupService.findCuratoryGroupOfInterest(tipp.title, null, activeCg)
          )
        }
        else if (!result && mismatches.size() > 0 && !found.to_create) {
          log.debug("Creating RR on tipp for id conflicts ${mismatches}")

          result = true
          Map additionalInfo = [
            otherComponents: [otherComponent],
            mismatches: mismatches,
            vars: [comp.object.name, mismatches]
          ]

          reviewRequestService.raise(
            tipp,
            "There have been conflicts while linking the TIPP to an existing title!",
            "Check Title identifiers",
            null,
            (additionalInfo as JSON).toString(),
            type_sic,
            componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
          )
        }
      }
    }
    else if (tipp.title == null) {
      Map additionalInfo = [otherComponents: []]

      found.matches.each { comp ->
        additionalInfo.otherComponents << [
          oid: "${comp.object.class.name}:${comp.object.id}",
          name: comp.object.name,
          id: comp.object.id,
          uuid: comp.object.uuid
        ]
      }
      result = true

      reviewRequestService.raise(
          tipp,
          "TIPP conflicts",
          "TIPP ${tipp.name} conflicts with other titles.".toString(),
          null,
          (additionalInfo as JSON).toString(),
          RefdataCategory.lookup("ReviewRequest.StdDesc", "Generic Matching Conflict"),
          componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
      )
    }

    if (found?.conflicts?.size() > 0) {
      Map additionalInfo = [otherComponents: []]
      result = true

      found.conflicts.each { comp ->
        additionalInfo.otherComponents << [
          oid: "${comp.object.class.name}:${comp.object.id}",
          name: comp.object.name,
          id: comp.object.id,
          uuid: comp.object.uuid
        ]
      }
      reviewRequestService.raise(
          tipp,
          "TIPP conflicts",
          "TIPP ${tipp.name} conflicts with other titles.".toString(),
          null,
          (additionalInfo as JSON).toString(),
          RefdataCategory.lookup("ReviewRequest.StdDesc", "Generic Matching Conflict"),
          componentLookupService.findCuratoryGroupOfInterest(tipp, null, activeCg)
      )
    }
    result
  }

  public Map crossCheckIds(def current_tipps, tippInfo) {
    final Map namespaces = [
      serial: ['zdb', 'eissn', 'issn'],
      monograph: ['isbn', 'doi', 'pisbn']
    ]

    String typeString = tippInfo.publicationType ?: tippInfo.type
    RefdataValue status_active = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_ACTIVE)
    List full_matches = []

    Map result = [full_matches: [], failed_matches: []]

    Map jsonIdMap = [:]
    tippInfo.identifiers.each { jsonId ->
      jsonIdMap[jsonId.type] = jsonId.value
    }

    if (jsonIdMap.size() == 0 && tippInfo.title) {
      tippInfo.title.identifiers.each { jsonId ->
        jsonIdMap[jsonId.type] = jsonId.value
      }
    }

    current_tipps.each { ctipp ->
      List tipp_id_info = Identifier.executeQuery('''from Identifier as i
                                                  where exists (
                                                    select 1 from ComponentIdentifier
                                                    where component = :tipp
                                                    and identifier = i
                                                    and status = :sa)''',
                                              [tipp: ctipp, sa: status_active]).collect { ido -> [type: ido.namespace.value, value: ido.value, normname: ido.normname]}
      log.debug("Checking against existing IDs: ${tipp_id_info}")
      List tipp_id_match_results = []
      boolean has_conflicts = false

      if (tippInfo.importId == ctipp.importId) {
        tipp_id_match_results << [namespace: 'title_id', value: tippInfo.importId, match: 'OK']
      }

      namespaces[typeString.toLowerCase()].eachWithIndex { plns, idx ->
        if (jsonIdMap[plns] != null) {
          log.debug("Check incoming id: ${jsonIdMap[plns]}")
          boolean unmatched = true

          tipp_id_info.each { tid ->
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
        full_matches << ctipp
      }
    }

    if (full_matches.size() == 1) {
      result.full_matches = full_matches
    }
    else if (full_matches.size() > 1) {
      boolean coverage_match = false

      full_matches.each { fm ->
        if (existsCoverage(fm, tippInfo.coverageStatements[0])) {
          result.full_matches << fm
          coverage_match = true
        }
      }

      if (!coverage_match) {
        result.full_matches = full_matches
      }
    }

    result
  }

  public void updateLastSeen(tipp, Long systime) {
    if (!tipp.lastSeen || systime > tipp.lastSeen) {
      TitleInstancePackagePlatform.executeUpdate("update TitleInstancePackagePlatform set lastSeen = :ts where id = :tid", [ts: systime, tid: tipp.id])
    }
  }

  public Map restLookup(tippInfo) {
    Map result = [:]
    List tipps = []
    Map pkgInfo = tippInfo.pkg ?: tippInfo.package
    String typeString = tippInfo.publicationType ?: tippInfo.type

    if (pkgInfo?.id && tippInfo.hostPlatform?.id) {
      RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      RefdataValue status_expected = RefdataCategory.lookup("KBComponent.Status", "Expected")
      RefdataValue status_retired = RefdataCategory.lookup("KBComponent.Status", "Retired")
      List status_valid = [status_current, status_expected]

      if (tippInfo.status?.toLowerCase() == 'retired' || (tippInfo.access_end_date && GOKbTextUtils.completeDateString(tippInfo.access_end_date) < LocalDate.now().atStartOfDay())) {
        status_valid << status_retired
      }

      // remap JSON Identifiers to [type: value]
      Map jsonIdMap = [:]
      tippInfo.identifiers.each { jsonId ->
        jsonIdMap[jsonId.type] = jsonId.value
      }

      if (jsonIdMap.size() == 0 && tippInfo.title) {
        tippInfo.title.identifiers.each { jsonId ->
          jsonIdMap[jsonId.type] = jsonId.value
        }
      }

      String titleId = tippInfo.titleId ?: tippInfo.importId

      if (titleId) {
        tipps = TitleInstancePackagePlatform.executeQuery('''select tipp from TitleInstancePackagePlatform as tipp
            where tipp.pkg.id = :pkg
            and tipp.hostPlatform.id = :plt
            and tipp.importId = :tid
            and tipp.status IN (:tStatus)''',
            [
              pkg   : pkgInfo.id,
              plt    : tippInfo.hostPlatform.id,
              tid    : titleId,
              tStatus: status_valid
            ]
        )
      }

      if (tipps.size() == 0) {
        // search for other Identifiers, depending on publicationType
        log.debug("Going through ids: ${jsonIdMap}")

        if ("SERIAL".equalsIgnoreCase(typeString)) {
          // Journal
          ['zdb', 'eissn', 'issn', 'doi'].each { ns_value ->
            if (jsonIdMap[ns_value]) {
              List found = TitleInstancePackagePlatform.lookupAllByIO(ns_value, jsonIdMap[ns_value])

              if (found.size() > 0) {
                found.each {
                  if (TitleInstancePackagePlatform.isInstance(it)
                      && !tipps.contains(it)
                      && it.pkg?.id == pkgInfo.id
                      && status_valid.contains(it.status)
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
              List found = TitleInstancePackagePlatform.lookupAllByIO(ns_value, jsonIdMap[ns_value])

              if (found.size() > 0) {
                found.each {
                  if (TitleInstancePackagePlatform.isInstance(it)
                      && !tipps.contains(it)
                      && it.pkg?.id == pkgInfo.id
                      && status_valid.contains(it.status)
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

  public Map convertCoverageItem(TIPPCoverageStatement c) {
    Map result = [
        'startVolume': c.startVolume,
        'startIssue': c.startIssue,
        'endVolume': c.endVolume,
        'endIssue': c.endIssue,
        'embargo': c.embargo,
        'coverageDepth': c.coverageDepth,
        'coverageNote': c.coverageNote,
        'startDate': c.startDate,
        'endDate': c.endDate
    ]

    result
  }

  public Map convertCoverageItem(Map c) {
    Map result = [:]

    String parsedStart = GOKbTextUtils.completeDateString(c.startDate)
    String parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)
    Date startAsDate = (parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
    Date endAsDate = (parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
    RefdataValue cov_depth

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

    result = [
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

    result
  }

  public void deleteExistingCoverage(tipp) {
    def tcs_ids = tipp.coverageStatements*.id

    tcs_ids.each {
      def tcs_obj = TIPPCoverageStatement.get(it)
      tipp.removeFromCoverageStatements(tcs_obj)
    }
    tipp.save(flush: true)
  }

  public Boolean existsCoverage(tipp, coverage) {
    Boolean result = false
    def mapped_statement = convertCoverageItem(coverage)

    tipp.coverageStatements.each { cs ->
      boolean matching = true

      mapped_statement.each { k, v ->
        if (cs[k] != (v ?: null)) {
          log.debug("Found differring $k .. $v <> ${cs[k]}!")
          matching = false
        }
      }

      if (matching) {
        result = true
      }
    }

    result
  }

  @Transactional
  public void touchPackage(TitleInstancePackagePlatform tipp) {
    Package pkg_obj = KBComponent.deproxy(tipp.pkg)

    pkg_obj?.lastSeen = new Date().getTime()
    pkg_obj?.save(flush:true)
  }

  public boolean updateTippFields(TitleInstancePackagePlatform tipp, Map tippInfo, User user = null, boolean create_coverage = true) {
    boolean hasChanged = componentUpdateService.updateIdentifiers(tipp, tippInfo.identifiers, user, null, true)

    log.debug("updateTippFields hasChanged after ids: ${hasChanged}")

    if (create_coverage) {
      List cov_list = tippInfo.coverageStatements ?: tippInfo.coverage

      cov_list.each { c ->
        if (!existsCoverage(tipp, c)) {
          tipp.addToCoverageStatements(convertCoverageItem(c))
          hasChanged = true
        }
      }

      if (hasChanged) {
        tipp.save(flush: true, failOnError: true)
      }
    }

    log.debug("Update simple fields: ${tippInfo}")

    // These values can be changed to empty Strings
    ['parentPublicationTitleId', 'precedingPublicationTitleId', 'firstAuthor', 'publisherName',
     'volumeNumber', 'editionStatement', 'firstEditor', 'subjectArea', 'series'].each { propName ->
      if (tippInfo[propName]?.trim() != tipp[propName]) {
        tipp[propName] = tippInfo[propName]?.trim()
        hasChanged = true
      }
    }

    //Name, URL are only overwritten by a real value
    ['name', 'url'].each { propName ->
      if (tippInfo[propName] && tippInfo[propName].trim() != tipp[propName]) {
        tipp[propName] = tippInfo[propName].trim()
        hasChanged = true
      }
    }

    if (!tipp.importId && (tippInfo.importId || tippInfo.titleId)) {
      tipp.importId = tippInfo.importId ?: tippInfo.titleId
      hasChanged = true
    }

    log.debug("Updated info (${tipp.id}): ${tipp.url} ${tipp.name}")

    if (tippInfo.dateFirstInPrint) {
      hasChanged |= ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(tippInfo.dateFirstInPrint), tipp, 'dateFirstInPrint')
    }
    else {
      log.debug("No dateFirstInPrint -> ${tippInfo.dateFirstInPrint}")
    }

    LocalDateTime access_start_ldt = GOKbTextUtils.completeDateString(tippInfo.accessStartDate)
    LocalDateTime date_first_online = GOKbTextUtils.completeDateString(tippInfo.dateFirstOnline)

    if (access_start_ldt) {
      hasChanged |= ClassUtils.setDateIfPresent(access_start_ldt, tipp, 'accessStartDate')

      if (tipp.accessEndDate && tipp.accessEndDate < tipp.accessStartDate) {
        tipp.accessEndDate = null
      }
    }

    if (date_first_online) {
      hasChanged |= ClassUtils.setDateIfPresent(date_first_online, tipp, 'dateFirstOnline')
    }

    if (tippInfo.accessEndDate) {
      hasChanged |= ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(tippInfo.accessEndDate), tipp, 'accessEndDate')

      if (tipp.accessStartDate && tipp.accessEndDate < tipp.accessStartDate) {
        tipp.accessStartDate = null
      }
    }

    if (tipp.accessEndDate && tipp.accessEndDate < new Date()) {
      hasChanged |= ClassUtils.setRefdataIfPresent('Retired', tipp, 'status')
    }
    else if (tippInfo.status?.toLowerCase() == 'retired' && tipp.status.value != 'Retired') {
      hasChanged |= ClassUtils.setRefdataIfPresent('Retired', tipp, 'status')
      hasChanged |= ClassUtils.updateDateField(LocalDate.now(), tipp, 'accessEndDate')

      if (tipp.accessStartDate && tipp.accessEndDate < tipp.accessStartDate) {
        tipp.accessStartDate = null
      }
    }
    else if (date_first_online && date_first_online > LocalDateTime.now()) {
      hasChanged |= ClassUtils.setRefdataIfPresent('Expected', tipp, 'status')
      hasChanged |= ClassUtils.setDateIfPresent(date_first_online, tipp, 'accessStartDate')

      if (tipp.accessEndDate && tipp.accessEndDate < tipp.accessStartDate) {
        tipp.accessEndDate = null
      }
    }
    else if (access_start_ldt && access_start_ldt > LocalDateTime.now()) {
      hasChanged |= ClassUtils.setRefdataIfPresent('Expected', tipp, 'status')

      if (tipp.accessEndDate && tipp.accessEndDate < tipp.accessStartDate) {
        tipp.accessEndDate = null
      }
    }

    hasChanged |= ClassUtils.setRefdataIfPresent(tippInfo.medium, tipp, 'medium')
    hasChanged |= ClassUtils.setRefdataIfPresent(tippInfo.language, tipp, 'language')

    if (tippInfo.paymentType in ['F', 'f']) {
      hasChanged |= ClassUtils.setRefdataIfPresent('OA', tipp, 'paymentType')
    } else if (tippInfo.paymentType in ['P', 'p']) {
      hasChanged |= ClassUtils.setRefdataIfPresent('Paid', tipp, 'paymentType')
    }

    hasChanged |= ClassUtils.setRefdataIfPresent(tippInfo.publicationType, tipp, 'publicationType')

    if (hasChanged) {
      tipp.save(flush:true, failOnError: true)
    }

    hasChanged
  }

  public Map updateLinks(TitleInstancePackagePlatform obj, reqBody, boolean changed, boolean remove = true) {
    log.debug("Updating TIPP links ..")
    Map errors = [:]
    Boolean needsSave = false

    if (reqBody.ids instanceof Collection || reqBody.identifiers instanceof Collection) {
      List id_list = reqBody.ids instanceof Collection ? reqBody.ids : reqBody.identifiers

      Map id_result = restMappingService.updateIdentifiers(obj, id_list, remove)

      if (id_result.errors.size() > 0) {
        errors.ids = id_result.errors
      }

      if (id_result.changed) {
        needsSave = true
        changed = true
      }
    }

    if (needsSave) {
      obj.lastSeen = System.currentTimeMillis()
      obj.save(flush: true)
    }

    errors
  }

  public Map reactivateOldestTitleTipp(TitleInstancePackagePlatform obj, User user = null, CuratoryGroup activeGroup = null) {
    Map result = [result: 'OK', additionalDeletes: 0]
    RefdataValue status_retired = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
    RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    String qry_str = '''from TitleInstancePackagePlatform as t
                        where t.title = :ti
                        and t.pkg = :pkg
                        order by id'''
    TitleInstance ti = obj.title ? TitleInstance.get(obj.title.id) : null

    if (ti) {
      List current_tipps = []
      List retired_tipps = []
      List ti_pkg_tipps = TitleInstancePackagePlatform.executeQuery(qry_str, [pkg: obj.pkg, ti: ti])

      ti_pkg_tipps.each { tipp ->
        if (tipp.status == status_current) {
          current_tipps << tipp
        }
        else if (tipp.status == status_retired) {
          retired_tipps << tipp
        }
      }

      if (current_tipps.size() == 1 && retired_tipps.size() > 0) {
        if (current_tipps[0].dateCreated > retired_tipps[0].dateCreated) {
          TitleInstancePackagePlatform duplicate = current_tipps[0]
          TitleInstancePackagePlatform to_reactivate = retired_tipps[0]
          retired_tipps.drop(1)

          if (retired_tipps.size() > 0) {
            retired_tipps.each { ttd ->
              ttd.status = status_deleted
              ttd.save()
              result.additionalDeletes++
            }
          }

          mergeDuplicate(duplicate, to_reactivate, user, activeGroup)
        }
        else {
          result.result = 'SKIPPED'
          result.info = "Skipped processing due to date rules (current > retired)"
        }
      }
      else {
        result.result = 'SKIPPED'
        result.info = "Skipped due to missing candidates (current: ${current_tipps.size()}, retired: ${retired_tipps.size()})"
      }
    }
    else {
      result.result = 'ERROR'
      result.code = 400
      result.message = 'Unable to reference TIPP title!'
    }

    result
  }

  public void mergeDuplicate(TitleInstancePackagePlatform duplicate, TitleInstancePackagePlatform target, User user = null, CuratoryGroup activeGroup = null, boolean keepOld = false) {
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)

    if (keepOld) {
      log.debug("Merging without info transfer ..")
    }
    else {
      log.debug("Transfering info to reactivated TIPP ..")
      List new_target_ids = duplicate.activeIdInfo

      componentUpdateService.updateIdentifiers(target, new_target_ids, user, activeGroup, true)

      Map coverage_match = [add: [], delete: []]

      duplicate.coverageStatements.each { c ->
        if (!existsCoverage(target, c)) {
          coverage_match.add << [
            'startVolume': c.startVolume,
            'startIssue': c.startIssue,
            'endVolume': c.endVolume,
            'endIssue': c.endIssue,
            'embargo': c.embargo,
            'coverageDepth': c.coverageDepth,
            'coverageNote': c.coverageNote,
            'startDate': c.startDate,
            'endDate': c.endDate
          ]
        }
      }

      target.coverageStatements.each { c ->
        if (!existsCoverage(duplicate, c)) {
          coverage_match.delete << c.id
        }
      }

      coverage_match.delete.each { cid ->
        TIPPCoverageStatement tcs_obj = TIPPCoverageStatement.get(cid)
        target.removeFromCoverageStatements(tcs_obj)
      }

      coverage_match.add.each {
        target.addToCoverageStatements(it)
      }

      log.debug("Setting new URL ${target.url} -> ${duplicate.url}")

      target.url = duplicate.url

      target.save()
    }

    if (duplicate.status == status_current && target.accessEndDate) {
      target.accessEndDate = null
    }

    if (duplicate.status != target.status) {
      target.status = duplicate.status
    }

    target.save(flush: true)

    duplicate.deleteSoft()
    touchPackage(target)
  }

  public TitleInstancePlatform ensureTipl(title, platform, url) {
    if ( ( title != null ) && ( platform != null ) && ( url?.trim()?.length() > 0 ) ) {
      RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      List r = TitleInstancePlatform.executeQuery('''from TitleInstancePlatform as tipl
                                                      where tipl.title = :ti
                                                      and tipl.hostPlatform = :plt
                                                      and tipl.status = :sc
                                                      ''',
                                                      [ti: title, plt: platform, sc: status_current])

      if ( r.size() == 0 ) {
        return new TitleInstancePlatform(url: url, hostPlatform: platform, title: title).save(flush:true, failOnError:true)
      } else if ( r.size() == 1 ) {
        TitleInstancePlatform matched_tipl = r[0]

        if (url && matched_tipl.url != url) {
          matched_tipl.url = url
          matched_tipl.save(flush: true)
        }

        return matched_tipl

      } else {
        log.warn("Found more than one TIPL for ${title} on ${platform}!")
        return null
      }
    }
  }
}
