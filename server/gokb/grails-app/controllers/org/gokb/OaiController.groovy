package org.gokb

import grails.converters.*
import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*

class OaiController {

  def grailsApplication

  def index() { 
    def result = [:]

    log.debug("index (${params})");

    if ( params.id ) {
      grailsApplication.getArtefacts("Domain").find { dc ->
        def r = false
        def cfg = dc.clazz.declaredFields.find { it.name == 'oaiConfig' }
        if ( cfg ) {
          log.debug("has config");
         
          def o = dc.clazz.oaiConfig
          if ( o.id == params.id ) {
            result.oaiConfig = o
            r = true
          }
        }
        r
      }
  
      if ( result.oaiConfig ) {
        log.debug("Got config : ${result.oaiConfig}");
      }
    }

    render result as JSON
  }
}
