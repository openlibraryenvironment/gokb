package org.gokb

import org.springframework.security.access.annotation.Secured;
import grails.util.GrailsNameUtils

import org.gokb.cred.*
import org.hibernate.SessionFactory;
import org.hibernate.transform.AliasToEntityMapResultTransformer


class DecisionSupportController {

  def grailsApplication
  def springSecurityService

  SessionFactory sessionFactory

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
  }

}
