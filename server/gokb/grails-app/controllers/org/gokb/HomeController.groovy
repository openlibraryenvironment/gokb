package org.gokb

import grails.plugins.springsecurity.Secured
import org.gokb.cred.*


class HomeController {

  def grailsApplication
  def springSecurityService
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    def result=[:]

    User user = springSecurityService.currentUser
    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active')

    result.openActivities = Activity.findAllByOwnerAndStatus(user,active_status)

    result
  }

  def showRules() {
    def result=[:]
    result.rules = grailsApplication.config.validationRules
    result
  }
}
