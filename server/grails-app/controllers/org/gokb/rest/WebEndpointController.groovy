package org.gokb.rest

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import org.gokb.cred.User
import org.gokb.cred.WebHookEndpoint

import java.time.Duration
import java.time.LocalDateTime

class WebEndpointController {

    def componentLookupService
    def springSecurityService

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
        log.debug("#### " + result)

        render result as JSON
    }

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
        //log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
        log.debug("#### " + result)

        render result as JSON
    }


}
