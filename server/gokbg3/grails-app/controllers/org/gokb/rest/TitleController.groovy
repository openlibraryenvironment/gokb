package org.gokb.rest

import grails.converters.*
import grails.core.GrailsClass
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import groovyx.net.http.URIBuilder

import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

@Transactional(readOnly = true)
class TitleController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def titleLookupService
  def componentLookupService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = User.get(springSecurityService.principal.id)
    def es_search = params.es ? true : false
    Class type = setType(params)

    params.componentType = params.type ?: 'title' // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {

      if (type) {
        def start_db = LocalDateTime.now()
        result = componentLookupService.restLookup(user, type, params)
        log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
      }
      else {
        result.errors = [
          [message: "Unrecognized type ${params.type}", code: 400, result:"ERROR"]
        ]
      }
    }

    render result as JSON
  }

  private Class setType(params) {
    Class type = TitleInstance

    if (params.type) {
      if (params.type == 'journal' || params.type == 'serial' ) {
        type = JournalInstance
      }
      else if (params.type == 'book' || params.type == 'monograph') {
        type = BookInstance
      }
      else if (params.type == 'database') {
        type = DatabaseInstance
      }
      else {
        type = null
      }
    }
    return type
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def obj = null
    def includes = params['_include'] ? params['_include'].split(',') : []
    def embeds = params['_embed'] ? params['_embed'].split(',') : []
    def is_curator = true
    Class type = setType(params)
    User user = User.get(springSecurityService.principal.id)

    if (type && (params.oid || params.id)) {
      obj = type.findByUuid(params.id)

      if (!obj) {
        obj = type.get(genericOIDService.oidToId(params.id))
      }

      if (obj?.isReadable()) {
        result = restMappingService.mapObjectToJson(obj, params, user)

        if ( (params.history && params.history == 'true') || includes.contains('history') || embeds.contains('history') ) {
          if (embeds.contains('history')) {
            result._embedded['history'] = getDirectHistory(obj, params, user)
          }
          else {
            result.history = getDirectHistory(obj, params, user)
          }
        }
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
    Class type = setType(params)
    def obj = null
    def user = User.get(springSecurityService.principal.id)
    def ids = reqBody.ids ?: reqBody.identifiers
    def base = grailsApplication.config.serverURL + "/rest"

    def publisher_name = null

    if (reqBody?.publisher) {
      if (reqBody.publisher instanceof Collection) {
        log.debug("Skipping publisher list")
      }
      else if (reqBody.publisher instanceof String) {
        publisher_name = reqBody.publisher
      }
      else {
        publisher_name = Org.get(reqBody.publisher)?.name
      }
    }

    if ( reqBody?.name?.trim() && type && type != TitleInstance ) {
      try {
        def title_lookup = titleLookupService.find(
          reqBody.name,
          publisher_name,
          ids,
          type.name
        )

        if (title_lookup.to_create) {
          obj = type.newInstance()
          obj.name = reqBody.name.trim()

          obj = restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

          if ( obj.validate() ) {
            if (errors.size() == 0 ) {
              obj.save(flush:true)

              if (title_lookup.matches.size() > 0) {
                def additionalInfo = [:]
                def combo_ids = [obj.id]

                additionalInfo.otherComponents = []

                title_lookup.matches.each { tlm ->
                  additionalInfo.otherComponents.add([oid:"${tlm.object.id}", name:"${tlm.object.name}"])
                  combo_ids.add(tlm.obect.id)
                }

                additionalInfo.cstring = combo_ids.sort().join('_')

                ReviewRequest.raise(
                  obj,
                  "New TI created.",
                  "There have been possible conflicts with other existing titles.",
                  user, 
                  null,
                  (additionalInfo as JSON).toString()
                )
              }

              if (reqBody.variantNames) {
                obj = restMappingService.updateVariantNames(obj, reqBody.variantNames)
              }

              errors << updateCombos(obj, reqBody)

              result = restMappingService.mapObjectToJson(obj, params, user)
            }
            else {
              result.message = message(code: 'default.create.errors.message')
            }
          }
          else {
            result.result = 'ERROR'
            errors << messageService.processValidationErrors(obj.errors, request.locale)
          }
        }
        else {
          title_lookup.matches.each { tlm ->
            if (!errors.ids) {
              errors.ids = []
            }

            errors.ids << [message:"There has been an identifier conflict with another title!", baddata: reqBody.ids, item: [id: tlm.object.id, name: tlm.object.name, href: (base + "/titles/" + tlm.object.id)]]
          }
        }
      }
      catch (grails.validation.ValidationException ve) {
        errors.ids = messageService.processValidationErrors(ve.errors, request.locale)
      }
    }
    else if (!type) {
      response.setStatus(400)
      result.result = 'ERROR'
      result.message = "Unrecognized title type!"
    }
    else if (type == TitleInstance) {
      response.setStatus(400)
      result.result = 'ERROR'
      result.message = "Specific title type required!"
    }
    else {
      errors.name = [[baddata: reqBody?.name, message:"Request is missing a title name!"]]
    }

    if (errors) {
      result.result = 'ERROR'
      response.setStatus(400)
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='GET')
  def getHistory() {
    def result = [:]
    def user = User.get(springSecurityService.principal.id)
    def obj = null

    if (params.id) {
      obj = TitleInstance.findByUuid(params.id)

      if (!obj) {
        obj = TitleInstance.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        result.data = getDirectHistory(obj, params, user)
      }
      else {
        result = ['result': "ERROR", 'message': "Could not resolve object", 'code': 404]
      }
    }
    else {
      result = ['result': "ERROR", 'message': "Missing ID", 'code': 400]
      log.debug("getHistory :: Missing ID!")
    }

    render result as JSON
  }

  private def getDirectHistory(obj, params, user) {
    def result = []
    def embeds = params['_embed'] ? params['_embed'].split(',') : []
    def sdf = new SimpleDateFormat("yyyy-MM-dd")

    if (obj) {
      def history = obj.titleHistory

      if (history) {
        history.each { he ->
          def mapped_event = [id: he.id, date: sdf.format(he.date), from: [], to: []]

          he.from.each { f ->
            if (embeds.contains('history')) {
              mapped_event.from << restMappingService.mapObjectToJson(f, params, user)
            }
            else {
              mapped_event.from << [name: f.name, id: f.id, uuid: f.uuid]
            }
          }

          he.to.each { t ->
            if (embeds.contains('history')) {
              mapped_event.to << restMappingService.mapObjectToJson(t, params, user)
            }
            else {
              mapped_event.to << [name: t.name, id: t.id, uuid: t.uuid]
            }
          }

          result << mapped_event
        }
      }
    }
    result
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstance.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstance.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      def editable = isUserCurator(obj,user) || user.isAdmin()

      if (editable) {
        obj = restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

        if ( reqBody.status ) {
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          RefdataValue newStatus = RefdataValue.get(reqBody.status)

          if ( status_deleted != newStatus || obj.isDeletable() ) {
            obj.status = newStatus
          }
        }

        if ( obj.validate() ) {
          log.debug("No errors.. updating combos..")

          if (reqBody.variantNames) {
            obj = restMappingService.updateVariantNames(obj, reqBody.variantNames)
          }

          errors << updateCombos(obj, reqBody)

          if ( errors.size() == 0 ) {
            obj = obj.save(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            result.message = message(code:'default.update.errors.message')
            response.setStatus(400)
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          errors.addAll(messageService.processValidationErrors(obj.errors, request.locale))
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of the title to make changes!"
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Package not found or empty request body!"
    }

    if(errors.size() > 0) {
      result.result = 'ERROR'
      result.error = errors
    }
    render result as JSON
  }

  @Transactional
  private def updateCombos(obj, reqBody, boolean remove = true) {
    log.debug("Updating title combos ..")
    def errors = [:]

    if (reqBody.ids || reqBody.identifiers) {
      def id_map = reqBody.ids ?: reqBody.identifiers
      def id_errors = restMappingService.updateIdentifiers(obj, id_map, remove)

      if (id_errors.size() > 0) {
        errors.ids = id_errors
      }
    }

    if (reqBody.publisher) {
      def pub_errors = restMappingService.updatePublisher(obj, reqBody.publisher, remove)

      if (pub_errors.size() > 0)
        errors.publisher = pub_errors
    }

    errors
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='DELETE')
  @Transactional
  def delete() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstance.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstance.get(genericOIDService.oidToId(params.id))
    }

    if ( obj && obj.isDeletable() ) {
      def curator = isUserCurator(obj, user)

      if ( curator || user.isAdmin() ) {
        obj.deleteSoft()
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing title to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "TitleInstance not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to delete this component!"
    }
    result
  }

  def isUserCurator(obj, user) {
    def curator = true

    if (KBComponent.has(obj, 'curatoryGroups')) {

      if (obj.curatoryGroups.size() > 0) {
        if (!user.curatoryGroups?.id.intersect(obj.curatoryGroups.id)) {
          curator = false
        }
      }
    }

    return curator
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def retire() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstance.findByUuid(params.id) ?: TitleInstance.get(genericOIDService.oidToId(params.id))

    if ( obj && obj.isEditable() ) {
      def curator = isUserCurator(obj, user)

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
    result
  }
}
