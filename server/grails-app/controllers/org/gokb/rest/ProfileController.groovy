package org.gokb.rest

import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured

import org.gokb.cred.JobResult
import org.gokb.cred.KBComponent
import org.gokb.cred.Role
import org.gokb.cred.User
import org.grails.web.json.JSONObject

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
class ProfileController {

  static namespace = 'rest'

  def springSecurityService
  def userProfileService
  def passwordEncoder
  def concurrencyManagerService
  def jobResultService

  def show() {
    User user = User.get(springSecurityService.principal.id)

    List cur_groups = []
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"

    user.curatoryGroups?.each { cg ->
      Map cg_info = [
        name: cg.name,
        id: cg.id,
        uuid: cg.uuid,
        email: cg.email,
        _links: [
          self: [
            href: base + "/curatoryGroups/$cg.id",
          ],
          update: [
            href: (cg.owner == user || user.isAdmin()) ? base + "/curatoryGroups/$cg.id" : null
          ],
          delete: [
            href: (user.superUserStatus) ? base + "/curatoryGroups/$cg.id" : null
          ]
        ]
      ]

      if (cg.owner == user || user.isAdmin()) {
        cg_info.cancelledImportAlerts = cg.cancelledImportAlerts
        cg_info.newReviewsAlerts = cg.newReviewsAlerts
      }

      cur_groups << cg_info
    }

    def roles = []
    Role.findAll().each { role ->
      if (user.hasRole(role.authority))
        roles.add([id: role.id, authority: role.authority])
    }

    def links = [
      'self'  : ['href': base + '/profile'],
      'update': ['href': base + '/profile'],
      'delete': ['href': base + '/profile']
    ]

    Map result = [
      data: [
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        email: user.email,
        curatoryGroups: cur_groups,
        enabled: user.enabled,
        accountExpired: user.accountExpired,
        accountLocked: user.accountLocked,
        passwordExpired: user.accountExpired,
        defaultPageSize: user.defaultPageSize,
        roles: roles,
        _links: links
    ]]

    render result as JSON
  }

  @Transactional
  def update() {
    Map result = [:]
    User user = User.get(springSecurityService.principal.id)
    JSONObject reqData = request.JSON

    result = userProfileService.update(user, reqData, params, user)
    render result as JSON
  }

  @Secured(value = ['ROLE_USER', 'IS_AUTHENTICATED_FULLY'], httpMethod = 'PATCH')
  @Transactional
  def patch() {
    Map result = [:]
    JSONObject reqData = request.JSON
    User user = User.get(springSecurityService.principal.id)

    if (reqData.new_password && reqData.password) {
      if (passwordEncoder.matches(reqData.password, user.password)) {
        user.password = reqData.new_password
        user.save(flush: true, failOnError: true)
      } else {
        response.status = 400
        result.errors = [password: [message: "wrong password - profile unchanged", code: null]]
        render result as JSON
        return
      }
    }
    reqData.remove('new_password')
    reqData.remove('password')
    result = userProfileService.update(user, reqData, params, user)

    if (result.errors) {
      response.status = 400
    }

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
    Map result = [:]
    int max = params.limit ? params.int('limit') : 10
    int offset = params.offset ? params.int('offset') : 0
    boolean showFinished = params.boolean('showFinished') ?: false
    User user = User.get(springSecurityService.principal.id)
    Map errors = [:]

    if (params.boolean('archived') == true || params.boolean('combined') == true) {
      params.user = user.id
      result.data = []

      Map finished_results = jobResultService.fetchJobs(params, max, offset)

      if (params.boolean('combined') == true) {
        Map active_jobs = concurrencyManagerService.getUserJobs(user.id, max, offset, false)

        int combined_total += finished_results._pagination.total + active_jobs._pagination.total

        if (offset == 0) {
          result.data = active_jobs.data + finished_results.data
        }
        else {
          result.data = finished_results.data
        }

        result['_pagination'] = [
          offset: offset,
          limit: max,
          total: combined_total
        ]
      }
      else {
        result = finished_results
      }
    }
    else {
      result = concurrencyManagerService.getUserJobs(user.id, max, offset, showFinished)
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def cleanupJobs() {
    Map result = [:]
    int max = params.limit ? params.int('limit') : 10
    int offset = params.offset ? params.int('offset') : 0
    String base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = User.get(springSecurityService.principal.id)
    List jobs = concurrencyManagerService.getUserJobs(user.id as int, max, offset)

    jobs.each { k, v ->
      if (v.endTime || v.cancelled) {
        Job j = concurrencyManagerService.getJob(v.id, true)
        log.debug("Removed job ${v.id}")
      }
    }

    render result as JSON
  }
}
