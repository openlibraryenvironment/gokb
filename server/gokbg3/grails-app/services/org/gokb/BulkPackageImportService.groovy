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

  static final Map KNOWN_CONFIG_FIELDS = [
    "package_name": [required: true],
    "package_id": [required: false],
    "package_source": [required: false],
    "package_provider": [required: true, cls: Org, field: 'uuid'],
    "package_nominal_platform": [required: true, cls: Platform, field: 'uuid'],
    "package_curatory_group": [required: true, cls: CuratoryGroup, field: 'name'],
    "package_titlelist": [required: true, validate: 'checkUrl' ],
    "package_id_namespace": [required: false, cls: IdentifierNamespace, field: 'value'],
    "content_type": [required: false, rdc: 'Package.ContentType'],
    "title_id_namespace": [required: false, cls: IdentifierNamespace, field: 'value'],
    "global": [required: false, rdc: 'Package.Global'],
    "package_created_date": [required: false, validate: 'checkTimestamp'],
    "package_changed_date": [required: false, validate: 'checkTimestamp']
  ]

  def upsertConfig (reqBody, user) {
    def result = [result: 'OK']
    def validation = validateConfig(reqBody)

    if (validation.errors) {
      result.errors = validation.errors
      result.result = 'WARNING'
      result.message = 'There are single package level validation errors in the provided JSON!'
    }

    if (validation.valid) {
      def existing_cfg = BulkImportListConfig.findByCode(reqBody.code)

      if (existing_cfg) {
        if (user.superUserStatus || existing_cfg.owner == user) {
          if (reqBody.cfg) {
            existing_cfg.cfg = (reqBody.cfg as JSON).toString()
          }

          if (reqBody.url) {
            existing_cfg.url = reqBody.url
          }

          if (reqBody.frequency) {
            existing_cfg.frequency = RefdataCategory.lookup('BulkImportListConfig.Frequency', reqBody.frequency)
          }

          if (reqBody.active == true) {
            existing_cfg.automatedUpdate = true
          }
          else if (reqBody.active == false) {
            existing_cfg.automatedUpdate = false
          }
          existing_cfg.save(flush: true)
        }
      }
      else {
        def info = [
          code: reqBody.code,
          cfg: (reqBody.cfg ? (reqBody.cfg as JSON).toString() : null),
          frequency: (reqBody.frequency ? RefdataCategory.lookup('BulkImportListConfig.Frequency', reqBody.frequency) : null),
          owner: user,
          url: reqBody.url,
          automatedUpdate: reqBody.automatedUpdate
        ]

        existing_cfg = new BulkImportListConfig(info)
        existing_cfg.save(flush: true, failOnError: true)
      }
    }
    else {
      result.result = 'ERROR'
      result.message = 'There have been critical validation errors!'
    }

    result
  }

  private def validateConfig (config) {
    def result = [valid: true, errors:[:]]

    if (!config.code || !config.code.trim()) {
      result.valid = false
      result.errors.info = [message: "No config code provided!"]
    }

    if (!config.url && !config.cfg) {
      result.valid = false
      result.errors.info = [message: "No config content info provided (local/URL)!"]
    }

    if (config.url) {
      if (!validationService.checkUrl(config.url)) {
        result.valid = false
        result.errors.url = [message: "Invalid URL provided: ${config.url}!", value: config.url]
      }
      else {
        def remote_conf = fetchRemoteConfig(config.url)

        if (remote_conf) {
          boolean conf_valid = true
          remote_conf.collections?.eachWithIndex { col, idx ->
            def col_errors = validateCollection(col)

            if (col_errors) {
              if (col_errors.generic) {
                result.valid = false
              }
              if (!result.errors.cfg) {
                result.errors.cfg = [remote: [:]]
              }

              result.errors.cfg.remote["${idx}"] = col_errors
            }
          }

          if (!conf_valid) {
            result.valid = false
            result.errors.url = [message: "Invalid config at provided URL ${config.url}!", value: config.url]
          }
        }
        else {
          result.valid = false
          result.errors.url = [message: "Unable to fetch config item from url ${config.url}!", value: config.url]
        }
      }
    }

    if (config.frequency && !RefdataCategory.lookup('BulkImportListConfig.Frequency', config.frequency)) {
      result.valid = false
      result.errors.frequency = [message: "Unable to lookup frequency ${config.frequency}!", value: config.frequency]
    }

    if (config.cfg) {
      config.cfg.collections.eachWithIndex { col, idx ->
        def col_errors = validateCollection(col)

        if (col_errors) {
          if (col_errors.generic) {
            result.valid = false
          }
          if (!result.errors.cfg) {
            result.errors.cfg = [local: [:]]
          } else if (!result.errors.cfg.local) {
            result.errors.cfg.local = [:]
          }

          result.errors.cfg.local["${idx}"] = col_errors
        }
      }
    }

    result
  }

  private def validateCollection(col) {
    log.debug("Checking collection info: ${col}")
    def errors = [:]
    def col_errors = checkConfigItem(col, false)

    if (col_errors.size() > 0) {
      errors.generic = col_errors
    }

    col.package_list.eachWithIndex { info, idx ->
      log.debug("Checking package info: ${info}")
      def pkg_errors = checkConfigItem(info, true)

      if (pkg_errors.size() > 0) {
        if (!errors.packages) {
          errors.packages = []
        }

        errors.packages << [index: idx, errors: pkg_errors]
      }
    }

    errors
  }

  private def fetchRemoteConfig(url) {
    def client = new RESTClient(url)

    client.request(GET, ContentType.JSON) {
      response.success = { resp, data ->
        log.debug("Got bulk collection list")

        if (data.collections) {
          return data
        }
        else {
          return null
        }
      }
      response.failure = { resp, data ->
        log.error("Got remote config request status ${resp.status} .. ${data}")
        return null
      }
    }
  }

  private def checkConfigItem(cobj, boolean specific = false) {
    def errors = [:]

    KNOWN_CONFIG_FIELDS.each { fname, cfg ->
      if (specific && cfg.required && !cobj[fname]) {
        errors[fname] = [message: "Missing required field '${fname}'!"]
      }
      else if (cfg.cls && cobj[fname] && cfg.field  == 'uuid') {
        if (!cfg.cls.findByUuid(cobj[fname])) {
          if (!cfg.cls.findByName(cobj[fname])) {
            errors[fname] = [message: "Unable to reference '${fname}'!"]
          }
          else if (cfg.cls.findAllByName(cobj[fname]).size() > 1) {
            log.error("BulkConfig: Found dupes for ${fname} ${cobj[fname]}!")
            errors[fname] = [message: "Found dupes for ${fname} ${cobj[fname]}!"]
          }
        }
      }
      else if (cfg.cls && cobj[fname] && cfg.field  == 'name' && !cfg.cls.findByName(cobj[fname])) {
        errors[fname] = [message: "Unable to reference '${fname}'!"]
      }
      else if (cfg.cls && cobj[fname] && cfg.field  == 'value' && !cfg.cls.findByValue(cobj[fname])) {
        errors[fname] = [message: "Unable to reference '${fname}'!"]
      }
      else if (cfg.rdc && cobj[fname] && !RefdataCategory.lookup(cfg.rdc, cobj[fname])) {
        errors[fname] = [message: "Unable to lookup refdata ${fname} ${cfg.rdc}:${cobj[fname]}!"]
      }
      else if (cfg.validate && cobj[fname] && !validationService."${cfg.validate}"(cobj[fname])) {
        errors[fname] = [message: "Unable to lookup refdata ${fname}:${cobj[fname]}!"]
      }
    }

    errors
  }

  def startUpdate(listInfo, dryRun, async, User user = null) {
    def result = [result: 'OK']
    def job_rdv = RefdataCategory.lookup('Job.Type', 'BulkPackageIngest')
    def running_jobs = concurrencyManagerService.getActiveJobsForType(job_rdv)

    if (running_jobs.size() == 0) {
        log.debug("Creating new job..")
        Job new_job = concurrencyManagerService.createJob { ljob ->
          fetchUpdatedLists(listInfo, dryRun, ljob)
        }

        if (user) {
          new_job.ownerId = user.id
        }

        new_job.description = "Bulk package import ${user ? '(manual)' : ''}"
        new_job.type = job_rdv
        new_job.startTime = new Date()
        new_job.startOrQueue()

        if (!user || !async) {
          result = new_job.get()
          log.debug("Got result ${result}!")
        }
        else {
          result.job_id = new_job.uuid
        }
    }
    else {
      log.debug("Job is already running!")
      result.result = 'SKIPPED_ALREADY_RUNNING'
    }

    result
  }

  private def fetchUpdatedLists (listInfo, dryRun, job) {
    def result = [result: 'OK', report: [:]]
    def allCollections = []
    boolean cancelled = false

    if (listInfo.url) {
      log.debug("Fetching config from ${listInfo.url} ..")
    }
    else if (listInfo.cfg != null) {
      log.debug("Parsing static config ..")
      def local_cfg = JSON.parse(listInfo.cfg)

      if (local_cfg) {
        log.debug("Parsed successfully: ${local_cfg}")
        allCollections = local_cfg.collections
      }
    }

    if (allCollections) {
      for (type in allCollections) {
        log.debug("Starting with collection ${type.collection_name} ..")
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
          for (item in type.package_list) {
            if (Thread.currentThread().isInterrupted()) {
              break
              cancelled = true
            }

            Package.withNewSession { session ->
              type_results.total++
              CuratoryGroup curator = (item.package_curatory_group || type.package_curatory_group) ? CuratoryGroup.findByNameIlike(item.package_curatory_group ?: type.package_curatory_group) : null
              Platform platform = (item.package_nominal_platform || type.package_nominal_platform) ? Platform.findByUuid(item.package_nominal_platform ?: type.package_nominal_platform) : null
              IdentifierNamespace title_id_ns = (item.title_id_namespace || type.title_id_namespace) ? IdentifierNamespace.findByValue(item.title_id_namespace ?: type.title_id_namespace) : null
              Org provider = (item.package_provider || type.package_provider) ? Org.findByUuid(item.package_provider ?: type.package_provider) : null
              Identifier collection_id = ((item.package_id_namespace || type.package_id_namespace) && item.package_id) ? componentLookupService.lookupOrCreateCanonicalIdentifier((item.package_id_namespace ?: type.package_id_namespace), item.package_id) : null
              boolean skip = false
              Source source = null

              def pkg_result = [
                id: collection_id?.value,
                name: item.package_name,
                result: 'OK',
                gokb_uuid: null,
                errors: [:]
              ]
              log.debug("Processing ${type.collection_name} ${item.package_name}")

              if (curator && listInfo.owner && !listInfo.owner.superUserStatus && !listInfo.owner.curatoryGroups.contains(curator)) {
                skip = true
                pkg_result.errors.curatoryGroup = [
                  [
                    message: "Config owner does not have the permission!",
                    messageCode: "import.bulk.error.curator.permissions"
                  ]
                ]
              }

              if (item.package_curatory_group || type.package_curatory_group) {
                def local_cg = CuratoryGroup.findByUuid(item.package_curatory_group ?: type.package_curatory_group)

                if (local_cg) {
                  curator = local_cg
                }
              }

              if (!skip && curator && provider && platform) {
                Package obj = Package.findByNormname(KBComponent.generateNormname(item.package_name))

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

                if (obj?.source?.bulkConfig && obj.source.bulkConfig.id != listInfo.id) {
                  log.warn("Matched package ${obj} already has another bulk config (${obj.source.bulkConfig.id}) assigned!")
                  pkg_result.errors.matching = [
                    [
                      message: "A single package has been matched, but its source is already connected to antother bulk config!",
                      messageCode: "import.bulk.error.matched.sourceBulkConfig.label",
                    ]
                  ]
                  skip = true
                }

                if (!skip) {
                  if (!obj) {
                    log.debug("Creating new Package ..")

                    try {
                      obj = new Package(name: item.package_name).save(flush: true, failOnError: true)
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

                    if (!obj.contentType && (item.content_type || type.content_type)) {
                      obj.contentType = RefdataCategory.lookup('Package.ContentType', item.content_type ?: type.content_type)
                    }

                    if (item.global || type.global) {
                      obj.global = RefdataCategory.lookup('Package.Global', item.global ?: type.global)
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
                        def dupe = Source.findByName(item.package_name)

                        if (!dupe) {
                          source = new Source(name: item.package_name).save(flush:true, failOnError: true)
                        }
                        else {
                          log.warn("Found existing source with package name ${item.package_name}!")
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

                    if (source) {
                      log.debug("Setting source info ..")
                      source.bulkConfig = listInfo
                      source.targetNamespace = title_id_ns
                      source.url = item.package_titlelist

                      if (listInfo.frequency) {
                        source.automaticUpdates = listInfo.automatedUpdate
                        source.frequency = RefdataCategory.lookup('Source.Frequency', listInfo.frequency.value)
                      }
                      else {
                        log.debug("No frequency for ${item.package_name} - Setting automated source update to 'false'!")
                        source.automaticUpdates = false
                      }
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
                        pkg_job.description = "BulkConfig KBART Source ingest (${obj.name})".toString()
                        pkg_job.type = dryRun ? RefdataCategory.lookup('Job.Type', 'KBARTSourceIngestDryRun') : RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
                        pkg_job.linkedItem = pkgInfo
                        pkg_job.message("Starting upsert for Package ${obj.name}".toString())
                        pkg_job.startOrQueue()
                        def job_result = pkg_job.get()

                        log.debug("Finished job with result: ${job_result?.result}")

                        pkg_result.validation = job_result?.validation

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
                log.debug("Unable to reference a local curatory group for ${item.package_curatory_group ?: type.package_curatory_group}")
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
              type_results.report << pkg_result
            }
          }
        }
        else {
          log.debug("Job was cancelled.. skipping further processing")
        }

        // job.message("Completed type ${type.collection_name} with ${type_results}".toString())
        result.report[type.collection_name] = type_results
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
