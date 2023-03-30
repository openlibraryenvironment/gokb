package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import static groovyx.net.http.Method.*
import groovyx.net.http.*

import org.gokb.cred.*

@Slf4j
class BulkPackageImportService {

  def componentLookupService
  def componentUpdateService
  def concurrencyManagerService
  def dateFormatService
  def grailsApplication
  def packageSourceUpdateService
  def TSVIngestionService
  def validationService

  static final KNOWN_CONFIG_FIELDS = [
    "package_name": [required: true],
    "package_id": [required: false],
    "package_source": [required: false],
    "package_provider": [required: true, cls: Org, field: 'uuid'],
    "package_nominal_platform": [required: true, cls: Platform, field: 'uuid'],
    "package_curatory_group": [required: true, cls: CuratoryGroup, field: 'name'],
    "package_titlelist": [required: true, validate: 'checkUrl' ],
    "package_id_namespace": [required: false, cls: IdentifierNamespace, field: 'value'],
    "package_content_type": [required: false, rdc: 'Package.ContentType'],
    "title_id_namespace": [required: false, cls: IdentifierNamespace, field: 'value'],
    "validity_range": [required: false, rdc: 'Package.Global'],
    "package_created_date": [required: false, validate: 'checkTimestamp'],
    "package_changed_date": [required: false, validate: 'checkTimestamp']
  ]

  def upsertConfig (reqBody) {
    def result = [result: 'OK']
    def validation = validateConfig(reqBody)

    if (validation.valid) {
      def existing_cfg = BulkImportListConfig.findByCode(reqBody.code)

      if (existing_cfg) {
        if (reqBody.cfg) {
          existing_cfg.cfg = (reqBody.cfg as JSON).toString()
        }

        if (reqBody.url) {
          existing_cfg.url = reqBody.url
        }

        if (reqBody.active == true) {
          existing_cfg.automatedUpdate = true
        }
        else if (reqBody.active == false) {
          existing_cfg.automatedUpdate = false
        }
        existing_cfg.save(flush: true)
      }
      else {
        def info = [
          code: name,
          cfg: (reqBody.cfg ? (reqBody as JSON).toString() : null),
          url: reqBody.url,
          automatedUpdate: reqBody.automatedUpdate
        ]

        existing_cfg = new BulkImportListConfig(code: name, cfg: (reqBody.cfg ? (reqBody as JSON).toString() : null), url: reqBody.url)
        existing_cfg.save(flush: true, failOnError: true)
      }
    }
    else {
      result.result = 'ERROR'
      result.message = 'There are validation errors in the provided JSON!'
    }
  }

  def deleteConfig

  def validateConfig (config) {
    def result = [valid: true, errors:[:]]

    if (!config.url && !config.cfg ) {
      result.valid = false
    }

    if (config.url) {
      result.valid &= validationService.checkUrl(config.url) ? true : false
    }

    if (config.frequency) {
      result.valid &= RefdataCategory.lookup('BulkImportListConfig.Frequency', config.frequency) ? true : false
    }

    if (config.cfg) {
      config.cfg.collections.each { cname, collection_info ->
        collection_info.package_list.eachWithIndex { info, idx ->
          def pkg_errors = [:]

          KNOWN_CONFIG_FIELDS.each { fname, cfg ->
            if (field.required && !info[fname]) {
              result.valid = false
              pkg_errors[fname] = [message: "Missing required field '${fname}'!"]
            }
            else if (cfg.cls && cfg.field  == 'uuid' && !cfg.cls.findByUuid(info[fname])) {
              result.valid = false
              pkg_errors[fname] = [message: "Unable to reference '${fname}'!"]
            }
            else if (cfg.cls && cfg.field  == 'name' && !cfg.cls.findByName(info[fname])) {
              result.valid = false
              pkg_errors[fname] = [message: "Unable to reference '${fname}'!"]
            }
            else if (cfg.cls && cfg.field  == 'value' && !cfg.cls.findByValue(info[fname])) {
              result.valid = false
              pkg_errors[fname] = [message: "Unable to reference '${fname}'!"]
            }
            else if (cfg.rdc && info[fname] && RefdataCategory.lookup(cfg.rdc, info[fname])) {
              result.valid = false
              pkg_errors[fname] = [message: "Unable to lookup refdata ${fname} ${cfg.rdc}:${info[fname]}!"]
            }
            else if (cfg.validate && info[fname] && !validationService."${cfg.validate}"(info[fname])) {
              result.valid = false
              pkg_errors[fname] = [message: "Unable to lookup refdata ${fname}:${info[fname]}!"]
            }
          }

          if (pkg_errors) {
            if (!result.errors[cname]) {
              result.errors[cname] = []
            }

            result.errors[cname] << [index: idx, errors: pkg_errors]
          }
        }
      }
    }

    result
  }

  def startUpdate(listInfo, dryRun, User user = null) {
    def result = [result: 'OK']

    RefdataCategory.withNewSession {
      def running_jobs = concurrencyManagerService.getActiveJobsForType(RefdataCategory.lookup('Job.Type', 'BulkPackageIngest'))

      if (running_jobs.size() == 0) {
          log.debug("Creating new job..")
          Job new_job = concurrencyManagerService.createJob { ljob ->
            fetchUpdatedLists(listInfo, dryRun, ljob)
          }

          if (user) {
            new_job.ownerId = user.id
          }

          new_job.description = "Bulk package import ${user ? '(manual)' : ''}"
          new_job.type = RefdataCategory.lookup('Job.Type', 'BulkPackageIngest')
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

  private def fetchUpdatedLists (listInfo, dryRun, job) {
    def result = [result: 'OK', report: [:]]
    def allCollections = [:]
    boolean cancelled = false

    if (listInfo.url) {
      def client = new RESTClient(listInfo.url)
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
          return 'SKIPPED_API_ERROR'
        }
      }
    }
    else if (listInfo.cfg) {
      allCollections = JSON.parse(listInfo.cfg)
    }

    if (allCollections) {
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
          report: []
        ]

        if (!cancelled) {
          for (item in items) {
            if (Thread.currentThread().isInterrupted()) {
              break
              cancelled = true
            }

            Package.withNewSession { session ->
              type_results.total++
              CuratoryGroup curator = CuratoryGroup.findByNameIlike(item.package_curatory_group)
              Platform platform = item.package_nominal_platform ? Platform.findByUuid(item.package_nominal_platform) : null
              IdentifierNamespace title_id_ns = item.title_id_namespace ? IdentifierNamespace.findByValue(item.title_id_namespace) : null
              Org provider = item.package_provider ? Org.findByUuid(item.package_provider) : null
              boolean skipped = false
              Source source = null
              Identifier collection_id = item.package_id_namespace ? componentLookupService.lookupOrCreateCanonicalIdentifier(item.package_id_namespace, item.package_id) : null

              def pkg_result = [
                id: collection_id,
                name: item.package_name,
                result: 'OK',
                gokb_uuid: null,
                errors: [:]
              ]
              log.debug("Processing ${type} ${item.package_name}")

              if (item.package_curatory_group) {
                def local_cg = CuratoryGroup.findByUuid(item.package_curatory_group)

                if (local_cg) {
                  curator = local_cg
                }
              }

              if (curator && provider && platform) {
                Package obj = Package.findByNormname(KBComponent.generateNormname(pkgName))
                boolean skip = false

                if (!obj && collection_id) {
                  def candidates = Package.executeQuery('''from Package as p
                                                          where exists (select 1 from Combo where fromComponent = p and toComponent = :cg)
                                                          and exists (select 1 from Combo where fromComponent = p and toComponent = :cid)''', [cg: curator, cid: collection_id])

                  if (candidates.size() == 1) {
                    obj = candidates[0]
                    log.debug("Found package ${obj} via id ${collection_id} and curatoryGroup ${curator}")
                  }
                  else if (candidates.size() > 1) {
                    log.warn("Found ${candidates} as possible package candidates!")
                    type_results.error++
                    pkg_result.result = 'ERROR'
                    pkg_result.errors.matching = [
                      [
                        message: "The package information matched multiple existing packages!",
                        messageCode: "import.bulk.error.matched.multiple.label",
                      ]
                    ]
                    skip = true
                  }
                } else if (obj && !obj.curatoryGroups.contains(curator)) {
                  log.warn("Matched package has other curators!")
                  type_results.error++
                  pkg_result.result = 'ERROR'
                  pkg_result.errors.matching = [
                    [
                      message: "The matched Package already has another curator!",
                      messageCode: "import.bulk.error.matched.wrongCurator.label",
                    ]
                  ]
                  skip = true
                }

                if (!skip) {
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
                    pkg_result.gokb_uuid = obj.uuid

                    if (!obj.contentType && item.content_type) {
                      obj.contentType = RefdataCategory.lookup('Package.ContentType', item.content_type)
                    }

                    if (!obj.global && item.validity_range) {
                      obj.global = RefdataCategory.lookup('Package.Global', item.validity_range)
                    }

                    obj.nominalPlatform = platform
                    obj.provider = provider
                    obj.save()

                    if (collection_id && !obj.ids.contains(collection_id)) {
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
                          source = new Source(name: pkgName, url: item.package_titlelist, targetNamespace: item.title_id_namespace).save(flush:true, failOnError: true)
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

                    if (source && source.url != item.package_titlelist) {
                      source.url = item.package_titlelist
                      source.save()
                    }
                  }

                  if (obj && source) {
                    def deposit_token = java.util.UUID.randomUUID().toString()
                    File tmp_file = TSVIngestionService.createTempFile(deposit_token)
                    def file_info = packageSourceUpdateService.fetchKbartFile(tmp_file, new URL(item.package_titlelist))
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
                          packageSourceUpdateService.updateFromSource(obj, null, pjob, curator, dryRun)
                        }

                        pkg_job.groupId = curator?.id
                        pkg_job.description = "EZB KBART Source ingest (${obj.name})".toString()
                        pkg_job.type = RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
                        pkg_job.linkedItem = pkgInfo
                        pkg_job.message("Starting upsert for Package ${obj.name}".toString())
                        pkg_job.startOrQueue()
                        def job_result = pkg_job.get()

                        log.debug("Finished job with result: ${job_result}")

                        if (job_result?.badrows) {
                          pkg_result.errors.validation = job_result.badrows.collect { [errors: it.errors, rownum: it.row] }
                        }

                        if (job_result?.result == 'ERROR') {
                          pkg_result.result = 'ERROR'
                          type_results.errors++
                        }
                        else {
                          type_results.success++
                        }
                      }
                      catch (Exception e) {
                        log.error("Exception creating source update job!", e)
                        pkg_result.result = 'ERROR'
                        pkg_result.errors.processing = [
                          [
                            message: "There was an error processing the package import!",
                            messageCode: "import.bulk.error.generic.label"
                          ]
                        ]
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
                  else {
                    log.warn("Unable to reference package!")
                  }
                }
              }
              else if (!curator) {
                log.debug("Unable to reference a local curatory group for ${item.package_curatory_group}")
                type_results.skipped++
                type_results.noCurator++
                pkg_result.result = 'ERROR'
                pkg_result.errors.curatoryGroup = [
                  [
                    message: "Unable to reference package curatory group!",
                    messageCode: "import.bulk.error.curator.lookup"
                  ]
                ]
              }
              else {
                log.debug("Skipping package due to missing info! Provider: ${provider}, Platform: ${platform}")
                type_results.skipped++
                pkg_result.result = 'ERROR'

                if (!provider) {
                  type_results.noProvider++
                  pkg_result.errors.provider = [
                    [
                      message: "Skipped package due to missing provider info!",
                      messageCode: "import.bulk.error.provider.lookup"
                    ]
                  ]
                }

                if (!platform) {
                  type_results.noPlatform++
                  pkg_result.errors.nominalPlatform = [
                    [
                      message: "Skipped package due to missing platform info!",
                      messageCode: "import.bulk.error.nominalPlatform.lookup"
                    ]
                  ]
                }
              }
            }
            type_results.report << pkg_result
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
      log.debug("No collections found.")
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
