package org.gokb

class UserController extends grails.plugin.springsecurity.ui.UserController {

  UserProfileService userProfileService

  def delete() {
    log.debug("Deleting user ${params.id} ..")
    userProfileService.delete(User.get(params.id))
    redirect(controller: 'user', action: 'search')
  }
}
