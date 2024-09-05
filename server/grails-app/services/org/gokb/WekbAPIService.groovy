package org.gokb

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import org.gokb.cred.Org
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source

// @Transactional
class WekbAPIService {

    static transactional = false
    static GrailsApplication grailsApplication
    OrgService orgService

    static BlockingHttpClient blockingHttpClient

    static final String BASE_URL = "https://wekb.hbz-nrw.de"
    static final String PATH = "/api2/searchApi"
    String USERNAME
    String PASSWORD


    @javax.annotation.PostConstruct
    def init() {
        USERNAME = grailsApplication.config.getProperty('gokb.wekbApiUser')
        PASSWORD = grailsApplication.config.getProperty('gokb.wekbApiUserPassword')
        blockingHttpClient = HttpClient.create(new URL(BASE_URL)).toBlocking()
    }


    def getPackageByUuid(String uuid) {
        Map params = [
                "componentType": "package",
                "uuid": uuid
        ]
        def result = null
        if(isUuidValid(uuid)) {
            result = executeRequest (params)
        }
        return result
    }

    def getPlatformByUuid (String uuid) {
        Map params = [
                "componentType": "platform",
                "uuid": uuid
        ]
        def result = null
        if(uuid) {
            result = executeRequest (params)
        }
        return result
    }

    def getProviderByUuid (String uuid) {
        Map params = [
                "componentType": "provider",
                "uuid": uuid
        ]
        def result = null
        if(uuid) {
            result = executeRequest (params)
        }
        return result
    }

    def getTIPPSOfPackage (String uuid, Integer max) {
        Map params = [
                "componentType": "TitleInstancePackagePlatform",
                "tippPackageUuid": uuid,
                "max": max
        ]
        def result = null
        if(uuid) {
            result = executeRequest (params)
        }
        return result
    }

    def executeRequest (Map queryParams) {
        def result = null
        try {

            UriBuilder basePath = UriBuilder.of(BASE_URL).path(PATH)

            for ( def param : queryParams.entrySet() ) {
                basePath = basePath.queryParam(param.getKey().toString(), param.getValue())
            }

            URI uri = basePath.queryParam('username', USERNAME)
                    .queryParam('password', PASSWORD)
                    .build()

            HttpResponse resp = blockingHttpClient.exchange(HttpRequest.POST(uri, []), String)

            if (resp.status == HttpStatus.OK) {
                def jsonResponse = new JsonSlurper().parseText(resp.body())
                if(jsonResponse?.result_count_total > 0 || jsonResponse?.result?.size() > 0){
                    result = jsonResponse.result
                }
                log.debug(jsonResponse.toString())

            }
        } catch (io.micronaut.http.client.exceptions.HttpClientException e) {
            log.error("HttpClientException...", e)
        }
        catch (Exception e) {
            log.error("Exception...", e)
        }

        return result
    }


    private static boolean isUuidValid(String uuid){
        //  ^\{?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\}?$
        if ( uuid ) {
            String[] parts = uuid.split("-")
            if (parts.length == 5 && parts[0].length() == 8 && parts[1].length() == 4 && parts[2].length() == 4
                    && parts[3].length() == 4 && parts[4].length() == 12
            ) {

                return true
            }
        }

        return false
    }


    def collectData (def importData) {
        def result = [:]

        def provider = importData?.provider
        def providerExists = provider.providerExists
        def platformExists = importData?.platform?.platformExists
        def source = importData?.source
        def pckg = importData?.packageItem

        log.debug("COLLECT data..." )

        if (providerExists) {
            // get Provider Objekt

        }




        /*
        def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
        def existingProvider = Org.findAllByNameIlikeAndStatusNotEqual(provider.name, status_deleted);

        if(existingProvider.size() > 0) {
            log.debug("### found existing Provider: " + existingProvider.get(0).id)
        }
        result.packageName = importData.packageItem?.name
        */


        return result
    }


    def checkIfProviderExists (String uuid) {
        def result = [:]
        def wekbProvider = getProviderByUuid(uuid)

        log.debug("WEKBPROVIDER: " + wekbProvider)
        def providerHomepage = null
        def providerName = null

        if (wekbProvider) {
            providerName = wekbProvider.name
            providerHomepage = wekbProvider.homepage

            def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
            def existingProvider = Org.findAllByNameIlikeAndStatusNotEqual(providerName, status_deleted);

            if(existingProvider.size() > 0) {
                result.providerExists = true
                result.providerId = existingProvider.get(0).id
            } else {
                result.providerExists = false
            }
            // Homepage soll bei Neuanlage auch gespeichert werden
            result.providerHomepage = providerHomepage
        }

        return result
    }



}

