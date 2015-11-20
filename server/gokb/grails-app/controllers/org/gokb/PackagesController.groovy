package org.gokb

import grails.converters.*
import org.springframework.security.acls.model.NotFoundException
import grails.plugins.springsecurity.Secured

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*

import grails.plugin.gson.converters.GSON


class PackagesController {

  def genericOIDService
  def springSecurityService

  def index() {
    def result = [:]
    // User user = springSecurityService.currentUser
    // For now, just get all the items owned by this user - eventually have a folder structure
    // result.saved_items = SavedSearch.executeQuery('Select ss from SavedSearch as ss where ss.owner = ?',[user]);
    log.debug("Packages::index ${params}.");
    if ( request.method=='PUT') {
      log.debug("Handling put")
    }
    result
  }
}
