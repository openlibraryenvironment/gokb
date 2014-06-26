package org.gokb

import grails.converters.*
import grails.plugins.springsecurity.Secured

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*


class ProfileController {

  def springSecurityService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
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
    redirect(action:'index')
  }
}
