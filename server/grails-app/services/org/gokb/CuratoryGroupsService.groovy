package org.gokb

import gokbg3.RestMappingService
import grails.core.GrailsApplication
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.User

class CuratoryGroupsService {

  static namespace = 'rest'

  RestMappingService restMappingService
  GrailsApplication grailsApplication



  Map restLookup(User user, Map params) {
    Map result = [data: [], errors: [:]]

    RefdataValue status_filter = RefdataCategory.lookup('KBComponent.Status', 'Current')

    log.debug("###### " + params)

    if (params.status) {

      RefdataValue status = RefdataCategory.lookup('KBComponent.Status', params.status)
        // params.status can be a Number but instance of String
      if (!status) {
        try {
          int statusId = Integer.parseInt(params.status)
          RefdataCategory cat = RefdataCategory.findByLabel('KBComponent.Status')
          status = RefdataValue.get(params.status)

        } catch (NumberFormatException nfe) {
          log.warn(nfe)
        }
      }

      if (status) {
        status_filter = status
      }
    }

    String name_filter = null
    if (params.curatoryGroupName) {
      name_filter = params.curatoryGroupName.toString().trim()
    }

    List<CuratoryGroup> curGroups = null

    if (name_filter) {
      curGroups = CuratoryGroup.findAllByNameIlikeAndStatus(name_filter, status_filter)
    }
    else {
      curGroups = CuratoryGroup.findAllByStatus(status_filter)
    }

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
