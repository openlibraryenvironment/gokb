package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import grails.converters.JSON
import org.gokb.cred.*
import org.grails.web.json.JSONObject

class TippService {
  def componentUpdateService
  def titleLookupService
  def sessionFactory
  def reviewRequestService

  def matchPackage(Package aPackage) {
    def tippIDs = aPackage.tipps*.id
    log.debug("found ${tippIDs.size()} TIPPs in package $aPackage")
    tippIDs.each { id ->
      def tipp = TitleInstancePackagePlatform.get(id)
      if (!tipp.title) {
        matchTitle(tipp)
      }
    }
  }

  void matchTitle(TitleInstancePackagePlatform tipp) {
    def found
    final IdentifierNamespace ZDB_NS = IdentifierNamespace.findByValue('zdb')
    def title_changed = false
    def title_class_name = TitleInstance.determineTitleClass([type: tipp.publicationType.value])

    // remap Identifiers
    def my_ids = []
    tipp.ids.each {
      my_ids << it.id
    }
    found = titleLookupService.find(
        tipp.name,
        tipp.getPublisherName(),
        my_ids,
        title_class_name
    )

    TitleInstance ti
    if (found.matches.size() == 1) {
      ti = found.matches[0].object
    }
    else if (found.to_create == true) {
      ti = Class.forName(title_class_name).newInstance()
      ti.name = tipp.name
      titleLookupService.addPublisher(tipp.publisherName, ti)
      ti.save(flush: true)
      tipp.ids.each {
        ti.ids << it
        if (it.namespace == ZDB_NS) {
          // TODO: ZDB-Enrichment for new Journals with ZDB-ID already present
        }
      }
      // Add the core data.
      componentUpdateService.ensureCoreData(ti, tipp, false, null)

      title_changed |= componentUpdateService.setAllRefdata([
          'medium', 'language'
      ], tipp, ti)

      def pubFrom = tipp.accessStartDate ? GOKbTextUtils.completeDateString(tipp.accessStartDate.format("yyyy-MM-dd")) : null
      def pubTo = tipp.accessEndDate ? GOKbTextUtils.completeDateString(tipp.accessEndDate.format("yyyy-MM-dd"), false) : null
      def firstInPrint = tipp.dateFirstInPrint ? GOKbTextUtils.completeDateString(tipp.dateFirstInPrint.format("yyyy-MM-dd")) : null
      def firstOnline = tipp.dateFirstOnline ? GOKbTextUtils.completeDateString(tipp.dateFirstOnline.format("yyyy-MM-dd")) : null

      log.debug("Completed date publishedFrom ${tipp.accessStartDate} -> ${pubFrom}")

      title_changed |= ClassUtils.setDateIfPresent(pubFrom, ti, 'publishedFrom')
      title_changed |= ClassUtils.setDateIfPresent(pubTo, ti, 'publishedTo')
      title_changed |= ClassUtils.setDateIfPresent(firstInPrint, ti, 'dateFirstInPrint')
      title_changed |= ClassUtils.setDateIfPresent(firstOnline, ti, 'dateFirstOnline')

      titleLookupService.addPublisher(tipp.publisherName, ti)

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
      ti.merge(flush: true)
    }
    if (ti) {
      tipp.title = ti
      tipp.save()
      log.debug("linked TIPP $tipp with TitleInstance $ti")
    }
    handleFindConflicts(tipp, found)
  }

  def copyTitleData(ConcurrencyManagerService.Job job = null) {
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

  def scanTIPPs(ConcurrencyManagerService.Job job = null) {
    autoTimestampEventListener.withoutLastUpdated(TitleInstancePackagePlatform) {
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

  private void handleFindConflicts(TitleInstancePackagePlatform tipp, def found) {
    // use this to create ReviewRequests as needed
    // TODO: check if the ReviewRequest was raised already before issuing a new one
    if (tipp.reviewRequests.size() < 1) {
      if (found.matches.size > 1) {
        reviewRequestService.raise(
            tipp,
            "TIPP matched several titles",
            "TIPP ${tipp.name} coudn't be linked.",
            null,
            null,
            found.matches as JSON,
            RefdataCategory.lookup("ReviewRequest.StdDesc", "Multiple Matches")
        )
      }
      if (found.conflicts.size > 0) {
        reviewRequestService.raise(
            tipp,
            "TIPP conflicts",
            "TIPP ${tipp.name} conflicts with other titles.",
            null,
            null,
            found.conflicts as JSON,
            RefdataCategory.lookup("ReviewRequest.StdDesc", "Major Identifier Mismatch")
        )
      }
    }
  }
}
