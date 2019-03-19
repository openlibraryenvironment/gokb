package org.gokb


import grails.gorm.transactions.Transactional
import org.gokb.FTControl
import org.hibernate.ScrollMode
import java.nio.charset.Charset
import java.util.GregorianCalendar
import org.gokb.cred.*
import java.text.SimpleDateFormat

@Transactional
class FTUpdateService {

  def ESWrapperService
  def sessionFactory
  def grailsApplication

  public static boolean running = false;


  /**
   * Update ES.
   * The caller is responsible for running this function in a task if needed. This method
   * is responsible for ensuring only 1 FT index task runs at a time. It's a simple mutex.
   * see https://async.grails.org/latest/guide/index.html
   */
  def synchronized updateFTIndexes() {
    log.debug("updateFTIndexes");

    if ( running == false ) {
      running = true;
      doFTUpdate()
      log.info("FTUpdate done.")
      return new Date();
    }
    else {
      log.info("FTUpdate already running")
      return "Job cancelled – FTUpdate was already running!";
    }
  }

  def doFTUpdate() {
    log.debug("doFTUpdate");

    log.debug("Execute IndexUpdateJob starting at ${new Date()}");
    def start_time = System.currentTimeMillis();

    def esclient = ESWrapperService.getClient()

    try {
  
      updateES(esclient, org.gokb.cred.BookInstance.class) { kbc ->
  
        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        def current_pub = kbc.currentPublisher
  
        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
//         result.publisher = kbc.currentPublisher?.name
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        result.altname = []
        result.updater='book'
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        result.lastUpdatedDisplay = sdf.format(kbc.lastUpdated)
        
        result.status = kbc.status?.value
  
        result.identifiers = []
        kbc.ids.each { identifier ->
          result.identifiers.add([namespace:identifier.namespace.value, value:identifier.value] );
        }
    
        result.componentType=kbc.class.simpleName
  
        // log.debug("process ${result}");
        result
      }
  
  
      updateES(esclient, org.gokb.cred.JournalInstance.class) { kbc ->
  
        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        def current_pub = kbc.currentPublisher
  
        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.updater='journal'
        // result.publisher = kbc.currentPublisher?.name
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        result.lastUpdatedDisplay = sdf.format(kbc.lastUpdated)
        
        result.status = kbc.status?.value
  
        result.identifiers = []
        kbc.ids.each { identifier ->
          result.identifiers.add([namespace:identifier.namespace.value, value:identifier.value] );
        }
  
        result.componentType=kbc.class.simpleName
  
        // log.debug("process ${result}");
        result
      }

      updateES(esclient, org.gokb.cred.DatabaseInstance.class) { kbc ->

        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        def current_pub = kbc.currentPublisher

        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        // result.publisher = kbc.currentPublisher?.name
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        result.lastUpdatedDisplay = sdf.format(kbc.lastUpdated)

        result.status = kbc.status?.value

        result.identifiers = []
        kbc.ids.each { identifier ->
          result.identifiers.add([namespace:identifier.namespace.value, value:identifier.value] );
        }

        result.componentType=kbc.class.simpleName

        // log.debug("process ${result}");
        result
      }
  
      updateES(esclient, org.gokb.cred.Package.class) { kbc ->

        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.description = kbc.description
        result.descriptionURL = kbc.descriptionURL
        result.sortname = kbc.name
        result.altname = []
        result.listStatus = kbc.listStatus?.value
        result.lastUpdatedDisplay = sdf.format(kbc.lastUpdated)

        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.updater='pkg'
        result.titleCount = "${kbc.tipps?.findAll{ it.status?.value == 'Current'}?.size() ?: '0'}"

        result.cpname = kbc.provider?.name

        result.provider = kbc.provider ? kbc.provider.getLogEntityId() : ""
        result.providerUuid = kbc.provider?.uuid ?: ""

        result.nominalPlatform = kbc.nominalPlatform ? kbc.nominalPlatform.getLogEntityId() : ""
        result.platformName = kbc.nominalPlatform?.name
        result.platformUuid = kbc.nominalPlatform?.uuid ?: ""
        
        result.curatoryGroups = []
        kbc.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }

        result.status = kbc.status?.value

        result.identifiers = []
        kbc.ids.each { identifier ->
          result.identifiers.add([namespace:identifier.namespace.value, value:identifier.value] );
        }

        result.componentType=kbc.class.simpleName

        result
      }

      updateES(esclient, org.gokb.cred.TitleInstancePackagePlatform.class) { kbc ->

        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null

        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid

        result.curatoryGroups = []
        kbc.pkg?.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }

        result.titleType = kbc.title?.niceName ?: 'Unknown'

        result.lastUpdatedDisplay = sdf.format(kbc.lastUpdated)

        result.url = kbc.url

        if (kbc.title?.niceName == 'Journal') {
          result.coverage = []

          ArrayList coverage_src = kbc.coverageStatements?.size() > 0 ? kbc.coverageStatements : [kbc]

          coverage_src.each { tcs ->
            def cst = [:]

            cst.startDate = tcs.startDate ? sdf.format(tcs.startDate) : ""
            cst.startVolume = tcs.startVolume ?: ""
            cst.startIssue = tcs.startIssue ?: ""
            cst.endDate = tcs.endDate ? sdf.format(tcs.endDate) : ""
            cst.endVolume = tcs.endVolume ?: ""
            cst.endIssue = tcs.endIssue ?: ""
            cst.embargo = tcs.embargo ?: ""
            cst.coverageNote = tcs.coverageNote ?: ""

            result.coverage.add(cst)
          }
        }

        result.tippPackage = kbc.pkg ? kbc.pkg.getLogEntityId() : ""
        result.tippPackageUuid = kbc.pkg ? kbc.pkg?.uuid : ""

        result.tippTitle = kbc.title ? kbc.title.getLogEntityId() : ""
        result.tippTitleUuid = kbc.title ? kbc.title?.uuid : ""

        result.hostPlatform = kbc.hostPlatform ? kbc.hostPlatform.getLogEntityId() : ""
        result.hostPlatformUuid = kbc.hostPlatform ? kbc.hostPlatform?.uuid : ""

        result.status = kbc.status?.value

        result.componentType=kbc.class.simpleName

        result
      }
  
      updateES(esclient, org.gokb.cred.Org.class) { kbc ->
        def result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.altname = []
        result.updater='org'
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        result.roles = []
        kbc.roles.each { role ->
          result.roles.add(role.value)
        }

        result.status = kbc.status?.value

        result.identifiers = []
        kbc.ids.each { identifier ->
          result.identifiers.add([namespace:identifier.namespace.value, value:identifier.value] );
        }

        result.componentType=kbc.class.simpleName
  
        result
      }

      updateES(esclient, org.gokb.cred.Platform.class) { kbc ->
        def result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.updater='platform'

        result.cpname = kbc.provider?.name

        result.provider = kbc.provider ? kbc.provider.getLogEntityId() : ""
        result.providerUuid = kbc.provider ? kbc.provider?.uuid : ""

        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.updater='platform'
        result.primaryUrl = kbc.primaryUrl
        result.status = kbc.status?.value
        
        result.identifiers = []
        kbc.ids.each { identifier ->
          result.identifiers.add([namespace:identifier.namespace.value, value:identifier.value] );
        }

        result.componentType=kbc.class.simpleName

        result
      }

    }
    catch ( Exception e ) {
      log.error("Problem",e);
    }

    running = false;
  }


  def updateES(esclient, domain, recgen_closure) {

    log.info("updateES(${domain}...)");
    cleanUpGorm();

    def count = 0;
    try {
      log.debug("updateES - ${domain.name}");

 
      def latest_ft_record = null;
      def highest_timestamp = 0;
      def highest_id = 0;
      FTControl.withNewTransaction {
        latest_ft_record = FTControl.findByDomainClassNameAndActivity(domain.name,'ESIndex')

        log.debug("result of findByDomain: ${domain} ${latest_ft_record}");
        if ( !latest_ft_record) {
          latest_ft_record=new FTControl(domainClassName:domain.name,activity:'ESIndex',lastTimestamp:0,lastId:0).save(flush:true, failOnError:true)
          log.debug("Create new FT control record, as none available for ${domain.name}");
        }
        else {
          highest_timestamp = latest_ft_record.lastTimestamp
          log.debug("Got existing ftcontrol record for ${domain.name} max timestamp is ${highest_timestamp} which is ${new Date(highest_timestamp)}");
        }
      }
      def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')
      def status_retired = RefdataCategory.lookupOrCreate('KBComponent.Status','Retired')

      log.debug("updateES ${domain.name} since ${latest_ft_record.lastTimestamp}");

      def total = 0;
      Date from = new Date(latest_ft_record.lastTimestamp);
  
      def countq = domain.executeQuery("select count(o.id) from "+domain.name+" as o where (( o.lastUpdated > :ts ) OR ( o.dateCreated > :ts )) ",[ts: from], [readonly:true])[0];
      log.debug("Will process ${countq} records");

      def q = domain.executeQuery("select o.id from "+domain.name+" as o where ((o.lastUpdated > :ts ) OR ( o.dateCreated > :ts )) order by o.lastUpdated, o.id",[ts: from], [readonly:true]);
    
      log.debug("Query completed.. processing rows...");

      // while (results.next()) {
      for (r_id in q) {
        if ( Thread.currentThread().isInterrupted() ) {
          log.debug("Job cancelling ..")
          break;
        }

        Object r = domain.get(r_id)
        log.debug("${r.id} ${domain.name} -- (rects)${r.lastUpdated} > (from)${from}");
        def idx_record = recgen_closure(r)

        def es_index = grailsApplication.config.gokb?.es?.index ?: "gokbg3"

        if ( idx_record != null ) {
          def recid = idx_record['_id'].toString()
          idx_record.remove('_id');
          
          def future = esclient.prepareIndex(es_index,'component',recid).setSource(idx_record)
          def result=future.get()
        }


        if ( r.lastUpdated?.getTime() > highest_timestamp ) {
          highest_timestamp = r.lastUpdated?.getTime();
        }
        highest_id=r.id

        count++
        total++

        if ( count > 250 ) {
          count = 0;
          log.debug("interim:: processed ${++total} out of ${countq} records (${domain.name}) - updating highest timestamp to ${highest_timestamp} interim flush");
          FTControl.withNewTransaction {
            latest_ft_record = FTControl.get(latest_ft_record.id);
            if ( latest_ft_record ) {
              latest_ft_record.lastTimestamp = highest_timestamp
              latest_ft_record.lastId = highest_id
              latest_ft_record.save(flush:true, failOnError:true);
            }
            else {
              log.error("Unable to locate free text control record with ID ${latest_ft_record.id}. Possibe parallel FT update");
            }
          }
          cleanUpGorm();
          synchronized(this) {
            Thread.yield()
            Thread.sleep(2000);
          }
        }
      }

      // update timestamp
      FTControl.withNewTransaction {
        latest_ft_record = FTControl.get(latest_ft_record.id);
        latest_ft_record.lastTimestamp = highest_timestamp
        latest_ft_record.lastId = highest_id
        latest_ft_record.save(flush:true, failOnError:true);
      }
      cleanUpGorm();

      log.info("final:: Processed ${total} out of ${countq} records for ${domain.name}. Max TS seen ${highest_timestamp} highest id with that TS: ${highest_id}");
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
  }

  def clearDownAndInitES() {
    if ( running == false ) {
      log.debug("Remove existing FTControl ..")
      FTControl.withTransaction {
        FTControl.executeUpdate("delete FTControl c");
      }
      updateFTIndexes();
    }
    else {
      log.info("FTUpdate already running")
      return "Job cancelled – FTUpdate was already running!";
    }
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }
 
}
