package org.gokb.rest

import grails.converters.*
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import java.time.Duration
import java.time.LocalDateTime

import org.gokb.cred.*
import org.grails.web.json.JSONObject

@Transactional(readOnly = true)
class PlatformController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def componentLookupService
  def platformService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    Map result = [:]
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    boolean es_search = params.boolean('es') ? true : false

    params.componentType = "Platform" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      LocalDateTime start_es = LocalDateTime.now()
      result = ESSearchService.find(params, null, user)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      LocalDateTime start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, Platform, params)
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
    Platform obj = null
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    if (params.oid || params.id) {
      obj = Platform.findByUuid(params.id)

      if (!obj) {
        obj = Platform.get(genericOIDService.oidToId(params.id))
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

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def match() {
    Map result = platformService.restLookup(params)
    Map conflicts = [:]

    result.matches.each { id, errs ->
      errs.each { e ->
        if (!conflicts[e.field])
          conflicts[e.field] = []
        conflicts[e.field] << [message: e.message, baddata: e.value, matches: id]
      }
    }
    result.result = 'OK'
    result.conflicts = conflicts
    render result as JSON
  }

  @Transactional
  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def save() {
    Map result = ['result':'OK', 'params': params, changed: true]
    JSONObject reqBody = request.JSON
    Map errors = [:]
    User user = User.get(springSecurityService.principal.id)

    if (reqBody) {
      Platform obj
      Map lookup_result = platformService.restLookup(reqBody)

      if (lookup_result.to_create) {
        String normname = Platform.generateNormname(reqBody.name)

        try {
          obj = new Platform(name: reqBody.name, normname: normname)
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
        errors.object = [[baddata: reqBody, message: "Unable to save object!"]]
        response.status = 400
      }
      else if (obj) {
        obj.save(flush:true)
        response.status = 201
        Map jsonMap = obj.jsonMapping

        log.debug("Updating ${obj}")
        result.changed |= restMappingService.updateObject(obj, jsonMap, reqBody)

        if (obj.validate()) {
          log.debug("No errors.. saving")

          Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames)

          result.changed |= variant_result.changed

          if (variant_result.errors.size() > 0) {
            errors.variantNames = variant_result.errors
          }

          errors << platformService.updateLinks(obj, reqBody, result.changed)

          obj.save(flush:true, failOnError: true)

          result = restMappingService.mapObjectToJson(obj, params, user)
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
      }
    }
    else {
      errors.object = [[badData: reqBody, message:"Unable to save platform!"]]
    }

    if (errors) {
      result.result = 'ERROR'
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    Map result = [result:'OK', params: params, changed: false]
    JSONObject reqBody = request.JSON
    Map errors = [:]
    boolean remove = (request.method == 'PUT')
    User user = User.get(springSecurityService.principal.id)
    Platform obj = Platform.findByUuid(params.id)

    if (!obj) {
      obj = Platform.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.status = 409
          result.message = message(code: "default.update.errors.message")
          render result as JSON
        }

        Map jsonMap = obj.jsonMapping

        result.changed = restMappingService.updateObject(obj, jsonMap, reqBody)

        Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)

        result.changed |= variant_result.changed

        if (variant_result.errors.size() > 0) {
          errors.variantNames = variant_result.errors
        }

        errors << platformService.updateLinks(obj, reqBody, result.changed, remove)

        if (obj.validate()) {
          if (errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            response.status = 400
            result.message = message(code:"default.update.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          errors << messageService.processValidationErrors(obj.errors, request.locale)
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
      result.message = "Platform not found or empty request body!"
    }

    if (errors.size() > 0) {
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    Map result = ['result':'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    Platform obj = Platform.findByUuid(params.id)

    if (!obj) {
      obj = Platform.get(genericOIDService.oidToId(params.id))
    }

    if (obj) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        obj.deleteSoft()
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing platform to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Platform not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User is not allowed to delete this component!"
    }
    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def retire() {
    Map result = ['result':'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    Platform obj = Platform.findByUuid(params.id) ?: genericOIDService.resolveOID(params.id)

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
}
