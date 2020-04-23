package org.gokb

import grails.gorm.transactions.Transactional
import org.gokb.cred.ComponentLike
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.DSAppliedCriterion
import org.gokb.cred.Folder
import org.gokb.cred.History
import org.gokb.cred.KBComponent
import org.gokb.cred.Note
import org.gokb.cred.Package
import org.gokb.cred.ReviewRequest
import org.gokb.cred.Role
import org.gokb.cred.SavedSearch
import org.gokb.cred.User
import org.gokb.cred.UserOrganisation
import org.gokb.cred.UserOrganisationMembership
import org.gokb.cred.UserRole
import org.gokb.cred.WebHookEndpoint
import org.gokb.refine.RefineProject

@Transactional
class UserProfileService {

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

  def update(User user, def data) {
    def result = ['result': 'OK']
    def immutables = ['id', 'username', 'enabled', 'accountExpired', 'accountLocked', 'passwordExpired', 'last_alert_check']
    def errors = []
    def skippedCG = false
    def reqBody = data

    if (reqBody && reqBody.id && reqBody.id == user.id) {
      reqBody.each { field, val ->
        if (val && user.hasProperty(field)) {
          // roles have to be treated separately, as they're not a user property
          if (field != 'curatoryGroups') {
            if (!immutables.contains(field)) {
              user."${field}" = val
            } else {
              log.debug("Ignoring immutable field ${field}")
            }
          } else {
            if (user.hasRole('ROLE_EDITOR') || user.hasRole('ROLE_ADMIN')) {
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
            } else {
              skippedCG = true
            }
          }
        } else if (field == "roles") {
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
          result.message = "User profile sucessfully updated."
        } else {
          result.result = "ERROR"
          result.message = "There have been errors saving the user object."
          result.errors = user.errors
        }
      }
    } else {
      log.debug("Missing update payload or wrong user id")
      result.result = "ERROR"
      response.setStatus(400)
      result.message = "Missing update payload or wrong user id!"
    }
    return result
  }

  def create(def data) {
    User user = new User()
    def result = ['result': 'OK']
    def errors = []
    def skippedCG = false
    def reqBody = data

    reqBody.each { field, val ->
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
        result.data = user
      } else {
        result.result = "ERROR"
        result.message = "There have been errors saving the user object."
        result.errors = user.errors
      }
    }
    return result
  }
}
