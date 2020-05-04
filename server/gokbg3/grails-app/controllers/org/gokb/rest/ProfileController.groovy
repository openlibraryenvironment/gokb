package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.Role
import org.gokb.cred.User
import org.springframework.security.access.annotation.Secured

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
@Transactional(readOnly = true)
class ProfileController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def userProfileService
  def passwordEncoder

  def show() {
    def user = User.get(springSecurityService.principal.id)

    def cur_groups = []

    user.curatoryGroups?.each { cg ->
      cur_groups.add([name: cg.name, id: cg.id, uuid: cg.uuid])
    }

    def roles = []
    Role.findAll().each { role ->
      if (user.hasRole(role.authority))
        roles.add(role)
    }

    def links = [
      'self'  : ['href': 'rest/profile'],
      'update': ['href': 'rest/profile'],
      'delete': ['href': 'rest/profile']
    ]

    def result = ['data': [
      'id'             : user.id,
      'username'       : user.username,
      'displayName'    : user.displayName,
      'email'          : user.email,
      'curatoryGroups' : cur_groups,
      'enabled'        : user.enabled,
      'accountExpired' : user.accountExpired,
      'accountLocked'  : user.accountLocked,
      'passwordExpired': user.accountExpired,
      'defaultPageSize': user.defaultPageSize,
      'roles'          : roles,
      '_links'         : links
    ]]
    render result as JSON
  }

  @Transactional
  def update() {
    render userProfileService.update(springSecurityService.currentUser, request.JSON, springSecurityService.currentUser) as JSON
  }

  @grails.plugin.springsecurity.annotation.Secured(value = ['IS_AUTHENTICATED_FULLY'], httpMethod = 'PATCH')
  @Transactional
  def patch() {
    def result = [:]
    User user = springSecurityService.currentUser
    if (request.JSON.new_password) {
      if (passwordEncoder.matches(user.password, params.password, null)) {
        user.password = request.JSON.new_password
      } else {
        result.data = user
        result.error.message = "wrong password - profile unchanged"
        render result as JSON
      }
    }
    request.JSON.each { propName, propValue ->
      if (!propName in ["new_password", "password"]) {
        if (propName == "curatoryGroups") {
          // patch curatory groups
        } else if (propName == "roles") {
          // patch roles
        }
        user.propName = propValue
      }
    }
    user.save(flush: true, failOnError: true)
    result.data = user
    render result as JSON
  }

  @Transactional
  def delete() {
    userProfileService.delete(springSecurityService.currentUser)
    def result = [:]
    render result as JSON
  }
}
