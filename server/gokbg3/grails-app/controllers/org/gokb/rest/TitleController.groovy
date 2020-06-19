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
  def titleHistoryService
  def componentLookupService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def getTypes() {
    def result = ["serial","monograph","database"]

    return result as JSON
  }

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
    def user = User.get(springSecurityService.principal.id)
    def ids = reqBody.ids ?: reqBody.identifiers

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
      def obj = titleLookupService.find(
        reqBody.name,
        publisher_name,
        ids,
        user,
        null,
        type.name
      )

      if (!obj) {
        log.debug("Could not upsert object!")
        errors.object = [[badData: reqBody, message:"Unable to save object!"]]
      }
      else if (obj.hasErrors()) {
        log.debug("Object has errors!")
        errors = messageService.processValidationErrors(obj.errors, request.locale)
      }
      else {
        obj = restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

        if ( obj.validate() ) {
          if (errors.size() == 0 ) {
            obj.save(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            result.message = message(code: 'default.create.errors.message')
          }
        }
        else {
          result.result = 'ERROR'
          errors.addAll(messageService.processValidationErrors(obj.errors, request.locale))
        }
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

  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='GET')
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

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def addHistory() {
    def result = [:]
    def errors = [:]
    def reqBody = request.JSON
    def ti = null

    if (params.id) {
      ti = TitleInstance.findByUuid(params.id)

      if (!ti) {
        ti = TitleInstance.get(genericOIDService.oidToId(params.id))
      }
    }

    if ( ti && (reqBody.from || reqBody.to) && reqBody.date) {
      errors << titleHistoryService.addNewEvent(ti, reqBody)
    }
    else if (!ti) {
      result.result = "ERROR"
      response.status = 404
      result.message = "Unable to look up title with ID ${params.id}!"
    }
    else if (!reqBody.date) {
      result.result = "ERROR"
      response.status = 400
      result.message = "Missing event date!"
    }
    else {
      result.result = "ERROR"
      response.status = 400
      result.message = "Missing history partner!"
    }

    if (errors.size() > 0) {
      result.result = "ERROR"
      response.status = 400
    }
    else {
      result.data = getDirectHistory(ti, [:])
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='PUT')
  def setHistory() {

  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='DELETE')
  def deleteHistoryEvent() {
    def event = ComponentHistoryEvent.get(params.id)

    if (event) {
      titleHistoryService.deleteEvent(event)
    }
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

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='PUT')
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
      def editable = obj.isEditable()

      if ( editable && KBComponent.has(obj, 'curatoryGroups') && obj.curatoryGroups?.size() > 0 ) {
        def cur = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

        if (!cur) {
          editable = false
        }
      }

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

          errors << updateCombos(obj, reqBody)

          if ( errors.size() == 0 ) {
            obj = obj.merge(flush:true)
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
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
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

  private def updateCombos(obj, reqBody) {
    log.debug("Updating title combos ..")
    def errors = [:]

    def combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    def combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    def combo_id_type = RefdataCategory.lookup(Combo.RD_TYPE, "KBComponent.Ids")
    def combo_expired = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_EXPIRED)

    if (reqBody.ids || reqBody.identifiers) {
      def id_combos = []
      id_combos.addAll( obj.getCombosByPropertyName('ids') )
      def new_ids = restMappingService.updateIdentifiers(obj, reqBody.ids ?: reqBody.identifiers)

      new_ids.each { i ->

        def dupe = Combo.executeQuery("from Combo where type = ? and fromComponent = ? and toComponent = ?",[combo_id_type, obj, i])

        if (dupe.size() == 0) {
          log.debug("No combo found, adding ID ..")
          def new_combo = new Combo(fromComponent: obj, toComponent: i, status: combo_active, type: combo_id_type).save(flush:true)
        }
        else if (dupe.size() == 1 ) {
          if (dupe[0].status == combo_deleted) {
            log.debug("Matched ID combo was marked as deleted!")
          }
          else {
            log.debug("Not adding duplicate ..")
          }
        }
        else {
          if (!errors.ids) {
            errors.ids = []
          }

          errors.ids << [message: "There seem to be duplicate links for an identifier against this title!", baddata: i]
          log.error("Multiple ID combos for ${obj} -- ${i}!")
        }
      }

      Iterator items = id_combos.iterator();
      Object element;
      while (items.hasNext()) {
        element = items.next();
        if (!new_ids.contains(element.toComponent)) {
          // Remove.
          element.status = combo_deleted
        }
      }
    }

    if (reqBody.publisher) {
      def publisher_combos = []
      publisher_combos.addAll( obj.getCombosByPropertyName('publisher') )
      String propName = obj.isComboReverse('publisher') ? 'fromComponent' : 'toComponent'
      String tiPropName = obj.isComboReverse('publisher') ? 'toComponent' : 'fromComponent'
      def pubs_to_add = []

      if (reqBody.publisher instanceof Collection) {
        reqBody.publisher.each { pub ->
          if (!pubs_to_add.collect { it.id == pub}) {
            pubs_to_add << Org.get(pub)
          }
          else {
            log.warn("Duplicate for incoming publisher ${pub}!")
          }
        }
      }
      else {
          if (!pubs_to_add.collect { it.id == reqBody.publisher}) {
            pubs_to_add << Org.get(reqBody.publisher)
          }
          else {
            log.warn("Duplicate for incoming publisher ${reqBody.publisher}!")
          }
      }

      pubs_to_add.each { publisher ->
        boolean found = false
        for ( int i=0; !found && i<publisher_combos.size(); i++) {
          Combo pc = publisher_combos[i]
          def idMatch = pc."${propName}".id == publisher.id

          if (idMatch) {
            found = true
          }
        }

        if (!found) {

          log.debug("Adding new combo for publisher ${publisher} (${propName}) to title ${obj} (${tiPropName})")

          RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, obj.getComboTypeValue('publisher'))

          def combo = null

          if (propName == "toComponent") {
            combo = new Combo(
              type            : (type),
              status          : combo_active,
              toComponent     : publisher,
              fromComponent   : obj
            )
          } else {
            combo = new Combo(
              type            : (type),
              status          : combo_active,
              fromComponent   : publisher,
              toComponent     : obj
            )
          }

          if (combo) {
            combo.save(flush:true, failOnError:true)

            log.debug "Added publisher ${publisher.name} for '${obj.name}'" +
              (combo.startDate ? ' from ' + combo.startDate : '') +
              (combo.endDate ? ' to ' + combo.endDate : '')
          } else {
            log.error("Could not create publisher Combo..")
            if (!errors.publisher) {
              errors.publisher = []
            }

            errors.publisher << [message: "Unable to add publisher ${publisher.name} to title!", baddata: publisher.id]
          }

        } else {
          log.debug "Publisher ${publisher.name} already set against '${obj.name}'"
        }
      }

      Iterator items = publisher_combos.iterator();
      Object element;
      while (items.hasNext()) {
        element = items.next();
        if (!pubs_to_add.contains(element.toComponent) && !pubs_to_add.contains(element.fromComponent)) {
          // Remove.
          element.delete()
        }
      }
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
