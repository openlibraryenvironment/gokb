package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.Package


class TippService {
  def sessionFactory
  def autoTimestampEventListener

  def copyTitleData(Job job = null) {
    if (job != null) {
      TitleInstance.withNewSession {
        scanTIPPs(job)
      }
    }
    else {
      TitleInstance.withSession {
        scanTIPPs(null)
      }
    }
  }

  def scanTIPPs(Job job = null) {
    autoTimestampEventListener.withoutLastUpdated(TitleInstancePackagePlatform, Package) {
      int index = 0
      boolean cancelled = false
      def tippIDs = TitleInstancePackagePlatform.executeQuery('select id from TitleInstancePackagePlatform where status != :status', [status: RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)])
      log.debug("found ${tippIDs.size()} TIPPs")
      def tippIDit = tippIDs.iterator()
      while (tippIDit.hasNext() && !cancelled) {
        TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tippIDit.next())
        index++
        if (tipp.title) {
          tipp.title.ids.each { data ->
            if (['isbn', 'pisbn', 'issn', 'eissn', 'issnl', 'doi', 'zdb', 'isil'].contains(data.namespace.value)) {
              if (!tipp.ids*.namespace.contains(data.namespace)) {
                tipp.ids << data
                log.debug("added ID $data in TIPP $tipp")
              }
            }
          }
          if (!tipp.name || tipp.name == '') {
            tipp.name = tipp.title.name
            log.debug("set TIPP name to $tipp.name")
          }
          if (tipp.isDirty()) {
            tipp.save(flush: true)
            log.debug("save $index")
          }
          log.debug("destroy #$index: $tipp")
          tipp.finalize()
        }
        job?.setProgress(index, tippIDs.size())
        if (job?.isCancelled()) {
          cancelled = true
        }
        if (index % 100 == 0) {
          log.debug("Clean up GORM");
          // Get the current session.
          def session = sessionFactory.currentSession
          // flush and clear the session.
          session.flush()
          session.clear()
        }
      }
      // one last flush
      sessionFactory.currentSession.flush()
      job?.endTime = new Date()
    }
  }
}
