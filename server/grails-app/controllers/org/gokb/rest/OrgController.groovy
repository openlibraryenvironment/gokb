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
    def base = grailsApplication.config.getProperty('serverURL', String, "") + "/rest"
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

    if (result.result == 'ERROR') {
      response.status = (result.status ?: 500)
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    def result = [:]
    def obj = null
    def base = grailsApplication.config.getProperty('serverURL', String, "") + "/rest"
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
  @Secured(value = ["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def save() {
    def result = ['result': 'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)

    def obj = null
    if (reqBody) {
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

          errors << orgService.updateCombos(obj, reqBody)
        }
        else {
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
        if (obj?.id != null && grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false)) {
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
        response.status = 400
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
          response.status = 409
          result.message = message(code: "default.update.errors.message")
          render result as JSON
        }

        def jsonMap = obj.jsonMapping

        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        def variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)

        if (variant_result.errors.size() > 0) {
          errors.variantNames = variant_result.errors
        }

        errors << orgService.updateCombos(obj, reqBody, remove)

        if (obj.validate()) {
          if (errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush: true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            log.debug("Errors: ${errors}")
            response.status = 400
            result.message = message(code: "default.update.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
        if (grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false)) {
          FTUpdateService.updateSingleItem(obj)

          obj.providedPackages.each {
            FTUpdateService.updateSingleItem(it)
          }
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
      log.debug("Errors: ${errors}")
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
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
        if (grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false)) {
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
    def obj = Org.findByUuid(params.id)

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if (obj && obj.isEditable()) {
      def curator = KBComponent.has(obj, 'curatoryGroups') ? (obj.curatoryGroups?.size() == 0 || user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)) : true

      if (curator || user.isAdmin()) {
        obj.retire()
        if (grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false)) {
          FTUpdateService.updateSingleItem(obj)
        }
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing organization to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Organization not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }
}
