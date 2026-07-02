package org.gokb.cred

import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.*

@Slf4j
class Party {

  transient springSecurityService
  transient grailsApplication

  String displayName

  // Timestamps
  Date dateCreated
  Date lastUpdated

  static constraints = {
    displayName blank: true, nullable:true
  }

}
