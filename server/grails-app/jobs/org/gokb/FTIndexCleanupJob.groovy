package org.gokb

import com.k_int.ConcurrencyManagerService
import grails.core.GrailsApplication
import org.springframework.beans.factory.annotation.Autowired


class FTIndexCleanupJob {

    GrailsApplication grailsApplication
    static concurrent = false

    @Autowired
    FTIndexCleanupService ftIndexCleanupService

    static triggers = {
        // Cron timer executes every Saturday 15:30:45.
        // configured in bootstrap.groovy via application.yml
    }

    def execute() {
        if ( grailsApplication.config.getProperty('gokb.ftIndexCleanup.enabled', Boolean, false) ) {
            log.debug ("Start scheduled Job FT Index Cleanup... ")
            ftIndexCleanupService.syncTippsBetweenIndexAndDB(null, null, null, false, true)
            log.debug ("FT Index Cleanup finished.")
        }
        else {
            log.debug("FTUpdate is not enabled - set config.ftupdate_enabled = true in config to enable")
        }

    }

}
