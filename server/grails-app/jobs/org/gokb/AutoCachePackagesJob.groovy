package org.gokb


import org.gokb.cred.Package

class AutoCachePackagesJob {

  def packageCachingService
  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Cron timer.
    cron name: 'AutoCachePackageTrigger', cronExpression: "0 * * * * ? *", startDelay:30000
  }

  def execute() {
    if (grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false)) {
      log.debug("Beginning scheduled auto cache packages job.")
      packageCachingService.cachePackageXml()
      log.debug("Finished scheduled package caching ..")
    }
  }
}
