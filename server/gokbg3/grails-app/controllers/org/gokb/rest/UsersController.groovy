package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import org.gokb.cred.Identifier
import org.gokb.cred.Role
import org.gokb.cred.User

import java.time.Duration
import java.time.LocalDateTime

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
@Transactional(readOnly = true)
class UsersController {

  static namespace = 'rest'

  def userProfileService
  def restMappingService
  def componentLookupService
  def springSecurityService

  @Secured(value = ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'GET')
  def show() {
    def links = [
            'self': [href: "/users/$params.id"]]
    def result = [data: [:]]
    def user = User.get(params.id)
    if (user != null) {
      def cur_groups = []

      user.curatoryGroups?.each { cg ->
        cur_groups.add([id: cg.id, name: cg.name, uuid: cg.uuid])
      }

      result.data = [
              'id'             : user.id,
              'username'       : user.username,
              'displayName'    : user.displayName,
              'email'          : user.email,
              'enabled'        : user.enabled,
              'accountExpired' : user.accountExpired,
              'accountLocked'  : user.accountLocked,
              'passwordExpired': user.accountExpired,
              'defaultPageSize': user.defaultPageSize,
              'curatoryGroups' : cur_groups,
              'roles'          : user.authorities
      ]

      links << [
              'update': [href: "/users/$params.id"],
              'delete': [href: "/users/$params.id"]
      ]
    }

    result.data.put('_links', links)
    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def user = User.get(params.id)
    render userProfileService.update(user, request.JSON) as JSON
  }

  @Secured(value = ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'DELETE')
  @Transactional
  def delete() {
    def delUser = User.get(params.id)
    render userProfileService.delete(delUser) as JSON
  }
}
