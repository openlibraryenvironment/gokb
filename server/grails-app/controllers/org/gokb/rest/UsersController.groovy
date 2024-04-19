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
      String cgQuery = " ${params.roleId ? 'and' : ' where'} ("
      def cgIds = params.curatoryGroupId.split(',')
      cgIds.eachWithIndex { v, i ->
        CuratoryGroup cg = CuratoryGroup.findById(v as Long)
        if (cg) {
          cgQuery += "${i > 0 ? " or" : ""} :group$i in elements (u.curatoryGroups)"
          hqlParams += ["group$i": cg]
        } else {
          result += [error: [message: "curatoryGroupId $v is unknown",
                             code   : 1]]
        }
      }
      cgQuery += ')'
      if (!result.error)
        hqlQuery += cgQuery
    }

    if (params.name) {
      hqlQuery += " ${params.roleId || params.curatoryGroupId ? 'and' : ' where'} (lower(u.username) like lower(:name) " +
        // displayName matching might get kicked out
        "or lower(u.displayName) like lower(:name)" +
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
        result.data.add(userProfileService.collectUserProps(user, params))
    }
    result += [
      _pagination: [
        offset: offset,
        limit : limit,
        total : count
      ]
    ]

    def base = grailsApplication.config.getProperty('grails.serverURL', String, '') + "/" + namespace
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
