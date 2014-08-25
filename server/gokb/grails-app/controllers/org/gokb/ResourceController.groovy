package org.gokb

import grails.plugins.springsecurity.Secured
import org.gokb.cred.*
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils

class ResourceController {

  def genericOIDService
  def classExaminationService
  def springSecurityService
  def gokbAclService
  def aclUtilService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    User user = springSecurityService.currentUser

    def result = [:]

    if ( params.id ) {
      log.debug("Attempt to retrieve ${params.id} and find a global template for it");
      result.displayobj = genericOIDService.resolveOID(params.id)

      if ( result.displayobj ) {

        log.debug("Got object");

        def new_history_entry = new History(controller:params.controller,
            action:params.action,
            actionid:params.id,
            owner:user,
            title:"View ${result.displayobj.toString()}").save()

        log.debug("i2");

        result.displayobjclassname = result.displayobj.class.name
        result.__oid = "${result.displayobjclassname}:${result.displayobj.id}"
        result.displaytemplate = grailsApplication.config.globalDisplayTemplates[result.displayobjclassname]

        log.debug("i3");
        // Add any refdata property names for this class to the result.
        result.refdata_properties = classExaminationService.getRefdataPropertyNames(result.displayobjclassname)
        result.displayobjclassname_short = result.displayobj.class.simpleName

        log.debug("component test")

        result.isComponent = (result.displayobj instanceof KBComponent)
        
        log.debug("ACL");
        result.acl = gokbAclService.readAclSilently(result.displayobj)
      }
      else {
        log.debug("unable to resolve object");
      }
      log.debug("done")
    }
    result
  }
}
