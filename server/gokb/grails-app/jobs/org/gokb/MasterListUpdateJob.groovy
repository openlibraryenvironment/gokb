package org.gokb


class MasterListUpdateJob {
  
  // Allow only one run at a time.
  def concurrent = false
  
  static triggers = {
//      cron name: 'MidnightGeneration', cronExpression: "0 0 0 * * ?", startDelay:60000
    cron name: 'MasterListUpdateTrigger', cronExpression: "0/15 * * * * ?", startDelay:10000
  }
  
  PackageService packageService

  def execute() {
    log.debug ("Beginning scheduled Master Package update job.")
    packageService.updateMasterFor(
      (packageService.getAllProviders()[5]).id,
      true
    )
    
//    packageService.updateAllMasters(true)
    log.debug ("Master Package update job completed.")
  }
}
