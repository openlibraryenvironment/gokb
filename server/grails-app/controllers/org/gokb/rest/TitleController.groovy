package org.gokb.rest

import grails.converters.*
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import java.time.Duration
import java.time.LocalDateTime

import org.gokb.cred.*

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
    def result = ["serial","monograph","database"]

    return result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    log.debug("Index with params: ${params}")
    def result = [:]
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    def es_search = params.es ? true : false
    Class type = setType(params)

    params.componentType = params.type ?: 'TitleInstance' // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      params.remove('type')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params, null, user)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")

      if (result.result == 'ERROR') {
        response.status = (result.status ?: 500)
      }
    }
    else {
      if (type) {
        def start_db = LocalDateTime.now()
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
    def result = [:]
    def obj = null
    def includes = params['_include'] ? params['_include'].split(',') : []
    def embeds = params['_embed'] ? params['_embed'].split(',') : []
    def is_curator = true
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
            result._embedded['history'] = getDirectHistory(obj, params, user)
          }
          else {
            result.history = getDirectHistory(obj, params, user)
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
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    Class type = setType(reqBody?.type ? reqBody : params)
    def obj = null
    def user = User.get(springSecurityService.principal.id)
    def ids = reqBody.ids ?: reqBody.identifiers
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"

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

        if (title_lookup.to_create || reqBody._checked == true) {
          obj = type.newInstance()
          obj.name = reqBody.name.trim()

          obj = restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

          if ( obj.validate() ) {
            obj.save(flush:true)

            if (title_lookup.matches.size() > 0 && !reqBody._checked) {
              def additionalInfo = [:]
              def combo_ids = [obj.id]
              RefdataValue rr_type = RefdataCategory.lookup("ReviewRequest.StdDesc", "Duplicate Title Info")

              additionalInfo.otherComponents = []

              title_lookup.matches.each { tlm ->
                additionalInfo.otherComponents.add([
                  oid:"${tlm.object.class.name}:${tlm.object.id}",
                  name:"${tlm.object.name}",
                  id: tlm.object.id,
                  uuid: tlm.object.uuid
                ])
                combo_ids.add(tlm.object.id)
              }

              additionalInfo.cstring = combo_ids.sort().join('_')

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

            def variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames)

            if (variant_result.errors.size() > 0) {
              errors.variantNames = variant_result.errors
            }

            def subject_result = restMappingService.updateSubjects(obj, reqBody.subjects)

            if (subject_result.errors.size() > 0) {
              errors.subjects = subject_result.errors
            }

            errors << updateCombos(obj, reqBody)

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

  @Transactional
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
      def add_result = titleHistoryService.addNewEvent(ti, reqBody)

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
      result.data = getDirectHistory(ti, [:])
    }

    render result as JSON
  }

  @Transactional
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  def updateHistory() {
    log.debug("Updating history ..")
    def result = [:]
    def errors = [:]
    def remove = (request.method == 'PUT')
    def reqBody = request.JSON
    def ti = null

    if (params.id) {
      ti = TitleInstance.findByUuid(params.id)

      if (!ti) {
        ti = TitleInstance.get(genericOIDService.oidToId(params.id))
      }
    }

    if (ti) {
      def current_history = ti.titleHistory
      def events = []

      log.debug("Current history: ${current_history}")

      if (reqBody instanceof List) {
        log.debug("Got list of events")

        reqBody.each { event ->
          log.debug("Event ${event}")
          def parts = [from: [], to: []]

          if (event.id) {
            def matched_event = current_history.find { it.id == event.id }

            if (event.date && matched_event && event.date != dateFormatService.formatDate(matched_event.date)) {
              def he_obj = ComponentHistoryEvent.get(matched_event.id)

              if (he_obj) {
                def parsed_date = null

                try {
                  parsed_date = dateFormatService.parseDate(event.date)
                }
                catch (Exception e){
                  log.debug("Illegal date value ${event.date}!")

                  if (!errors.date)
                    errors.date = []

                  errors.date << [message: "Unable to parse event date!", baddate: event]
                }

                if (errors.size() == 0 && parsed_date) {
                  he_obj.eventDate = parsed_date
                  log.debug("Updated date of existing event!")
                }

                events.add(he_obj.id)
              }
              else {
                log.debug("Unable to lookup event by id!")
                if (!errors.id)
                  errors.id = []

                errors.id << [message: "Unable to lookup event for ID ${event.id}", baddata: event]
              }
            }
            else if (!matched_event) {
              log.debug("Matched event is not connected to this title!")
              if (!errors.id)
                errors.id = []

              errors.id << [message: "Existing event with ID ${event.id} is not connected to this title!", baddata: event]
            }
            else {
              events.add(matched_event.id)
            }
          }
          else {
            def lookedUpIds = []

            if (event.from instanceof List) {
              event.from.each { entry ->
                def cti = null

                if (entry instanceof Integer) {
                  if (entry == ti.id) {
                    cti = ti
                  }
                  else {
                    cti = TitleInstance.get(entry)
                  }
                }
                else if (entry instanceof Map) {
                  if (entry.id == ti.id) {
                    cti = ti
                  }
                  else {
                    cti = TitleInstance.get(entry.id)
                  }
                }

                if (cti) {
                  if (!lookedUpIds.contains(cti.id)) {
                    lookedUpIds.add(cti.id)
                  }
                  else {
                    if (!errors.from)
                      errors.from = []

                    errors.from << [message: "Multiple instances of title ${cti.id} in event participants!", baddata: entry, code: 404]
                  }

                  if (cti.id != ti.id) {
                    def addResult = ensureSingleParticipant(ti, 'from', cti, event.date)

                    if (addResult.errors) {
                      errors << addResult.errors
                    }
                    else {
                      log.debug("New event ${addResult}")
                      events.add(addResult.id)
                    }
                  }
                }
                else {
                  if (!errors.from)
                    errors.from = []

                  errors.from << [message: "Unable to lookup title for ${entry}", baddata: entry, code: 404]
                }
              }
            }

            if (event.to instanceof List) {
              event.to.each { entry ->
                def cti = null

                if (entry instanceof Integer) {
                  if (entry == ti.id) {
                    cti = ti
                  }
                  else {
                    cti = TitleInstance.get(entry)
                  }
                }
                else if (entry instanceof Map) {
                  if (entry.id == ti.id) {
                    cti = ti
                  }
                  else {
                    cti = TitleInstance.get(entry.id)
                  }
                }

                if (cti) {
                  if (!lookedUpIds.contains(cti.id)) {
                    lookedUpIds.add(cti.id)
                  }
                  else {
                    if (!errors.to)
                      errors.to = []

                    errors.to << [message: "Multiple instances of title ${cti.id} in event participants!", baddata: entry, code: 404]
                  }

                  if (cti.id != ti.id) {
                    def addResult = ensureSingleParticipant(ti, 'to', cti, event.date)

                    if (addResult.errors) {
                      errors << addResult.errors
                    }
                    else {
                      log.debug("New event ${addResult}")
                      events.add(addResult.id)
                    }
                  }
                }
                else {
                  if (!errors.to)
                    errors.to = []

                  errors.to << [message: "Unable to lookup title for ${entry}", baddata: entry, code: 404]
                }
              }
            }

            if (event.from instanceof Integer && event.from != ti.id) {
              def cti = TitleInstance.get(event.from)

              if (cti) {
                if (cti.id != ti.id) {
                  def addResult = ensureSingleParticipant(ti, 'from', cti, event.date)

                  if (addResult.errors) {
                    errors << addResult.errors
                  }
                  else {
                    log.debug("New event ${addResult}")
                    events.add(addResult.id)
                  }
                }
              }
              else {
                if (!errors.id)
                  errors.from = []

                errors.id << [message: "Unable to lookup title for ID ${from_entry.id}", baddata: entry, code: 404]
              }
            } else if (event.to instanceof Integer && event.to != ti.id) {
              def cti = TitleInstance.get(event.from)

              if (cti) {
                if (cti.id != ti.id) {
                  def addResult = ensureSingleParticipant(ti, 'from', cti, event.date)

                  if (addResult.errors) {
                    errors << addResult.errors
                  }
                  else {
                    log.debug("New event ${addResult}")
                    events.add(addResult.id)
                  }
                }
              }
              else {
                if (!errors.id)
                  errors.from = []

                errors.id << [message: "Unable to lookup title for ID ${from_entry.id}", baddata: entry, code: 404]
              }
            }
          }
        }

        if (errors.size() > 0) {
          result.result = 'ERROR'
          result.message = "There were errors updating the title history!"
          response.status = 400
          result.errors = errors
        }
        else if (remove) {
          current_history.each { ce ->
            if (!events.find { it == ce.id }) {
              def event = ComponentHistoryEvent.get(ce.id)
              event.delete(flush:true, failOnError:true)
            }
          }
        }
        result.data = getDirectHistory(ti, [:])
      }
      else {
        log.debug("Found illegal payload format!")
        result.result = 'ERROR'
        result.message = "Unexpected payload format, expected array of events!"
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
  private ensureSingleParticipant(ti, type, participant, date) {
    def result = [:]
    def dupe_hql = '''select che from ComponentHistoryEvent as che where exists
    (select pf.id from ComponentHistoryEventParticipant as pf where pf.participant = :from and pf.participantRole = 'in' and pf.event = che)
    AND exists (select pt.id from ComponentHistoryEventParticipant as pt where pt.participant = :to and pt.participantRole = 'out' and pt.event = che)'''

    def pars = [:]

    if (type == 'from') {
      pars = [from: participant, to: ti]
    } else {
      pars = [from: ti, to: participant]
    }

    def dupe = ComponentHistoryEvent.executeQuery(dupe_hql, pars)

    if (!dupe) {
      def req = [date: date]

      if (type == 'from') {
        req.from = [participant.id]
      } else {
        req.to = [participant.id]
      }

      def add_result = titleHistoryService.addNewEvent(ti, req)

      if (add_result.errors) {
        result.errors = add_result.errors
      }
      else {
        result.id = add_result.new_events[0]
      }
    }
    else {
      if (dupe.size() == 1) {
        ComponentHistoryEvent existingEvent = dupe[0]
        result.id = existingEvent.id

        if (date && (!existingEvent.eventDate || dateFormatService.formatDate(existingEvent.eventDate) != date)) {
          existingEvent.eventDate = dateFormatService.parseDate(date)
        }
      }
      else {
        log.error("Got multiple history events between two titles ${dupe}!")
      }
    }

    result
  }

  @Transactional
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='DELETE')
  def deleteHistoryEvent() {
    def event = ComponentHistoryEvent.get(params.id)

    if (event) {
      event.delete(flush:true, failOnError:true)
    }
    else {
      response.status = 404
    }
  }

  private def getDirectHistory(obj, params, User user = null) {
    def result = []
    def embeds = params['_embed'] ? params['_embed'].split(',') : []

    if (obj) {
      def history = obj.titleHistory

      if (history) {
        history.each { he ->
          def mapped_event = [id: he.id, date: he.date ? dateFormatService.formatDate(he.date) : null, from: [], to: []]

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
    def remove = (request.method == 'PUT')
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstance.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstance.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      def editable = isUserCurator(obj,user) || user.isAdmin()

      if (editable) {
        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.status = 409
          result.message = message(code: "default.update.errors.message")
          render result as JSON
        }

        obj = restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

        if ( obj.validate() ) {
          log.debug("No errors.. updating combos..")

          def variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)

          if (variant_result.errors.size() > 0) {
            errors.variantNames = variant_result.errors
          }

          def subject_result = restMappingService.updateSubjects(obj, reqBody.subjects, remove)

          if (subject_result.errors.size() > 0) {
            errors.subjects = subject_result.errors
          }

          errors << updateCombos(obj, reqBody, remove)

          if ( errors.size() == 0 ) {
            obj = obj.save(flush:true)
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

    if(errors.size() > 0) {
      result.result = 'ERROR'
      result.error = errors
    }
    render result as JSON
  }

  @Transactional
  private def updateCombos(obj, reqBody, boolean remove = true) {
    log.debug("Updating title combos ..")
    def changed = false
    def errors = [:]

    if (reqBody.ids instanceof Collection || reqBody.identifiers instanceof Collection) {
      def id_list = reqBody.ids instanceof Collection ? reqBody.ids : reqBody.identifiers

      def id_result = restMappingService.updateIdentifiers(obj, id_list, remove)

      changed |= id_result.changed

      if (id_result.errors.size() > 0) {
        errors.ids = id_result.errors
      }
    }

    def pub_result = restMappingService.updatePublisherList(obj, reqBody.publisher, remove)

    changed |= pub_result.changed

    if (pub_result.errors.size() > 0) {
      errors.publisher = pub_result.errors
    }

    if (changed) {
      obj.lastSeen = System.currentTimeMillis()

      titleAugmentService.touchTitleTipps(obj, false)
    }

    errors
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    log.debug("Delete Title with id ${params.id}")
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
    def result = [:]
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    log.debug("tipps :: ${params}")
    def obj = TitleInstance.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstance.get(genericOIDService.oidToId(params.id))
    }

    log.debug("TIPPs for Title: ${obj}")

    if (obj) {
      def context = "/titles/" + params.id + "/tipps"
      def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
      def es_search = params.es ? true : false

      params.remove('id')
      params.remove('uuid')
      params.remove('es')
      params.title = obj.id

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
      result.message = "Title id ${params.id} could not be resolved!"
      response.status = 404
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def merge() {
    log.debug("Merging title ..")
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstance.findByUuid(params.id) ?: TitleInstance.get(genericOIDService.oidToId(params.id))

    if (obj && obj.isEditable()) {
      def curator = isUserCurator(obj, user)

      if (curator || user.isAdmin()) {
        def target = obj.class.get(params.int('target'))

        if (target) {
          if (params.list('ids')?.size() > 0) {
            params.list('ids').each { tid ->
              def idObj = Identifier.get(Long.valueOf(tid))

              if (idObj && !target.ids.contains(idObj)) {
                target.ids.add(idObj)
                target.save(flush: true)
              }
            }
          }
          else if (params.boolean('mergeIds')) {
            def id_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids')

            obj.ids.each{ old_id ->

              def old_combo = Combo.findByFromComponentAndToComponent(obj, old_id)

              def dupes = Combo.executeQuery("Select c from Combo as c where c.toComponent = :ido and c.fromComponent = :nt and c.type = :ct", [ido: old_id, nt: target, ct: id_combo_type])

              if (!dupes || dupes.size() == 0){
                log.debug("Adding Identifier ${old_id} to ${target}")
                Combo new_id = new Combo(toComponent: old_id, fromComponent: target, type: id_combo_type, status: old_combo.status).save(flush: true, failOnError: true)
              }
              else{
                log.debug("Identifier ${old_id} is already connected to ${target}..")
              }
            }
          }

          titleHistoryService.transferEvents(obj, target)

          obj.refresh()

          if (params.list('tipps')?.size() > 0) {
            params.list('tipps').each { tipp ->
              def tipp_combo = Combo.executeQuery("from Combo where fromComponent = :title and toComponent.id = :tippId", [title: obj, tippId: Long.valueOf(tipp)])

              if (tipp_combo?.size() == 1) {
                tipp_combo[0].fromComponent = target
              }
            }
          }
          else if (params.boolean('mergeTipps')) {
            obj.tipps.each { tipp ->
              def tippObj = TitleInstancePackagePlatform.get(tipp.id)

              tippObj.title = target
              tippObj.save(flush: true)
              target.save(flush: true)

              log.debug("Changed TIPP title to ${tippObj.title}")
            }
          }

          log.debug("Deleting stale title ${obj}")
          obj.deleteSoft()
          obj.save(flush: true)

          log.debug("Title is ${obj.status.value}!")
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
