package org.gokb


import org.gokb.cred.Package
import org.gokb.cred.RefdataCategory

class AutoUpdatePackagesJob {

  def packageService
  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Set from Bootstrap
  }

  def execute() {
    if (grailsApplication.config.gokb.packageUpdate.enabled && grailsApplication.config.gokb.ygorUrl) {
      log.debug("Beginning scheduled auto update packages job.")
      def status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")
      // find all updateable packages
      def updPacks = Package.executeQuery(
        "from Package p " +
          "where p.source is not null and " +
          "p.source.automaticUpdates = true " +
          "and p.status != ?" +
          "and (p.source.lastRun is null or p.source.lastRun < current_date)",[status_deleted])
      for (p in Package) {
        if (p.source.needsUpdate() == true) {
          def result = packageService.updateFromSource(p)
          log.debug("Result of update: ${result}")

          sleep(10000)
        }
        else {
          log.debug("Skip package ${p.name} -> ${p.source.lastRun} ${p.source.needsUpdate()}")
        }

        def session = sessionFactory.currentSession
        session.flush()
        session.clear()

        if (Thread.currentThread().isInterrupted()) {
          break
        }
      }
      log.info("auto update packages job completed.")
    } else {
      log.debug("automatic package update is not enabled - set config.gokb.packageUpdate_enabled = true and config.gokb.ygorUrl in config to enable");
    }
  }
}
