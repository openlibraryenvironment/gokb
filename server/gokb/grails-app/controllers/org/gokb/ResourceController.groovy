package org.gokb

import grails.plugins.springsecurity.Secured

class ResourceController {

  def genericOIDService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]

    if ( params.id ) {
      log.debug("Attempt to retrieve ${params.id} and find a global template for it");
      result.displayobj = genericOIDService.resolveOID(params.id)
      if ( result.displayobj ) {
        result.displayobjclassname = result.displayobj.class.name
        result.__oid = "${result.displayobjclassname}:${result.displayobj.id}"
        result.displaytemplate = grailsApplication.config.globalDisplayTemplates[result.displayobjclassname]
        log.debug("result of lookup: ${result}");
      }
      else {
        log.debug("unable to resolve object");
      }
    }

    result
  }
}
