package org.gokb

import grails.converters.*
import org.springframework.security.acls.model.NotFoundException
import grails.plugins.springsecurity.Secured

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*

import grails.plugin.gson.converters.GSON

import org.springframework.web.multipart.MultipartHttpServletRequest


class PackagesController {

  def genericOIDService
  def springSecurityService

  def index() {
    def result = [:]
    // User user = springSecurityService.currentUser
    // For now, just get all the items owned by this user - eventually have a folder structure
    // result.saved_items = SavedSearch.executeQuery('Select ss from SavedSearch as ss where ss.owner = ?',[user]);
    log.debug("Packages::index ${params}.");
    result
  }

  def packageContent() {
    def result = [:]
    log.debug("packageContent::${params}")

    if ( request.method=='POST') {
      log.debug("Handling post")

      if ( request instanceof MultipartHttpServletRequest ) {
        log.debug("Multipart")
        def upload_mime_type = request.getFile("content")?.contentType  // getPart?
        def upload_filename = request.getFile("content")?.getOriginalFilename()
        if ( upload_mime_type && upload_filename ) {
          log.debug("Got file content")
        }
      }
    }
    result
  }
}
