package org.gokb

import grails.plugins.springsecurity.Secured
import org.gokb.cred.*

class SecurityController {
  
  def genericOIDService

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def index() {
  }
  
  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def roles () {

    def result = [:]
    if ( params.id ) {
      // Read in a user.
      log.debug("Attempt to retrieve ${params.id} and find associated roles.");
      def obj = genericOIDService.resolveOID(params.id)
      
      if (obj && obj instanceof User) {
        User user = obj as User
                
        // Current roles the user is a member of.
        def currentRoles = user.getAuthorities()
        
        // Build a list of roles and set current boolean to whether this user is a member or not. 
        result['currentRoles'] = [:] as Map<String, Boolean>
        
        // Go through all available roles.
        Role.all.each { Role role ->

          // Add to the available roles map.
          result['currentRoles'][role.authority] = currentRoles.contains(role)
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
