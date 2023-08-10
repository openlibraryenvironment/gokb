package org.gokb


import org.gokb.cred.Package
import org.gokb.cred.RefdataCategory

class AutoUpdatePackagesJob {

  def ezbCollectionService
  def packageSourceUpdateService
  def sessionFactory
  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Set from Bootstrap
  }

  def execute() {
    if (grailsApplication.config.getProperty('gokb.packageUpdate.enabled', Boolean, false)) {
      log.debug("Beginning scheduled auto update packages job.")
      def status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")
      // find all updateable packages
      def updPacks = Package.executeQuery(
        '''select p.id from Package p
           where p.source is not null and
           p.source.automaticUpdates = true
           and p.status != :sd
           and (p.source.lastRun is null or p.source.lastRun < current_date)''',
           [sd: status_deleted])

      for (pid in updPacks) {
        Package p = Package.findById(pid)

        if (p.source.needsUpdate() == true) {
          def result = packageSourceUpdateService.updateFromSource(p)
          log.debug("Result of update: ${result}")

          sleep(5000)
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
      log.debug("automatic package update is not enabled - set config.gokb.packageUpdate_enabled = true in config to enable")
    }

    if (grailsApplication.config.getProperty('gokb.ezbOpenCollections.enabled', Boolean, false)) {
      log.debug("Beginning scheduled ezb package update job.")
      ezbCollectionService.startUpdate()
    }
  }
}
