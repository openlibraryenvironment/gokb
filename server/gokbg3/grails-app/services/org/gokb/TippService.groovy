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
import org.gokb.exceptions.MultipleComponentsMatchedException
import org.grails.web.json.JSONObject

class TippService {

  def componentUpdateService
  def titleLookupService
  def messageService

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
      ti.ids = tipp.ids
    }
    // Add the core data.
    if (ti) {
      componentUpdateService.ensureCoreData(ti, tipp, true, null)

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
      def tippIDs = TitleInstancePackagePlatform.executeQuery('select id from TitleInstancePackagePlatform where status != :status', [status: RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)])
      log.debug("found ${tippIDs.size()} TIPPs")
      tippIDs.eachWithIndex { id, index ->
        TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(id)
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
      }
      job?.setProgress(10, 10)
    }
  }

  private void handleFindConflicts(TitleInstancePackagePlatform tipp, def found) {
    // use this to create more ReviewRequests as needed
  }
}
