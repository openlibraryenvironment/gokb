package org.gokb

import grails.plugins.springsecurity.Secured
import org.gokb.cred.*

class SecurityController {

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
      
      if (obj instanceof User) {
        User user = obj as User
        def roles = user.getAuthorities()
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
