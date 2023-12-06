package org.gokb.cred

import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.*

import java.lang.reflect.Field

import javax.persistence.Transient

import org.hibernate.proxy.HibernateProxy

@Slf4j
class Party {

  transient springSecurityService
  transient grailsApplication

  String displayName

  // Timestamps
  Date dateCreated
  Date lastUpdated

  static hasMany = [
    memberships: UserOrganisationMembership
  ]

  static mappedBy = [
    memberships: 'party'
  ]

  static constraints = {
    displayName blank: true, nullable:true
  }

}
