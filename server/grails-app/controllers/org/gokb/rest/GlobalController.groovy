package org.gokb.rest

import grails.converters.*
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import java.time.Duration
import java.time.LocalDateTime

import org.gokb.cred.*

@Transactional(readOnly = true)
class GlobalController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def componentLookupService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    def cobj = setType(params)
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    def es_search = params.es ? true : false

    if (es_search) {
      params.remove('es')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      def start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, cobj, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
    }

    if (result.result == 'ERROR') {
      response.status = (result.status ?: 500)
    }

    render result as JSON
  }

  private Class setType(params) {
    Class type = KBComponent

    if (params.componentType) {
      def typeString = params.componentType

      if (typeString.toLowerCase() == 'journal' || typeString.toLowerCase() == 'serial' ) {
        type = JournalInstance
      }
      else if (typeString.toLowerCase() == 'book' || typeString.toLowerCase() == 'monograph') {
        type = BookInstance
      }
      else if (typeString.toLowerCase() == 'database') {
        type = DatabaseInstance
      }
      else if (typeString.toLowerCase() == 'title') {
        type = TitleInstance
      }
      else if (typeString.toLowerCase() == 'tipp') {
        type = TitleInstancePackagePlatform
      }
      else {
        try {
          type = Class.forName('org.gokb.cred.' + typeString)
        }
        catch (Exception e) {
          log.debug("Unable to find class ${typeString}")
        }
      }
    }
    return type
  }
}