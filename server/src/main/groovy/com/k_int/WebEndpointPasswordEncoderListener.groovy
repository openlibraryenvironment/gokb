package com.k_int


import grails.plugin.springsecurity.SpringSecurityService
import org.gokb.cred.WebHookEndpoint
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.springframework.beans.factory.annotation.Autowired
import grails.events.annotation.gorm.Listener
import groovy.transform.CompileStatic

@CompileStatic
class WebEndpointPasswordEncoderListener {


    @Autowired
    SpringSecurityService springSecurityService

    @Listener(WebHookEndpoint)
    void onPreInsertEvent(PreInsertEvent event) {
        encodePasswordForEvent(event)
    }

    @Listener(WebHookEndpoint)
    void onPreUpdateEvent(PreUpdateEvent event) {
        encodePasswordForEvent(event)
    }

    private void encodePasswordForEvent(AbstractPersistenceEvent event) {
        if (event.entityObject instanceof WebHookEndpoint) {
            WebHookEndpoint w = event.entityObject as WebHookEndpoint
            if (w.ba_password && ((event instanceof  PreInsertEvent) || (event instanceof PreUpdateEvent && w.isDirty('ba_password')))) {
                event.getEntityAccess().setProperty('ba_password', encodePassword(w.ba_password))
            }
        }
    }

    private String encodePassword(String password) {
        springSecurityService?.passwordEncoder ? springSecurityService.encodePassword(password) : password
        
    }
}


