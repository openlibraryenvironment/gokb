package org.gokb.rest

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.Role
import org.gokb.cred.User
import grails.plugin.springsecurity.annotation.Secured
import java.util.concurrent.CancellationException

class JobsController {

  static namespace = 'rest'

  def springSecurityService
  ConcurrencyManagerService concurrencyManagerService

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def index() {
    def result = [:]
    def max = params.limit ? params.int('limit') : 10
    def offset = params.offset ? params.int('offset') : 0
    def base = grailsApplication.config.serverURL + "/rest"
    def sort = params._sort ?: null
    def order = params._order ?: null
    User user = User.get(springSecurityService.principal.id)
    def errors = [:]

    if (params.user) {
      if (user.superUserStatus || user.id == params.int('user')) {
        result = concurrencyManagerService.getUserJobs(params.int('user'), max, offset)
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "Insuffictient permissions to retrieve the jobs for this user"
      }
    }
    if (params.curatoryGroup) {
      if (user.superUserStatus || user.curatoryGroups?.find { it.id == params.int('curatoryGroup')}) {
        result = concurrencyManagerService.getGroupJobs(params.int('curatoryGroup'), max, offset)
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "Insuffictient permissions to retrieve the jobs for this group"
      }
    }
    else if (user.superUserStatus) {
      def rawJobs = concurrencyManagerService.getJobs()
      def selected = []

      rawJobs.each { k, v ->
        selected << [
          id: v.id,
          progress: v.progress,
          messages: v.messages,
          description: v.description,
          type: v.type,
          begun: v.begun,
          startTime: v.startTime,
          endTime: v.endTime,
          cancelled: v.isCancelled()
        ]
      }

      if (offset > 0) {
        selected = selected.drop(offset)
      }

      result.data = selected.take(max)

      result._pagination = [
        offset: offset,
        limit: max,
        total: selected.size()
      ]
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "Insuffictient permissions to retrieve all jobs"
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def show() {
    def result = [:]
    User user = User.get(springSecurityService.principal.id)
    Integer id = params.int('id')
    Job job = concurrencyManagerService?.jobs?.containsKey(id) ? concurrencyManagerService.jobs[id] : null

    if ( job ) {
      log.debug("${job}")

      if (user.superUserStatus || (job.ownerId && job.ownerId == user.id)) {
        result.description = job.description
        result.type = job.type
        result.startTime = job.startTime
        result.messages = job.messages
        result.progress = job.progress
        result.linkedItem = job.linkedItem
        result.begun = job.begun

        if ( job.isDone() ) {
          result.finished = true
          result.endTime = job.endTime
          try {
            result.job_result = job.get()
          }
          catch (CancellationException ce) {
            result.cancelled = true
          }
        }
        else {
          result.finished = false
        }
      }
      else {
        result.result = "ERROR"
        response.setStatus(403)
        result.message = "No permission to view job with ID ${id}."
      }
    }
    else {
      result.result = "ERROR"
      response.setStatus(404)
      result.message = "Could not find job with ID ${id}."
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def cancel() {
    def result = [result: 'OK']
    Job job = concurrencyManagerService?.jobs?.containsKey(params.id) ? concurrencyManagerService.jobs[params.id] : null
    User user = User.get(springSecurityService.principal.id)

    if (job) {
      if (user.superUserStatus || job.ownerId == user.id) {
        if (job.isDone()) {
          result.message = "This Job has already finished!"
        }
        else {
          job.cancel(true)
        }
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User is not authorized to cancel this job!"
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Unable to retrieve Job with ID ${params.id}"
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def delete() {
    def result = [result: 'OK']
    Job job = concurrencyManagerService?.jobs?.containsKey(id) ? concurrencyManagerService.jobs[id] : null
    User user = User.get(springSecurityService.principal.id)

    if (job) {
      if (user.superUserStatus || job.ownerId == user.id) {
        if (job.isDone()) {
          def removed = concurrencyManagerService.getJob(params.id)
          log.debug("Removed job with id ${removed.id}")
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          result.message = "This job is still running. Please cancel it first before removing it!"
        }
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User is not authorized to delete this job!"
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Unable to retrieve Job with ID ${params.id}"
    }

    render result as JSON
  }
}
