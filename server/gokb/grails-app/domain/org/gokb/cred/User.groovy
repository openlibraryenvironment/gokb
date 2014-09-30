package org.gokb.cred

import grails.plugins.springsecurity.SpringSecurityService
import groovy.util.logging.Log4j;
import javax.persistence.Transient
import org.hibernate.proxy.HibernateProxy

@Log4j
class User {

  transient springSecurityService
  transient grailsApplication

  String username
  String password
  String displayName
  String email
  boolean enabled
  boolean accountExpired
  boolean accountLocked
  boolean passwordExpired
  Long defaultPageSize = new Long(10)
//  Set<Territory> territories

  RefdataValue showQuickView
  RefdataValue showInfoIcon
    
//  static hasMany = [
//    territories : Territory
//  ]

  static constraints = {
    username blank: false, unique: true
    password blank: false
    displayName blank: true, nullable:true
    showQuickView blank: true, nullable:true
    email blank: true, nullable:true
    defaultPageSize blank: true, nullable:true
  }

  static mapping = {
    password column: '`password`'
  }

  Set<Role> getAuthorities() {
    UserRole.findAllByUser(this).collect { it.role } as Set
  }
  
  transient boolean isAdmin() {
    Role adminRole = Role.findByAuthority("ROLE_ADMIN")
    
    if (adminRole != null) {
      return getAuthorities().contains(adminRole)
    } else {
      log.error( "Error loading admin role (ROLE_ADMIN)" )
    }
    
    adminRole.save()
    false
  } 

  def beforeInsert() {
    encodePassword()
    if ( displayName == null )
      displayName = username
  }

  def beforeUpdate() {
    if (isDirty('password')) {
      encodePassword()
    }
    if ( displayName == null )
      displayName = username
  }
  
  public boolean isCurrent() {
     equals(springSecurityService.currentUser)
  }
  
  public boolean isEditable(boolean default_to = true) {    
    // Users can edit themselves.
    return isCurrent() || User.isTypeEditable (default_to)
  }
  
  @Override
  public boolean equals(Object obj) {

    log.debug("USER::equals ${obj?.class.name} :: ${obj}")
    if ( obj != null ) {

      def o = obj
      
      if ( o instanceof HibernateProxy) {
        o = deproxy(o)
      }
      
      if ( o instanceof User ) {
        return getId() == obj.getId()
      }
    }

    // Return false if we get here.
    false
  }

  protected void encodePassword() {
    // log.debug("Encoding password: ${password} (This should be plaintext at this stage)")
    password = springSecurityService.encodePassword(password)
  }


  transient def getUserOptions() {
    def userOptions = [:]
    userOptions.availableSearches = grailsApplication.config.globalSearchTemplates.sort{ it.value.title }
    userOptions
  }
  
  transient def getUserPreferences() {
    def userPrefs = [:]
    if (showInfoIcon?.value) {
      userPrefs["showInfoIcon"] = showInfoIcon.value?.equalsIgnoreCase("Yes") ? true : false
    }
    
    if (showQuickView?.value) {
      userPrefs["showQuickView"] = showQuickView?.value?.equalsIgnoreCase("Yes") ? true : false
    }
    
    // Return the prefs.
    userPrefs
  }

  static def refdataFind(params) {
    def result = [];
    def ql = null;
    // ql = RefdataValue.findAllByValueIlikeOrDescriptionIlike("%${params.q}%","%${params.q}%",params)
    // ql = RefdataValue.findWhere("%${params.q}%","%${params.q}%",params)

    def query = "from User as u where lower(u.username) like ? or lower(u.displayName) like ? or lower(u.email) like ?"
    def query_params = ["%${params.q.toLowerCase()}%","%${params.q.toLowerCase()}%","%${params.q.toLowerCase()}%"]

    ql = User.findAll(query, query_params, params)

    if ( ql ) {
      ql.each { id ->
        result.add([id:"${id.class.name}:${id.id}",text:"${id.username} / ${id.displayName?:''}"])
      }
    }

    result
  }

  public String toString() {
    return "${username} / ${displayName?:'No display name'}".toString();
  }

  public String getNiceName() {
    return "User";
  }

}
