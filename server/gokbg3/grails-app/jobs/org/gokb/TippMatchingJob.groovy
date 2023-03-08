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
      def startTime = LocalDateTime.now()
      def count = 0
      def result = [matched: 0, created: 0, unmatched: 0, reviews: 0]
      def tippIDs = TitleInstancePackagePlatform.executeQuery(
          "select id from TitleInstancePackagePlatform tipp where status != :sdel and not exists (select c from Combo as c where c.type = :ctype and c.toComponent = tipp)",
          [sdel : RefdataCategory.lookup('KBComponent.Status', 'Deleted'),
          ctype: RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')])
      log.info("${tippIDs.size()} detached TIPPs to check")

      for (Long tippID : tippIDs) {
        def session = sessionFactory.currentSession
        log.debug("begin tipp")
        count++
        TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tippID)
        // ignore Tipp if RR.Date > Tipp.Date
        if (tipp) {
          def rrList = ReviewRequest.findAllByComponentToReviewAndStatusAndStdDesc(tipp, RefdataCategory.lookup("ReviewRequest.Status", "Open"), RefdataCategory.lookup("ReviewRequest.StdDesc", "Ambiguous Title Matches"))
          if (rrList.size() == 0) {
            log.debug("match tipp $tipp")
            def group = tipp.pkg.curatoryGroups?.size() > 0 ? CuratoryGroup.get(tipp.pkg.curatoryGroups[0].id) : null
            def match_result = tippService.matchTitle(tipp, group)

            result[match_result.status]++
            if(match_result.reviewCreated) {
              result.reviews++
            }
          }
          else {
            log.debug("tipp $tipp has ${rrList.size()} recent Review Requests and is ignored.")
          }
          log.debug("end tipp")
        }

        if (count % 50 == 0) {
          session.flush()
          session.clear()
        }

        if (Thread.currentThread().isInterrupted()) {
          session.flush()
          session.clear()
          break
        }
      }
      log.info("end matching Job after ${(LocalDateTime.now().minusNanos(startTime.getNano())).second} sec and ${tippIDs.size()} TIPPs.. ${result}")
    }
    else {
      log.info("Skipping title matching Job due to active import jobs ..")
    }
  }
}
