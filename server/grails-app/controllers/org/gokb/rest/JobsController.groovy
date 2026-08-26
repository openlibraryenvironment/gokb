package org.gokb.rest

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.JobResult
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.User
import grails.plugin.springsecurity.annotation.Secured
import java.util.concurrent.CancellationException
import java.time.LocalDate

class JobsController {

  static namespace = 'rest'

  def springSecurityService
  def jobResultService
  ConcurrencyManagerService concurrencyManagerService

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def index() {
    Map result = [result:'OK', errors: [:]]
    int max = params.limit ? params.int('limit') : 10
    int offset = params.offset ? params.int('offset') : 0
    User user = User.get(springSecurityService.principal.id)
    boolean showFinished = params.boolean('showFinished') ?: false

    if (!user.isAdmin()) {
      if (params.user && user.id != params.long('user')) {
        result.result = 'ERROR'
        response.status = 403
        result.message = "Insuffictient permissions to retrieve jobs for these filters!"

        result.errors['user'] = [message: 'Unable to retrieve results filtered by another user!']
      }
      if (params.curatoryGroup && !user.curatoryGroups?.find { it.id == params.int('curatoryGroup') }) {
        result.result = 'ERROR'
        response.status = 403
        result.message = "Insuffictient permissions to retrieve jobs for these filters!"

        result.errors['curatoryGroup'] = [message: 'Unable to retrieve results filtered by a group you are not a part of!']
      }
      if (!params.user && !params.curatoryGroups && !params.linkedItem) {
        result.result = 'ERROR'
        response.status = 403
        result.message = "Insuffictient permissions to retrieve jobs for these filters!"
      }

      render result as JSON
      return
    }

    if ((params.user || params.curatoryGroup || params.linkedItem)) {
      if (params.boolean('archived') == true) {
        result = jobResultService.fetchJobs(params)
      }
      else if (params.boolean('combined') == true) {
        Map active_jobs = [:]

        if (params.user) {
          active_jobs = concurrencyManagerService.getUserJobs(params.long('user'), max, offset, false)
        }
        else if (params.curatoryGroup) {
          active_jobs = concurrencyManagerService.getGroupJobs(params.long('curatoryGroup'), max, offset, false)
        }
        else if (params.linkedItem) {
          active_jobs = concurrencyManagerService.getComponentJobs(params.long('linkedItem') ?: KBComponent.findByUuid(params.linkedItem), max, offset, false)
        }

        result.data = []

        int hqlTotal = active_jobs._pagination.total

        if (offset == 0) {
          result.data = active_jobs.data
          max = max - active_jobs
        }

        Map jr_result = jobResultService.fetchJobs(params, max, offset)

        hqlTotal += jr_result._pagination.total
        result.data = result.data + jr_result.data

        result['_pagination'] = [
          offset: params.int('offset') ?: 0,
          limit: params.int('limit') ?: 10,
          total: hqlTotal
        ]
      }
      else {
        if (params.user) {
          result = concurrencyManagerService.getUserJobs(params.long('user'), max, offset, false)
        }
        else if (params.curatoryGroup) {
          result = concurrencyManagerService.getGroupJobs(params.long('curatoryGroup'), max, offset, false)
        }
        else if (params.linkedItem) {
          result = concurrencyManagerService.getComponentJobs(params.long('linkedItem') ?: KBComponent.findByUuid(params.linkedItem), max, offset, false)
        }
      }
    }
    else {
      if (params.boolean('archived') == true) {
        result = jobResultService.fetchJobs(params, max, offset)
      }
      else {
        result = concurrencyManagerService.getFilteredJobs(null, null, max, offset, showFinished)
      }
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def show() {
    def result = [:]
    User user = User.get(springSecurityService.principal.id)
    boolean onlyArchived = params.boolean('archived') ?: false
    Job job = concurrencyManagerService?.getJob(params.id)
    JobResult jobResult = JobResult.findByUuid(params.id)

    if (job && !onlyArchived) {
      log.debug("${job}")

      if (user.isAdmin() || (job.ownerId && job.ownerId.toLong() == user.id) || job.linkedItem) {
        result.description = job.description
        result.type = job.type ? [id: job.type.id, name: job.type.value, value: job.type.value] : null
        result.startTime = job.startTime
        result.messages = job.messages
        result.progress = job.progress
        result.linkedItem = job.linkedItem
        result.begun = job.begun

        if (job.isDone()) {
          result.finished = true
          result.endTime = job.endTime
          try {
            result.job_result = job.get()
            result.status = result.job_result?.result
          }
          catch (CancellationException ce) {
            result.cancelled = true
            result.status = 'CANCELLED'
          }
        }
        else {
          result.finished = false
          result.status = job.begun ? 'RUNNING' : 'WAITING'
        }
      }
      else {
        result.result = "ERROR"
        response.status = 403
        result.message = "No permission to view job with ID ${params.id}."
      }
    }
    else if (onlyArchived && jobResult) {
      if (user.isAdmin() || jobResult.linkedItemId) {
        def linkedComponent = jobResult.linkedItemId ? KBComponent.get(jobResult.linkedItemId) : null

        result.uuid = jobResult.uuid
        result.description = jobResult.description
        result.type = jobResult.type ? [id: jobResult.type.id, name: jobResult.type.value, value: jobResult.type.value] : null
        result.startTime = jobResult.startTime
        result.endTime = jobResult.endTime
        result.status = jobResult.statusText
        result.finished = true
        result.linkedItem = linkedComponent ? [id: linkedComponent.id, type: linkedComponent.niceName, uuid: linkedComponent.uuid, name: linkedComponent.name] : null
        result.job_result = jobResult.resultJson
      }
      else {
        result.result = "ERROR"
        response.status = 403
        result.message = "No permission to view job with ID ${params.id}."
      }
    }
    else {
      result.result = "ERROR"
      response.status = 404
      result.message = "Could not find job with ID ${params.id}."
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def cancel() {
    def result = [result: 'OK']
    Job job = concurrencyManagerService.getJob(params.id)
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
    Job job = concurrencyManagerService.getJob(params.id)
    User user = User.get(springSecurityService.principal.id)

    if (job) {
      if (user.superUserStatus || job.ownerId == user.id) {
        if (job.isDone()) {
          def removed = concurrencyManagerService.getJob(params.id, true)
          log.debug("Removed job with id ${removed.uuid}")
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
