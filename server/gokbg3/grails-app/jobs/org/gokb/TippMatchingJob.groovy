package org.gokb

import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.hibernate.mapping.List

class TippMatchingJob {

  // Allow only one run at a time.
  static concurrent = false

  def tippService

  static triggers = {
    // Cron timer.
    cron name: 'TippMatchingTrigger', cronExpression: "0 20 0/1 * * ?"
  }

  def execute() {
  TitleInstance.withSession {
      def tippIDs = TitleInstancePackagePlatform.executeQuery(
          "select id from TitleInstancePackagePlatform tipp where status != :sdel and not exists (select c from Combo as c where c.type = :ctype and c.toComponent = tipp)",
          [sdel : RefdataCategory.lookup('KBComponent.Status', 'Deleted'),
           ctype: RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')])
      log.debug("${tippIDs.size()} detached TIPPs to check")
      for (Long tippID : tippIDs) {
        log.debug("begin tipp")
        TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tippID)
          // ignore Tipp if RR.Date > Tipp.Date
          def rrList = ReviewRequest.findAllByComponentToReviewAndDateCreatedGreaterThan(tipp, tipp.dateCreated)
          if (rrList.size() == 0) {
            log.debug("match tipp $tipp")
            tippService.matchTitle(tipp)
        } else {
            log.debug("tipp $tipp has ${rrList.size()} recent Review Requests and is ignored.")
          }
        log.debug("end tipp")
      }
    }
  }
}
