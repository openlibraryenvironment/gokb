package org.gokb

import grails.converters.JSON
import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.*

class CreateController {

  def index() { 
    log.debug("Create... ${params}");
    def result=[:]

    // Create a new empty instance of the object to create
    result.newclassname=params.tmpl
    if ( params.tmpl ) {
      def newclass = grailsApplication.getArtefact("Domain",result.newclassname);
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
    if ( params.cls ) {
      def newclass = grailsApplication.getArtefact("Domain",params.cls)
      if ( newclass ) {
        try {
          result.newobj = newclass.newInstance()

          params.each { p ->
            log.debug("Consider ${p.key} -> ${p.value}");
            if ( newclass.hasPersistentProperty(p.key) ) {
              GrailsDomainClassProperty pdef = newclass.getPersistentProperty(p.key) 
              log.debug(pdef);
              if ( pdef.association ) {
                switch ( pdef.association-type ) {
                  case 'one-to-one':
                    log.debug("one-to-one");
                    break
                  default:
                    log.debug("unhandled association type");
                    break
                }
              }
              else {
                log.debug("Scalar property");
              }
            }
            else {
              log.debug("Persistent class has no property named ${p.key}");
            }
          }
        }
        catch ( Exception e ) {
          log.error("Problem",e);
        }
      }
    }
  }
}
