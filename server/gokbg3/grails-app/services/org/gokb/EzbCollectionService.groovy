package org.gokb

import groovy.util.logging.Slf4j
import com.k_int.ConcurrencyManagerService.Job
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.gokb.cred.*

@Slf4j
class EzbCollectionService {

  def componentLookupService
  def componentUpdateService
  def concurrencyManagerService
  def dateFormatService
  def grailsApplication
  def packageService
  def TSVIngestionService

  static ArrayList activeTypes = [
    'collections_from_national_license_packages',
    'collections_from_alliance_license_packages',
    'collections_from_consortia_packages',
    'collections_from_national_consortia_packages'
  ]

  static boolean running = false

  synchronized def startUpdate(Job job) {
    def result = 'OK'

    if (!running) {
      running = true
      if (!job) {
        Job new_job = concurrencyManagerService.startOrQueue { ljob ->
          fetchUpdatedLists(ljob)
        }

        new_job.description = "EZB open collections harvesting"
        new_job.type = RefdataCategory.lookup('Job.Type', 'EZBCollectionIngest')
        new_job.startOrQueue()
        result = new_job.get()
      } else {
        result = fetchUpdatedLists(job)
      }
      running = false
    }
    else {
      result = 'SKIPPED_ALREADY_RUNNING'
    }

    result
  }

  private def fetchUpdatedLists (job) {
    if (grailsApplication.config.gokb.ezbOpenCollections?.url) {
      def baseUrl = grailsApplication.config.gokb.ezbOpenCollections.url
      def allCollections = [:]
      boolean cancelled = false

      def client = new RESTClient(baseUrl)
      job.startTime = new Date()

      client.request(GET, ContentType.JSON) {
        response.success = { resp, data ->
          log.debug("Got EZB collection list")
          data.collections?.each { type, items ->
            log.debug("Mapping ${type} with ${items.size()} items")
            if (type in activeTypes) {
              log.debug("Added items for active type")
              allCollections[type] = items
            }
            else {
              log.debug("Skipped inactive type..")
            }
          }
        }
        response.failure = { resp, data ->
          log.error("Got status ${resp.status} .. ${data}")
          return 'SKIPPED_EZB_API_ERROR'
        }
      }

      allCollections.each { type, items ->
        log.debug("Starting with type ${type} ..")
        def type_results = [total: 0, skipped: 0, noProvider: 0, noPlatform: 0, noCurator: 0, errors: 0]

        if (!cancelled) {
          Package.withNewSession { session ->
            for (item in items) {
              type_results.total++
              def pkgName = "${item.ezb_collection_id}: ${item.ezb_collection_name}"
              log.debug("Processing ${type} ${item.ezb_collection_name}")

              CuratoryGroup curator = CuratoryGroup.findByName(grailsApplication.config.gokb.ezbAugment.rrCurators)
              Platform platform = item.ezb_collection_platform ? Platform.findByUuid(item.ezb_collection_platform) : null
              Org provider = item.ezb_collection_provider ? Org.findByUuid(item.ezb_collection_provider) : null
              boolean skipped = false

              if (item.ezb_collection_curatory_group != 'ezb_curatory_group') {
                def local_cg = CuratoryGroup.findByUuid(item.ezb_collection_curatory_group)

                if (local_cg) {
                  curator = local_cg
                }
              }

              if (curator && provider && platform) {
                Identifier collection_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb-collection-id', item.ezb_collection_id)

                if (item.ezb_package_type_name != 'Konsortialpaket') {
                  pkgName += ": ${item.ezb_package_type_name}"
                }
                else if (item.ezb_owner.trim()) {
                  pkgName += ": ${item.ezb_owner}"
                }

                Package obj = Package.findByNormname(KBKomponent.generateNormname(pkgName))

                if (!obj) {
                  def candidates = Package.executeQuery('''from Package as p
                                                          where exists (select 1 from Combo where fromComponent = p and toComponent = :cg)
                                                          and exists (select 1 from Combo where fromComponent = p and toComponent = :cid)''', [cg: curator, cid: collection_id])

                  if (candidates.size() == 1) {
                    obj = candidates[0]
                    log.debug("Found package ${obj} via id ${collection_id} and curatoryGroup ${curator}")
                  }
                  else if (candidates.size() > 1) {
                    log.warn("Found ${candidates} as possible package candidates!")
                    skipped = true
                  }
                }

                if (!obj) {
                  log.debug("Creating new Package ..")

                  try {
                    obj = new Package(name: pkgName).save(flush: true, failOnError: true)

                    obj.nominalPlatform = platform
                    obj.provider = provider
                    obj.ids << collection_id
                    obj.curatoryGroups << curator
                  }
                  catch (Exception e) {
                    log.debug("Errors creating new package!", e)
                    type_results.errors++
                  }
                }
                else {
                  log.debug("Handling package ${obj.name}")
                }

                if (obj && !obj.source) {
                  log.debug("Setting new package source..")
                  Source source = new Source(name: pkgName, url: item.ezb_collection_titlelist, targetNamespace: IdentifierNamespace.findByValue('ezb')).save(flush:true, failOnError: true)
                  source.curatoryGroups << curator
                  source.save(flush: true, failOnError: true)

                  obj.source = source

                  obj.save(flush: true, failOnError: true)
                }

                log.debug("Existing package since: ${obj?.dateCreated} - EZB start ${dateFormatService.parseTimestamp(item.ezb_collection_released_date)}")

                if (obj && obj.dateCreated > dateFormatService.parseTimestamp(item.ezb_collection_released_date)) {
                  Job pkg_job = concurrencyManagerService.createJob { pjob ->
                    packageService.updateFromSource(obj, null, pjob, curator)
                  }

                  pkg_job.groupId = curator.id
                  pkg_job.description = "EZB KBART Source ingest (${obj.name})".toString()
                  pkg_job.type = RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
                  pkg_job.linkedItem = [name: obj.name, type: "Package", id: obj.id, uuid: obj.uuid]
                  pkg_job.message("Starting upsert for Package ${obj.name}".toString())
                  pkg_job.startOrQueue()
                  def job_result = pkg_job.get()

                  if (Thread.currentThread().isInterrupted()) {
                    break
                    cancelled = true
                  }

                  log.debug("Finished job with result: ${job_result.message}")
                }
                else if (obj) {
                  log.warn("Matched package is older than the EZB release date.. Skipping!")
                  type_results.skipped++
                }
                else {
                  log.warn("Unable to reference package!")
                }
              }
              else if (!curator) {
                log.debug("Unable to reference a local curatory group for ${item.ezb_collection_curatory_group}")
                type_results.skipped++
                type_results.noCurator++
              }
              else {
                log.debug("Skipping package due to missing info! Provider: ${provider}, Platform: ${platform}")
                type_results.skipped++

                if (!provider) {
                  type_results.noProvider++
                }

                if (!platform) {
                  type_results.noPlatform++
                }
              }
            }
            session.flush()
            session.clear()
          }

          job.message("Completed type ${type} with ${type_results}".toString())
        }
        else {
          log.debug("Job was cancelled.. skipping further processing")
        }
      }
      job.endTime = new Date()
    }
    else {
      log.debug("No API base for open EZB collections configured.")
      return 'SKIPPED_NO_API_URL'
    }

    return 'FINISHED'
  }
}
