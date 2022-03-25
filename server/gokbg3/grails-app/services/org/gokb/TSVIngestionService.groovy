package org.gokb

import au.com.bytecode.opencsv.CSVReader

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ClassUtils

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.util.TypeConvertingMap

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId

import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.input.BOMInputStream
import org.gokb.cred.*
import org.gokb.exceptions.*
import org.gokb.GOKbTextUtils

@Transactional
class TSVIngestionService {

  def grailsApplication
  def componentLookupService
  def componentUpdateService
  def reviewRequestService
  def titleLookupService
  def ESSearchService
  def tippService
  def refdataCategory
  def sessionFactory

  def possible_date_formats = [
    new SimpleDateFormat('yyyy-MM-dd'), // Default format Owen is pushing ATM.
    new SimpleDateFormat('yyyy-MM'),
    new SimpleDateFormat('yyyy')
  ]

  /**
   * Define package level properties.
   * Currently only defines one type of property - typeValueFunction where the package will provide
   * a setX(Y,V), getX(Y,V) method. In the price example below, a column like pkg.price.list would result
   * in a call to getPrice('list') and if the returned value was different to the input file, call
   * setPrice('list','value'). This method may be arbitrarily complex. In the price example, multiple
   * associated tracking events can happen.
   *
   * Structure of map a regex for matching, a type and a property.
   */
  static def packageProperties = [
    [regex: ~/(pkg)\.(price)(\.(.*))?/, type: 'typeValueFunction', prop: 'Price'],  // Match pkg.price and pkg.price.anything
    [regex: ~/(pkg)\.(descriptionURL)/, type: 'simpleProperty', prop: 'descriptionURL']
  ]

  // Don't update the accessStartDate if we are seeing the tipp again in a file
  // already loaded.
  def tipp_properties_to_ignore_when_updating = ['accessStartDate']

  def updatePackage(Package pkg,
                    Long datafile_id,
                    IdentifierNamespace title_id_ns,
                    boolean source_update,
                    boolean incremental,
                    def request_user,
                    def active_group,
                    boolean dry_run,
                    Job job = null) {
    if (!job) {
      Package.withSession {
        IngestKbartRun myRun = new IngestKbartRun(pkg,
                                                        datafile_id,
                                                        title_id_ns,
                                                        source_update,
                                                        incremental,
                                                        request_user,
                                                        active_group,
                                                        dry_run)
        return myRun.start(job)
      }
    }
    Package.withNewSession {
      IngestKbartRun myRun = new IngestKbartRun(pkg,
                                                      datafile_id,
                                                      title_id_ns,
                                                      source_update,
                                                      incremental,
                                                      request_user,
                                                      active_group,
                                                      dry_run)
      return myRun.start(job)
    }
  }

  //these are now ingestions of profiles.
  def ingest(the_profile_id,
             datafile_id,
             job = null,
             ip_id = null,
             ingest_cfg = null,
             user = null) {

    if (the_profile_id == null) {
      log.error("No datafile ID passed in to ingest")
      return
    }

    def the_profile = IngestionProfile.get(the_profile_id)
    def default_group = CuratoryGroup.findByName('Local')

    if (the_profile == null)      {
      log.error("Unable to datafile for ID ${datafile_id}")
      return
    }

    return ingest2(the_profile.packageType,
                   the_profile.packageName,
                   the_profile.platformUrl,
                   the_profile.source,
                   datafile_id,
                   job,
                   null,
                   the_profile.providerNamespace,
                   ip_id,
                   ingest_cfg,
                   'N',
                   null,
                   user,
                   group)
  }

  def ingest2(packageType,
             packageName,
             platformUrl,
             source,
             datafile_id,
             job = null,
             providerName = null,
             providerIdentifierNamespace = null,
             ip_id = null,
             ingest_cfg = null,
             incremental = null,
             other_params = null,
             user_id = null,
             group_id = null,
             boolean dry_run = false) {

    log.debug("ingest2...")
    def result = [result: 'OK', dryRun: dry_run]
    result.messages = []

    long start_time = System.currentTimeMillis()

    // Read does no dirty checking
    log.debug("Get Datafile ${datafile_id}")
    def datafile = DataFile.read(datafile_id)
    log.debug("Got Datafile ${datafile.uploadName}")
    def src_id = source.id

    def kbart_cfg = grailsApplication.config.kbart2.mappings[packageType?.value.toString()]
    log.debug("Looking up config for ${packageType} ${packageType?.class.name} : ${kbart_cfg ? 'Found' : 'Not Found'}")

    if (packageType.value.equals('kbart2')) {
      log.debug("Processing as kbart2")
    }
    else if (kbart_cfg == null) {
      throw new RuntimeException("Unable to locate config information for package type ${packageType}. Registered types are ${grailsApplication.config.kbart2.mappings.keySet()}")
    }

    if (ingest_cfg == null) {
      ingest_cfg = [
        defaultTypeName: kbart_cfg?.defaultTypeName ?: 'org.gokb.cred.JournalInstance',
        identifierMap: kbart_cfg?.identifierMap ?: ['print_identifier': 'issn', 'online_identifier': 'eissn'],
        defaultMedium: kbart_cfg?.defaultMedium ?: 'Journal',
        providerIdentifierNamespace: providerIdentifierNamespace?.value,
        inconsistent_title_id_behavior: 'reject',
        quoteChar: '"',
        discriminatorColumn: kbart_cfg?.discriminatorColumn ?: 'publication_type',
        discriminatorFunction: kbart_cfg?.discriminatorFunction,
        polymorphicRows: kbart_cfg?.polymorphicRows
      ]

      if (!ingest_cfg.polymorphicRows) {
        ingest_cfg.polymorphicRows = [
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
      }
    }

    try {
      log.debug("Initialise start time")

      def ingest_systime = start_time
      def date_pattern_matches = (datafile.uploadName =~ /[\d]{4}-[\d]{2}-[\d]{2}/)
      def ingest_date = date_pattern_matches?.size() > 0 ? date_pattern_matches[0] : LocalDate.now().toString()

      log.debug("Set progress")
      job?.setProgress(0)

      def kbart_beans = []
      def badrows = []

      log.debug("Reading datafile")
      //we kind of assume that we need to convert to kbart
      if ("${packageType}" != 'kbart2') {
        kbart_beans = convertToKbart(packageType, datafile)
      } else {
        kbart_beans = getKbartBeansFromKBartFile(datafile)
      }

      def the_package = null
      def the_package_id = null
      def author_role_id = null
      def editor_role_id = null

      log.debug("Starting preflight")

      result.preflight = preflight(kbart_beans, ingest_cfg, source, packageName, providerName)

      if (result.preflight.passed) {
        log.debug("Passed preflight -- ingest")
        result.report = [matched: 0, created: 0, retired: 0, invalid: 0]

        Package.withTransaction() {
          the_package = handlePackage(packageName, source, providerName, other_params)
          assert the_package != null
          the_package_id = the_package.id
          def author_role = RefdataCategory.lookupOrCreate(grailsApplication.config.kbart2.personCategory, grailsApplication.config.kbart2.authorRole)
          author_role_id = author_role.id
          def editor_role = RefdataCategory.lookupOrCreate(grailsApplication.config.kbart2.personCategory, grailsApplication.config.kbart2.editorRole)
          editor_role_id = editor_role.id

          if (group_id) {
            def group = CuratoryGroup.get(group_id)
            the_package.addCuratoryGroupIfNotPresent(group.name)
            the_package.save(flush:true, failOnError:true)
          }
        }


        long startTime = System.currentTimeMillis()

        log.debug("Ingesting ${ingest_cfg.defaultMedium} ${kbart_beans.size}(cfg:${packageType?.value.toString()}) rows. Package is ${the_package_id}")
        //now its converted, ingest it into the database.

        for (int x = 0; x < kbart_beans.size; x++) {
          Package.withTransaction {
            def author_role = RefdataValue.get(author_role_id)
            def editor_role = RefdataValue.get(editor_role_id)
            def pkg_src = Source.get(src_id)
            def pkg_obj = Package.get(the_package_id)

            log.debug("**Ingesting ${x} of ${kbart_beans.size} ${kbart_beans[x]}")

            def row_specific_cfg = getRowSpecificCfg(ingest_cfg, kbart_beans[x])

            long rowStartTime = System.currentTimeMillis()

            if (validateRow(x, badrows, kbart_beans[x])) {
              def line_result = writeToDB(kbart_beans[x],
                        platformUrl,
                        pkg_src,
                        ingest_date,
                        ingest_systime,
                        author_role,
                        editor_role,
                        pkg_obj,
                        ingest_cfg,
                        badrows,
                        row_specific_cfg,
                        user_id,
                        group_id,
                        dry_run)

              result.report[line_result]++
            }
            else {
              result.report.invalid++
            }

            log.debug("ROW ELAPSED : ${System.currentTimeMillis() - rowStartTime}")
          }

          job?.setProgress(x, kbart_beans.size())

          if (x % 25 == 0) {
            cleanUpGorm()
          }
        }

        if (incremental == 'Y') {
          log.debug("Incremental -- no expunge")
        }
        else {
          log.debug("Expunging old tipps [Tipps belonging to ${the_package_id} last seen prior to ${ingest_date}] - ${packageName}")
          TitleInstancePackagePlatform.withTransaction {
            try {
              // Find all tipps in this package which have a lastSeen before the ingest date
              def q = TitleInstancePackagePlatform.executeQuery('select tipp '+
                               'from TitleInstancePackagePlatform as tipp, Combo as c '+
                               'where c.fromComponent.id=:pkg and c.toComponent=tipp and tipp.lastSeen < :dt and tipp.accessEndDate is null and tipp.status = :sc',
                              [pkg: the_package_id, dt: ingest_systime, sc: RefdataCategory.lookup('KBComponent.Status', 'Current')])

              q.each { tipp ->
                result.report.retired++
                log.debug("Soft delete missing tipp ${tipp.id} - last seen was ${tipp.lastSeen}, ingest date was ${ingest_systime}")

                if (!dry_run) {
                  ClassUtils.setDateIfPresent(GOKbTextUtils.completeDateString(ingest_date), tipp, 'accessEndDate')
                  tipp.retire()
                  tipp.save(failOnError: true, flush: true)
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
        result.messages.add("Processing Complete : numRows:${kbart_beans.size()}, avgPerRow:${average_milliseconds_per_row}, avgPerHour:${average_per_hour}")
        job.message("Processing Complete : numRows:${kbart_beans.size()}, avgPerRow:${average_milliseconds_per_row}, avgPerHour:${average_per_hour}")

        if (!dry_run) {
          Package.withTransaction {
            try {
              def update_agent = User.findByUsername('IngestAgent')
              // insertBenchmark updateBenchmark
              def p = Package.lock(the_package_id)

              if ( p.insertBenchmark == null )
                p.insertBenchmark = processing_elapsed
              p.lastUpdateComment = "KBART ingest of file:${datafile.name}[${datafile.id}] completed in ${processing_elapsed}ms, avg per row=${average_milliseconds_per_row}, avg per hour=${average_per_hour}"
              p.lastUpdatedBy = update_agent
              p.updateBenchmark = processing_elapsed
              p.save(flush: true, failOnError: true)
            }
            catch (Exception e) {
              log.warn("Problem updating package stats", e)
            }
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
    job.endTime = new Date()

    JobResult.withTransaction {
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
    }

    def elapsed = System.currentTimeMillis() - start_time

    log.debug("Ingest completed after ${elapsed}ms")

    result
  }

  //this method does a lot of checking, and then tries to save the title to the DB.
  def writeToDB(the_kbart,
                platform_url,
                source,
                ingest_date,
                ingest_systime,
                author_role,
                editor_role,
                the_package,
                ingest_cfg,
                badrows,
                row_specific_config,
                user_id,
                group_id,
                dry_run) {

    //simplest method is to assume that everything is new.
    //however the golden rule is to check that something already exists and then
    //re-use it.
    log.debug("TSVINgestionService:writeToDB -- package id is ${the_package.id}")

    //first we need a platform:
    def platform = null // handlePlatform(platform_url.host, source)
    def result = null

    log.debug("default platform via default platform URL ${platform_url}, ${platform_url?.class?.name} ${platform_url} title_url:${the_kbart.title_url}")

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
        platform = handlePlatform(title_url_host, title_url_protocol, source)
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
      log.debug("Platform is still null - use the default")
      platform = handlePlatform(platform_url.host, source)
    }

    assert the_package != null

    if (platform != null) {

        log.debug("${the_kbart.online_identifier}")

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
          result = manualUpsertTIPP(source,
              the_kbart,
              the_package,
              platform,
              ingest_date,
              ingest_systime,
              identifiers,
              user_id,
              group_id,
              dry_run
          )
        }
        else {
          log.debug("Skipping row - no identifiers")
          badrows.add([rowdata: the_kbart, message: 'No usable identifiers'])
          result = 'invalid'
        }

    } else {
      log.warn("couldn't resolve platform - title not added.")
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

  def manualUpsertTIPP(the_source,
                       the_kbart,
                       the_package,
                       the_platform,
                       ingest_date,
                       ingest_systime,
                       identifiers,
                       user_id,
                       group_id,
                       dry_run) {

    log.debug("TSVIngestionService::manualUpsertTIPP with pkg:${the_package}, plat:${the_platform}, date:${ingest_date}")

    assert the_package != null && the_platform != null

    def result = null
    def user = User.get(user_id)
    def group = group_id ? CuratoryGroup.get(group_id) : null
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
      pkg: [id: the_package.id, uuid: the_package.uuid, name: the_package.name],
      hostPlatform: [id: the_platform.id, uuid: the_platform.uuid, name: the_platform.name]
    ]

    def match_result = tippService.restLookup(tipp_map)


    if (match_results.full_matches.size() > 0) {
      result = 'matched'
      tipp = full_matches[0]
      // update Data
      log.debug("Updated TIPP ${tipp} with URL ${tipp?.url}")

      if (full_matches.size() > 1) {
        log.debug("multimatch (${full_matches.size()}) for $tipp")
        def additionalInfo = [otherComponents: []]

        full_matches.eachWithIndex { ct, idx ->
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
            group ?: componentLookupService.findCuratoryGroupOfInterest(tipp, user)
        )
      }
    }
    else if (match_result.partial_matches.size() > 0) {
      def best_matches = []

      for (int i = 0; i < priority_list.size(); i++) {
        if (match_result.partial_matches[i]?.size() > 0) {
          best_matches = match_result.partial_matches[i]
          break
        }
      }
      result = 'matched'
      tipp = best_matches[0].item
      // update Data
      tippService.restUpdate(tipp, tippJson)
      log.debug("Updated TIPP ${tipp} with URL ${tipp?.url}")

      if (best_matches.size() > 1) {
        log.debug("multiple (${best_matches.size()}) partial matches for $tipp")
        def additionalInfo = [otherComponents: [], matches: [:], mismatches: [:]]

        best_matches[0].matchResults.each {
          if (it.match == 'OK') {
            additionalInfo.matches[it.namespace] = it.value
          }
          else if (it.match == 'FAIL') {
            additionalInfo.mismatches[it.namespace] = it.value
          }
        }

        if (tipp_map.importId) {
          additionalInfo.matches['title_id'] = tipp_map.importId
        }

        additionalInfo.vars = [additionalInfo.matches, additionalInfo.mismatches]
        additionalInfo.matchResults = best_matches[0].matchResults

        best_matches.each { ct, idx ->
          if (idx > 0) {
            additionalInfo.otherComponents << [oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.item.id, uuid: ct.item.uuid, id: ct.item.id, name: ct.item.name, matchResults: ct.matchResults]
          }
        }

        // RR für Multimatch generieren
        reviewRequestService.raise(
            tipp,
            "A KBART record has been matched on an existing package title by some identifiers ({0}), but not by other important identifiers ({1}).",
            "Check the package titles and merge them if necessary.",
            user,
            null,
            (additionalInfo as JSON).toString(),
            RefdataCategory.lookup('ReviewRequest.StdDesc', 'Import Identifier Mismatch'),
            componentLookupService.findCuratoryGroupOfInterest(tipp, user)
        )
      }
    }
    else {
      result = 'created'

      if (!dry_run) {
        def tipp_fields = [
          pkg: the_package,
          hostPlatform: the_platform,
          url: the_kbart.title_url,
          name: the_kbart.publication_title,
          importId: the_kbart.title_id
        ]

        tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_fields)

        log.debug("Created TIPP ${tipp} with URL ${tipp?.url}, needs review ..")

        def additionalInfo = [otherComponents: []]

        match_result.failed_matches.each { ct ->
          additionalInfo.otherComponents << [oid: 'org.gokb.cred.TitleInstancePackagePlatform:' + ct.item.id, uuid: ct.item.uuid, id: ct.item.id, name: ct.item.name, matchResults: ct.matchResults]
        }

        // RR für Multimatch generieren
        reviewRequestService.raise(
            tipp,
            "A KBART record has been matched on an existing package title by some identifiers ({0}), but not by other important identifiers ({1}).",
            "Check the package titles and merge them if necessary.",
            user,
            null,
            (additionalInfo as JSON).toString(),
            RefdataCategory.lookup('ReviewRequest.StdDesc', 'Import Identifier Mismatch'),
            componentLookupService.findCuratoryGroupOfInterest(tipp, user)
        )
      }
    }

    if (!dry_run) {
      tippService.updateCoverage(tipp, tipp_values)
      tippService.updateSimpleFields(tipp, tipp_values, user)

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

      // Match title
      tippService.matchTitle(tipp, group)

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

  //this is a lot more complex than this for journals. (which uses refine)
  //theres no notion in here of retiring packages for example.
  //for this v1, I've made this very simple - probably too simple.
  def handlePackage(packageName, source, providerName, other_params) {
    def result
    def norm_pkg_name = KBComponent.generateNormname(packageName)
    log.debug("Attempt package match by normalised name: ${norm_pkg_name}")
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def packages = Package.executeQuery("select p from Package as p where p.normname=? and p.status != ?", [norm_pkg_name, status_deleted], [readonly: false])

    switch (packages.size()) {
      case 0:
        //no match. create a new package!
        log.debug("Create new package(${packageName},${norm_pkg_name})")

        def newpkgid = null

        def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
        def newpkg = new Package(
            name: packageName,
            normname: norm_pkg_name,
            source: source,
            status: status_current,
            description: other_params?.description
        )

        if (newpkg.save(flush: true, failOnError: true)) {
          newpkgid = newpkg.id

          if (providerName && providerName.length() > 0) {
            def norm_provider_name = KBComponent.generateNormname(providerName)
            def provider = null;
            def providers = org.gokb.cred.Org.findAllByNormname(norm_provider_name)

            if (providers.size() == 0)
              provider = new Org(name: providerName, normname: norm_provider_name).save(flush: true, failOnError: true)
            else if (providers.size() == 1)
              provider = providers[0]
            else
              log.error("Multiple orgs with name ${providerName}/${norm_provider_name} -- unable to set package provider")

            newpkg.provider = provider
            newpkg.save()
          }
        } else {
          for (error in result.errors) {
            log.error(error)
          }
        }


        log.debug("Created new package : ${newpkgid} in current session")
        result = Package.get(newpkgid)
        break;
      case 1:
        //found a match
        result=packages[0]
        log.debug("match package found: ${result}")
        // See if any properties have changed.
        if (other_params?.description && !result.description == other_params.description) {
          result.description = other_params.description
          result.save(flush: true, failOnError: true)
        }
        break
      default:
        log.error("found multiple packages when looking for ${packageName}")
        break
    }

    // The request can now have additional package level properties that we need to process.
    // other_params can contain 'pkg.' properties.
    handlePackageProperties(result, other_params)

    log.debug("handlePackage returns ${result}")
    result
  }

  def handlePackageProperties(pkg, props) {
    def package_changed = false

    packageProperties.each { pp ->
      // consider See if pp.regex matches any of the properties
      props?.keySet().grep(pp.regex).each { prop ->
        log.debug("Property ${prop} matched config ${pp}")

        switch (pp.type) {
          case 'typeValueFunction':
            // The property has a subtype eg price.list which should be mapped in a special way
            def propname_groups = prop =~ pp.regex
            def propname = propname_groups[0][2]
            def proptype = propname_groups[0][4]
            log.debug("Call getter object.${propname}(${proptype}) - value is ${props[prop]}")
            // If the value returned by the getter is not the same as the value we have, update
            def current_value = pkg."get${pp.prop}"(proptype)
            log.debug("current_value of ${prop} = ${current_value}")

            // If we don't currently have a value OR we have a value which is not the same as the one supplied
            if ((current_value == null && props[prop]?.trim()) || !current_value.equals(props[prop])) {
              log.debug("${current_value} != ${props[prop]} so set ${pp.prop}")
              pkg."set${pp.prop}"(proptype, props[prop].trim())
              package_changed = true
            }

            break
          case 'simpleProperty':
            // A simple scalar property
            pkg[pp.prop] = props[prop]
            break
          default:
            log.warn("Unhandled package property type ${pp.type} : ${pp}")
            break
        }
      }
    }

    if (package_changed) {
      pkg.save(flush: true, failOnError: true)
    }
  }

  def handlePlatform(host, protocol, the_source) {

    def result;
    // def platforms=Platform.findAllByPrimaryUrl(host);

    def orig_host = host

    if (host.startsWith("www.")){
      host = host.substring(4)
    }

    def platforms = Platform.executeQuery("select p from Platform as p where p.primaryUrl like :host", ['host': "%" + host + "%"], [readonly: false])

    switch (platforms.size()) {
      case 0:
        //no match. create a new platform!
        log.debug("Create new platform ${host}, ${host}")
        def newUrl = protocol + "://" + orig_host
        result = new Platform(name: host, primaryUrl: newUrl)

        // log.debug("Validate new platform");
        // result.validate();

        if (result) {
          if (result.save(flush:true, failOnError:true)) {
            // log.debug("saved new platform: ${result}")
          } else {
            // log.error("problem creating platform");
            for (error in result.errors) {
              log.error(error)
            }
          }
        }
        else {
          result.errors.allErrors.each {
            log.error("Problem creating platform : ${e}")
          }
          throw new RuntimeException('Error creating new platform')
        }
        break;
      case 1:
        //found a match
        result = platforms[0]
        log.debug("match platform found: ${result}")
        break
      default:
        log.error("found multiple platforms when looking for ${host}")
      break
    }

    assert result != null

    // log.debug("handlePlatform returning ${result}");
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

  def convertToKbart(packageType, data_file) {
    def results = []
    log.debug("in convert to Kbart2")
    log.debug("file package type is ${packageType}")

    //need to know the file type, then we need to create a new data structure for it
    //in the config, need to map the fields from the formats we support into kbart.

    def kbart_cfg = grailsApplication.config.kbart2.mappings."${packageType}"

    //can you read a tsv file?
    def charset = 'ISO-8859-1' // 'UTF-8'
    if (kbart_cfg == null) {
      throw new Exception("couldn't find config for ${packageType}")
    }
    else {
      log.debug("Got config ${kbart_cfg}")
    }

    BOMInputStream b = new BOMInputStream(
        new ByteArrayInputStream(data_file.fileData),
        ByteOrderMark.UTF_16LE,
        ByteOrderMark.UTF_16BE,
        ByteOrderMark.UTF_32LE,
        ByteOrderMark.UTF_32BE,
        ByteOrderMark.UTF_8
    )

    def ingest_charset = kbart_cfg.charset ?: 'ISO-8859-1'

    if (b.hasBOM() == false) {
      // No BOM found
    } else if (b.hasBOM(ByteOrderMark.UTF_16LE)) {
      // has a UTF-16LE BOM
      ingest_charset = 'UTF-16LE'
    } else if (b.hasBOM(ByteOrderMark.UTF_16BE)) {
      // has a UTF-16BE BOM
      ingest_charset = 'UTF-16BE'
    }

    log.debug("Convert to kbart2 using charset ${ingest_charset}")

    CSVReader csv = new CSVReader(
        new InputStreamReader(b, java.nio.charset.Charset.forName(ingest_charset)),
        (kbart_cfg.separator?:'\t') as char,
        (kbart_cfg.quoteChar?:'\0') as char
    )

    def fileRules = kbart_cfg.rules
    Map col_positions = [:]
    fileRules.each { fileRule ->
      col_positions[fileRule.field] = -1
    }
    String [] header = csv.readNext()

    int ctr = 0

    if (header == null || header.size() ==  0) {
      log.error("No header")
      results.add([message: "No header"])
      return results
    }


    log.debug("Processing column headers... count ${header?.length} items")
    header.each {
      log.debug("Column \"${ctr}\" == ${it} (${it.class.name})")
      col_positions [it.toString().trim()] = ctr++
    }

    def mapped_cols = [] as ArrayList
    def unmapped_cols = 0..(header.size()) as ArrayList

    log.debug("Col positions : ${col_positions}")
    log.debug("Before Mapped columns: ${mapped_cols}")
    log.debug("Before UnMapped columns: ${unmapped_cols}")


    fileRules.each { fileRule ->
      if (col_positions[fileRule.field] >= 0) {
        // Column is mapped
        unmapped_cols.remove(col_positions[fileRule.field] as Object)
        mapped_cols.add(col_positions[fileRule.field] as Object)
      }
      else {
        log.debug("Mapping contains a definition for ${fileRule.field} but unable to find a column with that name in file headings : ${header}")
      }
    }

    log.debug("After Mapped columns: ${mapped_cols}")
    log.debug("After UnMapped columns: ${unmapped_cols}")

    String [] nl = csv.readNext()

    long row_counter = 0

    while (nl != null) {

      log.debug("** Process row:${row_counter++} ${nl}")

      // KBartRecord result = new KBartRecord()
      def result = [:]
      result.unmapped = []

      fileRules.each { fileRule ->

        boolean done = false
        if (nl.length > col_positions[fileRule.field]) {
          String data = nl[col_positions[fileRule.field]]

          if (col_positions[fileRule.field] >= 0) {
            // log.debug("field : ${fileRule.field} ${col_positions[fileRule.field]} ${data}")
            if (fileRule.separator != null && data.indexOf(fileRule.separator) > -1) {
              def parts = data.split(fileRule.separator)
              data = parts[0]

              if (parts.size() > 1 && fileRule.additional != null) {
                for (int x = 1; x < parts.size(); x++) {
                  result[fileRule.additional] << parts[x]
                }
                done=true
              }
            }

            if (fileRule.additional != null && !done) {
              if ( data ) {
                if ( result[fileRule.additional] == null )
                  result[fileRule.additional] = []

                result[fileRule.additional] << data
              }
            } else {
              result[fileRule.kbart] = data
            }
          }
        }
        else {
          log.warn("Missing column[${col_positions[fileRule.field]}]-${fileRule.field} in ingest file at line ${row_counter}")
        }
      }


      log.debug("Processing ${unmapped_cols.size()} unmapped columns")
      unmapped_cols.each { unmapped_col_idx ->
        if (unmapped_col_idx < nl.size() && unmapped_col_idx < header.size()) {
          log.debug("Setting unmapped column idx ${unmapped_col_idx} ${header[unmapped_col_idx]} to ${nl[unmapped_col_idx]}")
          // result.unmapped.add([header[unmapped_col_idx], nl[unmapped_col_idx]])
          result.unmapped.add([
              name: header[unmapped_col_idx],
              value: nl[unmapped_col_idx],
              index: unmapped_col_idx
          ]);
        }
      }

      log.debug("${result}")
      results << result
      nl = csv.readNext()
    }

    log.debug("\n\n Convert to KBart completed cleanly")
    results
  }

  def cleanUpGorm() {
    // log.debug("Clean up GORM");

    // Get the current session.
    def session = sessionFactory.currentSession

    // flush and clear the session.
    session.flush()
    session.clear()
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
  def preflight(kbart_beans,
                ingest_cfg,
                source,
                packageName,
                providerName) {
    log.debug("preflight")

    def result = [:]

    result.stats = [matches: [partial: 0, full: 0], created: 0, conflicts: 0]
    result.passed = true
    result.probcount = 0
    result.packageName = packageName
    result.providerName = providerName
    result.sourceName = source?.name
    result.sourceId = source?.id

    def preflight_counter = 0

    // Iterate through -- create titles
    kbart_beans.each { the_kbart ->

      def row_specific_cfg = getRowSpecificCfg(ingest_cfg, the_kbart)

      TitleInstance.withNewTransaction {

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

        log.debug("Preflight [${packageName}:${preflight_counter++}] title:${the_kbart.publication_title} identifiers:${identifiers}")

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
              result.stats.matches.partial++
            }

            if (title_lookup_result.matches.size() > 1) {
              result.stats.conflicts++
            }
          }
          else if (title_lookup_result.matches?.collect { it.conflicts?.size() > 0 }?.size() > 0) {
            result.stats.matches.partial++
          }
          else {
            result.stats.matches.full++
          }
        }
        else {
          log.warn("${packageName}:${preflight_counter++}] No identifiers. Map:${ingest_cfg.identifierMap}, print_identifier:${the_kbart.print_identifier} online_identifier:${the_kbart.online_identifier}")
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

    return;
  }
}
