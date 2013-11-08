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
      log.debug(dc.clazz.simpleName);
      if ( 1==2 ) {
        result[dc.clazz.simpleName] = [:]
      }
    }

    render result as JSON
  }
}
