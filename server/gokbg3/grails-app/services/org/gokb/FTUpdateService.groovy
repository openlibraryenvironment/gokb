package org.gokb

import com.k_int.ESSearchService
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.opensearch.action.bulk.BulkItemResponse
import org.opensearch.action.bulk.BulkRequest
import org.opensearch.action.bulk.BulkResponse
import org.opensearch.action.index.IndexRequest
import org.opensearch.client.RequestOptions
import org.opensearch.common.xcontent.XContentType

@Transactional
class FTUpdateService {

  def ESWrapperService
  def sessionFactory
  def dateFormatService

  public static boolean running = false


  /**
   * Update ES.
   * The caller is responsible for running this function in a task if needed. This method
   * is responsible for ensuring only 1 FT index task runs at a time. It's a simple mutex.
   * see https://async.grails.org/latest/guide/index.html
   */
  def updateFTIndexes() {
    log.debug("updateFTIndexes")
    if (running == false) {
      running = true
      doFTUpdate()
      log.debug("FTUpdate done.")
      return new Date()
    }
    else {
      log.error("FTUpdate already running")
      return "Job cancelled – FTUpdate was already running!"
    }
  }


  def buildEsRecord (kbc) {
    def result = [:]

    switch (kbc.class) {
      case org.gokb.cred.Package:
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.contentType = kbc.contentType?.value ?: ""
        result.description = kbc.description
        result.descriptionURL = kbc.descriptionURL
        result.sortname = kbc.name
        result.altname = []
        result.listStatus = kbc.listStatus?.value ?: ""
        result.editStatus = kbc.editStatus?.value ?: ""
        result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.updater = 'pkg'
        result.titleCount = kbc.currentTippCount
        result.cpname = kbc.provider?.name
        result.provider = kbc.provider ? kbc.provider.getLogEntityId() : ""
        result.providerName = kbc.provider?.name ?: ""
        result.providerUuid = kbc.provider?.uuid ?: ""
        result.nominalPlatform = kbc.nominalPlatform ? kbc.nominalPlatform.getLogEntityId() : ""
        result.nominalPlatformName = kbc.nominalPlatform?.name ?: ""
        result.nominalPlatformUuid = kbc.nominalPlatform?.uuid ?: ""
        result.scope = kbc.scope?.value ?: ""
        result.global = kbc.global?.value ?: ""
        if (kbc.listVerifiedDate)
          result.listVerifiedDate = dateFormatService.formatIsoTimestamp(kbc.listVerifiedDate)
        if (kbc.source) {
          result.source = [
            id              : kbc.source.id,
            name            : kbc.source.name,
            automaticUpdates: kbc.source.automaticUpdates,
            url             : kbc.source.url,
            frequency       : (kbc.source.frequency?.value ?: ""),
          ]
          if (kbc.source.lastRun)
            result.source.lastRun = dateFormatService.formatIsoTimestamp(kbc.source.lastRun)
        }
        result.curatoryGroups = []
        kbc.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }
        result.status = kbc.status?.value ?: ""
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name,
                                  baseUrl      : idc.toComponent.namespace.baseUrl])
        }
        result.componentType = kbc.class.simpleName
        break
      case org.gokb.cred.Org:
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.altname = []
        result.updater = 'org'
        result.titleNamespace = kbc.titleNamespace?.value
        result.packageNamespace = kbc.packageNamespace?.value
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)
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
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name,
                                  baseUrl      : idc.toComponent.namespace.baseUrl])
        }
        result.componentType = kbc.class.simpleName
        result.platforms = []
        kbc.providedPlatforms?.each { plt ->
          def platform = [:]
          platform.uuid = plt.uuid ?: ""
          platform.url = plt.primaryUrl ?: ""
          platform.name = plt.name ?: ""
          result.platforms.add(platform)
        }
        break
      case org.gokb.cred.Platform:
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.updater = 'platform'
        result.cpname = kbc.provider?.name
        result.provider = kbc.provider ? kbc.provider.getLogEntityId() : ""
        result.providerUuid = kbc.provider ? kbc.provider.uuid : ""
        result.providerName = kbc.provider ? kbc.provider.name : ""
        result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)
        result.curatoryGroups = []
        kbc.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }
        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.updater = 'platform'
        result.primaryUrl = kbc.primaryUrl
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name,
                                  baseUrl      : idc.toComponent.namespace.baseUrl])
        }
        result.componentType = kbc.class.simpleName
        break
      case org.gokb.cred.JournalInstance:
        def current_pub = kbc.currentPublisher
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.updater = 'journal'
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        if (kbc.publishedFrom) result.publishedFrom = dateFormatService.formatDate(kbc.publishedFrom)
        if (kbc.publishedTo) result.publishedTo = dateFormatService.formatDate(kbc.publishedTo)
        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name,
                                  baseUrl      : idc.toComponent.namespace.baseUrl])
        }
        result.componentType = kbc.class.simpleName
        break
      case org.gokb.cred.DatabaseInstance:
        def current_pub = kbc.currentPublisher
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        if (kbc.publishedFrom) result.publishedFrom = dateFormatService.formatDate(kbc.publishedFrom)
        if (kbc.publishedTo) result.publishedTo = dateFormatService.formatDate(kbc.publishedTo)
        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name,
                                  baseUrl      : idc.toComponent.namespace.baseUrl])
        }
        result.componentType = kbc.class.simpleName
        break
      case org.gokb.cred.OtherInstance:
        def current_pub = kbc.currentPublisher
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        if (kbc.publishedFrom) result.publishedFrom = dateFormatService.formatDate(kbc.publishedFrom)
        if (kbc.publishedTo) result.publishedTo = dateFormatService.formatDate(kbc.publishedTo)
        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name,
                                  baseUrl      : idc.toComponent.namespace.baseUrl])
        }
        result.componentType = kbc.class.simpleName
        break
      case org.gokb.cred.BookInstance:
        def current_pub = kbc.currentPublisher
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        if (kbc.publishedFrom) result.publishedFrom = dateFormatService.formatDate(kbc.publishedFrom)
        if (kbc.publishedTo) result.publishedTo = dateFormatService.formatDate(kbc.publishedTo)
        if (kbc.dateFirstInPrint) result.dateFirstInPrint = dateFormatService.formatDate(kbc.dateFirstInPrint)
        if (kbc.dateFirstOnline) result.dateFirstOnline = dateFormatService.formatDate(kbc.dateFirstOnline)
        result.altname = []
        result.updater = 'book'
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name,
                                  baseUrl      : idc.toComponent.namespace.baseUrl])
        }
        result.componentType = kbc.class.simpleName
        break
      case org.gokb.cred.TitleInstancePackagePlatform:
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name ?: (kbc.title?.name ?: null)
        result.componentType = kbc.class.simpleName

        result.curatoryGroups = []
        kbc.pkg?.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }
        result.titleType = kbc.title?.niceName ?: 'Unknown'
        result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)
        result.url = kbc.url
        if (kbc.title?.niceName == 'Journal') {
          result.coverage = []
          ArrayList coverage_src = kbc.coverageStatements?.size() > 0 ? kbc.coverageStatements : [kbc]
          coverage_src.each { tcs ->
            def cst = [:]
            if (tcs.startDate) cst.startDate = dateFormatService.formatDate(tcs.startDate)
            cst.startVolume = tcs.startVolume ?: ""
            cst.startIssue = tcs.startIssue ?: ""
            if (tcs.endDate) cst.endDate = dateFormatService.formatDate(tcs.endDate)
            cst.endVolume = tcs.endVolume ?: ""
            cst.endIssue = tcs.endIssue ?: ""
            cst.embargo = tcs.embargo ?: ""
            cst.coverageNote = tcs.coverageNote ?: ""
            cst.coverageDepth = tcs.coverageDepth ? tcs.coverageDepth.value : ""
            result.coverage.add(cst)
          }
        }
        if (kbc.title?.niceName == 'Book') {
          // edition for eBooks
          def edition = [:]
          if (kbc.title?.editionDifferentiator) {
            edition.differentiator = kbc.title.editionDifferentiator
          }
          if (kbc.title?.editionStatement) {
            edition.statement = kbc.title.editionStatement
          }
          if (!edition.isEmpty()) {
            result.titleEdition = edition
          }
          // simple eBook fields
          result.titleVolumeNumber = kbc.title?.volumeNumber ?: ""
          if (kbc.title?.dateFirstInPrint) result.titleDateFirstInPrint = dateFormatService.formatDate(kbc.title.dateFirstInPrint)
          if (kbc.title?.dateFirstOnline) result.titleDateFirstOnline = dateFormatService.formatDate(kbc.title.dateFirstOnline)
          result.titleFirstEditor = kbc.title?.firstEditor ?: ""
          result.titleFirstAuthor = kbc.title?.firstAuthor ?: ""
          result.titleImprint = kbc.title?.imprint?.name ?: ""
        }

        if (kbc.pkg) {
          result.tippPackage = kbc.pkg.getLogEntityId()
          result.tippPackageName = kbc.pkg.name
          result.tippPackageUuid = kbc.pkg.uuid
        }

        if (kbc.hostPlatform) {
          result.hostPlatform = kbc.hostPlatform.getLogEntityId()
          result.hostPlatformName = kbc.hostPlatform.name
          result.hostPlatformUuid = kbc.hostPlatform.uuid
        }

        // title history
        result.titleHistory = []
        // publishers
        result.titlePublishers = []
        // variant names
        result.altname = []
        if (kbc.title) {
          result.tippTitle = kbc.title.getLogEntityId()
          result.tippTitleName = kbc.title.name
          result.tippTitleUuid = kbc.title.uuid
          result.tippTitleMedium = kbc.title.medium.value
          kbc.title.titleHistory?.each { he ->
            if (he.date) {
              def event = [:]
              event.date = dateFormatService.formatDate(he.date)
              event.from = []
              if (he.from) {
                event.from.addAll(he.from.collect { fe -> [id: fe?.id, uuid: fe?.uuid, name: fe?.name] })
              }
              event.to = []
              if (he.to) {
                event.to.addAll(he.to.collect { te -> [id: te?.id, uuid: te?.uuid, name: te?.name] })
              }
              event.id = he.id ?: ""
              result.titleHistory.add(event)
            }
          }
          kbc.title.publisher?.each { pub ->
            def publisher = [:]
            publisher.name = pub.name ?: ""
            publisher.id = pub.id ?: ""
            publisher.uuid = pub.uuid ?: ""
            result.titlePublishers.add(publisher)
          }
          kbc.title.variantNames.each { vn ->
            result.altname.add(vn.variantName)
          }
        }

        if (kbc.medium) result.medium = kbc.medium.value
        if (kbc.status) result.status = kbc.status.value
        if (kbc.publicationType) result.publicationType = kbc.publicationType.value

        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name,
                                  baseUrl      : idc.toComponent.namespace.baseUrl])
        }

        if (kbc.dateFirstOnline) result.dateFirstOnline = dateFormatService.formatDate(kbc.dateFirstOnline)
        if (kbc.dateFirstInPrint) result.dateFristInPrint = dateFormatService.formatDate(kbc.dateFirstInPrint)
        if (kbc.accessStartDate) result.accessStartDate = dateFormatService.formatDate(kbc.accessStartDate)
        if (kbc.accessEndDate) result.accessEndDate = dateFormatService.formatDate(kbc.accessEndDate)
        if (kbc.lastChangedExternal) result.lastChangedExternal = dateFormatService.formatDate(kbc.lastChangedExternal)

        if (kbc.publisherName) result.publisherName = kbc.publisherName
        if (kbc.subjectArea) result.subjectArea = kbc.subjectArea
        if (kbc.series) result.series = kbc.series
        if (kbc.volumeNumber) result.volumeNumber = kbc.volumeNumber
        if (kbc.editionStatement) result.editionStatement = kbc.editionStatement
        if (kbc.firstAuthor) result.firstAuthor = kbc.firstAuthor
        if (kbc.firstEditor) result.firstEditor = kbc.firstEditor
        if (kbc.parentPublicationTitleId) result.parentPublicationTitleId = kbc.parentPublicationTitleId
        if (kbc.precedingPublicationTitleId) result.precedingPublicationTitleId = kbc.precedingPublicationTitleId
        if (kbc.importId) result.importId = kbc.importId

        // prices
        result.prices = []
        kbc.prices?.each { p ->
          def price = [:]
          price.type = p.priceType?.value ?: ""
          price.amount = String.valueOf(p.price) ?: ""
          price.currency = p.currency?.value ?: ""
          if (p.startDate)
            price.startDate = dateFormatService.formatIsoTimestamp(p.startDate)
          if (p.endDate)
            price.endDate = dateFormatService.formatIsoTimestamp(p.endDate)
          result.prices.add(price)
        }
        break
      default:
        result = null
        break
    }
    result
  }


  synchronized def doFTUpdate() {
    log.debug("doFTUpdate")
    log.debug("Execute IndexUpdateJob starting at ${new Date()}")
    def esclient = ESWrapperService.getClient()
    try {
      updateES(esclient, org.gokb.cred.Package.class)
      updateES(esclient, org.gokb.cred.Org.class)
      updateES(esclient, org.gokb.cred.Platform.class)
      updateES(esclient, org.gokb.cred.JournalInstance.class)
      updateES(esclient, org.gokb.cred.DatabaseInstance.class)
      updateES(esclient, org.gokb.cred.OtherInstance.class)
      updateES(esclient, org.gokb.cred.BookInstance.class)
      updateES(esclient, org.gokb.cred.TitleInstancePackagePlatform.class)
    }
    catch (Exception e) {
      log.error("Problem", e)
    }
    running = false
  }


  def updateSingleItem(kbc) {
    def idx_record = buildEsRecord(kbc)
    def es_index = ESSearchService.indicesPerType.get(idx_record['componentType'])

    if (idx_record != null) {
      def recid = idx_record['_id'].toString()
      idx_record.remove('_id')
      def esClient = ESWrapperService.getClient()
      IndexRequest request = new IndexRequest(es_index).id(recid).source(idx_record)
      def result = esClient.index(request, RequestOptions.DEFAULT)
      log.info("UpdateSingleItem :: ES returned ${result}")
    }
  }


  def updateES(esClient, domain) {
    log.debug("updateES(${domain}...)")
    cleanUpGorm()
    def count = 0
    try {
      log.debug("updateES - ${domain.name}")
      def latest_ft_record = null
      def highest_timestamp = 0
      def highest_id = 0
      FTControl.withNewTransaction {
        latest_ft_record = FTControl.findByDomainClassNameAndActivity(domain.name, 'ESIndex')

        log.debug("result of findByDomain: ${domain} ${latest_ft_record}")
        if (!latest_ft_record) {
          latest_ft_record = new FTControl(domainClassName: domain.name, activity: 'ESIndex', lastTimestamp: 0, lastId: 0).save(flush: true, failOnError: true)
          log.debug("Create new FT control record, as none available for ${domain.name}")
        }
        else {
          highest_timestamp = latest_ft_record.lastTimestamp
          log.debug("Got existing ftcontrol record for ${domain.name} max timestamp is ${highest_timestamp} which is ${new Date(highest_timestamp)}")
        }
      }
      log.debug("updateES ${domain.name} since ${latest_ft_record.lastTimestamp}")

      def total = 0
      Date from = new Date(latest_ft_record.lastTimestamp)
      def countq = domain.executeQuery("select count(o.id) from " + domain.name + " as o where (( o.lastUpdated > :ts ) OR ( o.dateCreated > :ts )) ", [ts: from], [readonly: true])[0]
      log.debug("Will process ${countq} records")
      def q = domain.executeQuery("select o.id from " + domain.name + " as o where ((o.lastUpdated > :ts ) OR ( o.dateCreated > :ts )) order by o.lastUpdated, o.id", [ts: from], [readonly: true])
      log.debug("Query completed.. processing rows...")

      BulkRequest bulkRequest = new BulkRequest()
      for (r_id in q) {
        if (Thread.currentThread().isInterrupted()) {
          log.warn("Job cancelling ..")
          running = false
          break
        }
        Object r = domain.get(r_id)
        log.debug("${r.id} ${domain.name} -- (rects)${r.lastUpdated} > (from)${from}")
        def idx_record = buildEsRecord(r)
        if (idx_record != null) {
          IndexRequest singleRequest = new IndexRequest(ESSearchService.indicesPerType.get(idx_record['componentType']))
          singleRequest.id(idx_record['_id'].toString())
          idx_record.remove('_id')
          singleRequest.source((idx_record as JSON).toString(), XContentType.JSON)
          bulkRequest.add(singleRequest)
        }
        if (r.lastUpdated?.getTime() > highest_timestamp) {
          highest_timestamp = r.lastUpdated?.getTime()
        }
        highest_id = r.id
        count++
        total++
        if (count > 250) {
          count = 0
          log.debug("... interim:: processed ${total} out of ${countq} records (${domain.name}) - updating highest timestamp to ${highest_timestamp} interim flush")
          BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT)
          logBulkFailures(bulkResponse)
          log.debug("... BulkResponse: ${bulkResponse}")
          FTControl.withNewTransaction {
            latest_ft_record = FTControl.get(latest_ft_record.id)
            if (latest_ft_record) {
              latest_ft_record.lastTimestamp = highest_timestamp
              latest_ft_record.lastId = highest_id
              latest_ft_record.save(flush: true, failOnError: true)
            }
            else {
              log.error("Unable to locate free text control record with ID ${latest_ft_record.id}. Possibe parallel FT update")
            }
          }
          cleanUpGorm()
          synchronized (this) {
            Thread.yield()
          }
        }
      }
      if (count > 0) {
        BulkResponse bulkFinalResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT)
        log.debug("... final BulkResponse: ${bulkFinalResponse}")
        logBulkFailures(bulkFinalResponse)
      }
      // update timestamp
      if (total > 0) {
        FTControl.withNewTransaction {
          latest_ft_record = FTControl.get(latest_ft_record.id)
          latest_ft_record.lastTimestamp = highest_timestamp
          latest_ft_record.lastId = highest_id
          latest_ft_record.save(flush: true, failOnError: true)
        }
      }
      cleanUpGorm()
      log.debug("... final:: Processed ${total} out of ${countq} records for ${domain.name}. Max TS seen ${highest_timestamp} highest id with that TS: ${highest_id}")
    }
    catch (Exception e) {
      log.error("Problem with FT index", e)
    }
  }


  private void logBulkFailures(BulkResponse bulkResponse){
    if (bulkResponse.hasFailures()){
      for (BulkItemResponse bulkItemResponse : bulkResponse){
        if (bulkItemResponse.isFailed()){
          BulkItemResponse.Failure failure = bulkItemResponse.getFailure()
          log.debug("... Elasticsearch bulk operation failure: ${failure}")
        }
      }
    }
  }


  def cleanUpGorm() {
    log.debug("Clean up GORM")
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }


  def clearDownAndInit() {
    if (running == false) {
      log.debug("Remove existing FTControl ..")
      FTControl.withTransaction {
        def res = FTControl.executeUpdate("delete FTControl c")
        log.debug("Result: ${res}")
      }
      updateFTIndexes()
    }
    else {
      log.error("FTUpdate already running")
      return "Job cancelled – FTUpdate was already running!"
    }
  }


  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy")
  }
}
