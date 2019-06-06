package gokbg3


class PreferencesInterceptor {

  public PreferencesInterceptor() {
      matchAll().excludes(controller:'api')
                .excludes(controller: 'login')
                .excludes(controller: 'rest')
                .excludes(controller:'integration')
                .excludes(controller: 'oai')
                .excludes(controller: 'ajaxSupport')
      // matchAll().excludes(controller: 'auth')
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
