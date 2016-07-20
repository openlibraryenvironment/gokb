package org.gokb

import org.springframework.security.access.annotation.Secured;
import grails.util.GrailsNameUtils
import org.gokb.cred.*
import org.hibernate.SessionFactory;
import org.hibernate.transform.AliasToEntityMapResultTransformer

class DecisionSupportController {

  def grailsApplication
  def springSecurityService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = [:]
    log.debug("DecisionSupportController::index");
    result
  }


  def untwo() {
    log.debug("untwo");
  }

}
