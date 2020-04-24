package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.CuratoryGroup
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
}
