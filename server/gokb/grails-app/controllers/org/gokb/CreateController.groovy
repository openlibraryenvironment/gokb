package org.gokb

import grails.converters.JSON
import grails.plugins.springsecurity.Secured

class CreateController {

  def index() { 
    log.debug("Create... ${params}");
    def result=[:]

    // Create a new empty instance of the object to create
    def newclassname = params.tmpl
    if ( params.tmpl ) {
      def newclass = grailsApplication.getArtefact("Domain",newclassname);
      if ( newclass ) {
        try {
          result.displayobj = newclass.newInstance()
  
          if ( params.tmpl )
            result.displaytemplate = grailsApplication.config.globalDisplayTemplates[params.tmpl]
        }
        catch ( Exception e ) {
          log.error("Problem",e);
        }
      }
    }

    result
  }

  def process() {
    def result=['responseText':'OK']
    log.debug("create::process params - ${params}");
    log.debug("create::process request - ${request}");
    log.debug("method: ${request.method}");
    log.debug("json: ${request.JSON}");
    log.debug("format: ${request.format}");
    render result as JSON
  }
}
