package org.gokb.rest


import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured

import java.time.Duration
import java.time.LocalDateTime

import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.User
import org.grails.web.json.JSONObject

@Transactional(readOnly = true)
class OrgController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def componentLookupService
  def orgService
  def FTUpdateService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    log.debug("Org index query: ${params}")
    Map result = [:]
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    boolean es_search = params.boolean('es') ? true : false

    params.componentType = "Org" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      LocalDateTime start_es = LocalDateTime.now()
      result = ESSearchService.find(params, null, user)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      LocalDateTime start_db = LocalDateTime.now()
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
    Map result = [:]
    Org obj = null
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
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
    Map result = [result: 'OK', params: params]
    Boolean changed = true
    JSONObject reqBody = request.JSON
    Map errors = [:]
    User user = User.get(springSecurityService.principal.id)
    Org obj = null

    if (reqBody) {
      Map lookup_result = orgService.restLookup(reqBody)

      if (lookup_result.to_create) {
        String  normname = Org.generateNormname(reqBody.name)

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
        Map jsonMap = obj.jsonMapping

        log.debug("Updating ${obj}")
        changed |= restMappingService.updateObject(obj, jsonMap, reqBody)

        if (obj.validate()) {
          log.debug("No errors.. saving")
          obj.save()

          Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames)

          if (variant_result.errors.size() > 0) {
            errors.variantNames = variant_result.errors
          }

          Map comments_result = restMappingService.updateComments(obj, reqBody.comments)

          if (comments_result.errors.size() > 0) {
            errors.comments = comments_result.errors
          }

          errors << orgService.updatelinks(obj, reqBody, changed)

          if (errors) {
            obj.expunge()
          }
        }
        else {
          errors << messageService.processValidationErrors(obj.errors, request.locale)
          obj.expunge()
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
    Map result = [result: 'OK', params: params, changed: false]
    JSONObject reqBody = request.JSON
    Map errors = [:]
    boolean remove = (request.method == 'PUT')
    User user = User.get(springSecurityService.principal.id)
    Org obj = Org.findByUuid(params.id)
    String old_name

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      old_name = obj.name

      if (obj.isEditable() && componentUpdateService.isUserCurator(obj, user)) {
        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.status = 409
          result.message = message(code: "default.update.errors.message")
          render result as JSON
          return
        }

        Map jsonMap = obj.jsonMapping

        result.changed |= restMappingService.updateObject(obj, jsonMap, reqBody)

        if (obj.validate()) {
          Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)

          result.changed |= variant_result.changed

          if (variant_result.errors.size() > 0) {
            errors.variantNames = variant_result.errors
          }

          Map comments_result = restMappingService.updateComments(obj, reqBody.comments, remove)

          if (comments_result.errors.size() > 0) {
            errors.comments = comments_result.errors
          }

          result.changed |= comments_result.changed

          errors << orgService.updateLinks(obj, reqBody, result.changed, remove)

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
        if (result.changed && reqBody.name && reqBody.name != old_name && grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false)) {
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
    Map result = ['result': 'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    Org obj = Org.findByUuid(params.id)

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if (obj && obj.isDeletable()) {
      boolean curator = (obj.curatoryGroups?.size() == 0 || user.curatoryGroups*.id.intersect(obj.curatoryGroups*.id))

      if (curator || user.isAdmin()) {
        obj.deleteSoft()
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
    Org obj = Org.findByUuid(params.id)

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if (obj && obj.isEditable()) {
      boolean curator = (obj.curatoryGroups?.size() == 0 || user.curatoryGroups*.id.intersect(obj.curatoryGroups*.id))

      if (curator || user.isAdmin()) {
        obj.retire()
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
