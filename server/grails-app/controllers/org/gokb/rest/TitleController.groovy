package org.gokb.rest

import grails.converters.*
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import java.time.Duration
import java.time.LocalDateTime

import org.gokb.cred.*
import org.grails.web.json.JSONObject

@Transactional(readOnly = true)
class TitleController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def titleAugmentService
  def titleLookupService
  def titleHistoryService
  def componentLookupService
  def dateFormatService
  def reviewRequestService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def getTypes() {
    Map result = ["serial","monograph","database"]

    return result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    log.debug("Index with params: ${params}")
    Map result = [:]
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    boolean es_search = params.boolean('es') ?: false
    Class type = setType(params)

    params.componentType = params.type ?: 'TitleInstance' // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      params.remove('type')
      LocalDateTime start_es = LocalDateTime.now()
      result = ESSearchService.find(params, null, user)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")

      if (result.result == 'ERROR') {
        response.status = (result.status ?: 500)
      }
    }
    else {
      if (type) {
        LocalDateTime start_db = LocalDateTime.now()
        result = componentLookupService.restLookup(user, type, params)
        log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")

        if (result.result == 'ERROR') {
          response.status = (result.status ?: 500)
        }
      }
      else {
        result.errors = [
          [message: "Unrecognized type ${params.type}", code: 400, result:"ERROR"]
        ]

        response.status = 400
      }
    }

    render result as JSON
  }

  private Class setType(params) {
    Class type = TitleInstance

    if (params.type) {
      if (params.type.toLowerCase() == 'journal' || params.type.toLowerCase() == 'serial' ) {
        type = JournalInstance
      }
      else if (params.type.toLowerCase() == 'book' || params.type.toLowerCase() == 'monograph') {
        type = BookInstance
      }
      else if (params.type.toLowerCase() == 'database') {
        type = DatabaseInstance
      }
      else if (params.type.toLowerCase() == 'other') {
        type = OtherInstance
      }
      else {
        type = null
      }
    }
    return type
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    Map result = [:]
    TitleInstance obj = null
    List includes = params['_include'] ? params['_include'].split(',') : []
    List embeds = params['_embed'] ? params['_embed'].split(',') : []
    Class type = setType(params)
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    if (type && (params.oid || params.id)) {
      obj = type.findByUuid(params.id)

      if (!obj) {
        obj = type.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        result = restMappingService.mapObjectToJson(obj, params, user)

        if ( (params.history && params.history == 'true') || includes.contains('history') || embeds.contains('history') ) {
          if (embeds.contains('history')) {
            result._embedded['history'] = titleHistoryService.getDirectHistory(obj, true, user)
          }
          else {
            result.history = titleHistoryService.getDirectHistory(obj, false, user)
          }
        }
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
  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def save() {
    Map result = ['result':'OK', 'params': params]
    JSONObject reqBody = request.JSON
    Map errors = [:]
    Class type = setType(reqBody?.type ? reqBody : params)
    TitleInstance obj = null
    boolean changed = true
    User user = User.get(springSecurityService.principal.id)
    List ids = reqBody.ids ?: reqBody.identifiers ?: []
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    boolean allow_id_conflicts = user.isAdmin() || reqBody?._checked == true

    String publisher_name = null

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
        Map title_lookup = titleLookupService.find(
          reqBody.name,
          publisher_name,
          ids,
          type.name
        )

        if (title_lookup.to_create || allow_id_conflicts) {
          obj = type.newInstance()
          obj.name = reqBody.name.trim()

          changed |= restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

          if ( obj.validate() ) {
            obj.save(flush:true)

            if (title_lookup.matches.size() > 0 && !reqBody._checked) {
              Map additionalInfo = [:]
              List linked_ids = [obj.id]
              RefdataValue rr_type = RefdataCategory.lookup("ReviewRequest.StdDesc", "Duplicate Title Info")

              additionalInfo.otherComponents = []

              title_lookup.matches.each { tlm ->
                additionalInfo.otherComponents.add([
                  oid:"${tlm.object.class.name}:${tlm.object.id}",
                  name:"${tlm.object.name}",
                  id: tlm.object.id,
                  uuid: tlm.object.uuid
                ])
                linked_ids.add(tlm.object.id)
              }

              additionalInfo.cstring = linked_ids.sort().join('_')

              reviewRequestService.raise(
                obj,
                "New TI created.",
                "There have been possible conflicts with other existing titles.",
                null,
                null,
                (additionalInfo as JSON).toString(),
                rr_type,
                componentLookupService.findCuratoryGroupOfInterest(obj)
              )
            }

            Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames)

            if (variant_result.errors.size() > 0) {
              errors.variantNames = variant_result.errors
            }

            Map subject_result = restMappingService.updateSubjects(obj, reqBody.subjects)

            if (subject_result.errors.size() > 0) {
              errors.subjects = subject_result.errors
            }

            errors << titleAugmentService.updateLinks(obj, reqBody, changed)

            result = restMappingService.mapObjectToJson(obj, params, user)
            response.status = 201
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

            errors.ids << [
              message:"There has been an identifier conflict with another title!",
              messageCode: 'error.create.title.identifierConflict',
              baddata: reqBody.ids,
              item: [
                id: tlm.object.id,
                name: tlm.object.name,
                href: (base + "/titles/" + tlm.object.id)
              ]
            ]
          }
        }
      }
      catch (grails.validation.ValidationException ve) {
        errors.ids = messageService.processValidationErrors(ve.errors, request.locale)
      }
    }
    else if (!type) {
      response.status = 400
      result.result = 'ERROR'
      result.message = "Unrecognized title type!"
    }
    else if (type == TitleInstance) {
      response.status = 400
      result.result = 'ERROR'
      result.message = "Specific title type required!"
    }
    else {
      errors.name = [
        [
          baddata: reqBody?.name,
          message:"Request is missing a title name!",
          messageCode: "validiation.missingName"
        ]
      ]
    }

    if (errors.size() > 0) {
      log.debug("Errors: ${errors}")
      result.result = 'ERROR'
      if (!obj || obj.id == null) {
        response.status = 400
      }
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='GET')
  def getHistory() {
    Map result = [:]
    boolean full_embeds = params.boolean('embeds') ?: false
    User user = User.get(springSecurityService.principal.id)
    TitleInstance obj = null

    if (params.id) {
      obj = TitleInstance.findByUuid(params.id)

      if (!obj) {
        obj = TitleInstance.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        result.data = titleHistoryService.getDirectHistory(obj, full_embeds, user)
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

  @Transactional
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def addHistory() {
    Map result = [:]
    Map errors = [:]
    JSONObject reqBody = request.JSON
    TitleInstance ti = null

    if (params.id) {
      ti = TitleInstance.findByUuid(params.id)

      if (!ti) {
        ti = TitleInstance.get(genericOIDService.oidToId(params.id))
      }
    }

    if ( ti && (reqBody.from || reqBody.to) && reqBody.date) {
      Map add_result = titleHistoryService.addNewEvent(ti, reqBody)

      if (add_result.errors) {
        errors << add_result
      }
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
      result.errors = errors
      response.status = 400
    }
    else {
      result.data = titleHistoryService.getDirectHistory(ti, [:])
    }

    render result as JSON
  }

  @Transactional
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  def updateHistory() {
    log.debug("Updating history ..")
    Map result = [:]
    Map errors = [:]
    boolean remove = (request.method == 'PUT')
    JSONObject reqBody = request.JSON
    TitleInstance ti = null

    if (params.id) {
      ti = TitleInstance.findByUuid(params.id)

      if (!ti) {
        ti = TitleInstance.get(genericOIDService.oidToId(params.id))
      }
    }

    if (ti) {
      result = titleHistoryService.restUpdate(ti, reqBody)

      if (update_result.result == 'OK') {
        result.data = titleHistoryService.getDirectHistory(ti, [:])
      }
      else {
        response.status = 400
      }
    }
    else {
      result.result = 'ERROR'
      result.message = "Unable to lookup title!"
      response.status = 404
    }

    render result as JSON
  }

  @Transactional
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='DELETE')
  def deleteHistoryEvent() {
    ComponentHistoryEvent event = ComponentHistoryEvent.get(params.id)

    if (event) {
      event.delete(flush:true, failOnError:true)
    }
    else {
      response.status = 404
    }
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    Map result = ['result':'OK', 'params': params, changed: false]
    JSONObject reqBody = request.JSON
    boolean remove = (request.method == 'PUT')
    Map errors = [:]
    User user = User.get(springSecurityService.principal.id)
    TitleInstance obj = TitleInstance.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstance.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      if (componentUpdateService.isUserCurator(obj,user)) {
        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.status = 409
          result.message = message(code: "default.update.errors.message")
          render result as JSON
          return
        }

        result.changed |= restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

        if ( obj.validate() ) {
          log.debug("No errors.. updating combos..")

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

          errors << titleAugmentService.updateLinks(obj, reqBody, result.changed, remove)

          if ( errors.size() == 0 ) {
            obj = obj.merge(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            result.message = message(code:'default.update.errors.message')
            response.status = 400
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          errors.addAll(messageService.processValidationErrors(obj.errors, request.locale))
        }
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of the title to make changes!"
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Package not found or empty request body!"
    }

    if (errors.size() > 0) {
      result.result = 'ERROR'
      result.error = errors
    }
    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    log.debug("Delete Title with id ${params.id}")
    Map result = ['result':'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    TitleInstance obj = TitleInstance.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstance.get(genericOIDService.oidToId(params.id))
    }

    if ( obj && obj.isDeletable() ) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        obj.deleteSoft()

        componentUpdateService.closeConnectedReviews(obj)
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing title to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.status = 404
      result.message = "TitleInstance not found or empty request body!"
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
    TitleInstance obj = TitleInstance.findByUuid(params.id) ?: TitleInstance.get(genericOIDService.oidToId(params.id))

    if ( obj && obj.isEditable() ) {
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

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def tipps() {
    Map result = [:]
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    log.debug("tipps :: ${params}")
    TitleInstance obj = TitleInstance.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstance.get(genericOIDService.oidToId(params.id))
    }

    log.debug("TIPPs for Title: ${obj}")

    if (obj) {
      String context = "/titles/" + params.id + "/tipps"
      boolean es_search = params.boolean('es') ? true : false

      params.remove('id')
      params.remove('uuid')
      params.remove('es')
      params.title = obj.id

      if (es_search) {
        LocalDateTime start_es = LocalDateTime.now()
        params.remove('componentType')
        params.componentType = "TIPP"

        result = ESSearchService.find(esParams, context)
        log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis()}")
      }
      else {
        LocalDateTime start_db = LocalDateTime.now()
        result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params, context)
        log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis()}")
      }
    }
    else {
      result.result = 'ERROR'
      result.message = "Title id ${params.id} could not be resolved!"
      response.status = 404
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def merge() {
    log.debug("Merging title ..")
    Map result = ['result':'OK', 'params': params]
    Map errors = [:]
    User user = User.get(springSecurityService.principal.id)
    TitleInstance obj = TitleInstance.findByUuid(params.id) ?: TitleInstance.get(genericOIDService.oidToId(params.id))

    if (obj && obj.isEditable()) {
      if (componentUpdateService.isUserCurator(obj, user)) {
        TitleInstance target = obj.class.get(params.long('target'))

        if (target) {
          errors = titleAugmentService.mergeTitles(obj, target, params)

          if (errors) {
            result.errors = errors
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 404
          result.message = "Unable to reference target title!"
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
