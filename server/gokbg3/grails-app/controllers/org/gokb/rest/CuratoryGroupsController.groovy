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

  def componentLookupService
  def springSecurityService
  def restMappingService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def curGroups = CuratoryGroup.findAll()
    String sortField = params._sort
    String sortOrder = params._order?.toLowerCase()

    curGroups = curGroups.toSorted { a, b ->
      if (sortField) {
        if (sortOrder?.toLowerCase() == "desc")
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

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    def result = [:]
    def curGroup = null
    def base = grailsApplication.config.serverURL + "/rest"

    if (params.oid || params.id) {
      curGroup = CuratoryGroup.findByUuid(params.id)

      if (!curGroup) {
        curGroup = genericOIDService.resolveOID(params.id)
      }

      if (!curGroup && params.long('id')) {
        curGroup = Org.get(params.long('id'))
      }

      if (curGroup) {
        result = restMappingService.mapObjectToJson(curGroup, params, null)
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
