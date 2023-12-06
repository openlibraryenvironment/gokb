package org.gokb.rest

import grails.converters.*
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import org.gokb.cred.*

@Transactional(readOnly = true)
class LanguageController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def restMappingService
  def languagesService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def result = languagesService.getLanguages()
    render result as JSON
  }
}
