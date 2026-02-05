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
import java.util.regex.Pattern

class WebEndpointController {

    static namespace = 'rest'

    def componentLookupService
    def springSecurityService
    WebEndpointService webEndpointService

    static Pattern FIXED_DATE_ENDING_PLACEHOLDER_PATTERN = ~/\{YYYY-MM-DD\}\.(tsv|txt)$/
    static Pattern VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN = ~/([12][0-9]{3}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]))\.(tsv|txt)$/

    @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
    def index() {
        def result = [:]
        def base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
        User user = null

        if (springSecurityService.isLoggedIn()) {
            user = User.get(springSecurityService.principal?.id)
        }

        // params['_embed'] = params['_embed'] ?: 'identifiedComponents'

        result = componentLookupService.restLookup(user, WebHookEndpoint, params)

        if (result.data) {
            def resultList = result.data
            resultList*.remove('epPassword')
            resultList*.remove('epUsername')

            if (params['method']) {
                //resultList = resultList.findAll( x -> x.transferMethod?.name == params['method'])
                resultList = resultList.findAll( x -> x.url.startsWith(params['method'].toLowerCase()) )
            }

            result.data = resultList
        }

        render result as JSON
    }

    @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
    def show() {
        def result = [:]
        def base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
        User user = null

        if (springSecurityService.isLoggedIn()) {
            user = User.get(springSecurityService.principal?.id)
        }
        def start_db = LocalDateTime.now()

        // params['_embed'] = params['_embed'] ?: 'identifiedComponents'

        result = componentLookupService.restLookup(user, WebHookEndpoint, params)

        def resultList = result.data

        if(resultList.size() > 0){
            resultList*.remove('epPassword')
            resultList*.remove('epUsername')

            result.data = resultList.get(0)
        }

        render result as JSON
    }

    @Secured(value = ["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
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
            hostname = parts.hostname
            directory = parts.directory
            filename = parts.filename
        }

        def dateMaskMatch = (filename =~ FIXED_DATE_ENDING_PLACEHOLDER_PATTERN)

        FTPClient ftp = new FTPClient()
        FTPClientConfig config = new FTPClientConfig()

        try {
            ftp.connect(hostname)
            ftp.enterLocalPassiveMode()
            def loggedIn = ftp.login(whe.getEpUsername(), whe.getEpPassword())

            if (ftp.isConnected()) {

                FTPFile[] files
                if(dateMaskMatch.size() > 0) {
                    String fixedPart = filename.split("\\{")[0]
                    files = ftp.listFiles(directory)

                    boolean found = false

                    for (FTPFile file : files) {

                        if(file.name.startsWith(fixedPart)){
                            result.result = "success"
                            result.message = "dateMaskFound"
                            found = true
                            break
                        }
                    }
                    if(!found){
                        result.result = "error"
                        result.message = "dateMaskNotFound"
                    }
                }
                else {
                    files = ftp.listFiles(directory + filename)
                    if (files.length > 0 && files[0].size > 0) {

                        result.result = "success"
                        result.message = "success"
                    } else {
                        result.result = "error"
                        result.message = "partlySuccessful"
                    }

                    ftp.logout()
                    ftp.disconnect()
                }
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
