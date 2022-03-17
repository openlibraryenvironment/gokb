package org.gokb


import org.gokb.cred.Package

class AutoCachePackagesJob {

  def packageService
  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Cron timer.
    cron name: 'AutoCachePackageTrigger', cronExpression: "0 * * * * ? *", startDelay:30000
  }

  def execute() {
    if (grailsApplication.config.gokb.packageOaiCaching.enabled) {
      log.debug("Beginning scheduled auto cache packages job.")
      packageService.cachePackageXml()
      log.debug("Finished scheduled package caching ..")
    }
  }
}
