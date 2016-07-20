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
    def dimension = params.dimension?:'Platform'

    result.matrix = calculateMatrix(dimension, params.q?:'%');

    result
  }


  def untwo() {
    log.debug("untwo");
  }

  private def calculateMatrix(dimension, q) {

    def qry = null;

    switch (dimension) {
      case 'Package':
        break;
      case 'Title':
        break;
      case 'Platform':
      default:
        qry = 'select p from Platform as p where p.name like :q';
        break;
    }

    KBComponent.executeQuery(qry,[q:q]);
  }

}
