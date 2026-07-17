package org.gokb


class FTIndexCleanupJob {

    static concurrent = false

    FTIndexCleanupService ftIndexCleanupService

    static triggers = {
        // Cron timer executes every Saturday 15:30.
        cron name: 'FTCleanupTrigger', cronExpression: "30 15 * * 6 ?", startDelay:120000
    }

    def execute() {
        //TODO: set Config property
        /* if ( grailsApplication.config.getProperty('gokb.ftcleanup_enabled', Boolean, false) ) {
            log.debug ("Start Job FT Index Cleanup... ")
            ftIndexCleanupService.syncTippsBetweenIndexAndDB()
            log.debug ("FT Index Cleanup finished.")
        }
        else {
            log.debug("FTUpdate is not enabled - set config.ftupdate_enabled = true in config to enable")
        } */

        ftIndexCleanupService.syncTippsBetweenIndexAndDB()
    }

}
