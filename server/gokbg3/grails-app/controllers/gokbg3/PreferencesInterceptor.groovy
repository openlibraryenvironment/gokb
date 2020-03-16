package gokbg3


class PreferencesInterceptor {

  public PreferencesInterceptor() {
    match(controller: 'admin')
    match(controller: 'component')
    match(controller: 'coreference')
    match(controller: 'create')
    match(controller: 'decisionSupport')
    match(controller: 'file')
    match(controller: 'folderUpload')
    match(controller: 'fwk')
    match(controller: 'globalSearch')
    match(controller: 'group')
    match(controller: 'home')
    match(controller: 'ingest')
    match(controller: 'masterList')
    match(controller: 'packages')
    match(controller: 'resource')
    match(controller: 'savedItems')
    match(controller: 'search')
    match(controller: 'security')
    match(controller: 'titleList')
    match(controller: 'upload')
    match(controller: 'user')
    match(controller: 'workflow')
  }

  boolean before() {

	log.debug("PreferencesInterceptor::before(${params}) ${request.getRequestURL()}")

    if ( session.sessionPreferences == null ) {
      log.debug("setting session preferences....${grailsApplication.config.appDefaultPrefs ? 'present' : 'Not present'}");
      session.sessionPreferences = grailsApplication.config.appDefaultPrefs
    }
    else {
    }

    true 
  }

  boolean after() {
    true 
  }

  void afterView() {
    // no-op
  }
}
