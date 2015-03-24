package org.gokb

import grails.plugins.springsecurity.Secured
import grails.util.GrailsNameUtils

import org.gokb.cred.*
import org.hibernate.SessionFactory;
import org.hibernate.transform.AliasToEntityMapResultTransformer

import org.gokb.cred.CuratoryGroup


class GroupController {

  def grailsApplication
  def springSecurityService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    def result = [:]
    if ( params.id ) {
      result.group = CuratoryGroup.get(params.id);
    }
    return result
  }
}
