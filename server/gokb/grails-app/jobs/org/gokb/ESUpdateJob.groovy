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
    log.debug ("Beginning scheduled es update job.")
    FTUpdateService.updateFTIndexes();
    log.debug ("es update job completed.")
  }
}
