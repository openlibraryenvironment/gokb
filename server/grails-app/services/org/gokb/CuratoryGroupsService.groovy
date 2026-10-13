package org.gokb

import gokbg3.RestMappingService
import grails.core.GrailsApplication
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.RefdataCategory
import org.gokb.cred.User

class CuratoryGroupsService {

  static namespace = 'rest'

  RestMappingService restMappingService
  GrailsApplication grailsApplication



  Map restLookup(User user, Map params) {
    Map result = [data: [], errors: [:]]

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
    result = [data: []]
    curGroups.each { group ->
      result.data += restMappingService.mapObjectToJson(group, params, null)
    }



    return result
  }


}
