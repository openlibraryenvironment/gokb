package org.gokb

import org.gokb.cred.*
import grails.gorm.transactions.Transactional
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.client.Requests
import com.k_int.ConcurrencyManagerService.Job
import grails.gorm.DetachedCriteria

class CleanupService {
  def sessionFactory
  def ESWrapperService
  def grailsApplication
  
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
  
  private def expungeByIds ( ids, Job j = null ) {
    
    def result = [report: []]
    def esclient = ESWrapperService.getClient()
    def idx = 0
    
    for (component_id in ids) {

      if ( Thread.currentThread().isInterrupted() ) {
        log.debug("Job cancelling ..")
        break;
      }

      idx++

      try {
        KBComponent.withNewTransaction {
          log.debug("Expunging ${component_id}");
          def component = KBComponent.get(component_id);
          def c_id = "${component.class.name}:${component.id}"
          def expunge_result = component.expunge();
          log.debug("${expunge_result}");

          DeleteRequest req = Requests.deleteRequest(grailsApplication.config.gokb?.es?.index ?: "gokbg3")
                .type('component')
                .id(c_id)

          def es_response = esclient.delete(req)


          log.debug("${es_response}")
          result.report.add(expunge_result)
        }
        j?.setProgress(idx,ids.size())
      }
      catch ( Throwable t ) {
        log.error("problem",t);
        j?.message("Problem expunging component with id ${component_id}".toString())
      }
    }
    
    return result
  }

  @Transactional
  def deleteOrphanedTipps(Job j = null) {
    log.debug("Expunging TIPPs with missing links")

    def delete_candidates = TitleInstancePackagePlatform.executeQuery("select tipp.id from TitleInstancePackagePlatform as tipp where not exists (from Combo as c where c.toComponent = tipp AND c.type.value = 'TitleInstance.Tipps')")

    log.debug("Found ${delete_candidates.size()} erroneous TIPPs..")

    def result = expungeByIds(delete_candidates, j)

    log.debug("Done");
    return new Date();
  }
  
  @Transactional
  def expungeDeletedComponents(Job j = null) {

    log.debug("Process delete candidates");

    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')

    def delete_candidates = KBComponent.executeQuery('select kbc.id from KBComponent as kbc where kbc.status=:deletedStatus and kbc.duplicateOf IS NULL',[deletedStatus: status_deleted])

    def result = expungeByIds(delete_candidates, j)

    log.debug("Done")
    j.endTime = new Date()

    return result
  }

  @Transactional
  def expungeRejectedComponents(Job j = null) {

    log.debug("Process rejected candidates");

    def status_rejected = RefdataCategory.lookupOrCreate('KBComponent.EditStatus', 'Rejected')

    def delete_candidates = KBComponent.executeQuery('select kbc.id from KBComponent as kbc where kbc.editStatus=:rejectedStatus',[rejectedStatus: status_rejected])

    def result = expungeByIds(delete_candidates, j)

    log.debug("Done")
    j.endTime = new Date()

    return result
  }

  @Transactional
  def deleteNoUrlPlatforms(Job j = null) {
    log.debug("Delete platforms without URL")

    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')

    def delete_candidates = Platform.executeQuery('from Platform as plt where plt.primaryUrl IS NULL and plt.status = ?', [status_current])

    delete_candidates.each { ptr ->
      def repl_crit = Platform.createCriteria()
      def orig_plt = repl_crit.list () {
        isNotNull('primaryUrl')
        eq ('name', ptr.name)
        eq ('status', status_current)
      }

      if ( orig_plt?.size() == 1 ) {
        log.debug("Found replacement platform for ${ptr}")
        def new_plt = orig_plt[0]

        def old_from_combos = Combo.executeQuery("from Combo where fromComponent = ?", [ptr])
        def old_to_combos = Combo.executeQuery("from Combo where toComponent = ?", [ptr])

        old_from_combos.each { oc ->
          def existing_new = Combo.executeQuery("from Combo where type = ? and fromComponent = ? and toComponent = ?",[oc.type, new_plt, oc.toComponent])

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
          def existing_new = Combo.executeQuery("from Combo where type = ? and toComponent = ? and fromComponent = ?",[oc.type, new_plt, oc.fromComponent])

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
    j.endTime = new Date();
  }

  @Transactional
  def ensureUuids(Job j = null)  {
    log.debug("GOKb missing uuid check..")
    def ctr = 0
    def skipped = 0
    KBComponent.withNewSession {
      KBComponent.executeQuery("select kbc.id from KBComponent as kbc where kbc.id is not null and kbc.uuid is null").eachWithIndex { kbc_id ->
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
        catch(Exception e){
          log.debug("ensureUuids :: Skip component id ${kbc_id}")
          log.debug("${e}")
          result.skipped.add(kbc_id)
          skipped++
        }
      }
    }
    log.debug("ensureUuids :: ${ctr} components updated with uuid");

    if (skipped > 0) log.debug("${skipped} components skipped when updating with uuid");

    j.endTime = new Date()
    j.message("Finished adding missing uuids (total: ${ctr}, skipped: ${skipped})".toString())
  }

  @Transactional
  def ensureTipls(Job j = null)  {
    log.debug("GOKb missing tipl check..")

    def ctr = 0
    def new_tipl = 0
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def combo_status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    def combo_tipp = RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipps')
    def combo_tipl = RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipls')

    try {

      log.debug("Getting count..")
      def count = TitleInstancePackagePlatform.executeQuery("select count(tipp.id) from TitleInstancePackagePlatform as tipp where tipp.status != ?",[status_deleted])[0]
      def tipp_crit = new DetachedCriteria(TitleInstancePackagePlatform).build{
        ne('status', status_deleted)
      }
      log.debug("Got criteria..")

      def batchsize = 50
      def offset = 0

      while (offset < count) {

        if ( Thread.currentThread().isInterrupted() ) {
          log.debug("Job cancelling ..")
          break;
        }

        def tipps = tipp_crit.list (max: batchsize, offset: offset) {}

        for (tipp in tipps) {

          def tipls = checkForTipl(tipp.title, tipp.hostPlatform, tipp.url)
          def final_tipl = null

          if ( tipls?.size() == 0 ) {
            final_tipl = new TitleInstancePlatform(url:tipp.url).save()

            def plt_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTitles')
            def plt_combo = new Combo(toComponent:final_tipl, fromComponent:tipp.hostPlatform, type:plt_combo_type, status:combo_status_active).save();

            def ti_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipls')
            def ti_combo = new Combo(toComponent:final_tipl, fromComponent:tipp.title, type:ti_combo_type, status:combo_status_active).save();

            new_tipl++
          }
          else if ( tipls?.size() == 1 ) {
            final_tipl = tipls[0]

            if (tipp.url && final_tipl.url != tipp.url) {
              final_tipl.url = tipp.url
            }

          }
          else {
            log.debug("Found more than one TIPL for ${tipp.title} on ${tipp.hostPlatform}!")
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
              and titleCombo.fromComponent=?
              and platformCombo.toComponent=tipl
              and platformCombo.fromComponent=?
              and tipl.status=?
              ''',[title, platform, status_current])
      result
    }
  }
  
  def housekeeping(Job j = null) {
    log.debug("Housekeeping")

    try {
      def ctr = 0
      def start_time = System.currentTimeMillis()
      log.debug("Remove any ISSN identifiers where an eISSN with the same value is also present")
      // Find all identifier occurrences where the component attached also has an issn with the same value.
      // select combo from Combo as combo where combo.toComponent in (select identifier from Identifier as identifier where identifier.ns.ns = 'eissn' )
      //    and exists (
      def ns_issn = IdentifierNamespace.findByValue('issn')
      def ns_eissn = IdentifierNamespace.findByValue('eissn')
      log.debug("Query")
      def q1 = Identifier.executeQuery('select i1 from Identifier as i1 where i1.namespace = :n1 and exists ( select i2 from Identifier as i2 where i2.namespace=:n2 and i2.value = i1.value )',
                                       [n1:ns_issn, n2:ns_eissn])
      log.debug("Query complete, elapsed = ${System.currentTimeMillis() - start_time}")
      def id_combo_type = RefdataValue.findByValue('KBComponent.Ids')
      q1.each { issn ->
        log.debug("cleaning up ${issn.namespace.value}:${issn.value}")
        Combo.executeUpdate('delete from Combo c where c.type=:tp and ( c.fromComponent = :f or c.toComponent=:t )',[f:issn, t:issn, tp:id_combo_type])
        ctr++
      }
      log.debug("ISSN/eISSN cleanup complete ctr=${ctr}, elapsed = ${System.currentTimeMillis() - start_time}")
    
      // Cleanup duplicate identifiers too.
      duplicateIdentifierCleanup()
    }
    catch ( Exception e ) {
      e.printStackTrace()
      j?.message = 'Housekeeping was aborted due to errors.'
    }

    j?.endTime = new Date()
  }
  
  private final def duplicateIdentifierCleanup = {
    log.debug("Beginning duplicate identifier tidyup.")
    
    // Lookup the Ids refdata element name.
    final long id_combo_type_id = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids').id
    
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
      while (i<(total - 1) && from_id == results[i+1][1] && to_id == results[i+1][2]) {
        
        // Increment i here so we keep the index up to date for the outer loop too!
        i++
        to_delete << results[i][0]
      }
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
  def addMissingCoverageObjects(Job j = null) {
    log.debug("Creating missing coverage statements..")
    def ctr = 0
    def errors = 0

    TitleInstancePackagePlatform.withNewSession {
      def tipp_crit = TitleInstancePackagePlatform.createCriteria()
      def tipps = tipp_crit.list () {
        isEmpty('coverageStatements')
        or {
          isNotNull('startDate')
          isNotNull('startVolume')
          isNotNull('endDate')
          isNotNull('endVolume')
          isNotNull('embargo')
        }
      }

      for (t in tipps) {

        if ( Thread.currentThread().isInterrupted() ) {
          log.debug("Job cancelling ..")
          break;
        }

        try {
          t.addToCoverageStatements(startDate: t.startDate, startVolume: t.startVolume, startIssue: t.startIssue, endDate: t.endDate, endVolume: t.endVolume, endIssue: t.endIssue, coverageNote: t.coverageNote, embargo: t.embargo)

          t.save(flush:true, failOnError:true);
        }
        catch (Exception e) {
          log.error("Error while creating coverage statement", e)
          errors++
        }

        if (ctr % 50 == 0) {
          cleanUpGorm()
        }

        j.setProgress(ctr++, tipps.size())
      }
    }
    log.debug("Done");
    j.message("Finished creating ${ctr} new statements (${errors} errors)".toString())
    j.endTime = new Date()
  }

  def reviewDates(Job j = null) {
    log.debug("Adding Reviews to components with inconsistent dates")
    TitleInstancePackagePlatform.withNewSession {
      def tippCoverageDates = TIPPCoverageStatement.executeQuery("from TIPPCoverageStatement where endDate < startDate",[readOnly: true])

      log.debug("Found ${tippCoverageDates.size()} offending coverageStatements")
      j.message("Found ${tippCoverageDates.size()} offending coverageStatements".toString())

      tippCoverageDates.each { tcs ->
        if (tcs.id){
          KBComponent kbc = KBComponent.get(tcs.owner.id)

          if (kbc) {
            ReviewRequest.raise(
              kbc,
              "Please review coverage dates.",
              "Found an end date before start date!."
            )
          }
          else {
            log.debug("Could not get KBComponent for ${tcs}!")
          }
        }
      }

      def tippAccessDates = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform where accessEndDate < accessStartDate",[readOnly: true])

      log.debug("Found ${tippAccessDates.size()} offending tipp access dates")
      j.message("Found ${tippAccessDates.size()} offending tipp access dates".toString())

      tippAccessDates.each { tcs ->
        if (tcs.id){
          KBComponent kbc = KBComponent.get(tcs.id)

          if (kbc) {
            ReviewRequest.raise(
              kbc,
              "Please review coverage dates.",
              "Found an end date before start date!."
            )
          }
          else {
            log.debug("Could not get KBComponent for ${tcs}!")
          }
        }
      }

      def titleDates = TitleInstance.executeQuery("from TitleInstance where publishedTo < publishedFrom",[readOnly: true])

      log.debug("Found ${titleDates.size()} offending publishing dates")
      j.message("Found ${titleDates.size()} offending publishing dates".toString())

      titleDates.each { tcs ->
        if (tcs.id){
          KBComponent kbc = KBComponent.get(tcs.id)

          if (kbc) {
            ReviewRequest.raise(
              kbc,
              "Please review coverage dates.",
              "Found an end date before start date!."
            )
          }
          else {
            log.debug("Could not get KBComponent for ${tcs}!")
          }
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
}
