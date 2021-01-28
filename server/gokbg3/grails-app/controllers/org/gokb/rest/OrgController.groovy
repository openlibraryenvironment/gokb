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
      result = ESSearchService.find(params)
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
  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def save() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)

    if (reqBody) {
      def obj = null
      def lookup_result = orgService.restLookup(reqBody)

      if (lookup_result.to_create) {
        def normname = Org.generateNormname(reqBody.name)
        obj = new Org(name: reqBody.name, normname: normname).save(flush:true)
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

      if (lookup_result.to_create && !obj) {
        log.debug("Could not upsert object!")
        errors.object = [[badData: reqBody, message:"Unable to save object!"]]
      }
      else if (obj?.hasErrors()) {
        log.debug("Object has errors!")
        errors = messageService.processValidationErrors(obj.errors, request.locale)
        log.debug("${errors}")
      }
      else if (obj) {
        def jsonMap = obj.jsonMapping

        log.debug("Updating ${obj}")
        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        if ( obj.validate() ) {
          if(errors.size() == 0) {
            log.debug("No errors.. saving")
            obj.save()

            if (reqBody.variantNames) {
              obj = restMappingService.updateVariantNames(obj, reqBody.variantNames)
            }

            errors << updateCombos(obj, reqBody)

            if (errors.size() == 0) {
              log.debug("No errors: ${errors}")
              obj.save(flush:true)
              response.status = 201
              result = restMappingService.mapObjectToJson(obj, params, user)
            }
            else {
              result.result = 'ERROR'
              log.debug("There were errors setting combo props!")
              obj.discard()
              result.error = errors
            }
          }
          else {
            response.setStatus(400)
            result.message = message(code:"default.create.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          errors.addAll(messageService.processValidationErrors(obj.errors, request.locale))
        }
      }
    }
    else {
      errors = [badData: reqBody, message:"Unable to save organization!"]
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
    def result = ['result':'OK', 'params': params]
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

      if ( editable && obj.respondsTo('curatoryGroups') && obj.curatoryGroups?.size() > 0 ) {
        def cur = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

        if (!cur) {
          editable = false
        }
      }

      if (editable) {

        def jsonMap = obj.jsonMapping

        obj = restMappingService.updateObject(obj, jsonMap, reqBody)


        if (reqBody.variantNames) {
          obj = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)
        }

        errors << updateCombos(obj, reqBody, remove)

        if( obj.validate() ) {
          if(errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)
          }
          else {
            response.setStatus(400)
            result.message = message(code:"default.update.errors.message")
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

    if (errors.size() > 0) {
      log.debug("Errors: ${errors}")
      result.error = errors
    }

    render result as JSON
  }

  private def updateCombos(obj, reqBody, boolean remove = true) {
    log.debug("Updating package combos ..")
    def errors = [:]

    if (reqBody.ids instanceof Collection || reqBody.identifiers instanceof Collection) {
      def id_list = reqBody.ids instanceof Collection ? reqBody.ids : reqBody.identifiers

      def id_errors = restMappingService.updateIdentifiers(obj, id_list, remove)

      if (id_errors.size() > 0) {
        errors.ids = id_errors
      }
    }

    if (reqBody.providedPlatforms instanceof Collection) {
      def plt_combo_type = RefdataCategory.lookup('Combo.Type', 'Platform.Provider')
      Set new_plts = []

      reqBody.providedPlatforms.each { plt ->
        Platform plt_obj = null

        if (plt instanceof String) {
          plt_obj = Platform.findByNameIlike(plt)
        }
        else if (plt instanceof Integer){
          plt_obj = Platform.findById(plt)
        }
        else if (plt instanceof Map) {
          if (plt.id) {
            log.debug("Getting Platform by ID ${plt.id}..")
            plt_obj = Platform.findById(plt.id)
          }
          else {
            def lookup = platformService.restLookup(plt, null)
            log.debug("Result of platform lookup: ${lookup}")

            if (lookup.to_create) {
              plt_obj = Platform.upsertDTO(plt)
            }
            else {
              lookup.matches?.each { mid, info ->
                log.debug("Handling platform with ID ${mid}..")
                if (!plt_obj && !errors.providedPlatforms) {
                  def plt_candidate = Platform.get(mid)

                  if (plt_candidate && plt_candidate.provider == null) {
                    plt_obj = plt_candidate
                  }
                  else if (!plt_candidate) {
                    errors.providedPlatforms << [message: "Unable to lookup platform!", code: 404, baddata: plt]
                  }
                  else {
                    errors.providedPlatforms << [message: "Matched Platform already has a Provider!", code: 409, baddata: plt]
                  }
                }
                else {
                  log.debug("Not overwriting or adding while errors exist!")
                }
              }

              if (lookup.matches?.size() > 1) {
                log.warn("Multiple matches for platform info ${plt}!")
              }
            }
          }
        }

        if (plt_obj) {
          new_plts << plt_obj
        }
        else {
          if (!errors.providedPlatforms) {
            errors.providedPlatforms = []
          }
          errors.providedPlatforms << [message: "Unable to lookup platform!", code: 404, baddata: plt]
        }
      }

      if (!obj.hasErrors() || errors.size() > 0) {
        new_plts.each { c ->
          if (!obj.providedPlatforms.contains(c)) {
            log.debug("Adding new platform ${c}..")
            def new_combo = new Combo(fromComponent: c, toComponent: obj, type: plt_combo_type).save(flush: true)
          }
          else {
            log.debug("Existing platform ${c}..")
          }
        }

        if (remove) {
          obj.providedPlatforms.retainAll(new_plts)
        }
      }
      log.debug("New cgs: ${obj.providedPlatforms}")
    }

    if (reqBody.curatoryGroups instanceof Collection) {
      def cg_errors = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      if (cg_errors.size() > 0) {
        errors['curatoryGroups'] = cg_errors
      }
    }

    log.debug("After update: ${obj}")
    errors
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='DELETE')
  @Transactional
  def delete() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Org.findByUuid(params.id)

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if ( obj && obj.isDeletable() ) {
      def curator = KBComponent.has(obj, 'curatoryGroups') ? user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id) : true

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
    def obj = Org.findByUuid(params.id)

    if (!obj) {
      obj = Org.get(genericOIDService.oidToId(params.id))
    }

    if ( obj && obj.isEditable() ) {
      def curator = KBComponent.has(obj, 'curatoryGroups') ? (obj.curatoryGroups?.size() == 0 || user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)) : true

      if ( curator || user.isAdmin() ) {
        obj.retire()
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
