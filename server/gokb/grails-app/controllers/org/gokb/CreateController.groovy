package org.gokb

class CreateController {

  def index() { 
    log.debug("Create... ${params}");
    def result=[:]

    // Create a new empty instance of the object to create
    def newclassname = params.tmpl
    def newclass = grailsApplication.getArtefact("Domain",newclassname);
    try {
      result.displayobj = newclass.newInstance()

      if ( params.tmpl )
        result.displaytemplate = grailsApplication.config.globalDisplayTemplates[params.tmpl]
    }
    catch ( Exception e ) {
      log.error("Prolem",e);
    }

    result
  }
}
