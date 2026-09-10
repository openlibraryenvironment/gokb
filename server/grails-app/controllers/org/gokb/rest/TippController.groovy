package org.gokb.rest

import grails.converters.*
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import java.time.Duration
import java.time.LocalDateTime

import org.gokb.cred.*
import org.grails.web.json.JSONObject

@Transactional(readOnly = true)
class TippController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def cleanupService
  def componentLookupService
  def componentUpdateService
  def concurrencyManagerService
  def tippService
  def tippUpsertService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    Map result = [:]
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    boolean es_search = params.boolean('es') ?: false

    params.componentType = "TIPP" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      LocalDateTime start_es = LocalDateTime.now()
      result = ESSearchService.find(params, null, user)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis()}")
    }
    else {
      LocalDateTime start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis()}")
    }
    if (result.result == 'ERROR') {
      response.status = (result.status ?: 500)
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    Map result = [:]
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    if (params.oid || params.id) {
      TitleInstancePackagePlatform obj = TitleInstancePackagePlatform.findByUuid(params.id)

      if (!obj) {
        obj = TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        result = restMappingService.mapObjectToJson(obj, params, user)
      }
      else {
        result.message = "Object ID could not be resolved!"
        response.status = 404
        result.code = 404
        result.result = 'ERROR'
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 400
      result.code = 400
      result.message = 'No object id supplied!'
    }

    render result as JSON
  }

  @Transactional
  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def save() {
    Map result = ['result': 'OK', 'params': params]
    JSONObject reqBody = request.JSON
    Boolean changed = true
    Map errors = [:]
    User user = User.get(springSecurityService.principal.id)
    Package pkg = null

    if (reqBody?.pkg) {
      pkg = Package.get(reqBody.pkg)
    }

    if (pkg) {
      if (componentUpdateService.isUserCurator(pkg, user)) {
        log.debug("Incoming: ${reqBody}")
        Map tipp_validation = tippService.validateDTO(reqBody)

        if (tipp_validation.valid) {
          TitleInstancePackagePlatform obj = tippUpsertService.upsertDTO(reqBody, user)

          if (obj?.validate()) {
            response.status = 201

            Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames)

            if (variant_result.errors.size() > 0) {
              errors.variantNames = variant_result.errors
            }

            Map subject_result = restMappingService.updateSubjects(obj, reqBody.subjects)

            if (subject_result.errors.size() > 0) {
              errors.subjects = subject_result.errors
            }

            errors << tippService.updateLinks(obj, reqBody, changed)

            tippService.touchPackage(obj)

            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            result.result = 'ERROR'
            result.message = "There have been validation errors while creating the object!"
            response.status = 400
            errors = messageService.processValidationErrors(obj.errors, request.locale)
            componentUpdateService.expungeComponent(obj)
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          result.message = "There have been validation errors!"
          errors = tipp_validation.errors
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

    if (errors) {
      result.result = 'ERROR'
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    Map result = [result: 'OK', params: params, changed: false]
    JSONObject reqBody = request.JSON
    boolean remove = (request.method == 'PUT')
    Map errors = [:]
    boolean set_access_end = false
    User user = User.get(springSecurityService.principal.id)
    TitleInstancePackagePlatform obj = TitleInstancePackagePlatform.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))
    }

    if (obj?.pkg && reqBody) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        Map active_pkg_jobs = concurrencyManagerService.getComponentJobs(obj.pkg.id)

        if (active_pkg_jobs?.data?.size() == 0) {
          reqBody.id = reqBody.id ?: params.id // storing the TIPP ID in the JSON data for later use in upsertDTO

          Map tipp_validation = tippService.validateDTO(reqBody)

          if (tipp_validation.valid) {
            if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
              response.status = 409
              result.message = message(code: "default.update.errors.message")
              render result as JSON
              return
            }

            if (reqBody.status?.name == 'Retired' && obj.status != RefdataValue.get(reqBody.status.id)) {
              result.changed = true
              set_access_end = true
            }

            Map jsonMap = obj.jsonMapping

            result.changed |= restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

            if (set_access_end) {
              log.debug("Setting accessEndDate for newly retired TIPP ..")
              obj.accessEndDate = new Date()
            }

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

            if (reqBody.prices != null) {
              log.debug("Updating prices ..")
              Map prices_result = restMappingService.updatePrices(obj, reqBody.prices, remove)

              result.changed |= prices_result.changed

              if (prices_result.errors.size() > 0) {
                errors.prices = prices_result.errors
              }
            }

            errors << tippService.updateLinks(obj, reqBody, result.changed, remove)

            if (obj?.validate()) {
              if (reqBody.coverageStatements != null) {
                result.changed |= tippService.updateCoverage(obj, reqBody)
              }

              if (result.changed) {
                log.debug("No errors.. saving")
                obj = obj.merge(flush: true)
                tippService.touchPackage(obj)
              }
            }
            else {
              result.result = 'ERROR'
              response.status = 400
              errors = messageService.processValidationErrors(obj.errors, request.locale)
            }

            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            result.result = 'ERROR'
            response.status = 400
            result.message = "There have been validation errors!"
            errors = tipp_validation.errors
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 409
          result.message = "Please wait for any package imports to finish before manually updating the title."
          result.messageCode = "error.update.tipp.runningImport"
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

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    Map result = ['result': 'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    TitleInstancePackagePlatform obj = TitleInstancePackagePlatform.findByUuid(params.id) ?: TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))

    if (obj?.pkg && obj.isDeletable()) {
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
    else if (!obj || !obj.pkg) {
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

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def retire() {
    Map result = ['result': 'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    TitleInstancePackagePlatform obj = TitleInstancePackagePlatform.findByUuid(params.id) ?: TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))

    if (obj?.pkg && obj.isEditable()) {
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
      result.message = "TIPP or connected Package not found!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def bulk() {
    log.debug("Bulk update: ${params} - ${request.post}")
    Map result = ['result':'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    JSONObject reqBody = request.post ? request.JSON : null

    if (reqBody?.status) {
      RefdataValue status_rdv = null

      if (reqBody.status instanceof String) {
        status_rdv = RefdataCategory.lookup('KBComponent.Status', reqBody.status)
      }
      else {
        status_rdv = RefdataValue.findByOwnerAndId(RefdataCategory.findByLabel('KBcomponent.Status'), reqBody.status)
      }

      if (status_rdv) {
        List accessible = []
        List connected_pkgs = []
        List errors = []

        for (def tippId: reqBody.items) {
          TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.findById(tippId)

          if (tipp && componentUpdateService.isUserCurator(tipp, user)) {
            if (!connected_pkgs.contains(tipp.pkg.id)) {
              connected_pkgs.add(tipp.pkg.id)
            }
            accessible.add(tipp.id)
          }
          else {
            errors.add(tippId)
          }
        }

        TitleInstancePackagePlatform.executeUpdate("update TitleInstancePackagePlatform set status = :status, lastUpdated = :date where id IN (:ids)", [status: status_rdv, ids: accessible, date: new Date()])

        connected_pkg.each {
          Package pkg = Package.get(it)

          pkg?.lastSeen = System.currentTimeMillis()
        }

        if (errors.size() > 0) {
          result.result = 'ERROR'
          result.errors = errors
          result.message = "Skipped ${errors.size()}/${reqBody.items.size()} items due to missing authorization!"
        }
      }
      else {
        result.result = 'ERROR'
        response.status = 400
        result.message = "Unable to reference status type!"
      }
    }
    else if (params['_field']?.trim() && params['_value']?.trim()) {
      Map report = componentUpdateService.bulkUpdateField(user, TitleInstancePackagePlatform, params)

      if (report.errors > 0) {
        result.result = 'ERROR'
        result.report = report
        response.status = 403
        result.message = "Unable to change ${params['_field']} for ${report.error} of ${report.total} items."
      } else {
        result.message = "Successfully changed ${params['_field']} for ${report.total} items."
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 400
      result.message = "Missing required params '_field' and '_value'"
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'GET')
  @Transactional
  def setStatus() {
    Map result = ['result': 'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    TitleInstancePackagePlatform obj = TitleInstancePackagePlatform.findByUuid(params.id) ?: TitleInstancePackagePlatform.findById(genericOIDService.oidToId(params.id))

    if (obj?.pkg && obj.isEditable()) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        RefdataValue status_rdv = null

        if (params.int('status')) {
          status_rdv = RefdataValue.get(params.int('status'))
        }
        else {
          status_rdv = RefdataCategory.lookup('KBComponent.status', params.status)
        }

        if (status_rdv) {
          obj.status = status_rdv
          obj.save(flush: true)
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          result.message = "Unable to reference status type!"
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
      result.message = "TIPP or connected Package not found!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'GET')
  def getCoverage() {
    Map result = [:]
    User user = User.get(springSecurityService.principal.id)
    String context = "/tipps/" + params.id + "/coverage"
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.findByUuid(params.id) ?: TitleInstancePackagePlatform.get(params.id)

    if (tipp) {
      params.owner = tipp.id
      result = componentLookupService.restLookup(user, TIPPCoverageStatement, params, context)
    }
    else {
      result.result = 'ERROR'
      response.status = 404
      result.message = "TIPP not found!"
    }
    render result as JSON
  }

  /*
  * Merge a TIPP into another one and if necessary reactivate the latter
  */

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def merge() {
    Map result = ['result':'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    TitleInstancePackagePlatform obj = TitleInstancePackagePlatform.findByUuid(params.id) ?: TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))
    CuratoryGroup activeGroup = params.long('activeGroup') ? CuratoryGroup.get(params.long('activeGroup')) : null
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    Boolean keepOld = params.boolean('keepOld') ?: false

    if (obj && obj.isEditable()) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        if (params.target) {
          TitleInstancePackagePlatform target = TitleInstancePackagePlatform.get(params.long('target'))

          if (target) {
            tippService.mergeDuplicate(obj, target, user, activeGroup, keepOld)
          }
          else {
            result.result = 'ERROR'
            response.status = 404
            result.message = "Unable to reference target title!"
          }
        }
        else {
          result = tippService.reactivateOldestTitleTipp(obj)

          if (result.result == 'ERROR') {
            response.status = 400
          }
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
      result.message = "Title not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }
}
