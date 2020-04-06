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
      if (params.type == 'journal' || params ) {
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
    def errors = []
    Class type = setType(params)
    def user = User.get(springSecurityService.principal.id)

    if ( reqBody?.name?.trim() && type && type != TitleInstance ) {
      def obj = titleLookupService.find(
        reqBody.name,
        reqBody.publisher,
        reqBody.identifiers,
        user,
        null,
        type.name
      );

      if (!obj) {
        log.debug("Could not upsert object!")
        errors = [badData: reqBody, message:"Unable to save object!"]
      }
      else if (obj.hasErrors()) {
        log.debug("Object has errors!")
        errors = messageService.processValidationErrors(obj.errors, request.locale)
        log.debug("${errors}")
      }
      else {
        def jsonMap = obj.jsonMapping

        log.debug("Updating ${obj}")
        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        updateCombos(obj, reqBody)

        if (!obj.hasErrors()) {
          result = restMappingService.mapObjectToJson(obj, params, user)
        }
        else {
          result.result = 'ERROR'
          response.setStatus(422)
          errors.addAll(messsageService.processValidationErrors(obj.errors, request.locale))
        }
      }
    }
    else if (!type) {
      errors = [badData: reqBody, message:"Unrecognized title type!"]
    }
    else if (type == TitleInstance) {
      errors = [badData: reqBody, message:"Specific title type required!"]
    }
    else {
      errors = [badData: reqBody, message:"Unable to save title!"]
    }

    if (errors) {
      result.result = 'ERROR'
      result.error = errors
    }

    result
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='GET')
  def getHistory() {
    def result = null
    def user = User.get(springSecurityService.principal.id)
    def full = params.full ? true : false
    def obj = null

    if (params.id) {
      obj = TitleInstance.findByUuid(params.id)

      if (!obj) {
        obj = TitleInstance.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        def history = obj.titleHistory

        if (history) {
          result = []

          history.each { he ->
            def mapped_event = [id: he.id, date: he.date, from: [], to: []]

            he.from.each { f ->
              if (full) {
                mapped_event.from << restMappingService.mapObjectToJson(f, params, user)
              }
              else {
                mapped_event.from << [name: f.name, id: f.id, uuid: f.uuid]
              }
            }

            he.to.each { t ->
              if (full) {
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
    }
    else {
      log.debug("getHistory :: Missing ID!")
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='PUT')
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = []
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstance.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstance.get(genericOIDService.oidToId(params.id))
    }
    def editable = obj.isEditable()

    if (obj && reqBody) {
      obj.lock()

      if ( editable && KBComponent.has(obj, 'curatoryGroups') && obj.curatoryGroups?.size() > 0 ) {
        def cur = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

        if (!cur) {
          editable = false
        }
      }

      if (editable) {

        def jsonMap = obj.jsonMapping

        restMappingService.updateObject(obj, jsonMap, reqBody)

        if ( reqBody.status ) {
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          RefdataValue newStatus = RefdataValue.get(reqBody.status)

          if ( status_deleted != RefdataValue.get(reqBody.status) || obj.isDeletable() ) {
            obj.status = newStatus
          }
        }

        if( obj.validate() ) {
          if(errors.size() == 0) {
            obj.save(flush:true)
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(422)
          errors.addAll(messsageService.processValidationErrors(pkg.errors, request.locale))
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

  private void updateCombos(obj, reqBody) {
    log.debug("Updating title combos ..")

    if (reqBody.publisher) {
      if (reqBody.publisher instanceof Collection) {
        Set new_pubs = []

        reqBody.publisher.each { pub ->
          def pub_obj = null

          if (pub instanceof String) {
            pub_obj = Org.findByNameIlike(pub)
          }
          else {
            pub_obj = Org.get(pub)
          }

          if (pub_obj) {
            new_pubs << pub_obj
          }
          else {
            obj.errors.reject(
              'component.addToList.denied.label',
              ['publisher'] as Object[],
              '[Could not process list of items for property {0}]'
            )
            obj.errors.rejectValue(
              'publisher',
              'component.addToList.denied.label'
            )
          }
        }

        if (!obj.hasErrors()) {
          new_pubs.each { c ->
            if (!obj.publisher.contains(c)) {
              log.debug("Adding new publisher ${c}..")
              obj.publisher.add(c)
            }
            else {
              log.debug("Existing publisher ${c}..")
            }
          }
          obj.publisher.retainAll(new_pubs)
        }
        log.debug("New cgs: ${obj.publisher}")
      }
      else {
        def prov = null

        try {
          prov = Org.get(reqBody.publisher)
        }
        catch (Exception e) {
        }

        if (prov) {
          if (!obj.publisher.contains(prov)) {
            obj.changePublisher(prov)
          }
        }
        else {
          obj.errors.reject(
            'default.not.found.message',
            ['Org', reqBody.publisher] as Object[],
            '[{0} not found with id {1}!]'
          )
          obj.errors.rejectValue(
            'publisher',
            'default.not.found.message'
          )
        }
      }
    }
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
      def curator = KBComponent.has(obj, 'curatoryGroups') ? user.curatoryGroups?.id.intersect(pkg.curatoryGroups?.id) : true

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

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='GET')
  @Transactional
  def retire() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstance.findByUuid(params.id) ?: genericOIDService.resolveOID(params.id)
    def curator = KBComponent.has(obj, 'curatoryGroups') ? user.curatoryGroups?.id.intersect(pkg.curatoryGroups?.id) : true

    if ( obj && obj.isEditable() ) {
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
