package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Transactional
import org.opensearch.action.delete.DeleteRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.Requests
import org.gokb.DomainClassExtender
import org.gokb.cred.*

class CleanupService {
  def sessionFactory
  def ESWrapperService
  def grailsApplication
  def reviewRequestService
  def componentLookupService
  def componentUpdateService
  def validationService
  def autoTimestampEventListener
  def titleAugmentService

  def tidyMissnamedPublishers () {

    try {

      log.debug("Tidy the missnamed publishers")
      def matches = Org.executeQuery('from Org as o where o.name LIKE :pattern', [pattern: '%::{Org:%}'])
      final def toDelete = []

      for (Org original : matches) {

        Org.withNewTransaction {
          String name = original.name
          log.debug("Considering ${name}")

          // Strip the formatting noise.
          String idStr = name.replaceAll(/.*\:\:\{Org\:(\d+)\}/, '$1')
          Long theId = (idStr.isLong() ? idStr.toLong() : null )

          if (theId) {
            if (theId != original.id) {

              Org newTarget = Org.read(theId)

              log.debug("Move the publisher entries to ${newTarget}")

              // Unsaved components can't have combo relations
              final RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, Org.getComboTypeValueFor(TitleInstance, "publisher"))
              final String direction = Org.isComboReverseFor(TitleInstance, 'publisher') ? 'from' : 'to'
              final String opp_dir = direction == 'to' ? 'from' : 'to'
              String hql_query = "from Combo where type=:type and ${direction}Component=:original"

              def hql_params = ['type': type, 'original': original]
              def allCombos = Combo.executeQuery(hql_query,hql_params)

              // In most cases we don't want to update the target of the combo, but instead reinstate the previous entry and completely remove this
              // entry.
              for (Combo c : allCombos) {
                // Lets see if there is a combo already existing that points to the intended target that was mistakenly replace during ingest.
                Date start = c.startDate.clearTime()

                // Query for the combo that was replaced.
                hql_query = "from Combo where type=:type and ${opp_dir}Component=:linkComp and ${direction}Component=:newTarget and endDate >= :dayStart AND endDate < :nextDay"
                hql_params = ['type': type, 'linkComp': c."${opp_dir}Component", 'newTarget': newTarget, 'dayStart': start, 'nextDay': (start + 1)]
                def toReinstate = Combo.executeQuery(hql_query,hql_params)

                if (toReinstate) {
                  // Just reinstate the first.
                  toReinstate[0].endDate = null
                  toReinstate[0].save( failOnError:true )

                  // This combo should be removed by the expunge process later on.
      //            c.delete( flush: true, failOnError:true )
                } else {
                  // This combo didn't replace an existing one but still points to the wrong component.
                  c."${direction}Component" = newTarget
                  c.save(  flush: true, failOnError:true )
                }
              }

              // Remove the duplicate publisher.
              toDelete << original.id

            } else {
              // Publisher was a brand new one. Just rename the publisher.
              log.debug("Correct component with incorrect title. Leave the relationship in place but rename the org.")
              String theName = name.replaceAll(/(.*)\:\:\{Org\:\d+\}/, '$1')

              // Strange things happening when attempting to rename "original" reload from the id.
              Org rnm = Org.get(original.id)
              rnm.name = theName
              rnm.save( flush: true, failOnError:true )
            }
          } else {
            log.debug("'${name}' does not contain an identifier, so we are ignoring this match." )
          }
        }
      }

      expungeByIds(toDelete)

    } catch (Throwable t) {
      log.error("Error tidying duplicated (missnamed) orgs. ${t}")
    }
  }

  @Transactional
  private def expungeByIds ( ids, Job j = null ) {
    def result = [report: []]
    def esclient = ESWrapperService.getClient()
    def idx = 0

    for (component_id in ids){
      if (Thread.currentThread().isInterrupted()){
        log.debug("Job cancelling ..")
        j.endTime = new Date()
        break
      }
      idx++

      try{
        KBComponent.withNewTransaction {
          log.debug("Expunging ${component_id}")
          def component = KBComponent.get(component_id)

          if (component) {
            def expunge_result = componentUpdateService.expungeComponent(component)
            log.debug("${expunge_result}")


            result.report.add(expunge_result)
          }
          else {
            log.error("ExpungeByIds: Unable to reference component $component_id!")
          }
        }
        j?.setProgress(idx, ids.size())
      }
      catch (Throwable t){
        log.error("problem", t)
        j?.message("Problem expunging component with id ${component_id}".toString())
      }
    }

    j?.message("Finished deleting ${idx} components.")
    result
  }

  def deleteOrphanedTipps(Job j = null) {
    log.debug("Expunging TIPPs with missing links")

    TitleInstancePackagePlatform.withNewSession {
      def delete_candidates = TitleInstancePackagePlatform.executeQuery("select tipp.id from TitleInstancePackagePlatform as tipp where not exists (from Combo as c where c.toComponent = tipp AND c.type.value = 'TitleInstance.Tipps')")

      log.debug("Found ${delete_candidates.size()} erroneous TIPPs..")

      def result = expungeByIds(delete_candidates, j)

      log.debug("Done")

    }
    return new Date()
  }

  def expungeRejectedComponents(Job j = null) {
    def result = null

    log.debug("Process rejected candidates")
    TitleInstancePackagePlatform.withNewSession {
      RefdataValue status_rejected = RefdataCategory.lookup('KBComponent.EditStatus', 'Rejected')
      RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

      def delete_candidates = KBComponent.executeQuery('select kbc.id from KBComponent as kbc where kbc.editStatus=:rejectedStatus and kbc.status=:deletedStatus',[rejectedStatus: status_rejected, deletedStatus: status_deleted])

      result = expungeByIds(delete_candidates, j)

      log.debug("Done")
      j.endTime = new Date()
    }

    result
  }

  @Transactional
  def deleteNoUrlPlatforms(Job j = null) {
    log.debug("Delete platforms without URL")

    Platform.withNewSession {
      RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')

      def delete_candidates = Platform.executeQuery('from Platform as plt where plt.primaryUrl IS NULL and plt.status <> :sd', [sd: status_deleted])

      delete_candidates.each { ptr ->
        Platform.withNewTransaction {
          def repl_crit = Platform.createCriteria()
          def orig_plt = repl_crit.list () {
            isNotNull('primaryUrl')
            eq ('name', ptr.name)
            eq ('status', status_current)
          }

          if ( orig_plt?.size() == 1 ) {
            log.debug("Found replacement platform for ${ptr}")
            def new_plt = orig_plt[0]

            def old_from_combos = Combo.executeQuery("from Combo where fromComponent = :op", [op: ptr])
            def old_to_combos = Combo.executeQuery("from Combo where toComponent = :op", [op: ptr])

            old_from_combos.each { oc ->
              def existing_new = Combo.executeQuery("from Combo where type = :ct and fromComponent = :plt and toComponent = :op",[ct: oc.type, plt: new_plt, op: oc.toComponent])

              if (existing_new?.size() == 0 && oc.toComponent != new_plt) {
                oc.fromComponent = new_plt
                oc.save(flush:true)
              }
              else {
                log.debug("New Combo already exists, or would link item to itself.. deleting instead!")
                oc.status = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
                oc.save(flush:true)
              }
            }

            old_to_combos.each { oc ->
              def existing_new = Combo.executeQuery("from Combo where type = :ct and toComponent = :plt and fromComponent = :cc",[ct: oc.type, plt: new_plt, cc: oc.fromComponent])

              if (existing_new?.size() == 0 && oc.fromComponent != new_plt) {
                oc.toComponent = new_plt
                oc.save(flush:true)
              }
              else {
                log.debug("New Combo already exists, or would link item to itself.. deleting instead!")
                oc.status = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
                oc.save(flush:true)
              }
            }

            ptr.name = "${ptr.name} DELETED"
            ptr.deleteSoft()
          }
          else {
            log.debug("Could not find a valid replacement for platform ${ptr}")
          }
        }
      }
    }
    j.endTime = new Date();
  }

  @Transactional
  def ensureUuids(Job j = null)  {
    log.debug("GOKb missing uuid check..")
    def ctr = 0
    def skipped = []

    KBComponent.withNewSession {
      KBComponent.executeQuery("select kbc.id from KBComponent as kbc where kbc.id is not null and kbc.uuid is null").each { kbc_id ->
        try {
          KBComponent comp = KBComponent.get(kbc_id)
          log.debug("Repair component with no uuid.. ${comp.class.name} ${comp.id} ${comp.name}")
          comp.generateUuid()
          comp.markDirty('uuid')
          log.debug("Generated ${comp.uuid}")
          comp.save(flush:true, failOnError:true)
          comp.discard()
          ctr++
        }
        catch(grails.validation.ValidationException ve){
          log.error("ensureUuids :: Skip component id ${kbc_id} because of validation")
          log.error("${ve.errors}")
          skipped.add(kbc_id)
          skipped++
        }
        catch(Exception e){
          log.error("ensureUuids :: Skip component id ${kbc_id}")
          log.error("${e}")
          skipped.add(kbc_id)
          skipped++
        }
      }
    }
    log.debug("ensureUuids :: ${ctr} components updated with uuid");

    j.message("Finished adding missing uuids (total: ${ctr}, skipped: ${skipped.size()})".toString())

    if (skipped > 0) log.error("ensureUuids :: ${skipped.size()} components skipped when updating with uuid");

    j.endTime = new Date()
  }

  @Transactional
  def ensureTipls(Job j = null)  {
    log.debug("GOKb missing tipl check..")

    def ctr = 0
    def new_tipl = 0

    TitleInstancePackagePlatform.withNewSession {
      def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
      def combo_tipp = RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipps')
      def combo_tipl = RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipls')

      try {

        log.debug("Getting count..")
        def count = TitleInstancePackagePlatform.executeQuery("select count(tipp.id) from TitleInstancePackagePlatform as tipp where tipp.status != :sd",[sd: status_deleted])[0]
        def tipp_crit = new DetachedCriteria(TitleInstancePackagePlatform).build{
          ne('status', status_deleted)
        }
        log.debug("Got criteria..")

        def batchsize = 50
        def offset = 0

        while (offset < count) {

          if ( Thread.currentThread().isInterrupted() ) {
            log.debug("Job cancelling ..")
            j.endTime = new Date()
            break;
          }

          def tipps = tipp_crit.list (max: batchsize, offset: offset) {}

          for (tipp in tipps) {

            def tipls = checkForTipl(tipp.title?:null, tipp.hostPlatform, tipp.url)
            def final_tipl = null

            if ( tipls?.size() == 0 ) {
              final_tipl = new TitleInstancePlatform(url:tipp.url).save(flush: true)
              final_tipl.tiplHostPlatform = tipp.hostPlatform
              final_tipl.tiplTitle = tipp.title
              final_tipl.save(flush: true)

              new_tipl++
            }
            else if ( tipls?.size() == 1 ) {
              final_tipl = tipls[0]

              if (tipp.url && final_tipl.url != tipp.url) {
                final_tipl.url = tipp.url
              }

            }
            else {
              log.debug("Found more than one TIPL for ${tipp.title?:tipp} on ${tipp.hostPlatform}!")
            }

            log.debug("TIPL ${final_tipl}")
            j.setProgress(ctr, count)
            ctr++
          }
          offset += batchsize
          log.debug("ensureTipls :: Processed ${ctr} TIPPs")
          Thread.yield()
          cleanUpGorm()
        }

        j.message("Finished checking for missing TIPLs, with ${new_tipl} newly created.".toString())
        j.setProgress(100)

      }
      catch ( Exception e ) {
        log.error("Problem with ensure TIPLs",e)
        j.message("There was an error ensuring TIPLs.. check logs for info.".toString())
      }
      finally {
        log.debug("ensureTipls finished (${ctr} TIPPs)");
      }
    }
    j.endTime = new Date()
  }

  private def checkForTipl(title, platform, url) {
    if ( ( title != null ) && ( platform != null ) && ( url?.trim()?.length() > 0 ) ) {
      def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      def result = TitleInstancePlatform.executeQuery('''select tipl
              from TitleInstancePlatform as tipl,
              Combo as titleCombo,
              Combo as platformCombo
              where titleCombo.toComponent=tipl
              and titleCombo.fromComponent=:ti
              and platformCombo.toComponent=tipl
              and platformCombo.fromComponent=:plt
              and tipl.status=:sc
              ''',[ti: title, plt: platform, sc: status_current])
      result
    }
  }

  def housekeeping(Job j = null) {
    log.debug("Housekeeping")
    Identifier.withNewSession {
      try {
        def unused = Identifier.executeQuery("select i.id from Identifier as i where not exists (select c from Combo as c where c.toComponent = i)")
        def rem_unused = expungeAll(unused, j)

        log.debug("Removed ${rem_unused.num_expunged} unused identifiers")
        j?.message("Removed ${rem_unused.num_expunged} unused identifiers".toString())

        def dupes_vals = Identifier.executeQuery("select count(*), i.normname, i.namespace.id from Identifier as i group by i.normname, i.namespace.id having count(*) > 1")
        def dupes_to_remove = []

        dupes_vals?.each { d ->
          def duplicates = Identifier.executeQuery("from Identifier as i where i.normname = :val and i.namespace.id = :ns", [val: d[1], ns: d[2]])
          def first = duplicates[0]

          duplicates.eachWithIndex { dui, idx ->
            if (idx > 0) {
              Combo.executeUpdate("update Combo as c set c.toComponent = :firstID where c.toComponent = :idc and not exists (select ci from Combo as ci where ci.toComponent.id = :firstID and ci.fromComponent = c.fromComponent)", [firstID: first, idc: dui])
              dupes_to_remove.add(dui.id)
            }
          }
        }
        def rem_dupes = expungeAll(dupes_to_remove, j)

        log.debug("Removed ${rem_dupes.num_expunged} linked identifiers")
        j?.message("Removed ${rem_dupes.num_expunged} linked identifiers".toString())
        // Cleanup duplicate identifiers too.
        duplicateIdentifierCleanup()
      }
      catch ( Exception e ) {
        e.printStackTrace()
        j?.message('Housekeeping was aborted due to errors.')
      }
    }

    markInvalidComponentNames(j)

    j?.endTime = new Date()
  }

  private final def duplicateIdentifierCleanup = {
    log.debug("Beginning duplicate identifier tidyup.")

    // Lookup the Ids refdata element name.
    final long id_combo_type_id = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids').id

    def start_time = System.currentTimeMillis()

    final session = sessionFactory.currentSession

    // Query string with :startId as parameter placeholder.
    String query = 'SELECT c.combo_id, dups.combo_from_fk, dups.combo_to_fk, dups.occurances FROM combo c join ' +
      '(SELECT combo_from_fk, combo_to_fk, count(*) as occurances FROM combo WHERE combo_type_rv_fk=:rdvId GROUP BY combo_from_fk, combo_to_fk HAVING count(*) > 1) dups ' +
      'on c.combo_from_fk = dups.combo_from_fk AND c.combo_to_fk = dups.combo_to_fk;'

    // Create native SQL query.
    def sqlQuery = session.createSQLQuery(query)

    // Use Groovy with() method to invoke multiple methods
    // on the sqlQuery object.
    final results = sqlQuery.with {

        // Set value for parameter startId.
      setLong('rdvId', id_combo_type_id)

      // Get all results.
      list()
    }

    int total = results.size()
    log.debug("Got ${total} candidates: ${results}")

    long projected_deletes = 0
    def to_delete = []
    for (int i=0; i<total; i++) {
      def result = results[i]

      // 0 = combo_id
      long cid = result[0]

      // 1 = from_component
      long from_id = result[1]

      // 2 = to_component
      long to_id = result[2]

      // 3 = Number of occurances
      projected_deletes += (result[3] - 1)
      to_delete << results[i][0]
    }

    // We can also check the number of occurances from the query as an added safety check.
    log.debug("Projected deletions = ${projected_deletes}")
    log.debug("Collected deletions = ${to_delete.size()}")
    if (to_delete.size() != projected_deletes) {
      log.error("Missmatch in duplicate combo deletion, backing out...")
    } else {

      if (projected_deletes > 0) {
        log.debug("Matched number of deletions and projected number, delete...")

        query = 'DELETE FROM Combo c WHERE c.combo_id IN (:delete_ids)'

        while(to_delete.size() > 0){
          def to_delete_size = to_delete.size();
          def qrySize = (to_delete.size() > 50) ? 50 : to_delete.size();
          log.debug "${to_delete_size} identifiers remaining."
          def to_delete_part = to_delete.take(qrySize);
          to_delete = to_delete.drop(qrySize);

          // Create native SQL query.
          sqlQuery = session.createSQLQuery(query)
          def dres = sqlQuery.with {

            // Set value for parameter startId.
            setParameterList('delete_ids', to_delete_part)

            // Get all results.
            executeUpdate()
          }
          log.debug("Delete query returned ${dres} duplicated identifier instances removed.")
        }
      } else {
        log.debug("No duplicates to delete...")
      }
    }

    log.debug("Finished cleaning identifiers elapsed = ${System.currentTimeMillis() - start_time}")
  }

  @Transactional
  def cleanupIssnConflictTitles(Job j = null) {
    def result = [result: 'OK']
    log.debug("Cleanup journal namespace conflicts..")
    def qryString = '''
      from Combo as cj
      where cj.type = :cti
      and cj.status = :csa
      and cj.toComponent.id in (
        select id from Identifier
        where namespace = :nse
      )
      and cj.fromComponent.id in (
        select ji.id from JournalInstance as ji
        where ji.status = :sc
        and exists (
          select 1 from Combo as cc
          where cc.type = :cti
          and cc.status = :csa
          and cc.fromComponent = ji
          and cc.toComponent.id in (
            select id from Identifier
            where namespace = :nsp
            and value = cj.toComponent.value
          )
        )
        and exists (
          select 1 from Combo as cp
          where fromComponent = ji
          and cp.type = :cti
          and cp.status = :csa
          and cp.toComponent.id in (
            select id from Identifier
            where namespace = :nse
            and id != cj.toComponent.id
          )
        )
      )
    '''

    TitleInstance.withNewSession { session ->
      boolean more = true
      int batch = 50
      def type_id = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')
      def combo_active = DomainClassExtender.comboStatusActive
      def combo_deleted = RefdataCategory.lookup('Combo.Status', 'Deleted')
      def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      def ns_eissn = IdentifierNamespace.findByValue('eissn')
      def ns_issn = IdentifierNamespace.findByValue('issn')

      int ctr = 0

      def candidates = Combo.executeQuery("select cj.id " + qryString, [
          cti: type_id,
          csa: combo_active,
          sc: status_current,
          nse: ns_eissn,
          nsp: ns_issn
        ],
        [readonly: true]
      )

      log.debug("cleanupIssnConflictTitles:: Processing ${candidates.size()}")
      j.message("Processing ${candidates.size()} titles..")

      for (c in candidates) {
        def cobj = Combo.get(c)
        cobj.status = combo_deleted
        cobj.save(flush: true)

        def journal = JournalInstance.get(cobj.fromComponent.id)

        journal.lastSeen = new Date().getTime()
        journal.save(flush: true)

        journal.tipps.each { tipp ->
          if (tipp.status != status_deleted) {
            tipp.lastSeen = new Date().getTime()
            tipp.save(flush: true)
          }
        }
        ctr++

        if (ctr % 50 == 0) {
          cleanUpGorm()
          j.setProgress(ctr, candidates.size())
        }
      }

      result.total = ctr
      j.setProgress(100)
      j.endTime = new Date()
    }

    result
  }

  def addMissingCoverageObjects(Job j = null) {
    log.debug("Creating missing coverage statements..")
    def ctr = 0
    def errors = 0

    autoTimestampEventListener.withoutLastUpdated(TitleInstancePackagePlatform) {
      TitleInstancePackagePlatform.withNewSession {
        def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
        def tippIds = TitleInstancePackagePlatform.executeQuery('''select tipp.id from TitleInstancePackagePlatform as tipp
            where status != :sd
            and not exists (
              select 1 from TIPPCoverageStatement
              where owner = tipp
            )''',
            [sd: status_deleted])

        int total_count = tippIds.size()

        while (tippIds.size() > 0) {
          def batch = tippIds.take(50)
          tippIds = tippIds.drop(50)

          for (tid in batch) {
            def tobj = TitleInstancePackagePlatform.get(tid)

            try {
              tobj.addToCoverageStatements(
                startDate: tobj.startDate,
                startVolume: tobj.startVolume,
                startIssue: tobj.startIssue,
                endDate: tobj.endDate,
                endVolume: tobj.endVolume,
                endIssue: tobj.endIssue,
                coverageDepth: RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', tobj.coverageDepth?.value ?: 'Fulltext'),
                coverageNote: tobj.coverageNote,
                embargo: tobj.embargo
              )

              tobj.save(flush:true, failOnError:true)
            }
            catch (Exception e) {
              log.error("Error while creating coverage statement", e)
              errors++
            }

            j.setProgress(ctr++, total_count)
          }

          if ( Thread.currentThread().isInterrupted() ) {
            log.debug("Job cancelling ..")
            j.endTime = new Date()
            break;
          }

          cleanUpGorm()
        }
      }
    }
    log.debug("Done");
    j.message("Finished creating ${ctr} new statements (${errors} errors)".toString())
    j.endTime = new Date()
  }

  @Transactional
  def reviewDates(Job j = null) {
    log.debug("Adding Reviews to components with inconsistent dates")
    TitleInstancePackagePlatform.withNewSession {
      def tippCoverageDates = TIPPCoverageStatement.executeQuery("from TIPPCoverageStatement where endDate < startDate",[readOnly: true])

      log.debug("Found ${tippCoverageDates.size()} offending coverageStatements")
      j.message("Found ${tippCoverageDates.size()} offending coverageStatements".toString())

      tippCoverageDates.each { tcs ->
        KBComponent kbc = KBComponent.get(tcs.owner.id)

        if (kbc) {
          log.debug("Adding RR to TIPP ${kbc}")
          def new_rr = ReviewRequest.raise(
            kbc,
            "Please review the coverage dates.",
            "Found an end date earlier than the start date!."
          ).save(flush:true)
          log.debug("Created RR: ${new_rr}")
        }
        else {
          log.debug("Could not get KBComponent for ${tcs}!")
        }
      }

      def tippAccessDates = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform where accessEndDate < accessStartDate",[readOnly: true])

      log.debug("Found ${tippAccessDates.size()} offending tipp access dates")
      j.message("Found ${tippAccessDates.size()} offending tipp access dates".toString())

      tippAccessDates.each { tcs ->
        if (tcs){
          log.debug("Adding RR to TIPP ${tcs}")
          def new_rr = ReviewRequest.raise(
            tcs,
            "Please review the coverage dates.",
            "Found an end date earlier than the start date!."
          ).save(flush:true)
          log.debug("Created RR: ${new_rr}")
        }
        else {
          log.debug("Could not get KBComponent for ${tcs}!")
        }
      }

      def titleDates = TitleInstance.executeQuery("from TitleInstance where publishedTo < publishedFrom",[readOnly: true])

      log.debug("Found ${titleDates.size()} offending publishing dates")
      j.message("Found ${titleDates.size()} offending publishing dates".toString())

      titleDates.each { tcs ->
        if (tcs){
          log.debug("Adding RR to title ${tcs}")
          def new_rr = ReviewRequest.raise(
            tcs,
            "Please review the publishing dates.",
            "Found an end date earlier than the start date!."
          ).save(flush:true)
          log.debug("Created RR: ${new_rr}")
        }
        else {
          log.debug("Could not get KBComponent for ${tcs}!")
        }
      }
    }
    log.debug("Done");
    j.endTime = new Date()
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }

  @Transactional
  def rejectWrongTitles(Job job) {
    log.debug("GOKb mark wrong titles for deletion")
    def ctr = 0
    def tick=TitleInstance.withNewSession {
      Date now = new Date()
      def deleted_status = RefdataCategory.lookup('KBComponent.Status', KBComponent.STATUS_DELETED)
      def tipps_combo = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')

      def res = TitleInstance.executeUpdate("update TitleInstance as title set title.status = :ds, lastUpdateComment = 'Deleted via title cleanup', lastUpdated = :now where status <> :ds and (title.id not in " +
              "(select fromComponent.id from Combo where type = :tc)" +
              " or title.id not in " +
              "(select fromComponent.id from Combo where type = :tc and toComponent.status <> :ds))" +
              " and title.id not in " +
              "(select participant.id from ComponentHistoryEventParticipant)",[ds: deleted_status, tc: tipps_combo, now: now])

      job.message("${res} titles set to status 'Deleted'")
    }
    job.endTime = new Date()
  }

  @Transactional
  def rejectNoIdTitles(Job job) {
    log.debug("GOKb mark titles without IDs & TIPPs for deletion")
    def ctr = 0
    def tick=TitleInstance.withNewSession {
      def rejected_status = RefdataCategory.lookup('KBComponent.EditStatus', KBComponent.EDIT_STATUS_REJECTED)
      def tipps_combo = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')
      def ids_combo = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')

      def res = TitleInstance.executeUpdate("update TitleInstance as title set title.editStatus = :ds where title.editStatus <> :ds and title.id not in " +
              "(select fromComponent.id from Combo where type = :tc)" +
              " and title.id not in " +
              "(select fromComponent.id from Combo where type = :ic)",[ds: rejected_status, tc: tipps_combo, ic:ids_combo])

      job.message("${res} titles set to editStatus 'Rejected'")
    }
    job.endTime = new Date()
  }

  @Transactional
  def expungeAll(List components, Job j = null) {
    def result = [num_requested: components.size(), num_expunged: 0]
    def esclient
    log.debug("Component bulk expunge")
    log.debug("Expunging ${result.num_requested} components")
    esclient = ESWrapperService.getClient()
    def remaining = components

    KBComponent.withNewTransaction {
      while (remaining.size() > 0){
        def batch = remaining.take(50)
        remaining = remaining.drop(50)

        Combo.executeUpdate("delete from Combo as c where c.fromComponent.id IN (:component) or c.toComponent.id IN (:component)", [component: batch])
        ComponentWatch.executeUpdate("delete from ComponentWatch as cw where cw.component.id IN (:component)", [component: batch])
        KBComponentAdditionalProperty.executeUpdate("delete from KBComponentAdditionalProperty as c where c.fromComponent.id IN (:component)", [component: batch])
        KBComponentVariantName.executeUpdate("delete from KBComponentVariantName as c where c.owner.id IN (:component)", [component: batch])

        ReviewRequestAllocationLog.executeUpdate("delete from ReviewRequestAllocationLog as c where c.rr in ( select r from ReviewRequest as r where r.componentToReview.id IN (:component))", [component: batch])
        def events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant.id IN (:component)", [component: batch])

        events_to_delete.each {
          ComponentHistoryEventParticipant.executeUpdate("delete from ComponentHistoryEventParticipant as c where c.event = :event", [event: it])
          ComponentHistoryEvent.executeUpdate("delete from ComponentHistoryEvent as c where c.id = :event", [event: it.id])
        }

        ReviewRequest.executeUpdate("delete from ReviewRequest as c where c.componentToReview.id IN (:component)", [component: batch])
        ComponentPerson.executeUpdate("delete from ComponentPerson as c where c.component.id IN (:component)", [component: batch])
        ComponentSubject.executeUpdate("delete from ComponentSubject as c where c.component.id IN (:component)", [component: batch])
        ComponentIngestionSource.executeUpdate("delete from ComponentIngestionSource as c where c.component.id IN (:component)", [component: batch])
        KBComponent.executeUpdate("update KBComponent set duplicateOf = NULL where duplicateOf.id IN (:component)", [component: batch])
        ComponentPrice.executeUpdate("delete from ComponentPrice as cp where cp.owner.id IN (:component)", [component: batch])

        batch.each {
          def kbc = KBComponent.get(it)
          def class_simple_name = kbc.class.getSimpleName()
          def oid = "${kbc.class.name}:${it}"

          if (ESWrapperService.indicesPerType[class_simple_name]){
            DeleteRequest req = new DeleteRequest(grailsApplication.config.getProperty('gokb.es.indices.' + ESWrapperService.indicesPerType[class_simple_name]), oid)
            def es_response = esclient.delete(req, RequestOptions.DEFAULT)
          }
        }

        result.num_expunged += KBComponent.executeUpdate("delete KBComponent as c where c.id IN (:component)", [component: batch])
        j?.setProgress(result.num_expunged, result.num_requested)

        if (Thread.currentThread().isInterrupted()){
          log.debug("Job cancelling ..")
          break
        }
      }
    }
    result
  }

  def markInvalidComponentNames(Job j = null) {
    log.debug("Checking for corrupted component names")
    boolean more = true
    int offset = 0

    TitleInstance.withNewSession { tsession ->
      RefdataValue rr_type = RefdataCategory.lookup("ReviewRequest.StdDesc", "Invalid Name")
      RefdataValue status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
      RefdataValue deleted_status = RefdataCategory.lookup('KBComponent.Status', KBComponent.STATUS_DELETED)

      while (more) {
        def batch = KBComponent.executeQuery("from KBComponent as kbc where name like '%�%' and status != :del and not exists (select 1 from ReviewRequest where componentToReview = kbc and stdDesc = :type and status = :status)", [type: rr_type, status: status_open, del: deleted_status], [max: 50])

        batch.each { kbc ->
          reviewRequestService.raise(
            kbc,
            "Remove invalid characters from the title string.",
            "Invalid characters in title string",
            null,
            null,
            null,
            rr_type,
            componentLookupService.findCuratoryGroupOfInterest(kbc)
          )
        }

        offset += batch.size()
        tsession.flush()
        tsession.clear()

        if (Thread.currentThread().isInterrupted()){
          log.debug("Job cancelling ..")
          break
        }

        if (batch.size() == 0) {
          more = false
        }
      }

      if (j) {
        j.endTime = new Date()
        j.message("Created ${offset} reviews ('Invalid Name') for illegal characters in component names.".toString())
      }
    }
  }

  def markInvalidIdentifiers(Job j = null) {
    log.debug("Checking for invalid identifiers")
    def result = [occurrences: 0, components: [:], namespaces: [:]]

    Identifier.withNewSession { tsession ->
      boolean more = true
      RefdataValue rr_type = RefdataCategory.lookup("ReviewRequest.StdDesc", "Invalid Identifier")
      RefdataValue combo_ids = RefdataCategory.lookup('Combo.Type', "KBComponent.Ids")
      int offset = 0
      int batchSize = 50
      int total = Identifier.executeQuery("select count(i.id) from Identifier as i where exists (select 1 from Combo where toComponent = i)")[0]
      j.message("Processing $total identifiers..")

      Long highest_id = 0

      while (more) {
        def batch = Identifier.executeQuery("from Identifier as i where id > :hid and exists (select 1 from Combo where toComponent = i) order by id", [max: batchSize, hid: highest_id])

        batch.each { idc ->
          def isValid = validationService.checkIdForNamespace(idc.value, idc.namespace)

          if (!isValid) {
            result.occurrences++
            idc.identifiedComponents.each { kbc ->
              if (!result.components[kbc.id]) {
                result.components[kbc.id] = [name: kbc.name, uuid: kbc.uuid, type: kbc.niceName , invalid: []]
              }

              if (!result.namespaces[idc.namespace.value]) {
                result.namespaces[idc.namespace.value] = 1
              }
              else {
                result.namespaces[idc.namespace.value]++
              }

              result.components[kbc.id].invalid << [value: idc.value, namespace: idc.namespace.value]
            }
          }
          highest_id = idc.id
        }

        offset += batch.size()
        j.setProgress(offset, total)
        tsession.flush()
        tsession.clear()

        if (Thread.currentThread().isInterrupted()) {
          log.debug("Job cancelling ..")
          break
        }

        if (offset >= total) {
          more = false
        }
      }

      if (j) {
        j.setProgress(100)
        j.endTime = new Date()
        j.message("Found ${result.occurrences} connected to ${result.components.size()} invalid Identifiers.".toString())
      }
    }
    result
  }

  @Transactional
  def deleteOrphanedHistoryEvents (Job j = null) {
    def result = [total: 0]
    def session = null

    try {
      session = sessionFactory.currentSession
    }
    catch (Exception e) {
      log.debug("No session. Create new ..")
    }

    if (session) {
      cleanupEvents(session, result)
    }
    else {
      TitleInstance.withNewSession { tsession ->
        cleanupEvents(tsession, result)
      }
    }

    result
  }

  private void cleanupEvents(session, result) {
    RefdataValue deleted_status = RefdataCategory.lookup('KBComponent.Status', KBComponent.STATUS_DELETED)
    boolean more = true

    log.debug("Got ${ComponentHistoryEvent.list().size()} events!")

    while (more) {

      def batch = ComponentHistoryEventParticipant.executeQuery("select event.id from ComponentHistoryEventParticipant where participant.status = :sd", [sd: deleted_status], [max: 50])

      batch.each { eid ->
        def event = ComponentHistoryEvent.get(eid)

        if (event) {
          log.debug("Processing event ${event}")
          def components_to_update = []

          event.participants.each { chep ->
            if (chep.participant.status != deleted_status) {
              components_to_update << chep.participant
            }
          }

          event.delete(flush: true, failOnError: true)

          result.total++

          components_to_update.each { ctu ->
            if (ctu.tipps) {
              titleAugmentService.touchTitleTipps(ctu, false)
            }
          }
        }
        else {
          log.debug("Event ${eid} not found, probably already deleted ..")
        }
      }

      session.flush()
      session.clear()

      if (batch.size() < 50) {
        more = false
      }
    }
  }
}
