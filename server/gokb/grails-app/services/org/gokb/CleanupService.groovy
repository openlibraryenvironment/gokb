package org.gokb

import org.gokb.cred.*

class CleanupService {
  def sessionFactory
  
  def expungeDeletedComponents() {

    def result = [:]
    result.report = []

    log.debug("Process delete candidates");

    def delete_candidates = KBComponent.executeQuery('select kbc.id from KBComponent as kbc where kbc.status.value=:deletedStatus',[deletedStatus:'Deleted'])

    delete_candidates.each { component_id ->
      try {
        KBComponent.withNewTransaction {
          log.debug("Expunging ${component_id}");
          def component = KBComponent.get(component_id);
          def expunge_result = component.expunge();
          log.debug(expunge_result);
          result.report.add(expunge_result)
        }
      }
      catch ( Throwable t ) {
        log.error("problem",t);
      }
    }

    log.debug("Done");

    return result
  }
  
  def housekeeping() {
    log.debug("Housekeeping")
    try {
      def ctr = 0
      def start_time = System.currentTimeMillis()
      log.debug("Remove any ISSN identifiers where an eISSN with the same value is also present")
      // Find all identifier occurrences where the component attached also has an issn with the same value.
      // select combo from Combo as combo where combo.toComponent in (select identifier from Identifier as identifier where identifier.ns.ns = 'eissn' )
      //    and exists (
      log.debug("Query")
      def q1 = Identifier.executeQuery('select i1 from Identifier as i1 where i1.namespace.value = :n1 and exists ( select i2 from Identifier as i2 where i2.namespace.value=:n2 and i2.value = i1.value )',
                                       [n1:'issn', n2:'eissn'])
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
    }
  }
  
  private final def duplicateIdentifierCleanup = {
    log.debug "Beginning duplicate identifier tidyup."
    
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
    log.debug "Projected deletions = ${projected_deletes}"
    log.debug "Collected deletions = ${to_delete.size()}"
    if (to_delete.size() != projected_deletes) {
      log.error "Missmatch in duplicate combo deletion, backing out..."
    } else {
    
      if (projected_deletes > 0) {
        log.debug "Matched number of deletions and projected number, delete..."
        
        query = 'DELETE FROM Combo c WHERE c.combo_id IN (:delete_ids)'
        
        // Create native SQL query.
        sqlQuery = session.createSQLQuery(query)
        def dres = sqlQuery.with {
          
           // Set value for parameter startId.
           setParameterList('delete_ids', to_delete)
           
           // Get all results.
           executeUpdate()
         }
    
         log.debug "Delete query returned ${dres} duplicated identifier instances removed."
      } else {
        log.debug "No duplicates to delete..."
      }
    }
    
    log.debug("Finished cleaning identifiers elapsed = ${System.currentTimeMillis() - start_time}")
  }
}
