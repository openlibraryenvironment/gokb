package org.gokb

import com.k_int.ESSearchService
import grails.gorm.transactions.Transactional
import org.elasticsearch.action.bulk.BulkRequestBuilder

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
  def synchronized updateFTIndexes() {
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

  def doFTUpdate() {
    log.debug("doFTUpdate")
    log.debug("Execute IndexUpdateJob starting at ${new Date()}")
    def start_time = System.currentTimeMillis()
    def esclient = ESWrapperService.getClient()

    try {
      updateES(esclient, org.gokb.cred.Package.class) { org.gokb.cred.Package kbc ->
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
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated)
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
        result.scope = kbc.scope ? kbc.scope.value : ""
        if (kbc.listVerifiedDate)
          result.listVerifiedDate = dateFormatService.formatIsoTimestamp(kbc.listVerifiedDate)
        if (kbc.source) {
          result.source = [
            id              : kbc.source.id,
            name            : kbc.source.name,
            automaticUpdates: kbc.source.automaticUpdates,
            url             : kbc.source.url,
            frequency       : kbc.source.frequency,
          ]
          if (kbc.source.lastRun)
            result.source.lastRun = dateFormatService.formatIsoTimestamp(kbc.source.lastRun)
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
                                  namespaceName: idc.toComponent.namespace.name])
        }
        result.componentType = kbc.class.simpleName
        result
      }

      updateES(esclient, org.gokb.cred.Org.class) { org.gokb.cred.Org kbc ->
        def result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.altname = []
        result.updater = 'org'
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated)
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
                                  namespaceName: idc.toComponent.namespace.name])
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
        result
      }

      updateES(esclient, org.gokb.cred.Platform.class) { org.gokb.cred.Platform kbc ->
        def result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.updater = 'platform'
        result.cpname = kbc.provider?.name
        result.provider = kbc.provider ? kbc.provider.getLogEntityId() : ""
        result.providerUuid = kbc.provider ? kbc.provider?.uuid : ""
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated)
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
                                  namespaceName: idc.toComponent.namespace.name])
        }
        result.componentType = kbc.class.simpleName
        result
      }

      updateES(esclient, org.gokb.cred.JournalInstance.class) { org.gokb.cred.JournalInstance kbc ->
        def result = null
        def current_pub = kbc.currentPublisher
        result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name
        result.sortname = kbc.name
        result.updater = 'journal'
        // result.publisher = kbc.currentPublisher?.name
        result.publisher = current_pub ? current_pub.getLogEntityId() : ""
        result.publisherName = current_pub?.name
        result.publisherUuid = current_pub?.uuid ?: ""
        result.altname = []
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated)
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name])
        }
        result.componentType = kbc.class.simpleName
        // log.debug("process ${result}")
        result
      }

      updateES(esclient, org.gokb.cred.DatabaseInstance.class) { org.gokb.cred.DatabaseInstance kbc ->
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
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated)
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name])
        }
        result.componentType = kbc.class.simpleName
        // log.debug("process ${result}")
        result
      }

      updateES(esclient, org.gokb.cred.OtherInstance.class) { org.gokb.cred.OtherInstance kbc ->

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
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated)
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name])
        }
        result.componentType = kbc.class.simpleName
        // log.debug("process ${result}")
        result
      }

      updateES(esclient, org.gokb.cred.BookInstance.class) { org.gokb.cred.BookInstance kbc ->
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
        result.updater = 'book'
        kbc.variantNames.each { vn ->
          result.altname.add(vn.variantName)
        }
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated)
        result.status = kbc.status?.value
        result.identifiers = []
        kbc.getCombosByPropertyNameAndStatus('ids', 'Active').each { idc ->
          result.identifiers.add([namespace    : idc.toComponent.namespace.value,
                                  value        : idc.toComponent.value,
                                  namespaceName: idc.toComponent.namespace.name])
        }
        result.componentType = kbc.class.simpleName
        // log.debug("process ${result}")
        result
      }

      updateES(esclient, org.gokb.cred.TitleInstancePackagePlatform.class) { org.gokb.cred.TitleInstancePackagePlatform kbc ->

        def result = [:]
        result._id = "${kbc.class.name}:${kbc.id}"
        result.uuid = kbc.uuid
        result.name = kbc.name ?: (kbc.title?.name ?: null)
        result.componentType = kbc.class.simpleName

        result.curatoryGroups = []
        kbc.pkg?.curatoryGroups?.each { cg ->
          result.curatoryGroups.add(cg.name)
        }
        result.titleType = kbc.title?.niceName ?: 'Unknown'
        result.lastUpdatedDisplay = dateFormatService.formatIsoTimestamp(kbc.lastUpdated)
        result.url = kbc.url
        if (kbc.title?.niceName == 'Journal') {
          result.coverage = []
          ArrayList coverage_src = kbc.coverageStatements?.size() > 0 ? kbc.coverageStatements : [kbc]
          coverage_src.each { tcs ->
            def cst = [:]
            if (tcs.startDate) cst.startDate = dateFormatService.formatIsoTimestamp(tcs.startDate)
            cst.startVolume = tcs.startVolume ?: ""
            cst.startIssue = tcs.startIssue ?: ""
            if (tcs.endDate) cst.endDate = dateFormatService.formatIsoTimestamp(tcs.endDate)
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
          if (kbc.title?.editionNumber) {
            edition.number = kbc.title.editionNumber
          }
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
          if (kbc.title?.dateFirstInPrint) result.titleDateFirstInPrint = dateFormatService.formatIsoTimestamp(kbc.title.dateFirstInPrint)
          if (kbc.title?.dateFirstOnline) result.titleDateFirstOnline = dateFormatService.formatIsoTimestamp(kbc.title.dateFirstOnline)
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
              event.date = dateFormatService.formatIsoTimestamp(he.date)
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
                                  namespaceName: idc.toComponent.namespace.name])
        }

        if (kbc.dateFirstOnline) result.dateFirstOnline = dateFormatService.formatIsoTimestamp(kbc.dateFirstOnline)
        if (kbc.dateFirstInPrint) result.dateFristInPrint = dateFormatService.formatIsoTimestamp(kbc.dateFirstInPrint)
        if (kbc.accessStartDate) result.accessStartDate = dateFormatService.formatIsoTimestamp(kbc.accessStartDate)
        if (kbc.accessEndDate) result.accessEndDate = dateFormatService.formatIsoTimestamp(kbc.accessEndDate)
        if (kbc.lastChangedExternal) result.lastChangedExternal = dateFormatService.formatIsoTimestamp(kbc.lastChangedExternal)

        if (kbc.publisherName) result.publisherName = kbc.publisherName
        if (kbc.subjectArea) result.subjectArea = kbc.subjectArea
        if (kbc.series) result.series = kbc.series
        if (kbc.volumeNumber) result.volumeNumber = kbc.volumeNumber
        if (kbc.editionStatement) result.editionStatement = kbc.editionStatement
        if (kbc.firstAuthor) result.firstAuthor = kbc.firstAuthor
        if (kbc.firstEditor) result.firstEditor = kbc.firstEditor
        if (kbc.parentPublicationTitleId) result.parentPublicationTitleId = kbc.parentPublicationTitleId
        if (kbc.precedingPublicationTitleId) result.precedingPublicationTitleId = kbc.precedingPublicationTitleId

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

        result
      }
    }
    catch (Exception e) {
      log.error("Problem", e)
    }
    running = false
  }

  def updateES(esclient, domain, recgen_closure) {

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

      BulkRequestBuilder bulkRequest = esclient.prepareBulk()
      // while (results.next()) {
      for (r_id in q) {
        if (Thread.currentThread().isInterrupted()) {
          log.debug("Job cancelling ..")
          running = false
          break
        }
        Object r = domain.get(r_id)
        log.debug("${r.id} ${domain.name} -- (rects)${r.lastUpdated} > (from)${from}")
        def idx_record = recgen_closure(r)
        def es_index = ESSearchService.indicesPerType.get(idx_record['componentType'])
        if (idx_record != null) {
          def recid = idx_record['_id'].toString()
          idx_record.remove('_id')
          bulkRequest.add(esclient.prepareIndex(es_index, 'component', recid).setSource(idx_record))
        }
        if (r.lastUpdated?.getTime() > highest_timestamp) {
          highest_timestamp = r.lastUpdated?.getTime()
        }
        highest_id = r.id
        count++
        total++
        if (count > 250) {
          count = 0
          log.debug("interim:: processed ${total} out of ${countq} records (${domain.name}) - updating highest timestamp to ${highest_timestamp} interim flush")
          def bulkResponse = bulkRequest.get()
          log.debug("BulkResponse: ${bulkResponse}")
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
        def bulkFinalResponse = bulkRequest.get()
        log.debug("Final BulkResponse: ${bulkFinalResponse}")
      }
      // update timestamp
      FTControl.withNewTransaction {
        latest_ft_record = FTControl.get(latest_ft_record.id)
        latest_ft_record.lastTimestamp = highest_timestamp
        latest_ft_record.lastId = highest_id
        latest_ft_record.save(flush: true, failOnError: true)
      }
      cleanUpGorm()
      log.debug("final:: Processed ${total} out of ${countq} records for ${domain.name}. Max TS seen ${highest_timestamp} highest id with that TS: ${highest_id}")
    }
    catch (Exception e) {
      log.error("Problem with FT index", e)
    }
    finally {
      log.debug("Completed processing on ${domain.name} - saved ${count} records")
    }
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM")
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }

  def clearDownAndInitES() {
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
