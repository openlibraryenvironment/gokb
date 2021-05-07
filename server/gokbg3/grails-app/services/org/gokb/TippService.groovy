package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import grails.validation.ValidationException
import net.sf.json.JSON
import org.gokb.cred.Imprint
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
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

  def matchPackage(Package aPackage) {
    TitleInstance.withNewSession {
      int count = 0, index = 0
      boolean cancelled = false
      def tipps = TitleInstancePackagePlatform.findAllWhere(pkg: aPackage, title: null)
      log.debug("found ${tippIDs.size()} TIPPs in package $aPackage")
      tipps.each { tipp -> matchTitle(tipp) }
    }
  }

  void matchTitle(TitleInstancePackagePlatform tipp) {
    Map titleErrorMap = [:] // [<propertyName>: [message: <msg>, baddata: <propertyValue>], ..]
    def found
    def title_changed = false
    def title_class_name = TitleInstance.determineTitleClass([type: tipp.publicationType.value])

    found = titleLookupService.find(
        tipp.name,
        tipp.getPublisherName(),
        tipp.ids ?: [],
        title_class_name
    )

    TitleInstance ti
    if (found.matches.size() == 1) {
      ti = found.matches[0].object
    }
    else if (found.to_create == true) {
      ti = Class.forName(title_class_name).newInstance()
      ti.name = tipp.name
      ti.save(flush:true)
      ti.ids = tipp.ids
      titleLookupService.addPublisher(tipp.publisherName, ti)
    }
    // Add the core data.
    if (ti) {
      componentUpdateService.ensureCoreData(ti, tipp, false, null)

      title_changed |= componentUpdateService.setAllRefdata([
          'medium', 'language'
      ], tipp, ti)

      def pubFrom = GOKbTextUtils.completeDateString(tipp.accessStartDate)
      def pubTo = GOKbTextUtils.completeDateString(tipp.accessEndDate, false)

      log.debug("Completed date publishedFrom ${tipp.accessStartDate} -> ${pubFrom}")

      title_changed |= ClassUtils.setDateIfPresent(pubFrom, ti, 'publishedFrom')
      title_changed |= ClassUtils.setDateIfPresent(pubTo, ti, 'publishedTo')

      if (title_class_name == 'org.gokb.cred.BookInstance') {
        log.debug("Adding Monograph fields for ${ti.class.name}: ${ti}")

        title_changed |= ti.addMonographFields(new JSONObject([//editionNumber        : null,
                                                               //editionDifferentiator: null,
                                                               editionStatement: tipp.editionStatement,
                                                               volumeNumber    : tipp.volumeNumber,
                                                               //summaryOfContent     : null,
                                                               firstAuthor     : tipp.firstAuthor,
                                                               firstEditor     : tipp.firstEditor]))
      }

      if (title_changed) {
        ti.merge(flush: true)
      }
      tipp.title = ti
    }
    handleFindConflicts(tipp, found)
  }

  def copyTitleData(ConcurrencyManagerService.Job job = null) {
    TitleInstance.withNewSession {
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
              }
            }
          }
          if (!tipp.name || tipp.name == '') {
            tipp.name = tipp.title.name
            log.debug("set TIPP name to $tipp.name")
          }
        }
        job?.setProgress(index, tippIDs.size())
        if (job?.isCancelled()) {
          cancelled = true
        }
        if (count++ > 100) {
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

  private void handleFindConflicts(TitleInstancePackagePlatform tipp, def found) {
    // use this to create more ReviewRequests as needed
  }
}
