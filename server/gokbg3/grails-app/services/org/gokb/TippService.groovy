package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import grails.converters.JSON
import org.gokb.cred.Combo
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.RefdataValue
import org.gokb.cred.TIPPCoverageStatement
import org.gokb.rest.TippController
import org.grails.web.json.JSONObject
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.Package

import java.time.LocalDateTime
import java.time.ZoneId


class TippService {
  def componentUpdateService
  def titleLookupService
  def sessionFactory
  def reviewRequestService
  def autoTimestampEventListener

  public def updateCoverage(tipp, reqBody) {
    def cov_list = reqBody.coverageStatements ?: reqBody.coverage
    def missing = tipp.coverageStatements.collect { it.id }
    def changed = false

    cov_list?.each { c ->
      def parsedStart = GOKbTextUtils.completeDateString(c.startDate)
      def parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)

      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startVolume', c.startVolume)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startIssue', c.startIssue)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endVolume', c.endVolume)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endIssue', c.endIssue)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'embargo', c.embargo)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'coverageNote', c.coverageNote)
      changed |= com.k_int.ClassUtils.updateDateField(parsedStart, tipp, 'startDate')
      changed |= com.k_int.ClassUtils.updateDateField(parsedEnd, tipp, 'endDate')
      changed |= com.k_int.ClassUtils.setRefdataIfPresent(c.coverageDepth, tipp, 'coverageDepth', 'TitleInstancePackagePlatform.CoverageDepth')

      def cs_match = false
      def startAsDate = (parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
      def endAsDate = (parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)

      tipp.coverageStatements?.each { tcs ->

        if (!cs_match && (
          (c.id && tcs.id == c.id) ||
            (tcs.startVolume && tcs.startVolume == c.startVolume) ||
            (tcs.startDate && tcs.startDate == startAsDate) ||
            (!cs_match && !tcs.startVolume && !tcs.startDate && !tcs.endVolume && !tcs.endDate))
        ) {
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startIssue', c.startIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startVolume', c.startVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endVolume', c.endVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endIssue', c.endIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'embargo', c.embargo)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'coverageNote', c.coverageNote)
          changed |= com.k_int.ClassUtils.updateDateField(parsedStart, tcs, 'startDate')
          changed |= com.k_int.ClassUtils.updateDateField(parsedEnd, tcs, 'endDate')

          cs_match = true
          missing.remove(tcs.id)
        }
        else if (cs_match) {
          TippController.log.debug("Matched new coverage ${c} on multiple existing coverages!")
        }
      }

      if (!cs_match) {

        def cov_depth = null

        if (c.coverageDepth instanceof String) {
          cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', c.coverageDepth)
        }
        else if (c.coverageDepth instanceof Integer) {
          cov_depth = RefdataValue.get(c.coverageDepth)
        }
        else if (c.coverageDepth instanceof Map) {
          if (c.coverageDepth.id) {
            cov_depth = RefdataValue.get(c.coverageDepth.id)
          }
          else {
            cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', (c.coverageDepth.name ?: c.coverageDepth.value))
          }
        }

        if (!cov_depth) {
          cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', "Fulltext")
        }

        tipp.addToCoverageStatements('startVolume': c.startVolume,  \
           'startIssue': c.startIssue,  \
           'endVolume': c.endVolume,  \
           'endIssue': c.endIssue,  \
           'embargo': c.embargo,  \
           'coverageDepth': cov_depth,  \
           'coverageNote': c.coverageNote,  \
           'startDate': startAsDate,  \
           'endDate': endAsDate
        )
      }
    }
    if (cov_list) {
      missing.each {
        tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(it))
      }
    }

    tipp
  }

  def matchPackage(Package aPackage) {

    def tippIDs = TitleInstancePackagePlatform.executeQuery(
        'select tipp.id from TitleInstancePackagePlatform as tipp ' +
            ', Combo as c1 ' +
            'where ' +
            'c1.fromComponent=:pkg and c1.toComponent=tipp and c1.type=:rdv1 and c1.status=:act and ' +
            'not exists (from Combo as cmb where cmb.toComponent=tipp and cmb.type=:rdv2 and cmb.status=:act)',
        [rdv2 : RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipps'),
         act  : RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
         , pkg: aPackage, rdv1: RefdataCategory.lookup(Combo.RD_TYPE, 'Package.Tipps')
        ])
    log.debug("found ${tippIDs.size()} unbound TIPPs in package $aPackage")
    tippIDs.each { id ->
      matchTitle(TitleInstancePackagePlatform.get(id))
    }
  }

  void matchTitle(TitleInstancePackagePlatform tipp) {
    def found
    final IdentifierNamespace ZDB_NS = IdentifierNamespace.findByValue('zdb')
    def title_changed = false
    def title_class_name = TitleInstance.determineTitleClass(tipp.publicationType?.value ?: 'Serial')

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
      ti.save(flush: true)
      titleLookupService.addPublisher(tipp.publisherName, ti)
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

      def firstInPrint = tipp.dateFirstInPrint ? GOKbTextUtils.completeDateString(tipp.dateFirstInPrint.format("yyyy-MM-dd")) : null
      def firstOnline = tipp.dateFirstOnline ? GOKbTextUtils.completeDateString(tipp.dateFirstOnline.format("yyyy-MM-dd")) : null

      title_changed |= ti.hasProperty('dateFirstInPrint')?ClassUtils.setDateIfPresent(firstInPrint, ti, 'dateFirstInPrint'):false
      title_changed |= ti.hasProperty('dateFirstOnline')?ClassUtils.setDateIfPresent(firstOnline, ti, 'dateFirstOnline'):false

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

  def statusUpdate() {
    log.debug("${TitleInstancePackagePlatform.executeUpdate("update TitleInstancePackagePlatform tipp set tipp.status=:retired, tipp.lastUpdated=:today where tipp.status=:current and accessEndDate<:today", [retired: RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED), current: RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT), today: new Date()])} TIPPs retired")
    log.debug("${TitleInstancePackagePlatform.executeUpdate("update TitleInstancePackagePlatform tipp set tipp.status=:current, tipp.lastUpdated=:today where tipp.status=:expected and accessStartDate<=:today", [expected: RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_EXPECTED), current: RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT), today: new Date()])} TIPPs activated")
  }

  def scanTIPPs(Job job = null) {
    autoTimestampEventListener.withoutLastUpdated {
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
        def collection = []
        found.matches.each { comp ->
          collection << [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name]
        }
        reviewRequestService.raise(
          tipp,
          "TIPP matched several titles",
          "TIPP ${tipp.name} coudn't be linked.",
          null,
          null,
          [otherComponents: collection] as JSON,
          RefdataCategory.lookup("ReviewRequest.StdDesc", "Multiple Matches"),
          tipp.curatoryGroups?.size() == 1 ? tipp.curatoryGroups[0] : null
          // TODO: use currently active CG if tipp.curatoryGroups?.size() != 1
        )
      }
      if (found.conflicts.size > 0) {
        def collection = []
        found.conflicts.each { comp ->
          collection << [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name]
        }
        reviewRequestService.raise(
            tipp,
            "TIPP conflicts",
            "TIPP ${tipp.name} conflicts with other titles.",
            null,
            null,
            [otherComponents: collection] as JSON,
            RefdataCategory.lookup("ReviewRequest.StdDesc", "Major Identifier Mismatch")
        )
      }
    }
  }
}
