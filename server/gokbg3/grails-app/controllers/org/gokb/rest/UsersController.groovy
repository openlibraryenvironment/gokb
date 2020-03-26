package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.Role
import org.gokb.cred.User
import org.springframework.security.access.annotation.Secured

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
@Transactional(readOnly = true)
class UsersController {

  static namespace = 'rest'

  def userProfileService

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def user = User.get(params.id)

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
      'self'  : "/users/$params.id",
      'update': "/users/$params.id",
      'delete': "/users/$params.id"
    ]

    def result = [
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
    ]

    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    render userProfileService.update(User.get(params.id), request.JSON) as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    render userProfileService.delete(User.get(params.id)) as JSON
  }
}
