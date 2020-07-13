package org.gokb


import grails.gorm.transactions.Transactional
import org.elasticsearch.action.bulk.BulkRequestBuilder
import com.k_int.ConcurrencyManagerService.Job
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

  final static def components = [
    'org.gokb.cred.Package',
    'org.gokb.cred.Org',
    'org.gokb.cred.Platform',
    'org.gokb.cred.JournalInstance',
    'org.gokb.cred.DatabaseInstance',
    'org.gokb.cred.BookInstance',
    'org.gokb.cred.OtherInstance',
    'org.gokb.cred.TitleInstancePackagePlatform'
  ]


  /**
   * Update ES.
   * The caller is responsible for running this function in a task if needed. This method
   * is responsible for ensuring only 1 FT index task runs at a time. It's a simple mutex.
   * see https://async.grails.org/latest/guide/index.html
   */
  def synchronized updateFTIndexes(Job j = null) {
    log.debug("updateFTIndexes");
    def endTime = null

    if ( running == false ) {
      running = true;
      doFTUpdate(j)
      log.info("FTUpdate done.")
    }
    else {
      log.info("FTUpdate already running")
      j?.endTime = new Date()
    }
  }

  def doFTUpdate(Job j = null) {
    log.debug("doFTUpdate");

    log.debug("Execute IndexUpdateJob starting at ${new Date()}");
    def start_time = System.currentTimeMillis();
    def esclient = ESWrapperService.getClient()
    int total = 0
    int startc = 0

    log.debug("calculating count ..")

    components.each { c ->
      def ft_record = getControlRecord(c)
      Date from = new Date(ft_record.lastTimestamp);

      def countq = KBComponent.executeQuery("select count(o.id) from "+c+" as o where (( o.lastUpdated > :ts ) OR ( o.dateCreated > :ts )) ",[ts: from], [readonly:true])[0];
      log.debug("Will process ${countq} records");

      total += countq
    }

    try {

      startc += updateES(esclient, org.gokb.cred.Package.class, j, startc, total) { kbc ->

        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.contentType = kbc.contentType?.value
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
        result.titleCount = kbc.currentTippCount

        result.cpname = kbc.provider?.name

        result.provider = kbc.provider ? kbc.provider.getLogEntityId() : ""
        result.providerName = kbc.provider?.name ?: ""
        result.providerUuid = kbc.provider?.uuid ?: ""

        result.nominalPlatform = kbc.nominalPlatform ? kbc.nominalPlatform.getLogEntityId() : ""
        result.nominalPlatformName = kbc.nominalPlatform?.name ?: ""
        result.nominalPlatformUuid = kbc.nominalPlatform?.uuid ?: ""

        result.scope = kbc.scope ? kbc.scope.value : ""
        result.listVerifiedDate = kbc.listVerifiedDate ? sdf.format(kbc.listVerifiedDate) : ""

        result.curatoryGroups = []
        kbc.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }

        result.status = kbc.status?.value

        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids','Active').each { idc ->
          result.identifiers.add([namespace:idc.toComponent.namespace.value, value:idc.toComponent.value] );
        }

        result.componentType=kbc.class.simpleName

        result
      }

      startc += updateES(esclient, org.gokb.cred.Org.class, j, startc, total) { kbc ->
        def result = [:]
        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.altname = []
        result.updater='org'
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.lastUpdatedDisplay = sdf.format(kbc.lastUpdated)

        result.roles = []
        kbc.roles.each { role ->
          result.roles.add(role.value)
        }

        result.curatoryGroups = []
        kbc.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }

        result.status = kbc.status?.value

        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids','Active').each { idc ->
          result.identifiers.add([namespace:idc.toComponent.namespace.value, value:idc.toComponent.value] );
        }

        result.componentType=kbc.class.simpleName

        result
      }

      startc += updateES(esclient, org.gokb.cred.Platform.class, j, startc, total) { kbc ->
        def result = [:]
        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.updater='platform'

        result.cpname = kbc.provider?.name

        result.provider = kbc.provider ? kbc.provider.getLogEntityId() : ""
        result.providerUuid = kbc.provider ? kbc.provider?.uuid : ""
        result.lastUpdatedDisplay = sdf.format(kbc.lastUpdated)

        result.curatoryGroups = []
        kbc.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }

        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.updater='platform'
        result.primaryUrl = kbc.primaryUrl
        result.status = kbc.status?.value

        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids','Active').each { idc ->
          result.identifiers.add([namespace:idc.toComponent.namespace.value, value:idc.toComponent.value] );
        }

        result.componentType=kbc.class.simpleName

        result
      }

      startc += updateES(esclient, org.gokb.cred.JournalInstance.class, j, startc, total) { kbc ->

        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        def current_pub = kbc.currentPublisher

        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
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
        kbc.getCombosByPropertyNameAndStatus('ids','Active').each { idc ->
          result.identifiers.add([namespace:idc.toComponent.namespace.value, value:idc.toComponent.value] );
        }

        result.componentType=kbc.class.simpleName

        // log.debug("process ${result}");
        result
      }

      startc += updateES(esclient, org.gokb.cred.DatabaseInstance.class, j, startc, total) { kbc ->

        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        def current_pub = kbc.currentPublisher

        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
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
        kbc.getCombosByPropertyNameAndStatus('ids','Active').each { idc ->
          result.identifiers.add([namespace:idc.toComponent.namespace.value, value:idc.toComponent.value] );
        }

        result.componentType=kbc.class.simpleName

        // log.debug("process ${result}");
        result
      }

      startc += updateES(esclient, org.gokb.cred.BookInstance.class, j, startc, total) { kbc ->

        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        def current_pub = kbc.currentPublisher

        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
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
        kbc.getCombosByPropertyNameAndStatus('ids','Active').each { idc ->
          result.identifiers.add([namespace:idc.toComponent.namespace.value, value:idc.toComponent.value] );
        }

        result.componentType=kbc.class.simpleName

        // log.debug("process ${result}");
        result
      }

      startc += updateES(esclient, org.gokb.cred.OtherInstance.class, j, startc, total) { kbc ->

        def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss');
        def result = null
        def current_pub = kbc.currentPublisher

        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
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
        kbc.getCombosByPropertyNameAndStatus('ids','Active').each { idc ->
          result.identifiers.add([namespace:idc.toComponent.namespace.value, value:idc.toComponent.value] );
        }

        result.componentType=kbc.class.simpleName

        // log.debug("process ${result}");
        result
      }

      startc += updateES(esclient, org.gokb.cred.TitleInstancePackagePlatform.class, j, startc, total) { kbc ->

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
            cst.coverageDepth = tcs.coverageDepth ? tcs.coverageDepth.value : ""

            result.coverage.add(cst)
          }
        }

        result.tippPackage = kbc.pkg ? kbc.pkg.getLogEntityId() : ""
        result.tippPackageName = kbc.pkg ? kbc.pkg.name : ""
        result.tippPackageUuid = kbc.pkg ? kbc.pkg.uuid : ""

        result.tippTitle = kbc.title ? kbc.title.getLogEntityId() : ""
        result.tippTitleName = kbc.title ? kbc.title.name : ""
        result.tippTitleUuid = kbc.title ? kbc.title.uuid : ""

        result.hostPlatform = kbc.hostPlatform ? kbc.hostPlatform.getLogEntityId() : ""
        result.hostPlatformName = kbc.hostPlatform ? kbc.hostPlatform.name : ""
        result.hostPlatformUuid = kbc.hostPlatform ? kbc.hostPlatform.uuid : ""

        result.status = kbc.status?.value

        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids','Active').each { idc ->
          result.identifiers.add([namespace:idc.toComponent.namespace.value, value:idc.toComponent.value] );
        }

        result.componentType=kbc.class.simpleName

        result
      }

    }
    catch ( Exception e ) {
      log.error("Problem",e);
    }

    running = false;
    j.endTime = new Date()
  }

  private def getControlRecord(domainName) {
    def latest_ft_record = null
    FTControl.withNewTransaction {
      latest_ft_record = FTControl.findByDomainClassNameAndActivity(domainName,'ESIndex')

      log.debug("result of findByDomain: ${domainName} ${latest_ft_record}");
      if ( !latest_ft_record) {
        latest_ft_record=new FTControl(domainClassName:domainName,activity:'ESIndex',lastTimestamp:0,lastId:0).save(flush:true, failOnError:true)
        log.debug("Create new FT control record, as none available for ${domainName}");
      }
    }
    return latest_ft_record
  }

  def updateES(esclient, domain, j, startc, total, recgen_closure) {

    log.info("updateES(${domain}...)");
    cleanUpGorm();
    int ctotal = 0
    int count = 0;

    try {
      log.debug("updateES - ${domain.name}");
      def latest_ft_record = getControlRecord(domain.name);
      def highest_timestamp = latest_ft_record.lastTimestamp;
      Long highest_id = 0;
      def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')
      def status_retired = RefdataCategory.lookupOrCreate('KBComponent.Status','Retired')

      log.debug("updateES ${domain.name} since ${highest_timestamp}");
      Date from = new Date(latest_ft_record.lastTimestamp);

      def countq = domain.executeQuery("select count(o.id) from "+domain.name+" as o where (( o.lastUpdated > :ts ) OR ( o.dateCreated > :ts )) ",[ts: from], [readonly:true])[0];
      log.debug("Will process ${countq} records");

      def q = domain.executeQuery("select o.id from "+domain.name+" as o where ((o.lastUpdated > :ts ) OR ( o.dateCreated > :ts )) order by o.lastUpdated, o.id",[ts: from], [readonly:true]);

      log.debug("Query completed.. processing rows...");

      BulkRequestBuilder bulkRequest = esclient.prepareBulk();

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

          bulkRequest.add(esclient.prepareIndex(es_index,'component',recid).setSource(idx_record))
        }


        if ( r.lastUpdated?.getTime() > highest_timestamp ) {
          highest_timestamp = r.lastUpdated?.getTime();
        }
        highest_id=r.id

        count++
        ctotal++

        if ( count > 250 ) {
          count = 0;
          log.debug("interim:: processed ${ctotal} out of ${countq} records (${domain.name}) - updating highest timestamp to ${highest_timestamp} interim flush");
          j?.setProgress(startc+ctotal,total)
          def bulkResponse = bulkRequest.get()
          bulkRequest = esclient.prepareBulk();
          log.debug("BulkResponse: ${bulkResponse}")

          FTControl.withNewTransaction {
            latest_ft_record = FTControl.get(latest_ft_record.id);

            log.debug("Got lastest FT: ${latest_ft_record}")
            log.debug("TS: ${latest_ft_record.lastTimestamp} - ID: ${latest_ft_record.lastId}")

            if ( latest_ft_record ) {
              latest_ft_record.lastTimestamp = highest_timestamp
              latest_ft_record.lastId = highest_id
              latest_ft_record = latest_ft_record.merge(flush:true);
            }
            else {
              log.error("Unable to locate free text control record with ID ${latest_ft_record.id}. Possibe parallel FT update");
            }
          }

          log.debug("Updated FTControl ..")
          cleanUpGorm();
          synchronized(this) {
            Thread.yield()
          }
        }
      }

      log.debug("Processed ${ctotal} records of type ${domain.name}")

      if (count > 0) {
        def bulkFinalResponse = bulkRequest.get()
        j?.setProgress(startc+ctotal,total)
        log.debug("Final BulkResponse: ${bulkFinalResponse}")
      }

      j?.message("Processed ${ctotal} records of type ${domain.name}".toString())

      // update timestamp
      FTControl.withNewTransaction {
        latest_ft_record = FTControl.get(latest_ft_record.id);
        latest_ft_record.lastTimestamp = highest_timestamp
        latest_ft_record.lastId = highest_id
        latest_ft_record = latest_ft_record.merge(flush:true);

        log.debug("Merged ..")
        cleanUpGorm();
      }

      log.info("final:: Processed ${total} out of ${countq} records for ${domain.name}. Max TS seen ${highest_timestamp} highest id with that TS: ${highest_id}");
    }
    catch ( Exception e ) {
      j?.message("Failed processing records of type ${domain.name}!".toString())
      log.error("Problem with FT index",e);
    }
    finally {
      log.debug("Completed processing on ${domain.name} - saved ${count} records");
    }

    return ctotal
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
    log.debug("Done..")
  }

  def clearDownAndInitES(Job j = null) {
    def endTime = null
    if ( running == false ) {
      log.debug("Remove existing FTControl ..")
      components.each {
        log.debug("${it} ..")
        def result = FTControl.findByDomainClassName(it);

        if (result) {
          result.delete(flush:true)
          log.debug("Deleted control for ${it}")
        }
        else {
          log.debug("Control not found!")
        }
      }
      log.debug("Done..")
      updateFTIndexes(j);
    }
    else {
      log.info("FTUpdate already running")
      j?.message("Skipped - FTUpdate was alreay running")
      j.endTime = new Date()
    }
    return endTime
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

}
