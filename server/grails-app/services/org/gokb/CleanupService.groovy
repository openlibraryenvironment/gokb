package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Transactional
import org.gokb.DomainClassExtender
import org.gokb.cred.*
import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.Session
import org.opensearch.action.delete.DeleteRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.Requests
import org.opensearch.client.RestHighLevelClient

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
  def tippService

  @Transactional
  private Map expungeByIds ( ids, Job j = null ) {
    Map result = [report: []]
    RestHighLevelClient esclient = ESWrapperService.getClient()
    int idx = 0

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
          KBComponent component = KBComponent.get(component_id)

          if (component) {
            Map expunge_result = componentUpdateService.expungeComponent(component)
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

  public Date deleteOrphanedTipps(Job j = null) {
    log.debug("Expunging TIPPs with missing links")

    TitleInstancePackagePlatform.withNewSession {
      def delete_candidates = TitleInstancePackagePlatform.executeQuery('''select tipp.id from TitleInstancePackagePlatform as tipp where title is null''')

      log.debug("Found ${delete_candidates.size()} erroneous TIPPs..")

      def result = expungeByIds(delete_candidates, j)

      log.debug("Done")

    }

    return new Date()
  }

  public Map expungeRejectedComponents(Job j = null) {
    Map result = [:]

    log.debug("Process rejected candidates")
    TitleInstancePackagePlatform.withNewSession {
      RefdataValue status_rejected = RefdataCategory.lookup('KBComponent.EditStatus', 'Rejected')
      RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

      def delete_candidates = KBComponent.executeQuery('''select kbc.id from KBComponent as kbc
                                                          where kbc.editStatus = :rejectedStatus
                                                          and kbc.status = :deletedStatus''',
                                                        [
                                                          rejectedStatus: status_rejected,
                                                          deletedStatus: status_deleted
                                                        ])

      result = expungeByIds(delete_candidates, j)

      log.debug("Done")
      j.endTime = new Date()
    }

    result
  }

  public void ensureUuids(Job j = null)  {
    log.debug("GOKb missing uuid check..")
    int ctr = 0
    List skipped = []

    KBComponent.withNewSession {
      KBComponent.executeQuery('''select kbc.id from KBComponent as kbc
                                  where kbc.id is not null
                                  and kbc.uuid is null''')
      .each { kbc_id ->
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

  public Map ensureTipls(Job j = null)  {
    Session active_session

    try {
      active_session = sessionFactory.currentSession
    }
    catch (Exception e) {
      log.debug("Need new session ..")
    }

    if (active_session) {
      ensureTiplsRun(j, active_session)
    }
    else {
      TitleInstancePackagePlatform.withNewSession { session ->
        ensureTiplsRun(j, session)
      }
    }

  }

  private Map ensureTiplsRun(Job j = null, active_session)  {
    log.debug("GOKb missing tipl check..")
    Map result = [result: 'OK', new_tipls: 0]
    int ctr = 0
    int batchSize = 100
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
    boolean more = true

    try {
      Map qry_pars = [sc: status_current]

      result.count = TitleInstancePackagePlatform.executeQuery('''select count(*) from TitleInstancePackagePlatform as tipp
                                                                  where tipp.status = :sc
                                                                  and tipp.url is not null
                                                                  and tipp.title is not null
                                                                  and not exists (
                                                                    select 1 from TitleInstancePlatform as tipl
                                                                    where tipl.title = tipp.title
                                                                    and tipl.hostPlatform = tipp.hostPlatform
                                                                  )''',
                                                                  qry_pars
                                                              )[0]

      j?.message("TIPPs to process: ${result.count}")

      while (more) {
        List<Long> batch = TitleInstancePackagePlatform.executeQuery('''select tipp.id from TitleInstancePackagePlatform as tipp
                                                                        where tipp.status = :sc
                                                                        and tipp.url is not null
                                                                        and tipp.title is not null
                                                                        and not exists (
                                                                          select 1 from TitleInstancePlatform as tipl
                                                                          where tipl.title = tipp.title
                                                                          and tipl.hostPlatform = tipp.hostPlatform
                                                                        )''',
                                                                        qry_pars,
                                                                        [max: batchSize]
                                                                    )

        if ( Thread.currentThread().isInterrupted() || j?.isCancelled()) {
          log.debug("Job cancelling ..")
          j?.endTime = new Date()
          break;
        }

        if (batch.size() < batchSize) {
          more = false
        }

        batch.each { tid ->
          TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tid)
          List tipls = checkForTipl(tipp.title, tipp.hostPlatform, tipp.url)
          TitleInstancePlatform final_tipl = null

          if (tipls == null) {
            log.warn("ensureTipls :: Skipping TIPP ${tipp} due to missing info!")
          }
          else if (tipls.size() == 0) {
            final_tipl = new TitleInstancePlatform(url: tipp.url, hostPlatform: tipp.hostPlatform, title: tipp.title).save(flush: true, failOnError: true)
            result.new_tipls++
          }
          else if (tipls.size() == 1) {
            log.debug("ensureTipls :: Skipping TIPP ${tipp} due to matched tipl during in-batch check..")
          }
          else {
            log.debug("Found more than one TIPL for ${tipp.title ?: tipp} on ${tipp.hostPlatform}!")
          }

          log.debug("TIPL ${final_tipl}")
          j?.setProgress(ctr, result.count)
          ctr++

          active_session.flush()
          active_session.clear()
        }
      }

      j?.message("Finished checking for missing TIPLs, with ${result.new_tipls} newly created.".toString())
      j?.setProgress(100)
    }
    catch ( Exception e ) {
      log.error("Problem with ensure TIPLs",e)
      j?.message("There was an error ensuring TIPLs.. check logs for info.".toString())
    }
    finally {
      log.debug("ensureTipls finished (${ctr} TIPPs)")
    }

    j?.endTime = new Date()

    result
  }

  private List checkForTipl(title, platform, url) {
    List<TitleInstancePlatform> result = []

    if ( ( title != null ) && ( platform != null ) && ( url?.trim()?.length() > 0 ) ) {
      def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      result = TitleInstancePlatform.executeQuery('''select tipl from TitleInstancePlatform as tipl
                                                      where tipl.status = :sc
                                                      and tipl.title = :ti
                                                      and tipl.hostPlatform = :plt''',
                                                      [
                                                        ti: title,
                                                        plt: platform,
                                                        sc: status_current
                                                      ])
    }

    result
  }

  public void housekeeping(Job j = null) {
    log.debug("Housekeeping")

    Identifier.withNewSession {
      try {
        List unused = Identifier.executeQuery('''select i.id from Identifier as i
                                                where not exists (
                                                  select c from ComponentIdentifier as c
                                                  where c.identifier = i
                                                )''')

        Map rem_unused = expungeAll(unused, j)

        log.debug("Removed ${rem_unused.num_expunged} unused identifiers")
        j?.message("Removed ${rem_unused.num_expunged} unused identifiers".toString())

        List dupes_vals = Identifier.executeQuery('''select count(*),
                                                    i.normname,
                                                    i.namespace.id
                                                    from Identifier as i
                                                    group by i.normname,
                                                    i.namespace.id
                                                    having count(*) > 1''')
        List<Long> dupes_to_remove = []

        dupes_vals?.each { d ->
          List<Identifier> duplicates = Identifier.executeQuery('''from Identifier as i
                                                      where i.normname = :val
                                                      and i.namespace.id = :ns''',
                                                    [
                                                      val: d[1],
                                                      ns: d[2]
                                                    ])
          Indentifier first = duplicates[0]

          duplicates.eachWithIndex { dui, idx ->
            if (idx > 0) {
              ComponentIdentifier.executeUpdate('''update ComponentIdentifier as c
                                      set c.identifier = :firstID
                                      where c.identifier = :idc
                                      and not exists (
                                        select ci from ComponentIdentifier as ci
                                        where ci.identifier.id = :firstID
                                        and ci.component = c.component
                                      )''',
                                  [
                                    firstID: first,
                                    idc: dui
                                  ])
              dupes_to_remove.add(dui.id)
            }
          }
        }
        Map rem_dupes = expungeAll(dupes_to_remove, j)

        log.debug("Removed ${rem_dupes.num_expunged} linked identifiers")
        j?.message("Removed ${rem_dupes.num_expunged} linked identifiers".toString())

        // Cleanup duplicate identifiers too.
        duplicateIdentifierCleanup(j)
      }
      catch ( Exception e ) {
        e.printStackTrace()
        j?.message('Housekeeping was aborted due to errors.')
      }
    }

    markInvalidComponentNames(j)

    j?.endTime = new Date()
  }

  private void duplicateIdentifierCleanup(Job j = null) {
    log.debug("Beginning duplicate identifier tidyup.")
    Map result = [result: 'OK', projected_deletes: 0, deleted: 0]

    String query = '''from ComponentIdentifier as ci
                      where exists (
                        select 1 from ComponentIdentifier as ci2
                        where ci2.identifier = ci.identifier
                        and ci2.component = ci.component
                        and ci2.dateCreated < ci.dateCreated
                      )'''


    long start_time = System.currentTimeMillis()

    result.projected_deletes = ComponentIdentifier.executeQuery("select count(*) ${query}".toString())[0]
    more = true

    result.deleted = ComponentIdentifier.executeUpdate("delete ${query}".toString())

    log.debug("Finished cleaning duplicate ComponentIdentifiers: ${result} - elapsed = ${System.currentTimeMillis() - start_time}")
    j?.message("Finished cleaning ComponentIdentifiers: ${result} - elapsed = ${System.currentTimeMillis() - start_time}".toString())
  }

  public Map cleanupIssnConflictTitles(Job j = null) {
    log.debug("Cleanup journal namespace conflicts..")
    Map result = [result: 'OK']

    String qryString = '''from ComponentIdentifier as cj
                          where cj.status = :csa
                          and cj.identifier.id in (
                            select id from Identifier
                            where namespace = :nse
                          )
                          and cj.component.id in (
                            select ji.id from JournalInstance as ji
                            where ji.status = :sc
                            and exists (
                              select 1 from ComponentIdentifier as cc
                              where cc.type = :cti
                              and cc.status = :csa
                              and cc.component = ji
                              and cc.identifier.id in (
                                select id from Identifier
                                where namespace = :nsp
                                and value = cj.identifier.value
                              )
                            )
                            and exists (
                              select 1 from ComponentIdentifier as cp
                              where component = ji
                              and cp.status = :csa
                              and cp.identifier.id in (
                                select id from Identifier
                                where namespace = :nse
                                and id != cj.identifier.id
                              )
                            )
                          )'''

    TitleInstance.withNewSession { session ->
      boolean more = true
      int batch = 50
      RefdataValue status_active = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_ACTIVE)
      RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      IdentifierNamespace ns_eissn = IdentifierNamespace.findByValue('eissn')
      IdentifierNamespace ns_issn = IdentifierNamespace.findByValue('issn')

      int ctr = 0

      List candidates = ComponentIdentifier.executeQuery("select cj.id " + qryString, [
          cti: type_id,
          csa: status_active,
          sc: status_current,
          nse: ns_eissn,
          nsp: ns_issn
        ],
        [readonly: true]
      )

      log.debug("cleanupIssnConflictTitles:: Processing ${candidates.size()}")
      j.message("Processing ${candidates.size()} titles..")

      for (c in candidates) {
        ComponentIdentifier cobj = ComponentIdentifier.get(c)

        cobj.status = ci_status_deleted
        cobj.save(flush: true)

        JournalInstance journal = JournalInstance.get(cobj.fromComponent.id)

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

  public void addMissingCoverageObjects(Job j = null) {
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
                                                                [
                                                                  sd: status_deleted
                                                                ])

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
      def tippCoverageDates = TIPPCoverageStatement.executeQuery('''from TIPPCoverageStatement
                                                                    where endDate < startDate''',
                                                                  [
                                                                    readOnly: true
                                                                  ])

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

      def tippAccessDates = TitleInstancePackagePlatform.executeQuery('''from TitleInstancePackagePlatform
                                                                          where accessEndDate < accessStartDate''',
                                                                      [
                                                                        readOnly: true
                                                                      ])

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
  public void rejectUnlinkedTitles(Job job) {
    log.debug("GOKb mark unlinked titles for deletion")

    TitleInstance.withNewSession {
      Date now = new Date()
      RefdataValue deleted_status = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)

      int res = TitleInstance.executeUpdate('''update TitleInstance as ttl
                                                set title.status = :ds,
                                                lastUpdateComment = 'Deleted via title cleanup',
                                                lastUpdated = :now
                                                where status != :ds
                                                and not exists (
                                                  select 1 from TitleInstancePackagePlatform
                                                  where title = ttl
                                                  and status != :ds
                                                )
                                                and not exists (
                                                  select 1 from ComponentHistoryEventParticipant
                                                  where participant = ttl
                                                )''',
                                                [ds: deleted_status, now: now])

      job.message("${res} titles set to status 'Deleted'")
    }

    job.endTime = new Date()
  }

  @Transactional
  public void rejectNoIdTitles(Job job) {
    log.debug("GOKb mark titles without IDs & TIPPs for deletion")

    TitleInstance.withNewSession {
      RefdataValue rejected_status = RefdataCategory.lookup('KBComponent.EditStatus', KBComponent.EDIT_STATUS_REJECTED)

      def res = TitleInstance.executeUpdate('''update TitleInstance as ttl
                                                set title.editStatus = :ds
                                                where title.editStatus <> :ds
                                                and not exists (
                                                  select 1 from TitleInstancePackagePlatform
                                                  where title = ttl
                                                )
                                                and not exists (
                                                  select 1 from ComponentIdentifier
                                                  where component = ttl
                                                )''',
                                                [ds: rejected_status])

      job.message("${res} titles set to editStatus 'Rejected'")
    }
    job.endTime = new Date()
  }

  @Transactional
  public Map expungeAll(List components, Job j = null) {
    Map result = [num_requested: components.size(), num_expunged: 0]
    def esclient = ESWrapperService.getClient()
    log.debug("Component bulk expunge")
    log.debug("Expunging ${result.num_requested} components")

    List remaining = components

    KBComponent.withNewTransaction {
      while (remaining.size() > 0){
        def batch = remaining.take(50)
        remaining = remaining.drop(50)

        ComponentIdentifier.executeUpdate('''delete from ComponentIdentifier as c
                                              where c.component.id IN (:component)''',
                                              [component: batch])

        ComponentWatch.executeUpdate('''delete from ComponentWatch as cw
                                        where cw.component.id IN (:component)''',
                                        [component: batch])

        KBComponentAdditionalProperty.executeUpdate('''delete from KBComponentAdditionalProperty as c
                                                        where c.fromComponent.id IN (:component)''',
                                                        [component: batch])

        KBComponentVariantName.executeUpdate('''delete from KBComponentVariantName as c
                                                where c.owner.id IN (:component)''',
                                                [component: batch])

        ReviewRequestAllocationLog.executeUpdate('''delete from ReviewRequestAllocationLog as c
                                                    where c.rr in (
                                                      select r from ReviewRequest as r
                                                      where r.componentToReview.id IN (:component)
                                                    )''',
                                                    [component: batch])

        def events_to_delete = ComponentHistoryEventParticipant.executeQuery('''select c.event from ComponentHistoryEventParticipant as c
                                                                                where c.participant.id IN (:component)''',
                                                                              [
                                                                                component: batch
                                                                              ])

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
        ComponentAttachment.executeUpdate("delete from ComponentAttachment as cp where cp.component.id IN (:component)", [component: batch])

        batch.each {
          KBComponent kbc = KBComponent.get(it)
          String class_simple_name = kbc.class.getSimpleName()
          String oid = "${kbc.class.name}:${it}"

          if (KBComponent.has(kbc, 'publisherLinks')) {
            TitlePublisher.executeQuery("delete from TitlePublisher where title = :ti", [ti: kbc])
          }

          if (ESWrapperService.indicesPerType[class_simple_name]){
            DeleteRequest req = new DeleteRequest(grailsApplication.config.getProperty('gokb.es.indices.' + ESWrapperService.indicesPerType[class_simple_name]), oid)
            esclient.delete(req, RequestOptions.DEFAULT)
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

  public void markInvalidComponentNames(Job j = null) {
    log.debug("Checking for corrupted component names")
    boolean more = true
    int offset = 0

    TitleInstance.withNewSession { tsession ->
      RefdataValue rr_type = RefdataCategory.lookup("ReviewRequest.StdDesc", "Invalid Name")
      RefdataValue status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
      RefdataValue deleted_status = RefdataCategory.lookup('KBComponent.Status', KBComponent.STATUS_DELETED)

      while (more) {
        def batch = KBComponent.executeQuery('''from KBComponent as kbc
                                                where name like '%�%'
                                                and status != :del
                                                and not exists (
                                                  select 1 from ReviewRequest
                                                  where componentToReview = kbc
                                                  and stdDesc = :type
                                                  and status = :status
                                                )''',
                                            [
                                              type: rr_type,
                                              status: status_open,
                                              del: deleted_status
                                            ],
                                            [
                                              max: 50
                                            ])

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

  public Map markInvalidIdentifiers(Job j = null) {
    log.debug("Checking for invalid identifiers")
    Map result = [
      occurrences: 0,
      components: [:],
      namespaces: [:]
    ]

    Identifier.withNewSession { tsession ->
      boolean more = true
      RefdataValue rr_type = RefdataCategory.lookup("ReviewRequest.StdDesc", "Invalid Identifier")
      String query_str = '''from Identifier as i
                            where exists (
                              select 1 from ComponentIdentifier
                              where identifier = i
                            )'''
      int offset = 0
      int batchSize = 50
      int total = Identifier.executeQuery("select count(i.id) ${query_str}".toString())[0]
      j.message("Processing $total identifiers..")

      Long highest_id = 0

      while (more) {
        List batch = Identifier.executeQuery(query_str + " and id > :hid order by id",
                                            [hid: highest_id],
                                            [max: batchSize])

        batch.each { idc ->
          String validation_result = validationService.checkIdForNamespace(idc.value, idc.namespace)

          if (!validation_result) {
            result.occurrences++

            idc.identifiedComponents.each { kbc ->
              if (!result.components[kbc.id]) {
                result.components[kbc.id] = [
                                              name: kbc.name,
                                              uuid: kbc.uuid,
                                              type: kbc.niceName ,
                                              invalid: []
                                            ]
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
  public Map deleteOrphanedHistoryEvents (Job j = null) {
    Map result = [total: 0]

    try {
      Session session = sessionFactory.currentSession
      result.total = cleanupEvents(session, result)
    }
    catch (Exception e) {
      log.debug("No session. Create new ..")

      TitleInstance.withNewSession { tsession ->
        result.total = cleanupEvents(tsession, result)
      }
    }

    result
  }

  private int cleanupEvents(session, result) {
    RefdataValue deleted_status = RefdataCategory.lookup('KBComponent.Status', KBComponent.STATUS_DELETED)
    int result = 0
    boolean more = true

    log.debug("Got ${ComponentHistoryEvent.list().size()} events!")

    while (more) {
      List batch = ComponentHistoryEventParticipant.executeQuery('''select event.id from ComponentHistoryEventParticipant
                                                                    where participant.status = :sd''',
                                                                    [sd: deleted_status],
                                                                    [max: 50])

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

          result++

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

  public int closeOrphanedReviews() {
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', KBComponent.STATUS_DELETED)
    RefdataValue status_closed = RefdataCategory.lookup('ReviewRequest.Status', 'Closed')
    RefdataValue status_open = RefdataCategory.lookup('ReviewRequest.Status', 'Open')
    Date now = new Date()
    int result = KBComponent.executeUpdate('''update ReviewRequest
                                              set status = :closed,
                                              lastUpdated = :now
                                              where status = :open
                                              and componentToReview.status = :deleted''',
                                          [
                                            closed: status_closed,
                                            now: now,
                                            open: status_open,
                                            deleted: status_deleted
                                          ])

    result
  }

  public int fixDoiUrlIds() {
    int result = 0
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', KBComponent.STATUS_DELETED)
    def doi_candidates = Identifier.executeQuery('''select id.id from Identifier as id
                                                    where id.namespace = :doi
                                                    and id.value like :ptn''',
                                                  [
                                                    doi: IdentifierNamespace.findByValue('doi'),
                                                    ptn: 'http%'
                                                  ])

    doi_candidates.each { doi_id ->
      Identifier ido = Identifier.get(doi_id)
      List parts = ido.value.split('org/')

      log.debug("DOI parts: ${parts}")

      if (parts.size() == 2) {
        result++
        ido.value = parts[1]
        ido.save(flush: true)

        ido.activeIdentifiedComponents.each { cc ->
          if (cc.status != status_deleted) {
            cc.lastUpdateComment = "Fixed DOI identifier value"
            cc.save(flush: true)

            if (cc.class == TitleInstancePackagePlatform) {
              tippService.touchPackage(cc)
            }
          }
        }
      }

      if (result % 50 == 0) {
        sessionFactory.currentSession.clear()
      }
    }

    result
  }

  public Map generateTitleDOIsFromTippInfo(Job j = null) {
    Map result

    try {
      Session session = sessionFactory.currentSession
      result = processTitleDOIcleanup(session, j)
    }
    catch (Exception e) {
      TitleInstance.withNewSession { session ->
        result = processTitleDOIcleanup(session, j)
      }
    }

    result
  }

  private Map processTitleDOIcleanup(active_session, j) {
    Map result = [result: 'OK', counts: [:]]
    RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
    IdentifierNamespace doi_ns = IdentifierNamespace.findByValue('doi')

    String query_string = '''from BookInstance as ti
                          where status = :sc
                          and not exists (
                            select 1 from ComponentIdentifier
                            where component = ti
                            and identifier.namespace = :nsd
                          )'''

    boolean more = true
    Long last_id = 0L

    int count = BookInstance.executeQuery("select count(id) ${query_string}".toString(),
                                          [sc: status_current, nsd: doi_ns])[0]

    log.debug("Got total of ${count} ..")
    int ctr = 0

    while (more) {
      List<BookInstance> batch = BookInstance.executeQuery("${query_string} and id > :cursor order by id".toString(),
                                                            [
                                                              sc: status_current,
                                                              nsd: doi_ns,
                                                              cursor: last_id
                                                            ],
                                                            [max: 50])

      batch.each { book ->
        last_id = book.id

        def augment_result = titleAugmentService.addMissingDoiFromTipps(book)

        log.debug("Got TI augment result ${augment_result}")

        if (!result.counts[augment_result.result]) {
          result.counts[augment_result.result] = 1
        }
        else {
          result.counts[augment_result.result]++
        }

        ctr++
      }

      j?.setProgress(ctr, count)

      active_session.flush()
      active_session.clear()

      if (batch.size() < 50) {
        more = false
      }
    }

    j?.endTime = new Date()
    j?.message("Processed ${ctr} titles (${result.counts}).".toString())

    result
  }
}
