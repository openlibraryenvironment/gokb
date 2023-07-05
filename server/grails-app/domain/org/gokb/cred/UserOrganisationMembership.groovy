package org.gokb.cred

import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.*
import java.lang.reflect.Field
import javax.persistence.Transient
import org.hibernate.proxy.HibernateProxy

@Slf4j
class UserOrganisationMembership {

  static belongsTo = [ party: Party, memberOf: UserOrganisation ]

  Party party
  UserOrganisation memberOf
  RefdataValue status
  RefdataValue role


  static constraints = {
    party nullable:false, blank:false
    memberOf nullable:false, blank:false
    status nullable:true, blank:false
    role nullable:true, blank:false
  }

  static mapping = {
    party column: 'uom_party_fk'
    memberOf column: 'uom_userorg_fk'
    status column: 'uom_status_fk'
    role column: 'uom_role_fk'
  }

  public String toString() {
    return "${party.displayName} / ${memberOf.displayName}".toString()
  }

  public String getNiceName() {
    return "UserOrganisation";
  }



}
