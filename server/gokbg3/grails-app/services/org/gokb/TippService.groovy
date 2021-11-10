package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import grails.converters.JSON
import org.gokb.cred.Combo
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.RefdataValue
import org.gokb.cred.TIPPCoverageStatement
import org.gokb.rest.TippController
import org.grails.web.json.JSONObject
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.Package

import java.time.LocalDate
import java.time.ZoneId

class TippService {
  def componentUpdateService
  def titleLookupService
  def sessionFactory
  def reviewRequestService
  def autoTimestampEventListener

  /**
   * updating the coverage of this TIPP with the coverageData in reqBody
   *
   * @param tipp the TIPP to be updated
   * @param reqBody data extracted from JSON
   * @return the updated TIPP
   */
  public def updateCoverage(tipp, reqBody) {
    def cov_list = reqBody.coverageStatements ?: reqBody.coverage
    def stale_coverage_ids = tipp.coverageStatements.collect { it.id }

    def changed = false

    cov_list?.each { c ->
      def parsedStart = GOKbTextUtils.completeDateString(c.startDate)
      def parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)

      def cs_match = false
      def startAsDate = (parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
      def endAsDate = (parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
      def conflict = false
      def conflicting_statements = []

      if (c.id) {
        def idMatch = TIPPCoverageStatement.findByOwnerAndId(tipp, c.id)

        if (idMatch) {
          log.debug("Matched statement by id")
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'startIssue', c.startIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'startVolume', c.startVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'endVolume', c.endVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'endIssue', c.endIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'embargo', c.embargo)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(idMatch, 'coverageNote', c.coverageNote)
          changed |= com.k_int.ClassUtils.updateDateField(parsedStart, idMatch, 'startDate')
          changed |= com.k_int.ClassUtils.updateDateField(parsedEnd, idMatch, 'endDate')
          changed |= com.k_int.ClassUtils.setRefdataIfPresent(c.coverageDepth, idMatch, 'coverageDepth', 'TIPPCoverageStatement.CoverageDepth')

          cs_match = true
          stale_coverage_ids.removeAll(idMatch.id)
        }
        else {
          log.debug("No ID match for statement!")
        }
      }
      else {
        tipp.coverageStatements?.each { tcs ->
          if (!cs_match) {
            if (tcs.startVolume && tcs.startVolume == c.startVolume) {
              log.debug("Matched CoverageStatement by startVolume")
              cs_match = true
            }
            else if (tcs.startDate && tcs.startDate == startAsDate) {
              log.debug("Matched CoverageStatement by startDate")
              cs_match = true
            }
            else if (!tcs.startVolume && !tcs.startDate && !tcs.endVolume && !tcs.endDate) {
              log.debug("Matched CoverageStatement with unspecified values")
              cs_match = true
            }
            else if (tcs.startDate && tcs.endDate) {
              if (startAsDate && startAsDate > tcs.startDate && startAsDate < tcs.endDate) {
                conflict = true
                log.debug("Found conflicting statement: new start ${startAsDate} vs ${tcs.startDate} - ${tcs.endDate}")
              }
              else if (endAsDate && endAsDate > tcs.startDate && endAsDate < tcs.endDate) {
                conflict = true
                log.debug("Found conflicting statement: new end ${endAsDate} vs ${tcs.startDate} - ${tcs.endDate}")
              }
            }

            if (conflict) {
              conflicting_statements.add(tcs.id)
            }
            else if (cs_match) {
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startIssue', c.startIssue)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startVolume', c.startVolume)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endVolume', c.endVolume)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endIssue', c.endIssue)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'embargo', c.embargo)
              changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'coverageNote', c.coverageNote)
              changed |= com.k_int.ClassUtils.updateDateField(parsedStart, tcs, 'startDate')
              changed |= com.k_int.ClassUtils.updateDateField(parsedEnd, tcs, 'endDate')
              changed |= com.k_int.ClassUtils.setRefdataIfPresent(c.coverageDepth, tipp, 'coverageDepth', 'TIPPCoverageStatement.CoverageDepth')

              stale_coverage_ids.removeAll(tcs.id)
            }
            else {
              log.debug("No Match ..")
            }
          }
          else {
            log.debug("Already found a match ..")
          }
        }
      }

      for (def cst : conflicting_statements) {
        tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(cst))
        changed = true
      }

      if (!c.id && !cs_match) {
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

        def coverage_item = [
          'startVolume': c.startVolume,
          'startIssue': c.startIssue,
          'endVolume': c.endVolume,
          'endIssu e': c.endIssue,
          'embargo': c.embargo,
          'coverageDepth': cov_depth,
          'coverageNote': c.coverageNote,
          'startDate': startAsDate,
          'endDate ': endAsDate
        ]

        tipp.addToCoverageStatements(coverage_item)
        changed = true
      }
    }

    stale_coverage_ids.each {
      tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(it))
      changed = true
    }

    if (changed) {
      tipp.lastSeen = System.currentTimeMillis()
    }

    tipp
  }

  def matchPackage(Package aPackage, def job = null) {
    log.debug("Matching titles for package ${aPackage}")
    def more = true
    int offset = 0
    def total = TitleInstancePackagePlatform.executeQuery(
      'select count(tipp.id) from TitleInstancePackagePlatform as tipp , Combo as c1 ' +
          'where c1.fromComponent=:pkg and c1.toComponent=tipp and c1.type=:rdv1 ' +
          'and not exists (from Combo as cmb where cmb.toComponent=tipp and cmb.type=:rdv2)',
      [
        pkg : aPackage,
        rdv1: RefdataCategory.lookup(Combo.RD_TYPE, 'Package.Tipps'),
        rdv2: RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipps')
      ]
    )[0]

    def tippIDs = TitleInstancePackagePlatform.executeQuery(
      'select tipp.id from TitleInstancePackagePlatform as tipp , Combo as c1 ' +
          'where c1.fromComponent=:pkg and c1.toComponent=tipp and c1.type=:rdv1 ' +
          'and not exists (from Combo as cmb where cmb.toComponent=tipp and cmb.type=:rdv2)',
      [
          pkg : aPackage,
          rdv1: RefdataCategory.lookup(Combo.RD_TYPE, 'Package.Tipps'),
          rdv2: RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipps')
      ]
    )

    while (tippIDs.size() > 0) {
      def batchSize = tippIDs.size() > 50 ? 50 : tippIDs.size()
      def batch = tippIDs.take(batchSize)
      tippIDs = tippIDs.drop(batchSize)

      batch.each { id ->
        matchTitle(TitleInstancePackagePlatform.get(id))
        offset++
      }
      // Get the current session.
      def session = sessionFactory.currentSession
      // flush and clear the session.
      session.flush()
      session.clear()

      if (job) {
        job.setProgress((total + offset), total*2)
      }


      if (Thread.currentThread().isInterrupted() || job?.isCancelled()) {
        log.debug("cancelling package title matching for job #${job?.uuid}")
        more = false
        break
      }
    }
    log.debug("Finished title matching for ${offset} Titles")
  }

  void matchTitle(TitleInstancePackagePlatform tipp) {
    def found
    final IdentifierNamespace ZDB_NS = IdentifierNamespace.findByValue('zdb')
    def title_changed = false
    def title_class_name = TitleInstance.determineTitleClass(tipp.publicationType?.value ?: 'Serial')

    // remap Identifiers
    def my_ids = []
    tipp.ids.each {
      my_ids << [value: it.value, type: it.namespace.value]
    }
    found = titleLookupService.find(
        tipp.name,
        tipp.getPublisherName(),
        my_ids,
        title_class_name
    )
    // additional check with coverage statement to find the correct title
    if (found.matches?.size() > 1 && tipp.coverageStatements?.size() > 0) {
      coverageCheck(tipp, found)
    }

    TitleInstance ti
    if (found.matches.size() == 1) {
      // exactly one match
      ti = found.matches[0].object
      TIPPCoverageStatement currentCov = latest(tipp.coverageStatements)

      if (currentCov && ((ti.publishedFrom && currentCov.startDate && currentCov.startDate < ti.publishedFrom) || (ti.publishedTo && currentCov.endDate && currentCov.endDate > ti.publishedTo))) {
        reviewRequestService.raise(tipp,
            "TIPP coverage conflicts title publishing data",
            "TIPP ${tipp.name} was linked, check coverage",
            null,
            null,
            [otherComponents: ti] as JSON,
            RefdataCategory.lookup("ReviewRequest.StdDesc", "Coverage Mismatch")
        )
      }
    }
    else if (found.to_create == true) {
      log.debug("No existing title matched, creating ${tipp.name}")
      // unknown title
      ti = Class.forName(title_class_name).newInstance()
      ti.name = tipp.name

      log.debug("Set name ${ti.name} ..")
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

      title_changed |= ti.hasProperty('dateFirstInPrint') ? ClassUtils.updateDateField(firstInPrint, ti, 'dateFirstInPrint') : false
      title_changed |= ti.hasProperty('dateFirstOnline') ? ClassUtils.updateDateField(firstOnline, ti, 'dateFirstOnline') : false

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
    if (tipp.coverageStatements?.size() > 0) {
      coverageCheck(tipp, found)
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

  private void coverageCheck(tipp, found) {
    // find the latest coverage
    TIPPCoverageStatement latest = latest(tipp.coverageStatements)
    if (latest && found.matches.size > 1) {
      // too many identifier matches
      def covMatch = []
      for (def comp : found.matches) {
        if (JournalInstance.isInstance(comp)) {
          JournalInstance journal = JournalInstance(comp)
          if (// starts too early OR
              journal.publishedFrom && latest.startDate && latest.startDate < journal.publishedFrom ||
              // ends too late
              journal.publishedTo && latest.endDate && latest.endDate > journal.publishedTo)
            // no match
            break
          else
            covMatch << journal
        }
      }
      if (covMatch.size() == 1)
        found.matches = covMatch
    }
  }

  private TIPPCoverageStatement latest(def covStmts) {
    def latest = null
    if (covStmts?.size() > 0) {
      def today = LocalDate.now()
      covStmts.each {
        if (latest == null ||
            // a valid date beats a null
            !latest.startDate && it.startDate && today.isAfter(it.startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) ||
            // a valid date beats a prior date
            latest.startDate && it.startDate && today.isAfter(it.startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) && latest.startDate < it.startDate
        ) {
          latest = it
        }
      }
    }
    return latest
  }

  private void handleFindConflicts(TitleInstancePackagePlatform tipp, def found) {
    // use this to create ReviewRequests as needed
    // TODO: check if the ReviewRequest was raised already before issuing a new one
    if (tipp.reviewRequests.size() < 1) {
      if (found.matches.size > 1) {
        def additionalInfo = [otherComponents: []]
        found.matches.each { comp ->
          additionalInfo.otherComponents << [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name, id: comp.object.id, uuid: comp.object.uuid]
        }
        reviewRequestService.raise(
            tipp,
            "TIPP matched several titles",
            "TIPP ${tipp.name} coudn't be linked.".toString(),
            null,
            null,
            (additionalInfo as JSON).toString(),
            RefdataCategory.lookup("ReviewRequest.StdDesc", "Multiple Matches")
        )
      }
      if (found.conflicts.size > 0) {
        def additionalInfo = [otherComponents: []]
        found.conflicts.each { comp ->
          additionalInfo.otherComponents << [oid: "${comp.object.class.name}:${comp.object.id}", name: comp.object.name, id: comp.object.id, uuid: comp.object.uuid]
        }
        reviewRequestService.raise(
            tipp,
            "TIPP conflicts",
            "TIPP ${tipp.name} conflicts with other titles.".toString(),
            null,
            null,
            (additionalInfo as JSON).toString(),
            RefdataCategory.lookup("ReviewRequest.StdDesc", "Major Identifier Mismatch")
        )
      }
    }
  }
}
