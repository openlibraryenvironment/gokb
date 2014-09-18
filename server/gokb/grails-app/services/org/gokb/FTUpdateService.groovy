package org.gokb


import grails.transaction.Transactional
import org.gokb.FTControl
import org.hibernate.ScrollMode
import java.nio.charset.Charset
import java.util.GregorianCalendar


@Transactional
class FTUpdateService {

  def executorService
  def ESWrapperService
  def sessionFactory

  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP

  def updateFTIndexes() {
    log.debug("updateFTIndexes");
    def future = executorService.submit({
      doFTUpdate()
    } as java.util.concurrent.Callable)
    log.debug("updateFTIndexes returning");
  }

  def doFTUpdate() {
    log.debug("doFTUpdate");

    log.debug("Execute IndexUpdateJob starting at ${new Date()}");
    def start_time = System.currentTimeMillis();

    org.elasticsearch.groovy.node.GNode esnode = ESWrapperService.getNode()
    org.elasticsearch.groovy.client.GClient esclient = esnode.getClient()

    updateES(esclient, org.gokb.cred.KBComponent.class) { kbc ->
      def result = [:]
      result._id = "${kbc.class.name}:${kbc.id}"
      result.name = kbc.name
      result.altname = []
      kbc.variantNames.each { vn ->
        result.altname.add(vn.variantName)
      }
      result.componentType=kbc.class.simpleName

      result
    }
  }

  def updateES(esclient, domain, recgen_closure) {

    def count = 0;
    try {
      log.debug("updateES - ${domain.name}");

      def latest_ft_record = FTControl.findByDomainClassNameAndActivity(domain.name,'ESIndex')

      log.debug("result of findByDomain: ${latest_ft_record}");
      if ( !latest_ft_record) {
        latest_ft_record=new FTControl(domainClassName:domain.name,activity:'ESIndex',lastTimestamp:0)
      }

      log.debug("updateES ${domain.name} since ${latest_ft_record.lastTimestamp}");
      def total = 0;
      Date from = new Date(latest_ft_record.lastTimestamp);
      // def qry = domain.findAllByLastUpdatedGreaterThan(from,[sort:'lastUpdated']);

      def c = domain.createCriteria()
      c.setReadOnly(true)
      c.setCacheable(false)
      c.setFetchSize(Integer.MIN_VALUE);

      c.buildCriteria{
          gt('lastUpdated', from)
          order("lastUpdated", "asc")
      }

      def results = c.scroll(ScrollMode.FORWARD_ONLY)
    
      log.debug("Query completed.. processing rows...");

      while (results.next()) {
        Object r = results.get(0);
        def idx_record = recgen_closure(r)

        def future = esclient.index {
          index "gokb"
          type "component"
          id idx_record['_id']
          source idx_record
        }

        latest_ft_record.lastTimestamp = r.lastUpdated?.getTime()

        count++
        total++
        if ( count > 100 ) {
          count = 0;
          log.debug("processed ${++total} records (${domain.name})");
          latest_ft_record.save(flush:true);
          cleanUpGorm();
        }
      }
      results.close();

      println("Processed ${total} records for ${domain.name}");

      // update timestamp
      latest_ft_record.save(flush:true);
    }
    catch ( Exception e ) {
      log.error("Problem with FT index",e);
    }
    finally {
      log.debug("Completed processing on ${domain.name} - saved ${count} records");
    }
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
    propertyInstanceMap.get().clear()
  }

  def clearDownAndInitES() {
    FTControl.withTransaction {
      FTControl.executeUpdate("delete FTControl c");
    }

    updateFTIndexes();
  }
 
  def oldClearDownAndInitES() {

    log.debug("Clear down and init ES");
    org.elasticsearch.groovy.node.GNode esnode = ESWrapperService.getNode()
    org.elasticsearch.groovy.client.GClient esclient = esnode.getClient()

    // Get hold of an index admin client
    org.elasticsearch.groovy.client.GIndicesAdminClient index_admin_client = new org.elasticsearch.groovy.client.GIndicesAdminClient(esclient);

    try {
      // Drop any existing kbplus index
      log.debug("Dropping old ES index....");
      def future = index_admin_client.delete {
        indices 'gokb'
      }
      future.get()
      log.debug("Drop old ES index completed OK");
    }
    catch ( Exception e ) {
      log.warn("Problem deleting index...",e);
    }

    // Create an index if none exists
    log.debug("Create new ES index....");
    def future = index_admin_client.create {
      index 'gokb'
    }
    future.get()

    log.debug("Add title mappings....");
    future = index_admin_client.putMapping {
      indices 'gokb'
      type 'component'
      source  {
        'component' {
          properties {
            name : {
              type : 'multi_field'
              fields : {
                name : [ type : 'string', analyzer : 'snowball' ]
                altname : [ type : 'string', analyzer : 'snowball']
              }
            }
            componentType : [ type:"string", analyzer:'not_analyzed' ]
          }
        }
      }
    }
    log.debug("Join with future");
    future.get()
  }
}
