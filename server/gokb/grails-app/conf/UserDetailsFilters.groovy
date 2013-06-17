import org.gokb.cred.User

class UserDetailsFilters {

  def springSecurityService

  def filters = {
    setUsetFilter(controller:'*', action:'*') {
      before = {
        // if ( session.sessionPreferences == null ) {
        //   session.sessionPreferences = grailsApplication.config.appDefaultPrefs
        // }
        // else {
        // }
        if ( springSecurityService.principal instanceof String ) {
          log.debug("User is string: ${springSecurityService.principal}");
        }
        else if (springSecurityService.principal?.id != null ) {
          request.user = User.get(springSecurityService.principal.id)
        }
      }
    }
  }


}
