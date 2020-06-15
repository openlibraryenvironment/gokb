package org.gokb.rest

import grails.converters.*
import grails.core.GrailsClass
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import groovyx.net.http.URIBuilder

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

@Transactional(readOnly = true)
class PackageController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def componentLookupService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = User.get(springSecurityService.principal.id)
    def es_search = params.es ? true : false

    params.componentType = "Package" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      def start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, Package, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
    }

    result.data?.each { obj ->
      obj['_links'] << ['tipps': ['href': (base + "/packages/${obj.uuid}/tipps")]]
    }

    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def obj = null
    def base = grailsApplication.config.serverURL + "/rest"
    def is_curator = true
    User user = User.get(springSecurityService.principal.id)

    if (params.oid || params.id) {
      obj = Package.findByUuid(params.id)

      if (!obj) {
        obj = Package.get(genericOIDService.oidToId(params.id))
      }

      if (obj?.isReadable()) {
        result = restMappingService.mapObjectToJson(obj, params, user)

        // result['_currentTipps'] = obj.currentTippCount
        // result['_linkedOpenRequests'] = obj.getReviews(true,true).size()
      }
      else if (!obj) {
        result.message = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      }
      else {
        result.message = "Access to object was denied!"
        response.setStatus(403)
        result.code = 403
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
  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def save() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)

    if (reqBody) {
      log.debug("Save package ${reqBody}")
      def obj = Package.upsertDTO(reqBody, user)

      if (!obj) {
        log.debug("Could not upsert object!")
        errors.object = [[badData: reqBody, message:"Unable to save object!"]]
      }
      else if (obj.hasErrors()) {
        log.debug("Object has errors!")
        errors << messageService.processValidationErrors(obj.errors, request.locale)
        log.debug("${errors}")
      }
      else {
        def jsonMap = obj.jsonMapping

        jsonMap.ignore = [
          'lastProject',
          'status'
        ]

        jsonMap.immutable = [
          'userListVerifier',
          'listVerifiedDate',
          'listStatus'
        ]

        log.debug("Updating ${obj}")
        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        errors << updateCombos(obj, reqBody)

        log.debug("Errors: ${obj.errors}")

        if( obj.validate() ) {
          if (errors.size() == 0) {
            log.debug("No errors.. saving")
            obj.save(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            result.result = 'ERROR'
            result.message = message(code:"default.create.errors.message")
            response.setStatus(400)
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
      }
    }
    else {
      response.setStatus(400)
      errors.object = [[baddata: reqBody, message:"Unable to save package!"]]
    }

    if (errors) {
      result.result = 'ERROR'
      result.errors = errors
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='PUT')
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)
    def editable = true
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      obj.lock()

      if ( !user.hasRole('ROLE_ADMIN') && obj.curatoryGroups && obj.curatoryGroups.size() > 0 ) {
        def cur = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

        if (!cur) {
          editable = false
        }
      }
      if (editable) {

        def jsonMap = obj.jsonMapping

        jsonMap.ignore = [
          'lastProject',
          'status'
        ]

        jsonMap.immutable = [
          'userListVerifier',
          'listVerifiedDate',
          'listStatus'
        ]

        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        errors << updateCombos(obj, reqBody)

        if( obj.validate() ) {
          if (errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            result.result = 'ERROR'
            result.message = message(code:"default.update.errors.message")
            response.setStatus(400)
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          errors << messageService.processValidationErrors(obj.errors, request.locale)
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

    if(errors.size() > 0) {
      result.error = errors
    }
    render result as JSON
  }

  private def updateCombos(obj, reqBody) {
    log.debug("Updating package combos ..")
    def errors = [:]

    if (reqBody.ids || reqBody.identifiers) {
      def idmap = reqBody.ids ?: reqBody.identifiers
      restMappingService.updateIdentifiers(obj, idmap)
    }

    if (reqBody.provider) {
      def prov = null

      try {
        prov = Org.get(reqBody.provider)
      }
      catch (Exception e) {
      }

      if (prov) {
        if (!obj.hasErrors() && errors.size() == 0) {
          obj.provider = prov
        }
      }
      else {
        errors.provider = [[message: "Could not find provider Org with id ${reqBody.provider}!", baddata: reqBody.provider]]
      }
    }

    if (reqBody.nominalPlatform || reqBody.platform) {
      def plt_id = reqBody.nominalPlatform ?: reqBody.platform
      def plt = null

      try {
        plt = Platform.get(plt_id)
      }
      catch (Exception e) {
      }

      if (plt) {
        if (!obj.hasErrors() && errors.size() == 0) {
          obj.nominalPlatform = plt
        }
      }
      else {
        errors.nominalPlatform = [[message: "Could not find platform with id ${reqBody.nominalPlatform}!", baddata: plt_id]]
      }
    }

    if (reqBody.tipps) {
      reqBody.tipps.each { tipp_dto ->
        tipp_dto.pkg = obj.id
        def tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp_dto)

        if (!tipp_validation.valid) {
          if (!errors.tipps) {
            errors.tipps = []
          }

          errors.tipps << tipp_validation.errors
        }
        else {
          def upserted_tipp = TitleInstancePackagePlatform.upsertDTO(tipp_dto)

          if (upserted_tipp) {
            if (errors.size() == 0) {
              upserted_tipp = upserted_tipp?.merge(flush: true)
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
    log.debug("After update: ${obj.errors}")
    errors
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if ( obj && obj.isDeletable() ) {
      def curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

      if ( curator || user.isAdmin() ) {
        obj.deleteSoft()
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

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def retire() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if ( obj && obj.isEditable() ) {
      def curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

      if ( curator || user.isAdmin() ) {
        obj.retire()
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
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def tipps() {
    def result = [:]
    def user = User.get(springSecurityService.principal.id)
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
      response.setStatus(404)
    }

    render result as JSON
  }

  @Transactional
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
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

      if ( curator || user.isAdmin() ) {
        if (reqBody instanceof List) {
          def idx = 0

          reqBody.each { tipp ->
            def tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp)

            if (tipp_validation.valid) {
              def tipp_obj = TitleInstancePackagePlatform.upsertDTO(tipp)

              if (!tipp_obj) {
                errors.add(['code': 400, 'message': "TIPP could not be created!", baddata: tipp, idx: idx])
              }
            } else {
              errors.add(['code': 400, 'message': "TIPP information is not valid!", baddata: tipp, idx: idx, errors:tipp_validation.errors])
            }
            idx++
          }

          if (errors.size() == 0) {
            result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params, context)
          }
          else {
            result.result = 'ERROR'
            response.setStatus(400)
            result.errors = errors
            result.message = "There have been errors creating TIPPs!"
          }
        } else {
          result.result = 'ERROR'
          response.setStatus(400)
          result.message = "Missing expected array of TIPPs!"
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else if (!reqBody) {
      result.result = 'ERROR'
      response.setStatus(400)
      result.message = "Missing JSON payload!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.message = "Missing ID for connected package!"
    }

    render result as JSON
  }
}
