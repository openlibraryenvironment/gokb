package org.gokb.rest

import grails.converters.JSON
import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.ui.strategy.RegistrationCodeStrategy
import org.gokb.UserProfileService
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.User
import org.springframework.beans.factory.annotation.Autowired

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
@Transactional(readOnly = true)
class UsersController {

  static namespace = 'rest'

  @Autowired
  UserProfileService userProfileService
  GrailsApplication grailsApplication
  def springSecurityService
  def restMappingService

  @Secured(value = ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'GET')
  def show() {
    def result = [data: [:]]
    def user = User.get(params.id as int)

    if (user) {
      result.data = userProfileService.collectUserProps(user, params)
    }
    else {
      response.status = 404
      result.result = 'ERROR'
      result.message = 'User not found!'
    }

    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def index() {
    User user = springSecurityService.currentUser
    def result = userProfileService.restLookup(user, params)

    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def save() {
    User adminUser = User.get(springSecurityService.principal.id)
    def result = [:]
    if (request.JSON) {
      result = userProfileService.create(request.JSON, adminUser)
      if (!result.errors)
        response.status = 201
      else
        response.status = 400
    } else {
      response.status = 400
      def errors = []
      errors << [message: "no data found in the request body", baddata: request.JSON]
      result.errors = errors
    }
    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    User user = User.get(params.id)
    User adminUser = User.get(springSecurityService.principal.id)
    def result = [:]
    if (user && request.JSON)
      result = userProfileService.update(user, request.JSON, params, adminUser)
    else {
      response.status = 400
      def errors = []
      errors << [message: "no data found in the request", baddata: request.JSON]
      result.errors = errors
    }
    if (result.errors?.size() >0){
      log.debug("${result.errors}")
      response.status = 400
    }
    render result as JSON
  }

  @Secured(value = ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'DELETE')
  @Transactional
  def delete() {
    def result = [:]
    User adminUser = User.get(springSecurityService.principal.id)
    User delUser = User.get(params.id)

    if (delUser) {
      if (delUser.isAdmin() && !adminUser.isSuperUser()) {
        response.status = 403
        result.message = 'This account is not authorized to delete this user.'
      }
      else {
        response.status = 204
        result = userProfileService.delete(delUser)
      }
    }
    else {
      response.status = 404
      result.message = 'Unable to reference user with ID ${params.id}!'
    }

    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def activate() {
    def result = [:]
    Boolean alertUser = params.boolean('sendAlert') ?: false
    User adminUser = User.get(springSecurityService.principal.id)

    if (params.id) {
      result = userProfileService.activate(params.id, adminUser, alertUser)

      if (result.errors)
        response.status = 400
    } else {
      response.status = 404
      result.message = 'Unable to reference user by ID!'
    }
    render result as JSON
  }
}
