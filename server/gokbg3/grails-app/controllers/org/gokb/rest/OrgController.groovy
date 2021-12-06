package org.gokb.rest


import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.User

import java.time.Duration
import java.time.LocalDateTime

@Transactional(readOnly = true)
class OrgController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def componentLookupService
  def componentUpdateService
  def orgService
  def platformService
  def FTUpdateService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    log.debug("Org index query: ${params}")
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    def es_search = params.es ? true : false

    params.componentType = "Org" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params, null, user)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      def start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, Org, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
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
      obj = Org.findByUuid(params.id)

      if (!obj) {
        obj = genericOIDService.resolveOID(params.id)
      }

      if (!obj && params.long('id')) {
        obj = Org.get(params.long('id'))
      }

      if (obj) {
        result = restMappingService.mapObjectToJson(obj, params, user)
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
  @Secured(value = ["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def save() {
    def result = ['result': 'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)

    if (reqBody) {
      def obj = null
      def lookup_result = orgService.restLookup(reqBody)

      if (lookup_result.to_create) {
        def normname = Org.generateNormname(reqBody.name)
        try {
          obj = new Org(name: reqBody.name, normname: normname)
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

            errors[e.field] << [message: e.message, baddata: e.value, matches: id]
          }
        }
      }

      if (errors.size() > 0) {
        log.debug("Object has validation errors!")
        obj.discard()
        response.status = 400
      }
      else if (lookup_result.to_create && !obj) {
        log.debug("Could not upsert object!")
        response.status = 400
        errors.object = [[baddata: reqBody, message: "Unable to save object!"]]
      }
      else if (obj) {
        obj.save(flush:true)
        response.status = 201
        def jsonMap = obj.jsonMapping

        log.debug("Updating ${obj}")
        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        if (obj.validate()) {
          log.debug("No errors.. saving")
          obj.save()

          def variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames)

          if (variant_result.errors.size() > 0) {
            errors.variantNames = variant_result.errors
          }

          errors << updateCombos(obj, reqBody)
        }
        else {
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
        if (obj?.id != null && grailsApplication.config.gokb.ftupdate_enabled == true) {
          FTUpdateService.updateSingleItem(obj)
        }

        result = restMappingService.mapObjectToJson(obj, params, user)
      }
    }
    else {
      errors.object = [[badData: reqBody, message: "Unable to save organization!"]]
    }

    if (errors.size() > 0) {
      result.result = 'ERROR'
      if (!obj || obj.id == null) {
        response.setStatus(400)
      }
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def result = ['result': 'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def remove = (request.method == 'PUT')
    def user = User.get(springSecurityService.principal.id)
    def obj = Org.findByUuid(params.id)

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      obj.lock()
      def editable = obj.isEditable()

      if (editable && obj.respondsTo('curatoryGroups') && obj.curatoryGroups?.size() > 0) {
        def cur = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

        if (!cur) {
          editable = false
        }
      }

      if (editable) {

        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.setStatus(409)
          result.message = message(code: "default.update.errors.message")
          render result as JSON
        }

        def jsonMap = obj.jsonMapping

        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        def variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)

        if (variant_result.errors.size() > 0) {
          errors.variantNames = variant_result.errors
        }

        errors << updateCombos(obj, reqBody, remove)

        if (obj.validate()) {
          if (errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush: true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            log.debug("Errors: ${errors}")
            response.setStatus(400)
            result.message = message(code: "default.update.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
        if (grailsApplication.config.gokb.ftupdate_enabled == true) {
          FTUpdateService.updateSingleItem(obj)
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Package not found or empty request body!"
    }

    if (errors.size() > 0) {
      log.debug("Errors: ${errors}")
      result.error = errors
    }

    render result as JSON
  }

  private def updateCombos(obj, reqBody, boolean remove = true) {
    log.debug("Updating org combos ..")
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

    if (reqBody.providedPlatforms instanceof Collection) {
      def plts = reqBody.providedPlatforms

      def plts_result = orgService.updatePlatforms(obj, plts, remove)

      changed |= plts_result.changed

      if (plts_result.errors.size() > 0) {
        errors.providedPlatforms = plts_result.errors
      }
    }

    if (reqBody.curatoryGroups instanceof Collection) {
      def cg_result = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      changed |= cg_result.changed

      if (cg_result.errors.size() > 0) {
        errors['curatoryGroups'] = cg_result.errors
      }
    }

    if (reqBody.offices instanceof Collection) {
      def office_result = orgService.updateOffices(obj, reqBody.offices, remove)
      changed |= office_result.changed

      if (office_result.errors.size() > 0) {
        errors['offices'] = office_result.errors
      }
    }

    if (changed) {
      obj.lastSeen = System.currentTimeMillis()
    }
    log.debug("After update: ${obj}")
    errors
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'DELETE')
  @Transactional
  def delete() {
    def result = ['result': 'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Org.findByUuid(params.id)

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if (obj && obj.isDeletable()) {
      def curator = KBComponent.has(obj, 'curatoryGroups') ? user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id) : true

      if (curator || user.isAdmin()) {
        obj.deleteSoft()
        if (grailsApplication.config.gokb.ftupdate_enabled == true) {
          FTUpdateService.updateSingleItem(obj)
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Package not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to delete this component!"
    }
    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def retire() {
    def result = ['result': 'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Org.findByUuid(params.id)

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if (obj && obj.isEditable()) {
      def curator = KBComponent.has(obj, 'curatoryGroups') ? (obj.curatoryGroups?.size() == 0 || user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)) : true

      if (curator || user.isAdmin()) {
        obj.retire()
        if (grailsApplication.config.gokb.ftupdate_enabled == true) {
          FTUpdateService.updateSingleItem(obj)
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing organization to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Organization not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }
}
