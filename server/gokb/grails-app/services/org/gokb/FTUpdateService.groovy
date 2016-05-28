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

    def esclient = ESWrapperService.getClient()

    updateES(esclient, org.gokb.cred.BookInstance.class) { kbc ->

      def result = null

      result = [:]
      result._id = "${kbc.class.name}:${kbc.id}"
      result.name = kbc.name
      result.publisher = kbc.currentPublisher?.name
      result.publisherId = kbc.currentPublisher?.id
      result.altname = []
      kbc.variantNames.each { vn ->
        result.altname.add(vn.variantName)
      }

      result.identifiers = []
      kbc.ids.each { identifier ->
        result.identifiers.add([namespace:identifier.namespace.value, value:identifier.value] );
      }
  
      result.componentType=kbc.class.simpleName

      log.debug("process ${result}");

      return result
    }


    updateES(esclient, org.gokb.cred.JournalInstance.class) { kbc ->

      def result = null

      result = [:]
      result._id = "${kbc.class.name}:${kbc.id}"
      result.name = kbc.name
      result.publisher = kbc.currentPublisher?.name
      result.publisherId = kbc.currentPublisher?.id
      result.altname = []
      kbc.variantNames.each { vn ->
        result.altname.add(vn.variantName)
      }

      result.identifiers = []
      kbc.ids.each { identifier ->
        result.identifiers.add([namespace:identifier.namespace.value, value:identifier.value] );
      }

      result.componentType=kbc.class.simpleName

      log.debug("process ${result}");

      return result
    }

    updateES(esclient, org.gokb.cred.Package.class) { kbc ->
      def result = null
      result = [:]
      result._id = "${kbc.class.name}:${kbc.id}"
      result.name = kbc.name
      return result
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

        if ( idx_record != null ) {
          log.debug("Index start -- ${recid}");

          def recid = idx_record['_id'].toString()
          idx_record.remove('_id');

          def future = esclient.indexAsync {
            index 'gokb'
            type 'component'
            id recid
            source idx_record
          }

          future.actionGet()
          log.debug("Index completed -- ${recid}");
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
 
}
