package org.gokb

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

  def startUpdate(User user = null) {
    def result = [result: 'OK']
    def running_jobs = concurrencyManagerService.getActiveJobsForType(RefdataCategory.lookup('Job.Type', 'EZBCollectionIngest'))

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

  private def fetchUpdatedLists (job) {
    def result = [result: 'OK', report: [:]]
    def baseUrl = grailsApplication.config.getProperty('gokb.ezbOpenCollections.url')

    if (baseUrl) {
      def allCollections = [:]
      def archivedCollections = [:]
      boolean cancelled = false
      job.startTime = new Date()

      try {
        def request = HttpRequest.GET("/collections/v1/")
          .header('User-Agent', "GOKb KBART bulk import")
          .header('Accept', 'application/json')

        log.error("Headers: ${request.remoteAddress}")

        def resp = HttpClient.create(new URL(baseUrl)).toBlocking().retrieve(request, Map.class)

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
        def type_results = [
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
          matchingFailed: [],
          matchedOtherCg: [],
          sourceError: []
        ]

        if (!cancelled) {
          for (item in items) {
            if (Thread.currentThread().isInterrupted()) {
              break
              cancelled = true
            }

            boolean skipped = false
            boolean sourceResult = false
            def pkgInfo = [:]
            Date pkgCreated
            def curator_id = null

            Package.withNewSession { session ->
              type_results.total++
              def pkgName = buildPackageName(item)
              log.debug("Processing ${type} ${item.ezb_collection_name}")
              RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
              CuratoryGroup ezb_curator = CuratoryGroup.findByName(grailsApplication.config.getProperty('gokb.ezbAugment.rrCurators'))
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

              curator_id = ezb_curator.id

              if (curator_id && provider && platform && ezbSource) {
                Identifier collection_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb-collection-id', item.ezb_collection_id)

                obj = Package.findByNameAndStatus(pkgName, status_current)

                if (!obj) {
                  def candidates = findIdCandidates(collection_id, curator_id)

                  if (candidates.size() == 0) {
                    def other_cg_candidates = findIdCandidates(collection_id, null)

                    if (other_cg_candidates.size() == 1) {
                      if (hasEzbUrl(other_cg_candidates[0])) {
                        log.debug("Matched existing package of other curatory group with ezb update url!")
                        obj = candidates[0]
                      }
                      else {
                        skipped = true
                        type_results.skipped++
                        type_results.matchedOtherCg << item.ezb_collection_id
                      }
                    }
                    else if (other_cg_candidates.size() > 1) {
                      skipped = true
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
                    skipped = true
                    type_results.skipped++
                  }
                }

                if (!hasEzbUrl(obj)) {
                  log.debug("Matched package has a conflicting source url..")
                  skipped = true
                  type_results.skipped++
                }

                if (!skipped && !obj) {
                  log.debug("Creating new Package ..")

                  try {
                    obj = new Package(name: pkgName).save(flush: true, failOnError: true)

                    obj.curatoryGroups << ezb_curator

                    type_results.created++
                  }
                  catch (Exception e) {
                    log.debug("Errors creating new package!", e)
                    type_results.errors++
                  }
                }
                else if (!skipped) {
                  log.debug("Handling package ${obj.name}")
                  type_results.updated++
                }

                if (obj && !skipped) {
                  if (!obj.contentType) {
                    obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Journal')
                  }

                  String validity = 'Consortium'

                  if (type == 'collections_from_aggregator_packages') {
                    validity = 'Global'
                    obj.scope = RefdataCategory.lookup('Package.Scope', 'Aggregator')
                  }

                  obj.global = RefdataCategory.lookup('Package.Global', validity)

                  obj.nominalPlatform = platform
                  obj.provider = provider
                  obj.save(flush: true)

                  if (!obj.ids.contains(collection_id)) {
                    obj.ids.add(collection_id)
                  }

                  if (obj.name != pkgName) {
                    obj.name = pkgName
                  }

                  obj.save(flush: true)

                  pkgCreated = obj.dateCreated
                  pkgInfo = [name: obj.name, type: "Package", id: obj.id, uuid: obj.uuid]

                  CuratoryGroup primary_curator = obj.curatoryGroups[0]

                  curator_id = primary_curator.id

                  sourceResult = ensurePackageSource(obj, primary_curator, item)

                  log.debug("Existing package since: ${obj?.dateCreated} - EZB start ${dateFormatService.parseTimestamp(item.ezb_collection_released_date)}")
                }
                else if (!obj) {
                  log.warn("Unable to reference package!")
                  type_results.matchingFailed << item.ezb_collection_id
                }
              }
              else {
                log.debug("Skipping package due to missing info! Provider: ${provider}, Platform: ${platform}, Curator: ${curator_id}")
                type_results.skipped++
                type_results.skippedList << item.ezb_collection_id

                if (!curator_id)
                  type_results.noCurator++

                if (!provider) {
                  type_results.noProvider++
                }

                if (!platform) {
                  type_results.noPlatform++
                }
              }
            }

            if (!skipped && pkgInfo.id && sourceResult && pkgCreated && pkgCreated > dateFormatService.parseTimestamp(item.ezb_collection_released_date)) {
              if (hasChangedFile(pkgInfo.id, item)) {
                log.debug("Creating new import job ..")
                try {
                  Job pkg_job = concurrencyManagerService.createJob { pjob ->
                    packageSourceUpdateService.updateFromSource(pkgInfo.id, null, pjob, curator_id)
                  }

                  RefdataCategory.withNewSession {
                    pkg_job.groupId = curator_id
                    pkg_job.description = "EZB KBART Source ingest (${pkgInfo.name})".toString()
                    pkg_job.type = RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
                    pkg_job.linkedItem = pkgInfo
                    pkg_job.message("Starting upsert for Package ${pkgInfo.name}".toString())
                    pkg_job.startOrQueue()
                  }

                  def job_result = pkg_job.get()

                  log.debug("Finished job with result: ${job_result}")

                  if (job_result?.validation?.errors?.rows || job_result?.validation?.errors?.missingColumns) {
                    type_results.validationErrors[item.ezb_collection_id] = job_result.validation
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
                log.debug("Skipping unchanged Package file ${pkgInfo.name}.")
                type_results.unchanged++
              }
            }
            else if (skipped) {
              log.debug("Skipped..")
            }
            else if (!pkgInfo) {
              log.debug("Unable to reference package!")
            }
            else if (!sourceResult) {
              log.debug("No source object created.. skip")
              type_results.errors++
              type_results.sourceError << item.ezb_collection_id
            }
            else {
              log.warn("Matched package is older than the EZB release date.. Skipping!")
              type_results.skipped++
            }
          }
        }
        else {
          log.debug("Job was cancelled.. skipping further processing")
        }

        job.message("Completed type ${type} with ${type_results}".toString())
        result.report[type] = type_results
      }

      // Cleaning up newly archived collections
      result.report[ARCHIVED_TYPE] = [matchedOtherCg: [], skipped: 0, retired: 0, total: 0]
      log.debug("Looking for archived packages to retire ..")

      Package.withNewSession {
        archivedCollections.each { item ->
          result.report[ARCHIVED_TYPE].total++
          def pkgName = buildPackageName(item)
          RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
          def obj = Package.findByNameAndStatus(pkgName, status_current)
          CuratoryGroup curator = CuratoryGroup.findByName(grailsApplication.config.getProperty('gokb.ezbAugment.rrCurators'))

          if (item.ezb_collection_curatory_group) {
            def local_cg = CuratoryGroup.findByUuid(item.ezb_collection_curatory_group)

            if (local_cg) {
              curator = local_cg
            }
          }

          if (!obj) {
            Identifier collection_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb-collection-id', item.ezb_collection_id)
            def candidates = findIdCandidates(collection_id, curator.id)

            if (candidates.size() == 0) {
              def other_cg_candidates = findIdCandidates(collection_id, null)

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
            def date_changed = item.ezb_collection_deactivated_date.substring(0, 10) + ' 00:00:00'

            obj.retireAt(dateFormatService.parseTimestamp(date_changed))

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
      def job_map = [
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

      def jr = new JobResult(job_map).save(flush: true, failOnError: true)
    }

    result
  }

  private static boolean hasEzbUrl(Package pkg) {
    boolean result = false
    String src_url = pkg.source?.url ?: null

    if (src_url?.startsWith('https://ezb.uni-regensburg') || src_url?.startsWith( 'https://ezb.ur.de')) {
      result = true
    }

    result
  }

  private boolean hasChangedFile(pid, item) {
    Package.withNewSession {
      boolean result = false
      def deposit_token = java.util.UUID.randomUUID().toString()
      File tmp_file = TSVIngestionService.handleTempFile(deposit_token)
      def file_info = packageSourceUpdateService.fetchKbartFile(tmp_file, new URL(item.ezb_collection_titlelist))
      RefdataValue type_fa = RefdataCategory.lookup('Combo.Type', 'KBComponent.FileAttachments')

      def ordered_combos = Combo.executeQuery('''select c.toComponent from Combo as c
                                                where c.type = :ct
                                                and c.fromComponent.id = :pkg
                                                order by c.dateCreated desc''', [ct: type_fa, pkg: pid])

      def last_df_md5 = ordered_combos.size() > 0 ? ordered_combos[0].md5 : null

      if (!last_df_md5 || last_df_md5 != TSVIngestionService.analyseFile(tmp_file).md5sumHex) {
        result = true
      }

      result
    }
  }

  private boolean ensurePackageSource(Package pkg, CuratoryGroup curator, Map item) {
    boolean result = true
    Source source = pkg.source
    IdentifierNamespace ezb_ns = IdentifierNamespace.findByValue('ezb')

    if (!source) {
      log.debug("Setting new package source..")

      try {
        def dupe = Source.findByName(pkg.name)

        if (!dupe) {
          source = new Source(name: pkg.name, url: item.ezb_collection_titlelist, targetNamespace: ezb_ns).save(flush:true, failOnError: true)
          source.curatoryGroups << curator
        }
        else {
          log.warn("Found existing source with package name ${pkg.name}!")
          source = dupe
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
      source.save()
    }

    if (source && source.url != item.ezb_collection_titlelist) {
      source.url = item.ezb_collection_titlelist
      source.save()
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

  private Package[] findIdCandidates(collection_id, curator_id) {
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    RefdataValue combo_active = RefdataCategory.lookup('Combo.Status', 'Active')
    RefdataValue local_status = RefdataCategory.lookup('Package.Global', 'Local')
    def qry_pars = [clId: collection_id, sc: status_current, ca: combo_active, local: local_status]
    def qry = '''from Package as p
        where
        status = :sc
        and global != :local
        and exists (
          select 1 from Combo
          where fromComponent = p
          and status = :ca
          and toComponent = :clId)'''

    if (curator_id) {
      qry_pars.curator = curator_id
      qry += ''' and exists (
              select 1 from Combo
              where fromComponent = p
              and toComponent.id = :curator)'''
    }

    def result = Package.executeQuery(qry, qry_pars)

    result
  }
}
