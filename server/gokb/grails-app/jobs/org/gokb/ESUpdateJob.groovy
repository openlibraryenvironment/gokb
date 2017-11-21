package org.gokb


class ESUpdateJob {

  def grailsApplication
  def FTUpdateService
  
  // Allow only one run at a time.
  def concurrent = false
  
  static triggers = {
    // Cron timer.            
    cron name: 'ESUpdateTrigger', cronExpression: "0 0/5 * * * ?", startDelay:250000
  }
  
  PackageService packageService

  def execute() {
    if ( grailsApplication.config.ftupdate_enabled ) {
      log.debug ("Beginning scheduled es update job.")
      FTUpdateService.updateFTIndexes();
      log.debug ("es update job completed.")
    }
    else {
      log.debug("FTUpdate is not enabled - set config.ftupdate_enabled = true in config to enable");
    }
  }
}
