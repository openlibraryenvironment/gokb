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

  def delete(User user) {
    def result = [:]
    log.debug("Deleting user ${user.id} ..")
    def user_to_delete = User.get(user.id)
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
    def immutables = ['id', 'username', 'password', 'last_alert_check']
    def adminAttributes = ['roles', 'curatoryGroups', 'enabled', 'accountExpired', 'accountLocked', 'passwordExpired', 'last_alert_check']

    if (!adminUser.isAdmin() && user != adminUser) {
      errors << [message: "$adminUser.username is not allowed to change $user.username",
                 baddata: $user.username]
      result.errors = errors
      response.setStatus(400)
      return result
    }
    // apply changes
    data.each { field, value ->
      if (field != "roles" && field != "curatoryGroups" && value && !user.hasProperty(field)) {
        errors << [message: "$field is unknown", baddata: field]
        result.errors = errors
        return result
      }
      if (immutables.contains(field) && value != user[field]) {
        errors << [message: "$field is immutable", baddata: field]
        result.errors = errors
        return result
      }
      if (adminAttributes.contains(field) && !adminUser.isAdmin()) {
        errors <<[message:"$adminUser.username is not allowed to change $field ", baddata: adminUser.username]
        result.errors = errors
        return result
      }
      if (field == "roles") {
        // change roles
        // scan data
        Set<Role> newRoles = new HashSet<Role>()
        value.each { role ->
          Role newRole = Role.findById(role.id)
          if (!newRole)
            newRole = Role.findByAuthority(role.authority)
          if (newRole) {
            newRoles.add(newRole)
          } else {
            errors << [message: "Role Authority $field is unknown", baddata: field]
            result.errors = errors
            return result
          }
        }
        // alter previous role set
        Set<Role> previousRoles = user.getAuthorities()
        Role.findAll().each { role ->
          if (newRoles.contains(role)) {
            if (!previousRoles.contains(role)) {
              UserRole.create(user, role, true)
            }
          } else if (previousRoles.contains(role)) {
            UserRole.remove(user, role, true)
          }
        }
      } else if (field == "curatoryGroups") {
        // change cGroups
        def curGroups = []
        value.each { cg ->
          CuratoryGroup cg_obj = null
          if (cg.uuid?.trim()) {
            cg_obj = CuratoryGroup.findByUuid(cg.uuid)
          }
          if (!cg_obj && cg.id) {
            cg_obj = cg.id instanceof String ? genericOIDService.resolveOID(cg.id) : CuratoryGroup.get(cg.id)
          }
          if (cg_obj) {
            curGroups.add(cg_obj)
          } else {
            log.debug("CuratoryGroup ${cg} not found!")
            errors<<[message: "unknown CuratoryGroup $cg", baddata: cg]
            result.errors = errors
            return result
          }
        }
        user.curatoryGroups.addAll(curGroups)
        user.curatoryGroups.retainAll(curGroups)
      } else {
        user[field] = value
      }
    }
    user.save(flush: true)
    result.data = collectUserProps(user, params)
    return result
  }

  def create(def data) {
    User user = new User()
    def result = [data  : [],
                  result: 'OK']
    def errors = []
    def skippedCG = false

    data.each { field, val ->
      if (val && user.hasProperty(field)) {
        // roles have to be treated separately, as they're not a user property
        if (field != 'curatoryGroups') {
          user."${field}" = val
        } else {
          def curGroups = []
          val.each { cg ->
            def cg_obj = null
            if (cg.uuid?.trim()) {
              cg_obj = CuratoryGroup.findByUuid(cg.uuid)
            }

            if (!cg_obj && cg.id) {
              cg_obj = cg.id instanceof String ? genericOIDService.resolveOID(cg.id) : CuratoryGroup.get(cg.id)
            }

            if (cg_obj) {
              curGroups.add(cg_obj)
            } else {
              log.debug("CuratoryGroup ${cg} not found!")
              errors << ['message': 'Could not find referenced curatory group!', 'baddata': cg]
            }
          }
          log.debug("New CuratoryGroups: ${curGroups}")

          if (errors.size() > 0) {
            result.message = "There have been errors updating the users curatory groups."
            result.errors = errors
            response.setStatus(400)
          } else {
            user.curatoryGroups.addAll(curGroups)
            user.curatoryGroups.retainAll(curGroups)
          }
        }
      }
      if (field == "roles") {
        // scan data
        Set<Role> newRoles = new HashSet<Role>()
        val.each { value ->
          Role newRole = Role.findById(value.id)
          if (!newRole)
            newRole = Role.findByAuthority(value.authority)
          if (newRole) {
            newRoles.add(newRole)
          } else {
            errors << ['message': 'Could not find referenced role!', 'baddata': value.authority]
            log.error("Role Autority '$value.authority' is unknown!")
          }
        }
        // alter previous role set
        Set<Role> previousRoles = user.getAuthorities()
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
    }

    if (errors.size() == 0) {
      if (user.validate()) {
        user.save(flush: true)
        result.message = "User profile sucessfully created."
        result.data = collectUserProps(user)
      } else {
        result.result = "ERROR"
        result.message = "There have been errors saving the user object."
        result.errors = user.errors
      }
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
