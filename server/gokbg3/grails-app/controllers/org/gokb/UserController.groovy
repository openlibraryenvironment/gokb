package org.gokb

import org.gokb.cred.*
import org.gokb.refine.RefineProject

class UserController extends grails.plugin.springsecurity.ui.UserController {
  def delete() {
    log.debug("Deleting user ${params.id} ..")
    def user_to_delete = User.get(params.id)
    def del_user = User.findByUsername('deleted')

    if ( user_to_delete && del_user ) {

      log.debug("Replacing links to user with placeholder ..")
      ReviewRequest.executeUpdate("update ReviewRequest set raisedBy = :del where raisedBy = :utd",[utd: user_to_delete, del: del_user])
      ReviewRequest.executeUpdate("update ReviewRequest set closedBy = :del where closedBy = :utd",[utd: user_to_delete, del: del_user])
      ReviewRequest.executeUpdate("update ReviewRequest set reviewedBy = :del where reviewedBy = :utd",[utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set createdBy = :del where createdBy = :utd",[utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set modifiedBy = :del where modifiedBy = :utd",[utd: user_to_delete, del: del_user])
      RefineProject.executeUpdate("update RefineProject set lastCheckedOutBy = :del where lastCheckedOutBy = :utd",[utd: user_to_delete, del: del_user])
      Folder.executeUpdate("update Folder set owner = :del where owner = :utd",[utd: user_to_delete, del: del_user])
      CuratoryGroup.executeUpdate("update CuratoryGroup set owner = :del where owner = :utd",[utd: user_to_delete, del: del_user])
      Note.executeUpdate("update Note set creator = :del where creator = :utd",[utd: user_to_delete, del: del_user])
      KBComponent.executeUpdate("update KBComponent set lastUpdatedBy = :del where lastUpdatedBy = :utd",[utd: user_to_delete, del: del_user])
      UserOrganisation.executeUpdate("update UserOrganisation set owner = :del where owner = :utd",[utd: user_to_delete, del: del_user])

      log.debug("Setting links to null ..")
      ReviewRequest.executeUpdate("update ReviewRequest set allocatedTo = null where allocatedTo = :utd",[utd: user_to_delete])
      WebHookEndpoint.executeUpdate("update WebHookEndpoint set owner = null where owner = :utd",[utd: user_to_delete])
      Package.executeUpdate("update Package set userListVerifier = null where userListVerifier = :utd",[utd: user_to_delete])

      log.debug("Deleting dependent entities ..")
      DSAppliedCriterion.executeUpdate("delete from DSAppliedCriterion where user = :utd",[utd: user_to_delete])
      ComponentLike.executeUpdate("delete from ComponentLike where user = :utd",[utd: user_to_delete])
      History.executeUpdate("delete from History where owner = :utd",[utd: user_to_delete])
      UserOrganisationMembership.executeUpdate("delete from UserOrganisationMembership where party = :utd",[utd: user_to_delete])
      SavedSearch.executeUpdate("delete from SavedSearch where owner = :utd",[utd: user_to_delete])
      UserRole.removeAll(user_to_delete)

      log.debug("Deleting user object ..")
      user_to_delete.delete(flush:true, failOnError:true)

      log.debug("Done")
    } else {
      log.error("Could not find either the user object for deletion (${params.id}) or the placeholder user")
    }

    redirect(controller: 'user', action:'search')
  }
}
