package org.gokb

import grails.converters.*
import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*

class OaiController {

  def grailsApplication

  def index() { 
    def result = [:]

    grailsApplication.getArtefacts("Domain").each { dc ->
      def cfg = dc.clazz.declaredFields.find { it.name == 'oaiConfig' }
      if ( cfg != null ) {
        log.debug("....");
        result[dc.clazz.simpleName] = dc.clazz.oaiConfig
      }
    }

    render result as JSON
  }
}
