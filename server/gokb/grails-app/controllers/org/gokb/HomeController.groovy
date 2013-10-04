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
    def needs_review_status = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Needs Review')

    result.openActivities = Activity.findAllByOwnerAndStatus(user,active_status)
    result.recentlyClosedActivities = Activity.findAllByOwnerAndStatusNotEqual(user,active_status,[max: 10, sort: "lastUpdated", order: "desc"])
    result.recentlyViewed = History.findAllByOwner(user,[max: 20, sort: "activityDate", order: "desc"])
    result.recentReviewRequests = ReviewRequest.findAllByRaisedByAndStatus(user,needs_review_status,[max: 10, sort: "dateCreated", order: "desc"])

    result
  }

  def showRules() {
    def result=[:]
    result.rules = grailsApplication.config.validationRules
    result
  }

  def about() {
  }
}
