package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import grails.converters.JSON
import grails.validation.ValidationException
import org.apache.commons.lang.RandomStringUtils
import org.gokb.cred.BookInstance
import org.gokb.cred.Imprint
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Role
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.UpdateToken
import org.gokb.cred.User

import java.util.concurrent.CancellationException

class CrossReferenceService {

  def concurrencyManagerService
  def packageService
  def springSecurityService
  def componentUpdateService
  def titleLookupService
  def reviewRequestService
  def messageService
  def titleHistoryService
  def sessionFactory

  static transactional = false

  static determineTitleClass(titleObj) {
    if (titleObj.type) {
      switch (titleObj.type) {
        case "serial":
        case "Serial":
        case "Journal":
        case "journal":
          return "org.gokb.cred.JournalInstance"
          break;
        case "monograph":
        case "Monograph":
        case "Book":
        case "book":
          return "org.gokb.cred.BookInstance"
          break;
        case "Database":
        case "database":
          return "org.gokb.cred.DatabaseInstance"
          break;
        case "Other":
        case "other":
          return "org.gokb.cred.OtherInstance"
          break;
        default:
          return null
          break;
      }
    } else {
      return null
    }
  }

  static addMonographFields(BookInstance bi, titleObj) {

    def book_changed = false

    def bookStringAttrs = ["editionNumber", "editionDifferentiator",
                           "editionStatement", "volumeNumber",
                           "summaryOfContent", "firstAuthor", "firstEditor"]

    bookStringAttrs.each {
      if (titleObj[it] && titleObj[it].toString().trim().length() > 0) {
        book_changed |= ClassUtils.setStringIfDifferent(bi, it, titleObj[it])
      }
    }
    def dfip = GOKbTextUtils.completeDateString(titleObj.dateFirstInPrint)
    def dfo = GOKbTextUtils.completeDateString(titleObj.dateFirstOnline, false)

    book_changed |= ClassUtils.setDateIfPresent(dfip, bi, 'dateFirstInPrint')
    book_changed |= ClassUtils.setDateIfPresent(dfo, bi, 'dateFirstOnline')

    if (book_changed) {
      bi.save(failOnError: true)
    }

    book_changed
  }

  /**
   * starting a crossReferencePackage job
   * @param rjson
   * @param fullsync
   * @param update
   * @param request_user
   * @param request_locale
   * @param async wait for the job to end before returning from this method
   * @param result
   * @return result parameter enriched by the data from this method run
   */
  def xRefPackage(def rjson, boolean fullsync, boolean update, def request_user, def request_locale, boolean async, def result, boolean autoUpdate) {
    def pkg_validation = packageService.validateDTO(rjson.packageHeader, request_locale)
    if (!pkg_validation.valid) {
      log.debug("Package validation failed!")
      result.result = 'ERROR'
      response.setStatus(400)
      result.errors = pkg_validation.errors
      result.message = messageService.resolveCode('crossRef.package.error.validation.global', null, request_locale)
      return result
    }

    Package upserted_pkg = packageService.upsertDTO(rjson.packageHeader, request_user)
    if (!upserted_pkg) {
      result.result = 'ERROR'
      response.setStatus(400)
      result.errors = ['message'   : 'Package write error',
                       'linkedItem': ['name': upserted_pkg.name,
                                      'type': "Package",
                                      'id'  : upserted_pkg.id,
                                      'uuid': upserted_pkg.uuid]]
      return result
    }
    ConcurrencyManagerService.Job background_job = concurrencyManagerService.createJob { ConcurrencyManagerService.Job job ->
      return xRefTippsWork(job, request_user, request_locale, rjson, upserted_pkg.id, fullsync, update, autoUpdate)
    }
    log.debug("Starting job ${background_job}..")
    background_job.description = "Package CrossRef (${rjson.packageHeader.name})"
    background_job.type = RefdataCategory.lookupOrCreate('Job.Type', 'PackageCrossRef')
    background_job.linkedItem = [name: upserted_pkg.name,
                                 type: "Package",
                                 id  : upserted_pkg.id,
                                 uuid: upserted_pkg.uuid]
    background_job.message("Starting upsert for Package ${upserted_pkg.name} (uuid: ${upserted_pkg.uuid})".toString())
    background_job.startOrQueue()
    background_job.startTime = new Date()

    if (!async) {
      def job_final = null
      try {
        job_final = background_job.get()
      }
      catch (CancellationException ce) {
        result.result = 'ERROR'
        result.job_id = background_job.id
        result.message = "The import job was cancelled before completion."
      }
      if (job_final) {
        result = job_final
      }
      return result
    }
    result.job_id = background_job.id
    result.message = background_job.description
    result.linkedItem = background_job.linkedItem
    result.startTime = background_job.startTime
    return result
  }

  def xRefTippsWork(ConcurrencyManagerService.Job job, def request_user, def request_locale, def json, def pkgId, boolean fullsync, boolean update, boolean autoUpdate) {
    def job_result = [:]
    boolean cancelled = false
    def errors = [global: [], tipps: []]
    int num_removed_tipps = 0
    // Package.withNewSession begin
    Package.withNewSession {
      def user = User.get(request_user.id)
      def locale = request_locale
      springSecurityService.reauthenticate(request_user.username)

      job.ownerId = user.id

      try {
        Package the_pkg = Package.get(pkgId)
        def existing_tipps = []
        boolean valid = true

        if (!the_pkg) {
          // Package not found in DB - Fatal error
          job_result.result = 'ERROR'
          errors.global.add(['code': 400, 'message': message.resolveCode('crossRef.package.error', null, locale)])
          job_result.errors = errors
          job_result.message = messageService.resolveCode('crossRef.package.error', null, locale)
          return job_result
        }
        job_result.pkgId = the_pkg.id
        job_result.uuid = the_pkg.uuid
        job_result.name = the_pkg.name

        if (!the_pkg.curatoryGroups || the_pkg.curatoryGroups.size() < 1) {
          valid = false
          log.warn("Package update denied because it has no curatory group set!")
          job_result.result = 'ERROR'
          errors.global.add(['code': 400, 'message': message.resolveCode('crossRef.package.error.deied', null, locale)])
          job_result.errors = errors
          job_result.message = messageService.resolveCode('crossRef.package.error.denied', [the_pkg.name, the_pkg.curatoryGroups], locale)
          return job_result
        }
        def is_curator = user.curatoryGroups?.id.intersect(the_pkg.curatoryGroups.id)

        if (is_curator?.size() == 1) {
          job.groupId = is_curator[0]
        } else if (is_curator?.size() > 1) {
          log.debug("Got more than one cg candidate!")
          job.groupId = is_curator[0]
        }

        if (is_curator || user.authorities.contains(Role.findByAuthority('ROLE_SUPERUSER'))) {
          componentUpdateService.ensureCoreData(the_pkg, json.packageHeader, fullsync, user)

          if (the_pkg.tipps) {
            //existing_tipps = TitleInstance.executeQuery(all current tipps in package)
            existing_tipps = TitleInstance.executeQuery(
              "select tipp.id from TitleInstancePackagePlatform tipp, Combo combo " +
                "where tipp.status = :status " +
                "and combo.toComponent=tipp " +
                "and combo.fromComponent=:package",
              [package: the_pkg, status: RefdataCategory.lookup('KBComponent.Status', 'Current')])
            //existing_tipps = the_pkg.tipps*.id
            log.debug("Matched package has ${existing_tipps.size()} TIPPs")
          }
          def tipps_to_delete = existing_tipps?.clone()

          Map platform_cache = [:]
          log.debug("\n\n\nPackage ID: ${the_pkg.id} / ${json.packageHeader}");
          def idx = 0

          // Validate and upsert titles and platforms
          def tipp_upsert_start_time = System.currentTimeMillis()
          for (json_tipp in json.tipps) {
            idx++
            log.debug("handling tipp ${json_tipp.title}");
            def title_validation = TitleInstance.validateDTO(json_tipp.title);
            def tipp_plt_dto = json_tipp.hostPlatform ?: json_tipp.platform
            valid &= title_validation.valid

            if (title_validation && !title_validation.valid) {
              log.warn("Not valid after title validation ${json_tipp.title}");
              def preval_errors = [
                code   : 400,
                message: messageService.resolveCode('crossRef.package.tipps.error.title.preValidation', [json_tipp.title.name, title_validation.errors], locale),
                baddata: json_tipp.title,
                idx    : idx,
                errors : title_validation.errors
              ]
              errors.tipps.add(preval_errors)
            } else {
              def valid_ti = true

              TitleInstance.withNewSession {
                def ti = null
                def titleObj = json_tipp.title.name ? json_tipp.title : json_tipp
                def title_changed = false
                def title_class_name = determineTitleClass(titleObj)

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
                      } else {
                        def imprint = Imprint.findByName(titleObj.imprint) ?: new Imprint(name: titleObj.imprint).save(flush: true, failOnError: true);
                        title.imprint = imprint;
                        title_changed = true
                      }
                    }

                    // Add the core data.
                    log.debug("ensure core data")
                    componentUpdateService.ensureCoreData(ti, titleObj, fullsync, user)
                    log.debug("set all Refdata")
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
                        result.errors = he_result.errors
                      }
                    }

                    if (title_class_name == 'org.gokb.cred.BookInstance') {

                      log.debug("Adding Monograph fields for ${ti.class.name}: ${ti}")
                      def mg_change = addMonographFields((BookInstance) ti, titleObj)

                      // TODO: Here we will have to add authors and editors, like addPerson() in TSVIngestionService
                      if (mg_change) {
                        title_changed = true
                      }
                    }

                    titleLookupService.addPublisherHistory(ti, titleObj.publisher_history)

                    ti = ti.merge(flush: true)

                    json_tipp.title.internalId = ti.id
                  } else {
                    def errorObj = ['code': 400, 'message': messageService.resolveCode('crossRef.package.tipps.error.title', json_tipp.title.name, locale), 'baddata': json_tipp.title]
                    if (ti != null) {
                      errorObj.errors = messageService.processValidationErrors(ti.errors)
                      errors.tipps.add(errorObj)
                      ti.discard()
                    }
                    valid_ti = false
                    valid = false
                  }
                }
                catch (org.gokb.exceptions.MultipleComponentsMatchedException mcme) {
                  log.debug("Handling MultipleComponentsMatchedException")
                  result.result = "ERROR"
                  result.message = messageService.resolveCode('crossRef.title.error.multipleMatches', [json_tipp?.title?.name, mcme.matched_ids], locale)
                }
                catch (grails.validation.ValidationException ve) {
                  log.error("ValidationException attempting to cross reference title", ve);
                  valid_ti = false
                  valid = false
                  def validation_errors = [
                    code   : 400,
                    message: messageService.resolveCode('crossRef.package.tipps.error.title.validation', [json_tipp?.title?.name], locale),
                    baddata: json_tipp,
                    idx    : idx,
                    errors : messageService.processValidationErrors(ve.errors)
                  ]
                  errors.tipps.add(validation_errors)
                }
              }

              if (valid_ti && json_tipp.title.internalId == null) {
                log.error("Failed to locate a title for ${json_tipp?.title} when attempting to create TIPP");
                valid = false
                errors.tipps.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.title', [json_tipp?.title?.name], locale)])
              }
            }

            def valid_plt, valid_cached = false
            if (!platform_cache.containsKey(tipp_plt_dto.name)) {
              // platform is new and has to be validated
              valid_plt = Platform.validateDTO(tipp_plt_dto)
              valid &= valid_plt?.valid
            } else {
              valid_plt = platform_cache[tipp_plt_dto.name].platform
              valid_cached = platform_cache[tipp_plt_dto.name].valid
            }

            if (!valid_cached && !valid_plt.valid) {
              log.warn("Not valid after platform validation ${tipp_plt_dto}");

              def plt_errors = [
                code   : 400,
                idx    : idx,
                message: messageService.resolveCode('crossRef.package.tipps.error.platform.preValidation', [tipp_plt_dto?.name], locale),
                baddata: tipp_plt_dto,
                errors : valid_plt.errors
              ]
              errors.tipps.add(plt_errors)
            }
            Platform pl = null
            if (valid) {
              def pl_id
              if (platform_cache.containsKey(tipp_plt_dto.name)) {
                pl = platform_cache[tipp_plt_dto.name].platform
              } else {
                // Not in cache.
                try {
                  pl = Platform.upsertDTO(tipp_plt_dto, user);
                  if (pl) {
                    platform_cache[tipp_plt_dto.name] = [platform: pl, valid: true]
                    componentUpdateService.ensureCoreData(pl, tipp_plt_dto, fullsync)
                  } else {
                    log.error("Could not find/create ${tipp_plt_dto}")
                    errors.tipps.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.platform', [tipp_plt_dto.name], locale)])
                    valid = false
                  }
                }
                catch (grails.validation.ValidationException ve) {
                  log.error("ValidationException attempting to validate Platform during cross reference package", ve);
                  valid_plt = false
                  valid = false
                  def plt_errors = [
                    code   : 400,
                    message: messageService.resolveCode('crossRef.package.tipps.error.platform.validation', [tipp_plt_dto], locale),
                    baddata: tipp_plt_dto,
                    idx    : idx,
                    errors : messageService.processValidationErrors(ve.errors)
                  ]
                  errors.tipps.add(plt_errors)
                }
              }

              if (pl && (tipp_plt_dto.internalId == null)) {
                tipp_plt_dto.internalId = pl.id;
              } else {
                log.warn("No platform arising from ${tipp_plt_dto}");
              }
            }

            if ((json_tipp.package == null) && (the_pkg.id)) {
              json_tipp.package = [internalId: the_pkg.id]
            } else {
              log.warn("No package");
              errors.tipps.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.pkgId', [json_tipp.title.name], locale)])
              valid = false
            }

            //}
            // end Validation of titles and platforms

            if (valid) {
              // If valid so far, validate tipp
              log.debug("Validating tipp [${idx}]");
              // for (json_tipp in json.tipps) {
              def validation_result = TitleInstancePackagePlatform.validateDTO(json_tipp)

              if (validation_result && !validation_result.valid) {
                log.debug("TIPP Validation failed on ${json_tipp}")
                valid = false
                def tipp_errors = [
                  'code' : 400,
                  idx    : idx,
                  message: messageService.resolveCode('crossRef.package.tipps.error.preValidation', [json_tipp.title.name, validation_result.errors], locale),
                  baddata: json_tipp,
                  errors : validation_result.errors
                ]
                errors.tipps.add(tipp_errors)
              }

              if (Thread.currentThread().isInterrupted()) {
                log.debug("Job cancelling ..")
                cancelled = true
                job.endTime = new Date()
                job_result.result = "CANCELLED"
                job_result.message = "Import was cancelled before completion!"
                break;
              }
              // }
              //end validation of TIPPs
            } else {
              log.warn("Not validating tipps - failed pre validation")
            }

            if (valid) {
              log.debug("upsert tipp data")

              def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
              def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
              def status_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')
              def status_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')

              def tipp_fails = 0

              if (json.tipps?.size() > 0) {
                Package.withNewSession {
                  def pkg_new = Package.get(the_pkg.id)
                  def status_ip = RefdataCategory.lookup('Package.ListStatus', 'In Progress')

                  if (pkg_new.status == status_current && pkg_new?.listStatus != status_ip) {
                    pkg_new.listStatus = status_ip
                    pkg_new.save(flush: true)
                  }
                }
              }

              // If valid, upsert tipps
              //       for (json_tipp in json.tipps) {

              log.debug("Upsert tipp [${idx}] ${json_tipp}")
              def upserted_tipp = null

              try {
                upserted_tipp = TitleInstancePackagePlatform.upsertDTO(json_tipp, user)
                log.debug("Upserted TIPP ${upserted_tipp} with URL ${upserted_tipp?.url}")
                upserted_tipp = upserted_tipp?.merge(flush: true)

                componentUpdateService.ensureCoreData(upserted_tipp, json_tipp, fullsync)
              }
              catch (ValidationException ve) {
                log.error("ValidationException attempting to cross reference TIPP", ve);
                valid = false
                tipp_fails++
                def tipp_errors = [
                  code   : 400,
                  idx    : idx,
                  message: messageService.resolveCode('crossRef.package.tipps.error.validation', [json_tipp.title.name], locale),
                  baddata: json_tipp,
                  errors : messageService.processValidationErrors(ve.errors)
                ]
                errors.tipps.add(tipp_errors)

                if (upserted_tipp)
                  upserted_tipp.discard()
              }
              catch (Exception ge) {
                log.error("Exception attempting to cross reference TIPP:", ge)
                valid = false
                tipp_fails++
                def tipp_errors = [
                  code   : 500,
                  idx    : idx,
                  message: messageService.resolveCode('crossRef.package.tipps.error', [json_tipp.title.name], locale),
                  baddata: json_tipp
                ]
                errors.tipps.add(tipp_errors)

                if (upserted_tipp)
                  upserted_tipp.discard()
              }

              if (upserted_tipp) {
                if (existing_tipps.size() > 0 && upserted_tipp && existing_tipps.contains(upserted_tipp.id)) {
                  log.debug("Existing TIPP matched!")
                  tipps_to_delete.removeElement(upserted_tipp.id)
                }

                if (upserted_tipp && upserted_tipp?.status != status_deleted && json_tipp.status == "Deleted") {
                  upserted_tipp.deleteSoft()
                  num_removed_tipps++;
                } else if (upserted_tipp && upserted_tipp?.status != status_retired && json_tipp.status == "Retired") {
                  upserted_tipp.retire()
                  num_removed_tipps++;
                } else if (upserted_tipp && upserted_tipp.status != status_current && (!json_tipp.status || json_tipp.status == "Current")) {
                  if (upserted_tipp.isDeleted() && !fullsync) {
                    reviewRequestService.raise(
                      upserted_tipp,
                      "Matched TIPP was marked as Deleted.",
                      "Check TIPP Status.",
                      user,
                      null,
                      null,
                      RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Status Deleted')
                    )
                  }
                  upserted_tipp.setActive()
                }

                upserted_tipp.save()

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
                    RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Platform Noncurrent')
                  )
                }
              } else {
                log.debug("Could not reference TIPP")
                valid = false
                tipp_fails++
                def tipp_errors = [
                  code   : 500,
                  idx    : idx,
                  message: messageService.resolveCode('crossRef.package.tipps.error', [json_tipp.title.name], locale),
                  baddata: json_tipp
                ]
                errors.tipps.add(tipp_errors)
              }

              if (idx % 50 == 0) {
                def session = sessionFactory.currentSession
                // flush and clear the session.
                session.flush()
                session.clear()
              }

              job.setProgress(idx, json.tipps.size() + 1)
            }
          } // Ende schleife
          if (!valid) {
            job_result.result = 'ERROR'
            job_result.message = "Package was created, but ${tipp_fails} TIPPs could not be created!"
          } else {
            if (!update && existing_tipps.size() > 0) {
              tipps_to_delete.eachWithIndex { ttd, ix ->
                def to_retire = TitleInstancePackagePlatform.get(ttd)
                if (to_retire?.isCurrent()) {
                  if (fullsync) {
                    to_retire.deleteSoft()
                  } else {
                    to_retire.retire()
                  }
                  to_retire.save(failOnError: true)
                  num_removed_tipps++;
                }
                if (ix % 100 == 0) {
                  def session = sessionFactory.currentSession
                  // flush and clear the session.
                  session.flush()
                  session.clear()
                }
              }
              if (num_removed_tipps > 0) {
                def additionalInfo = [:]

                additionalInfo.vars = [the_pkg.id, num_removed_tipps]

                reviewRequestService.raise(
                  the_pkg,
                  "TIPPs retired.",
                  "An update to package ${the_pkg.id} did not contain ${num_removed_tipps} previously existing TIPPs.",
                  user,
                  null,
                  (additionalInfo as JSON).toString(),
                  RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'TIPPs Retired')
                )
              }
            }
            // log.debug("Found ${num_removed_tipps} TIPPS to delete/retire from the matched package!")
            job_result.result = 'OK'
            job_result.message = messageService.resolveCode('crossRef.package.success', [json.packageHeader.name, idx, existing_tipps.size(), num_removed_tipps], locale)

            Package.withNewSession {
              def pkg_obj = Package.get(the_pkg.id)
              if (pkg_obj.status.value != 'Deleted') {
                pkg_obj.lastUpdateComment = job_result.message
                pkg_obj.save(flush: true)
              }
              if (autoUpdate) {
                pkg_obj.source?.lastRun = new Date()
                pkg_obj.source?.save(flush: true)
              }
            }
            log.debug("Elapsed tipp processing time: ${System.currentTimeMillis() - tipp_upsert_start_time} for ${idx} records")
          }
        } else {
          job_result.result = 'ERROR'
          job_result.message = messageService.resolveCode('crossRef.package.error.tipps', [json.packageHeader.name], locale)
          log.warn("Not loading tipps - failed validation")

          if (the_pkg) {
            def additionalInfo = [:]

            if (errors.global.size() > 0 || errors.tipps.size() > 0) {
              additionalInfo.errorObjects = errors
            }

            additionalInfo.vars = [job.id]

            reviewRequestService.raise(
              the_pkg,
              "Invalid TIPPs.",
              "An update for this package failed because of invalid TIPP information (JOB ${job.id}).",
              user,
              null,
              (additionalInfo as JSON).toString(),
              RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Invalid TIPPs')
            )
          }
        }
      }
      catch (Exception e) {
        log.error("Package Crossref failed with Exception", e)
        job_result.result = "ERROR"
        job_result.message = "xRefService: Package referencing failed with exception!"
        job_result.code = 500
        errors.global.add([code: 500, message: messageService.resolveCode('crossRef.package.error.unknown', null, locale), data: json.packageHeader])
      }
      def session = sessionFactory.currentSession

      // flush and clear the session.
      session.flush()
      session.clear()
    }
    // end of Package.withNewSession

    if (!cancelled) {
      job.message(job_result.message.toString())
      job.setProgress(100)
      job.endTime = new Date()
    }

    if (errors.global.size() > 0 || errors.tipps.size() > 0) {
      job_result.errors = errors
    }
    return job_result
  }
}
