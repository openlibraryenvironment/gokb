package org.gokb

import grails.plugins.springsecurity.Secured
import org.gokb.cred.*


class HomeController {

  def grailsApplication
  def springSecurityService
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    redirect(controller:'search',action:'index',params:[qbe:'g:components'])
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def dash() { 
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

  def releaseNotes() {
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def profile() {
    def result = [:]
    User user = springSecurityService.currentUser
    result.user = user
    result
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def preferences() {
    def result = [:]
    User user = springSecurityService.currentUser
    result.user = user
    result
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def changePass() {
    if ( params.newpass == params.repeatpass ) {
      User user = springSecurityService.currentUser
      if ( user.password == springSecurityService.encodePassword(params.origpass) ) {
        user.password = params.newpass
        user.save();
        flash.message = "Password Changed!"
      }
      else {
        flash.message = "Existing password does not match: not changing"
      }
    }
    else {
      flash.message = "New password does not match repeat password: not changing"
    }
    redirect(action:'profile')
  }
}
