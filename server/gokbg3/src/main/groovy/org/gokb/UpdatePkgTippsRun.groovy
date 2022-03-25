package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ESSearchService

import gokbg3.DateFormatService
import gokbg3.MessageService

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.util.Holders
import grails.util.TypeConvertingMap

import groovy.util.logging.Slf4j

import java.time.ZoneId

import org.apache.commons.lang.RandomStringUtils
import org.gokb.cred.*
import org.grails.web.json.JSONObject

@Slf4j
class UpdatePkgTippsRun {

  static MessageService messageService = Holders.grailsApplication.mainContext.getBean('messageService')
  static PackageService packageService = Holders.grailsApplication.mainContext.getBean('packageService')
  static SpringSecurityService springSecurityService = Holders.grailsApplication.mainContext.getBean('springSecurityService')
  static ComponentUpdateService componentUpdateService = Holders.grailsApplication.mainContext.getBean('componentUpdateService')
  static TitleLookupService titleLookupService = Holders.grailsApplication.mainContext.getBean('titleLookupService')
  static ReviewRequestService reviewRequestService = Holders.grailsApplication.mainContext.getBean('reviewRequestService')
  static CleanupService cleanupService = Holders.grailsApplication.mainContext.getBean('cleanupService')
  static ComponentLookupService componentLookupService = Holders.grailsApplication.mainContext.getBean('componentLookupService')
  static ESSearchService esSearchService = Holders.grailsApplication.mainContext.getBean('ESSearchService')
  static TippService tippService = Holders.grailsApplication.mainContext.getBean('tippService')
  static DateFormatService dateFormatService = Holders.grailsApplication.mainContext.getBean('dateFormatService')

  static LOCK = new Object()

  def rjson // request JSON
  boolean addOnly
  boolean fullsync
  boolean autoUpdate
  Locale locale
  User user
  Map jsonResult = [result: "SUCCESS"]
  Map errors = [global: [], tipps: []]
  def existing_tipp_ids = []
  int removedNum = 0
  def invalidTipps = []
  def matched_tipps = [:]
  Package pkg
  CuratoryGroup activeGroup
  def pkg_validation
  def pltCache = [:] // DTO.name : validPlatformInstance
  Job job = null

  def status_current
  def status_deleted
  def status_retired
  def status_expected
  def rr_deleted
  def rr_nonCurrent
  def rr_TIPPs_retired
  def rr_TIPPs_invalid
  def listStatus_ip

  public UpdatePkgTippsRun(JSONObject json, Boolean add, Boolean full, Boolean isAutoUpdate, Locale loc, User u) {
    rjson = json
    addOnly = add
    fullsync = full
    locale = loc
    user = u
    autoUpdate = isAutoUpdate
  }

  def work(Job aJob) {
    synchronized (LOCK) {
      log.info("start Update Package $rjson.packageHeader.name with ${rjson.tipps.size()} tipps")
      job = aJob ?: job
      boolean cancelled = false
      int total = 0

      try {
        status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
        status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
        status_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')
        status_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')
        rr_deleted = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Status Deleted')
        rr_nonCurrent = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Platform Noncurrent')
        rr_TIPPs_retired = RefdataCategory.lookup('ReviewRequest.StdDesc', 'TIPPs Retired')
        rr_TIPPs_invalid = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Invalid Record')
        listStatus_ip = RefdataCategory.lookup('Package.ListStatus', 'In Progress')

        springSecurityService.reauthenticate(user.username)
        user = User.get(user.id)
        job?.ownerId = user.id

        // check api permissions
        if (!(user?.apiUserStatus)) {
          globalError([code   : 403,
                       message: messageService.resolveCode('crossRef.package.error.apiRole', [], locale)]
          )
          job?.endTime = new Date()

          return jsonResult
        }

        // validate and upsert header pkg
        if (!(rjson?.packageHeader?.name)) {
          globalError([code   : 400,
                       message: messageService.resolveCode('crossRef.package.error', [], locale)]
          )
          job?.endTime = new Date()

          return jsonResult
        }

        // Package Validation
        pkg_validation = Package.validateDTO(rjson.packageHeader, locale)

        if (!pkg_validation.valid) {
          globalError([code   : 400,
                       message: messageService.resolveCode('crossRef.package.error.validation.global', null, locale),
                       errors : pkg_validation.errors]
          )
          job?.endTime = new Date()

          return jsonResult
        }

        // upsert Package
        if (rjson.packageUuid) {
          rjson.packageHeader.uuid = rjson.packageUuid
        }

        def proxy = packageService.upsertDTO(rjson.packageHeader, user)

        if (!proxy) {
          globalError([code   : 403,
                       message: messageService.resolveCode('crossRef.package.error', null, locale),
          ])
          job?.endTime = new Date()

          return jsonResult
        }

        pkg = ClassUtils.deproxy(proxy)
        componentUpdateService.ensureCoreData(pkg, rjson.packageHeader, fullsync, user)
        jsonResult.pkgId = pkg.id
        pkg.listStatus = listStatus_ip
        pkg.save(flush: true)

        job?.linkedItem = [name: pkg.name,
                          type: "Package",
                          id  : pkg.id,
                          uuid: pkg.uuid]
        job?.message("found Package ${pkg.name} (uuid: ${pkg.uuid})")

        checkActiveGroup()
        handleUpdateToken()

        existing_tipp_ids = TitleInstance.executeQuery(
            "select tipp.id from TitleInstancePackagePlatform tipp, Combo combo where " +
                "tipp.status in :status and " +
                "combo.toComponent = tipp and " +
                "combo.fromComponent = :package",
            [package: pkg, status: [status_current, status_expected]])
        log.debug("Matched package has ${pkg.tipps.size()} TIPPs")
        total = rjson.tipps.size() + (addOnly ? 0 : existing_tipp_ids.size())

        int idx = 0
        for (def json_tipp : rjson.tipps) {
          idx++
          def currentTippError = [index: idx]
          log.debug("Handling #$idx TIPP ${json_tipp.name ?: json_tipp.title.name}")
          if ((json_tipp.package == null) && (pkg.id)) {
            json_tipp.package = [id: pkg.id, internalId: pkg.id]
            if (rjson.packageHeader.fileNameDate) {
              json_tipp.updateDate = dateFormatService.parseDate(rjson.packageHeader.fileNameDate)
            }
          }
          else {
            log.error("No package")
            currentTippError.put('package', ['message': messageService.resolveCode('crossRef.package.tipps.error.pkgId', [json_tipp.title.name], locale), baddata: json_tipp.package])
            invalidTipps << json_tipp
          }

          if (!invalidTipps.contains(json_tipp)) {
            // validate and upsert PlatformInstance
            Map pltErrorMap = handlePlt(json_tipp)
            if (pltErrorMap.size() > 0) {
              currentTippError.put('platform', pltErrorMap)
            }
          }

          if (!invalidTipps.contains(json_tipp)) {
            // validate and upsert TIPP
            Map tippErrorMap = handleTIPP(json_tipp)
            if (tippErrorMap.size() > 0) {
              currentTippError.put('tipp', tippErrorMap)
            }
          }

          if (invalidTipps.contains(json_tipp)) {
            reviewRequestService.raise(
                pkg,
                "TIPP rejected",
                "TIPP ${json_tipp.name ?: json_tipp.title.name} could not be imported. ${(currentTippError as JSON).toString()}",
                user,
                null,
                (currentTippError as JSON).toString(),
                rr_TIPPs_invalid,
                componentLookupService.findCuratoryGroupOfInterest(pkg, user)
            )
            job?.message("skipped invalid title ${(currentTippError as JSON).toString()}")
          }
          else if (currentTippError.size() > 1) {
            errors.tipps.add(currentTippError)
            String msg = "ignored data ${(currentTippError as JSON).toString()}"
            job?.message(msg)
          }

          if (Thread.currentThread().isInterrupted() || job?.isCancelled()) {
            log.debug("cancelling Job #${job?.uuid}")
            cancelled = true
            def msg = "the job got cancelled"
            globalError([message: msg, code: 500])
            break
          }

          job?.setProgress(idx, total*2)

          if (idx % 50 == 0) {
            log.info("Clean up")
            cleanupService.cleanUpGorm()
          }
        }

        if (!cancelled) {
          pkg = Package.get(pkg.id)

          if (invalidTipps.size() > 0) {
            String msg = messageService.resolveCode('crossRef.package.tipps.ignored', [invalidTipps.size()], locale)
            log.warn(msg)
            jsonResult.result = "WARNING"
            jsonResult.message = msg
            errors.global.add([message: msg, baddata: rjson.packageHeader])
            job?.message(msg)
          }

          if (!rjson.tipps || rjson.tipps.size() == invalidTipps.size()) {
            log.debug("imported Package $pkg.name contains no valid TIPPs")
          }

          if (!addOnly && existing_tipp_ids.size() > 0) {
            existing_tipp_ids.eachWithIndex { ttd, ix ->
              def to_retire = TitleInstancePackagePlatform.get(ttd)

              if (to_retire?.isCurrent()) {
                if (fullsync) {
                  to_retire.deleteSoft()
                }
                else {
                  to_retire.retire()
                  to_retire.accessEndDate = to_retire.accessEndDate ?:
                      (rjson.fileNameDate ?
                          dateFormatService.parseDate(rjson.fileNameDate) : new Date())
                }

                log.info("${fullsync ? 'delete' : 'retire'} TIPP [$ix]")

                to_retire.save(failOnError: true)

                if ((++removedNum) % 50 == 0) {
                  log.debug("flush session")
                  cleanupService.cleanUpGorm()
                }
              }
            }

            if (removedNum > 0) {
              def additionalInfo = [:]
              additionalInfo.vars = [pkg.id, removedNum]
              reviewRequestService.raise(
                  pkg,
                  "TIPPs retired.",
                  "An update to package ${pkg.id} did not contain ${removedNum} previously existing TIPPs.",
                  user,
                  null,
                  (additionalInfo as JSON).toString(),
                  rr_TIPPs_retired
              )
            }
          }

          log.debug("Removed ${removedNum} TIPPS from the matched package!")
          jsonResult.result = 'OK'
          def msg = messageService.resolveCode('crossRef.package.success', [rjson.packageHeader.name, rjson.tipps.size(), existing_tipp_ids.size(), removedNum], locale)
          jsonResult.message = msg
          job?.message(msg)

          if (pkg.status != status_deleted) {
            pkg = Package.get(pkg.id)
            pkg.lastUpdateComment = jsonResult.message
            pkg.merge()
          }

          if (autoUpdate && pkg.source) {
            Source src = Source.get(pkg.source.id)
            src.lastRun = new Date()
            src.merge()
          }
        }

        tippService.matchPackage(pkg, job)

        log.debug("final flush")
        cleanupService.cleanUpGorm()

        job?.setProgress(100)

      } catch (Exception e) {
        log.error("exception caught: ", e)
        Package.withNewSession {
          String fail_msg = messageService.resolveCode('crossRef.package.error.unknown', [e], locale)
          globalError([message: fail_msg, code: 500])
        }
        job?.endTime = new Date()
      }
      if (errors.global.size() > 0) {
        jsonResult << [errors: [global: errors.global]]
      }
      if (errors.tipps.size() > 0) {
        jsonResult << [errors: [tipps: errors.tipps]]
      }

      job?.endTime = new Date()

      JobResult.withNewSession {
        def result_object = JobResult.findByUuid(job?.uuid)
        if (!result_object) {
          def job_map = [
              uuid        : (job?.uuid),
              description : (job?.description),
              resultObject: (jsonResult as JSON).toString(),
              type        : (job?.type),
              statusText  : (jsonResult.result),
              ownerId     : (job?.ownerId),
              groupId     : (job?.groupId),
              startTime   : (job?.startTime),
              endTime     : (job?.endTime),
              linkedItemId: (job?.linkedItem?.id)
          ]
          new JobResult(job_map).save(flush: true, failOnError: true)
        }
      }
      return jsonResult
    }
  }

  private void globalError(Map error) {
    log.error(error.message)
    jsonResult.result = "ERROR"
    jsonResult.code = error.code
    jsonResult.message = error.message
    errors.global.add(error)
    if (errors.tipps.size() > 0) {
      jsonResult.errors = errors
    }
    else {
      jsonResult.errors = [global: errors.global]
    }
    job?.message(error.message)
  }

  private def checkActiveGroup() {
    boolean curated_pkg = false
    def user_groups = user.curatoryGroups

    if (rjson.packageHeader.activeCuratoryGroupId) {
      int group_id = rjson.packageHeader.activeCuratoryGroupId as int
      def active_group = CuratoryGroup.get(group_id)

      if (active_group && user_groups.contains(active_group)) {
        job?.groupId = group_id
        activeGroup = active_group
      }
    }
    else if (pkg.curatoryGroups && pkg.curatoryGroups?.size() > 0) {
      def curatory_group_ids = user_groups?.id?.intersect(pkg.curatoryGroups?.id)
      if (curatory_group_ids?.size() == 1) {
        job?.groupId = curatory_group_ids[0]
        activeGroup = CuratoryGroup.get(curatory_group_ids[0])
      }
    }
  }

  private def handleUpdateToken() {
    if (!pkg_validation.match && rjson.packageHeader.generateToken) {
      String charset = (('a'..'z') + ('0'..'9')).join()
      def tokenValue = RandomStringUtils.random(255, charset.toCharArray())

      if (pkg.updateToken) {
        def currentToken = pkg.updateToken
        pkg.updateToken = null
        currentToken.delete(flush: true)
      }

      def update_token = new UpdateToken(pkg: pkg, updateUser: user, value: tokenValue).merge(flush: true)
      jsonResult.updateToken = update_token.value
    }
  }

  private Map handlePlt(JSONObject tippJson) {
    def pltKey = 'platform'
    def tippPlt = tippJson[pltKey]
    def pltError = [:]
    if (!tippPlt) {
      pltKey = 'hostPlatform'
      tippPlt = tippJson[pltKey]
    }

    def pl = pltCache[tippPlt.name]
    if (!pl) {
      log.debug("validating platform $tippPlt")
      def valid_plt = Platform.validateDTO(tippPlt)
      if (valid_plt && !valid_plt.valid) {
        log.error("platform ${tippPlt} invalid!")
        invalidTipps << tippJson
        return valid_plt.errors
      }
      else {
        if (valid_plt.errors.size() > 0) {
          pltError.putAll(valid_plt.errors)
        }
        try {
          pl = Platform.upsertDTO(tippPlt, user)
          if (pl) {
            pltCache[tippPlt.name] = pl
            pl.merge()
            componentUpdateService.ensureCoreData(pl, tippPlt, fullsync, user)
          }
          else {
            log.error("Could not find/create ${tippPlt}")
            invalidTipps << tippJson
            pltError.putAll([
                message: messageService.resolveCode('crossRef.package.tipps.error.platform', [tippPlt.name], locale),
                baddata: tippPlt])
            return pltError
          }
        }
        catch (grails.validation.ValidationException ve) {
          log.error("platform ValidationException attempting to cross reference TIPP $tippJson", ve)
          invalidTipps << tippJson
          return messageService.processValidationErrors(ve.errors, locale)
        }
      }
    }
    tippJson[pltKey].id = pl.id
    tippJson[pltKey].uuid = pl.uuid
    tippJson.hostPlatform = tippJson[pltKey]
    return pltError
  }

  private Map handleTIPP(JSONObject tippJson) {
    Map tippError = [:]
    def stash = tippJson.title
    def priority_list = ['zdb', 'eissn', 'issn', 'isbn', 'doi']
    boolean created = false
    log.debug("${stash}")
    tippJson.title = null
    def validation_result = TitleInstancePackagePlatform.validateDTO(tippJson, locale)
    tippJson.title = stash
    log.debug("validate TIPP ${tippJson.name ?: tippJson.title.name}")
    if (!validation_result.valid) {
      invalidTipps << tippJson
      log.debug("TIPP Validation failed on ${tippJson.name ?: tippJson.title.name}")
      return validation_result.errors
    }
    else {
      if (validation_result.errors?.size() > 0) {
        tippError.putAll(validation_result.errors)
      }
      log.debug("search TIPP ${tippJson.name ?: tippJson.title.name}")
      TitleInstancePackagePlatform[] current_tipps = null
      TitleInstancePackagePlatform tipp
      try {
        log.debug("Lookup ${tippJson}")
        def match_result = tippService.restLookup(tippJson)
        log.debug("Lookup returned: ${match_result}")
        // Fallunterscheidung

        if (match_result.full_matches.size() > 0) {
          tipp = match_result.full_matches[0]

          if (match_result.full_matches.size() > 1) {
            log.debug("multiple (${match_result.full_matches.size()}) full matches for $tipp")
            def additionalInfo = [otherComponents: []]

            match_result.full_matches.eachWithIndex { ct, idx ->
              if (idx > 0) {
                additionalInfo.otherComponents << [oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.id, uuid: ct.uuid, id: ct.id, name: ct.name]
              }
            }

            // RR für Multimatch generieren
            reviewRequestService.raise(
                tipp,
                "Ambiguous KBART Record Matches",
                "A KBART record has been matched on multiple package titles.",
                user,
                null,
                (additionalInfo as JSON).toString(),
                RefdataCategory.lookup('ReviewRequest.StdDesc', 'Ambiguous Record Matches'),
                componentLookupService.findCuratoryGroupOfInterest(tipp, user)
            )
          }
        }
        else if (match_result.partial_matches.size() > 0) {
          def best_matches = []

          for (int i = 0; i < priority_list.size(); i++) {
            if (match_result.partial_matches[i]?.size() > 0) {
              best_matches = match_result.partial_matches[i]
              break
            }
          }

          tipp = best_matches[0].item

          if (best_matches.size() > 1) {
            log.debug("multiple (${best_matches.size()}) partial matches for $tipp")
            def additionalInfo = [otherComponents: [], matches: [:], mismatches: [:]]

            best_matches[0].matchResults.each {
              if (it.match == 'OK') {
                additionalInfo.matches[it.namespace] = it.value
              }
              else if (it.match == 'FAIL') {
                additionalInfo.mismatches[it.namespace] = it.value
              }
            }



            if (tippJson.titleId) {
              additionalInfo.matches['title_id'] = tippJson.titleId
            }

            additionalInfo.vars = [additionalInfo.matches, additionalInfo.mismatches]
            additionalInfo.matchResults = best_matches[0].matchResults

            best_matches.each { ct, idx ->
              if (idx > 0) {
                additionalInfo.otherComponents << [oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.item.id, uuid: ct.item.uuid, id: ct.item.id, name: ct.item.name, matchResults: ct.matchResults]
              }
            }

            // RR für Multimatch generieren
            reviewRequestService.raise(
                tipp,
                "A KBART record has been matched on an existing package title by some identifiers, but not by other important identifiers.",
                "Check the package titles and merge them if necessary.",
                user,
                null,
                (additionalInfo as JSON).toString(),
                RefdataCategory.lookup('ReviewRequest.StdDesc', 'Import Identifier Mismatch'),
                componentLookupService.findCuratoryGroupOfInterest(tipp, user)
            )
          }
        }
        else {
          log.debug("Creating new TIPP..")
          created = true

          def tipp_fields = [
            pkg: pkg,
            hostPlatform: Platform.get(tippJson.hostPlatform.id),
            url: tippJson.url,
            name: tippJson.name,
            importId: tippJson.titleId
          ]

          tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_fields)

          if (match_result.failed_matches?.size() > 0) {
            log.debug("Created TIPP ${tipp} with URL ${tipp?.url}, needs review ..")

            def additionalInfo = [otherComponents: []]

            match_result.failed_matches.each { ct ->
              additionalInfo.otherComponents << [oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.item.id, uuid: ct.item.uuid, id: ct.item.id, name: ct.item.name, matchResults: ct.matchResults]
            }

            // RR für Multimatch generieren
            reviewRequestService.raise(
                tipp,
                "A KBART record has been matched on an existing package title by some identifiers ({0}), but not by other important identifiers ({1}).",
                "Check the package titles and merge them if necessary.",
                user,
                null,
                (additionalInfo as JSON).toString(),
                RefdataCategory.lookup('ReviewRequest.StdDesc', 'Import Identifier Mismatch'),
                componentLookupService.findCuratoryGroupOfInterest(tipp, user)
            )
          }
        }

        if (tipp) {
          tippService.updateSimpleFields(tipp, tippJson, true, user)

          if (!matched_tipps[tipp.id]) {
            matched_tipps[tipp.id] = 1

            if (!created) {
              TIPPCoverageStatement.executeUpdate("delete from TIPPCoverageStatement where owner = ?", [tipp])
              tipp.refresh()
            }
          }
          else {
            matched_tipps[tipp.id]++
          }

          def cov_list = tippJson.coverageStatements ?: tippJson.coverage

          cov_list.each { c ->
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

            tipp.addToCoverageStatements(coverage_item)
            tipp = tipp.merge(flush:true)
          }
        }
      }
      catch (grails.validation.ValidationException ve) {
        log.error("ValidationException attempting to create/update TIPP", ve)
        tippError.putAll(messageService.processValidationErrors(ve.errors))

        if (created) {
          tipp?.expunge()
        }

        return tippError
      }
      catch (Exception ge) {
        log.error("Exception attempting to create/update TIPP:", ge)
        def tipp_error = [
            message: messageService.resolveCode('crossRef.package.tipps.error', [tippJson.name], locale),
            baddata: tippJson,
            errors : [message: ge.toString()]
        ]
        tipp?.expunge()
        return tipp_error
      }
      if (tipp) {
        if (existing_tipp_ids.size() > 0 && existing_tipp_ids.contains(tipp.id)) {
          log.debug("Existing TIPP matched!")
          existing_tipp_ids.removeElement(tipp.id)
        }
        // Probably, these tipp.status are overwritten already
        if (tipp.status != status_deleted && tippJson.status == "Deleted") {
          tipp.deleteSoft()
          removedNum++
        }
        else if (tipp.status != status_retired && tippJson.status == "Retired") {
          tipp.retire()
          removedNum++
        }
        else if (tipp.status != status_current && (!tippJson.status || tippJson.status == "Current")) {
          if (tipp.isDeleted() && !fullsync) {
            reviewRequestService.raise(
                tipp,
                "Matched TIPP was marked as Deleted.",
                "Check TIPP Status.",
                user,
                null,
                null,
                rr_deleted
            )
          }
          tipp.status = status_current
        }
        tipp.merge()
        if (!tipp.hostPlatform) {
          log.debug("unknown hostPlatform for TIPP $tipp")
        }
        else if (tipp.isCurrent() && tipp.hostPlatform.status != status_current) {
          def additionalInfo = [:]
          additionalInfo.vars = [tipp.hostPlatform.name, tipp.hostPlatform.status?.value]
          reviewRequestService.raise(
              tipp,
              "The existing platform matched for this TIPP (${tipp.hostPlatform}) is marked as ${tipp.hostPlatform.status?.value}! Please review the URL/Platform for validity.",
              "Platform not marked as current.",
              user,
              null,
              (additionalInfo as JSON).toString(),
              rr_nonCurrent
          )
        }
      }
      else {
        log.debug("Could not reference TIPP")
        invalidTipps << tippJson
        def tipp_error = [
            message: messageService.resolveCode('crossRef.package.tipps.error', [tippJson.title.name], locale),
            baddata: tippJson
        ]
        return tipp_error
      }
    }
    return tippError
  }
}
