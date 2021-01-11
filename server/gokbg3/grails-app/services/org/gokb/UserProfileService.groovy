package org.gokb


import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import org.gokb.cred.*
import org.gokb.refine.RefineProject
import org.springframework.beans.factory.annotation.Autowired

@Transactional
class UserProfileService {

  @Autowired
  GrailsApplication grailsApplication

  def delete(User user_to_delete) {
    def result = [:]
    log.debug("Deleting user ${user_to_delete.id} ..")
    def del_user = User.findByUsername('deleted')

    if (user_to_delete && del_user) {

      log.debug("Replacing links to user with placeholder ..")
      ReviewRequest.executeUpdate("update ReviewRequest set raisedBy = :del where raisedBy = :utd", [utd: user_to_delete, del: del_user])
      ReviewRequest.executeUpdate("update ReviewRequest set closedBy = :del where closedBy = :utd", [utd: user_to_delete, del: del_user])
      ReviewRequest.executeUpdate("update ReviewRequest set reviewedBy = :del where reviewedBy = :utd", [utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set createdBy = :del where createdBy = :utd", [utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set modifiedBy = :del where modifiedBy = :utd", [utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set lastCheckedOutBy = :del where lastCheckedOutBy = :utd", [utd: user_to_delete, del: del_user])
      Folder.executeUpdate("update Folder set owner = :del where owner = :utd", [utd: user_to_delete, del: del_user])
      CuratoryGroup.executeUpdate("update CuratoryGroup set owner = :del where owner = :utd", [utd: user_to_delete, del: del_user])
      Note.executeUpdate("update Note set creator = :del where creator = :utd", [utd: user_to_delete, del: del_user])
      KBComponent.executeUpdate("update KBComponent set lastUpdatedBy = :del where lastUpdatedBy = :utd", [utd: user_to_delete, del: del_user])
      UserOrganisation.executeUpdate("update UserOrganisation set owner = :del where owner = :utd", [utd: user_to_delete, del: del_user])

      log.debug("Setting links to null ..")
      ReviewRequest.executeUpdate("update ReviewRequest set allocatedTo = null where allocatedTo = :utd", [utd: user_to_delete])
      WebHookEndpoint.executeUpdate("update WebHookEndpoint set owner = null where owner = :utd", [utd: user_to_delete])
      Package.executeUpdate("update Package set userListVerifier = null where userListVerifier = :utd", [utd: user_to_delete])

      log.debug("Deleting dependent entities ..")
      DSAppliedCriterion.executeUpdate("delete from DSAppliedCriterion where user = :utd", [utd: user_to_delete])
      ComponentLike.executeUpdate("delete from ComponentLike where user = :utd", [utd: user_to_delete])
      History.executeUpdate("delete from History where owner = :utd", [utd: user_to_delete])
      UserOrganisationMembership.executeUpdate("delete from UserOrganisationMembership where party = :utd", [utd: user_to_delete])
      SavedSearch.executeUpdate("delete from SavedSearch where owner = :utd", [utd: user_to_delete])
      UserRole.removeAll(user_to_delete)

      log.debug("Deleting user object ..")
      user_to_delete.delete(flush: true, failOnError: true)

      log.debug("Done")
      result.result = "OK"
    } else {
      log.error("Could not find either the user object for deletion (${params.id}) or the placeholder user")
      result.result = "ERROR"
      result.errors = user_to_delete?.errors
    }
    return result
  }

  def update(User user, def data, params = [:], User adminUser) {
    def result = [:]
    def errors = []
    log.debug("Updating user ${user.id} ..")
    def immutables = ['id', 'username', 'last_alert_check']
    def adminAttributes = ['roleIds', 'password', 'curatoryGroupIds', 'enabled', 'accountExpired', 'accountLocked', 'passwordExpired', 'last_alert_check']

    if (!adminUser.isAdmin() && user != adminUser) {
      errors << [user: [message: "user $adminUser.username is not allowed to change properties of user $user.username",
                        baddata: user.username, code: null]]
    }
    data.each { field, value ->
      if (field in immutables && (user[field] != value)) {
        errors << ["${field}": [message: "property is immutable!",
                            baddata: value, code: null]]
      }
      if (field in adminAttributes && !adminUser.isAdmin()) {
        errors << [user: [message: "user $adminUser.username is not allowed to change property $field of user $user.username",
                          baddata: field, code: null]]
      }
    }
    if (errors.size() > 0) {
      result.errors = errors
      return result
    }
    return modifyUser(user, data)
  }

  def create(def data) {
    User user = new User()
    Role roleUser = Role.findByAuthority("ROLE_USER")
    if (!data.roleIds)
      data.roleIds = []
    data.roleIds << roleUser.id
    return modifyUser(user, data)
  }

  def modifyUser(User user, Map data) {
    boolean newUser = user.username == null
    def result = [:]
    def errors = []
    Set<Role> newRoles = []
    // apply changes
    data.each { field, value ->
      if (field != "roleIds" && field != "curatoryGroupIds" && !user.hasProperty(field)) {
        log.error("property user.$field is unknown!")
        errors << ["${field}": [message: "unknown property", baddata: field, code: null]]
      } else {
        if (field == "roleIds") {
          // change roles
          // scan data
          value.each { roleId ->
            Role newRole = Role.findById(roleId)
            if (newRole) {
              newRoles.add(newRole)
            } else {
              log.error("Role ID $roleId not found!")
              errors << [role: [message: "role ID is unknown", baddata: roleId, code: null]]
            }
          }
          // alter role set
          Set<Role> previousRoles = []
          if (!newUser) {
            UserRole.findAllByUser(user).each { ur ->
              previousRoles << ur.role
            }
            Role.findAll().each { role ->
              if (newRoles.contains(role)) {
                if (!previousRoles.contains(role)) {
                  UserRole.create(user, role, true)
                }
              } else if (previousRoles.contains(role)) {
                UserRole.remove(user, role, true)
              }
            }
          }
        } else if (field == "curatoryGroupIds") {
          // change cGroups
          Set<CuratoryGroup> curGroups = []
          value.each { cgId ->
            CuratoryGroup cg_obj = null
            if (cgId) {
              cg_obj = CuratoryGroup.findByUuid(cgId)
            }
            if (!cg_obj) {
              cg_obj = CuratoryGroup.findById(cgId)
            }
            if (cg_obj) {
              curGroups.add(cg_obj)
            } else {
              log.error("CuratoryGroup ID ${cgId} not found!")
              errors << [curatoryGroup: [message: "unknown CuratoryGroup ID", baddata: cgId, code: null]]
              result.errors = errors
              return result
            }
          }
          if (newUser) {
            user.curatoryGroups = curGroups
          } else {
            user.curatoryGroups.addAll(curGroups)
            user.curatoryGroups.retainAll(curGroups)
          }
        } else {
          user[field] = value
        }
      }
    }

    if (errors.size() == 0) {
      if (user.validate()) {
        user = user.merge(flush: true, failOnError: true)
        if (newUser) {
          // create & connect roles
          newRoles.each { role ->
            UserRole.create(user, role).save(flush: true)
          }
        }
        result.data = collectUserProps(user)
      } else {
        user.refresh()
        result.errors = user.errors.allErrors
      }
    } else {
      user.refresh()
      result.errors = errors
    }
    return result
  }

  def collectUserProps(User user, params = [:]) {
    def base = grailsApplication.config.serverURL + "/rest"
    def includes = [], excludes = [],
        newUserData = [
          'id'             : user.id,
          'username'       : user.username,
          'displayName'    : user.displayName,
          'email'          : user.email,
          'enabled'        : user.enabled,
          'accountExpired' : user.accountExpired,
          'accountLocked'  : user.accountLocked,
          'passwordExpired': user.passwordExpired,
          'status'         : user.enabled && !user.accountExpired && !user.accountLocked && !user.passwordExpired,
          'defaultPageSize': user.defaultPageSize
        ]
    if (params._embed?.split(',')?.contains('curatoryGroups'))
      newUserData.curatoryGroups = user.curatoryGroups
    else {
      newUserData.curatoryGroups = []
      user.curatoryGroups.each { group ->
        newUserData.curatoryGroups += [
          id    : group.id,
          name  : group.name,
          _links: [
            'self': [href: base + "/curatoryGroups/$group.id"]
          ]
        ]
      }
    }
    if (params._embed?.split(',')?.contains('roles'))
      newUserData.roles = user.authorities
    else {
      newUserData.roles = []
      user.authorities.each { role ->
        newUserData.roles += [
          id       : role.id,
          authority: role.authority,
          _links   : [
            'self': [href: base + "/roles/$role.id"]
          ]
        ]
      }
    }

    if (params._include)
      includes = params._include.split(',')
    if (params._exclude) {
      excludes = params._exclude.split(',')
      includes.each { prop ->
        excludes -= prop
      }
    }

    newUserData = newUserData.findAll { k, v ->
      (!excludes.contains(k) || (!includes.empty && includes.contains(k)))
    }

    newUserData._links = [
      self  : [href: base + "/users/$user.id"],
      update: [href: base + "/users/$user.id"],
      delete: [href: base + "/users/$user.id"]
    ]

    return newUserData
  }
}
