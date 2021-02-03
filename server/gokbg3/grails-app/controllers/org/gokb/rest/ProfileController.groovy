package org.gokb.rest

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.JobResult
import org.gokb.cred.KBComponent
import org.gokb.cred.Role
import org.gokb.cred.User
import grails.plugin.springsecurity.annotation.Secured

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
class ProfileController {

  static namespace = 'rest'

  def springSecurityService
  def userProfileService
  def passwordEncoder
  ConcurrencyManagerService concurrencyManagerService

  def show() {
    def user = User.get(springSecurityService.principal.id)

    def cur_groups = []
    def base = grailsApplication.config.serverURL + "/rest"

    user.curatoryGroups?.each { cg ->
      cur_groups.add([name: cg.name, id: cg.id, uuid: cg.uuid])
    }

    def roles = []
    Role.findAll().each { role ->
      if (user.hasRole(role.authority))
        roles.add(role)
    }

    def links = [
      'self'  : ['href': base + '/profile'],
      'update': ['href': base + '/profile'],
      'delete': ['href': base + '/profile']
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
    Map result = [:]
    User user = User.get(springSecurityService.principal.id)
    def reqData = request.JSON
    reqData.remove('new_password')
    reqData.remove('password')
    result = userProfileService.update(user, reqData, params, user)
    render result as JSON
  }

  @Secured(value = ['ROLE_USER', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'PATCH')
  @Transactional
  def patch() {
    Map result = [:]
    Map reqData = request.JSON
    User user = User.get(springSecurityService.principal.id)
    if (reqData.new_password && reqData.password) {
      if (passwordEncoder.isPasswordValid(user.password, reqData.password, null)) {
        user.password = reqData.new_password
        user.save(flush: true, failOnError: true);
      } else {
        response.status = 400
        result.errors = [password: [message: "wrong password - profile unchanged", code: null]]
        render result as JSON
      }
    }
    reqData.remove('new_password')
    reqData.remove('password')
    result = userProfileService.update(user, reqData, params, user)
    if (result.errors != null)
      response.status = 400
    render result as JSON
  }

  @Secured(value = ['ROLE_USER', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'DELETE')
  @Transactional
  def delete() {
    User user = User.get(springSecurityService.principal.id)
    userProfileService.delete(user)
    response.status = 204
  }

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def getJobs() {
    def result = [:]
    def max = params.limit ? params.int('limit') : 10
    def offset = params.offset ? params.int('offset') : 0
    def base = grailsApplication.config.serverURL + "/rest"
    def sort = params._sort ?: null
    def order = params._order ?: null
    User user = User.get(springSecurityService.principal.id)
    def errors = [:]

    if (params.boolean('archived') == true) {
      result.data = []
      def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.ownerId = ?", [user.id])[0]
      def jobs = JobResult.executeQuery("from JobResult as jr where jr.ownerId = ? order by jr.startTime desc", [user.id], [max: max, offset: offset])

      jobs.each { j ->
        def component = j.linkedItemId ? KBComponent.get(j.linkedItemId) : null
        // No JsonObject for list view

        result.data << [
          uuid: j.uuid,
          description: j.description,
          type: j.type ? [id: j.type.id, name: j.type.value, value: j.type.value] : null,
          linkedItem: (component ? [id: component.id, type: component.niceName, uuid: component.uuid, name: component.name] : null),
          startTime: j.startTime,
          endTime: j.endTime,
          status: j.statusText
        ]
      }

      result['_pagination'] = [
        offset: offset,
        limit: max,
        total: hqlTotal
      ]
    }
    else {
      result = concurrencyManagerService.getUserJobs(user.id as int, max, offset)
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def cleanupJobs() {
    def result = [:]
    def max = params.limit ? params.int('limit') : 10
    def offset = params.offset ? params.int('offset') : 0
    def base = grailsApplication.config.serverURL + "/rest"
    def sort = params._sort ?: null
    def order = params._order ?: null
    User user = User.get(springSecurityService.principal.id)
    def errors = [:]
    def jobs = concurrencyManagerService.getUserJobs(user.id as int, max, offset)

    jobs.each { k, v ->
      if (v.endTime || v.cancelled) {
        def j = concurrencyManagerService.getJob(v.id, true)
        log.debug("Removed job ${v.id}")
      }
    }

    render result as JSON
  }
}
