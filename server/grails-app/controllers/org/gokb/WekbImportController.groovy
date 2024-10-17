package org.gokb

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured

class WekbImportController {

    WekbAPIService wekbAPIService
    OrgService orgService

    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def index() {
        //NO-OP
    }

    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def getPackageMetaData(){
        log.debug("getPackageMetaData query: ${params}")
        def noResults = ["":""]
        def uuid = params?.uuid
        if(uuid){
            def result = wekbAPIService.getPackageByUuid(uuid)
            log.debug("RESULT: " + result)
            if(result){
                render result as JSON
            }
        }

        render noResults as JSON

    }


    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def getPlatformMetaData(){
        def noResults = ["":""]
        def uuid = params?.uuid
        if(uuid){
            def result = wekbAPIService.getPlatformByUuid(uuid)
            log.debug("RESULT : " + result)
            if(result){
                render result as JSON
            }
        }

        render noResults as JSON

    }


    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def getProviderData(){
        def noResults = ["":""]
        def uuid = params?.uuid
        log.debug("getProviderData : " + uuid)
        if(uuid){
            def result = wekbAPIService.checkIfProviderExists(uuid)
            log.debug("RESULT : " + result)
            if(result){
                render result as JSON
            }
        }

        render noResults as JSON

    }


    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def checkProviderExists(){
        def noResults = ["":""]
        def reqBody = request.JSON
        def errors = [:]
        log.debug("CheckProviderExists: " + reqBody)
        if(reqBody){

            def result = orgService.restLookup(reqBody)

            log.debug("RESULT : " + result)
            if(result){
                render result as JSON
            }
        }

        render noResults as JSON

    }


    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
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

    }

    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def getTitleData(){
        def noResults = ["":""]
        def uuid = params?.uuid
        def max = params?.int('max')
        def offset = params?.int('offset')
        log.debug("getTitleData : " + uuid + ", max: " + max + ", offset: " + offset)
        if(uuid){
            def result = wekbAPIService.getTIPPSOfPackage(uuid, max ?: 10, offset ?: 0)
            log.debug("RESULT : " + result)
            if(result){
                render result as JSON
            }
        }

        render noResults as JSON

    }


}
