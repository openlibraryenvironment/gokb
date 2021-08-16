package org.gokb

import grails.util.Holders
import org.gokb.cred.RefdataCategory
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDateTime

class TippMatchingJob {

  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Cron timer.
    cron name: 'TippMatchingTrigger', cronExpression: "0 40 * * * ?"
  }

  @Autowired
  TippService tippService

  def execute() {
    // this shouldn't run while TIPPs are imported (@UpdatePackageTippsRun.groovy):
    // concurrent writes to the Combo table will result in incomplete results in both jobs.
    synchronized (UpdatePkgTippsRun.LOCK){

    def startTime = LocalDateTime.now()
    TitleInstance.withNewSession {
      def tippIDs = TitleInstancePackagePlatform.executeQuery(
          "select id from TitleInstancePackagePlatform tipp where status != :sdel and not exists (select c from Combo as c where c.type = :ctype and c.toComponent = tipp)",
          [sdel : RefdataCategory.lookup('KBComponent.Status', 'Deleted'),
           ctype: RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')])
      log.debug("${tippIDs.size()} detached TIPPs to check")
      for (Long tippID : tippIDs) {
        log.debug("begin tipp")
        TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tippID)
        // ignore Tipp if RR.Date > Tipp.Date
        if (tipp) {
          def rrList = ReviewRequest.findAllByComponentToReviewAndDateCreatedGreaterThan(tipp, tipp.dateCreated)
          if (rrList.size() == 0) {
            log.debug("match tipp $tipp")
            tippService.matchTitle(tipp)
          }
          else {
            log.debug("tipp $tipp has ${rrList.size()} recent Review Requests and is ignored.")
          }
          log.debug("end tipp")
        }
      }
      log.debug("end matching Job after ${(LocalDateTime.now().minusNanos(startTime.getNano())).second} sec and ${tippIDs.size()} TIPPs")
    }}
  }
}
