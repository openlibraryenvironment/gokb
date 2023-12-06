package org.gokb

import grails.util.Holders
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.RefdataCategory
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform

import java.time.LocalDateTime

class TippMatchingJob {

  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // See Bootstrap.groovy
  }

  def tippService
  def sessionFactory
  def concurrencyManagerService

  def execute() {
    def activeJobs = concurrencyManagerService.getActiveImportJobs()

    if (!activeJobs) {
      def result = tippService.matchUnlinkedTipps()
    }
    else {
      log.info("Skipping title matching Job due to active import jobs ..")
    }
  }
}
