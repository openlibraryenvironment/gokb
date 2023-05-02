package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

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
  def packageSourceUpdateService
  def TSVIngestionService

  static ArrayList activeTypes = [
    'collections_from_national_license_packages',
    'collections_from_alliance_license_packages',
    'collections_from_consortia_packages',
    'collections_from_national_consortia_packages'
  ]

  def startUpdate(User user = null) {
    def result = [result: 'OK']

    RefdataCategory.withNewSession {
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
    }

    result
  }

  private def fetchUpdatedLists (job) {
    def result = [result: 'OK', report: [:]]

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

      // Local backup for testing

      // File jsonFile = new File(getClass().getResource(
      //     "${File.separator}ezb_collections.json").toURI())

      // def data = new JsonSlurper().parse(jsonFile)

      // data.collections?.each { type, items ->
      //   log.debug("Mapping ${type} with ${items.size()} items")
      //   if (type in activeTypes) {
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
          validationErrors: [:]
        ]

        if (!cancelled) {
          for (item in items) {
            if (Thread.currentThread().isInterrupted()) {
              break
              cancelled = true
            }

            Package.withNewSession { session ->
              type_results.total++
              def pkgName = "${item.ezb_collection_id}: ${item.ezb_collection_name}"
              log.debug("Processing ${type} ${item.ezb_collection_name}")

              CuratoryGroup curator = CuratoryGroup.findByName(grailsApplication.config.gokb.ezbAugment.rrCurators)
              Platform platform = item.ezb_collection_platform ? Platform.findByUuid(item.ezb_collection_platform) : null
              IdentifierNamespace ezb_ns = IdentifierNamespace.findByValue('ezb')
              Org provider = item.ezb_collection_provider ? Org.findByUuid(item.ezb_collection_provider) : null
              boolean skipped = false
              Source source = null

              if (item.ezb_collection_curatory_group) {
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

                Package obj = Package.findByNormname(KBComponent.generateNormname(pkgName))

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
                    type_results.created++
                  }
                  catch (Exception e) {
                    log.debug("Errors creating new package!", e)
                    type_results.errors++
                  }
                }
                else {
                  log.debug("Handling package ${obj.name}")
                  type_results.updated++
                }

                if (obj) {
                  if(!obj.contentType) {
                    obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Journal')
                  }

                  if (!obj.global) {
                    obj.global = RefdataCategory.lookup('Package.Global', 'Consortium')
                  }

                  obj.nominalPlatform = platform
                  obj.provider = provider
                  obj.save()

                  if (!obj.ids.contains(collection_id)) {
                    obj.ids << collection_id
                  }

                  if (!obj.curatoryGroups.contains(curator)) {
                    obj.curatoryGroups << curator
                  }

                  obj.save()

                  source = obj.source

                  if (!source) {
                    log.debug("Setting new package source..")

                    try {
                      def dupe = Source.findByName(pkgName)

                      if (!dupe) {
                        source = new Source(name: pkgName, url: item.ezb_collection_titlelist, targetNamespace: ezb_ns).save(flush:true, failOnError: true)
                      }
                      else {
                        log.warn("Found existing source with package name ${pkgName}!")
                        source = dupe
                      }
                    }
                    catch (Exception e) {
                      log.error("Exception creating source:", e)
                      type_results.errors++
                    }

                    if (source) {
                      source.curatoryGroups << curator
                      source.save()

                      obj.source = source
                      obj.save(flush: true)
                    }
                  }

                  if (source && source.url != item.ezb_collection_titlelist) {
                    source.url = item.ezb_collection_titlelist
                    source.save()
                  }
                }

                log.debug("Existing package since: ${obj?.dateCreated} - EZB start ${dateFormatService.parseTimestamp(item.ezb_collection_released_date)}")

                if (obj && source && obj.dateCreated > dateFormatService.parseTimestamp(item.ezb_collection_released_date)) {
                  def deposit_token = java.util.UUID.randomUUID().toString()
                  File tmp_file = TSVIngestionService.createTempFile(deposit_token)
                  def file_info = packageSourceUpdateService.fetchKbartFile(tmp_file, new URL(item.ezb_collection_titlelist))
                  def pkgInfo = [name: obj.name, type: "Package", id: obj.id, uuid: obj.uuid]
                  RefdataValue type_fa = RefdataCategory.lookup('Combo.Type', 'KBComponent.FileAttachments')

                  def ordered_combos = Combo.executeQuery('''select c.toComponent from Combo as c
                                                            where c.type = :ct
                                                            and c.fromComponent.id = :pkg
                                                            order by c.dateCreated desc''', [ct: type_fa, pkg: obj.id])

                  def last_df_md5 = ordered_combos.size() > 0 ? ordered_combos[0].md5 : null

                  if (!last_df_md5 || last_df_md5 != TSVIngestionService.analyseFile(tmp_file).md5sumHex) {
                    obj.refresh()
                    log.debug("Creating new import job ..")
                    try {
                      Job pkg_job = concurrencyManagerService.createJob { pjob ->
                        packageSourceUpdateService.updateFromSource(obj, null, pjob, curator)
                      }

                      pkg_job.groupId = curator?.id
                      pkg_job.description = "EZB KBART Source ingest (${obj.name})".toString()
                      pkg_job.type = RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
                      pkg_job.linkedItem = pkgInfo
                      pkg_job.message("Starting upsert for Package ${obj.name}".toString())
                      pkg_job.startOrQueue()
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
                      type_results.errors++
                    }
                  }
                  else {
                    log.debug("Skipping unchanged Package file ${obj.name}.")
                    type_results.unchanged++
                  }
                }
                else if (!source) {
                  log.debug("No source object created.. skip")
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
                type_results.skippedList << item.ezb_collection_id
              }
              else {
                log.debug("Skipping package due to missing info! Provider: ${provider}, Platform: ${platform}")
                type_results.skipped++
                type_results.skippedList << item.ezb_collection_id

                if (!provider) {
                  type_results.noProvider++
                }

                if (!platform) {
                  type_results.noPlatform++
                }
              }
            }
          }
        }
        else {
          log.debug("Job was cancelled.. skipping further processing")
        }

        job.message("Completed type ${type} with ${type_results}".toString())
        result.report[type] = type_results
      }

      result.result = 'FINISHED'
      job.endTime = new Date()
    }
    else {
      log.debug("No API base for open EZB collections configured.")
      result.result = 'SKIPPED_NO_API_URL'
    }

    JobResult.withNewSession {
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
}
