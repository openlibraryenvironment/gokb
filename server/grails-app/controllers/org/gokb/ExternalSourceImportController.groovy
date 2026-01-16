package org.gokb

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured

class ExternalSourceImportController {

  WekbAPIService wekbAPIService
  OrgService orgService

  def index() {
    //NO-OP
  }

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  def getPackageMetaData(){
    log.debug("getPackageMetaData query: ${params}")
    def noResults = ["":""]
    def uuid = params?.uuid

    if (uuid) {
      def result = wekbAPIService.getPackageByUuid(uuid)
      //log.debug("RESULT: " + result)
      if (result) {
        render result as JSON
        return
      }
    }

    render noResults as JSON
  }


  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  def getPlatformMetaData(){
    def noResults = ["":""]
    def uuid = params?.uuid

    if (uuid) {
      def result = wekbAPIService.getPlatformByUuid(uuid)
      //log.debug("RESULT : " + result)
      if (result) {
        render result as JSON
        return
      }
    }

    render noResults as JSON
  }

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  def getProviderData(){
    def noResults = ["":""]
    def uuid = params?.uuid
    log.debug("getProviderData : " + uuid)

    if (uuid) {
      def result = wekbAPIService.checkIfProviderExists(uuid)
      //log.debug("RESULT : " + result)
      if (result) {
        render result as JSON
        return
      }
    }

    render noResults as JSON
  }

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  def checkProviderExists(){
    def noResults = ["":""]
    def reqBody = request.JSON
    def errors = [:]

    if (reqBody) {
      def result = orgService.restLookup(reqBody)
      if (result) {
        render result as JSON
        return
      }
    }

    render noResults as JSON
  }


  /*@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def submitImportData(){
      def noResults = ["":""]
      def reqBody = request.JSON
      def errors = [:]
      log.debug("submitImportData: " + reqBody)

      if(reqBody){
          def result = wekbAPIService.collectData(reqBody)

          log.debug("RESULT : " + result)
          if(result){
              render result as JSON
          }
      }

      render noResults as JSON

  } */

  @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  def getTitleData(){
    def noResults = ["":""]
    def uuid = params?.uuid
    def max = params?.int('max')
    def offset = params?.int('offset')
    log.debug("getTitleData : " + uuid + ", max: " + max + ", offset: " + offset)

    if (uuid) {
      def result = wekbAPIService.getTIPPSOfPackage(uuid, max ?: 10, offset ?: 0)
      //log.debug("RESULT : " + result)
      if (result) {
        render result as JSON
        return
      }
    }

    render noResults as JSON
  }
}
