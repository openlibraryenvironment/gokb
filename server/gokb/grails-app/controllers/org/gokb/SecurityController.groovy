package org.gokb

import org.gokb.cred.UserRole;


import grails.plugins.springsecurity.Secured
import org.gokb.cred.*

class SecurityController {
  
  def genericOIDService
  def springSecurityService

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def index() {
  }
  
  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def updateRole() {
    
    // The result.
    def result = [:]
    
    if ( params.id ) {
      
      log.debug("Attempt to update Roles ${params.roles} for user ${params.id}.");
      def obj = genericOIDService.resolveOID(params.id)
      
      // User is sent.
      if (obj && obj instanceof User) {
        
        // User.
        User user = obj as User
        
        if (springSecurityService.currentUser != user) {
        
          params.each { String k, v ->
            
            if ( v == null ) {
              // Log the message.
              log.error ("Value not specified for ${k}")
              
            } else {
            
              if (k.startsWith("role")) {
                
                // Attempt to update the role association.
                long roleNum = k.substring(4) as Long
                
                // Get the role.
                Role r = Role.get(roleNum)
                if (r) {
                  
                  // Load the UserRole pair.
                  UserRole ur = UserRole.get(user.id, r.id)
                  
                  // Set or unset.
                  boolean set = v != "false"
                  
                  // Now lookup.
                  if (set == true) {
                    
                    // Try and set.
                    if (ur) {
                      log.debug ("Not setting role as it's already set.")
                    } else {
                      // Add the pair.
                      UserRole.create(user, r, true)
                      log.debug("Added User with id ${user.id} to the Role ${r.authority}")
                    }
                    
                  } else {
                  
                    // Unset the UserRole.
                    if (!ur) {
                      log.debug ("Not removing role as it's already not set.")
                    } else {
                      // Add the pair.
                      ur.delete(flush:true)
                      log.debug("Removed User with id ${user.id} from the Role ${r.authority}")
                    }
                  }
                  
                } else {
                  log.error ("Could not find role with id ${roleNum}")
                }
              }
            }
          }
          
          // Redirect to the user screen.
          redirect(controller: "security", action: "roles", 'params' : [ 'id': "${user.class.name}:${user.id}" ])
        } else {
          log.error ("User ${user.id} attempted to mdoify their own roles.")
        }
      }
    }
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def roles () {

    def result = [:]
    if ( params.id ) {
      
      log.debug("Attempt to retrieve ${params.id} and find associated roles.");
      def obj = genericOIDService.resolveOID(params.id)
      
      if (obj && obj instanceof User) {
        User currentUser = springSecurityService.currentUser
        User user = obj as User
        result['d'] = user
        
        // Set editable flag. Must be admin user and also prevent editing of own perms.
        result["editable"] = currentUser.isAdmin() && currentUser != user
                
        // Current roles the user is a member of.
        def currentRoles = user.getAuthorities()
        
        // Build a list of roles and set current boolean to whether this user is a member or not. 
        result['currentRoles'] = [:] as Map<Role, Boolean>
        
        // Go through all available roles.
        Role.all.each { Role role ->

          // Add to the available roles map.
          result['currentRoles'][role] = currentRoles.contains(role)
        }
        
      } else {
        log.error ("Roles can only be viewed altered for a User.")
      }
    }
    
    result
  }
  
  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def perms () {
    
  }
}
