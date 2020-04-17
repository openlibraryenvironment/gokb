package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.Org
import org.gokb.cred.Role
import org.springframework.security.access.annotation.Secured

@Transactional(readOnly = true)
class RolesController {

  static namespace = 'rest'

  def genericOIDService
  def restMappingService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    Role[] roles = Role.findAll()
    String sortField = params._sort
    String sortOrder = params._order?.toLowerCase()
    if (sortField) {
      roles = roles.toSorted { a, b ->
        if (sortOrder?.toLowerCase() == "desc")
          b[sortField].toString().toLowerCase() <=> a[sortField].toString().toLowerCase()
        else
          a[sortField].toString().toLowerCase() <=> b[sortField].toString().toLowerCase()
      }
    }

    def result = [data: []]
    roles.each { role ->
      result.data += restMappingService.mapObjectToJson(role, params, null)
    }

    render result as JSON
  }
}