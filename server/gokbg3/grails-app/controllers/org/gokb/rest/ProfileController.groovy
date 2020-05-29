package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.Role
import org.gokb.cred.User
import grails.plugin.springsecurity.annotation.Secured

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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
    User user = User.get(springSecurityService.principal.id)
    render userProfileService.update(user, request.JSON.data, params, user) as JSON
  }

  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  @Transactional
  def patch() {
    def result = [:]
    Map reqData = request.JSON.data
    User user = User.get(springSecurityService.principal.id)
    if (reqData.new_password && reqData.password) {
      if (passwordEncoder.isPasswordValid(user.password, reqData.password, null)) {
        user.password = reqData.new_password
        user.save(flush: true, failOnError: true);
      } else {
//        result.data = user
        result.error = [message: "wrong password - profile unchanged"]
        render result as JSON
      }
    }
    reqData.remove('new_password')
    reqData.remove('password')
    render userProfileService.update(user, reqData, params, user) as JSON
  }

  @Transactional
  def delete() {
    userProfileService.delete(User.get(springSecurityService.principal.id))
    def result = [:]
    render result as JSON
  }
}
