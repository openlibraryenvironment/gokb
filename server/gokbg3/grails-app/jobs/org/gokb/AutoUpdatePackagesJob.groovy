package org.gokb


import org.gokb.cred.Package

class AutoUpdatePackagesJob {

  def packageService
  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Cron timer.
    cron name: 'AutoUpdatePackageTrigger', cronExpression: "0 0 6 * * ? *" // daily at 6:00 am
  }

  def execute() {
    if (grailsApplication.config.gokb.packageUpdate.enabled && grailsApplication.config.gokb.ygorUrl) {
      log.debug("Beginning scheduled auto update packages job.")
      // find all updateable packages
      def updPacks = Package.executeQuery(
        "from Package p " +
          "where p.source is not null and " +
          "p.source.automaticUpdates = true " +
          "and (p.source.lastRun is null or p.source.lastRun < current_date)")
      updPacks.each { Package p ->
        if (p.source.needsUpdate()) {
          def result = packageService.updateFromSource(p)
          log.debug("Result of update: ${result}")

          sleep(10000)
        }
      }
      log.info("auto update packages job completed.")
    } else {
      log.debug("automatic package update is not enabled - set config.gokb.packageUpdate_enabled = true and config.gokb.ygorUrl in config to enable");
    }
  }
}
