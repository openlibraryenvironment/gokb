package org.gokb

import com.k_int.ESSearchService
import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job
import grails.converters.JSON
import org.gokb.cred.*
import org.opensearch.action.bulk.BulkItemResponse
import org.opensearch.action.bulk.BulkRequest
import org.opensearch.action.bulk.BulkResponse
import org.opensearch.action.index.IndexRequest
import org.opensearch.client.RequestOptions
import org.opensearch.common.xcontent.XContentType

import groovy.transform.Synchronized

import java.util.concurrent.TimeUnit

class FTUpdateService {

  def ESWrapperService
  def sessionFactory
  def dateFormatService
  def grailsApplication

  public static boolean packagesRunning = false
  public static boolean orgsRunning = false
  public static boolean platformsRunning = false
  public static boolean titlesRunning = false
  public static boolean tippsRunning = false

  /**
   * Update ES.
   * The caller is responsible for running this function in a task if needed. This method
   * is responsible for ensuring only 1 FT index task runs at a time. It's a simple mutex.
   * see https://async.grails.org/latest/guide/index.html
   */
  def updateFTIndexes(Job j = null) {
    log.debug("updateFTIndexes")

    ['packages','orgs','platforms','titles', 'tipps'].each { indexType ->
      if (this."${indexType}Running" == false) {
        this."${indexType}Update"(j)
      } else {
        if (j) j.message("Indexing for $indexType is already running.. skip")
        log.debug("Skipping indexing for $indexType .. already running!")
      }
    }

    return new Date()
  }


  def buildEsRecord (proxy) {
    def result = [:]
    def kbc = KBComponent.deproxy(proxy)
    result._id = "${kbc.class.name}:${kbc.id}"
    result.id = kbc.id
    result.uuid = kbc.uuid
    result.name = kbc.name
    result.sortname = kbc.name
    result.shortcode = kbc.shortcode
    result.status = kbc.status?.value ?: ""
    result.identifiers = []
    kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
      Identifier id_obj = Identifier.get(idc.toComponent.id)

      result.identifiers.add([
        namespace    : id_obj.namespace.value,
        value        : id_obj.value,
        namespaceName: id_obj.namespace.name ?: "",
        baseUrl      : id_obj.namespace.baseUrl ?: "",
        type         : id_obj.namespace.family ?: "",
        normval      : id_obj.normname
      ])
    }
    result.componentType = kbc.class.simpleName
    result.dateCreated = dateFormatService.formatIsoTimestamp(kbc.dateCreated)
    result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated ?: kbc.dateCreated)

    result.subjects = []

    kbc.activeSubjectsInfo.each { asi ->
      result.subjects << asi
    }

    switch (kbc.class) {
      case Package:
        result.contentType = kbc.contentType?.value ?: ""
        result.description = kbc.description
        result.descriptionURL = kbc.descriptionURL
        result.listStatus = kbc.listStatus?.value ?: ""
        result.editStatus = kbc.editStatus?.value ?: ""
        result.scope = kbc.scope?.value ?: ""
        result.global = kbc.global?.value ?: ""
        result.globalNote = kbc.globalNote

        if (grailsApplication.config.getProperty('gokb.stableUriBase')) {
          result.uri = "${grailsApplication.config.getProperty('gokb.stableUriBase')}/package/${kbc.uuid}".toString()
        }

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
        result.contentProvider = kbc.contentProvider ? kbc.contentProvider.getLogEntityId() : ""
        result.contentProviderName = kbc.contentProvider?.name ?: ""
        result.contentProviderUuid = kbc.contentProvider?.uuid ?: ""
        result.nominalPlatform = kbc.nominalPlatform ? kbc.nominalPlatform.getLogEntityId() : ""
        result.nominalPlatformName = kbc.nominalPlatform?.name ?: ""
        result.nominalPlatformUrl = kbc.nominalPlatform?.primaryUrl ?: ""
        result.nominalPlatformUuid = kbc.nominalPlatform?.uuid ?: ""
        result.startYear = kbc.startYear
        result.endYear = kbc.endYear

        if (kbc.listVerifiedDate)
          result.listVerifiedDate = dateFormatService.formatIsoTimestamp(kbc.listVerifiedDate)

        if (kbc.source) {
          result.source = [
            id              : kbc.source.id,
            name            : kbc.source.name,
            automaticUpdates: kbc.source.automaticUpdates,
            url             : kbc.source.url,
            frequency       : (kbc.source.frequency?.value ?: ""),
            importConfig    : (kbc.source.importConfig?.value ?: "")
          ]
          if (kbc.source.lastRun)
            result.source.lastRun = dateFormatService.formatIsoTimestamp(kbc.source.lastRun)
        }

        result.curatoryGroups = []

        kbc.curatoryGroups?.each { cg ->
          def cgobj = CuratoryGroup.get(cg.id)
          result.curatoryGroups.add(cgobj.name)
        }

        break
      case Org:
        result.updater = 'org'
        result.titleNamespace = kbc.titleNamespace?.value
        result.titleNamespaceSerial = kbc.titleNamespaceSerial?.value
        result.titleNamespaceMonograph = kbc.titleNamespaceMonograph?.value
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
          def cgobj = CuratoryGroup.get(cg.id)
          result.curatoryGroups.add(cgobj.name)
        }

        result.platforms = []

        kbc.providedPlatforms?.each { plt ->
          def pobj = Platform.get(plt.id)

          if (pobj.status.value == 'Current') {
            def platform = [:]
            platform.uuid = pobj.uuid ?: ""
            platform.url = pobj.primaryUrl ?: ""
            platform.name = pobj.name ?: ""
            result.platforms.add(platform)
          }
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
          def cgobj = CuratoryGroup.get(cg.id)
          result.curatoryGroups.add(cgobj.name)
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

        result.titleHistory = []

        kbc.titleHistory?.each { he ->
          if (he.date) {
            Map event = [
              date: dateFormatService.formatDate(he.date),
              from: [],
              to: [],
              id: (he.id ?: "")
            ]

            if (he.from) {
              event.from.addAll(he.from.collect { fe -> [id: fe?.id, uuid: fe?.uuid, name: fe?.name] })
            }

            if (he.to) {
              event.to.addAll(he.to.collect { te -> [id: te?.id, uuid: te?.uuid, name: te?.name] })
            }

            result.titleHistory.add(event)
          }
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
        result.firstAuthor = kbc.firstAuthor ?: ""
        result.firstEditor = kbc.firstEditor ?: ""

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
        TitleInstance ti = kbc.title ? TitleInstance.get(kbc.title.id) : null
        Package pkg = Package.get(kbc.pkg.id)

        result.curatoryGroups = []

        pkg?.curatoryGroups?.each { cg ->
          def cgobj = CuratoryGroup.get(cg.id)
          result.curatoryGroups.add(cgobj.name)
        }
        result.titleType = ti?.niceName ?: 'Unknown'
        result.url = kbc.url

        if (ti?.niceName == 'Journal') {
          long startCoverage = new Date().getTime()
          result.coverage = []
          def coverage_src = kbc.coverageStatements?.size() > 0 ? kbc.coverageStatements : [kbc]
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
          log.info("Journal's Coverage mapping lasted: ${new Date().getTime() - startCoverage} ms.")
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
          result.titleIdentifiers = []
          result.titleSubjects = []

          ti.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
            Identifier id_obj = Identifier.get(idc.toComponent.id)

            result.titleIdentifiers.add([
              namespace    : id_obj.namespace.value,
              value        : id_obj.value,
              namespaceName: id_obj.namespace.name ?: "",
              baseUrl      : id_obj.namespace.baseUrl ?: "",
              type         : id_obj.namespace.family ?: ""
            ])
          }

          ti.activeSubjectsInfo.each { asi ->
            result.titleSubjects << asi
          }

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

  def doBackgroundReindex(j) {
    log.debug("doFTUpdate")
    log.debug("Execute IndexUpdateJob starting at ${new Date()}")
    def esclient = ESWrapperService.getClient()

    try {
      updateES(esclient, Package.class, j, true)
      updateES(esclient, Org.class, j, true)
      updateES(esclient, Platform.class, j, true)
      updateES(esclient, JournalInstance.class, j, true)
      updateES(esclient, DatabaseInstance.class, j, true)
      updateES(esclient, OtherInstance.class, j, true)
      updateES(esclient, BookInstance.class, j, true)
      updateES(esclient, TitleInstancePackagePlatform.class, j, true)
    }
    catch (Exception e) {
      log.error("Problem", e)
    }

    return new Date()
  }

  def updateSingleItem(kbc) {
    def idx_record = buildEsRecord(kbc)
    def es_index = grailsApplication.config.getProperty('gokb.es.indices.' + ESWrapperService.indicesPerType.get(idx_record['componentType']))

    if (idx_record != null) {
      def recid = idx_record['_id'].toString()
      idx_record.remove('_id')
      def esClient = ESWrapperService.getClient()
      IndexRequest request = new IndexRequest(es_index).id(recid).source(idx_record)
      def result = esClient.index(request, RequestOptions.DEFAULT)
      log.debug("UpdateSingleItem :: ES returned ${result}")
    }
  }

  def triggerUpdateForClass(cls, Job j = null) {
    log.debug("triggerUpdateForClass")
    def indexType = ESWrapperService.indicesPerType[cls.simpleName]

    if (this."${indexType}Running" == false) {
      this."${indexType}Running" = true
      this."${indexType}Update"(j)
      log.debug("FTUpdate done.")

      return new Date()
    }
    else {
      if (j) j.message("Indexing for $indexType is already running.. skip")
      log.debug("FTUpdate for index $indexType already running")

      return "Job cancelled – FTUpdate for index $indexType was already running!"
    }
  }

  private void packagesUpdate(j) {
    packagesRunning = true
    def esclient = ESWrapperService.getClient()
    updateES(esclient, Package.class, j)
    packagesRunning = false
  }

  private void orgsUpdate(j) {
    orgsRunning = true
    def esclient = ESWrapperService.getClient()
    updateES(esclient, Org.class, j)
    orgsRunning = false
  }

  private void platformsUpdate(j) {
    platformsRunning = true
    def esclient = ESWrapperService.getClient()
    updateES(esclient, Platform.class, j)
    platformsRunning = false
  }

  private void titlesUpdate(j) {
    titlesRunning = true
    def esclient = ESWrapperService.getClient()
    updateES(esclient, JournalInstance.class, j)
    updateES(esclient, DatabaseInstance.class, j)
    updateES(esclient, OtherInstance.class, j)
    updateES(esclient, BookInstance.class, j)
    titlesRunning = false
  }

  private void tippsUpdate(j) {
    tippsRunning = true
    def esclient = ESWrapperService.getClient()
    updateES(esclient, TitleInstancePackagePlatform.class, j)
    tippsRunning = false
  }


  Map updateSpecifiedTippBulk(List<TitleInstancePackagePlatform> tipps, Job job = null) {
    tippsRunning = true

    Map result = [result: "OK"]

    def esClient = ESWrapperService.getClient()
    // def indexName = grailsApplication.config.getProperty('gokb.es.indices.' + ESWrapperService.indicesPerType.get(domain.simpleName))
    def indexName = grailsApplication.config.getProperty('gokb.es.indices.tipps')

    BulkRequest bulkRequest = new BulkRequest()
    int count = 0
    int total = tipps.size()

    try {

      for (TitleInstancePackagePlatform tipp : tipps) {
        if (Thread.currentThread().isInterrupted()) {
          log.warn("Job cancelling ..")
          break
        }

        def osRecord = buildEsRecord(tipp)

        if (osRecord != null) {
          IndexRequest singleRequest = new IndexRequest(indexName)
          singleRequest.id(osRecord['_id'].toString())
          osRecord.remove('_id')
          singleRequest.source((osRecord as JSON).toString(), XContentType.JSON)
          bulkRequest.add(singleRequest)
        }

        count++


        if (count % 50 == 0 || count == total) {
          // log.debug("... interim:: processed ${total} out of ${countq} records (${domain.name}) - updating highest timestamp to ${highest_timestamp} interim flush")
          BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT)

          if (bulkResponse.hasFailures()) {
            logBulkFailures(bulkResponse)
            log.error("Bulk Update had errors!")
            break
          }
          log.debug("... BulkResponse: ${bulkResponse}")

          if (count != total) {
            bulkRequest = new BulkRequest()
          }

          log.info("Index Update: completed Bulk. Now indexed: " + count + " of " + total)

        }

      }
    } catch (Exception e) {
      result.result = "ERROR"
      log.error("Error while indexing ", e)
    }


    result.indexed = count

    if (job) {
      job.message("Indexing finished for ${count} Tipps...")
    }

    log.debug("... final:: Processed ${count} out of ${total} records. ")

    tippsRunning = false

    return result
  }

  def updateES(esClient, domain, job, boolean reindex = false) {
    int bulkSize = 100
    int limitPerJob = Integer.MAX_VALUE //no limit

    log.debug("updateES(${domain}...)")
    def indexType = ESWrapperService.indicesPerType[domain.name]
    def indexName = grailsApplication.config.getProperty('gokb.es.indices.' + ESWrapperService.indicesPerType.get(domain.simpleName))

    domain.withNewSession {
      try {
        log.debug("updateES - ${domain.name}")
        def latest_ft_record = null
        def highest_timestamp = 0
        def highest_id = 0
        def activity_type = reindex ? 'ESReindex' : 'ESIndex'

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

        log.debug("updateES ${domain.name} since ${latest_ft_record.lastTimestamp}")

        Date from = new Date(latest_ft_record.lastTimestamp)
        def countq = domain.executeQuery("select count(o.id) from " + domain.name + " as o where (o.lastUpdated > :ts OR (o.lastUpdated = :ts AND o.id > :lid) OR o.dateCreated > :ts)", [ts: from, lid: latest_ft_record.lastId, max: limitPerJob], [readonly: true])[0]

        if (job) job.message("Indexing start for ${countq} ${domain.simpleName} ..".toString())

        log.debug("Will process ${countq} records")
        def q = domain.executeQuery("select o.id, o.lastUpdated from " + domain.name + " as o where (o.lastUpdated > :ts OR (o.lastUpdated = :ts AND o.id > :lid) OR o.dateCreated > :ts) order by o.lastUpdated, o.id", [ts: from, lid: latest_ft_record.lastId, max: limitPerJob], [readonly: true])
        log.debug("Query completed.. processing rows...")

        BulkRequest bulkRequest = new BulkRequest()

        int total = q.size()
        int count = 0

        // Performance statistics
        int p_bulksTotal = (total + bulkSize - 1) / bulkSize
        int p_actualBulk = 1
        int p_bulkAtHour = 1
        long p_bulkStartTime = new Date().getTime()
        long p_timeTotal = 0
        long p_highestBulkTime = 0
        int p_estimationInterval = 15 // in minutes

        long p_totalStartTime = new Date().getTime()
        long p_hourStartTime = new Date().getTime()

        int p_journals = 0
        int p_books = 0


        for (record in q) {
          if (Thread.currentThread().isInterrupted()) {
            log.warn("Job cancelling ..")
            break
          }

          long recId = record[0]
          Date recLastUpdated = dateFormatService.parseTimestampMs(record[1]?.toString())

          Object r = domain.get(recId)
          log.debug("${r.id} ${domain.name} -- (rects)${r.lastUpdated} > (from)${from}")

          def idx_record = buildEsRecord(r)

          if (idx_record != null) {
            IndexRequest singleRequest = new IndexRequest(indexName)
            singleRequest.id(idx_record['_id'].toString())
            idx_record.remove('_id')
            singleRequest.source((idx_record as JSON).toString(), XContentType.JSON)
            bulkRequest.add(singleRequest)

            if (domain.simpleName == "TitleInstancePackagePlatform") {
              if (idx_record.titleType == "Journal") {
                p_journals++
              }
              else if (idx_record.titleType == "Book") {
                p_books++
              }
            }
          }

          if (recLastUpdated?.getTime() > highest_timestamp) {
            highest_timestamp = recLastUpdated?.getTime()
          }

          highest_id = r.id

          count++

          if (count % bulkSize == 0 || count == total) {

            log.debug("... interim:: processed ${total} out of ${countq} records (${domain.name}) - updating highest timestamp to ${highest_timestamp} interim flush")
            BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT)

            if (bulkResponse.hasFailures()) {
              logBulkFailures(bulkResponse)
              log.error("Bulk Update had errors, skipping domain ${domain}!")
              break
            }

            log.debug("... BulkResponse: ${bulkResponse}")

            if (latest_ft_record) {
              latest_ft_record.lastTimestamp = highest_timestamp
              latest_ft_record.lastId = highest_id
              latest_ft_record.save(flush: true, failOnError: true)
            }
            else {
              log.error("Unable to locate free text control record with ID ${latest_ft_record.id}. Possibe parallel FT update")
            }

            if (count != total) {
              bulkRequest = new BulkRequest()
            }

            cleanUpGorm()

            long p_bulkDuration = new Date().getTime() - p_bulkStartTime
            p_timeTotal += p_bulkDuration
            if (p_bulkDuration > p_highestBulkTime) {
              p_highestBulkTime = p_bulkDuration
            }

            if (domain.simpleName == "TitleInstancePackagePlatform") {
              log.info("TIPP Statistik - Bulk ${p_actualBulk}/${p_bulksTotal} ## Dauer: ${p_bulkDuration}, Avg.: ${(long) (p_timeTotal/p_actualBulk)} " +
                      "slowest: ${p_highestBulkTime}, Books: ${p_books}, Journals: ${p_journals}" )
            }
            else {
              log.info("${domain.simpleName} Statistik - Gesamt-Bulk ${p_actualBulk}/${p_bulksTotal} ## Dauer: ${p_bulkDuration}, Avg.: ${(long) (p_timeTotal / p_actualBulk)} " +
                      "slowest: ${p_highestBulkTime}")
            }

            if (new Date().getTime() - p_hourStartTime >= p_estimationInterval * 60 * 1000) {
              long estimatedDuration = ((long) (p_timeTotal/p_actualBulk)) * (p_bulksTotal - p_actualBulk)
              log.info("${domain.name} Indexing Update: ${(p_actualBulk - p_bulkAtHour) * bulkSize} Records were updated in the last ${p_estimationInterval} Minutes. " +
                      "##### Estimated Duration is: " + String.format("%02d min, %02d sec",
                      TimeUnit.MILLISECONDS.toMinutes(estimatedDuration),
                      TimeUnit.MILLISECONDS.toSeconds(estimatedDuration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(estimatedDuration))))
              p_hourStartTime = new Date().getTime()
              p_bulkAtHour = p_actualBulk
            }

            if (domain.simpleName == "TitleInstancePackagePlatform") {
              p_books = 0
              p_journals = 0
            }

            p_actualBulk++
            p_bulkStartTime = new Date().getTime()

          }
        }

        if (job) job.message("Indexing finished for ${countq} ${domain.simpleName}.".toString())

        log.debug("... final:: Processed ${count} out of ${countq} records for ${domain.name}. Max TS seen ${highest_timestamp} highest id with that TS: ${highest_id}")
      }
      catch (Exception e) {
        log.error("Problem with FT index", e)
      }
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


  def clearDownAndInit(Job j = null) {
    if (packagesRunning == false &&
        orgsRunning == false &&
        platformsRunning == false &&
        titlesRunning == false &&
        tippsRunning == false
    ) {
      log.debug("Remove existing FTControl ..")

      FTControl.withTransaction {
        def res = FTControl.executeUpdate("delete FTControl c")
        log.debug("Result: ${res}")
      }
      updateFTIndexes(j)
    }
    else {
      if (j) j.message("Already running, skip..")
      log.debug("FTUpdate already running")
      return "Job cancelled – FTUpdate was already running!"
    }
  }


  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy")
  }
}
