package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.ReviewRequest
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

  @Secured(['IS_AUTHENTICATED_FULLY'])
  def index() {
    def curGroups = CuratoryGroup.findAll()

    String sortField = null, sortOrder = null
    if (params._sort) {
      sortField = params._sort
    }
    if (params._order) {
      sortOrder = params._order.toLowerCase()
    }

    if (sortField) {
      curGroups = curGroups.toSorted { a, b ->
        if (sortOrder == "desc")
          b[sortField].toString().toLowerCase() <=> a[sortField].toString().toLowerCase()
        else
          a[sortField].toString().toLowerCase() <=> b[sortField].toString().toLowerCase()
      }
    }
    def result = [data: []]
    curGroups.each { group ->
      result.data += restMappingService.mapObjectToJson(group, params, null)
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def curGroup = null
    def base = grailsApplication.config.serverURL + "/rest"

    if (params.oid || params.id) {
      curGroup = CuratoryGroup.findByUuid(params.id)

      if (!curGroup && params.oid) {
        curGroup = genericOIDService.resolveOID(params.oid)
      }
      if (!curGroup && params.long('id')) {
        curGroup = CuratoryGroup.get(params.long('id'))
      }

      if (curGroup) {
        result.data = restMappingService.mapObjectToJson(curGroup, params, null)
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

  @Secured("hasAnyRole('ROLE_CONTRIBUTOR', 'ROLE_EDITOR', 'ROLE_ADMIN') and isAuthenticated()")
  def getReviews() {
    def result = [:]
    def max = params.limit ? params.long('limit') : 10
    def offset = params.offset ? params.long('offset') : 0
    def base = grailsApplication.config.serverURL + "/rest"
    def sort = params.sort ?: null
    def order = params.order ?: null
    def group = CuratoryGroup.get(params.id)
    User user = User.get(springSecurityService.principal.id)
    def errors = [:]

    if (group) {
      def qry = "where exists (select arg from AllocatedReviewGroup arg where arg.group = :group and arg.review = rr)"
      def qryParams = [group:group]
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
}
