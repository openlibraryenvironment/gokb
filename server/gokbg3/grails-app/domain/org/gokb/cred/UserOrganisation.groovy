package org.gokb.cred

import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.*
import java.lang.reflect.Field
import javax.persistence.Transient
import org.hibernate.proxy.HibernateProxy

@Slf4j
class UserOrganisation extends Party {

  
  User owner
  RefdataValue mission

  static constraints = {
    owner (nullable:true, blank:false)
  }

  static mapping = {
    owner: 'uo_owner_fk'
  }

  static hasMany = [
    members: UserOrganisationMembership
  ]

  static mappedBy = [
    members:'memberOf'
  ]

  public static final String restPath = "/userorgs"

  public String toString() {
    return displayName.toString()
  }

  public String getNiceName() {
    return "UserOrganisation";
  }

  def beforeInsert() {
    def user = springSecurityService?.currentUser
    this.owner = user
  }

  @Transient
  def getFolders() {
    Folder.executeQuery('select f from Folder as f where f.owner = :org',[org:this]);
  }
}
