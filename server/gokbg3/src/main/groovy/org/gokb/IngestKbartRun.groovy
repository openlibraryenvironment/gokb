package org.gokb

import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder

import com.k_int.ClassUtils
import com.k_int.ESSearchService

import gokbg3.DateFormatService
import gokbg3.MessageService

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.gorm.transactions.Transactional
import grails.util.Holders
import grails.util.TypeConvertingMap

import groovy.util.logging.Slf4j

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.input.BOMInputStream
import org.apache.commons.lang.RandomStringUtils
import org.gokb.cred.*
import org.gokb.exceptions.*
import org.gokb.GOKbTextUtils
import org.grails.web.json.JSONObject

@Slf4j
class IngestKbartRun {

  static MessageService messageService = Holders.grailsApplication.mainContext.getBean('messageService')
  static PackageService packageService = Holders.grailsApplication.mainContext.getBean('packageService')
  static SpringSecurityService springSecurityService = Holders.grailsApplication.mainContext.getBean('springSecurityService')
  static ComponentUpdateService componentUpdateService = Holders.grailsApplication.mainContext.getBean('componentUpdateService')
  static CleanupService cleanupService = Holders.grailsApplication.mainContext.getBean('cleanupService')
  static TitleLookupService titleLookupService = Holders.grailsApplication.mainContext.getBean('titleLookupService')
  static ReviewRequestService reviewRequestService = Holders.grailsApplication.mainContext.getBean('reviewRequestService')
  static ComponentLookupService componentLookupService = Holders.grailsApplication.mainContext.getBean('componentLookupService')
  static ValidationService validationService = Holders.grailsApplication.mainContext.getBean('validationService')
  static def concurrencyManagerService = Holders.grailsApplication.mainContext.getBean('concurrencyManagerService')
  static TippService tippService = Holders.grailsApplication.mainContext.getBean('tippService')
  static DateFormatService dateFormatService = Holders.grailsApplication.mainContext.getBean('dateFormatService')

  boolean addOnly
  boolean async
  boolean dryRun
  boolean isUpdate
  boolean skipInvalid
  User user
  Map errors = [global: [], tipps: []]
  int removedNum = 0
  def invalidTipps = []
  def matched_tipps = [:]
  def titleIdMap = [:]
  def titleMatchResult = [
    matches: [
      partial: 0,
      full: 0
    ],
    created: 0,
    conflicts: 0,
    noid: 0
  ]
  def titleMatchConflicts = []
  Package pkg
  CuratoryGroup activeGroup
  def pkg_validation
  def priority_list = ['zdb', 'eissn', 'issn', 'isbn', 'doi']
  def job = null
  IdentifierNamespace providerIdentifierNamespace
  Long ingest_systime

  DataFile datafile

  def possible_date_formats = [
    new SimpleDateFormat('yyyy-MM-dd'),
    new SimpleDateFormat('yyyy-MM'),
    new SimpleDateFormat('yyyy')
  ]

  public IngestKbartRun(Package pack,
                        DataFile data_file,
                        IdentifierNamespace titleIdNamespace = null,
                        Boolean is_async = false,
                        Boolean incremental = false,
                        User u = null,
                        CuratoryGroup active_group = null,
                        Boolean dry_run = false,
                        Boolean skip_invalid = false) {
    pkg = pack
    addOnly = incremental
    user = u
    async = is_async
    activeGroup = active_group
    dryRun = dry_run
    datafile = data_file
    providerIdentifierNamespace = titleIdNamespace
    skipInvalid = skip_invalid
  }

  def start(nJob) {
    job = nJob ?: job
    def pid = pkg.id
    log.debug("ingest start")
    def result = [result: 'OK', dryRun: dryRun]
    result.messages = []

    long start_time = System.currentTimeMillis()
    log.debug("Got Datafile ${datafile?.uploadName}")

    if (job && !job.startTime) {
      job.startTime = new Date()
    }

    def ingest_cfg = [
      defaultTypeName: 'org.gokb.cred.JournalInstance',
      identifierMap: ['print_identifier': 'issn', 'online_identifier': 'eissn'],
      defaultMedium: 'Journal',
      providerIdentifierNamespace: (providerIdentifierNamespace?.value ?: null),
      inconsistent_title_id_behavior: 'reject',
      quoteChar: '"',
      discriminatorColumn: 'publication_type',
      discriminatorFunction: null,
      polymorphicRows: [
        'serial':[
          identifierMap: ['print_identifier': 'issn', 'online_identifier': 'eissn'],
          defaultMedium: 'Serial',
          defaultTypeName: 'org.gokb.cred.JournalInstance'
        ],
        'monograph':[
          identifierMap: ['print_identifier': 'pisbn', 'online_identifier': 'isbn'],
          defaultMedium: 'Book',
          defaultTypeName: 'org.gokb.cred.BookInstance'
        ]
      ]
    ]

    try {
      log.debug("Initialise start time")

      ingest_systime = start_time
      def date_pattern_matches = (datafile.uploadName =~ /[\d]{4}-[\d]{2}-[\d]{2}/)
      def ingest_date = date_pattern_matches?.size() > 0 ? date_pattern_matches[0] : LocalDate.now().toString()
      boolean valid_encoding = true

      if (!(datafile.encoding in ['UTF-8', 'US-ASCII'])) {
        log.debug("Illegal charset ${encoding} found..")
        valid_encoding = false
        result.result = 'ERROR'
        result.messageCode = 'kbart.errors.url.charset'
        result.messages.add("File has illegal charset ${encoding}!")
      }

      log.debug("Set progress")
      job?.setProgress(0)

      def file_info = validationService.generateKbartReport(new ByteArrayInputStream(datafile.fileData), providerIdentifierNamespace, false)
      result.report = [numRows: file_info.rows.total, skipped: file_info.rows.skipped, invalid: file_info.rows.error]
      result.validation = file_info

      if (file_info.errors.missingColumns) {
        result.result = 'ERROR'
        result.messages.add("File is missing mandatory columns ${file_info.errors.missingColumns}!")
      }
      else if (file_info.errors.rows) {
        result.result = 'ERROR'
        result.messages.add("There are ${file_info.rows.error} invalid rows (${file_info.rows.warning} with warnings)!")
      }

      def running_jobs = concurrencyManagerService.getComponentJobs(pkg.id)

      if (valid_encoding && (file_info.valid || (skipInvalid && !file_info.errors.missingColumns)) && running_jobs.data?.size() <= 1) {
        CSVReader csv = initReader(datafile)

        String[] header = csv.readNext()

        header = header.collect { it.toLowerCase().trim() }

        Map col_positions = [:]
        int ctr = 0

        header.each {
          col_positions[it] = ctr++
        }

        log.debug("Handling header ${header}")

        int old_tipp_count = 0

        TitleInstancePackagePlatform.withNewSession {
          old_tipp_count = TitleInstancePackagePlatform.executeQuery('select count(*) '+
                                'from TitleInstancePackagePlatform as tipp, Combo as c '+
                                'where c.fromComponent.id=:pkg and c.toComponent=tipp and tipp.status = :sc',
                              [pkg: pkg.id, sc: RefdataCategory.lookup('KBComponent.Status', 'Current')])[0]
        }

        result.report = [numRows: file_info.rows.total, skipped: file_info.rows.skipped, matched: 0, partial: 0, created: 0, retired: 0, reviews: 0, invalid: 0,  previous: old_tipp_count]

        if (old_tipp_count > 0) {
          isUpdate = true
        }

        long startTime = System.currentTimeMillis()

        if (!dryRun) {
          Package.withNewSession {
            def p = Package.get(pid)
            p.listStatus = RefdataCategory.lookup('Package.ListStatus', 'In Progress')
            p.save(flush: true)
            new Combo(fromComponent: p, toComponent: datafile, type: RefdataCategory.lookup('Combo.Type','KBComponent.FileAttachments')).save(flush: true, failOnError: true)
          }
        }

        log.debug("Ingesting ${ingest_cfg.defaultMedium} ${file_info.rows.total + file_info.rows.skipped} rows. Package is ${pkg.id}")

        boolean more = true
        int rownum = 0

        while (more) {
          String[] row_data = csv.readNext()

          if (row_data != null) {
            rownum++

            if (!result.validation.warnings.rows["${rownum}"] || !result.validation.warnings.rows["${rownum}"].shortRow) {
              long rowStartTime = System.currentTimeMillis()

              if (!result.validation.errors.rows["${rownum}"]) {
                def row_kbart_beans = getKbartBeansForRow(col_positions, row_data)
                def row_specific_cfg = getRowSpecificCfg(ingest_cfg, row_kbart_beans)
                log.debug("**Ingesting ${rownum} of ${file_info.rows.total + file_info.rows.skipped} ${row_kbart_beans}")

                if (dryRun) {
                  checkTitleMatchRow(row_kbart_beans, rownum, ingest_cfg)
                }

                def line_result = writeToDB(row_kbart_beans,
                          ingest_date,
                          ingest_systime,
                          ingest_cfg,
                          row_specific_cfg)

                result.report[line_result.status]++

                if (line_result.reviewCreated) {
                  result.report.reviews++
                }
              }
              else {
                log.debug("**Skipped ${rownum} of ${file_info.rows.total + file_info.rows.skipped}")
                result.report.invalid++
              }

              log.debug("ROW ELAPSED : ${System.currentTimeMillis() - rowStartTime}")
            }

            job?.setProgress(rownum, file_info.rows.total + file_info.rows.skipped)

            if (Thread.currentThread().isInterrupted()) {
              result.result = 'CANCELLED'
              break
            }
          }
          else {
            more = false
          }
        }

        if (result.result != 'CANCELLED' && dryRun) {
          result.titleMatch = titleMatchResult
        }

        if (addOnly) {
          log.debug("Incremental -- no expunge")
        }
        else if (isUpdate) {
          log.debug("Expunging old tipps [Tipps belonging to ${pkg.id} last seen prior to ${ingest_date}] - ${pkg.name}")
          if (!dryRun && result.result != 'CANCELLED') {
            try {
              TitleInstancePackagePlatform.withNewSession {
                // Find all tipps in this package which have a lastSeen before the ingest date
                def retire_pars = [
                  pkgid: pkg.id,
                  dt: ingest_systime,
                  sc: RefdataCategory.lookup('KBComponent.Status', 'Current'),
                  sr: RefdataCategory.lookup('KBComponent.Status', 'Retired'),
                  igdt: dateFormatService.parseDate(ingest_date),
                  now: new Date()
                ]

                log.debug("Retiring via pars ${retire_pars}")

                def retired_count = TitleInstancePackagePlatform.executeUpdate('''update TitleInstancePackagePlatform as tipp
                    set tipp.status = :sr, tipp.accessEndDate = :igdt, tipp.lastUpdated = :now
                    where exists (select 1 from Combo as tc where tc.fromComponent.id = :pkgid and tc.toComponent.id = tipp.id)
                    and (tipp.lastSeen is null or tipp.lastSeen < :dt) and tipp.status = :sc''', retire_pars)

                result.report.retired = retired_count
                log.debug("Completed tipp cleanup (${retired_count} retired)")
              }
            }
            catch (Exception e) {
              log.error("Problem retiring TIPPs", e)
              result.result = 'ERROR'
            }
            finally {
              log.debug("Done")
            }
          }
        }

        long processing_elapsed = System.currentTimeMillis() - startTime
        def average_milliseconds_per_row = file_info.rows.total > 0 ? processing_elapsed.intdiv(file_info.rows.total  + file_info.rows.skipped) : 0
        // 3600 seconds in an hour, * 1000ms in a second
        def average_per_hour = average_milliseconds_per_row > 0 ? 3600000.intdiv(average_milliseconds_per_row) : 0

        result.report.timestamp = System.currentTimeMillis()
        result.report.event = (result.result == 'CANCELLED' ? 'ProcessingCancelled' : 'ProcessingComplete')
        result.report.averagePerRow = average_milliseconds_per_row
        result.report.averagePerHour = average_per_hour
        result.report.elapsed = processing_elapsed
        job?.message("Processing Complete : numRows:${file_info.rows.total + file_info.rows.skipped}, avgPerRow:${average_milliseconds_per_row}, avgPerHour:${average_per_hour}")

        if (!dryRun) {
          try {
            Package.withNewSession {
              Package p = Package.get(pid)

              def update_agent = User.findByUsername('IngestAgent')
              // insertBenchmark updateBenchmark
              if ( p.insertBenchmark == null )
                p.insertBenchmark = processing_elapsed

              p.lastUpdateComment = "KBART ingest of file:${datafile.name}[${datafile.id}] completed in ${processing_elapsed}ms, avg per row=${average_milliseconds_per_row}, avg per hour=${average_per_hour}"
              p.lastUpdatedBy = update_agent
              p.updateBenchmark = processing_elapsed
              p.save(flush: true, failOnError: true)
            }

            def matching_job = concurrencyManagerService.createJob { mjob ->
              tippService.matchPackage(pid, mjob)
            }

            RefdataCategory.withNewSession {
              matching_job.description = "Package Title Matching".toString()
              matching_job.type = RefdataCategory.lookup('Job.Type', 'PackageTitleMatch')
              matching_job.linkedItem = [name: pkg.name, type: "Package", id: pkg.id, uuid: pkg.uuid]
              matching_job.message("Starting title match for Package ${pkg.name}".toString())
              matching_job.startOrQueue()
              matching_job.startTime = new Date()
            }

            if (!async) {
              result.matchingJob = matching_job.get()
            }
            else {
              result.matchingJob = matching_job.uuid
            }
          }
          catch (Exception e) {
            log.warn("Problem updating package stats", e)
          }
        }
      }
      else if (running_jobs.data?.size() > 1) {
        result.result = 'ERROR'
        result.messageCode = 'kbart.errors.alreadyRunning'
        result.messages.add('An import job for this package is already in progress!')
      }
    }
    catch (IllegalCharactersException ice) {
      result.result = 'ERROR'
      result.messageCode = 'kbart.errors.replacementChars'

      if (job) {
        job.exception = ice.toString()
      }
    }
    catch (Exception e) {
      if (job) {
        job.exception = e.toString()
      }
      result.result = 'ERROR'
      log.error("Problem", e)
    }

    if (job) {
      job.setProgress(100)
      job.endTime = new Date()

      JobResult.withNewSession {
        def result_object = JobResult.findByUuid(job.uuid)

        if (result.titleMatch) {
          result.titleMatch.rowConflicts = titleMatchConflicts
        }

        if (!result_object) {
          def job_map = [
              uuid        : (job.uuid),
              description : (job.description),
              resultObject: (result as JSON).toString(),
              type        : (job.type),
              statusText  : (result.result),
              ownerId     : (job.ownerId),
              groupId     : (job.groupId),
              startTime   : (job.startTime),
              endTime     : (job.endTime),
              linkedItemId: (job.linkedItem?.id)
          ]

          def jr = new JobResult(job_map).save(flush: true, failOnError: true)
        }
      }
    }

    def elapsed = System.currentTimeMillis() - start_time

    log.debug("Ingest completed after ${elapsed}ms")

    result
  }

  def writeToDB(the_kbart,
               ingest_date,
               ingest_systime,
               ingest_cfg,
               row_specific_config) {

    //simplest method is to assume that everything is new.
    //however the golden rule is to check that something already exists and then
    //re-use it.
    log.debug("TSVINgestionService:writeToDB -- package id is ${pkg.id}")
    def result = [status: null, reviewCreated: false]

    TitleInstancePackagePlatform.withNewSession {
      //first we need a platform:
      def platform = null

      if (the_kbart.title_url != null) {
        log.debug("Extract host from ${the_kbart.title_url}")

        def title_url_host = null
        def title_url_protocol = null

        try {
          def title_url = new URL(the_kbart.title_url)
          log.debug("Parsed title_url : ${title_url}")
          title_url_host = title_url.getHost()
          title_url_protocol = title_url.getProtocol()
        }
        catch (Exception e) {
        }

        if (title_url_host) {
          log.debug("Got platform from title host :: ${title_url_host}")
          platform = handlePlatform(title_url_host, title_url_protocol)
          log.debug("Platform result : ${platform}")
        }
        else {
          log.debug("title_url_host::${title_url_host}")
        }
      }
      else {
        log.debug("No title url")
      }

      if (platform == null) {
        log.debug("Platform is still null - use the default (${pkg.nominalPlatform})")
        platform = pkg.nominalPlatform
      }

      if (platform != null) {

          log.debug("online_identifier ${the_kbart.online_identifier}")

          def identifiers = []

          if (the_kbart.online_identifier && the_kbart.online_identifier.trim())
            identifiers << [type: row_specific_config.identifierMap.online_identifier, value: the_kbart.online_identifier.trim()]

          if (the_kbart.print_identifier && the_kbart.print_identifier.trim())
            identifiers << [type: row_specific_config.identifierMap.print_identifier, value: the_kbart.print_identifier.trim()]

          if (the_kbart.title_id && the_kbart.title_id.trim()) {
            log.debug("title_id ${the_kbart.title_id}")

            if (ingest_cfg.providerIdentifierNamespace) {
              identifiers << [type: ingest_cfg.providerIdentifierNamespace, value: the_kbart.title_id.trim()]
            }
          }

          if (the_kbart.zdb_id && the_kbart.zdb_id.trim()) {
            identifiers << [type: 'zdb', value: the_kbart.zdb_id]
          }

          the_kbart.each { k, v ->
            if (k.startsWith('identifier_')) {
              def ns_val = k.split('_', 2)[1]
              log.debug("Found potential additional namespace ${ns_val}")

              if (IdentifierNamespace.findByValue(ns_val)) {
                identifiers << [type: ns_val, value:v]
              }
              else {
                log.debug("Unknown additional identifier namespace ${ns_val}!")
              }
            }
          }

          if (the_kbart.doi_identifier && the_kbart.doi_identifier.trim() && !identifiers.findAll { it.type == 'doi'}) {
            identifiers << [type: 'doi', value: the_kbart.doi_identifier.trim()]
          }

          def titleClass = TitleInstance.determineTitleClass(the_kbart.publication_type)

          if (titleClass) {
            result = manualUpsertTIPP(the_kbart,
                platform,
                ingest_date,
                ingest_systime,
                identifiers)
          }
          else {
            log.error("Unable to reference title class!")
          }

      } else {
        log.warn("couldn't resolve platform - title not added.")
        result.status = 'invalid'
      }
    }
    result
  }

  Date parseDate(String datestr) {
    def parsed_date = null;
    if ( datestr && ( datestr.length() > 0 ) ) {
      for(Iterator<SimpleDateFormat> i = possible_date_formats.iterator(); ( i.hasNext() && ( parsed_date == null ) ); ) {
        try {
          parsed_date = i.next().clone().parse(datestr.replaceAll('-','/'))
        }
        catch ( Exception e ) {
        }
      }
    }
    parsed_date
  }

  def manualUpsertTIPP(the_kbart,
                       the_platform,
                       ingest_date,
                       ingest_systime,
                       identifiers) {

    log.debug("TSVIngestionService::manualUpsertTIPP with pkg:${pkg}, plat:${the_platform}, date:${ingest_date}")

    assert pkg != null && the_platform != null

    def result = [status: null, reviewCreated: false]
    TitleInstancePackagePlatform tipp = null

    def tipp_map = [
      url: the_kbart.title_url?.trim(),
      coverageStatements: [
        [
          embargo: the_kbart.embargo_info?.trim(),
          coverageDepth: the_kbart.coverage_depth?.trim(),
          coverageNote: the_kbart.coverage_note?.trim(),
          startDate: the_kbart.date_first_issue_online?.trim(),
          startVolume: the_kbart.num_first_vol_online?.trim(),
          startIssue: the_kbart.num_first_issue_online?.trim(),
          endDate: the_kbart.date_last_issue_online?.trim(),
          endVolume: the_kbart.num_last_vol_online?.trim(),
          endIssue: the_kbart.num_last_issue_online?.trim()
        ]
      ],
      importId: the_kbart.title_id?.trim(),
      name: the_kbart.publication_title?.trim(),
      publicationType: the_kbart.publication_type?.trim(),
      parentPublicationTitleId: the_kbart.parent_publication_title_id?.trim(),
      precedingPublicationTitleId: the_kbart.preceding_publication_title_id?.trim(),
      firstAuthor: the_kbart.first_author?.trim(),
      publisherName: the_kbart.publisher_name?.trim(),
      volumeNumber: the_kbart.monograph_volume?.trim(),
      editionStatement: the_kbart.monograph_edition?.trim(),
      dateFirstInPrint: the_kbart.date_monograph_published_print?.trim(),
      dateFirstOnline: the_kbart.date_monograph_published_online?.trim(),
      firstEditor: the_kbart.first_editor?.trim(),
      url: the_kbart.title_url?.trim(),
      subjectArea: the_kbart.subject_area?.trim() ?: (the_kbart.subject?.trim() ?: the_kbart.primary_subject?.trim()),
      series: (the_kbart.monograph_parent_collection_title ?: the_kbart.series?.trim()),
      language: the_kbart.language?.trim(),
      medium: the_kbart.medium?.trim(),
      accessStartDate:the_kbart.access_start_date?.trim() ?: ingest_date,
      accessEndDate: the_kbart.access_end_date?.trim(),
      lastSeen: ingest_systime,
      identifiers: identifiers,
      pkg: [id: pkg.id, uuid: pkg.uuid, name: pkg.name],
      hostPlatform: [id: the_platform.id, uuid: the_platform.uuid, name: the_platform.name],
      paymentType: the_kbart.access_type?.trim()
    ]

    if (isUpdate || !tipp_map.importId) {
      def match_result = tippService.restLookup(tipp_map)

      if (match_result.full_matches.size() > 0) {
        result.status = 'matched'
        tipp = match_result.full_matches[0]
        tipp.refresh()

        if (tipp.accessStartDate) {
          tipp_map.accessStartDate = null
        }

        // update Data
        log.debug("Updated TIPP ${tipp} with URL ${tipp?.url}")

        if (match_result.full_matches.size() > 1) {
          log.debug("multimatch (${match_result.full_matches.size()}) for $tipp")
          def additionalInfo = [otherComponents: []]

          match_result.full_matches.eachWithIndex { ct, idx ->
            if (idx > 0) {
              additionalInfo.otherComponents << [oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.id, uuid: ct.uuid, id: ct.id, name: ct.name]
            }
          }
          result.reviewCreated = true

          // RR für Multimatch generieren
          reviewRequestService.raise(
              tipp,
              "Ambiguous KBART Record Matches",
              "A KBART record has been matched on multiple package titles.",
              user,
              null,
              (additionalInfo as JSON).toString(),
              RefdataCategory.lookup('ReviewRequest.StdDesc', 'Ambiguous Record Matches'),
              componentLookupService.findCuratoryGroupOfInterest(tipp, user, activeGroup)
          )
        }
      }
      else {
        result.status = 'created'

        if (!dryRun) {
          def tipp_fields = [
            pkg: pkg,
            hostPlatform: the_platform,
            url: the_kbart.title_url?.trim(),
            name: the_kbart.publication_title.trim(),
            importId: the_kbart.title_id?.trim()
          ]

          tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_fields)

          log.debug("Created TIPP ${tipp} with URL ${tipp?.url}")

          if (match_result.failed_matches.size() > 0) {
            result.status = 'partial'
            result.reviewCreated = true

            def additionalInfo = [otherComponents: []]

            match_result.failed_matches.each { ct ->
              additionalInfo.otherComponents << [
                oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.item.id,
                uuid: ct.item.uuid,
                id: ct.item.id,
                name: ct.item.name,
                matchResults: ct.matchResults
              ]
            }

            // RR für Multimatch generieren
            reviewRequestService.raise(
                tipp,
                "A KBART record has been matched on an existing package title by some identifiers, but not by other important identifiers.",
                "Check the package titles and merge them if necessary.",
                user,
                null,
                (additionalInfo as JSON).toString(),
                RefdataCategory.lookup('ReviewRequest.StdDesc', 'Import Identifier Mismatch'),
                componentLookupService.findCuratoryGroupOfInterest(tipp, user, activeGroup)
            )
          }
        }
      }

      if (!dryRun) {
        if (!matched_tipps[tipp.id]) {
          matched_tipps[tipp.id] = 1

          if (result.status != 'created' && result.status != 'partial') {
            TIPPCoverageStatement.executeUpdate("delete from TIPPCoverageStatement where owner = ?", [tipp])
            tipp.refresh()
          }
        }
        else {
          matched_tipps[tipp.id]++
        }
      }
    }
    else {
      def jsonIdMap = [:]

      identifiers.each { jsonId ->
        jsonIdMap[jsonId.type] = jsonId.value
      }

      if (titleIdMap[tipp_map.importId]) {
        for (tidm in titleIdMap[tipp_map.importId]) {
          jsonIdMap.each { ns, val ->
            if (tidm.ids[ns] != val) {
              result.status = 'partial'
            }
          }

          if (result.status != 'partial') {
            result.status = 'matched'

            if (!dryRun) {
              tipp = TitleInstancePackagePlatform.findById(tidm.oid)
            }
          }
        }
      }
      else {
        result.status = 'created'
      }

      if (result.status != 'matched') {
        if (!dryRun) {
          def tipp_fields = [
            pkg: pkg,
            hostPlatform: the_platform,
            url: the_kbart.title_url,
            name: the_kbart.publication_title,
            importId: the_kbart.title_id
          ]

          tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_fields)
        }

        if (tipp_map.importId) {
          if (!titleIdMap[tipp_map.importId]) {
            titleIdMap[tipp_map.importId] = []
          }

          titleIdMap[tipp_map.importId] << [
            ids: jsonIdMap,
            oid: (dryRun ? null : tipp.id)
          ]
        }
      }
      else if (tipp.accessStartDate) {
        tipp_map.accessStartDate = null
      }
    }

    if (!dryRun) {
      tipp = tippService.updateTippFields(tipp, tipp_map, user)
      tipp.refresh()

      // log.debug("Values updated, set lastSeen");

      if (ingest_systime) {
        log.debug("Update last seen on tipp ${tipp.id} - set to ${ingest_date} (${tipp.lastSeen} -> ${ingest_systime})")
        tipp.lastSeen = ingest_systime
      }

      // setPrices(tipp, the_kbart)

      // Look through the field list for any tipp.custprop values
      // log.debug("Checking for tipp custprops")

      // addCustprops(tipp, the_kbart, 'tipp.custprops.')
      // addUnmappedCustprops(tipp, the_kbart.unmapped, 'tipp.custprops.')

      log.debug("manualUpsertTIPP returning")
      log.debug("TIPP ${tipp.id} info check: ${tipp.name}, ${tipp.url}")

      if (tipp.validate()) {
        tipp.save(flush: true)
      }
      else {
        log.error("Validation failed!")
        tipp.errors.allErrors.each {
            log.error("${it}")
        }
      }

    }

    result
  }

  def setPrices(tipp, cols) {
    cols.each { name, val ->
      if (name ==~ ~/^listprice_.+/ && val.trim()) {
        def currency = name.split('_')[1]
        def combined_price = "${val.trim()} ${currency}"

        def priceObj = tipp.setPrice('list', combined_price)

        if (!priceObj) {
          log.debug("Unable to create attached list price (${name}: ${val.trim()})!")
        }
      }
    }
  }

  def handlePlatform(host, protocol) {
    def result
    def orig_host = host

    Platform.withNewSession {
      if (host.startsWith("www.")){
        host = host.substring(4)
      }

      def plt_params = ['host': "%" + host + "%", sc: RefdataCategory.lookup('KBComponent.Status', 'Deleted')]
      def platforms = Platform.executeQuery("select p from Platform as p where status != :sc and (p.primaryUrl like :host or p.name = :host)", plt_params, [readonly: false])

      switch (platforms.size()) {
        case 0:
          log.debug("Unable to reference TIPP URL against existing platforms!")
        case 1:
          //found a match
          result = platforms[0]
          log.debug("match platform found: ${result}")
          break
        default:
          log.debug("found multiple platforms when looking for ${host}")
        break
      }
    }

    result
  }

  private CSVReader initReader (the_data) {
    def charset = 'UTF-8'

    final CSVParser parser = new CSVParserBuilder()
    .withSeparator('\t' as char)
    .withIgnoreQuotations(true)
    .build()

    CSVReader csv = new CSVReaderBuilder(
        new InputStreamReader(
            new BOMInputStream(
                new ByteArrayInputStream(the_data.fileData),
                ByteOrderMark.UTF_16LE,
                ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_32LE,
                ByteOrderMark.UTF_32BE,
                ByteOrderMark.UTF_8
            ),
            java.nio.charset.Charset.forName(charset)
        )
    ).withCSVParser(parser)
    .build()

    return csv
  }

  def getKbartBeansForRow(col_positions, row_data) {
    def result = [:]

    for (key in col_positions.keySet()) {
      // log.debug("Checking \"${key}\" - key position is ${col_positions[key]}")
      if (key && key.length() > 0) {
        if ((int)key.toCharArray()[0] == 65279) {
          def corrected_key = key.getAt(1..key.length() - 1)
          result[corrected_key] = row_data[col_positions[key]]
        } else {
          if (col_positions[key] != null && col_positions[key] < row_data.length) {
            result[key] = row_data[col_positions[key]]
          }
          else {
            log.error("Column references value not present in col ${col_positions[key]}!")
          }
        }
      }
    }

    result
  }

  /**
   * Sometimes a row will have a discriminator that tells us to interpret the columns in different ways. for example,
   * KBart publication_type can be Serial or Monograph -- Depeneding on which we might need to do something different like
   * treat the print identifier as an isbn or an issn. This method looks at the config and the values for the row and
   * works out what the right bit of row specific config is. Example config looks like this
   * elsevier:[
   *           quoteChar:'"',
   *           // separator:',',
   *           charset:'UTF-8',
   *           defaultTypeName:'org.gokb.cred.BookInstance',
   *           identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
   *           defaultMedium:'Book',
   *           discriminatorColumn:'publication_type',
   *           polymorphicRows:[
   *             'Serial':[
   *               identifierMap:[ 'print_identifier':'issn', 'online_identifier':'issn' ],
   *               defaultMedium:'Serial',
   *               defaultTypeName:'org.gokb.cred.TitleInstance'
   *              ],
   *             'Monograph':[
   *               identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
   *               defaultMedium:'Book',
   *               defaultTypeName:'org.gokb.cred.BookInstance'
   *             ]
   *           ],
   *           // doDistanceMatch=true, // To enable full string title matching
   *           rules:[
   *             [field: 'publication_title', kbart: 'publication_title'],
   *             [field: 'print_identifier', kbart: 'print_iden..........
   */
  def getRowSpecificCfg(cfg, row) {
    def result = cfg
    log.debug("getRowSpecificCfg(${cfg.polymorphicRows},${cfg.discriminatorColumn},${row[cfg.discriminatorColumn]})")

    if (cfg.polymorphicRows && cfg.discriminatorColumn) {
      if (row[cfg.discriminatorColumn]) {
        def row_specific_cfg = cfg.polymorphicRows[row[cfg.discriminatorColumn].toLowerCase()]

        if (row_specific_cfg) {
          result = row_specific_cfg
        }
      }
    }
    else if (cfg.polymorphicRows && cfg.discriminatorFunction) {
      log.debug("calling discriminatorFunction ${row}")
      def rowtype = cfg.discriminatorFunction.call(row)

      if (rowtype) {
        def row_specific_cfg = cfg.polymorphicRows[rowtype]

        if (row_specific_cfg) {
          result = row_specific_cfg
        }
      }
      log.debug("discriminatorFunction ${rowtype}, rowConfig=${result}")
    }
    result
  }

  def checkTitleMatchRow(the_kbart, rownum, ingest_cfg) {
    def row_specific_cfg = getRowSpecificCfg(ingest_cfg, the_kbart)

    TitleInstance.withNewSession {
      def identifiers = []

      if (the_kbart.online_identifier && the_kbart.online_identifier.trim())
        identifiers << [type: row_specific_cfg.identifierMap.online_identifier, value: the_kbart.online_identifier.trim()]

      if (the_kbart.print_identifier && the_kbart.print_identifier.trim())
        identifiers << [type: row_specific_cfg.identifierMap.print_identifier, value: the_kbart.print_identifier.trim()]

      if (the_kbart.zdb_id && the_kbart.zdb_id.trim()) {
        identifiers << [type: 'zdb', value: the_kbart.zdb_id.trim()]
      }

      if (the_kbart.title_id && the_kbart.title_id.trim()) {
        log.debug("title_id ${the_kbart.title_id}")

        if (ingest_cfg.providerIdentifierNamespace) {
          identifiers << [type: ingest_cfg.providerIdentifierNamespace, value: the_kbart.title_id.trim()]
        }
      }

      if (the_kbart.doi_identifier && the_kbart.doi_identifier.trim() && !identifiers.findAll { it.type == 'doi'}) {
        identifiers << [type: 'doi', value: the_kbart.doi_identifier.trim()]
      }

      log.debug("TitleMatch title:${the_kbart.publication_title} identifiers:${identifiers}")

      if (identifiers.size() > 0) {
        def title_lookup_result = titleLookupService.find(
            the_kbart.publication_title,
            the_kbart.publisher_name,
            identifiers,
            TitleInstance.determineTitleClass(the_kbart.publication_type)
        )

        boolean hasConflicts = false
        boolean partial = false
        def matchConflicts = []

        title_lookup_result.matches.each { trm ->
          if (trm.conflicts.size() > 0) {
            partial = true

            def match = [
              id: trm.object.id,
              name: trm.object.name,
              conflicts: trm.conflicts
            ]
            matchConflicts << match

            if (trm.warnings.contains('duplicate')) {
              hasConflicts = true
            }
          }
        }

        if (matchConflicts) {
          titleMatchConflicts << [row: rownum, matches: matchConflicts]
        }

        if (hasConflicts) {
          titleMatchResult.conflicts++
        }

        if (title_lookup_result.to_create) {
          titleMatchResult.created++

          if (partial) {
            titleMatchResult.matches.partial++
          }
        }
        else if (partial) {
          titleMatchResult.matches.partial++
        }
        else {
          titleMatchResult.matches.full++
        }
      }
      else {
        log.warn("[${the_kbart.publication_title}] No identifiers.")
        titleMatchResult.noid++
      }
    }
  }

  /**
   *  Add mapped custom properties to an object. Extensibility mechanism.
   *  Look through the properties passed for any that start with the given prefix. If any
   *  matches are found, add the remaining property name to obj as custom properties.
   *  Sometimes, a column is mapped into a custprop widget -> tipp.custprops.widget. This
   *  handles that case.
   *  @See KBComponent.additionalProperties
   */
  def addCustprops(obj, props, prefix) {
    boolean changed = false
    props.each { k, v ->
      if (k.toString().startsWith(prefix)) {
        log.debug("Got custprop match : ${k} = ${v}");
        def trimmed_name = m.name.substring(prefix.length())
        obj.appendToAdditionalProperty(trimmed_name, m.value)
        changed = true
      }
    }

    if (changed) {
      obj.save(flush:true, failOnError:true)
    }

    return;
  }

  /**
   *  Add mapped custom properties to an object. Extensibility mechanism.
   *  Look through any unmapped properties that start with the given prefix. If any
   *  matches are found, add the remaining property name to obj as custom properties.
   *  Sometimes, a column is mapped into a custprop widget -> tipp.custprops.widget. This
   *  handles that case.
   *  @See KBComponent.additionalProperties
   */
  def addUnmappedCustprops(obj, unmappedprops, prefix) {
    boolean changed = false
    unmappedprops.each { m ->
      if ( m.name.toString().startsWith(prefix) ) {
        log.debug("Got custprop match : ${m.name} = ${m.value}")
        def trimmed_name = m.name.substring(prefix.length())
        obj.appendToAdditionalProperty(trimmed_name, m.value)
        changed=true
      }
    }

    if (changed) {
      obj.save(flush:true, failOnError:true)
    }

    return
  }
}
