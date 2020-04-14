package org.gokb.rest

import grails.converters.JSON
import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import org.gokb.cred.User
import org.gokb.cred.UserRole
import org.springframework.beans.factory.annotation.Autowired

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
@Transactional(readOnly = true)
class UsersController {

  static namespace = 'rest'

  def userProfileService

  @Secured(value = ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'GET')
  def show() {
    def result = [data: [:]]
    def user = User.get(params.id)
    if (user) {
      result.data = collectUserProps(user)
    }
    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def search() {
    def result = [data: []]
    // parse params
    int offset = params.offset ? params.offset as int : 0
    int limit = params.limit ? params.limit as int : 10

    def paging
    String[] sortFields, sortOrders
    if (params._sort)
      sortFields = params._sort.split(',')
    if (params._order)
      sortOrders = params._order.split(',')

    def hqlQuery = "select ultimate from User ultimate where ultimate in (select distinct u " +
            "from User u, UserRole ur, Role r " +
            "where u = ur.user and ur.role=r"
    def hqlParams = [:]
    if (params.name) {
      hqlQuery += " and (lower(u.username) like :name or lower(u.displayName) like :name)"
      hqlParams << ["name": "%$params.name%"]
    }
    if (params.roleId) {
      hqlQuery += " and r.id = :roleId"
      hqlParams += ["roleId": params.roleId as Long]
    }
    if (params.curatoryGroupId) {
      hqlQuery += " and :group in u.curatoryGroups.id"
      hqlParams += ["group": params.curatoryGroupId as Long]
    }
    hqlQuery += ")"
    if (sortOrders && sortFields) {
      int maxIndex = sortOrders.size() < sortFields.size() ? sortOrders.size() : sortFields.size()
      for (int i = 0; i < maxIndex; i++) {
        if (i == 0)
          hqlQuery += " order by"
        else
          hqlQuery += " ,"
        hqlQuery += " ultimate.${sortFields[i]}"
        hqlQuery += "desc" == sortOrders[i].toLowerCase() ? " desc" : " asc"
      }
    }

    def metaParams = [max: limit, offset: offset]
    def users = User.executeQuery(hqlQuery, hqlParams, metaParams)
    users.each { user ->
      result.data.add(collectUserProps(user))
    }

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

  def private collectUserProps(User user) {
    def base = grailsApplication.config.serverURL + "/" + namespace
    def includes = [], excludes = [],
        newUserData = [
                'id'             : user.id,
                'username'       : user.username,
                'displayName'    : user.displayName,
                'email'          : user.email,
                'enabled'        : user.enabled,
                'accountExpired' : user.accountExpired,
                'accountLocked'  : user.accountLocked,
                'passwordExpired': user.accountExpired,
                'defaultPageSize': user.defaultPageSize
        ]
    if (params._embed?.split(',')?.contains('curatoryGroups'))
      newUserData.curatoryGroups = user.curatoryGroups
    else {
      newUserData.curatoryGroups = []
      user.curatoryGroups.each { group ->
        newUserData.curatoryGroups += [
                id    : group.id,
                name  : group.name,
                _links: [
                        'self': [href: base + "/curatoryGroups/$group.id"]
                ]
        ]
      }
    }
    newUserData.roles = user.authorities

    if (params._include)
      includes = params._include.split(',')
    if (params._exclude) {
      excludes = params._exclude.split(',')
      includes.each { prop ->
        excludes -= prop
      }
    }

    newUserData = newUserData.findAll { k, v ->
      (!excludes.contains(k) || (!includes.empty && includes.contains(k)))
    }

    newUserData._links = [
            self  : [href: base + "/users/$user.id"],
            update: [href: base + "/users/$user.id"],
            delete: [href: base + "/users/$user.id"]
    ]

    return newUserData
  }
}
