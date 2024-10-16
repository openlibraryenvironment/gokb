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
  ConcurrencyManagerService concurrencyManagerService

  @Secured("hasAnyRole('ROLE_USER') and isAuthenticated()")
  def index() {
    def result = [:]
    def max = params.limit ? params.int('limit') : 10
    def offset = params.offset ? params.int('offset') : 0
    User user = User.get(springSecurityService.principal.id)
    def showFinished = params.boolean('showFinished') ?: false

    if (params.keySet().intersect(['user', 'curatoryGroup', 'linkedItem']).size() == 1) {
      // by user
      if (params.user) {
        if (user.superUserStatus || user.id == params.int('user')) {
          long userId = params.long('user')

          if (params.boolean('archived') == true || params.boolean('combined') == true) {
            result.data = []
            def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.ownerId = ?0", [userId])[0]
            def jobs = JobResult.executeQuery("from JobResult as jr where jr.ownerId = ?0 order by jr.startTime desc", [userId], [max: max, offset: offset])

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
            def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.groupId = ?0", [groupId])[0]
            def jobs = JobResult.executeQuery("from JobResult as jr where jr.groupId = ?0 order by jr.startTime desc", [groupId], [max: max, offset: offset])

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
        Long compId = KBComponent.findByUuid(params.linkedItem)?.id ?: params.long('linkedItem')

        if (compId) {
          if (params.boolean('archived') == true || params.boolean('combined') == true) {
            result.data = []
            def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.linkedItemId = ?0", [compId])[0]
            def jobs = JobResult.executeQuery("from JobResult as jr where jr.linkedItemId = ?0 order by jr.startTime desc", [compId], [max: max, offset: offset])

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
        else {
          result.result = 'ERROR'
          response.status = 404
          result.messageCode = "job.fetch.error.linkedItem.notFound"
          result.message = "Unable to reference linked item id."
        }
      }
    }
    // all jobs
    else if (user.isAdmin()) {
      if (params.archived == "true") {

        result.data = []
        int hqlTotal
        JobResult[] jobs
        LocalDate dateFilter = null
        def date_qry = "jr.startTime > :date and jr.startTime < :nd"
        def qry_pars = [:]

        if (params.date) {
          try {
            dateFilter = LocalDate.parse(params.date)
          }
          catch (Exception e) {
            log.debug(e)
            result.result = 'ERROR'
            response.status = 400
            result.message = 'Unable to parse provided date parameter!'

            render result as JSON
          }
        }

        def base_qry = "from JobResult as jr"
        def count_qry = "select count(jr.id) from JobResult as jr"

        if (params.type == 'import') {
          qry_pars.jt = [RefdataCategory.lookup('Job.Type','KBARTIngest'), RefdataCategory.lookup('Job.Type','KBARTSourceIngest')]

          base_qry += " where type in (:jt)"
          count_qry += " where type in (:jt)"
        }
        else if (params.int('type')) {
          RefdataValue rdv_type = RefdataValue.get(params.int('type'))

          if (rdv_type) {
            if (rdv_type.owner = RefdataCategory.lookup('Job.Type')) {
              qry_pars.jt = rdv_type

              count_qry += " where type = :jt"
              base_qry += " where type = :jt"
            }
            else {
              result.result = 'ERROR'
              response.status = 400
              result.message = 'Reference value ${params.type} is not a job type!'
            }
          }
          else {
            result.result = 'ERROR'
            response.status = 400
            result.message = 'Unable to reference job type via ID ${params.type}!'
          }
        }

        if (result.result != 'ERROR') {
          if (dateFilter) {
            if (params.type) {
              base_qry += " and "
              count_qry += " and "
            }
            else {
              base_qry += " where "
              count_qry += " where "
            }

            base_qry += date_qry
            count_qry += date_qry
            qry_pars.date = java.sql.Date.valueOf(dateFilter)
            qry_pars.nd = java.sql.Date.valueOf(dateFilter.plusDays(1))
          }

          hqlTotal = JobResult.executeQuery(count_qry, qry_pars)[0]
          jobs = JobResult.executeQuery("${base_qry} order by jr.startTime desc".toString(), qry_pars, [max: max, offset: offset])

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
      }
      else {
        if (params.int('type')) {
          RefdataValue rdv_type = RefdataValue.get(params.int('type'))

          if (rdv_type) {
            if (rdv_type.owner = RefdataCategory.lookup('Job.Type')) {
              result = concurrencyManagerService.getJobsForType(rdv_type, max, offset, showFinished)
            }
            else {
              result.result = 'ERROR'
              response.status = 400
              result.message = 'Reference value ${params.type} is not a job type!'
            }
          }
          else {
            result.result = 'ERROR'
            response.status = 400
            result.message = 'Unable to reference job type via ID ${params.type}!'
          }
        }
        else {
          result
        }
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "Insufficient permissions to retrieve all jobs"
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
