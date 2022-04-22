package org.gokb

import au.com.bytecode.opencsv.CSVReader

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
  static def concurrencyManagerService = Holders.grailsApplication.mainContext.getBean('concurrencyManagerService')
  static TippService tippService = Holders.grailsApplication.mainContext.getBean('tippService')
  static DateFormatService dateFormatService = Holders.grailsApplication.mainContext.getBean('dateFormatService')

  boolean addOnly
  boolean async
  boolean dryRun
  User user
  Map jsonResult = [result: "SUCCESS"]
  Map errors = [global: [], tipps: []]
  int removedNum = 0
  def invalidTipps = []
  def matched_tipps = [:]
  Package pkg
  CuratoryGroup activeGroup
  def pkg_validation
  def priority_list = ['zdb', 'eissn', 'issn', 'isbn', 'doi']
  def job = null
  IdentifierNamespace providerIdentifierNamespace
  Long ingest_systime

  def status_current
  def status_deleted
  def status_retired
  def status_expected
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
                        Boolean dry_run = false) {
    pkg = pack
    addOnly = incremental
    user = u
    async = is_async
    activeGroup = active_group
    dryRun = dry_run
    datafile = data_file
    providerIdentifierNamespace = titleIdNamespace
  }

  def start(nJob) {
    job = nJob ?: job
    log.debug("ingest start")
    def result = [result: 'OK', dryRun: dryRun]
    result.messages = []

    long start_time = System.currentTimeMillis()
    log.debug("Got Datafile ${datafile?.uploadName}")

    status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
    status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    status_retired = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Retired')
    status_expected = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Expected')

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

      log.debug("Set progress")
      job?.setProgress(0)

      def kbart_beans = []
      def badrows = []

      log.debug("Reading datafile")
      //we kind of assume that we need to convert to kbart
      kbart_beans = getKbartBeansFromKBartFile(datafile)
      log.debug("Starting preflight")

      result.preflight = preflight(kbart_beans, ingest_cfg)

      if (result.preflight.passed) {
        log.debug("Passed preflight -- ingest")
        int old_tipp_count = TitleInstancePackagePlatform.executeQuery('select count(*) '+
                                'from TitleInstancePackagePlatform as tipp, Combo as c '+
                                'where c.fromComponent.id=:pkg and c.toComponent=tipp and tipp.status = :sc',
                              [pkg: pkg.id, sc: RefdataCategory.lookup('KBComponent.Status', 'Current')])[0]

        result.report = [matched: 0, partial: 0, created: 0, retired: 0, invalid: 0, previous: old_tipp_count]

        long startTime = System.currentTimeMillis()

        log.debug("Ingesting ${ingest_cfg.defaultMedium} ${kbart_beans.size} rows. Package is ${pkg.id}")
        //now its converted, ingest it into the database.

        for (int x = 0; x < kbart_beans.size; x++) {
          Package.withNewSession {
            log.debug("**Ingesting ${x} of ${kbart_beans.size} ${kbart_beans[x]}")

            def row_specific_cfg = getRowSpecificCfg(ingest_cfg, kbart_beans[x])

            long rowStartTime = System.currentTimeMillis()

            if (validateRow(x, badrows, kbart_beans[x])) {
              def line_result = writeToDB(kbart_beans[x],
                        ingest_date,
                        ingest_systime,
                        ingest_cfg,
                        badrows,
                        row_specific_cfg)

              result.report[line_result]++
            }
            else {
              result.report.invalid++
            }

            log.debug("ROW ELAPSED : ${System.currentTimeMillis() - rowStartTime}")
          }

          job?.setProgress(x, kbart_beans.size())

          if (x % 25 == 0) {
            cleanupService.cleanUpGorm()
          }
        }

        if (addOnly) {
          log.debug("Incremental -- no expunge")
        }
        else {
          log.debug("Expunging old tipps [Tipps belonging to ${pkg.id} last seen prior to ${ingest_date}] - ${pkg.name}")
          if (!dryRun) {
            try {
              // Find all tipps in this package which have a lastSeen before the ingest date
              boolean moreToRetire = true

              while (moreToRetire) {
                def q = TitleInstancePackagePlatform.executeQuery('select tipp '+
                                  'from TitleInstancePackagePlatform as tipp, Combo as c '+
                                  'where c.fromComponent.id=:pkg and c.toComponent=tipp and (tipp.lastSeen is null or tipp.lastSeen < :dt) and tipp.status = :sc',
                                [pkg: pkg.id, dt: ingest_systime, sc: RefdataCategory.lookup('KBComponent.Status', 'Current')])

                q.each { tipp ->
                  log.debug("Retire missing tipp ${tipp.id} - last seen was ${tipp.lastSeen}, ingest date was ${ingest_systime}")
                  result.report.retired++
                  ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(ingest_date), tipp, 'accessEndDate')
                  tipp.retire()
                  tipp.save(failOnError: true, flush: true)
                }

                if (q.size() == 0) {
                  moreToRetire = false
                }
              }
              log.debug("Completed tipp cleanup")
            }
            catch (Exception e) {
              log.error("Problem", e)
              result.result = 'ERROR'
            }
            finally {
              log.debug("Done")
            }
          }
        }

        if (badrows.size() > 0) {
          def msg = "There are ${badrows.size()} bad rows -- write to badfile and report"
          result.badrows = badrows
          result.messages.add(msg)
        }

        long processing_elapsed = System.currentTimeMillis() - startTime
        def average_milliseconds_per_row = kbart_beans.size() > 0 ? processing_elapsed.intdiv(kbart_beans.size()) : 0
        // 3600 seconds in an hour, * 1000ms in a second
        def average_per_hour = average_milliseconds_per_row > 0 ? 3600000.intdiv(average_milliseconds_per_row) : 0

        result.report.timestamp = System.currentTimeMillis()
        result.report.event = 'ProcessingComplete'
        result.report.numRows = kbart_beans.size()
        result.report.averagePerRow = average_milliseconds_per_row
        result.report.averagePerHour = average_per_hour
        result.report.elapsed = processing_elapsed
        job.message("Processing Complete : numRows:${kbart_beans.size()}, avgPerRow:${average_milliseconds_per_row}, avgPerHour:${average_per_hour}")

        if (!dryRun) {
          try {
            def update_agent = User.findByUsername('IngestAgent')
            // insertBenchmark updateBenchmark
            def p = Package.get(pkg.id)

            if ( p.insertBenchmark == null )
              p.insertBenchmark = processing_elapsed
            p.lastUpdateComment = "KBART ingest of file:${datafile.name}[${datafile.id}] completed in ${processing_elapsed}ms, avg per row=${average_milliseconds_per_row}, avg per hour=${average_per_hour}"
            p.lastUpdatedBy = update_agent
            p.updateBenchmark = processing_elapsed
            p.save(flush: true, failOnError: true)

            def matching_job = concurrencyManagerService.createJob { mjob ->
              Package.withNewSession {
                tippService.matchPackage(p, mjob)
                mjob.endTime = new Date()
              }
            }

            matching_job.description = "Package Title Matching".toString()
            matching_job.type = RefdataCategory.lookup('Job.Type', 'PackageTitleMatch')
            matching_job.linkedItem = [name: p.name, type: "Package", id: p.id, uuid: p.uuid]
            matching_job.message("Starting title match for Package ${p.name}".toString())
            matching_job.startOrQueue()
            matching_job.startTime = new Date()

            if (!async) {
              matching_job.get()
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

      if (!result.preflight.passed) {
        result.result = 'ERROR'
      }
    }
    catch (IllegalCharactersException ice) {
      result.result = 'ERROR'
      result.messageCode = 'kbart.errors.replacementChars'
      result.messages.add(ice.toString())
      job.message(ice.toString())
    }
    catch (Exception e) {
      job.message(e.toString())
      result.result = 'ERROR'
      result.messages.add(e.toString())
      log.error("Problem", e)
    }

    job?.setProgress(100)
    job?.endTime = new Date()

    def result_object = JobResult.findByUuid(job?.uuid)

    if (!result_object) {
      def job_map = [
          uuid        : (job?.uuid),
          description : (job?.description),
          resultObject: (result as JSON).toString(),
          type        : (job?.type),
          statusText  : (result.result),
          ownerId     : (job?.ownerId),
          groupId     : (job?.groupId),
          startTime   : (job?.startTime),
          endTime     : (job?.endTime),
          linkedItemId: (job?.linkedItem?.id)
      ]
      new JobResult(job_map).save(flush: true, failOnError: true)
    }

    def elapsed = System.currentTimeMillis() - start_time

    log.debug("Ingest completed after ${elapsed}ms")

    result
  }

  def writeToDB(the_kbart,
               ingest_date,
               ingest_systime,
               ingest_cfg,
               badrows,
               row_specific_config) {

    //simplest method is to assume that everything is new.
    //however the golden rule is to check that something already exists and then
    //re-use it.
    log.debug("TSVINgestionService:writeToDB -- package id is ${pkg.id}")

    //first we need a platform:
    def platform = null
    def result = null

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

        the_kbart.additional_isbns.each { identifier ->
          if (identifier.trim()) {
            identifiers << [type: 'isbn', value:identifier.trim()]
          }
        }

        if (the_kbart.title_id && the_kbart.title_id.trim()) {
          log.debug("title_id ${the_kbart.title_id}")

          if ( ingest_cfg.providerIdentifierNamespace ) {
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

        def titleClass = TitleInstance.determineTitleClass(the_kbart.publication_type)

        if (titleClass && identifiers.size() > 0) {
          result = manualUpsertTIPP(the_kbart,
              platform,
              ingest_date,
              ingest_systime,
              identifiers)
        }
        else {
          log.debug("Skipping row - no identifiers")
          badrows.add([rowdata: the_kbart, message: 'No usable identifiers'])
          result = 'invalid'
        }

    } else {
      log.warn("couldn't resolve platform - title not added.")
      result = 'invalid'
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

    def result = null
    TitleInstancePackagePlatform tipp = null

    def tipp_map = [
      url: the_kbart.title_url ?: '',
      coverageStatements: [
        [
          embargo: the_kbart.embargo_info ?: '',
          coverageDepth: the_kbart.coverage_depth?: '',
          coverageNote: the_kbart.coverage_note ?: '',
          startDate: the_kbart.date_first_issue_online,
          startVolume: the_kbart.num_first_vol_online,
          startIssue: the_kbart.num_first_issue_online,
          endDate: the_kbart.date_last_issue_online,
          endVolume: the_kbart.num_last_vol_online,
          endIssue: the_kbart.num_last_issue_online
        ]
      ],
      importId: the_kbart.title_id,
      name: the_kbart.publication_title,
      publicationType: the_kbart.publication_type,
      parentPublicationTitleId: the_kbart.parent_publication_title_id,
      precedingPublicationTitleId: the_kbart.preceding_publication_title_id,
      firstAuthor: the_kbart.first_author,
      publisherName: the_kbart.publisher_name,
      volumeNumber: the_kbart.monograph_volume,
      editionStatement: the_kbart.monograph_edition,
      dateFirstInPrint: the_kbart.date_monograph_published_print,
      dateFirstOnline: the_kbart.date_monograph_published_online,
      firstEditor: the_kbart.first_editor,
      url: the_kbart.title_url,
      subjectArea: the_kbart.subject_area ?: (the_kbart.subject ?: the_kbart.primary_subject),
      series: the_kbart.series,
      language: the_kbart.language,
      medium: the_kbart.medium,
      accessStartDate:the_kbart.access_start_date ?: ingest_date,
      accessEndDate: the_kbart.access_end_date,
      lastSeen: ingest_systime,
      identifiers: identifiers,
      pkg: [id: pkg.id, uuid: pkg.uuid, name: pkg.name],
      hostPlatform: [id: the_platform.id, uuid: the_platform.uuid, name: the_platform.name]
    ]

    def match_result = tippService.restLookup(tipp_map)

    if (match_result.full_matches.size() > 0) {
      result = 'matched'
      tipp = match_result.full_matches[0]
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
      result = 'created'

      if (!dryRun) {
        def tipp_fields = [
          pkg: pkg,
          hostPlatform: the_platform,
          url: the_kbart.title_url,
          name: the_kbart.publication_title,
          importId: the_kbart.title_id
        ]

        tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_fields)

        log.debug("Created TIPP ${tipp} with URL ${tipp?.url}")

        if (match_result.failed_matches.size() > 0) {
          result = 'partial'

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

        if (result != 'created' && result != 'partial') {
          TIPPCoverageStatement.executeUpdate("delete from TIPPCoverageStatement where owner = ?", [tipp])
          tipp.refresh()
        }
      }
      else {
        matched_tipps[tipp.id]++
      }

      tippService.checkCoverage(tipp, tipp_map, (result == 'created' || result == 'partial'))
      tippService.updateSimpleFields(tipp, tipp_map, true, user)

      // log.debug("Values updated, set lastSeen");

      if (ingest_systime) {
        // log.debug("Update last seen on tipp ${tipp.id} - set to ${ingest_date}")
        tipp.lastSeen = ingest_systime
      }

      // Allow columns like tipp.price, tipp.price.list, tipp.price.perpetual - Call the setPrice(type, value) for each
      setTypedProperties(tipp, the_kbart.unmapped, 'Price',  ~/(tipp)\.(price)(\.(.*))?/, 'currency')

      // Look through the field list for any tipp.custprop values
      log.debug("Checking for tipp custprops")

      addCustprops(tipp, the_kbart, 'tipp.custprops.')
      addUnmappedCustprops(tipp, the_kbart.unmapped, 'tipp.custprops.')

      log.debug("manualUpsertTIPP returning")
      tipp.save(flush: true)
    }

    result
  }

  def setTypedProperties(tipp, props, field, regex, type) {
    log.debug("setTypedProperties(...${field},...)")

    props.each { up ->
      def prop = up.name

      if (prop ==~ regex && up.value.trim()) {
        def propname_groups = prop =~ regex
        def propname = propname_groups[0][2]
        def proptype = propname_groups[0][4]

        def current_value = tipp."get${field}"(proptype)
        def value_from_file = formatValueFromFile(up.value.trim(), type)

        log.debug("setTypedProperties - match regex on ${prop},type=${proptype},value_from_file=${value_from_file} current=${current_value}")

        // If we don't currently have a value OR we have a value which is not the same as the one supplied
        if (current_value == null || !current_value.equals(value_from_file)) {
          log.debug("${current_value} !=  ${value_from_file} so set...")
          tipp."set${field}"(proptype, value_from_file)
        }
      }
      else {
        // log.debug("${prop} does not match regex");
      }
    }
  }

  private String formatValueFromFile(String v, String t) {
    String result = null

    switch (t) {
      case 'currency':
        // "1.24", "1 GBP", "11234.43", "3334", "3334.2", "2.3 USD" -> "1.24", "1.00 GBP", "11234.43", "3334.00", "3334.20", "2.30 USD"
        String[] currency_components = v.split(' ')

        if (currency_components.length == 2) {
          result = String.format('%.2f', Float.parseFloat(currency_components[0])) + ' ' + currency_components[1]
        }
        else {
          result = String.format('%.2f', Float.parseFloat(currency_components[0]))
        }
        break
      default:
        result = v.trim()
    }

    return result
  }

  def handlePlatform(host, protocol) {
    def result
    def orig_host = host

    if (host.startsWith("www.")){
      host = host.substring(4)
    }

    def platforms = Platform.executeQuery("select p from Platform as p where p.primaryUrl like :host or p.name = :host and status != :sc", ['host': "%" + host + "%", sc: RefdataCategory.lookup('KBComponent.Status', 'Deleted')], [readonly: false])

    switch (platforms.size()) {
      case 0:
        log.debug("Unable to reference TIPP URL against existing platforms!")
      case 1:
        //found a match
        result = platforms[0]
        log.debug("match platform found: ${result}")
        break
      default:
        log.error("found multiple platforms when looking for ${host}")
      break
    }

    result
  }

  //note- don't do the additional fields just yet, these will need to be mapped in
  def getKbartBeansFromKBartFile(the_data) {
    log.debug("kbart2 file, so use CSV to Bean") //except that this doesn't always work :(
    def results = []
    def charset = 'UTF-8'

    def csv = new CSVReader(
        new InputStreamReader(
            new org.apache.commons.io.input.BOMInputStream(
                new ByteArrayInputStream(the_data.fileData),
                ByteOrderMark.UTF_16LE,
                ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_32LE,
                ByteOrderMark.UTF_32BE,
                ByteOrderMark.UTF_8
            ),
            java.nio.charset.Charset.forName(charset)
        ),
        '\t' as char,'\0' as char
    )
    //results=ctb.parse(hcnms, csv)
    //quick check that results aren't null...

    Map col_positions = [:]
    String[] header = csv.readNext()
    int ctr = 0

    header.each {
      //col_positions[it]=-1
      col_positions[it] = ctr++
    }

    log.debug("${col_positions}")
    String[] nl = csv.readNext()
    int rownum = 0

    while (nl != null) {
      Map result = [:]
      if (nl.length > 0) {

        for (key in col_positions.keySet()) {

          // log.debug("Checking \"${key}\" - key position is ${col_positions[key]}")

          if (key && key.length() > 0) {
            //so, springer files seem to start with a dodgy character (int) 65279
            if ((int)key.toCharArray()[0] == 65279) {
              def corrected_key = key.getAt(1..key.length() - 1)
              //if ( ( col_positions[key] ) && ( nl.length < col_positions[key] ) ) {
              result[corrected_key] = nl[col_positions[key]]
              //}
            } else {
              //if ( ( col_positions[key] ) && ( nl.length < col_positions[key] ) ) {

                if (col_positions[key] != null && col_positions[key] < nl.length) {
                  if (nl[col_positions[key]].length() > 4092) {
                    throw new RuntimeException("Unexpectedly long value in row ${rownum} -- Probably miscoded quote in line. Correct and resubmit")
                  }
                  else if (nl[col_positions[key]].contains('�')) {
                    throw new IllegalCharactersException("Found UTF-8 replacement char in row ${rownum} -- Probably saved non-UTF-8 file as UTF-8!")
                  }
                  else{
                    result[key] = nl[col_positions[key]]
                  }
                }
                else {
                  log.error("Column references value not present in col ${col_positions[key]} row ${rownum}")
                }
              //}
            }
          }
        }
      }
      else {
        log.warn("Possible malformed last row")
      }

      // log.debug(new KBartRecord(result))
      //this is a cheat cos I don't get why springer files don't work!
      // results<<new KBartRecord(result)
      results.add(result)
      nl = csv.readNext()
      rownum++
    }
    results
  }

  def makeBadFile() {
    def depositToken = java.util.UUID.randomUUID().toString()
    def baseUploadDir = grailsApplication.config.project_dir ?: '.'
    def sub1 = deposit_token.substring(0,2)
    def sub2 = deposit_token.substring(2,4)
    validateUploadDir("${baseUploadDir}")
    validateUploadDir("${baseUploadDir}/${sub1}")
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}")
    def bad_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}"
    log.debug("makeBadFile... ${bad_file_name}")
    def bad_file = new File(bad_file_name)
    bad_file
  }

  private def validateUploadDir(path) {
    File f = new File(path)
    if (!f.exists()) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }

  def validateRow(rownum, badrows, row_data) {
    log.debug("Validate :: ${row_data}")
    def result = true
    def errors = []

    // check the_kbart.date_first_issue_online is present and validates
    if (row_data.date_first_issue_online != null && row_data.date_first_issue_online.trim()) {
      def parsed_start_date = parseDate(row_data.date_first_issue_online)

      if (parsed_start_date == null) {
        reasons.add("Row ${rownum} contains an invalid or unrecognised date format for date_first_issue_online :: ${row_data.date_first_issue_online}")
        result = false
      }
    }

    if (row_data.date_last_issue_online != null && row_data.date_last_issue_online.trim()) {
      def parsed_start_date = parseDate(row_data.date_first_issue_online)

      if (parsed_start_date == null) {
        reasons.add("Row ${rownum} contains an invalid or unrecognised date format for 'date_last_issue_online' :: ${row_data.date_last_issue_online}")
        result = false
      }
    }

    if (row_data.date_monograph_published_online != null && row_data.date_monograph_published_online.trim()) {
      def parsed_start_date = parseDate(row_data.date_monograph_published_online)

      if (parsed_start_date == null) {
        reasons.add("Row ${rownum} contains an invalid or unrecognised date format for 'date_monograph_published_online' :: ${row_data.date_monograph_published_online}")
        result = false
      }
    }

    if (row_data.date_monograph_published_print != null && row_data.date_monograph_published_print.trim()) {
      def parsed_start_date = parseDate(row_data.date_monograph_published_print)

      if (parsed_start_date == null) {
        reasons.add("Row ${rownum} contains an invalid or unrecognised date format for 'date_monograph_published_print' :: ${row_data.date_monograph_published_online}")
        result = false
      }
    }

    if (!row_data.title_id || !row_data.title_id.trim()) {
      reasons.add("Row ${rownum} does not contain a value for 'title_id'")
      result = false
    }

    if (!row_data.publication_title || !row_data.publication_title.trim()) {
      reasons.add("Row ${rownum} does not contain a value for 'publication_title'")
      result = false
    }

    if (!row_data.publication_type || !row_data.publication_type.trim() || !TitleInstance.determineTitleClass(row_data.publication_type)) {
      reasons.add("Row ${rownum} does not contain a valid 'publication_type' :: ${row_data.publication_type}")
      result = false
    }

    if (!row_data.title_url || !row_data.title_url.trim()) {
      reasons.add("Row ${rownum} does not contain a value for 'title_url'")
      result = false
    }

    if (!result) {
      log.error("Recording bad row : ${reasons}")
      badrows.add([rowdata: row_data, message: reasons, row: rownum])
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


  // Preflight works through a file adding and verifying titles and platforms, and posing questions which need to be resolved
  // before the ingest proper. We record parameters used so that after recording any corrections we can re-process the file.
  def preflight(kbart_beans, ingest_cfg) {
    log.debug("preflight")

    def result = [:]

    result.stats = [matches: [partial: 0, full: 0], created: 0, conflicts: 0]
    result.passed = true
    result.probcount = 0

    def preflight_counter = 0

    // Iterate through -- create titles
    kbart_beans.each { the_kbart ->

      def row_specific_cfg = getRowSpecificCfg(ingest_cfg, the_kbart)

      TitleInstance.withNewSession {

        def identifiers = []

        if (the_kbart.online_identifier && the_kbart.online_identifier.trim())
          identifiers << [type: row_specific_cfg.identifierMap.online_identifier, value: the_kbart.online_identifier.trim()]

        if (the_kbart.print_identifier && the_kbart.print_identifier.trim())
          identifiers << [type: row_specific_cfg.identifierMap.print_identifier, value: the_kbart.print_identifier.trim()]

        the_kbart.additional_isbns.each { identifier ->
          if (identifier && identifier.trim())
            identifiers << [type: 'isbn', value: identifier.trim()]
        }

        if (the_kbart.zdb_id && the_kbart.zdb_id.trim()) {
          identifiers << [type: 'zdb', value: the_kbart.zdb_id.trim()]
        }

        if (the_kbart.title_id && the_kbart.title_id.trim()) {
          log.debug("title_id ${the_kbart.title_id}")

          if (ingest_cfg.providerIdentifierNamespace) {
            identifiers << [type: ingest_cfg.providerIdentifierNamespace, value: the_kbart.title_id.trim()]
          }
        }

        log.debug("Preflight [${pkg.name}:${preflight_counter++}] title:${the_kbart.publication_title} identifiers:${identifiers}")

        if (identifiers.size() > 0) {
          def title_lookup_result = titleLookupService.find(
              the_kbart.publication_title,
              the_kbart.publisher_name,
              identifiers,
              TitleInstance.determineTitleClass(the_kbart.publication_type)
          )

          if (title_lookup_result.conflicts) {
            result.stats.conflicts++
            result.probcount++
          }
          else if (title_lookup_result.to_create) {
            result.stats.created++

            if (title_lookup_result.matches?.collect { it.conflicts?.size() > 0 }?.size() > 0) {
              log.debug("New title -- Conflicts: ${title_lookup_result.matches}")
              result.stats.matches.partial++
            }

            if (title_lookup_result.matches.size() > 1) {
              result.stats.conflicts++
            }
          }
          else if (title_lookup_result.matches?.collect { it.conflicts?.size() > 0 }?.size() > 0) {
            log.debug("Partial Match -- Conflicts: ${title_lookup_result.matches}")
            result.stats.matches.partial++
          }
          else {
            result.stats.matches.full++
          }
        }
        else {
          log.warn("${pkg.name}:${preflight_counter++}] No identifiers. Map:${ingest_cfg.identifierMap}, print_identifier:${the_kbart.print_identifier} online_identifier:${the_kbart.online_identifier}")
        }
      }
    }

    log.debug("preflight returning ${result.passed}")
    result
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
