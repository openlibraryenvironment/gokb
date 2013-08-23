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
    result.recentlyClosedActivities = Activity.findAllByOwnerAndStatusNotEqual(user,active_status,[max: 10, sort: "lastUpdated", order: "desc"])
    result.recentlyViewed = History.findAllByOwner(user,[max: 20, sort: "activityDate", order: "desc"])

    result
  }

  def showRules() {
    def result=[:]
    result.rules = grailsApplication.config.validationRules
    result
  }
}
