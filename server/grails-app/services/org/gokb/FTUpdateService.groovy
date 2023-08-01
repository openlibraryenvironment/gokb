package org.gokb

import com.k_int.ESSearchService
import com.k_int.ClassUtils
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.*
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
  def grailsApplication

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
    Map<String, Object> result = [:]
    result._id = "${kbc.class.name}:${kbc.id}"
    result.uuid = kbc.uuid
    result.name = kbc.name
    result.sortname = kbc.name
    result.status = kbc.status?.value ?: ""
    result.identifiers = []
    kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
      Identifier id_obj = ClassUtils.deproxy(idc.toComponent)
      result.identifiers.add([namespace    : id_obj.namespace.value,
                              value        : id_obj.value,
                              namespaceName: id_obj.namespace.name ?: "",
                              baseUrl      : id_obj.namespace.baseUrl ?: ""])
    }
    result.componentType = kbc.class.simpleName
    result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
    result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)

    switch (kbc.class) {
      case Package:
        result.contentType = kbc.contentType?.value ?: ""
        result.description = kbc.description
        result.descriptionURL = kbc.descriptionURL
        result.listStatus = kbc.listStatus?.value ?: ""
        result.editStatus = kbc.editStatus?.value ?: ""
        result.scope = kbc.scope?.value ?: ""
        result.global = kbc.global?.value ?: ""

        result.altname = []
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
        result.nominalPlatformUrl = kbc.nominalPlatform?.primaryUrl ?: ""
        result.nominalPlatformUuid = kbc.nominalPlatform?.uuid ?: ""

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

        break
      case Org:
        result.updater = 'org'
        result.titleNamespace = kbc.titleNamespace?.value
        result.packageNamespace = kbc.packageNamespace?.value
        result.preferredShortname = kbc.preferredShortname ?: ""

        result.altname = []

        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        result.roles = []

        kbc.roles.each { role ->
          result.roles.add(role.value)
        }

        result.curatoryGroups = []

        kbc.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }

        result.platforms = []

        kbc.providedPlatforms?.each { plt ->
          def platform = [:]
          platform.uuid = plt.uuid ?: ""
          platform.url = plt.primaryUrl ?: ""
          platform.name = plt.name ?: ""
          result.platforms.add(platform)
        }

        break
      case Platform:
        result.updater = 'platform'
        result.cpname = kbc.provider?.name
        result.primaryUrl = kbc.primaryUrl
        result.provider = kbc.provider ? kbc.provider.getLogEntityId() : ""
        result.providerUuid = kbc.provider ? kbc.provider.uuid : ""
        result.providerName = kbc.provider ? kbc.provider.name : ""

        result.curatoryGroups = []

        kbc.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }

        result.altname = []

        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        break
      case JournalInstance:
        result.updater = 'journal'
        def current_pub = kbc.currentPublisher
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""

        if (kbc.publishedFrom) result.publishedFrom = dateFormatService.formatDate(kbc.publishedFrom)
        if (kbc.publishedTo) result.publishedTo = dateFormatService.formatDate(kbc.publishedTo)

        result.altname = []

        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        break
      case DatabaseInstance:
        result.updater = 'database'
        def current_pub = kbc.currentPublisher
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""

        if (kbc.publishedFrom) result.publishedFrom = dateFormatService.formatDate(kbc.publishedFrom)
        if (kbc.publishedTo) result.publishedTo = dateFormatService.formatDate(kbc.publishedTo)

        result.altname = []

        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        break
      case OtherInstance:
        result.updater = 'other'
        def current_pub = kbc.currentPublisher
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""

        if (kbc.publishedFrom) result.publishedFrom = dateFormatService.formatDate(kbc.publishedFrom)
        if (kbc.publishedTo) result.publishedTo = dateFormatService.formatDate(kbc.publishedTo)

        result.altname = []

        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        break
      case BookInstance:
        result.updater = 'book'
        def current_pub = kbc.currentPublisher
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        result.editionStatement = kbc.editionStatement ?: ""
        result.volumeNumber = kbc.volumeNumber ?: ""

        if (kbc.publishedFrom) result.publishedFrom = dateFormatService.formatDate(kbc.publishedFrom)
        if (kbc.publishedTo) result.publishedTo = dateFormatService.formatDate(kbc.publishedTo)
        if (kbc.dateFirstInPrint) result.dateFirstInPrint = dateFormatService.formatDate(kbc.dateFirstInPrint)
        if (kbc.dateFirstOnline) result.dateFirstOnline = dateFormatService.formatDate(kbc.dateFirstOnline)

        result.altname = []

        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }

        break
      case TitleInstancePackagePlatform:
        result.updater = 'tipp'
        def ti = kbc.title ? ClassUtils.deproxy(kbc.title) : null
        result.name = kbc.name ?: (ti.name ?: null)

        result.curatoryGroups = []
        kbc.pkg?.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }
        result.titleType = ti?.niceName ?: 'Unknown'
        result.url = kbc.url

        if (ti?.niceName == 'Journal') {
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
        else if (ti?.niceName == 'Book') {
          // edition for eBooks
          def edition = [:]
          if (ti?.editionDifferentiator) {
            edition.differentiator = ti.editionDifferentiator
          }
          if (ti?.editionStatement) {
            edition.statement = ti.editionStatement
          }
          if (!edition.isEmpty()) {
            result.titleEdition = edition
          }
          // simple eBook fields
          result.titleVolumeNumber = ti?.volumeNumber ?: ""
          if (ti?.dateFirstInPrint) result.titleDateFirstInPrint = dateFormatService.formatDate(ti.dateFirstInPrint)
          if (ti?.dateFirstOnline) result.titleDateFirstOnline = dateFormatService.formatDate(ti.dateFirstOnline)
          result.titleFirstEditor = ti?.firstEditor ?: ""
          result.titleFirstAuthor = ti?.firstAuthor ?: ""
          result.titleImprint = ti?.imprint?.name ?: ""
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
          result.hostPlatformUrl = kbc.hostPlatform.primaryUrl
        }

        // title history
        result.titleHistory = []
        // publishers
        result.titlePublishers = []
        // variant names#
        result.altname = []

        if (ti) {
          result.tippTitle = ti.getLogEntityId()
          result.tippTitleName = ti.name
          result.tippTitleUuid = ti.uuid
          result.tippTitleMedium = ti.medium?.value

          ti.titleHistory?.each { he ->
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

          ti.publisher?.each { pub ->
            def publisher = [:]
            publisher.name = pub.name ?: ""
            publisher.id = pub.id ?: ""
            publisher.uuid = pub.uuid ?: ""
            result.titlePublishers.add(publisher)
          }
          ti.variantNames.each { vn ->
            result.altname.add(vn.variantName)
          }
        }

        if (kbc.medium) result.medium = kbc.medium.value
        if (kbc.publicationType) result.publicationType = kbc.publicationType.value

        if (kbc.dateFirstOnline) result.dateFirstOnline = dateFormatService.formatDate(kbc.dateFirstOnline)
        if (kbc.dateFirstInPrint) result.dateFirstInPrint = dateFormatService.formatDate(kbc.dateFirstInPrint)
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
      updateES(esclient, Package.class)
      updateES(esclient, Org.class)
      updateES(esclient, Platform.class)
      updateES(esclient, JournalInstance.class)
      updateES(esclient, DatabaseInstance.class)
      updateES(esclient, OtherInstance.class)
      updateES(esclient, BookInstance.class)
      updateES(esclient, TitleInstancePackagePlatform.class)
    }
    catch (Exception e) {
      log.error("Problem", e)
    }
    running = false
  }

  synchronized def doBackgroundReindex() {
    log.debug("doFTUpdate")
    log.debug("Execute IndexUpdateJob starting at ${new Date()}")
    def esclient = ESWrapperService.getClient()
    try {
      updateES(esclient, Package.class, true)
      updateES(esclient, Org.class, true)
      updateES(esclient, Platform.class, true)
      updateES(esclient, JournalInstance.class, true)
      updateES(esclient, DatabaseInstance.class, true)
      updateES(esclient, OtherInstance.class, true)
      updateES(esclient, BookInstance.class, true)
      updateES(esclient, TitleInstancePackagePlatform.class, true)
    }
    catch (Exception e) {
      log.error("Problem", e)
    }
    running = false
  }

  def updateSingleItem(kbc) {
    def idx_record = buildEsRecord(kbc)
    def es_index = grailsApplication.config.getProperty('gokb.es.indices.' + ESSearchService.indicesPerType.get(idx_record['componentType']))

    if (idx_record != null) {
      def recid = idx_record['_id'].toString()
      idx_record.remove('_id')
      def esClient = ESWrapperService.getClient()
      IndexRequest request = new IndexRequest(es_index).id(recid).source(idx_record)
      def result = esClient.index(request, RequestOptions.DEFAULT)
      log.debug("UpdateSingleItem :: ES returned ${result}")
    }
  }


  def updateES(esClient, domain, boolean reindex = false) {
    log.debug("updateES(${domain}...)")
    cleanUpGorm()
    def count = 0
    try {
      log.debug("updateES - ${domain.name}")
      def latest_ft_record = null
      def highest_timestamp = 0
      def highest_id = 0
      def activity_type = reindex ? 'ESReindex' : 'ESIndex'

      FTControl.withTransaction {
        latest_ft_record = FTControl.findByDomainClassNameAndActivity(domain.name, activity_type)

        log.debug("result of findByDomain: ${domain} ${latest_ft_record}")
        if (!latest_ft_record) {
          latest_ft_record = new FTControl(domainClassName: domain.name, activity: activity_type, lastTimestamp: 0, lastId: 0).save(flush: true, failOnError: true)
          log.debug("Create new ${activity_type} FT control record, as none available for ${domain.name}")
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
          IndexRequest singleRequest = new IndexRequest(grailsApplication.config.getProperty('gokb.es.indices.' + ESSearchService.indicesPerType.get(idx_record['componentType'])))
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

          if (bulkResponse.hasFailures()) {
            logBulkFailures(bulkResponse)
            log.error("Bulk Update had errors, skipping domain ${domain}!")
            break
          }

          log.debug("... BulkResponse: ${bulkResponse}")

          FTControl.withTransaction {
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
        }
      }
      if (count > 0) {
        BulkResponse bulkFinalResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT)
        log.debug("... final BulkResponse: ${bulkFinalResponse}")
        logBulkFailures(bulkFinalResponse)
      }
      // update timestamp
      if (total > 0) {
        FTControl.withTransaction {
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
          log.error("... opensearch bulk operation failure: ${failure}")
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
