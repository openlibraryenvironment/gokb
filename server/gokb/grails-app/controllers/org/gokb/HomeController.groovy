package org.gokb

import grails.plugins.springsecurity.Secured


class HomeController {

  def grailsApplication

  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
  }

  def showRules() {
    def result=[:]
    result.rules = grailsApplication.config.validationRules
    result
  }
}
