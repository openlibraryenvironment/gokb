package org.gokb


class MasterListUpdateJob {
  
  // Allow only one run at a time.
  static concurrent = false
  
  static triggers = {
    // Cron timer.            
    cron name: 'MasterListUpdateJobTrigger', cronExpression: "0 0/2 * * * ?", startDelay:600000
  }
  
  PackageService packageService

  def execute() {
    log.debug ("Beginning scheduled Master Package update job.")
    if ( grailsApplication.config.masterListGenerationEnabled ) {
      packageService.updateAllMasters(true)
    }
    else {
      log.debug("grailsApplication.config.masterListGenerationEnabled not set - no master list generation");
    }
    log.debug ("Master Package update job completed.")
  }
}
