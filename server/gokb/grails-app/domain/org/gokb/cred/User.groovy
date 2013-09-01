package org.gokb.cred

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
  
  static manyByCombo = [
    territories : Territory
  ]

  static constraints = {
    username blank: false, unique: true
    password blank: false
    displayName blank: true, nullable:true
    email blank: true, nullable:true
  }

  static mapping = {
    password column: '`password`'
  }

  Set<Role> getAuthorities() {
    UserRole.findAllByUser(this).collect { it.role } as Set
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

  protected void encodePassword() {
    log.debug("Encoding password: ${password} (This should be plaintext at this stage)")
    password = springSecurityService.encodePassword(password)
  }


  transient def getUserOptions() {
    def userOptions = [:]
    userOptions.availableSearches = grailsApplication.config.globalSearchTemplates.sort{ it.value.title }
    userOptions
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

}
