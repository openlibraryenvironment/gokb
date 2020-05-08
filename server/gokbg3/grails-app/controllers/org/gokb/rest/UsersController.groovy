package org.gokb.rest

import gokbg3.RegisterController
import grails.converters.JSON
import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.ui.strategy.RegistrationCodeStrategy
import org.gokb.UserProfileService
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.dao.SaltSource

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
@Transactional(readOnly = true)
class UsersController {

  static namespace = 'rest'

  @Autowired
  SaltSource saltSource
  UserProfileService userProfileService
  GrailsApplication grailsApplication
  def springSecurityService

  @Secured(value = ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'GET')
  def show() {
    def result = [data: [:]]
    def user = User.get(params.id as int)
    if (user) {
      result.data = collectUserProps(user)
    }
    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def index() {
    int pageSize = springSecurityService.currentUser.defaultPageSize
    def result = [data: []]
    // parse params
    int offset = params.offset ? params.offset as int : 0
    int limit = params.limit ? params.limit as int : (pageSize > 0 ? pageSize : 10)
    String[] sortFields = null, sortOrders = null
    if (params['_sort']) {
      sortFields = params['_sort'].split(',')
    }
    if (params['_order']) {
      sortOrders = params['_order'].split(',')
    }
    def sortQuery = "select ultimate from User ultimate where ultimate in ("
    def hqlParams = [:]
    def hqlQuery = "select distinct u from User u"

    if (params.roleId) {
      hqlQuery += ", UserRole ur, Role r where u = ur.user and ur.role=r "
      def roleIds = params.roleId.split(',')
      hqlQuery += ' and ('
      roleIds.eachWithIndex { v, i ->
        hqlQuery += "${i > 0 ? " or" : ""} r.id = :roleId$i"
        hqlParams += ["roleId$i": v as Long]
      }
      hqlQuery += ")"
    }

    if (params.curatoryGroupId) {
      def cgIds = params.curatoryGroupId.split(',')
      hqlQuery += " ${params.roleId ? 'and' : ' where'} ("
      cgIds.eachWithIndex { v, i ->
        CuratoryGroup cg = CuratoryGroup.findById(v as Long)
        if (cg) {
          hqlQuery += "${i > 0 ? " or" : ""} :group$i in elements (u.curatoryGroups)"
          hqlParams += ["group$i": cg]
        } else {
          result += [error: [message: "curatoryGroupId $v is unknown",
                             code   : 1]]
        }
      }
      hqlQuery += ')'
    }

    if (params.name) {
      hqlQuery += " ${params.roleId || params.curatoryGroupId ? 'and' : ' where'} (lower(u.username) like :name " +
        // displayName matching might get kicked out
        "or lower(u.displayName) like :name" +
        ")"
      hqlParams << ["name": "%$params.name%"]
    }

    if (params.containsKey("status")) {
      hqlQuery += "${params.roleId || params.curatoryGroupId || params.name ? 'and' : ' where'}"
      if (params.status == "true") {
        hqlQuery += " u.enabled = true and u.accountLocked = false and u.accountExpired = false and u.passwordExpired = false"
      } else {
        hqlQuery += " (u.enabled = false or u.accountLocked = true or u.accountExpired = true or u.passwordExpired = true)"
      }
    }

    sortQuery += hqlQuery + ")"
    if (sortOrders && sortFields) {
      int maxIndex = sortFields.size()
      for (int i = 0; i < maxIndex; i++) {
        if (i == 0)
          sortQuery += " order by"
        else
          sortQuery += " ,"
        sortQuery += " ultimate.${sortFields[i]}"
        sortQuery += ((sortOrders[i] != null) && ("desc" != sortOrders[i].toLowerCase())) ? " asc" : " desc"
      }
    }

    def metaParams = [max: limit, offset: offset]
    int count = User.executeQuery(hqlQuery, hqlParams).size()
    def users = User.executeQuery(sortQuery, hqlParams, metaParams)
    users.each {
      user ->
        result.data.add(collectUserProps(user))
    }
    result += [
      _pagination: [
        offset: offset,
        limit : limit,
        total : count
      ]
    ]

    def base = grailsApplication.config.serverURL + "/" + namespace
    def filter = ['limit', 'offset', 'controller', 'action']
    String outParams = '?'
    params.each {
      param, val ->
        if (!(param in filter)) {
          if (outParams.size() > 1)
            outParams += "&"
          outParams += "$param=$val"
        }
    }

    result += [
      _links: [
        self : [href: base + "/${params.controller}/$outParams&limit=$limit&offset=$offset "],
        first: [href: base + "/users/$outParams&limit=$limit&offset=0 "],
        last : [href: base + "/users/$outParams&limit=$limit&offset=${((int) (count / limit)) * limit}  "]
      ]
    ]
    if (offset >= limit)
      result._links += [prev: [href: base + "/users/$outParams&limit=$limit&offset=${offset - limit}"]]
    if (offset + limit < count)
      result._links += [next: [href: base + "/users/$outParams&limit=$limit&offset=${offset + limit}"]]

    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def create() {
    def result = userProfileService.create(request.JSON)
    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def user = User.get(params.id)
    def result = userProfileService.update(user, request.JSON, springSecurityService.currentUser)
    render result as JSON
  }

  @Secured(value = ['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'DELETE')
  @Transactional
  def delete() {
    def delUser = User.get(params.id)
    render userProfileService.delete(delUser) as JSON
  }

  def collectUserProps(User user) {
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
          'passwordExpired': user.passwordExpired,
          'status'         : user.enabled && !user.accountExpired && !user.accountLocked && !user.passwordExpired,
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
    if (params._embed?.split(',')?.contains('roles'))
      newUserData.roles = user.authorities
    else {
      newUserData.roles = []
      user.authorities.each { role ->
        newUserData.roles += [
          id    : role.id,
          authority  : role.authority,
          _links: [
            'self': [href: base + "/roles/$role.id"]
          ]
        ]
      }
    }

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
