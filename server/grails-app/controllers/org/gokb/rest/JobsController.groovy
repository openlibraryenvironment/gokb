package org.gokb.rest

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.JobResult
import org.gokb.cred.KBComponent
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
    def showFinished = params.boolean('showFinished') ?: false
    def errors = [:]
    if (params.keySet().intersect(['user', 'curatoryGroup', 'linkedItem']).size() == 1) {
      // by user
      if (params.user) {
        if (user.superUserStatus || user.id == params.int('user')) {
          long userId = params.long('user')

          if (params.boolean('archived') == true || params.boolean('combined') == true) {
            result.data = []
            def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.ownerId = ?1", [userId])[0]
            def jobs = JobResult.executeQuery("from JobResult as jr where jr.ownerId = ?1 order by jr.startTime desc", [userId], [max: max, offset: offset])

            if (params.boolean('combined') == true) {
              def active_jobs = concurrencyManagerService.getUserJobs(userId, max, offset, false)

              hqlTotal += active_jobs._pagination.total

              if (offset == 0) {
                result.data = active_jobs.data
              }
            }

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
            result = concurrencyManagerService.getUserJobs(userId, max, offset, showFinished)
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 403
          result.message = "Insuffictient permissions to retrieve the jobs for this user"
        }
      }
      // by curatoryGroup
      if (params.curatoryGroup) {
        if (user.superUserStatus || user.curatoryGroups?.find { it.id == params.int('curatoryGroup') }) {
          long groupId = params.long('curatoryGroup')

          if (params.boolean('archived') == true || params.boolean('combined') == true) {
            result.data = []
            def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.groupId = ?1", [groupId])[0]
            def jobs = JobResult.executeQuery("from JobResult as jr where jr.groupId = ?1 order by jr.startTime desc", [groupId], [max: max, offset: offset])

            if (params.boolean('combined') == true) {
              def active_jobs = concurrencyManagerService.getGroupJobs(groupId, max, offset, false)

              hqlTotal += active_jobs._pagination.total

              if (offset == 0) {
                result.data = active_jobs.data
              }
            }

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
            result = concurrencyManagerService.getGroupJobs(groupId, max, offset, showFinished)
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 403
          result.message = "Insuffictient permissions to retrieve the jobs for this group"
        }
      }
      // by linked Component
      if (params.linkedItem) {
        long compId = params.long('linkedItem')

        if (params.boolean('archived') == true || params.boolean('combined') == true) {
          result.data = []
          def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.linkedItemId = ?1", [compId])[0]
          def jobs = JobResult.executeQuery("from JobResult as jr where jr.linkedItemId = ?1 order by jr.startTime desc", [compId], [max: max, offset: offset])

          if (params.boolean('combined') == true) {
            def active_jobs = concurrencyManagerService.getComponentJobs(compId, max, offset, false)

            hqlTotal += active_jobs._pagination.total

            if (offset == 0) {
              result.data = active_jobs.data
            }
          }

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
          result = concurrencyManagerService.getComponentJobs(compId, max, offset, showFinished)
        }
      }
    }
    // all jobs
    else if (user.superUserStatus) {
      if (params.archived == "true") {
        result.data = []
        def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr")[0]
        def jobs = JobResult.executeQuery("from JobResult as jr order by jr.startTime desc", [], [max: max, offset: offset])

        jobs.each { JobResult j ->
          def component = j.linkedItemId ? KBComponent.get(j.linkedItemId) : null
          // No JsonObject for list view
          CuratoryGroup cg = CuratoryGroup.get(j.groupId)
          result.data << [
              group      : cg?[id:cg.id, name:cg.name, uuid: cg.uuid]:null,
              uuid       : j.uuid,
              description: j.description,
              type       : j.type ? [id: j.type.id, name: j.type.value, value: j.type.value] : null,
              linkedItem : (component ? [id: component.id, type: component.niceName, uuid: component.uuid, name: component.name] : null),
              startTime  : j.startTime,
              endTime    : j.endTime,
              status     : j.statusText
          ]
        }

        result['_pagination'] = [
            offset: offset,
            limit : max,
            total : hqlTotal
        ]
      }
      else {
        def rawJobs = concurrencyManagerService.getJobs()
        def selected = []

        rawJobs.each { k, v ->
          selected << [
              id         : v.uuid,
              progress   : v.progress,
              messages   : v.messages,
              description: v.description,
              type       : v.type ? [id: v.type.id, name: v.type.value, value: v.type.value] : null,
              begun      : v.begun,
              startTime  : v.startTime,
              linkedItem : v.linkedItem,
              endTime    : v.endTime,
              cancelled  : v.isCancelled()
          ]
        }

        if (offset > 0) {
          selected = selected.drop(offset)
        }

        result.data = selected.take(max)

        result._pagination = [
            offset: offset,
            limit : max,
            total : selected.size()
        ]
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "Insuffictient permissions to retrieve all jobs"
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
      if (user.superUserStatus || (job.ownerId && job.ownerId == user.id) || job.linkedItem) {
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
      if (user.superUserStatus || (jobResult.ownerId && jobResult.ownerId == user.id) || (jobResult.groupId && user.curatoryGroups.find { it.id == jobResult.groupId })) {
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

  public static def filterJobResults(String propName, def id, def max, def offset, Map result) {
    if (['ownerId', 'groupId', 'linkedItemId'].contains(propName)) {
      def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr." + propName + " = :val", [val: id.toLong()])[0]
      def jobs = JobResult.executeQuery("from JobResult as jr where jr." + propName + " = :val order by jr.startTime desc", [val: id.toLong()], [max: max, offset: offset])
      jobs.each { j ->
        def component = j.linkedItemId ? KBComponent.get(j.linkedItemId) : null
        // No JsonObject for list view
        CuratoryGroup cg = CuratoryGroup.get(j.groupId)
        result.data << [
            group      : cg ? [id: cg.id, name: cg.name, uuid: cg.uuid] : null,
            uuid       : j.uuid,
            description: j.description,
            type       : j.type ? [id: j.type.id, name: j.type.value, value: j.type.value] : null,
            linkedItem : (component ? [id: component.id, type: component.niceName, uuid: component.uuid, name: component.name] : null),
            startTime  : j.startTime,
            endTime    : j.endTime,
            status     : j.statusText
        ]
      }

      result['_pagination'] = [
          offset: offset,
          limit : max,
          total : hqlTotal
      ]
    }
    return result
  }
}
