package org.gokb.cred

import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.Log4j;

import java.lang.reflect.Field

import javax.persistence.Transient

import org.hibernate.proxy.HibernateProxy

@Log4j
class User extends Party {

  transient springSecurityService
  transient grailsApplication

  // Used in user import to bypass password encoding - used to directly load hashes instead of password
  transient direct_password = false

  String username
  String password
  String email
  boolean enabled
  boolean accountExpired
  boolean accountLocked
  boolean passwordExpired
  Long defaultPageSize = new Long(10)
  Org org

  // When did the alerting system last check things on behalf of this user
  Date last_alert_check

  // seconds user wants between checks - System only checks daily, so values < 24*60*60 don't make much sense at the moment
  Long alert_check_frequency

  RefdataValue send_alert_emails
  RefdataValue showQuickView
  RefdataValue showInfoIcon
    
  static hasMany = [
    curatoryGroups : CuratoryGroup,

  ]
  
  static mappedBy = [curatoryGroups: "users"]

  static constraints = {
    username blank: false, unique: true
    password blank: false
    showQuickView blank: true, nullable:true
    email blank: true, nullable:true
    defaultPageSize blank: true, nullable:true
    curatoryGroups blank: true, nullable:true
    org blank: false, nullable:true
    last_alert_check blank: false, nullable:true
    alert_check_frequency blank: false, nullable:true
    send_alert_emails blank: false, nullable:true
  }

  static mapping = {
    password column: '`password`'
  }

  Set<Role> getAuthorities() {
    UserRole.findAllByUser(this).collect { it.role } as Set
  }
  
  public transient boolean hasRole (String roleName) {
    
    Role role = Role.findByAuthority("${roleName}")
    
    if (role != null) {
      return getAuthorities().contains(role)
    } else {
      log.error( "Error loading admin role (${role})" )
    }

    // Default to false.
    false
  }

  transient def getOwnedGroups() {
    UserOrganisation.executeQuery('select uo from UserOrganisation as uo where uo.owner = :owner',[owner:this])
  }

  /**
   *  Return a list of all folders this user has access to
   */
  transient def getFolderList() {

    def direct_ownership = Folder.executeQuery('select f from Folder as f where f.owner = :user',[user:this]);
    // This query finds all folders where the user is a direct member of the group
    def via_group = Folder.executeQuery('select f from Folder as f where f.owner in ( select uom.memberOf from UserOrganisationMembership as uom where uom.party = :user )',[user:this])

    def result = direct_ownership + via_group

    result.each {
      log.debug(it)
    }

    return result
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

    if ( direct_password ) {
    }
    else {
      encodePassword()
    }

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
        o = User.deproxy(o)
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
    
    // Use the available meta methods to get a list of all the properties against the user.
    // If they are of type refdata/and are set then we add here. If they are null then we should omit.
    def props = User.declaredFields.grep { !it.synthetic }
    for (Field p : props) {
      if (p.type == RefdataValue.class) {
        // Let's get the value.
        
        def val = this."${p.name}"
        if (val) {
          userPrefs["${p.name}"] = val.value?.equalsIgnoreCase("Yes") ? true : false
        }
      }
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


  transient static def tsv_dataload_config = [
    header:[
      defaultTargetClass:'org.gokb.cred.User',

      // Identify the different combinations that can be used to identify domain objects for the current row
      // Names columns in the import sheet - importer will map according to config and do the right thing
      targetObjectIdentificationHeuristics:[
        [
          ref:'role_user', cls:'org.gokb.Role',
          heuristics:[ [ type : 'hql', hql: 'select r from Role as r where r.authority=:user', values : [ user : [type:'static', value:'ROLE_USER'] ] ] ],
          creation:[ onMissing:false, ]
        ],
        [
          ref:'role_admin', cls:'org.gokb.Role',
          heuristics:[ [ type : 'hql', hql: 'select r from Role as r where r.authority=:user', values : [ user : [type:'static', value:'ROLE_ADMIN'] ] ] ],
          creation:[ onMissing:false, ]
        ],
        [
          ref:'role_contributor', cls:'org.gokb.Role',
          heuristics:[ [ type : 'hql', hql: 'select r from Role as r where r.authority=:user', values : [ user : [type:'static', value:'ROLE_CONTRIBUTOR'] ] ] ],
          creation:[ onMissing:false, ]
        ],
        [
          ref:'role_editor', cls:'org.gokb.Role',
          heuristics:[ [ type : 'hql', hql: 'select r from Role as r where r.authority=:user', values : [ user : [type:'static', value:'ROLE_EDITOR'] ] ] ],
          creation:[ onMissing:false, ]
        ],
        [
          ref:'role_api', cls:'org.gokb.Role',
          heuristics:[ [ type : 'hql', hql: 'select r from Role as r where r.authority=:user', values : [ user : [type:'static', value:'ROLE_API'] ] ] ],
          creation:[ onMissing:false, ]
        ],
        [
          ref:'role_refineuser', cls:'org.gokb.Role',
          heuristics:[ [ type : 'hql', hql: 'select r from Role as r where r.authority=:user', values : [ user : [type:'static', value:'ROLE_REFINEUSER'] ] ] ],
          creation:[ onMissing:false, ]
        ],
        [
          ref:'role_refinetester', cls:'org.gokb.Role',
          heuristics:[ [ type : 'hql', hql: 'select r from Role as r where r.authority=:user', values : [ user : [type:'static', value:'ROLE_REFINETESTER'] ] ] ],
          creation:[ onMissing:false, ]
        ],
      ],

      // Determine what this row can create (Referenced objects hanging off the primary User
      creationRules : [
        [
          whenPresent:[ [ type:'val', colname:'username', errorOnMissing:true] ],
          ref:'MainUserItem',
          cls:'org.gokb.cred.User',
          creation : [ 
            properties:[
              [ type:'val', property:'username', colname:'username' ],
              [ type:'val', property:'password', colname:'password' ],
              [ type:'val', property:'email', colname:'email' ],
              [ type:'val', property:'displayName', colname:'display_name' ],
              [ type:'valueClosure', property:'direct_password', closure: {  colmap, nl, locatedObjects -> true } ],
            ]
          ]
        ],
        [
          whenPresent:[[type:'val',colname:'admin_authority']], ref:'admin_ur', cls:'org.gokb.cred.UserRole', creation:[properties:[
            [ type:'ref', property:'user',refname:'MainUserItem' ] , [ type:'ref', property:'role',refname:'role_admin' ] ] ]
        ],
        [
          whenPresent:[[type:'val',colname:'user_authority']], ref:'user_ur', cls:'org.gokb.cred.UserRole', creation:[properties:[
            [ type:'ref', property:'user',refname:'MainUserItem' ] , [ type:'ref', property:'role',refname:'role_user' ] ] ]
        ],
        [
          whenPresent:[[type:'val',colname:'contributor_authority']], ref:'contrib_ur', cls:'org.gokb.cred.UserRole', creation:[properties:[
            [ type:'ref', property:'user',refname:'MainUserItem' ] , [ type:'ref', property:'role',refname:'role_contributor' ] ] ]
        ],
        [
          whenPresent:[[type:'val',colname:'api_authority']], ref:'api_ur', cls:'org.gokb.cred.UserRole', creation:[properties:[
            [ type:'ref', property:'user',refname:'MainUserItem' ] , [ type:'ref', property:'role',refname:'role_api' ] ] ]
        ],
        [
          whenPresent:[[type:'val',colname:'refine_user_authority']], ref:'refine_user_ur', cls:'org.gokb.cred.UserRole', creation:[properties:[
            [ type:'ref', property:'user',refname:'MainUserItem' ] , [ type:'ref', property:'role',refname:'role_refineuser' ] ] ]
        ],
        [
          whenPresent:[[type:'val',colname:'refine_tester_authority']], ref:'refine_tester_ur', cls:'org.gokb.cred.UserRole', creation:[properties:[
            [ type:'ref', property:'user',refname:'MainUserItem' ] , [ type:'ref', property:'role',refname:'role_refinetester' ] ] ]
        ],
        [
          whenPresent:[[type:'val',colname:'editor_authority']], ref:'editor_ur', cls:'org.gokb.cred.UserRole', creation:[properties:[
            [ type:'ref', property:'user',refname:'MainUserItem' ] , [ type:'ref', property:'role',refname:'role_editor' ] ] ]
        ],
      ],

      cols: [
        [colname:'username', desc:''],
        [colname:'password', desc:''],
        [colname:'email', desc:''],
        [colname:'display_name', desc:''],
      ]
    ]
  ]
  
}
