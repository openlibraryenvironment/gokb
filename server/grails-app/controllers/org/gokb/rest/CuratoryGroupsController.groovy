package org.gokb.rest

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.apache.commons.lang.StringUtils
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.CuratoryGroupType
import org.gokb.cred.JobResult
import org.gokb.cred.KBComponent
import org.gokb.cred.ReviewRequest
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Org
import org.gokb.cred.Role
import org.gokb.cred.User
import org.springframework.security.access.annotation.Secured
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor

@Transactional(readOnly = true)
class CuratoryGroupsController {

  static namespace = 'rest'

  def genericOIDService
  def restMappingService
  def springSecurityService
  def componentLookupService
  ConcurrencyManagerService concurrencyManagerService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def status_filter = RefdataCategory.lookup('KBComponent.Status', 'Current')

    if (params.status) {
      def status = RefdataCategory.lookup('KBComponent.Status', params.status)

      if (status) {
        status_filter = status
      }
    }

    def curGroups = CuratoryGroup.findAllByStatus(status_filter)

    String sortField = null, sortOrder = null
    if (params._sort) {
      sortField = params._sort
    }
    if (params._order) {
      sortOrder = params._order.toLowerCase()
    }

    if (sortField) {
      curGroups = curGroups.toSorted { a, b ->
        if (sortOrder == "desc"){
          b[sortField].toString().toLowerCase() <=> a[sortField].toString().toLowerCase()
        }
        else{
          a[sortField].toString().toLowerCase() <=> b[sortField].toString().toLowerCase()
        }
      }
    }
    def result = [data: []]
    curGroups.each { group ->
      result.data += restMappingService.mapObjectToJson(group, params, null)
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    def result = [:]
    def curGroup = null
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    if (params.oid || params.id) {
      curGroup = CuratoryGroup.findByUuid(params.id)

      if (!curGroup && params.oid) {
        curGroup = genericOIDService.resolveOID(params.oid)
      }
      if (!curGroup && params.long('id')) {
        curGroup = CuratoryGroup.get(params.long('id'))
      }

      if (curGroup) {
        result.data = restMappingService.mapObjectToJson(curGroup, params, user)
      } else {
        result.message = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      }
    } else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.code = 400
      result.message = 'No object id supplied!'
    }

    render result as JSON
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def save() {
    CuratoryGroup newGroup = null
    def result = [:]
    def errors = [:]
    def reqBody = request.JSON
    User user = User.get(springSecurityService.principal.id)

    if (reqBody?.name) {
      try {
        newGroup = new CuratoryGroup(name: reqBody.name)

        def jsonMap = [:]

        newGroup = restMappingService.updateObject(newGroup, jsonMap, reqBody)
      }
      catch (grails.validation.ValidationException ve) {
        errors = ve.errors
      }
    }
    else {
      errors = [result: 'ERROR', message:'Missing name for curatory group!', badData:[reqBody]]
    }

    if (!errors) {
      if ( newGroup.validate() ) {
        newGroup.save(flush: true)

        if (!errors) {
          response.setStatus(201)
          result = restMappingService.mapObjectToJson(source, params, user)
        }
        else {
          response.setStatus(400)
          result.errors = errors
          result.result = 'ERROR'
        }
      } else {
        result = [result: 'ERROR', message: "new curatory group data is not valid", errors: messageService.processValidationErrors(newGroup.errors)]
        response.setStatus(409)
        newGroup?.discard()
      }
    } else {
      response.setStatus(400)
      result.errors = errors
      result.result = 'ERROR'
    }
    render result as JSON
  }

  @Secured(['ROLE_EDITOR', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    CuratoryGroup group = CuratoryGroup.get(genericOIDService.oidToId(params.id))
    def result = [:]
    def errors = [:]
    def reqBody = request.JSON
    def remove = (request.method == 'PUT')
    User user = User.get(springSecurityService.principal.id)

    if (group) {
      boolean editable = user.hasRole('ROLE_ADMIN') || group.owner == user

      if (editable) {
        source = restMappingService.updateObject(group, null, reqBody)

        errors << updateMembers(group, reqBody, remove)

        if (!errors) {
          if ( group.validate() ) {
            source = group.merge(flush: true)
            result = restMappingService.mapObjectToJson(group, params, user)
          } else {
            result = [result: 'ERROR', message: "new group data is not valid", errors: messageService.processValidationErrors(group.errors)]
            response.setStatus(409)
            group?.discard()
          }
        } else {
          response.setStatus(400)
          result.errors = errors
          result.result = 'ERROR'
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing item to make changes!"
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Unable to lookup curatory group by id!"
    }
    render result as JSON
  }

  private updateMembers(group, reqBody, remove) {
    if (reqBody.members) {

    }
  }

  @Secured("hasAnyRole('ROLE_CONTRIBUTOR', 'ROLE_EDITOR', 'ROLE_ADMIN') and isAuthenticated()")
  def getReviews() {
    def result = [:]
    def max = params.limit ? params.long('limit') : 10
    def offset = params.offset ? params.long('offset') : 0
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    def sort = params.sort ?: null
    def order = params.order ?: null
    def group = CuratoryGroup.get(params.id)
    def inactive = RefdataCategory.lookupOrCreate('AllocatedReviewGroup.Status', 'Inactive')
    User user = User.get(springSecurityService.principal.id)
    def errors = [:]

    if (group) {
      def qry = "where exists (select arg from AllocatedReviewGroup arg" +
                              "where arg.group = :group and arg.review = rr and arc.status != :inactive)"
      def qryParams = [group:group, inactive:inactive]
      def sortQry = ""

      if (params.status) {
        def rdv = null

        if (params.status instanceof Integer) {
          def cat = RefdataCategory.findByLabel('ReviewRequest.Status')
          def val = RefdataValue.get(params.status)

          if (val && val in cat.values) {
            status = val
          }
        }
        else if (params.status instanceof String) {
          rdv = RefdataCategory.lookup('ReviewRequest.Status', params.status)
        }

        if (rdv) {
          status = rdv
          qry += " and rr.status = :status"
        }
        else {
          errors.status = [[message: "Illegal status value provided!", baddata: params.status]]
        }
      }

      if (sort) {
        if (ReviewRequest.hasProperty(sort)) {
          sortQry = " order by ${sort}"

          if (order.toLowerCase() == 'desc') {
            sortQry += " desc"
          }
        }
        else {
          errors.sort = [[message: "Unknown sort field ${sort}", baddata: sort]]
        }
      }

      if (errors.size() == 0) {
        def hqlTotal = ReviewRequest.executeQuery("select count(rr.id) from ReviewRequest as rr ${qry}".toString(), qryParams)[0]
        def rrResult = ReviewRequest.executeQuery("select rr from ReviewRequest as rr ${qry} ${sortQry}".toString(), qryParams, [max: max, offset: offset])

        result.data = []
        result['_pagination'] = [
          offset: offset,
          limit: max,
          total: hqlTotal
        ]

        result = componentLookupService.generateLinks(result, ReviewRequest, "/curatoryGroups/${params.id}/reviews", params, max, offset, hqlTotal)

        rrResult.each { rr ->
          def rrObj = restMappingService.mapObjectToJson(rr, params, user)
          rrObj.allocatedGroups = []

          rr.allocatedGroups?.each {
            def groupObj = [name: it.group.name, id: it.group.id]

            if (it.status) {
              groupObj << [status: [name:it.status?.value, id:it.status.id]]
            }
            else {
              groupObj << [status: null]
            }

            rrObj.allocatedGroups << groupObj
          }

          if (user.curatoryGroups?.contains(group)) {
            rrObj._links.update = ['href': base + "/reviews/${rrObj.id}"]
            rrObj._links.delete = ['href': base + "/reviews/${rrObj.id}"]
          }

          result.data << rrObj
        }
      }

      if (errors.size() > 0) {
        response.setStatus(400)
        result.errors = errors
      }
    }
    else {
      result.message = "Unable to lookup group for ID ${params.id}!"
      response.setStatus(404)
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_CONTRIBUTOR', 'ROLE_EDITOR', 'ROLE_ADMIN') and isAuthenticated()")
  def getJobs() {
    def result = [:]
    def max = params.limit ? params.int('limit') : 10
    def offset = params.offset ? params.int('offset') : 0
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    def sort = params._sort ?: null
    def order = params._order ?: null
    def group = CuratoryGroup.get(params.id)
    User user = User.get(springSecurityService.principal.id)
    def showFinished = params.boolean('showFinished') ?: false
    def errors = [:]

    if (group && (group.users.contains(user) || user.isAdmin())) {
      if (params.boolean('archived') == true || params.boolean('combined') == true) {
        result.data = []
        def hqlTotal = JobResult.executeQuery("select count(jr.id) from JobResult as jr where jr.groupId = ?0", [group.id])[0]
        def jobs = JobResult.executeQuery("from JobResult as jr where jr.groupId = ?0 order by jr.startTime desc", [group.id], [max: max, offset: offset])

        if (params.boolean('combined') == true) {
          def active_jobs = concurrencyManagerService.getGroupJobs(group.id, max, offset, false)

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
        result = concurrencyManagerService.getGroupJobs(group.id, max, offset, showFinished)
      }
    }

    render result as JSON
  }


  @Secured(value=["hasRole('ROLE_ADMIN')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def connectGroups() {
    def result = ['result':'ERROR',
                  'params': params]
    if (!params.superordinateId || !params.subordinateId){
      response.status = 422
      result.message = "Missing params. Requested parameters are 'superordinateId' and 'subordinateId'"
    }
    else if (params.superordinateId == params.subordinateId) {
      response.status = 422
      result.message = "A group cannot be set as its own superordinate."
    }
    else {
      CuratoryGroup superordinate = CuratoryGroup.get(genericOIDService.oidToId(params.superordinateId))
      CuratoryGroup subordinate = CuratoryGroup.get(genericOIDService.oidToId(params.subordinateId))

      if (!superordinate || !subordinate) {
        response.status = 404
        result.message = "CuratoryGroup combination not found for superordinate id ${params.superordinateId} and subordinate id ${params.subordinateId}."
      }
      else if (!subordinate.type || !superordinate.type) {
        response.status = 400
        result.message = "One or both of the provided groups do not have any type set."
      }
      else if (superordinate.type.level == CuratoryGroupType.Level.CENTRAL) {
        response.status = 409
        result.message = "Setting a central group as superordinate is not permissible."
      }
      else if (!(superordinate.type?.level > subordinate.type?.level)){
        response.status = 409
        result.message = "The given CuratoryGroups are not connectable in the requested way for hierarchic reasons."
      }
      else {
        try {
          subordinate.superordinatedGroup = superordinate
          subordinate.save(flush: true)
          result.result = 'OK'
          response.status = 200
          result.message = "Curatory Groups have been connected."
        }
        catch (Exception e) {
          e.printStackTrace()
          response.status = 500
          result.message = "Could not process request to connect CuratoryGroups."
        }
      }
    }
    render result as JSON
  }


  @Secured("hasRole('ROLE_ADMIN') and isAuthenticated()")
  @Transactional
  def createGroupType() {
    def result = [:]
    CuratoryGroupType.Level level
    String name
    try{
      level = params.level?.toUpperCase()
      if (StringUtils.isEmpty(params.name)){
        throw new Exception("Missing param name.")
      }
      name = params.name
    }
    catch (Exception e){
      result.result = 'ERROR'
      response.setStatus(500)
      result.message = "No CuratoryGroupType found for these params. ".concat(e.getMessage())
    }
    if (level && name){
      CuratoryGroupType cgt = new CuratoryGroupType(level:level, name:name)
      cgt.dump()
      result.result = 'OK'
      response.setStatus(200)
      result.message = "Created CuratoryGroupType ".concat(cgt.toString())
    }
    render result as JSON
  }
}
