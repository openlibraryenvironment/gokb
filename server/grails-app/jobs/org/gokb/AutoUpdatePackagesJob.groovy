package org.gokb


import org.gokb.cred.Package
import org.gokb.cred.RefdataCategory

class AutoUpdatePackagesJob {

  def ezbCollectionService
  def packageSourceUpdateService
  def curatoryGroupAlertingService
  def sessionFactory
  def adminAlertingService
  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Set from Bootstrap
  }

  def execute() {
    Map failed_jobs_by_group = [:]
    List failed_jobs_no_group = []

    if (grailsApplication.config.getProperty('gokb.packageUpdate.enabled', Boolean, false)) {
      log.debug("Beginning scheduled auto update packages job.")
      RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      RefdataValue status_expected = RefdataCategory.lookup("KBComponent.Status", "Expected")

      // find all updateable packages
      List<Long> updPacks = Package.executeQuery(
        '''select p.id from Package p
           where p.source is not null and
           p.source.automaticUpdates = true
           and p.status in (:sf)
           and (p.source.lastRun is null or p.source.lastRun < current_date)''',
           [sf: [status_current, status_expected]])

      for (pid in updPacks) {
        Package p = Package.findById(pid)

        if (p.source?.needsUpdate() == true) {
          Map result = packageSourceUpdateService.updateFromSource(p.id)
          log.debug("Result of update: ${result}")

          if (result.result == 'ERROR' || (result.result == 'SKIPPED' && result.messageCode == 'kbart.errors.skipped.noFileForAYear')) {
            if (result.jobInfo?.groupId) {
              if (result.jobInfo.groupId && !failed_jobs_by_group[result.jobInfo.groupId]) {
                failed_jobs_by_group[result.jobInfo.groupId] = []
              }

              failed_jobs_by_group[result.jobInfo.groupId] << result.jobInfo
            }
            else if (result.jobInfo) {
              failed_jobs_no_group << result.jobInfo
            }
            else {
              log.warn("No job info for source update for package '${p.name}' (ID ${p.id})")
            }

            if (result.messageCode == 'kbart.errors.url.fileSize') {
              adminAlertingService.sendSizeLimitAlert(p)
            }
          }

          sleep(5000)
        }
        else {
          log.debug("Skip package ${p.name} -> ${p.source?.lastRun} ${p.source?.needsUpdate()}")
        }

        def session = sessionFactory.currentSession
        session.flush()
        session.clear()

        if (Thread.currentThread().isInterrupted()) {
          break
        }
      }

      if (grailsApplication.config.getProperty('gokb.cancelledJobsNotifications', Boolean, false) && grailsApplication.config.getProperty('gokb.alerts.emailFrom') && failed_jobs_by_group) {
        failed_jobs_by_group.each { id, jobs ->
          curatoryGroupAlertingService.triggerDailyJobsAlert(id, jobs)
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
