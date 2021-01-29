package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import gokbg3.MessageService
import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.util.Holders
import grails.validation.ValidationException
import org.apache.commons.lang.RandomStringUtils
import org.gokb.cred.BookInstance
import org.gokb.cred.Imprint
import org.gokb.cred.JobResult
import org.gokb.cred.KBComponent
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Role
import org.gokb.cred.Source
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.UpdateToken
import org.gokb.cred.User
import org.gokb.exceptions.MultipleComponentsMatchedException

import groovy.util.logging.*
import org.hibernate.SessionFactory

import java.time.LocalDate

@Slf4j
class CrossRefPkgRun {

  MessageService messageService = Holders.grailsApplication.mainContext.getBean('messageService')
  PackageService packageService = Holders.grailsApplication.mainContext.getBean('packageService')
  SpringSecurityService springSecurityService = Holders.grailsApplication.mainContext.getBean('springSecurityService')
  ComponentUpdateService componentUpdateService = Holders.grailsApplication.mainContext.getBean('componentUpdateService')
  TitleLookupService titleLookupService = Holders.grailsApplication.mainContext.getBean('titleLookupService')
  ReviewRequestService reviewRequestService = Holders.grailsApplication.mainContext.getBean('reviewRequestService')
  SessionFactory sessionFactory = Holders.grailsApplication.mainContext.getBean('sessionFactory')

  def rjson // request JSON
  boolean addOnly
  boolean fullsync
  boolean autoUpdate
  Locale locale
  User user
  def jsonResult = [result: "SUCCESS"]
  def errors = [global: [], tipps: []]
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
  def status_ip

  public CrossRefPkgRun(def json, Boolean add, Boolean full, Boolean isAutoUpdate, Locale loc, User u) {
    rjson = json
    addOnly = add
    fullsync = full
    locale = loc
    user = u
    autoUpdate = isAutoUpdate
  }

  def work(Job aJob) {
    log.info("start CrossrefPackage $rjson.packageHeader.name with ${rjson.tipps.size()} tipps")
    job = aJob ?: job
    boolean cancelled = false
    int total = 0

    try {
      status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      status_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')
      status_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')
      rr_deleted = RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Status Deleted')
      rr_nonCurrent = RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Platform Noncurrent')
      rr_TIPPs_retired = RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'TIPPs Retired')
      status_ip = RefdataCategory.lookup('Package.ListStatus', 'In Progress')

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
      pkg_validation = packageService.validateDTO(rjson.packageHeader, locale)

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
        log.info("Crossreferencing #$idx title ${json_tipp.name ?: json_tipp.title.name}")

        if ((json_tipp.package == null) && (pkg.id)) {
          json_tipp.package = [internalId: pkg.id]
        }
        else {
          log.error("No package")
          tippError(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.pkgId', [json_tipp.title.name], request_locale)])
          invalidTipps << json_tipp
        }

        if (!invalidTipps.contains(json_tipp)) {
          // validate and upsert TitleInstance
          handleTitle(json_tipp)
        }

        if (!invalidTipps.contains(json_tipp)) {
          // validate and upsert PlatformInstance
          handlePlt(json_tipp)
        }

        if (!invalidTipps.contains(json_tipp)) {
          // validate and upsert TIPP
          handleTIPP(json_tipp)
        }

        if (Thread.currentThread().isInterrupted() || job?.isCancelled()) {
          log.debug("cancelling Job #${job?.uuid}")
          cancelled = true
          def msg = "the Job was canceled"
          globalError([message: msg, code: 500])
          break
        }

        job?.setProgress(idx, total)

        if (idx % 10 == 0) {
          log.info("Clean up");
          // Get the current session.
          def session = sessionFactory.currentSession
          // flush and clear the session.
          session.flush()
          session.clear()
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
          if (pkg.status == status_current && pkg?.listStatus != status_ip) {
            pkg.listStatus = status_ip
            pkg.merge(flush: true)
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
                // Get the current session.
                def session = sessionFactory.currentSession
                // flush and clear the session.
                session.flush()
                session.clear()
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
          pkg.merge(flush: true)
        }

        if (autoUpdate) {
          Source src = Source.get(pkg.source.id)
          src.lastRun = new Date()
          src.merge(flush: true)
        }
      }
      log.debug("final flush");
      // Get the current session.
      def session = sessionFactory.currentSession
      // flush and clear the session.
      session.flush()
      session.clear()
    } catch (Exception e) {
      log.error("exception caught: ", e)
      String msg = messageService.resolveCode('crossRef.package.error.unknown', [e], locale)
      globalError([message: msg, code: 500])
      job?.endTime = new Date()
      cancelled = true
    }
    if (!cancelled) {
      job?.setProgress(100)
    }
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
    log.info("xRefPackage job result: $jsonResult")
    if (errors.global.size() > 0) {
      jsonResult << [errors: [global: errors.global]]
    }

    if (errors.tipps.size() > 0) {
      jsonResult << [errors: [tipps: errors.tipps]]
    }

    job?.endTime = new Date()

    return jsonResult
  }

  private void globalError(def error) {
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

  private void tippError(def error) {
    log.error(error.message)
    jsonResult.message = error.message
    errors.tipps.add(error)

    if (errors.global.size() > 0) {
      jsonResult.errors = errors
    }
    else {
      jsonResult.errors = [tipps: errors.tipps]
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

  private void handleTitle(def tippJson) {
    def title_validation = TitleInstance.validateDTO(tippJson.title)

    if (title_validation && !title_validation.valid) {
      // remove invalid identifiers from JSON and revalidate
      for (iDerror in title_validation.errors.ids) {
        tippJson.title.identifiers.remove(iDerror.baddata)
        tippJson.identifiers?.remove(iDerror.baddata)
        log.debug("removing invalid identifier $iDerror.baddata from $tippJson.title")
      }

      def preval_error = [
          message: messageService.resolveCode('crossRef.package.tipps.error.title.preValidation', [tippJson.title.name, title_validation.errors], locale),
          baddata: tippJson.title,
          errors : title_validation.errors
      ]
      title_validation = TitleInstance.validateDTO(tippJson.title)

      tippError(preval_error)

      if (title_validation && !title_validation.valid) {
        log.error("invalid title data $tippJson.title: $preval_error")
        invalidTipps << tippJson
        return
      }
    }

    def ti = null
    def titleObj = tippJson.title.name ? tippJson.title : tippJson
    def title_changed = false
    def title_class_name = IntegrationController.determineTitleClass(titleObj)

    try {
      ti = titleLookupService.findOrCreate(
          titleObj.name,
          titleObj.publisher,
          titleObj.identifiers,
          user,
          null,
          title_class_name,
          titleObj.uuid
      )

      if (ti?.id && !ti.hasErrors()) {
        if (titleObj.imprint) {
          if (title.imprint?.name == titleObj.imprint) {
            // Imprint already set
          }
          else {
            def imprint = Imprint.findByName(titleObj.imprint) ?: new Imprint(name: titleObj.imprint).save(failOnError: true)
            title.imprint = imprint
            title_changed = true
          }
        }

        // Add the core data.
        componentUpdateService.ensureCoreData(ti, titleObj, fullsync, user)

        title_changed |= componentUpdateService.setAllRefdata([
            'OAStatus', 'medium',
            'pureOA', 'continuingSeries',
            'reasonRetired'
        ], titleObj, ti)

        def pubFrom = GOKbTextUtils.completeDateString(titleObj.publishedFrom)
        def pubTo = GOKbTextUtils.completeDateString(titleObj.publishedTo, false)

        log.debug("Completed date publishedFrom ${titleObj.publishedFrom} -> ${pubFrom}")

        title_changed |= ClassUtils.setDateIfPresent(pubFrom, ti, 'publishedFrom')
        title_changed |= ClassUtils.setDateIfPresent(pubTo, ti, 'publishedTo')

        if (titleObj.historyEvents?.size() > 0) {
          def he_result = titleHistoryService.processHistoryEvents(ti, titleObj, title_class_name, user, fullsync, locale)
          if (he_result.errors) {
            tippError([message: messageService.resolveCode('crossRef.package.tipps.error.title.history', null, locale),
                       baddata: tippJson.title,
                       errors : he_result.errors])
          }
        }

        if (title_class_name == 'org.gokb.cred.BookInstance') {
          log.debug("Adding Monograph fields for ${ti.class.name}: ${ti}")
          title_changed |= addMonographFields(ti, titleObj)
        }

        if (title_changed) {
          ti.merge(flush: true)
        }

        titleLookupService.addPublisherHistory(ti, titleObj.publisher_history)
        tippJson.title.internalId = ti.id
      }
      else {
        if (ti != null) {
          tippError(['message': messageService.resolveCode('crossRef.package.tipps.error.title.validation', [ti], locale),
                     'baddata': tippJson.title,
                     'errors' : messageService.processValidationErrors(ti.errors)])
          ti.discard()
        }

        invalidTipps << tippJson
      }
    }
    catch (MultipleComponentsMatchedException mcme) {
      log.error("Handling MultipleComponentsMatchedException")
      invalidTipps << tippJson
      tippError(['message': messageService.resolveCode('crossRef.title.error.multipleMatches', [tippJson?.title?.name, mcme.matched_ids], locale)])

      return
    }
    catch (ValidationException ve) {
      log.error("ValidationException attempting to cross reference title", ve)
      invalidTipps << tippJson
      tippError(['message': messageService.resolveCode('crossRef.package.tipps.error.title.validation', [tippJson?.title?.name], locale),
                 'baddata': tippJson,
                 'errors' : messageService.processValidationErrors(ve.errors)
      ])

      return
    }

    if (!invalidTipps.contains(tippJson) && tippJson.title.internalId == null) {
      invalidTipps << tippJson
      log.error("Failed to locate a title for ${tippJson?.title} when attempting to create TIPP")
      tippError(['message': messageService.resolveCode('crossRef.package.tipps.error.title', [tippJson.title.name], locale)])
    }
  }

  private void handlePlt(def tippJson) {
    def tippPlt = tippJson.hostPlatform ?: tippJson.platform
    def pl = pltCache[tippPlt.name]

    if (!pl) {
      log.debug("validating platform $tippPlt")
      def valid_plt = Platform.validateDTO(tippPlt)

      if (valid_plt && !valid_plt.valid) {
        log.error("platform ${tippPlt} invalid!")
        invalidTipps << tippJson
        def plt_error = [
            code   : 400,
            idx    : idx,
            message: messageService.resolveCode('crossRef.package.tipps.error.platform.preValidation', [tippPlt?.name], locale),
            baddata: tippPlt,
            errors : valid_plt.errors
        ]

        tippError(plt_error)
      }
      else {
        try {
          pl = Platform.upsertDTO(tippPlt, user)

          if (pl) {
            pltCache[tippPlt.name] = pl
//            pl.save()
            pl.merge(flush: true)

            componentUpdateService.ensureCoreData(pl, tippPlt, fullsync, user)
          }
          else {
            log.error("Could not find/create ${tippPlt}")
            invalidTipps << tippJson
            tippError(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.platform', [tippPlt.name], locale)])
          }
        }
        catch (grails.validation.ValidationException ve) {
          log.error("platform ValidationException attempting to cross reference TIPP $tippJson", ve)
          invalidTipps << tippJson
          tippError([
              code   : 400,
              message: messageService.resolveCode('crossRef.package.tipps.error.platform.validation', [tippPlt], request_locale),
              baddata: tippPlt,
              errors : messageService.processValidationErrors(ve.errors, locale)
          ])

          return
        }
      }
    }
    tippJson << [hostPlatform: [internalId: pl.id]]
  }

  private void handleTIPP(def tippJson) {
    def validation_result = TitleInstancePackagePlatform.validateDTO(tippJson)
    log.debug("validate TIPP ${tippJson.name ?: tippJson.title.name}")

    if (validation_result && !validation_result.valid) {
      invalidTipps << tippJson
      log.debug("TIPP Validation failed on ${tippJson.name ?: tippJson.title.name}")
      def tipp_error = [
          message: messageService.resolveCode('crossRef.package.tipps.error.preValidation', [tippJson.title.name, validation_result.errors], locale),
          baddata: tippJson,
          errors : validation_result.errors
      ]

      tippError(tipp_error)
    }
    else {
      log.debug("upsert TIPP ${tippJson.name ?: tippJson.title.name}")
      def upserted_tipp = null

      try {
        upserted_tipp = TitleInstancePackagePlatform.upsertDTO(tippJson, user)
        log.debug("Upserted TIPP ${upserted_tipp} with URL ${upserted_tipp?.url}")
        upserted_tipp.merge(flush: true)
        componentUpdateService.ensureCoreData(upserted_tipp, tippJson, fullsync, user)
      }
      catch (grails.validation.ValidationException ve) {
        log.error("ValidationException attempting to cross reference TIPP", ve)
        def tipp_error = [
            message: messageService.resolveCode('crossRef.package.tipps.error.validation', [tippJson.title.name], locale),
            baddata: tippJson,
            errors : messageService.processValidationErrors(ve.errors)
        ]
        tippError(tipp_error)
        upserted_tipp?.discard()
      }
      catch (Exception ge) {
        log.error("Exception attempting to cross reference TIPP:", ge)
        def tipp_error = [
            message: messageService.resolveCode('crossRef.package.tipps.error', [tippJson.title.name], locale),
            baddata: tippJson,
            errors : [message: ge.toString()]
        ]
        tippError(tipp_error)
        upserted_tipp?.discard()
      }

      if (upserted_tipp) {
        if (existing_tipp_ids.size() > 0 && existing_tipp_ids.contains(upserted_tipp.id)) {
          log.debug("Existing TIPP matched!")
          existing_tipp_ids.removeElement(upserted_tipp.id)
        }

        if (upserted_tipp.status != status_deleted && tippJson.status == "Deleted") {
          upserted_tipp.deleteSoft()
          removedNum++;
        }
        else if (upserted_tipp.status != status_retired && tippJson.status == "Retired") {
          upserted_tipp.retire()
          removedNum++;
        }
        else if (upserted_tipp.status != status_current && (!tippJson.status || tippJson.status == "Current")) {
          if (upserted_tipp.isDeleted() && !fullsync) {
            // upserted_tipp.merge(flush: true)
            reviewRequestService.raise(
                upserted_tipp,
                "Matched TIPP was marked as Deleted.",
                "Check TIPP Status.",
                user,
                null,
                null,
                rr_deleted
            )
          }
          upserted_tipp.status = status_current
        }
//        upserted_tipp.save()
        upserted_tipp.merge(flush: true)

        if (upserted_tipp.isCurrent() && upserted_tipp.hostPlatform?.status != status_current) {
          def additionalInfo = [:]
          additionalInfo.vars = [upserted_tipp.hostPlatform.name, upserted_tipp.hostPlatform.status?.value]
          reviewRequestService.raise(
              upserted_tipp,
              "The existing platform matched for this TIPP (${upserted_tipp.hostPlatform}) is marked as ${upserted_tipp.hostPlatform.status?.value}! Please review the URL/Platform for validity.",
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
        tippError(tipp_error)
      }
    }
  }

  private boolean addMonographFields(BookInstance bi, titleObj) {
    def book_changed = false

    ["editionNumber", "editionDifferentiator",
     "editionStatement", "volumeNumber",
     "summaryOfContent", "firstAuthor",
     "firstEditor"].each { stringPropertyName ->
      if (titleObj[stringPropertyName] && titleObj[stringPropertyName].toString().trim().length() > 0) {
        book_changed |= ClassUtils.setStringIfDifferent(bi, stringPropertyName, titleObj[stringPropertyName])
      }
    }

    def dfip = GOKbTextUtils.completeDateString(titleObj.dateFirstInPrint)
    book_changed |= ClassUtils.setDateIfPresent(dfip, bi, 'dateFirstInPrint')

    def dfo = GOKbTextUtils.completeDateString(titleObj.dateFirstOnline, false)
    book_changed |= ClassUtils.setDateIfPresent(dfo, bi, 'dateFirstOnline')

    book_changed
  }
}
