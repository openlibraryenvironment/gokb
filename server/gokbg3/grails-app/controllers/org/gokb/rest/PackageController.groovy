package org.gokb.rest

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.*
import grails.core.GrailsClass
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import groovyx.net.http.URIBuilder

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import org.apache.commons.lang.RandomStringUtils
import org.gokb.GOKbTextUtils
import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
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
  def FTUpdateService
  def titleLookupService
  def TSVIngestionService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    def es_search = params.es ? true : false

    params.componentType = "Package" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params, null, user)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      def start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, Package, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
    }

    if (result.result == 'ERROR') {
      response.status = (result.status ?: 500)
    }
    else {
      result.data?.each { obj ->
        obj['_links'] << ['tipps': ['href': (base + "/packages/${obj.uuid}/tipps")]]
        def countTippsParams = [:]
        countTippsParams.componentType = "TIPP"
        countTippsParams.tippPackage = obj.uuid
        countTippsParams.status = "Current"
        countTippsParams.max = 0
        obj['_tippCount'] = ESSearchService.find(countTippsParams)?._pagination?.total ?: 0
      }
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    def result = [:]
    def obj = null
    def base = grailsApplication.config.serverURL + "/rest"
    def is_curator = true
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
    def result = ['result': 'OK', 'params': params]
    def reqBody = request.JSON
    def request_locale = RequestContextUtils.getLocale(request)
    UpdateToken update_token = null
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)

    if (reqBody) {
      log.debug("Save package ${reqBody}")
      def pkg_validation = Package.validateDTO(reqBody, request_locale)
      def obj = null

      if (pkg_validation.valid) {
        def lookup_result = packageService.restLookup(reqBody)

        if (lookup_result.to_create) {
          def normname = Package.generateNormname(reqBody.name)
          try {
            obj = new Package(name: reqBody.name, normname: normname)
          }
          catch (grails.validation.ValidationException ve) {
            errors << messageService.processValidationErrors(ve.errors, request_locale)
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
        }
        else if (lookup_result.to_create && !obj) {
          log.debug("Could not upsert object!")
          errors.object = [[baddata: reqBody, message: "Unable to save object!"]]
        }
        else if (obj) {
          obj.save(flush:true)
          def jsonMap = obj.jsonMapping

          jsonMap.immutable = [
              'userListVerifier',
              'listVerifiedDate',
              'listStatus'
          ]

          log.debug("Updating ${obj}")
          obj = restMappingService.updateObject(obj, jsonMap, reqBody)

          if (obj.validate()) {
            if (errors.size() == 0) {
              log.debug("No errors.. saving")
              obj.save()

              def variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames)

              if (variant_result.errors.size() > 0) {
                errors.variantNames = variant_result.errors
              }

              String charset = (('a'..'z') + ('0'..'9')).join()
              def updateToken = RandomStringUtils.random(255, charset.toCharArray())
              update_token = new UpdateToken(pkg: obj, updateUser: user, value: updateToken).save(flush: true)

              errors << updateCombos(obj, reqBody, false, user)

              if (errors.size() == 0) {
                log.debug("No errors: ${errors}")
                obj.save(flush: true)
                response.status = 201
                result = restMappingService.mapObjectToJson(obj, params, user)

                if (update_token) {
                  result.updateToken = update_token.value
                }
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
          if (obj?.id != null && grailsApplication.config.gokb.ftupdate_enabled == true) {
            FTUpdateService.updateSingleItem(obj)
          }
        }
      }
      else {
        errors << pkg_validation.errors
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
    def result = ['result': 'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def remove = (request.method == 'PUT')
    def generateToken = params.generateToken ? params.boolean('generateToken') : (reqBody.generateToken ? true : false)
    UpdateToken update_token = null
    def request_locale = RequestContextUtils.getLocale(request)
    def user = User.get(springSecurityService.principal.id)
    def editable = true
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      if (!user.hasRole('ROLE_ADMIN') && obj.curatoryGroups && obj.curatoryGroups.size() > 0) {
        def cur = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

        if (!cur) {
          editable = false
        }
      }
      if (editable) {
        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.status = 409
          result.message = message(code: "default.update.errors.message")
          render result as JSON
        }

        def jsonMap = obj.jsonMapping

        jsonMap.immutable = [
            'userListVerifier',
            'listVerifiedDate',
            'listStatus'
        ]

        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        def variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)

        if (variant_result.errors.size() > 0) {
          errors.variantNames = variant_result.errors
        }

        errors << updateCombos(obj, reqBody, remove, user)

        if (obj.validate()) {
          if (generateToken) {
            String charset = (('a'..'z') + ('0'..'9')).join()
            def updateToken = RandomStringUtils.random(255, charset.toCharArray())

            if (obj.updateToken) {
              def currentToken = obj.updateToken
              obj.updateToken = null
              currentToken.delete(flush: true)
            }

            update_token = new UpdateToken(pkg: obj, updateUser: user, value: updateToken).save(flush: true)
          }

          if (errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush: true)
            result = restMappingService.mapObjectToJson(obj, params, user)

            if (update_token) {
              result.updateToken = update_token.value
            }
          }
          else {
            response.status = 400
            result.message = message(code: "default.update.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          errors << messageService.processValidationErrors(obj.errors, request_locale)
        }
        if (grailsApplication.config.gokb.ftupdate_enabled == true) {
          FTUpdateService.updateSingleItem(obj)
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

  private def updateCombos(obj, reqBody, boolean remove = true, user) {
    log.debug("Updating package combos ..")
    def errors = [:]
    def changed = false

    if (reqBody.ids instanceof Collection || reqBody.identifiers instanceof Collection) {
      def id_list = reqBody.ids instanceof Collection ? reqBody.ids : reqBody.identifiers

      def id_result = restMappingService.updateIdentifiers(obj, id_list, remove)

      changed |= id_result.changed

      if (id_result.errors.size() > 0) {
        errors.ids = id_result.errors
      }
    }

    if (reqBody.curatoryGroups) {
      def cg_result = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      changed |= cg_result.changed

      if (cg_result.errors.size() > 0) {
        errors['curatoryGroups'] = cg_result.errors
      }
    }

    if (reqBody.listStatus) {
      def new_val = null

      if (reqBody.listStatus instanceof String) {
        new_val = RefdataCategory.lookup('Package.ListStatus', reqBody.listStatus)
      }
      else if (reqBody.listStatus instanceof Integer) {
        def rdv = RefdataValue.get(reqBody.listStatus)

        if (rdv.owner == RefdataCategory.findByLabel('Package.ListStatus')) {
          new_val = rdv
        }

        if (new_val && new_val != obj.listStatus) {
          if (new_val.value == 'Checked') {
            RefdataValue review_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
            RefdataValue combo_tipps = RefdataCategory.lookup("Combo.Type", "Package.Tipps")
            def open_reviews = ReviewRequest.executeQuery("from ReviewRequest where componentToReview in (select k from KBComponent as k where exists (select 1 from Combo where fromComponent.id = :pkg and type = :ct and toComponent = k) or k.id = :pkg) and status = :so", [pkg: obj.id, so: review_open, ct: combo_tipps],[max: 1])

            log.debug("Open Reviews: ${open_reviews}")

            if (open_reviews.size() == 0) {
              obj.listStatus = new_val
              obj.listVerifiedDate = new Date()
            }
            else {
              errors['listStatus'] = [[message: 'All connected requests for review must be closed before the package can be marked as checked.', code: 409, messageCode: 'component.package.listStatus.error.openReviews']]
            }
          }
          else {
            obj.listStatus = new_val
          }
        }

        if (new_val && new_val.value == 'Checked') {
          obj.listVerifiedDate = new Date()
        }
      }
    }

    if (reqBody.provider instanceof Integer) {
      def prov = null

      try {
        prov = Org.get(reqBody.provider)
      }
      catch (Exception e) {
      }

      if (prov && prov != obj.provider) {
        obj.provider = prov
      }
      else if (!prov) {
        errors.provider = [[message: "Could not find provider Org with id ${reqBody.provider}!", baddata: reqBody.provider]]
      }
    }
    else if (reqBody.provider == null) {
      obj.provider = null
      changed = true
    }

    if (reqBody.nominalPlatform != null || reqBody.platform != null) {
      def plt_id = reqBody.nominalPlatform ?: reqBody.platform
      def plt = null

      try {
        plt = Platform.get(plt_id)
      }
      catch (Exception e) {
      }

      if (plt && plt != obj.nominalPlatform) {
        obj.nominalPlatform = plt
      }
      else if (!plt) {
        errors.nominalPlatform = [[message: "Could not find platform with id ${reqBody.nominalPlatform}!", baddata: plt_id]]
      }
    }
    else if (reqBody.nominalPlatform == null || reqBody.platform == null) {
      obj.nominalPlatform = null
      changed = true
    }

    if (reqBody.tipps) {
      reqBody.tipps.each { tipp_dto ->
        tipp_dto.pkg = obj.id
        def ti_errors = []

        if (tipp_dto.title && tipp_dto.title instanceof Map) {
          if (!tipp_dto.title.id) {
            try {
              def ti = TitleInstance.upsertDTO(titleLookupService, tipp_dto.title, user)

              if (ti) {
                tipp_dto.title = ti.id
              }
            }
            catch (grails.validation.ValidationException ve) {
              log.error("ValidationException attempting to cross reference title", ve);
              valid_ti = false
              def validation_errors = [
                  message: "Title ${tipp_dto.title?.name} failed validation!",
                  baddata: tipp_dto.title,
                  errors : messageService.processValidationErrors(ve.errors)
              ]
              ti_errors.add(validation_errors)
            }
            catch (org.gokb.exceptions.MultipleComponentsMatchedException mcme) {
              log.debug("Handling MultipleComponentsMatchedException")
              valid_ti = false
              ti_errors.add([baddata: tipp_dto.title, 'message': "Unable to uniquely match title ${tipp_dto.title?.name}, check duplicates for titles ${mcme.matched_ids}!", conflicts: mcme.matched_ids])
            }
          }
        }

        def tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp_dto, RequestContextUtils.getLocale(request))

        if (ti_errors?.size > 0 || !tipp_validation.valid) {
          if (!errors.tipps) {
            errors.tipps = []
          }

          if (ti_errors?.size > 0) {
            errors.tipps << ti_errors
          }

          if (!tipp_validation.valid) {
            errors.tipps << tipp_validation.errors
          }
        }
        else {
          def upserted_tipp = TitleInstancePackagePlatform.upsertDTO(tipp_dto, user)

          if (upserted_tipp) {
            if (errors.size() == 0) {
              log.debug("Ensuring TIPP core data ${tipp_dto}")
              componentUpdateService.ensureCoreData(upserted_tipp, tipp_dto, true, user)

              def tipp_status = null

              if (tipp_dto.status instanceof String) {
                tipp_status = RefdataCategory.lookup('KBComponent.Status', tipp_dto.status)
              }
              else if (tipp_dto.status instanceof Integer) {
                def id_rdv = RefdataValue.get(tipp_dto.status)
                tipp_status = id_rdv.owner.label == 'KBComponent.Status' ? id_rdv : null
              }
              else if (tipp_dto.status instanceof Map) {
                def id_rdv = RefdataValue.get(tipp_dto.status.id)
                tipp_status = id_rdv.owner.label == 'KBComponent.Status' ? id_rdv : null
              }

              if (tipp_status && upserted_tipp.status != tipp_status) {
                upserted_tipp.status = tipp_status
              }

              upserted_tipp = upserted_tipp?.save(flush: true)
              changed = true
            }
          }
          else {
            if (!errors.tipps) {
              errors.tipps = []
            }

            error.tipps << [[message: "Unable to reference TIPP!", baddata: tipp_dto]]
          }
        }
      }
    }

    if (changed) {
      obj.lastSeen = System.currentTimeMillis()
      obj.save()
    }
    errors
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    def result = ['result': 'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && obj.isDeletable()) {
      def curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

      if (curator || user.isAdmin()) {
        obj.deleteSoft()
        if (grailsApplication.config.gokb.ftupdate_enabled == true) {
          FTUpdateService.updateSingleItem(obj)
        }
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
    def result = ['result': 'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && obj.isEditable()) {
      def curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

      if (curator || user.isAdmin()) {
        obj.retire()
        FTUpdateService.updateSingleItem(obj)
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
    def result = [:]
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    log.debug("tipps :: ${params}")
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    log.debug("TIPPs for Package: ${obj}")

    if (obj) {
      def context = "/packages/" + params.id + "/tipps"
      def base = grailsApplication.config.serverURL + "/rest"
      def es_search = params.es ? true : false

      params.remove('id')
      params.remove('uuid')
      params.remove('es')
      params.pkg = obj.id

      def esParams = new HashMap(params)
      esParams.remove('componentType')
      esParams.componentType = "TIPP" // Tells ESSearchService what to look for

      log.debug("New ES params: ${esParams}")
      log.debug("New DB params: ${params}")

      if (es_search) {
        def start_es = LocalDateTime.now()
        result = ESSearchService.find(esParams, context)
        log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
      }
      else {
        def start_db = LocalDateTime.now()
        result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params, context)
        log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
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
    def result = [:]
    int max = params.limit ? params.int('limit') : 10
    int offset = params.offset ? params.int('offset') : 0

    log.debug("jobs :: ${params}")
    def obj = Package.findByUuid(params.id)?:Package.get(params.id)

    log.debug("Jobs for Package: ${obj}")

    if (obj) {
      if (params.boolean('archived') == true || params.boolean('combined') == true) {
        result.data = []
        def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.linkedItemId = ?", [obj.id])[0]
        def jobs = JobResult.executeQuery("from JobResult as jr where jr.linkedItemId = ? order by jr.startTime desc", [obj.id], [max: max, offset: offset])

        if (params.boolean('combined') == true) {
          def active_jobs = concurrencyManagerService.getComponentJobs(obj.id, max, offset, false)

          hqlTotal += active_jobs._pagination.total

          if (offset == 0) {
            result.data = active_jobs.data
          }
        }

        jobs.each { j ->
          result.data << [
            uuid: j.uuid,
            description: j.description,
            type: j.type ? [id: j.type.id, name: j.type.value, value: j.type.value] : null,
            linkedItem: [id: obj.id, type: obj.niceName, uuid: obj.uuid, name: obj.name],
            startTime: j.startTime,
            endTime: j.endTime,
            status: j.statusText
          ]
        }

        result['_pagination'] = [
          offset: offset,
          limit: max,
          total: hqlTotal
        ]
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
    def result = [:]
    def errors = []
    def user = User.get(springSecurityService.principal.id)
    def context = "/packages/" + params.id + "/tipps"
    log.debug("addTipps :: ${params}")
    def obj = Package.findByUuid(params.id)
    def reqBody = request.JSON

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {

      def curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

      params.pkg = params.id

      if (curator || user.isAdmin()) {
        if (reqBody instanceof List) {
          def idx = 0

          reqBody.each { tipp ->
            def tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp, RequestContextUtils.getLocale(request))

            if (tipp_validation.valid) {
              def tipp_obj = TitleInstancePackagePlatform.upsertDTO(tipp, user)

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
    log.debug("Form post")
    def result = ['result': 'OK']
    Package pkg = Package.get(params.id)
    def user = User.get(springSecurityService.principal.id)

    if (pkg && componentUpdateService.isUserCurator(pkg, user)) {
      DataFile datafile = null
      def upload_mime_type = request.getFile("submissionFile")?.contentType
      def upload_filename = request.getFile("submissionFile")?.getOriginalFilename()
      def deposit_token = java.util.UUID.randomUUID().toString()
      def temp_file = TSVIngestionService.copyUploadedFile(request.getFile("submissionFile"), deposit_token)
      CuratoryGroup active_group = params.int('activeGroup') ? CuratoryGroup.get(params.int('activeGroup')) : null
      IdentifierNamespace title_ns = params.int('titleIdNamespace') ? IdentifierNamespace.get(params.int('titleIdNamespace')) : null
      Boolean add_only = params.boolean('addOnly') ?: false
      Boolean dry_run = params.boolean('dryRun') ?: false
      Boolean skip_invalid = params.boolean('skipInvalid') ?: false
      def info = TSVIngestionService.analyseFile(temp_file)
      def platform_url = pkg.nominalPlatform?.primaryUrl ?: null
      def pkg_source = pkg.source
      Boolean async = params.async ? params.boolean('async') : true

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
                                          uploadMimeType:upload_mime_type).save()

          datafile.fileData = temp_file.getBytes()
          datafile.save(failOnError:true,flush:true)
          log.debug("Saved new datafile : ${datafile.id} -- ${datafile.uploadName}")
        }
      }

      if (datafile) {
        Job background_job = concurrencyManagerService.createJob { Job job ->
          TSVIngestionService.updatePackage(pkg,
                                            datafile,
                                            title_ns,
                                            async,
                                            add_only,
                                            user,
                                            active_group,
                                            dry_run,
                                            skip_invalid,
                                            job)
        }

        if (active_group) {
          background_job.groupId = active_group.id
        }
        background_job.ownerId = user.id
        background_job.description = "KBART REST ingest (${pkg.name})".toString()
        background_job.type = RefdataCategory.lookup('Job.Type', (dry_run ? 'KBARTIngestDryRun' : 'KBARTIngest'))
        background_job.linkedItem = [name: pkg.name, type: "Package", id: pkg.id, uuid: pkg.uuid]
        background_job.message("Starting upsert for Package ${pkg.name}".toString())
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

  @Transactional
  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  def triggerSourceUpdate() {
    def result = ['result': 'OK']
    def active_group = params.int('activeGroup') ? CuratoryGroup.get(params.int('activeGroup')) : null
    Boolean async = params.boolean('async') ?: true
    Boolean dry_run = params.boolean('dryRun') ?: false
    Package pkg = Package.get(params.id)
    def user = User.get(springSecurityService.principal.id)

    if (pkg && componentUpdateService.isUserCurator(pkg, user)) {
      Job background_job = concurrencyManagerService.createJob { Job job ->
        packageSourceUpdateService.updateFromSource(pkg, user, job, active_group, dry_run)
      }

      background_job.groupId = active_group.id
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
