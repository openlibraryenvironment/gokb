package org.gokb

import org.gokb.cred.*
import grails.plugins.springsecurity.Secured

class IngestController {

  def concurrencyManagerService
  def genericOIDService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result=[:]
    result.existingProfiles = IngestionProfile.findAll()
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def addProfile() {
    log.debug("addProfile ${params}")

    def result=[:]

    def pkg_source = genericOIDService.resolveOID(params.sourceId)
    def package_type = genericOIDService.resolveOID(params.packageType)

    log.debug("Adding new profile, source=${pkg_source} ${pkg_source?.class.name}, type=${package_type}, packageName:${params.packageName}")

    if ( pkg_source != null &&
         package_type != null &&
         params.packageName != null ) {

      log.debug("Creating...1")

      def new_profile = new IngestionProfile(
        source:pkg_source,
        name:params.profileName,
        packageName:params.packageName,
        packageType:package_type,
        platformUrl:params.platformUrl
      )
      log.debug("Create2")
      log.debug("\n\nCreated ${new_profile} ${new_profile.packageName}- now save")

      new_profile.save(flush:true, failOnError:true)
    }
    else {
      log.debug("Missing source, type or package name")
    }

    redirect(action:'index')
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def profile() {
    log.debug("profile")
    def result = [:]
    result.ip = IngestionProfile.get(params.id);
    result
  }

}
