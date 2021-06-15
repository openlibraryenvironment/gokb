package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import grails.gorm.transactions.NotTransactional
import grails.validation.ValidationException
import net.sf.json.JSON
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.Imprint
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.Package
import org.gokb.exceptions.MultipleComponentsMatchedException
import org.grails.web.json.JSONObject


class TippService {
  def componentUpdateService
  def titleLookupService
  def messageService
  def sessionFactory
  def autoTimestampEventListener

  def copyTitleData(ConcurrencyManagerService.Job job = null) {
    TitleInstance.withNewSession {
      autoTimestampEventListener.withoutLastUpdated(TitleInstancePackagePlatform) {
        int count = 0, index = 0
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
                  count++
                }
              }
            }
            if (!tipp.name || tipp.name == '') {
              tipp.name = tipp.title.name
              log.debug("set TIPP name to $tipp.name")
              count++
            }
            tipp.save(flush:true)
            tipp.finalize()
          }
          job?.setProgress(index, tippIDs.size())
          if (job?.isCancelled()) {
            cancelled = true
          }
          if (count > 100) {
            log.debug("Clean up GORM");
            // Get the current session.
            def session = sessionFactory.currentSession
            // flush and clear the session.
            session.flush()
            session.clear()
            count = 0
          }
        }
        // one last flush
        sessionFactory.currentSession.flush()
        job?.endTime = new Date()
      }
    }
  }
}
