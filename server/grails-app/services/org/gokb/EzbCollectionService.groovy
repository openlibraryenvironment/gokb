package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient

import org.gokb.cred.*

@Slf4j
class EzbCollectionService {

  def componentLookupService
  def componentUpdateService
  def concurrencyManagerService
  def dateFormatService
  def grailsApplication
  def packageSourceUpdateService
  def TSVIngestionService

  static ArrayList ACTIVE_TYPES = [
    'collections_from_national_license_packages',
    'collections_from_alliance_license_packages',
    'collections_from_consortia_packages',
    'collections_from_national_consortia_packages',
    'collections_from_aggregator_packages'
  ]

  static String ARCHIVED_TYPE = 'collections_no_longer_available'

  public Map startUpdate(User user = null) {
    Map result = [result: 'OK']
    List running_jobs = concurrencyManagerService.getActiveJobsForType(RefdataCategory.lookup('Job.Type', 'EZBCollectionIngest'))

    if (running_jobs.size() == 0) {
        log.debug("Creating new job..")
        Job new_job = concurrencyManagerService.createJob { ljob ->
          fetchUpdatedLists(ljob)
        }

        if (user) {
          new_job.ownerId = user.id
        }

        new_job.description = "EZB open collections harvesting ${user ? '(manual)' : ''}"
        new_job.type = RefdataCategory.lookup('Job.Type', 'EZBCollectionIngest')
        new_job.startOrQueue()

        if (!user) {
          result = new_job.get()
        }
    }
    else {
      log.debug("Job is already running!")
      result.result = 'SKIPPED_ALREADY_RUNNING'
    }

    result
  }

  private Map fetchUpdatedLists (job) {
    Map result = [result: 'OK', report: [:]]
    String baseUrl = grailsApplication.config.getProperty('gokb.ezbOpenCollections.url')

    if (baseUrl) {
      def allCollections = [:]
      def archivedCollections = [:]
      boolean cancelled = false
      job.startTime = new Date()

      try {
        HttpRequest request = HttpRequest.GET("/collections/v1/")
          .header('User-Agent', "GOKb KBART bulk import")
          .header('Accept', 'application/json')

        log.debug("Headers: ${request.remoteAddress}")

        Map resp = HttpClient.create(new URL(baseUrl)).toBlocking().retrieve(request, Map.class)

        resp.collections.each { type, items ->
          log.debug("Mapping ${type} with ${items.size()} items")
          if (type in ACTIVE_TYPES) {
            log.debug("Added items for active type")
            allCollections[type] = items
          }
          else if (type == ARCHIVED_TYPE) {
            archivedCollections = items
          }
          else {
            log.debug("Skipped inactive type..")
          }
        }
      }
      catch (Exception e) {
        log.error("Unable to fetch public collections!", e)
        cancelled = true
        result.result = 'ERROR'
        result.message = 'Unable to fetch collection info!'
      }

      // Local backup for testing

      // File jsonFile = new File(getClass().getResource(
      //     "${File.separator}ezb_collections.json").toURI())

      // def data = new JsonSlurper().parse(jsonFile)

      // data.collections?.each { type, items ->
      //   log.debug("Mapping ${type} with ${items.size()} items")
      //   if (type in ACTIVE_TYPES) {
      //     log.debug("Added items for active type")
      //     allCollections[type] = items
      //   }
      //   else {
      //     log.debug("Skipped inactive type..")
      //   }
      // }

      allCollections.each { type, items ->
        log.debug("Starting with type ${type} ..")
        Map type_results = [
          total: 0,
          skipped: 0,
          noProvider: 0,
          noPlatform: 0,
          noCurator: 0,
          unchanged: 0,
          updated: 0,
          created: 0,
          errors: 0,
          success: 0,
          skippedList: [],
          validationErrors: [:],
          validationWarnings: [:],
          matchingFailed: [],
          matchedOtherCg: [],
          sourceError: []
        ]

        if (!cancelled) {
          for (item in items) {
            if (Thread.currentThread().isInterrupted() || job.isCancelled()) {
              break
              cancelled = true
              result.result = 'CANCELLED'
            }

            handleEzbCollectionItem(item, type_results)
          }
        }
        else {
          log.debug("Job was cancelled.. skipping further processing")
        }

        job.message("Completed type ${type} with ${type_results}".toString())
        result.report[type] = type_results
      }

      if (cancelled) {
        return result
      }

      // Cleaning up newly archived collections
      result.report[ARCHIVED_TYPE] = [
        matchedOtherCg: [],
        skipped: 0,
        retired: 0,
        total: 0
      ]
      log.debug("Looking for archived packages to retire ..")

      Package.withNewSession {
        archivedCollections.each { item ->
          result.report[ARCHIVED_TYPE].total++
          String pkgName = buildPackageName(item)
          RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
          Package obj = Package.findByNameAndStatus(pkgName, status_current)
          CuratoryGroup curator = CuratoryGroup.findByName(grailsApplication.config.getProperty('gokb.ezbAugment.rrCurators'))

          if (item.ezb_collection_curatory_group) {
            CuratoryGroup local_cg = CuratoryGroup.findByUuid(item.ezb_collection_curatory_group)

            if (local_cg) {
              curator = local_cg
            }
          }

          if (!obj) {
            Identifier collection_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb-collection-id', item.ezb_collection_id)
            List candidates = findIdCandidates(collection_id, curator.id)

            if (candidates.size() == 0) {
              List other_cg_candidates = findIdCandidates(collection_id, null)

              if (other_cg_candidates.size() > 0) {
                result.report[ARCHIVED_TYPE].skipped++
                result.report[ARCHIVED_TYPE].matchedOtherCg << item.ezb_collection_id
              }
            }
            else if (candidates.size() == 1) {
              obj = candidates[0]
              log.debug("Found package ${obj} via id ${collection_id} and curatoryGroup ${curator}")
            }
            else if (candidates.size() > 1) {
              log.warn("Found ${candidates} as possible package candidates!")
              result.report[ARCHIVED_TYPE].skipped++
            }
          }

          if (obj) {
            String date_changed = item.ezb_collection_deactivated_date ? item.ezb_collection_deactivated_date.substring(0, 10) + ' 00:00:00' : null

            if (date_changed) {
              obj.retireAt(dateFormatService.parseTimestamp(date_changed))
            }
            else {
              obj.retire()
            }

            obj.save(flush: true)

            result.report[ARCHIVED_TYPE].retired++
          }
        }
      }

      result.result = 'FINISHED'
      job.endTime = new Date()
    }
    else {
      log.debug("No API base for open EZB collections configured.")
      result.result = 'SKIPPED_NO_API_URL'
    }

    JobResult.withNewTransaction {
      Map job_map = [
          uuid        : (job.uuid),
          description : (job.description),
          resultObject: (result as JSON).toString(),
          type        : (job.type),
          statusText  : (result.result),
          ownerId     : (job.ownerId),
          groupId     : (job.groupId),
          startTime   : (job.startTime),
          endTime     : (job.endTime)
      ]

      new JobResult(job_map).save(flush: true, failOnError: true)
    }

    result
  }

  private void handleEzbCollectionItem(item, type_results) {
    Map collection_result = processPackageInfo(item, type_results)

    if (!collection_result.skipped &&
        collection_result.pkgInfo.id &&
        collection_result.sourceResult &&
        collection_result.pkgCreated &&
        collection_result.pkgCreated > dateFormatService.parseTimestamp(item.ezb_collection_released_date)
    ) {
      if (hasChangedFile(collection_result.pkgInfo.id, item)) {
        log.debug("Creating new import job ..")

        try {
          Job pkg_job = concurrencyManagerService.createJob { pjob ->
            packageSourceUpdateService.updateFromSource(collection_result.pkgInfo.id, null, pjob, collection_result.curator_id)
          }

          RefdataCategory.withNewSession {
            pkg_job.groupId = collection_result.curator_id
            pkg_job.description = "EZB KBART Source ingest (${collection_result.pkgInfo.name})".toString()
            pkg_job.type = RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
            pkg_job.linkedItem = collection_result.pkgInfo
            pkg_job.message("Starting upsert for Package ${collection_result.pkgInfo.name}".toString())
            pkg_job.startOrQueue()
          }

          Map job_result = pkg_job.get() ?: [:]

          log.debug("Finished job with result: ${job_result}")

          if (job_result?.validation) {
            if (job_result.validation.errors?.rows || job_result.validation.errors?.missingColumns) {
              type_results.validationErrors[item.ezb_collection_id] = job_result.validation
            }
            else if (job_result.validation.warnings?.type?.replacementChars) {
              type_results.validationWarnings[item.ezb_collection_id] = job_result.validation
            }
          }

          if (job_result?.result == 'ERROR') {
            type_results.errors++
          }
          else {
            type_results.success++
          }
        }
        catch (Exception e) {
          log.error("Exception creating source update job!", e)
        }
      }
      else {
        log.debug("Skipping unchanged Package file ${collection_result.pkgInfo.name}.")
        type_results.unchanged++
      }
    }
    else if (collection_result.skipped) {
      log.debug("Skipped..")
    }
    else if (!collection_result.pkgInfo) {
      log.debug("Unable to reference package!")
    }
    else if (!collection_result.sourceResult) {
      log.debug("No source object created.. skip")
      type_results.errors++
      type_results.sourceError << item.ezb_collection_id
    }
    else {
      log.warn("Matched package is older than the EZB release date.. Skipping!")
      type_results.skipped++
    }
  }

  private Map processPackageInfo(item, type_results) {
    Map result = [
      skipped: false,
      sourceResult: false,
      pkgInfo: [:],
      pkgCreated: null,
      curator_id: null
    ]

    Package.withNewSession { session ->
      type_results.total++

      String pkgName = buildPackageName(item)
      log.debug("Processing ${item.ezb_package_type_name} ${item.ezb_collection_name}")
      String ezbCuratorName = grailsApplication.config.getProperty('gokb.ezbAugment.rrCurators')
      RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      RefdataValue ls_checked = RefdataCategory.lookup('Package.ListStatus', 'Checked')
      CuratoryGroup ezb_curator = CuratoryGroup.findByName(ezbCuratorName) ?: new CuratoryGroup(name: ezbCuratorName).save(flush: true)
      Platform platform = item.ezb_collection_platform ? Platform.findByUuid(item.ezb_collection_platform) : null
      Org provider = item.ezb_collection_provider ? Org.findByUuid(item.ezb_collection_provider) : null
      Boolean ezbSource = (item.ezb_collection_source == 'EZB')
      Package obj

      if (item.ezb_collection_curatory_group) {
        def local_cg = CuratoryGroup.findByUuid(item.ezb_collection_curatory_group)

        if (local_cg) {
          ezb_curator = local_cg
        }
      }

      result.curator_id = ezb_curator.id

      if (result.curator_id && provider && platform && ezbSource) {
        Identifier collection_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb-collection-id', item.ezb_collection_id)
        Identifier zdb_sigel = item.zdb_product_id?.trim() ? componentLookupService.lookupOrCreateCanonicalIdentifier('isil', item.zdb_product_id) : null

        obj = Package.findByNameAndStatus(pkgName, status_current)

        if (!obj) {
          def candidates = findIdCandidates(collection_id, result.curator_id)

          if (candidates.size() == 0) {
            def other_cg_candidates = findIdCandidates(collection_id, null)

            log.debug("Found ${other_cg_candidates} -> ${other_cg_candidates.size()}")

            if (other_cg_candidates.size() == 1) {
              if (hasEzbUrl(other_cg_candidates[0])) {
                log.debug("Matched existing package of other curatory group with ezb update url!")
                obj = other_cg_candidates[0]
              }
              else {
                result.skipped = true
                type_results.skipped++
                type_results.matchedOtherCg << item.ezb_collection_id
              }
            }
            else if (other_cg_candidates.size() > 1) {
              result.skipped = true
              type_results.skipped++
              type_results.matchedOtherCg << item.ezb_collection_id
            }
          }
          else if (candidates.size() == 1) {
            obj = candidates[0]
            log.debug("Found package ${obj} via id ${collection_id} and curatoryGroup ${ezb_curator}")
          }
          else if (candidates.size() > 1) {
            log.warn("Found ${candidates} as possible package candidates!")
            result.skipped = true
            type_results.skipped++
          }
        }

        if (obj && !hasEzbUrl(obj)) {
          log.debug("Matched package has a conflicting source url..")
          result.skipped = true
          type_results.skipped++
        }

        if (!result.skipped && !obj) {
          log.debug("Creating new Package ..")

          try {
            obj = new Package(name: pkgName).save(flush: true, failOnError: true)

            obj.addToCuratoryGroups(ezb_curator)
            obj.save()

            type_results.created++
          }
          catch (Exception e) {
            log.debug("Errors creating new package!", e)
            type_results.errors++
          }
        }
        else if (!result.skipped) {
          log.debug("Handling package ${obj.name}")
          type_results.updated++
        }

        if (obj && !result.skipped) {
          Boolean hasChanged = false

          if (!obj.contentType) {
            obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Journal')
            hasChanged = true
          }

          String validity = 'Consortium'

          if (item.ezb_package_type_name == 'Aggregatorpaket') {
            validity = 'Global'

            if (obj.scope?.value != 'Aggregator') {
              obj.scope = RefdataCategory.lookup('Package.Scope', 'Aggregator')
              hasChanged = true
            }
          }

          if (!obj.global || obj.global.value != validity) {
            obj.global = RefdataCategory.lookup('Package.Global', validity)
            hasChanged = true
          }

          if (obj.nominalPlatform != platform) {
            obj.nominalPlatform = platform
            hasChanged = true
          }

          if (platform.provider && platform.provider != provider && obj.provider != platform.provider) {
            log.info("Adjusted provider for $obj to platform selection!")
            obj.provider = platform.provider
            hasChanged = true
          }
          else if (provider && platform.provider == provider && obj.provider != provider) {
            obj.provider = provider
            hasChanged = true
          }

          if (hasChanged) {
            obj.save(flush: true)
          }

          if (!obj.ids*.id.contains(collection_id.id)) {
            obj.ids.add(collection_id)
            hasChanged = true
          }

          if (zdb_sigel && !obj.ids*.id.contains(zdb_sigel.id)) {
            obj.ids.add(zdb_sigel)
            hasChanged = true
          }

          if (obj.name != pkgName) {
            obj.name = pkgName
            hasChanged = true
          }

          if (hasChanged) {
            obj.save(flush: true)
          }

          def open_reviews_count = ReviewRequest.executeQuery('''select count(*) from ReviewRequest as rr
              where exists (
                select 1 from TitleInstancePackagePlatform as tipp
                where tipp.pkg = :pkg
                and rr.componentToReview.id = tipp.id
              )
              and status = :so
            ''', [
              pkg: obj,
              so: RefdataCategory.lookup('ReviewRequest.Status', 'Open'),
              cpt: RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
            ])[0]

          if (obj.listStatus != ls_checked && obj.currentTippCount > 0 && open_reviews_count == 0) {
            obj.listStatus = ls_checked
            obj.save(flush: true)
          }

          result.pkgCreated = obj.dateCreated
          result.pkgInfo = [name: obj.name, type: "Package", id: obj.id, uuid: obj.uuid]

          CuratoryGroup primary_curator = ClassUtils.deproxy(obj.curatoryGroups[0])

          result.curator_id = primary_curator?.id

          result.sourceResult = ensurePackageSource(obj, primary_curator, item)

          log.debug("Existing package since: ${obj?.dateCreated} - EZB start ${dateFormatService.parseTimestamp(item.ezb_collection_released_date)}")
        }
        else if (!obj) {
          log.warn("Unable to reference package!")
          type_results.matchingFailed << item.ezb_collection_id
        }
      }
      else {
        log.debug("Skipping package due to missing info! Provider: ${provider}, Platform: ${platform}, Curator: ${result.curator_id}")
        type_results.skipped++
        type_results.skippedList << item.ezb_collection_id

        if (!result.curator_id)
          type_results.noCurator++

        if (!provider) {
          type_results.noProvider++
        }

        if (!platform) {
          type_results.noPlatform++
        }
      }
    }

    result
  }

  private boolean hasEzbUrl(pkg) {
    boolean result = false
    String src_url = ClassUtils.deproxy(pkg).source?.url ?: null

    if (src_url?.startsWith('https://ezb.uni-regensburg') || src_url?.startsWith( 'https://ezb.ur.de')) {
      result = true
    }

    result
  }

  private boolean hasChangedFile(pid, item) {
    Package.withNewSession {
      boolean result = false
      String deposit_token = java.util.UUID.randomUUID().toString()
      File tmp_file = TSVIngestionService.handleTempFile(deposit_token)
      Map file_info = packageSourceUpdateService.fetchKbartFile(tmp_file, new URL(item.ezb_collection_titlelist))

      List ordered_attachments = ComponentAttachment.executeQuery('''select c.file from ComponentAttachment as c
                                                                      where c.component.id = :pkg
                                                                      order by c.dateCreated desc''',
                                                                      [ct: type_fa, pkg: pid])

      String last_df_md5 = ordered_attachments.size() > 0 ? ordered_attachments[0].md5 : null

      if (tmp_file.isFile() && (!last_df_md5 || last_df_md5 != TSVIngestionService.analyseFile(tmp_file).md5sumHex)) {
        result = true
        tmp_file.delete()
      }

      result
    }
  }

  private boolean ensurePackageSource(Package pkg, CuratoryGroup curator, Map item) {
    boolean result = true
    Source source = pkg.source
    IdentifierNamespace ezb_ns = IdentifierNamespace.findByValue('ezb')
    boolean source_changed = false

    if (!source) {
      log.debug("Setting new package source..")

      try {
        source = new Source(name: pkg.name, url: item.ezb_collection_titlelist, targetNamespace: ezb_ns).save(flush:true, failOnError: true)

        if (curator) {
          source.addToCuratoryGroups(curator)
          source.save(flush: true)

        }
      }
      catch (Exception e) {
        log.error("Exception creating source:", e)
        result = false
      }

      if (source) {
        pkg.source = source
        pkg.save(flush: true)
      }
    }

    if (source && source.automaticUpdates) {
      source.automaticUpdates = false
      source_changed = true
    }

    if (source && !source.importConfig) {
      source.importConfig = RefdataCategory.lookup('Source.ImportConfig', 'EZB')
      source_changed = true
    }

    if (source && source.url != item.ezb_collection_titlelist) {
      source.url = item.ezb_collection_titlelist
      source_changed = true
    }

    if (source_changed == true) {
      source.save(flush: true, failOnError: true)
    }

    result
  }

  private static String buildPackageName(item) {
    String result = "${item.ezb_collection_id}: ${item.ezb_collection_name}"

    if (item.ezb_package_type_name == 'Aggregatorpaket') {
      log.debug("No type suffix for aggregator packages ..")
    }
    else if (item.ezb_package_type_name != 'Konsortialpaket') {
      result += ": ${item.ezb_package_type_name}"
    }
    else if (item.ezb_owner.trim()) {
      result += ": ${item.ezb_owner}"
    }

    result
  }

  private List findIdCandidates(collection_id, curator_id) {
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    RefdataValue link_active = RefdataCategory.lookup('ComponentIdentifier.Status', 'Active')
    RefdataValue local_status = RefdataCategory.lookup('Package.Global', 'Local')
    Map qry_pars = [
      clId: collection_id,
      sc: status_current,
      ca: link_active,
      local: local_status
    ]
    def qry = '''FROM Package AS p
        WHERE
        status = :sc
        AND global != :local
        AND exists (
          SELECT 1 FROM ComponentIdentifier
          WHERE component = p
          AND status = :ca
          AND toComponent = :clId)'''

    if (curator_id) {
      qry_pars.curator = curator_id
      qry += ''' AND :curator MEMBER OF p.curatoryGroups'''
    }

    List result = Package.executeQuery(qry, qry_pars)

    result
  }
}
