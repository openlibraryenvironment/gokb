package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.*

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient

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
    "package_content_provider": [required: false, cls: Org, field: 'uuid'],
    "package_nominal_platform": [required: true, cls: Platform, field: 'uuid'],
    "package_curatory_group": [required: true, cls: CuratoryGroup, field: 'name'],
    "package_titlelist": [required: true, validate: 'checkUrl' ],
    "package_id_namespace": [required: false, cls: IdentifierNamespace, field: 'value'],
    "content_type": [required: false, rdc: 'Package.ContentType'],
    "title_id_namespace": [required: false, cls: IdentifierNamespace, field: 'value'],
    "global": [required: false, rdc: 'Package.Global'],
    "package_created_date": [required: false, validate: 'checkTimestamp'],
    "package_changed_date": [required: false, validate: 'checkTimestamp'],
    "fixed": [required: false, type: Boolean],
    "consistent": [required: false, type: Boolean],
    "breakable": [required: false, type: Boolean],
    "scope": [required: false, rdc: 'Package.Scope'],
    "other_package_identifiers": [required: false],
    "start_year": [required: false],
    "end_year": [required: false],
  ]

  @Transactional
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
            RefdataValue frequency_val = RefdataCategory.lookup('BulkImportListConfig.Frequency', reqBody.frequency)

            if (frequency_val) {
              existing_cfg.frequency = frequency_val
            }
            else {
              log.warn("Unable to reference bulk frequency ${reqBody.frequency}")
            }
          }

          if (reqBody.automatedUpdate == true) {
            existing_cfg.automatedUpdate = true
          }
          else if (reqBody.automatedUpdate == false) {
            existing_cfg.automatedUpdate = false
          }

          if (reqBody.updateOnly == true) {
            existing_cfg.updateOnly = true
          }
          else if (reqBody.updateOnly == false) {
            existing_cfg.updateOnly = false
          }

          if (reqBody.prependProviderName == true) {
            existing_cfg.prependProviderName = true
          }
          else if (reqBody.prependProviderName == false) {
            existing_cfg.prependProviderName = false
          }

          if (reqBody.updateNames == true) {
            existing_cfg.updateNames = true
          }
          else if (reqBody.updateNames == false) {
            existing_cfg.updateNames = false
          }

          if (reqBody.curatorPolicy) {
            RefdataValue policy_val = RefdataCategory.lookup('BulkImportListConfig.CuratorPolicy', reqBody.curatorPolicy)

            if (policy_val) {
              existing_cfg.curatorPolicy = policy_val
            }
            else {
              log.warn("Unable to reference bulk curator policy ${reqBody.curatorPolicy}")
            }
          }

          existing_cfg.save(flush: true, failOnError: true)
        }
      }
      else {
        def info = [
          code: reqBody.code,
          cfg: (reqBody.cfg ? (reqBody.cfg as JSON).toString() : null),
          frequency: (reqBody.frequency ? RefdataCategory.lookup('BulkImportListConfig.Frequency', reqBody.frequency) : null),
          owner: user,
          url: reqBody.url,
          automatedUpdate: reqBody.automatedUpdate,
          updateOnly: reqBody.updateOnly,
          prependProviderName: reqBody.prependProviderName,
          updateNames: reqBody.updateNames,
          curatorPolicy: (reqBody.curatorPolicy ? RefdataCategory.lookup('BulkImportListConfig.CuratorPolicy', reqBody.curatorPolicy) : null)
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

  private def validateConfig (Map config) {
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

  private def validateCollection(Map col) {
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

  private def fetchRemoteConfig(String url) {
    try {
      def resp = HttpClient.create(new URL(url)).toBlocking().retrieve(HttpRequest.GET("/"), Map.class)

      return resp
    }
    catch (Exception e) {
      return null
    }
  }

  private def checkConfigItem(Map cobj, boolean specific = false) {
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
      else if (cfg.type && cobj.containsKey(fname) && cobj[fname] != null && cobj[fname].class != cfg.type) {
        errors[fname] = [message: "Entry for field ${fname} must be of type ${cfg.type}!"]
      }
    }

    // Validate years

    if (cobj.containsKey('start_year')) {
      if (cobj.start_year == null) {
        // No start year
      }
      else if (cobj.start_year instanceof Integer) {
        if (cobj.start_year < 1700 || cobj.start_year > 9999) {
          errors['start_year'] = [message: "Package years must be between 1700 and 9999!"]
        }
      }
      else {
        errors['start_year'] = [message: "Package years must be four digit integers or null!"]
      }
    }

    if (cobj.containsKey('end_year')) {
      if (cobj.end_year == null) {
        // No end year
      }
      else if (end_year instanceof Integer) {
        if (cobj.end_year < 1700 || cobj.start_year > 9999) {
          errors['end_year'] = [message: "Package years must be between 1700 and 9999!"]
        }
        else if (!cobj.start_year) {
          errors['end_year'] = [message: "Missing start_year for given end_year!"]
        }
        else if (cobj.start_year instanceof Integer && cobj.end_year < cobj.start_year) {
          errors['end_year'] = [message: "Package end_year must not be earlier than the start_year!"]
        }
      }
      else {
        errors['end_year'] = [message: "Package years must be four digit integers or null!"]
      }
    }

    errors
  }

  @Transactional
  def startUpdate(BulkImportListConfig listInfo, Boolean dryRun, Boolean async, User user = null) {
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

  private def fetchUpdatedLists (BulkImportListConfig list_info, Boolean dryRun, Job job) {
    def result = [result: 'OK', report: [:]]
    def allCollections = []
    boolean cancelled = false

    if (list_info.url) {
      log.debug("Fetching config from ${list_info.url} ..")
    }
    else if (list_info.cfg != null) {
      log.debug("Parsing static config ..")
      def local_cfg = JSON.parse(list_info.cfg)

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

            boolean skip = false
            def pkgInfo = [:]
            def curator_id = null
            def title_ns_id = null
            def source_id = null
            def pkg_result = [:]

            Package.withNewSession { session ->
              type_results.total++
              CuratoryGroup curator
              BulkImportListConfig listInfo = BulkImportListConfig.get(list_info.id)

              if (item.package_curatory_group || type.package_curatory_group) {
                curator = CuratoryGroup.findByNameIlike(item.package_curatory_group ?: type.package_curatory_group)

                if (!curator) {
                  curator = CuratoryGroup.findByUuid(item.package_curatory_group ?: type.package_curatory_group)
                }
              }

              if (curator) {
                curator_id = curator.id
              }

              Platform platform = (item.package_nominal_platform || type.package_nominal_platform) ? Platform.findByUuid(item.package_nominal_platform ?: type.package_nominal_platform) : null
              IdentifierNamespace title_id_ns = (item.title_id_namespace || type.title_id_namespace) ? IdentifierNamespace.findByValue(item.title_id_namespace ?: type.title_id_namespace) : null
              Org provider = (item.package_provider || type.package_provider) ? Org.findByUuid(item.package_provider ?: type.package_provider) : null
              Org contentProvider = (item.package_content_provider || type.package_content_provider) ? Org.findByUuid(item.package_content_provider ?: type.package_content_provider) : null
              Identifier collection_id

              if ((item.package_id_namespace || type.package_id_namespace) && item.package_id) {
                collection_id = componentLookupService.lookupOrCreateCanonicalIdentifier((item.package_id_namespace ?: type.package_id_namespace), item.package_id)
              }

              Source source = null

              pkg_result = [
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
                    message: "Config owner does not have the permission to create package with defined curator!",
                    messageCode: "import.bulk.error.curator.permissions"
                  ]
                ]
              }

              if (!skip && curator && provider && platform) {
                Package obj = null

                if (item.package_uuid) {
                  obj = Package.findByUuid(item.package_uuid)
                }

                if (!obj && collection_id) {
                  def candidates = collection_id.getActiveIdentifiedComponents('Package')

                  if (candidates.size() == 1) {
                    obj = KBComponent.deproxy(candidates[0])
                    log.debug("Found package ${obj} via id ${collection_id} and curatoryGroup ${curator}")
                  }
                  else if (candidates.size() > 1) {
                    log.warn("Found ${candidates} as possible package candidates!")
                    type_results.errors++
                    pkg_result.result = 'ERROR'
                    pkg_result.errors.matching = [
                      [
                        message: "The package information matched multiple existing packages!",
                        messageCode: "import.bulk.error.matched.multiple.label",
                      ]
                    ]
                    skip = true
                  }
                }

                if (!obj) {
                  obj = Package.findByNormname(KBComponent.generateNormname(item.package_name))
                }

                if (obj && !obj.curatoryGroups.contains(curator) && listInfo.curatorPolicy?.value == 'Skip') {
                  log.warn("Matched package has other curators!")

                  type_results.errors++
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
                      message: "A single package has been matched, but its source is already connected to another bulk config!",
                      messageCode: "import.bulk.error.matched.sourceBulkConfig.label",
                    ]
                  ]
                  skip = true
                }

                if (!skip) {
                  boolean pkg_created = false
                  String provider_prefix = provider.preferredShortname ?: provider.name
                  String final_name = item.package_name

                  if (listInfo.prependProviderName) {
                    final_name = "${provider_prefix}: ${final_name}"
                  }

                  if (!obj) {
                    if (listInfo.updateOnly) {
                      log.debug("Skipping existing Package due to config ..")
                      skip = true
                    }
                    else {
                      log.debug("Creating new Package ..")

                      try {
                        obj = new Package(name: final_name).save(flush: true, failOnError: true)
                        type_results.created++
                        pkg_created = true
                      }
                      catch (Exception e) {
                        log.debug("Errors creating new package!", e)
                        type_results.errors++

                        pkg_result.errors.name = [
                          [
                            message: "Unable to save package '${final_name}', as there is already another package with this name!",
                            baddata: item.package_name
                          ]
                        ]
                      }
                    }
                  }
                  else {
                    log.debug("Handling package ${obj.name}")
                    type_results.updated++
                  }

                  if (obj) {
                    source = obj.source

                    if (item.content_type || type.content_type) {
                      if (!obj.contentType) {
                        obj.contentType = RefdataCategory.lookup('Package.ContentType', item.content_type ?: type.content_type)
                      }
                      else {
                        log.debug("Not updating existing contentType ..")
                      }
                    }

                    if (item.global || type.global) {
                      obj.global = RefdataCategory.lookup('Package.Global', item.global ?: type.global)
                    }

                    try {

                      if (item.containsKey('fixed') || type.fixed != null) {
                        setPackageBinaryRefdata(obj, 'fixed', 'Package.Fixed', item.fixed != null ? item.fixed : type.fixed)
                      }

                      if (item.containsKey('breakable') || type.breakable != null) {
                        setPackageBinaryRefdata(obj, 'breakable', 'Package.Breakable', item.breakable != null ? item.breakable : type.breakable)
                      }

                      if (item.containsKey('consistent') || type.consistent != null) {
                        setPackageBinaryRefdata(obj, 'consistent', 'Package.Consistent', item.consistent != null ? item.consistent : type.consistent)
                      }

                      if (item.scope || type.scope) {
                        obj.scope = RefdataCategory.lookup('Package.Scope', item.scope ?: type.scope)
                      }
                    }
                    catch (Exception e) {
                      log.debug("FAIL: ", e)
                    }

                    if (listInfo.updateNames && final_name != obj.name) {
                      obj.name = final_name
                      obj.save(flush: true, failOnError: true)

                      if (source) {
                        source.name = final_name
                        source.save(flush: true, failOnError: true)
                      }
                    }

                    if (item.containsKey('end_year')) {
                      if (item.end_year && item.containsKey('start_year')) {
                        if (!item.start_year) {
                          pkg_result.errors.end_year = [
                            [
                              message: "Unable to set package end year due to missing start date!",
                              baddata: item.end_year
                            ]
                          ]
                        }
                      }
                      else if (item.end_year && !obj.startYear) {
                        pkg_result.errors.end_year = [
                          [
                            message: "Unable to set package end year due to missing start date!",
                            baddata: item.end_year
                          ]
                        ]
                      }

                      if (!pkg_result.errors) {
                        obj.endYear = item.end_year ? item.end_year : null
                      }
                    }

                    if (!pkg_result.errors && item.containsKey('start_year')) {
                      obj.startYear = item.start_year ? item.start_year : null
                    }

                    obj.nominalPlatform = platform
                    obj.provider = provider
                    obj.contentProvider = contentProvider
                    obj.save()

                    pkg_result.gokb_uuid = obj.uuid
                    pkgInfo = [name: obj.name, type: "Package", id: obj.id, uuid: obj.uuid]

                    if (collection_id && !obj.ids.contains(collection_id)) {
                      obj.ids << collection_id
                    }

                    item.other_package_identifiers.each { opid ->
                      Identifier other_id

                      try {
                        other_id = componentLookupService.lookupOrCreateCanonicalIdentifier(opid.namespace, opid.value)
                      }
                      catch (grails.validation.ValidationException ve) {
                        if (!pkg_result.errors.other_package_identifiers) {
                          pkg_result.errors.other_package_identifiers = []
                        }

                        pkg_result.errors.other_package_identifiers << [
                          [
                            message: "Invalid additional package identifier!",
                            messageCode: "import.bulk.error.ids.format",
                            baddata: opid
                          ]
                        ]
                      }
                      catch (Exception e) {
                        if (!pkg_result.errors.other_package_identifiers) {
                          pkg_result.errors.other_package_identifiers = []
                        }

                        pkg_result.errors.other_package_identifiers << [
                          [
                            message: "Unable to reference additional package identifier!",
                            messageCode: "import.bulk.error.ids.unknown",
                            baddata: opid
                          ]
                        ]
                      }

                      RefdataValue ns_type_pkg = RefdataCategory.lookup("IdentifierNamespace.TargetType", "Package")
                      boolean already_linked = obj.ids.contains(other_id)

                      if (other_id) {
                        if (!already_linked && (!other_id.namespace.targetType || other_id.namespace.targetType == ns_type_pkg)) {
                          obj.ids << other_id
                        }
                        else if (already_linked) {
                          log.debug("Skipping existing id ${other_id}")
                        }
                        else {
                          if (!pkg_result.errors.other_package_identifiers) {
                            pkg_result.errors.other_package_identifiers = []
                          }

                          pkg_result.errors.other_package_identifiers << [
                            [
                              message: "Additional identifier namespace '${other_id.namespace.value}' is not permissible for packages!",
                              messageCode: "import.bulk.error.ids.targetType",
                              baddata: opid
                            ]
                          ]
                        }
                      }
                    }


                    RefdataValue type_pc = RefdataCategory.lookup("Combo.Type", "Package.CuratoryGroups")

                    def existing_combos_count = Combo.executeQuery('''select count(*) from Combo
                                                                      where type = :ct
                                                                      and fromComponent = :pkg
                                                                      and toComponent = :ncg
                                                                  ''', [
                                                                    ct: type_pc,
                                                                    pkg: obj,
                                                                    ncg: curator
                                                                  ])[0]

                    if (existing_combos_count == 0) {
                      log.debug("Handling changed curator ..")

                      if (pkg_created || listInfo.curatorPolicy?.value == 'Add' || listInfo.curatorPolicy?.value == 'New') {
                        log.debug("Adding new curator ${curator}")

                        obj.curatoryGroups << curator
                        obj.save(flush: true)

                        if (listInfo.curatorPolicy?.value == 'New') {
                          log.debug("Removing old groups ..")
                          obj.curatoryGroups.retainAll([curator])
                        }
                      }
                      else if (listInfo.curatorPolicy?.value == 'Old') {
                        log.debug("Not changing existing package curator ..")
                      }

                      log.debug("New curator list: ${obj.curatoryGroups}")
                    }

                    obj.save(flush: true, failOnError: true)


                    if (!source) {
                      log.debug("Setting new package source..")

                      try {
                        def dupe = Source.findByName(final_name)

                        if (!dupe) {
                          source = new Source(name: final_name).save(flush:true, failOnError: true)
                        }
                        else {
                          log.warn("Found existing source with package name ${final_name}!")
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
                    else {
                      if (source.curatoryGroups == obj.curatoryGroups) {
                        log.debug("Not updating source curators ..")
                      }
                      else {
                        obj.curatoryGroups.each { pcg ->
                          if (!source.curatoryGroups.contains(pcg)) {
                            source.curatoryGroups << pcg
                          }
                        }

                        source.save(flush: true)

                        source.curatoryGroups.retainAll(obj.curatoryGroups)
                        source.save(flush: true)
                      }
                    }

                    if (source) {
                      log.debug("Setting source info ..")
                      source.bulkConfig = listInfo
                      source.targetNamespace = title_id_ns
                      source.url = item.package_titlelist
                      source.frequency = listInfo.frequency ? RefdataCategory.lookup('Source.Frequency', listInfo.frequency.value) : null

                      if (listInfo.frequency && source.url) {
                        source.automaticUpdates = listInfo.automatedUpdate
                      }
                      else {
                        log.debug("No frequency or url for ${item.package_name} - Setting automated source update to 'false'!")
                        source.automaticUpdates = false
                      }
                      source.save()
                    }
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
            }

            if (!skip && pkgInfo.id && source_id) {
              if (hasChangedFile(pkgInfo.id, item)) {
                log.debug("Creating new import job ..")

                try {
                  Job pkg_job = concurrencyManagerService.createJob { pjob ->
                    packageSourceUpdateService.updateFromSource(pkgInfo.id, null, pjob, curator_id, dryRun)
                  }

                  Package.withNewSession {
                    pkg_job.groupId = curator_id
                    pkg_job.description = "BulkConfig KBART Source ingest (${pkgInfo.name})".toString()
                    pkg_job.type = dryRun ? RefdataCategory.lookup('Job.Type', 'KBARTSourceIngestDryRun') : RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
                    pkg_job.linkedItem = pkgInfo
                    pkg_job.message("Starting upsert for Package ${pkgInfo.name}".toString())
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
            else if (!source_id) {
              log.debug("No source object created.. skip")
            }
            else {
              log.warn("Unable to reference package!")
            }

            type_results.report << pkg_result
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

  private void setPackageBinaryRefdata(Package obj, String prop, String category, boolean val) {
    if (val == true) {
      obj[prop] = RefdataCategory.lookup(category, "Yes")
    }
    else if (val == false) {
      obj[prop] = RefdataCategory.lookup(category, "No")
    }
    else {
      obj[prop] = RefdataCategory.lookup(category, "Unknown")
    }
  }

  private boolean hasChangedFile(Long pid, Map item) {
    Package.withNewSession {
      boolean result = false
      def deposit_token = java.util.UUID.randomUUID().toString()
      File tmp_file = TSVIngestionService.handleTempFile(deposit_token)
      def file_info = packageSourceUpdateService.fetchKbartFile(tmp_file, new URL(item.package_titlelist))
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
}
