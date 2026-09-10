package org.gokb.rest

import com.k_int.ConcurrencyManagerService.Job

import grails.converters.*
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import java.time.Duration
import java.time.LocalDateTime

import org.gokb.cred.*
import org.grails.web.json.JSONObject
import org.springframework.web.servlet.support.RequestContextUtils

@Transactional(readOnly = true)
class PackageController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def packageService
  def packageSourceUpdateService
  def componentLookupService
  def componentUpdateService
  def concurrencyManagerService
  def TSVIngestionService
  def packageUpdateService
  def tippUpsertService
  def adminAlertingService
  def jobResultService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    Map result = [:]
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    boolean es_search = params.boolean('es') ? true : false

    params.componentType = "Package" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      LocalDateTime start_es = LocalDateTime.now()
      result = ESSearchService.find(params, null, user)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      LocalDateTime start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, Package, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
    }

    if (result.result == 'ERROR') {
      response.status = (result.status ?: 500)
    }
    else {
      result.data?.each { obj ->
        obj['_links'] << ['tipps': ['href': (base + "/packages/${obj.uuid}/tipps")]]
        Map countTippsParams = [:]
        countTippsParams.componentType = "TIPP"
        countTippsParams.tippPackage = obj.uuid
        countTippsParams.status = "Current"
        countTippsParams.max = 0

        if (grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false)) {
          obj['_tippCount'] = ESSearchService.find(countTippsParams)?._pagination?.total ?: 0
        }
        else {
          obj['_tippCount'] = obj.currentTippCount
        }
      }
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    Map result = [:]
    Package obj = null
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    if (params.oid || params.id) {
      obj = Package.findByUuid(params.id)

      if (!obj) {
        obj = Package.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        result = restMappingService.mapObjectToJson(obj, params, user)

        result['_tippCount'] = obj.currentTippCount
        // result['_linkedOpenRequests'] = obj.getReviews(true,true).size()
      }
      else {
        result.message = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.code = 400
      result.message = 'No object id supplied!'
    }

    render result as JSON
  }

  @Transactional
  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def save() {
    Map result = [result: 'OK', params: params]
    JSONObject reqBody = request.JSON
    Locale request_locale = RequestContextUtils.getLocale(request)
    Map errors = [:]
    User user = User.get(springSecurityService.principal.id)
    boolean editable = true
    boolean changed = true

    if (reqBody) {
      if (!user.isAdmin()) {
        if (reqBody.curatoryGroups) {
          editable = user.curatoryGroups*.id.intersect(reqBody.curatoryGroups*.id).size() > 0
        }
        else if (reqBody.activeGroup) {
          editable = user.curatoryGroups*.id.contains(reqBody.activeGroup.id)
        }
      }

      if (editable) {
        log.debug("Save package ${reqBody}")
        Map pkg_validation = packageService.restValidate(reqBody, request_locale, true)
        Package obj = null

        if (pkg_validation.valid) {
          Map lookup_result = packageService.restLookup(reqBody)

          if (lookup_result.to_create) {
            String normname = Package.generateNormname(reqBody.name)

            try {
              obj = new Package(name: reqBody.name, normname: normname)
            }
            catch (grails.validation.ValidationException ve) {
              errors = messageService.processValidationErrors(ve.errors, request_locale)
            }
            log.debug("New Object ${obj}")
          }
          else {
            lookup_result.matches.each { id, errs ->
              errs.each { e ->
                if (!errors[e.field])
                  errors[e.field] = []

                errors[e.field] << [matches: id] + e
              }
            }
          }

          if (errors.size() > 0) {
            log.debug("Object has validation errors!")
            obj?.discard()
          }
          else if (lookup_result.to_create && !obj) {
            log.debug("Could not upsert object!")
            errors.object = [[baddata: reqBody, message: "Unable to save object!"]]
          }
          else if (obj) {
            obj.save(flush:true)
            Map jsonMap = obj.jsonMapping

            jsonMap.immutable = [
                'userListVerifier',
                'listVerifiedDate',
                'listStatus'
            ]

            log.debug("Updating ${obj}")
            changed = restMappingService.updateObject(obj, jsonMap, reqBody)

            if (obj.validate()) {
              if (errors.size() == 0) {
                log.debug("No errors.. saving")
                obj.save()

                Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames)

                if (variant_result.errors.size() > 0) {
                  errors.variantNames = variant_result.errors
                }

                Map subject_result = restMappingService.updateSubjects(obj, reqBody.subjects)

                if (subject_result.errors.size() > 0) {
                  errors.subjects = subject_result.errors
                }

                if ((!reqBody.curatoryGroups || reqBody.curatoryGroups?.size() == 0) && reqBody.activeGroup) {
                  reqBody.curatoryGroups = [reqBody.activeGroup]
                }

                errors << packageUpdateService.updateLinks(obj, reqBody, changed, false, user)

                if (errors.size() == 0) {
                  log.debug("No errors: ${errors}")
                  obj.save(flush: true)
                  response.status = 201
                  result = restMappingService.mapObjectToJson(obj, params, user)
                }
                else {
                  result.result = 'ERROR'
                  log.debug("There were errors setting combo props!")
                  obj.discard()
                  result.error = errors
                }
              }
              else {
                result.result = 'ERROR'
                obj.discard()
                result.message = message(code: "default.create.errors.message")
                response.status = 400
              }
            }
            else {
              result.result = 'ERROR'
              obj.discard()
              response.status = 400
              errors << messageService.processValidationErrors(obj.errors, request_locale)
            }
          }
        }
        else {
          errors = pkg_validation.errors
        }
      }
      else {
        response.status = 403
        result.result = 'ERROR'
        result.message = "User is not authorized to create packages for this curatory group!"
      }
    }
    else {
      response.status = 400
      errors.object = [[baddata: reqBody, message: "Unable to save package!"]]
    }

    if (errors.size() > 0) {
      result.result = 'ERROR'
      result.errors = errors

      if (response.status == 200) {
        response.status = 400
      }
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    Map result = ['result': 'OK', 'params': params, changed: false]
    JSONObject reqBody = request.JSON
    Map errors = [:]
    boolean remove = (request.method == 'PUT')
    Locale request_locale = RequestContextUtils.getLocale(request)
    User user = User.get(springSecurityService.principal.id)
    Package obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.status = 409
          result.message = message(code: "default.update.errors.message")
          render result as JSON
          return
        }

        Map jsonMap = obj.jsonMapping

        jsonMap.immutable = [
            'userListVerifier',
            'listVerifiedDate',
            'listStatus'
        ]

        Map validate_result = packageService.restValidate(obj, reqBody, request_locale, remove)

        if (validate_result.valid == false) {
          response.status = 400
          result.result = 'ERROR'
          result.errors = validate_result.errors

          render result as JSON
          return
        }

        result.changed |= restMappingService.updateObject(obj, jsonMap, reqBody)

        if (obj.validate()) {
          log.debug("No errors.. saving")
          obj = obj.merge(flush: true)

          Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)

          result.changed |= variant_result.changed

          if (variant_result.errors.size() > 0) {
            errors.variantNames = variant_result.errors
          }

          Map subject_result = restMappingService.updateSubjects(obj, reqBody.subjects, remove)

          result.changed |= subject_result.changed

          if (subject_result.errors.size() > 0) {
            errors.subjects = subject_result.errors
          }

          errors << packageUpdateService.updateLinks(obj, reqBody, result.changed, remove, user)

          if (errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush: true, failOnError: true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            obj.discard()
            response.status = 400
            result.message = message(code: "default.update.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          errors << messageService.processValidationErrors(obj.errors, request_locale)
          obj.discard()
        }
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Package not found or empty request body!"
    }

    if (errors.size() > 0) {
      result.error = errors
    }
    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    Map result = ['result': 'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    Package obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && obj.isDeletable()) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        obj.deleteSoft()

        componentUpdateService.closeConnectedReviews(obj)
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Package not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User is not allowed to delete this component!"
    }
    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def retire() {
    Map result = ['result': 'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    Package obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        obj.retire()
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Package not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def tipps() {
    Map result = [:]
    User user = null
    Package obj = Package.findByUuid(params.id)

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    log.debug("tipps :: ${params}")

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    log.debug("TIPPs for Package: ${obj}")

    if (obj) {
      String context = "/packages/" + params.id + "/tipps"
      String base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
      boolean es_search = params.boolean('es') ?: false

      params.remove('id')
      params.remove('uuid')
      params.remove('es')
      params.pkg = obj.id

      if (es_search) {
        LocalDateTime start_es = LocalDateTime.now()
        params.remove('componentType')
        params.componentType = 'TIPP'

        result = ESSearchService.find(esParams, context)
        log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis()}")
      }
      else {
        LocalDateTime start_db = LocalDateTime.now()
        result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params, context)
        log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis()}")
      }
    }
    else {
      result.result = 'ERROR'
      result.message = "Package id ${params.id} could not be resolved!"
      response.status = 404
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def jobs() {
    Map result = [:]
    int max = params.limit ? params.int('limit') : 10
    int offset = params.offset ? params.int('offset') : 0

    log.debug("jobs :: ${params}")
    Package obj = Package.findByUuid(params.id) ?: Package.get(params.id)

    log.debug("Jobs for Package: ${obj}")

    if (obj) {
      if (params.boolean('archived') == true || params.boolean('combined') == true) {
        params.linkedItem = obj.id
        result.data = []

        Map finished_results = jobResultService.fetchJobs(params, max, offset)

        if (params.boolean('combined') == true) {
          Map active_jobs = concurrencyManagerService.getComponentJobs(obj.id, max, offset, false)

          int combined_total += finished_results._pagination.total + active_jobs._pagination.total

          if (offset == 0) {
            result.data = active_jobs.data + finished_results.data
          }
          else {
            result.data = finished_results.data
          }

          result['_pagination'] = [
            offset: offset,
            limit: max,
            total: combined_total
          ]
        }
        else {
          result = finished_results
        }
      }
      else {
        result = concurrencyManagerService.getComponentJobs(obj.id, max, offset, showFinished)
      }
    }
    render result as JSON
  }

  @Transactional
  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def addTipps() {
    log.debug("addTipps :: ${params}")
    Map result = [:]
    List errors = []
    User user = User.get(springSecurityService.principal.id)
    String context = "/packages/" + params.id + "/tipps"
    Package obj = Package.findByUuid(params.id)
    JSONObject reqBody = request.JSON

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        if (reqBody instanceof List) {
          params.pkg = params.id
          int idx = 0

          reqBody.each { tipp ->
            Map tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp, RequestContextUtils.getLocale(request))

            if (tipp_validation.valid) {
              TitleInstancePackagePlatform tipp_obj = tippUpsertService.upsertDTO(tipp, user)

              if (!tipp_obj) {
                errors.add(['code': 400, 'message': "TIPP could not be created!", baddata: tipp, idx: idx])
              }
            }
            else {
              errors.add(['code': 400, 'message': "TIPP information is not valid!", baddata: tipp, idx: idx, errors: tipp_validation.errors])
            }
            idx++
          }

          if (errors.size() == 0) {
            result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params, context)
          }
          else {
            result.result = 'ERROR'
            response.status = 400
            result.errors = errors
            result.message = "There have been errors creating TIPPs!"
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          result.message = "Missing expected array of TIPPs!"
        }
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else if (!reqBody) {
      result.result = 'ERROR'
      response.status = 400
      result.message = "Missing JSON payload!"
    }
    else {
      result.result = 'ERROR'
      response.status = 400
      result.message = "Missing ID for connected package!"
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def ingestKbart() {
    Map result = ['result': 'OK', errors: [:]]
    Package pkg = Package.findByUuid(params.id)

    if (!pkg) {
      pkg = Package.findById(genericOIDService.oidToId(params.id))
    }

    if (!pkg) {
      response.status = 404
      result.result = 'ERROR'
      result.message = "Unable to reference package!"

      render result as JSON
      return
    }

    Map pkgInfo = [:]
    User user = User.get(springSecurityService.principal.id)
    Long active_group_id = null
    Long title_ns_id = null
    Long title_ns_serial_id = null
    Long title_ns_mono_id = null
    Source source = pkg.source

    if (params.activeGroup) {
      CuratoryGroup active_group

      if (params.long('activeGroup')) {
        active_group = CuratoryGroup.get(params.long('activeGroup'))
      }

      if (!active_group) {
        result.result = 'ERROR'
        result.errors.activeGroup = [[message: 'Unable to reference active curatory group!', baddata: params.activeGroup]]
      }
      else {
        active_group_id = active_group.id
      }
    }

    if (params.titleIdNamespace) {
      IdentifierNamespace title_ns

      if (params.long('titleIdNamespace')) {
        title_ns = IdentifierNamespace.get(params.long('titleIdNamespace'))
      }

      if (!title_ns) {
        result.result = 'ERROR'
        result.errors.titleIdNamespace = [[message: 'Unable to reference title_id namespace!', baddata: params.titleIdNamespace]]
      }
      else {
        title_ns_id = title_ns.id
      }
    }

    if (params.titleIdSerial) {
      IdentifierNamespace title_ns

      if (params.long('titleIdSerial')) {
        title_ns = IdentifierNamespace.get(params.long('titleIdSerial'))
      }

      if (!title_ns) {
        result.result = 'ERROR'
        result.errors.titleIdSerial = [[message: "Unable to reference active serial title_id namespace!", baddata: params.titleIdSerial]]
      }
      else {
        title_ns_serial_id = title_ns.id
      }
    }

    if (params.titleIdMonograph) {
      IdentifierNamespace title_ns

      if (params.long('titleIdMonograph')) {
        title_ns = IdentifierNamespace.get(params.long('titleIdMonograph'))
      }

      if (!title_ns) {
        result.result = 'ERROR'
        result.errors.titleIdMonograph = [[message: "Unable to reference active monograph title_id namespace!", baddata: params.titleIdMonograph]]
      }
      else {
        title_ns_mono_id = title_ns.id
      }
    }

    if (result.result == 'ERROR') {
      result.message = "Failed to reference objects for one or more parameters!"
      response.status = 400
    }
    else if (componentUpdateService.isUserCurator(pkg, user)) {
      pkgInfo = [
        name: pkg.name,
        type: "Package",
        id: pkg.id,
        uuid: pkg.uuid
      ]
      DataFile datafile = null
      String upload_mime_type = request.getFile("submissionFile")?.contentType
      String upload_filename = request.getFile("submissionFile")?.getOriginalFilename()
      String deposit_token = java.util.UUID.randomUUID().toString()
      File temp_file = TSVIngestionService.handleTempFile(deposit_token, request.getFile("submissionFile"))

      Boolean add_only = params.boolean('addOnly') ?: false
      Boolean dry_run = params.boolean('dryRun') ?: false
      Boolean skip_invalid = params.boolean('skipInvalid') ?: false
      Boolean delete_missing = params.boolean('deleteMissing') ?: false
      Boolean async = params.async ? params.boolean('async') : true
      Long max_file_length = 20971520L

      Map info = TSVIngestionService.analyseFile(temp_file)

      if (!source || source?.ignoreSizeLimit || user.isAdmin() || info.filesize <= max_file_length) {
        log.debug("Got file with md5 ${info.md5sumHex}.. lookup by md5")
        datafile = DataFile.findByMd5(info.md5sumHex)

        if (!datafile) {
          log.debug("Create new datafile")
          DataFile.withNewTransaction {
            datafile = new DataFile(
              guid:deposit_token,
              md5:info.md5sumHex,
              uploadName:upload_filename,
              name:upload_filename,
              filesize:info.filesize,
              encoding:info.encoding,
              uploadMimeType:upload_mime_type
            ).save()

            datafile.fileData = temp_file.getBytes()
            datafile.save(failOnError:true,flush:true)
            log.debug("Saved new datafile : ${datafile.id} -- ${datafile.uploadName}")
          }
        }

        if (datafile) {
          Job background_job = concurrencyManagerService.createJob { Job job ->
            TSVIngestionService.updatePackage(pkg.id,
              datafile.id,
              title_ns_id,
              async,
              add_only,
              user.id,
              active_group_id,
              dry_run,
              skip_invalid,
              delete_missing,
              job,
              title_ns_serial_id,
              title_ns_mono_id
            )
          }

          if (active_group_id) {
            background_job.groupId = active_group_id
          }
          background_job.ownerId = user.id
          background_job.description = "KBART REST ingest (${pkgInfo.name})".toString()
          background_job.type = RefdataCategory.lookup('Job.Type', (dry_run ? 'KBARTIngestDryRun' : 'KBARTIngest'))
          background_job.linkedItem = pkgInfo
          background_job.message("Starting upsert for Package ${pkgInfo.name}".toString())
          background_job.startOrQueue()
          background_job.startTime = new Date()

          if (async) {
            result.jobId = background_job.uuid
          }
          else {
            result.job_result = background_job.get()
          }
        }
        else {
          log.debug("Unable to reference DataFile!")
          result.result = 'ERROR'
          response.status = 500
          result.message = "There has been an error processing the KBART file!"
        }
      }
      else if (source) {
        log.warn("KBART import failed for ${pkg} due to filesize restrictions!")
        result.result = 'ERROR'
        response.status = 413
        result.message = "The provided file is too big!"
        result.messageCode = "kbart.errors.fileSize"

        adminAlertingService.sendSizeLimitAlert(pkg)
      }
    }
    else if (pkg?.id) {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User must belong to at least one curatory group of an existing package to make changes!"
    }
    else {
      result.result = 'ERROR'
      response.status = 500
      result.message = "KBART import failed, please try again!"
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  def triggerSourceUpdate() {
    Map result = ['result': 'OK']
    User user = User.get(springSecurityService.principal.id)
    CuratoryGroup active_group = params.long('activeGroup') ? CuratoryGroup.get(params.long('activeGroup')) : null
    boolean async = params.boolean('async') ?: true
    boolean dry_run = params.boolean('dryRun') ?: false
    boolean restrictSize = (params.boolean('ignoreFileSize') && user.isAdmin) ? false : true
    Package pkg = Package.get(params.id)

    if (pkg && componentUpdateService.isUserCurator(pkg, user)) {
      Job background_job = concurrencyManagerService.createJob { Job job ->
        packageSourceUpdateService.updateFromSource(pkg.id, user.id, job, active_group.id, dry_run, restrictSize)
      }

      background_job.groupId = active_group?.id ?: (componentLookupService.findCuratoryGroupOfInterest(pkg, user)?.id ?: null)
      background_job.ownerId = user?.id ?: null
      background_job.description = "KBART Source ingest (${pkg.name})".toString()
      background_job.type = RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
      background_job.linkedItem = [name: pkg.name, type: "Package", id: pkg.id, uuid: pkg.uuid]
      background_job.message("Starting upsert for Package ${pkg.name}".toString())
      background_job.startOrQueue()
      background_job.startTime = new Date()

      if (async) {
        result.jobId = background_job.uuid
      }
      else {
        def job_result = background_job.get()
        result = job_result
      }
    }
    else if (!pkg) {
      response.status = 404
      result.result = 'ERROR'
      result.message = "Unable to reference package!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User must belong to at least one curatory group of an existing package to make changes!"
    }

    render result as JSON
  }

  private def cleanUpGorm(session) {
    log.debug("Clean up GORM")

    // flush and clear the session.
    session.flush()
    session.clear()
  }
}
