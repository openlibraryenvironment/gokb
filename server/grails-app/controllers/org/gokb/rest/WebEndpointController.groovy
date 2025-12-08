package org.gokb.rest

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFile
import org.gokb.WebEndpointService
import org.gokb.cred.User
import org.gokb.cred.WebHookEndpoint

import java.time.Duration
import java.time.LocalDateTime

class WebEndpointController {

    static namespace = 'rest'

    def componentLookupService
    def springSecurityService
    WebEndpointService webEndpointService

    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def index() {
        def result = [:]
        def base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
        User user = null

        if (springSecurityService.isLoggedIn()) {
            user = User.get(springSecurityService.principal?.id)
        }
        def start_db = LocalDateTime.now()


        params['_embed'] = params['_embed'] ?: 'identifiedComponents'

        result = componentLookupService.restLookup(user, WebHookEndpoint, params)
        //log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")

        if (result.data) {
            def resultList = result.data
            resultList*.remove('ba_password')
            resultList*.remove('ba_username')

            /* if (params['method']) {
                resultList = resultList.findAll( x -> x.transferMethod?.name == params['method'])
            } */

            result.data = resultList
        }

        render result as JSON
    }

    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def show() {
        def result = [:]
        def base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
        User user = null

        if (springSecurityService.isLoggedIn()) {
            user = User.get(springSecurityService.principal?.id)
        }
        def start_db = LocalDateTime.now()

        params['_embed'] = params['_embed'] ?: 'identifiedComponents'

        result = componentLookupService.restLookup(user, WebHookEndpoint, params)

        def resultList = result.data

        if(resultList.size() > 0){
            resultList*.remove('ba_password')
            resultList*.remove('ba_username')

            result.data = resultList.get(0)
        }

        render result as JSON
    }

    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def check() {
        def result = [:]
        def reqBody = request.JSON

        WebHookEndpoint whe = WebHookEndpoint.findById(reqBody.webhookendpoint)
        String path = reqBody.url
        String hostname = ""
        String directory = ""
        String filename = ""

        if(whe){
            def parts= webEndpointService.extractFtpUrlParts(whe.getUrl(), path)
            log.debug("*** " + parts.hostname + ", " + parts.directory + ", " + parts.filename)
            hostname = parts.hostname
            directory = parts.directory
            filename = parts.filename
        }

        FTPClient ftp = new FTPClient()
        FTPClientConfig config = new FTPClientConfig()

        try {
            ftp.connect(hostname)
            ftp.enterLocalPassiveMode()
            def loggedIn = ftp.login(whe.getBa_username(), whe.getBa_password())

            if (ftp.isConnected()) {

                FTPFile[] files = ftp.listFiles(directory + filename)
                if(files.length > 0 && files[0].size > 0){
                    result.result = "success"
                    result.message = "success"
                }
                else {
                    result.result = "error"
                    result.message = "partlySuccessful"
                }

                ftp.logout()
                ftp.disconnect()
            }
            else {
                result.result = "error"
                result.message = "connectError"
            }

            } catch (Exception e) {
                log.error("Fehler bei FTP-Verbindung: ", e)
                result.result = "error"
                result.message = "configurationError"
            }

        render result as JSON

    }




}
