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
  def messageService
  def restMappingService
  def componentLookupService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = User.get(springSecurityService.principal.id)
    def es_search = params.es ? true : false

    params.componentType = "Source" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    } else {
      def start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, Source, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
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
      obj = Source.findByUuid(params.id)

      if (!obj) {
        obj = Source.get(genericOIDService.oidToId(params.id))
      }

      if (obj?.isReadable()) {
        result = restMappingService.mapObjectToJson(obj, params, user)

        // result['_currentTipps'] = obj.currentTippCount
        // result['_linkedOpenRequests'] = obj.getReviews(true,true).size()
      } else if (!obj) {
        result.message = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      } else {
        result.message = "Access to object was denied!"
        response.setStatus(403)
        result.code = 403
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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def create() {
    Source source = new Source()
    def result = [:]
    def errors = [:]

    request.JSON.data.each { field, val ->
      if (val && source.hasProperty(field)) {
        source[field]=val
      }
      else {
        errors.message+="field $field is not applicable to sources"
      }
    }

    if (errors.size() == 0) {
      if (source.validate()) {
        source.save(flush: true)
        result.data = source
      } else {user
        result.errors = [message: "new source data is not valid"]
      }
    } else {
      result.errors = errors
    }
    render restMappingService.mapObjectToJson(source, params) as JSON
  }
}
