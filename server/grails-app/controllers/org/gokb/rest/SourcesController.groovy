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
    Map result = [:]
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
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
    Map result = [:]
    Source obj
    User user

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
    Source obj = null
    Boolean changed = true
    Map result = [:]
    Map errors = [:]
    def reqBody = request.JSON
    User user = User.get(springSecurityService.principal.id)

    if (reqBody?.name) {
      try {
        obj = new Source(name: reqBody.name)

        Map fieldConfig = [:]

        if (!user || !user.isAdmin()) {
          fieldConfig.ignore = ['importConfig', 'ignoreSizeLimit', 'ezbMatch']
        }

        changed = restMappingService.updateObject(obj, fieldConfig, reqBody)
      }
      catch (grails.validation.ValidationException ve) {
        errors = ve.errors
      }
    }
    else {
      errors = [result: 'ERROR', message:'Missing name for source!', badData:[reqBody]]
    }

    if (!errors) {
      if ( obj.validate() ) {
        obj.save(flush: true)

        errors << updateCombos(obj, reqBody, changed, false)

        if (!errors) {
          response.status = 201
          result = restMappingService.mapObjectToJson(obj, params, user)
        }
        else {
          response.status = 400
          result.errors = errors
          result.result = 'ERROR'
        }
      } else {
        result = [result: 'ERROR', message: "new source data is not valid", errors: messageService.processValidationErrors(obj.errors)]
        response.status = 409
        obj?.discard()
      }
    } else {
      response.status = 400
      result.errors = errors
      result.result = 'ERROR'
    }
    render result as JSON
  }

  @Secured(['ROLE_EDITOR', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def result = [result: 'OK', params: params, changed: false]
    Source obj = Source.get(genericOIDService.oidToId(params.id))
    def errors = [:]
    def reqBody = request.JSON
    boolean remove = (request.method == 'PUT')
    User user = User.get(springSecurityService.principal.id)
    boolean editable = true

    if ( !user.hasRole('ROLE_ADMIN') && obj.curatoryGroups && obj.curatoryGroups.size() > 0 ) {
      def cur = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

      if (!cur) {
        editable = false
      }
    }

    if (editable) {
      if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
        response.status = 409
        result.message = message(code: "default.update.errors.message")
        render result as JSON
      }

      Map fieldConfig = [:]

      if (!user || !user.isAdmin()) {
        fieldConfig.ignore = ['importConfig', 'ignoreSizeLimit', 'ezbMatch']
      }

      result.changed = restMappingService.updateObject(obj, fieldConfig, reqBody)

      errors << updateCombos(obj, reqBody, result.changed, remove)

      if (!errors) {
        if ( obj.validate() ) {
          obj = obj.merge(flush: true)
          result = restMappingService.mapObjectToJson(obj, params, user)
        } else {
          result = [result: 'ERROR', message: "new source data is not valid", errors: messageService.processValidationErrors(obj.errors)]
          response.status = 409
          obj?.discard()
        }
      } else {
        response.status = 400
        result.errors = errors
        result.result = 'ERROR'
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User must belong to at least one curatory group of an existing item to make changes!"
    }
    render result as JSON
  }

  private def updateCombos(obj, reqBody, changed, boolean remove = true) {
    log.debug("Updating package combos ..")
    Map errors = [:]

    if (reqBody.curatoryGroups) {
      Map update_result = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      changed |= update_result.changed

      if (update_result.errors.size() > 0) {
        errors['curatoryGroups'] = update_result.errors
      }
    }

    errors
  }
}
