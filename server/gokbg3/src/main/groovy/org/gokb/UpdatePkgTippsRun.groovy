package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ESSearchService
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
  static TippService tippService =  Holders.grailsApplication.mainContext.getBean('tippService')

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

  def synchronized work(Job aJob) {
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
      rr_TIPPs_invalid = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Invalid TIPPs')
      listStatus_ip = RefdataCategory.lookup('Package.ListStatus', 'In Progress')

      springSecurityService.reauthenticate(user.username)
      user = User.get(user.id)
      job?.ownerId = user.id

      // check permissions
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
        globalError([code   : 403,
                     message: messageService.resolveCode('crossRef.package.error.validation.global', null, locale),
                     errors : pkg_validation.errors]
        )
        job?.endTime = new Date()

        return jsonResult
      }
      // upsert Package
      def proxy = packageService.upsertDTO(rjson.packageHeader, user)

      if (!proxy) {
        globalError([code   : 400,
                     message: messageService.resolveCode('crossRef.package.error', null, locale),
        ])
        job?.endTime = new Date()

        return jsonResult
      }

      pkg = Package.get(proxy.id)
      def dummy = pkg.provider
      dummy = pkg.nominalPlatform

      jsonResult.pkgId = pkg.id
      job?.linkedItem = [name: pkg.name,
                         type: "Package",
                         id  : pkg.id,
                         uuid: pkg.uuid]
      job?.message("found Package ${pkg.name} (uuid: ${pkg.uuid})")

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
        log.info("Handling #$idx TIPP ${json_tipp.name ?: json_tipp.title.name}")

        if ((json_tipp.package == null) && (pkg.id)) {
          json_tipp.package = [internalId: pkg.id]
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
              "TIPP ${json_tipp.name ?: json_tipp.title.name} coudn't be imported. ${(currentTippError as JSON).toString()}",
              user,
              null,
              (currentTippError as JSON).toString(),
              rr_TIPPs_invalid
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

        job?.setProgress(idx, total)

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
              }

              log.info("${fullsync ? 'delete' : 'retire'} TIPP [$ix]")

              to_retire.save(failOnError: true)

              if ((++removedNum) % 50 == 0) {
                log.debug("flush session");
                cleanupService.cleanUpGorm()
              }
              job?.setProgress(removedNum + rjson.tipps.size(), total)
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
      log.debug("final flush");
      cleanupService.cleanUpGorm()

      if (!cancelled) {
        job?.setProgress(100)
      }
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

    tippService.matchPackage(pkg)
    
    return jsonResult
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

  private def handleUpdateToken() {
    boolean curated_pkg = false
    def curatory_group_ids = null
    if (pkg.curatoryGroups && pkg.curatoryGroups?.size() > 0) {
      curatory_group_ids = user.curatoryGroups?.id?.intersect(pkg.curatoryGroups?.id)
      if (curatory_group_ids?.size() == 1) {
        job?.groupId = curatory_group_ids[0]
      }
      else if (curatory_group_ids?.size() > 1) {
        log.debug("Got more than one cg candidate!")
        job?.groupId = curatory_group_ids[0]
      }
      curated_pkg = true
    }

    if (curatory_group_ids || !curated_pkg
        || user.authorities.contains(Role.findByAuthority('ROLE_SUPERUSER'))) {
      componentUpdateService.ensureCoreData(pkg, rjson.packageHeader, fullsync, user)

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
        current_tipps = findTipps(tippJson)
        // Fallunterscheidung
        if (current_tipps.size() == 1) {
          log.debug("Found one matching TIPP ${current_tipps[0]}")
          tipp = current_tipps[0]
          // update Data
          componentUpdateService.ensureCoreData(tipp, tippJson, fullsync, user)
          // overwrite String properties with JSON values
          ['name', 'parentPublicationTitleId', 'precedingPublicationTitleId', 'firstAuthor', 'publisherName',
           'volumeNumber', 'editionStatement', 'firstEditor'].each { propName ->
            tipp[propName] = tippJson[propName] ?: tipp[propName]
          }

          tipp.language = tippJson.language ? RefdataCategory.lookup(KBComponent.RD_LANGUAGE, tippJson.language) : tipp.language
          tipp.publicationType = tippJson.publicationType ? RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, tippJson.publicationType) : tipp.publicationType
          tipp.parentPublicationTitleId = tippJson.parentPublicationTitleId ?: tipp.parentPublicationTitleId
          tipp.precedingPublicationTitleId = tippJson.precedingTublicationTitleId ?: tipp.precedingPublicationTitleId
          tipp.precedingPublicationTitleId = tippJson.precedingTublicationTitleId ?: tipp.precedingPublicationTitleId
          tipp.importId = tippJson.titleId ?: tipp.importId
          log.debug("Updated TIPP ${tipp} with URL ${tipp?.url}")
        }
        else {
          // TIPP neu anlegen wenn kein aktueller RR mit vorhandenen TIPPs verknüpft ist
          def idents = []
          tippJson.identifiers.each { ident ->
            idents << componentLookupService.lookupOrCreateCanonicalIdentifier(ident.type, ident.value)
          }
          tipp = new TitleInstancePackagePlatform(
              [
                  'pkg'                        : Package.get(tippJson.package.internalId),
                  'title'                      : null,
                  'hostPlatform'               : Platform.get(tippJson.hostPlatform.internalId),
                  'url'                        : null,
                  'uuid'                       : tippJson.uuid,
                  'status'                     : tippJson.status ? RefdataCategory.lookup(KBComponent.RD_STATUS, tippJson.status) : null,
                  'name'                       : tippJson.name,
                  'editStatus'                 : tippJson.editStatus ? RefdataCategory.lookup(KBComponent.RD_EDIT_STATUS, tippJson.editStatus) : null,
                  'language'                   : tippJson.language ? RefdataCategory.lookup(KBComponent.RD_LANGUAGE, tippJson.language) : null,
                  'publicationType'            : tippJson.type ? RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, tippJson.type) : null,
                  'parentPublicationTitleId'   : tippJson.parent_publication_title_id,
                  'precedingPublicationTitleId': tippJson.preceding_publication_title_id,
                  'publisherName'              : tippJson.publisherName,
                  'ids'                        : idents,
                  'importId'                   : tippJson.titleId ?: null]
          ).save()
//          idents.each { tipp.ids << it }
          componentUpdateService.ensureCoreData(tipp, tippJson, fullsync, user)
          log.debug("Created TIPP ${tipp} with URL ${tipp?.url}")
        }
        tipp?.merge()
        if (current_tipps.size() > 1 && tipp) {
          log.debug("multimatch (${current_tipps.size()}) for $tipp")
          // RR für Multimatch generieren
          def myRR = reviewRequestService.raise(
              tipp,
              "TIPP has multiple matches.",
              "Multiple Identifier Matches for TIPP.",
              user,
              null,
              null,
              RefdataCategory.lookup('ReviewRequest.StdDesc', 'Multiple Matches')
          )
          current_tipps.each {
            if (tipp != it)
              it.setStatus(status_retired)
          }
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

/**
 * this method finds similar tipps based on their identifiers (ids). for performance reasons, the ElasticSearch index
 * is used first and if it fails, a database search is performed after for a definitive decision.
 * @param tippJson
 * @return
 */
  private TitleInstancePackagePlatform[] findTipps(tippJson) {
    def tipps = []
    // search TIPPs for json.title_id == tipp.importId
    if (tippJson.titleId) {
      // elastic search
      TypeConvertingMap map = [
          componentType    : 'TitleInstancePackagePlatform',
          importId         : tippJson.titleId,
          tippPackageUuid  : pkg.uuid,
          hostPlatformUuid : tippJson.hostPlatform.uuid,
          skipDomainMapping: true
      ]
      def something = esSearchService.find(map)
      if (something.records?.size() > 0) {
        log.debug("found by titleId in ES")
        something.records.each { tipps << TitleInstancePackagePlatform.findByUuid(it.uuid) }
        return tipps
      }
      // database search
      TitleInstancePackagePlatform.executeQuery(
          'select tipp from TitleInstancePackagePlatform as tipp, Combo as c1, Combo as c2 ' +
              'where c1.fromComponent = :pkg ' +
              'and c1.toComponent = tipp ' +
              'and c1.type = :typ1 ' +
              'and c1.status = :cStatus ' +
              'and c2.fromComponent = :plt ' +
              'and c2.toComponent = tipp ' +
              'and c2.type = :typ2 ' +
              'and c2.status = :cStatus ' +
              'and tipp.importId = :tid ' +
              'and tipp.status = :tStatus  ' +
              'order by tipp.id',
          [pkg    : pkg,
           typ1   : RefdataCategory.lookup(Combo.RD_TYPE, 'Package.Tipps'),
           plt    : Platform.get(tippJson.hostPlatform.internalId),
           typ2   : RefdataCategory.lookup(Combo.RD_TYPE, 'Platform.HostedTipps'),
           cStatus: RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE),
           tid    : tippJson.titleId,
           tStatus: status_current]
      ).each { tipps << it }
      if (tipps.size() > 0) {
        log.debug("found by titleId in DB")
        return tipps
      }
    }
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
    // search for package provider namespace identifier
    IdentifierNamespace providerNamespace = Package.get(pkg.id).provider?.titleNamespace
    if (providerNamespace && jsonIdMap[providerNamespace.value]) {
      // elastic search
      map = [componentType    : 'TitleInstancePackagePlatform',
             identfiers       : [type : providerNamespace.value,
                                 value: jsonIdMap[providerNamespace.value]],
             skipDomainMapping: true]
      something = esSearchService.find(map)
      if (something.size() > 0) {
        log.debug("found by provider namespace ID in ES")
        return something.records.each { tipps << TitleInstancePackagePlatform.findByUuid(it.uuid) }
      }
      def found = TitleInstancePackagePlatform.lookupAllByIO(providerNamespace.value, jsonIdMap[providerNamespace.value])
      if (found.size() > 0) {
        found.each {
          if (TitleInstancePackagePlatform.isInstance(it) && !tipps.contains(it)) {
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
              if (TitleInstancePackagePlatform.isInstance(it) && !tipps.contains(it)
                  && it.pkg == pkg && it.status == status_current) {
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
      ['isbn', 'pisbn', 'doi'].each { ns_value ->
        if (jsonIdMap[ns_value]) {
          def found = TitleInstancePackagePlatform.lookupAllByIO(ns_value, jsonIdMap[ns_value])
          if (found.size() > 0) {
            found.each {
              if (TitleInstancePackagePlatform.isInstance(it) && !tipps.contains(it)
                  && it.pkg == pkg && it.status == status_current) {
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
