package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.Role
import org.gokb.cred.Source
import org.gokb.cred.User
import org.gokb.cred.UserRole

import java.time.Duration
import java.time.LocalDateTime

@Transactional(readOnly = true)
class SourcesController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def restMappingService
  def componentLookupService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    def start_db = LocalDateTime.now()
    result = componentLookupService.restLookup(user, Source, params)
    log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")

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
      obj = Source.findByUuid(params.id)

      if (!obj) {
        obj = Source.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        result = restMappingService.mapObjectToJson(obj, params, user)

        // result['_currentTipps'] = obj.currentTippCount
        // result['_linkedOpenRequests'] = obj.getReviews(true,true).size()
      } else {
        result.message = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      }
    } else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.code = 400
      result.message = 'No object id supplied!'
    }

    render result as JSON
  }

  @Secured(['ROLE_EDITOR', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def save() {
    Source source = null
    def result = [:]
    def errors = [:]
    def reqBody = request.JSON
    User user = User.get(springSecurityService.principal.id)

    if (reqBody?.name) {
      try {
        source = new Source(name: reqBody.name)

        def jsonMap = [:]

        source = restMappingService.updateObject(source, jsonMap, reqBody)
      }
      catch (grails.validation.ValidationException ve) {
        errors = ve.errors
      }
    }
    else {
      errors = [result: 'ERROR', message:'Missing name for source!', badData:[reqBody]]
    }

    if (!errors) {
      if ( source.validate() ) {
        source.save(flush: true)

        errors << updateCombos(source, reqBody, false)

        if (!errors) {
          response.setStatus(201)
          result = restMappingService.mapObjectToJson(source, params, user)
        }
        else {
          response.setStatus(400)
          result.errors = errors
          result.result = 'ERROR'
        }
      } else {
        result = [result: 'ERROR', message: "new source data is not valid", errors: messageService.processValidationErrors(source.errors)]
        response.setStatus(409)
        source?.discard()
      }
    } else {
      response.setStatus(400)
      result.errors = errors
      result.result = 'ERROR'
    }
    render result as JSON
  }

  @Secured(['ROLE_EDITOR', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    Source source = Source.get(genericOIDService.oidToId(params.id))
    def result = [:]
    def errors = [:]
    def reqBody = request.JSON
    def remove = (request.method == 'PUT')
    User user = User.get(springSecurityService.principal.id)
    boolean editable = true

    if ( !user.hasRole('ROLE_ADMIN') && source.curatoryGroups && source.curatoryGroups.size() > 0 ) {
      def cur = user.curatoryGroups?.id.intersect(source.curatoryGroups?.id)

      if (!cur) {
        editable = false
      }
    }

    if (editable) {
      source = restMappingService.updateObject(source, null, reqBody)

      errors << updateCombos(source, reqBody, remove)

      if (!errors) {
        if ( source.validate() ) {
          source = source.merge(flush: true)
          result = restMappingService.mapObjectToJson(source, params, user)
        } else {
          result = [result: 'ERROR', message: "new source data is not valid", errors: messageService.processValidationErrors(source.errors)]
          response.setStatus(409)
          source?.discard()
        }
      } else {
        response.setStatus(400)
        result.errors = errors
        result.result = 'ERROR'
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User must belong to at least one curatory group of an existing item to make changes!"
    }
    render result as JSON
  }

  private def updateCombos(obj, reqBody, boolean remove = true) {
    log.debug("Updating package combos ..")
    def errors = [:]

    if (reqBody.curatoryGroups) {
      def cg_errors = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      if (cg_errors.size() > 0) {
        errors['curatoryGroups'] = cg_errors
      }
    }

    errors
  }
}
