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
  Package pkg
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
        job?.linkedItem = [name: pkg.name,
                          type: "Package",
                          id  : pkg.id,
                          uuid: pkg.uuid]
        job?.message("found Package ${pkg.name} (uuid: ${pkg.uuid})")

        setActiveGroup()
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
            json_tipp.package = [internalId: pkg.id,
                                 updateDate: rjson.fileNameDate ? dateFormatService.parseDate(rjson.fileNameDate) : new Date()]
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

          if (idx % 100 == 0) {
            log.info("Clean up");
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

          if (rjson.tipps?.size() > 0 && rjson.tipps.size() > invalidTipps.size()) {
            if (pkg.status == status_current && pkg?.listStatus != listStatus_ip) {
              pkg.listStatus = listStatus_ip
              pkg.merge()
            }
          }
          else {
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
                  log.debug("flush session");
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

        log.debug("final flush");
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

  private def setActiveGroup() {
    boolean curated_pkg = false
    CuratoryGroup[] user_groups = user.curatoryGroups

    if (rjson.activeCuratoryGroupId) {
      def active_group = CuratoryGroup.get(rjson.activeCuratoryGroupId)

      if (active_group && user_groups.contains(active_group)) {
        job?.groupId = rjson.activeCuratoryGroupId
      }
    }
    else if (pkg.curatoryGroups && pkg.curatoryGroups?.size() > 0) {
      def curatory_group_ids = user_groups?.id?.intersect(pkg.curatoryGroups?.id)
      if (curatory_group_ids?.size() == 1) {
        job?.groupId = curatory_group_ids[0]
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
    tippJson[pltKey].internalId = pl.id
    tippJson[pltKey].uuid = pl.uuid
    tippJson.hostPlatform = tippJson[pltKey]
    return pltError
  }

  private Map handleTIPP(JSONObject tippJson) {
    Map tippError = [:]
    def stash = tippJson.title
    log.debug("${stash}")
    tippJson.title = null
    def validation_result = TitleInstancePackagePlatform.validateDTO(tippJson, locale)
    tippJson.title = stash
    log.debug("${tippJson.title}")
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
        current_tipps = findTipps(tippJson)
        // Fallunterscheidung
        if (current_tipps.size() == 0) {
          // TIPP neu anlegen wenn kein aktueller RR mit vorhandenen TIPPs verknüpft ist
          def idents = []
          tippJson.identifiers.each { ident ->
            idents << componentLookupService.lookupOrCreateCanonicalIdentifier(ident.type, ident.value)
          }
          tipp = new TitleInstancePackagePlatform(
              [
                  'url'                        : tippJson.url,
                  'uuid'                       : tippJson.uuid,
                  'status'                     : tippJson.status ? RefdataCategory.lookup(KBComponent.RD_STATUS, tippJson.status) : null,
                  'name'                       : tippJson.name,
                  'editStatus'                 : tippJson.editStatus ? RefdataCategory.lookup(KBComponent.RD_EDIT_STATUS, tippJson.editStatus) : null,
                  'language'                   : tippJson.language ? RefdataCategory.lookup(KBComponent.RD_LANGUAGE, tippJson.language) : null,
                  'publicationType'            : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, tippJson.publicationType ?: tippJson.type ?: 'Serial'),
                  'parentPublicationTitleId'   : tippJson.parent_publication_title_id,
                  'precedingPublicationTitleId': tippJson.preceding_publication_title_id,
                  'publisherName'              : tippJson.publisherName,
                  'importId'                   : tippJson.titleId ?: null,
                  'accessStartDate'            : tippJson.accessStartDate ? dateFormatService.parseDate(tippJson.accessStartDate) : tippJson.package.updateDate,
                  'accessEndDate'              : tippJson.accessEndDate ? dateFormatService.parseDate(tippJson.accessEndDate) : null]
          ).save(flush:true)

          def pkg_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Tipps')
          new Combo(toComponent: tipp, fromComponent: pkg, type: pkg_combo_type).save(flush: true, failOnError: true)

          def plt_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTipps')
          new Combo(toComponent: tipp, fromComponent: Platform.get(tippJson.hostPlatform.internalId), type: plt_combo_type).save(flush: true, failOnError: true)

//          idents.each { tipp.ids << it }
          updateTippData(tipp, tippJson)

          log.debug("Created TIPP ${tipp} with URL ${tipp?.url}")
        }
        else {
          log.debug("Found ${current_tipps.size()} matching TIPP(s)")
          def full_matches = []
          def mismatches = []
          def id_mismatches = [:]

          def jsonIdMap = [:]
          tippJson.identifiers.each { jsonId ->
            jsonIdMap[jsonId.type] = jsonId.value
          }
          if (jsonIdMap.size() == 0) {
            tippJson.title.identifiers.each { jsonId ->
              jsonIdMap[jsonId.type] = jsonId.value
            }
          }

          current_tipps.each { ctipp ->
            def tipp_ids = ctipp.ids.collect { ido -> [type: ido.namespace.value, value: ido.value, normname: ido.normname]}

            tipp_ids.each { tid ->
              if (jsonIdMap[tid.type] && Identifier.normalizeIdentifier(jsonIdMap[tid.type]) != tid.normname && tid.type != 'pisbn') {
                id_mismatches[tid.type] = jsonIdMap[tid.type]
              }
            }

            if (id_mismatches.size() > 0) {
              mismatches << ctipp
            }
            else {
              full_matches << ctipp
            }
          }

          if (full_matches.size() > 0) {
            tipp = full_matches[0]
            // update Data
            updateTippData(tipp, tippJson)
            log.debug("Updated TIPP ${tipp} with URL ${tipp?.url}")

            if (full_matches.size() > 1) {
              log.debug("multimatch (${full_matches.size()}) for $tipp")
              def additionalInfo = [otherComponents: []]

              full_matches.eachWithIndex { ct, idx ->
                if (idx > 0) {
                  additionalInfo.otherComponents << [oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.id, uuid: ct.uuid, id: ct.id, name: ct.name]
                }
              }

              // RR für Multimatch generieren
              def myRR = reviewRequestService.raise(
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
          else {
            log.debug("All matches contain identifier conflicts. Creating new TIPP..")
            def idents = []
            tippJson.identifiers.each { ident ->
              idents << componentLookupService.lookupOrCreateCanonicalIdentifier(ident.type, ident.value)
            }
            tipp = new TitleInstancePackagePlatform(
                [
                    'url'                        : tippJson.url,
                    'uuid'                       : tippJson.uuid,
                    'status'                     : tippJson.status ? RefdataCategory.lookup(KBComponent.RD_STATUS, tippJson.status) : null,
                    'name'                       : tippJson.name,
                    'editStatus'                 : tippJson.editStatus ? RefdataCategory.lookup(KBComponent.RD_EDIT_STATUS, tippJson.editStatus) : null,
                    'language'                   : tippJson.language ? RefdataCategory.lookup(KBComponent.RD_LANGUAGE, tippJson.language) : null,
                    'publicationType'            : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, tippJson.publicationType ?: tippJson.type ?: 'Serial'),
                    'parentPublicationTitleId'   : tippJson.parent_publication_title_id,
                    'precedingPublicationTitleId': tippJson.preceding_publication_title_id,
                    'publisherName'              : tippJson.publisherName,
                    'importId'                   : tippJson.titleId ?: null,
                    'accessStartDate'            : tippJson.accessStartDate ? dateFormatService.parseDate(tippJson.accessStartDate) : tippJson.package.updateDate,
                    'accessEndDate'              : tippJson.accessEndDate ? dateFormatService.parseDate(tippJson.accessEndDate) : null]
            ).save(flush:true)

            def pkg_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Tipps')
            new Combo(toComponent: tipp, fromComponent: pkg, type: pkg_combo_type).save(flush: true, failOnError: true)

            def plt_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTipps')
            new Combo(toComponent: tipp, fromComponent: Platform.get(tippJson.hostPlatform.internalId), type: plt_combo_type).save(flush: true, failOnError: true)

            updateTippData(tipp, tippJson)

            log.debug("Created TIPP ${tipp} with URL ${tipp?.url}, needs review ..")

            def additionalInfo = [otherComponents: []]
            def id_matching = [:]

            jsonIdMap.each { ns, val ->
              if (!id_mismatches[ns]) {
                id_matching[ns] = val
              }
            }

            if (tippJson.titleId) {
              id_matching['title_id'] = tippJson.titleId
            }

            additionalInfo.matches = id_matching
            additionalInfo.mismatches = id_mismatches
            additionalInfo.vars = [id_matching, id_mismatches]

            mismatches.eachWithIndex { ct, idx ->
              if (idx > 0) {
                additionalInfo.otherComponents << [oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.id, uuid: ct.uuid, id: ct.id, name: ct.name]
              }
            }

            // RR für Multimatch generieren
            def myRR = reviewRequestService.raise(
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
          tipp = tippService.updateCoverage(tipp, tippJson)
          tipp.merge()
        }
      } catch (grails.validation.ValidationException ve) {
        log.error("ValidationException attempting to create/update TIPP", ve)
        tipp?.expunge()
        tippError.putAll(messageService.processValidationErrors(ve.errors))
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
          removedNum++;
        }
        else if (tipp.status != status_retired && tippJson.status == "Retired") {
          tipp.retire()
          removedNum++;
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

  private void updateTippData(tipp, tippJson) {
    componentUpdateService.ensureCoreData(tipp, tippJson, fullsync, user)
    // overwrite String properties with JSON values
    ['name', 'parentPublicationTitleId', 'precedingPublicationTitleId', 'firstAuthor', 'publisherName',
    'volumeNumber', 'editionStatement', 'firstEditor', 'url', 'importId', 'subjectArea', 'series'].each { propName ->
      tipp[propName] = tippJson[propName] ?: tipp[propName]
    }

    if (tippJson.medium) {
      def tmed = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_MEDIUM, tippJson.medium)

      if (tmed) {
        tipp.medium = tmed
      }
    }

    if (tippJson.dateFirstInPrint) {
      ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(tippJson.dateFirstInPrint), tipp, 'dateFirstInPrint')
    }
    else {
      log.debug("No dateFirstInPrint -> ${tippJson.dateFirstInPrint}")
    }

    if (tippJson.dateFirstOnline) {
      ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(tippJson.dateFirstOnline), tipp, 'dateFirstOnline')
    }

    tipp.language = tippJson.language ? RefdataCategory.lookup(KBComponent.RD_LANGUAGE, tippJson.language) : tipp.language
    tipp.publicationType = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, tippJson.publicationType ?: tippJson.type ?: tipp.publicationType.value)
  }

/**
 * this method finds similar tipps based on their identifiers (ids). for performance reasons, the ElasticSearch index
 * is used first and if it fails, a database search is performed after for a definitive decision.
 * @param tippJson
 * @return
 */
  private TitleInstancePackagePlatform[] findTipps(tippJson) {
    def tipps = []
    // remap JSON Identifiers to [type: value]
    def jsonIdMap = [:]
    tippJson.identifiers.each { jsonId ->
      jsonIdMap[jsonId.type] = jsonId.value
    }
    if (jsonIdMap.size() == 0) {
      tippJson.title.identifiers.each { jsonId ->
        jsonIdMap[jsonId.type] = jsonId.value
      }
    }
    // search TIPPs for json.title_id == tipp.importId
    if (tippJson.titleId) {
      /**
      * Exclude ES-lookup for now because of missing matches of newly created TIPPs
      * Reevaluate with v8.19.
      */

      // elastic search
      // TypeConvertingMap map = [
      //     componentType    : 'TitleInstancePackagePlatform',
      //     importId         : tippJson.titleId,
      //     pkg              : pkg.uuid,
      //     platform         : tippJson.hostPlatform.uuid,
      //     skipDomainMapping: true
      // ]
      // def something = esSearchService.find(map)
      // if (something.records?.size() > 0) {
      //   log.debug("found by titleId in ES")
      //   def error_tipps = []
      //   something.records.each {
      //     def tipp = TitleInstancePackagePlatform.findByUuid(it.uuid)

      //     if (tipp) {
      //       def tipp_ids = tipp.ids.collect { ido -> { type: ido.namespace.value, value: ido.value }}
      //       def id_mismatches = []
      //       tipp_ids.each { tid ->
      //         if (jsonIdMap[tid.type] && jsonIdMap[tid.type] != tid.value) {
      //           ids_mismatches << tid
      //         }
      //       }
      //       if (id_mismatches) {
      //         error_tipps << tipp
      //       }
      //       else {
      //         tipps << tipp
      //       }
      //     }
      //     else  {
      //       log.warn("ES record TIPP ${it.uuid} does not exist!")
      //     }
      //   }

      //   if (error_tipps.size() > 0 && tipps.size() == 0) {
      //     def additionalInfo = [:]
      //     additionalInfo.vars = [tipp.hostPlatform.name, tipp.hostPlatform.status?.value]
      //     reviewRequestService.raise(
      //         tipp,
      //         "The existing platform matched for this TIPP (${tipp.hostPlatform}) is marked as ${tipp.hostPlatform.status?.value}! Please review the URL/Platform for validity.",
      //         "Platform not marked as current.",
      //         user,
      //         null,
      //         (additionalInfo as JSON).toString(),
      //         rr_nonCurrent
      //     )
      //   }

      //   return tipps
      // }
      // database search
      def tippList = TitleInstancePackagePlatform.executeQuery(
          'select tipp from TitleInstancePackagePlatform as tipp, Combo as c1, Combo as c2 ' +
              'where c1.fromComponent = :pkg ' +
              'and c1.toComponent = tipp ' +
              'and c1.type = :typ1 ' +
              'and c2.fromComponent = :plt ' +
              'and c2.toComponent = tipp ' +
              'and c2.type = :typ2 ' +
              'and tipp.importId = :tid ' +
              'and tipp.status = :tStatus  ' +
              'order by tipp.id',
          [pkg    : pkg,
           typ1   : RefdataCategory.lookup(Combo.RD_TYPE, 'Package.Tipps'),
           plt    : Platform.get(tippJson.hostPlatform.internalId),
           typ2   : RefdataCategory.lookup(Combo.RD_TYPE, 'Platform.HostedTipps'),
           tid    : tippJson.titleId,
           tStatus: status_current]
      )
      if (tippList.size() > 0) {
        log.debug("found by titleId in DB")
        return tippList
      }
    }

    // search for package provider namespace identifier
    IdentifierNamespace providerNamespace = Package.get(pkg.id).provider?.titleNamespace
    if (providerNamespace && jsonIdMap[providerNamespace.value]) {
      /**
      * Exclude ES-lookup for now because of missing matches of newly created TIPPs
      * Reevaluate with v8.19.
      */

      // elastic search
      // TypeConvertingMap map = [
      //     componentType     : 'TitleInstancePackagePlatform',
      //     identfiers        : providerNamespace.value + ',' + jsonIdMap[providerNamespace.value],
      //     pkg               : pkg.uuid,
      //     platform          : tippJson.hostPlatform.uuid,
      //     skipDomainMapping : true
      // ]

      // def something = esSearchService.find(map)

      // if (something.records?.size() > 0) {
      //   log.debug("found by provider namespace ID in ES")
      //   something.records.each {
      //     def tipp = TitleInstancePackagePlatform.findByUuid(it.uuid)

      //     if (tipp) {
      //       tipps << tipp
      //     }
      //     else  {
      //       log.warn("ES record TIPP ${it.uuid} does not exist!")
      //     }
      //   }
      //   return tipps
      // }

      def found = TitleInstancePackagePlatform.lookupAllByIO(providerNamespace.value, jsonIdMap[providerNamespace.value])

      if (found.size() > 0) {
        found.each {
          if (TitleInstancePackagePlatform.isInstance(it) && !tipps.contains(it) && it.pkg == pkg && it.hostPlatform.uuid == tippJson.hostPlatform.uuid) {
            tipps.add(it)
          }
        }
        if (tipps.size() > 0) {
          log.debug("found by provider namespace ID in DB")
          return tipps
        }
      }
    }
    // search for other Identifiers, depending on publicationType
    if ("SERIAL".equalsIgnoreCase(tippJson.type)) {
      // Journal
      ['zdb', 'eissn', 'issn', 'doi'].each { ns_value ->
        if (jsonIdMap[ns_value]) {
          def found = TitleInstancePackagePlatform.lookupAllByIO(ns_value, jsonIdMap[ns_value])
          if (found.size() > 0) {
            found.each {
              if (TitleInstancePackagePlatform.isInstance(it)
                  && !tipps.contains(it)
                  && it.pkg == pkg
                  && it.status == status_current
                  && it.hostPlatform.uuid == tippJson.hostPlatform.uuid
                  && (!tippJson.titleId || !it.importId)) {
                tipps.add(it)
              }
            }
          }
        }
      }
      if (tipps.size() > 0) {
        log.debug("found by journal identifier set")
      }
    }
    else if ("MONOGRAPH".equalsIgnoreCase(tippJson.type)) {
      // Book
      ['isbn', 'doi'].each { ns_value ->
        if (jsonIdMap[ns_value]) {
          def found = TitleInstancePackagePlatform.lookupAllByIO(ns_value, jsonIdMap[ns_value])
          if (found.size() > 0) {
            found.each {
              if (TitleInstancePackagePlatform.isInstance(it)
                  && !tipps.contains(it)
                  && it.pkg == pkg
                  && it.status == status_current
                  && it.hostPlatform.uuid == tippJson.hostPlatform.uuid
                  && (!tippJson.titleId || !it.importId)) {
                tipps.add(it)
              }
            }
          }
        }
      }
      if (tipps.size() > 0) {
        log.debug("found by monograph identifier set")
      }
    }
    return tipps
  }
}
