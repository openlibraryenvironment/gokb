class PreferencesFilters {
   
  def filters = {
    setPrefsFilter(controller:'*', action:'*') {
      before = {
        if ( session.sessionPreferences == null ) {
          session.sessionPreferences = grailsApplication.config.appDefaultPrefs
        }
        else {
        }
      }
    }
  }
}
