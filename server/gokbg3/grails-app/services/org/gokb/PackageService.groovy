package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ClassUtils
import grails.gorm.transactions.Transactional

import grails.io.IOUtils
import groovy.util.logging.Slf4j
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder
import groovyx.net.http.*
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.RandomStringUtils
import org.gokb.cred.*
import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.Session
import org.hibernate.type.StandardBasicTypes
import org.mozilla.universalchardet.UniversalDetector
import org.springframework.util.FileCopyUtils

import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static groovyx.net.http.Method.GET
import static grails.async.Promises.*

@Slf4j
class PackageService {

  /*
  public static String missingTIPs = '''
    select distinct title, platform
    from TitleInstancePackagePlatform as tipp,
         Combo as title_combo,
         TitleInstance as title,
         Combo as platform_combo,
         Platform as plaform
     where title_combo.fromComponent=tipp
       and title_combo.toComponent=title
       and title_combo.type.value='Title'
       and platform_combo.fromComponent=tipp
       and platform_combo.toComponent=platform
       and platform_combo.type.value='Platform'
       and not exists (
             select tip
             from TitleInstancePlatform as tip,
                  Combo as tip_title_combo,
                  Combo as tip_platform_combo
             where tip_title_combo.fromComponent=tip
               and tip_title_combo.toComponent=title
               and tip_platform_combo.fromComponent=tip
               and tip_platform_combo.toComponent=platform
           )
'''
  */

  def sessionFactory
  def genericOIDService
  def restMappingService
  def componentLookupService
  def TSVIngestionService
  def grailsApplication
  def messageService
  def concurrencyManagerService
  def dateFormatService

  private static final String[] KBART_FIELDS = ['publication_title',
     'print_identifier',
     'online_identifier',
     'date_first_issue_online',
     'num_first_vol_online',
     'num_first_issue_online',
     'date_last_issue_online',
     'num_last_vol_online',
     'num_last_issue_online',
     'title_url',
     'first_author',
     'title_id',
     'embargo_info',
     'coverage_depth',
     'coverage_notes',
     'publisher_name',
     'preceding_publication_title_id',
     'date_monograph_published_print',
     'date_monograph_published_online',
     'monograph_volume',
     'monograph_edition',
     'first_editor',
     'parent_publication_title_id',
     'publication_type',
     'access_type',
     'zdb_id',
     'gokb_tipp_uid',
     'gokb_title_uid']

  public static boolean activeCaching = false
  public static final enum ExportType {
    KBART_TIPP, KBART_TITLE, TSV
  }

  /**
   * @return The scope value to be used by "Master Packages"
   */
  private RefdataValue getMasterScope() {
    // The Scope.
    RefdataCategory.lookupOrCreate("Package.Scope", "GOKb Master")
  }

  /**
   * Lookup or create a package based on the supplied package name.
   * Incremental will edit an existing package. If it's false the package and it's
   * TIPPs will have their status set to retired as well and a new package returned.
   */
  def findCorrectPackage(Map<String, Boolean> retired_packages, String package_name, boolean incremental) {

    log.debug("Trying to find a package for ${!incremental ? 'none-incremental' : 'incremental'} update using ${package_name}.")

    // Package.
    Package pkg = componentLookupService.lookupComponent(package_name)

    // If we don't have a package then we need to create one and the incremental flag
    // becomes irrelevant.
    if (!pkg) {

      log.error("No package found for package string supplied via refine. This should not happen as all packages should be looked up.")

    }
    else {

      // If this is a new package then we should retire the current one.
      if (!incremental) {

        if (!retired_packages.get(package_name)) {

          // Retire each TIPP
          pkg.getTipps().each { def tipp ->

            // Retire
            tipp.retire()
            log.debug("TIPP ${tipp.id} retired.")
          }

          // Then retire the package.
          pkg.retire()

          // Create a new package with the IDs
          Set<Identifier> pkIds = pkg.ids.findAll { Identifier the_id ->
            the_id?.getNamespace()?.getValue()?.equalsIgnoreCase('gokb-pkgid')
          }

          // Save the old one.
          if (pkg.save(failOnError: true)) {
            log.debug("Retired and saved package ${pkg.id}.")
          }

          // Get the original name of the package so we can preserve it when recreating.
          String original_name = pkg.name

          // New package.
          pkg = new Package(
              name: original_name
          )

          // Add all the ids.
          pkg.ids.addAll(pkIds)

          // Add to the map.
          retired_packages.put(package_name, true)
        }
      }
    }

    // Save the Package.
    pkg.save(failOnError: true)

    pkg
  }

  /**
   * Method to update all Master list type packages.
   */
  def synchronized updateAllMasters(delta = true) {

    // Create the criteria.
    getAllProviders().each { Org pr ->
      Org.withNewSession({ long prov_id, Session sess ->
        updateMasterFor(pr.id, delta)
      }.curry(pr.id))
    }
    return new Date();
  }

  private def cleanUpGorm() {
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }

  /**
   * Method to create or update a Package containing a list of all titles
   * provided by the supplied Org.
   */
  def updateMasterFor(long provider_id, delta = true) {

    // Read in a provider.
    Org provider = Org.get(provider_id)

    if (provider) {
      log.debug("Update or create master list for ${provider.name}")

      // Get the current master Package for this provider.
      ComboCriteria c = ComboCriteria.createFor(Package.createCriteria())
      Package master = c.get {
        and {
          c.add(
              "scope",
              "eq",
              getMasterScope())
          c.add(
              "provider",
              "eq",
              provider)
        }
      }

      // Update or create?
      if (master) {

        // Update...
        log.debug("Found package ${master.id} for ${provider.name}")

        delta = delta ? master.lastUpdated : false
      }
      else {

        // Set delta to false...
        delta = false

        master = new Package()

        // Need to pass the system_save parameter to flag as systemComponent.
        master.save(failOnError: true)

        // Create new...
        log.debug("Created Master Package ${master.id} for ${provider.name}.")
      }

      master.setName("${provider.name}: Master List")
      master.setScope(getMasterScope())
      master.setProvider(provider)
      master.setSystemComponent(true)

      // Save.
      master.save(failOnError: true)
      provider.save(failOnError: true, flush: true)

      log.debug("Saved Master package ${master.id}")

      // Now query for all packages for the modified since the delta.
      c = ComboCriteria.createFor(Package.createCriteria())
      Set<Package> pkgs = c.list {
        c.and {
          c.add(
              "id",
              "ne",
              master.id)

          c.add(
              "provider.id",
              "eq",
              provider_id)

          if (delta) {
            c.add(
                "lastUpdated",
                "gt",
                delta)
          }
        }
      } as Set

      log.debug("${pkgs.size() ?: 'No'} packages have been updated since the last time this master was updated.")


      for (Package pkg in pkgs) {

        // We should now have a definitive list of tipps that have been changed since the last update.

        // Go through the tipps in chunks.
        //def tipps = pkg.tipps.collect { it.id }

        def tipps = TitleInstancePackagePlatform.executeQuery('select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp', [pkg]);

        log.debug("Query returns ${tipps.size()} tipps");

        TitleInstancePackagePlatform.withNewSession {

          int counter = 1

          for (def t in tipps) {

            TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(t)

            // Do we need to update this tipp.
            if (!delta || (delta && tipp.lastUpdated > delta)) {
              TitleInstancePackagePlatform mt = setOrUpdateMasterTippFor(tipp.id, master.id)
            }
            else {
              log.debug("TIPP ${tipp.id} has not been updated since last run. Skipping.")
            }
            log.debug("TIPP ${counter} of ${tipps.size()} examined.")
            counter++
            tipp.discard();
          }
        }
      }
      log.debug("Finished updating master package ${master.id}")
    }
  }

  /**
   * @param tipp the tipp to base the master tipp on.
   * @param master the master package
   * @return the master tipp
   */
  public TitleInstancePackagePlatform setOrUpdateMasterTippFor(long tipp_id, long master_id) {

    Package master = Package.get(master_id)
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

    // Check the tipp isn't already a master.
    Package pkg = tipp.pkg
    if (pkg.status == getMasterScope()) {
      // Throw an exception.
      log.debug("getMasterTipp called for TIPP {tipp.id} that is already a master. Returning supplied tipp.")
      return tipp
    }

    // Master TIPP
    TitleInstancePackagePlatform master_tipp = tipp.masterTipp

    if (!master_tipp) {

      log.debug("No master TIPP associated with this TIPP directly. We should query for one.")

      // Now let's try and read an existing tipp from the master package.
      def mtp = master.getTipps().find {
        (it.title == tipp.getTitle()) &&
            (it.hostPlatform == tipp.getHostPlatform())
      }
      master_tipp = (mtp ? KBComponent.deproxy(mtp) : null)
    }

    if (!master_tipp) {
      // Create a new master tipp.
      master_tipp = tipp.clone().save(failOnError: true)
      log.debug("Added master tipp ${master_tipp.id} to tipp ${tipp.id}")
    }
    else {

      log.debug("Found master tipp ${master_tipp.id} to tipp ${tipp.id}")
      master_tipp = tipp.sync(master_tipp)
    }

    // Ensure certain values are correct.
    master_tipp.with {
      setName(null)
      setPkg(master)
      setSystemComponent(true)
    }


    // Save the master tipp.
    master_tipp.save(failOnError: true)
    master.save(failOnError: true)
    tipp.save(failOnError: true, flush: true)

    // Set as master for faster lookup.
    tipp.setMasterTipp(master_tipp)
    tipp.save(failOnError: true, flush: true)

    // Return the TIPP.
    master_tipp
  }

  /**
   * Get the Set of Orgs currently acting as a provider.
   */
  public Set<Org> getAllProviders() {

    log.debug("Looking for all providers.")

    // The results set.
    LinkedHashSet results = []

    // Create the criteria.
    ComboCriteria c = ComboCriteria.createFor(Package.createCriteria())

    // Query for a list of packages and return the providers.
    def providers = c.list {
      and {
        c.add(
            "status",
            "eq",
            RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT))
      }
    }.each {

      // Add any provider that is set.
      if (it?.provider) {
        results << (it.provider)
      }
    }

    log.debug("Found ${results.size()} providers.")
    results
  }

  @Transactional
  public def generatePackageTypes(Job j = null, def pkg_id = null) {
    log.debug("Generating missing package content types.")
    def result = [book: 0, db: 0, journal: 0, mixed: 0, errors: 0]
    def pkg_list = []

    if (!pkg_id) {
      pkg_list = Package.executeQuery("select id from Package where contentType is null")
    }
    else {
      pkg_list << pkg_id
    }

    def msg_list = []
    def rdv_journal = RefdataCategory.lookup("TitleInstance.Medium", "Journal")
    def rdv_book = RefdataCategory.lookup("TitleInstance.Medium", "Book")
    def rdv_db = RefdataCategory.lookup("TitleInstance.Medium", "Database")
    int ctr = 0

    for (pkg in pkg_list) {

      Package.withNewTransaction {

        def pkg_obj = Package.get(pkg)
        def has_db = pkg_obj.tipps.title.find { it.medium == rdv_db }
        def has_journal = pkg_obj.tipps.title.find { it.medium == rdv_journal }
        def has_book = pkg_obj.tipps.title.find { it.medium == rdv_book }

        if (has_db && !has_journal && !has_book) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Database')
          result.db++
        }
        else if (has_journal && !has_db && !has_book) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Journal')
          result.journal++
        }
        else if (has_book && !has_db && !has_journal) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Book')
          result.book++
        }
        else if (has_book && has_journal) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Mixed')
          result.mixed++
        }
        else if (!has_book && !has_journal && !has_db) {
          log.debug("No content to categorize for ${pkg_obj.name}")
        }
        else {
          def msg = "Found illegal package composition for ${pkg_obj.name} (journals: ${has_journal}, db: ${has_db}, book: ${has_book})!"
          log.warn(msg)
          result.errors++
          msg_list.add(msg)
        }
        ctr++
        if (j) {
          j.setProgress(ctr, pkg_list.size())
        }
      }
    }

    if (j) {
      j.message("Finished creating ${ctr} new types (${result.errors} errors).".toString())

      msg_list.each {
        j.message(it.toString())
      }

      j.endTime = new Date()
    }
  }

  @Transactional
  def compareLists(listOne, listTwo, def full = true, Date date = null, Job j = null) {
    def result = [:]
    def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    def status_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')
    def status_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    def tipp_status = [status_current]
    Date checkDate = date ?: new Date()
    def tipp_params = [:]
    def totals = [one: [tipps: 0, titles: 0], two: [tipps: 0, titles: 0]]
    def titlesOne = [:]
    def titlesTwo = [:]
    def currentPkgNum = 0
    boolean cancelled = false

    if (date) {
      if (date.before(new Date())) {
        tipp_status << status_retired
      }
    }

    if (full) {
      result = ['new': [], 'both':[], 'missing':[]]
    }
    else {
      result = ['new': 0, 'both': 0, 'missing': 0]
    }

    log.debug("Building titles map 1 ..")

    Package.withNewSession {
      for (p1 in listOne) {
        def pkg = Package.get(genericOIDService.oidToId(p1))
        currentPkgNum++

        if (pkg && !cancelled) {
          int total = TitleInstancePackagePlatform.executeQuery('''select count(*) from TitleInstancePackagePlatform as tipp
                                                                  where tipp.status in (:tippStatus)
                                                                  and exists (select c from Combo as c where c.fromComponent = :pkg and c.toComponent = tipp)''',
                                                                  [tippStatus: tipp_status, pkg: pkg])[0]
          int currentOffset = 0

          while (currentOffset < total) {
            def tipps = TitleInstancePackagePlatform.executeQuery('''from TitleInstancePackagePlatform as tipp
                                                                    where tipp.status in (:tippStatus)
                                                                    and exists (select c from Combo as c where c.fromComponent = :pkg and c.toComponent = tipp)''',
                                                                    [tippStatus: tipp_status, pkg: pkg])

            tipps.each { tipp ->
              def inRange = true

              if (tipp.accessEndDate && tipp.accessEndDate.before(checkDate)) {
                inRange = false
              }
              else if (tipp.accessStartDate && tipp.accessStartDate.after(checkDate)) {
                inRange = false
              }
              else if (tipp.status == status_retired) {
                if (date && tipp.lastUpdated?.before(checkDate)) {
                  inRange = false
                }
              }

              if (inRange) {
                if (!titlesOne[tipp.title.id]){
                  titlesOne[tipp.title.id] = [id: tipp.title.id, name: tipp.title.name, tipps: []]
                  totals.one.titles++
                }

                totals.one.tipps++
                titlesOne[tipp.title.id]['tipps'] << restMappingService.mapObjectToJson(tipp, tipp_params)
              }
              currentOffset++
            }
            cleanUpGorm()
          }

          if (Thread.currentThread().isInterrupted() || j?.isCancelled()) {
            log.debug("cancelling Job #${j?.uuid}")
            cancelled = true
            break
          }

          if (j) {
            j.setProgress(currentPkgNum, (listOne.size() + listTwo.size()))
          }
        }
        else if (!pkg) {
          log.debug("Unable to resolve Package with id ${p1}")
        }
      }

      log.debug("Added ${totals.one.titles} titles with ${totals.one.tipps} TIPPs!")

      log.debug("Building titles map 2 ..")

      for (p2 in listTwo) {
        def pkg = Package.get(genericOIDService.oidToId(p2))
        currentPkgNum++

        if (pkg && !cancelled) {
          int total = TitleInstancePackagePlatform.executeQuery("select count(*) from TitleInstancePackagePlatform as tipp where tipp.status in (:tippStatus) and exists (select c from Combo as c where c.fromComponent = :pkg and c.toComponent = tipp)", [tippStatus: tipp_status, pkg: pkg])[0]
          int currentOffset = 0

          while (currentOffset < total) {
            def tipps = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform as tipp where tipp.status in (:tippStatus) and exists (select c from Combo as c where c.fromComponent = :pkg and c.toComponent = tipp)", [tippStatus: tipp_status, pkg: pkg], [max: 50, offset: currentOffset])

            tipps.each { tipp ->
              def inRange = true

              if (tipp.accessEndDate && tipp.accessEndDate.before(checkDate)) {
                inRange = false
              }
              else if (tipp.accessStartDate && tipp.accessStartDate.after(checkDate)) {
                inRange = false
              }
              else if (tipp.status == status_retired) {
                if (date && tipp.lastUpdated.before(checkDate)) {
                  inRange = false
                }
              }

              if (inRange) {
                if (!titlesTwo[tipp.title.id]){
                  titlesTwo[tipp.title.id] = [id: tipp.title.id, name: tipp.title.name, tipps: []]
                  totals.two.titles++
                }

                totals.two.tipps++
                titlesTwo[tipp.title.id]['tipps'] << restMappingService.mapObjectToJson(tipp, tipp_params)

                if (!titlesOne[tipp.title.id]) {
                  if (full) {
                    result['new'] << restMappingService.mapObjectToJson(tipp, tipp_params)
                  }
                  else {
                    result['new']++
                  }
                }
              }
              currentOffset++
            }
          }

          cleanUpGorm()

          if (Thread.currentThread().isInterrupted() || j?.isCancelled()) {
            log.debug("cancelling Job #${j?.uuid}")
            cancelled = true
            break
          }

          if (j) {
            j.setProgress(currentPkgNum, (listOne.size() + listTwo.size()))
          }
        }
      }
    }

    log.debug("Finished collecting TIPPs. Starting comparison")

    if (!cancelled) {
      titlesOne.each { id, val ->
        if (!titlesTwo[id]) {
          if (full) {
            result['missing'] << val
          }
          else {
            result['missing']++
          }
        }
        else {
          if (full) {
            result['both'] << ['id': val.id, 'name': val.name, 'old': val.tipps, 'new': titlesTwo[id].tipps]
          }
          else {
            result['both']++
          }
        }
      }

      titlesTwo.each { id, val ->
        if (!titlesOne[id]) {
          if (full) {
            result['new'] << val
          }
          else {
            result['new']++
          }
        }
      }
    }

    if (j) {
      j.endTime = new Date()
    }

    log.debug("Added ${totals.two.titles} titles with ${totals.two.tipps} TIPPs!")
    result
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

  def restLookup(packageHeaderDTO, def user = null) {
    log.info("Upsert org with header ${packageHeaderDTO}");
    def result = [to_create: true];
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    def normname = Package.generateNormname(packageHeaderDTO.name)

    log.debug("Checking by normname ${normname} ..")
    def name_candidates = Package.executeQuery("from Package as p where p.normname = ? and p.status <> ?", [normname, status_deleted])
    def ids_list = packageHeaderDTO.identifiers ?: packageHeaderDTO.ids
    def matches = [:]
    def created = false
    boolean changed = false;

    if (name_candidates.size() > 0) {
      name_candidates.each { nc ->
        if (!matches["${nc.id}"])
          matches["${nc.id}"] = []

        matches["${nc.id}"] << [field: 'name', value: packageHeaderDTO.name, message: "Another package with this name already exists!"]
      }
    }

    // if (packageHeaderDTO.ids?.size() > 0) {
    //   ids_list.each { rid ->
    //     Identifier the_id = null

    //     if (rid instanceof Integer) {
    //       the_id = Identifier.get(rid)
    //     }
    //     else {
    //       def ns_field = rid.type ?: rid.namespace
    //       def ns = null

    //       if (ns_field) {
    //         if (ns_field instanceof Integer) {
    //           ns = IdentifierNamespace.get(ns_field)
    //         }
    //         else {
    //           ns = IdentifierNamespace.findByValueIlike(ns_field)
    //         }

    //         if (ns) {
    //           def match = Package.lookupByIO(ns.value, rid.value)

    //           if (match && match.status != status_deleted) {
    //             if (!matches["${ns.id}"])
    //               matches["${ns.id}"] = []

    //             matches["${ns.id}"] << [field: 'ids', value: rid.value, message: "An existing package was matched by a supplied identifier!"]
    //           }
    //         }
    //       }
    //     }
    //   }
    // }

    def variant_normname = GOKbTextUtils.normaliseString(packageHeaderDTO.name)
    def variant_matches = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

    variant_matches.each { vm ->
      if (!matches["${vm.id}"])
        matches["${vm.id}"] = []

      matches["${vm.id}"] << ['field': 'name', value: packageHeaderDTO.name, message: "Provided name matched a variant of an existing package!"]
    }

    if (packageHeaderDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      packageHeaderDTO.variantNames.each {
        def variant = null

        if (it instanceof String) {
          variant = it.trim()
        }
        else if (it instanceof Map) {
          variant = it.variantName?.trim() ?: null
        }

        if (variant) {
          def var_norm = Package.generateNormname(variant)
          def name_matches = Package.findAllByNormnameAndStatusNotEqual(var_norm, status_deleted)

          name_matches.each { nm ->
            if (!matches["${nm.id}"])
              matches["${nm.id}"] = []

            matches["${nm.id}"] << [field: 'variantNames', value: variant, message: "Provided variant matched the title of an existing package!"]
          }

          def variant_nn = GOKbTextUtils.normaliseString(variant)
          def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_nn, status_deleted]);

          variant_candidates.each { vc ->
            log.debug("Found existing package variant name for variantName ${variant}")
            if (!matches["${vc.id}"])
              matches["${vc.id}"] = []

            matches["${vc.id}"] << ['field': 'variantNames', value: variant, message: "Provided variant matched that of an existing package!"]
          }
        }
      }
    }

    if (matches.size() > 0) {
      result.to_create = false
      result.matches = matches
    }

    result
  }

  /**
   * Definitive rules for taking a package header DTO and inserting or updating an existing package based on package name
   *
   * listStatus:'Checked',
   * status:'Current',
   * breakable:'Unknown',
   * consistent:'Unknown',
   * fixed:'Unknown',
   * paymentType:'Unknown',
   * global:'Global',
   * nominalPlatform:54678
   * provider:4325
   * listVerifier:'',
   * userListVerifier:'benjamin_ahlborn'
   * listVerifierDate:'2015-06-19T00:00:00Z'
   * source:[
   *   url:'http://www.zeitschriftendatenbank.de'
   *   defaultAccessURL:''
   *   explanationAtSource:''
   *   contextualNotes:''
   *   frequency:''
   *   ruleset:''
   *   defaultSupplyMethod:'Other'
   *   defaultDataFormat:'Other'
   *   responsibleParty:''
   * ]
   * name:'Campus: All Journals'
   * curatoryGroups:[
   *   curatoryGroup:"SuUB Bremen"
   * ]
   * variantNames : [
   *   variantName:"Campus: All Journals"
   * ]
   */

  @Transactional
  public Package upsertDTO(packageHeaderDTO, def user = null) {
    log.info("Upsert package with header ${packageHeaderDTO}");
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def pkg_normname = Package.generateNormname(packageHeaderDTO.name)

    log.debug("Checking by normname ${pkg_normname} ..")
    def name_candidates = Package.executeQuery("from Package as p where p.normname = ? and p.status <> ?", [pkg_normname, status_deleted])
    def full_matches = []
    def created = false
    def result = packageHeaderDTO.uuid ? Package.findByUuid(packageHeaderDTO.uuid) : null;
    boolean changed = false;

    if (!result && name_candidates.size() > 0 && packageHeaderDTO.identifiers?.size() > 0) {
      log.debug("Got ${name_candidates.size()} matches by name. Checking against identifiers!")
      name_candidates.each { mp ->
        if (mp.ids.size() > 0) {
          def id_match = false;

          packageHeaderDTO.identifiers.each { rid ->

            Identifier the_id = componentLookupService.lookupOrCreateCanonicalIdentifier(rid.type, rid.value);

            if (mp.ids.contains(the_id)) {
              id_match = true
            }
          }

          if (id_match && !full_matches.contains(mp)) {
            full_matches.add(mp)
          }
        }
      }

      if (full_matches.size() == 1) {
        log.debug("Matched package by name + identifier!")
        result = full_matches[0]
      }
      else if (full_matches.size() == 0 && name_candidates.size() == 1) {
        result = name_candidates[0]
        log.debug("Found a single match by name!")
      }
      else {
        log.warn("Found multiple possible matches for package! Aborting..")
        return result
      }
    }
    else if (!result && name_candidates.size() == 1) {
      log.debug("Matched package by name!")
      result = name_candidates[0]
    }
    else if (result && result.name != packageHeaderDTO.name) {
      def current_name = result.name

      changed |= ClassUtils.setStringIfDifferent(result, 'name', packageHeaderDTO.name)

      if (!result.variantNames.find { it.variantName == current_name }) {
        result.ensureVariantName(current_name)
      }
    }

    if (!result) {
      log.debug("Did not find a match via name, trying existing variantNames..")
      def variant_normname = GOKbTextUtils.normaliseString(packageHeaderDTO.name)
      def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

      if (variant_candidates.size() == 1) {
        result = variant_candidates[0]
        log.debug("Package matched via existing variantName.")
      }
    }

    if (!result && packageHeaderDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      packageHeaderDTO.variantNames.each {

        if (it.trim().size() > 0) {
          result = Package.findByName(it)

          if (result) {
            log.debug("Found existing package name for variantName ${it}")
          }
          else {

            def variant_normname = GOKbTextUtils.normaliseString(it)
            def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

            if (variant_candidates.size() == 1) {
              log.debug("Found existing package variant name for variantName ${it}")
              result = variant_candidates[0]
            }
          }
        }
      }
    }

    if (!result) {
      log.debug("No existing package matched. Creating new package..")

      result = new Package(name: packageHeaderDTO.name, normname: pkg_normname)

      created = true

      if (packageHeaderDTO.uuid && packageHeaderDTO.uuid.trim().size() > 0) {
        result.uuid = packageHeaderDTO.uuid
      }

      result.save(flush: true, failOnError: true)
    }
    else if (user && !user.hasRole('ROLE_SUPERUSER') && result.curatoryGroups && result.curatoryGroups?.size() > 0) {
      def cur = user.curatoryGroups?.id.intersect(result.curatoryGroups?.id)

      if (!cur) {
        log.debug("No curator!")
        return result
      }
    }

    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.listStatus, result, 'listStatus')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.status, result, 'status')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.editStatus, result, 'editStatus')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.scope, result, 'scope')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.breakable, result, 'breakable')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.consistent, result, 'consistent')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.fixed, result, 'fixed')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.paymentType, result, 'paymentType')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.global, result, 'global')
    changed |= ClassUtils.setStringIfDifferent(result, 'listVerifier', packageHeaderDTO.listVerifier?.toString())
    // User userListVerifier
    changed |= ClassUtils.setDateIfPresent(packageHeaderDTO.listVerifiedDate, result, 'listVerifiedDate');

    // ListVerifier

    if (packageHeaderDTO.userListVerifier) {
      def looked_up_user = User.findByUsername(packageHeaderDTO.userListVerifier)
      if (looked_up_user && ((result.userListVerifier == null) || (result.userListVerifier?.id != looked_up_user?.id))) {
        result.userListVerifier = looked_up_user
        changed = true
      }
      else {
        log.warn("Unable to find username for list verifier ${packageHeaderDTO.userListVerifier}");
      }
    }

    // Platform

    if (packageHeaderDTO.nominalPlatform) {
      def platformDTO = [:];

      if (packageHeaderDTO.nominalPlatform instanceof String && packageHeaderDTO.nominalPlatform.trim()) {
        platformDTO['name'] = packageHeaderDTO.nominalPlatform
      }
      else if (packageHeaderDTO.nominalPlatform instanceof Integer) {
        platformDTO['id'] = (Long) packageHeaderDTO.nominalPlatform
      }
      else if (packageHeaderDTO.nominalPlatform.name && packageHeaderDTO.nominalPlatform.name.trim().size() > 0) {
        platformDTO = packageHeaderDTO.nominalPlatform
      }

      if (platformDTO) {
        def np = null

        if (platformDTO.uuid) {
          np = Platform.findByUuid(platformDTO.uuid)
        }
        else if (platformDTO.id) {
          np = Platform.get(platformDTO.id)
        }
        else if (platformDTO.name) {
          np = Platform.findByName(platformDTO.name)
        }

        if (!np && platformDTO.name) {
          np = Platform.upsertDTO(platformDTO)
        }

        if (np) {
          if (result.nominalPlatform != np) {
            result.nominalPlatform = np;
            changed = true
          }
          else {
            log.debug("Platform already set")
          }
        }
        else {
          log.warn("Unable to locate nominal platform ${packageHeaderDTO.nominalPlatform}");
        }
      }
      else {
        log.warn("Could not extract platform information from JSON!")
      }
    }

    // Provider

    if (packageHeaderDTO.nominalProvider) {

      def providerDTO = [:]

      if (packageHeaderDTO.nominalProvider instanceof String && packageHeaderDTO.nominalProvider.trim()) {
        providerDTO['name'] = packageHeaderDTO.nominalProvider
      }
      else if (packageHeaderDTO.nominalProvider.name && packageHeaderDTO.nominalProvider.name.trim()) {
        providerDTO = packageHeaderDTO.nominalProvider
      }

      log.debug("Trying to set package provider.. ${providerDTO}")
      def prov = null

      if (providerDTO?.uuid) {
        prov = Org.findByUuid(providerDTO.uuid)
      }

      if (providerDTO && !prov) {
        def norm_prov_name = KBComponent.generateNormname(providerDTO.name)

        prov = Org.findByNormname(norm_prov_name)

        if (!prov) {
          log.debug("None found by Normname ${norm_prov_name}, trying variants")
          def variant_normname = GOKbTextUtils.normaliseString(providerDTO.name)
          def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status = ?", [variant_normname, status_deleted]);

          if (candidate_orgs.size() == 1) {
            prov = candidate_orgs[0]
          }
          else if (candidate_orgs.size() == 0) {
            log.debug("No org match for provider ${packageHeaderDTO.nominalProvider}. Creating new org..")
            prov = new Org(name: providerDTO.name, normname: norm_prov_name, uuid: providerDTO.uuid ?: null).save(flush: true, failOnError: true);
          }
          else {
            log.warn("Multiple org matches for provider ${packageHeaderDTO.nominalProvider}. Skipping..");
          }
        }
      }

      if (prov) {
        if (result.provider != prov) {
          result.provider = prov;

          log.debug("Provider ${prov.name} set.")
          changed = true
        }
        else {
          log.debug("No provider change")
        }
      }
    }
    else {
      log.debug("No provider found!")
    }

    // Source

    // variantNames are handled in ComponentUpdateService
    // packageHeaderDTO.variantNames?.each {
    //   if ( it.trim().size() > 0 ) {
    //     result.ensureVariantName(it)
    //     changed=true
    //   }
    // }

    // CuratoryGroups

    packageHeaderDTO.curatoryGroups?.each {
      def cg = null
      def cgname = null

      if (it instanceof Integer) {
        cg = CuratoryGroup.get(it)
      }
      else if (it instanceof String) {
        cgname = it

        cg = CuratoryGroup.findByNameIlike(it)
      }
      else if (it.id) {
        cg = CuratoryGroup.get(it.id)
      }
      else if (it.name) {
        cgname = it.name

        cg = CuratoryGroup.findByNameIlike(it.name)
      }

      if (cg) {
        if (result.curatoryGroups.find { it.name == cg.name }) {
        }
        else {

          result.curatoryGroups.add(cg)
          changed = true;
        }
      }
      else if (cgname) {
        try {
          def new_cg = new CuratoryGroup(name: cgname).save(flush: true, failOnError: true)
          result.curatoryGroups.add(new_cg)
          changed = true
        }
        catch (grails.validation.ValidationException ve) {
          log.debug("Unable to create new CG!")
        }
      }
    }

    if (packageHeaderDTO.source) {
      def src = null

      if (packageHeaderDTO.source instanceof Integer) {
        src = Source.get(packageHeaderDTO.source)
      }
      else if (packageHeaderDTO.source instanceof Map) {
        def sourceMap = packageHeaderDTO.source

        if (sourceMap.id) {
          src = Source.get(sourceMap.id)
        }
        else {
          def namespace = null

          if (sourceMap.targetNamespace instanceof Integer) {
            namespace = IdentifierNamespace.get(sourceMap.targetNamespace)
          }

          if (!result.source || result.source.name != result.name) {
            def source_config = [
                name           : result.name,
                url            : sourceMap.url,
                frequency      : sourceMap.frequency,
                ezbMatch       : (sourceMap.ezbMatch ?: false),
                automaticUpdate: (sourceMap.automaticUpdate ?: false),
                targetNamespace: namespace
            ]

            src = new Source(source_config).save(flush: true)

            result.curatoryGroups.each { cg ->
              src.curatoryGroups.add(cg)
            }
          }
          else {
            src = result.source

            changed |= ClassUtils.setStringIfDifferent(src, 'frequency', sourceMap.frequency)
            changed |= ClassUtils.setStringIfDifferent(src, 'url', sourceMap.url)
            changed |= ClassUtils.setBooleanIfDifferent(src, 'ezbMatch', sourceMap.ezbMatch)
            changed |= ClassUtils.setBooleanIfDifferent(src, 'automaticUpdate', sourceMap.automaticUpdate)

            if (namespace && namespace != src.targetNamespace) {
              src.targetNamespace = namespace
              changed = true
            }

            src.save(flush: true)
          }
        }
      }

      if (src && result.source != src) {
        result.source = src
        changed = true
      }
    }

    result.save(flush: true)


    result
  }


  /**
   * collects the data of the given package into a KBART formatted TSV file for later download
   */
  void createKbartExport(Package pkg, ExportType exportType=ExportType.KBART_TIPP) {
    if (pkg) {
      def exportFileName = generateExportFileName(pkg, exportType)
      def path = exportFilePath()
      try {
        def out = new File("${path}${exportFileName}")
        if (out.isFile())
          return
        else {
          new File(path).list().each { fileName ->
            if (fileName.startsWith(exportFileName.substring(0, exportFileName.length() - 21))) {
              if (!new File(path + fileName).delete())
                log.warn("couldn't delete file ${path}${fileName}")
            }
          }
        }
        out.withWriter { writer ->
          // As per spec header at top of file / section
          // II: Need to add in preceding_publication_title_id
          KBART_FIELDS.eachWithIndex { field, i ->
            writer.write(field)
            writer.write(i < KBART_FIELDS.size() - 1 ? '\t' : '\n')
          }

          def session = sessionFactory.getCurrentSession()
          def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
          def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status = :sc and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p', pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sc', status_current)
          query.setParameter('ct', combo_tipps)

          ScrollableResults tippIDs = query.scroll(ScrollMode.FORWARD_ONLY)
          int ctr = 0

          TitleInstancePackagePlatform.withNewSession { tsession ->
            while (tippIDs.next()) {
              def tipp_id = tippIDs.get(0)
              TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

              kbartRecordsFor(tipp, exportType).each { record ->
                KBART_FIELDS.eachWithIndex { fieldName, i ->
                  writer.write(sanitize(record[fieldName]))
                  writer.write(i < KBART_FIELDS.size() - 1 ? '\t' : '\n')
                }
              }

              if (ctr % 50 == 0) {
                tsession.flush()
                tsession.clear()
              }
              ctr++
            }
          }
          tippIDs.close()
          writer.close()
        }
      }
      catch (Exception e) {
        log.error("Problem with creating KBART export data", e)
      }
    }
  }

  public void createTsvExport(Package pkg) {
    def export_date = dateFormatService.formatDate(new Date());
    String filename = generateExportFileName(pkg, ExportType.TSV)
    String path = exportFilePath()
    try {
      if (pkg) {
        String lastUpdate = dateFormatService.formatDate(pkg.lastUpdated)
        File out = new File("${path}${filename}")
        if (out.isFile())
          return
        else {
          new File(path).list().each { someFileName ->
            if (someFileName.startsWith(filename.substring(0, filename.length() - 15))) {
              if (!new File(path + someFileName).delete())
                log.warn("couldn't delete file ${path}${someFileName}")
            }
          }
        }
        out.withWriter { writer ->
          def sanitize = { it ? "${it}".trim() : "" }

          // As per spec header at top of file / section
          writer.write("GOKb Export : ${pkg.provider?.name} : ${pkg.name} : ${export_date}\n");

          writer.write('TIPP ID\t' +
              'TIPP URL\t' +
              'Title ID\t' +
              'Title\t' +
              'TIPP Status\t' +
              '[TI] Publisher\t' +
              '[TI] Imprint\t' +
              '[TI] Published From\t' +
              '[TI] Published to\t' +
              '[TI] Medium\t' +
              '[TI] OA Status\t' +
              '[TI] Continuing series\t' +
              '[TI] ISSN\t' +
              '[TI] EISSN\t' +
              '[TI] ZDB-ID\t' +
              'Package\t' +
              'Package ID\t' +
              'Package URL\t' +
              'Platform\t' +
              'Platform URL\t' +
              'Platform ID\t' +
              'Reference\t' +
              'Edit Status\t' +
              'Access Start Date\t' +
              'Access End Date\t' +
              'Coverage Start Date\t' +
              'Coverage Start Volume\t' +
              'Coverage Start Issue\t' +
              'Coverage End Date\t' +
              'Coverage End Volume\t' +
              'Coverage End Issue\t' +
              'Embargo\t' +
              'Coverage depth\t' +
              'Coverage note\t' +
              'Host Platform URL\t' +
              'Format\t' +
              'Payment Type\t' +
              '[TI] DOI\t' +
              '[TI] ISBN\t' +
              '[TI] pISBN' +
              '\n');

          def session = sessionFactory.getCurrentSession()
          def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status <> :sd and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p', pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sd', status_deleted)
          query.setParameter('ct', combo_tipps)

          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)
          int ctr = 0

          TitleInstancePackagePlatform.withNewSession { tsession ->
            while (tipps.next()) {
              def tipp_id = tipps.get(0);
              TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

              if (tipp.coverageStatements?.size() > 0) {
                tipp.coverageStatements.each { tcs ->
                  writer.write(
                      sanitize(tipp.getId()) + '\t' +
                          sanitize(tipp.url) + '\t' +
                          sanitize(tipp.title?.getId()) + '\t' +
                          sanitize(tipp.name ?: tipp.title?.name) + '\t' +
                          sanitize(tipp.status.value) + '\t' +
                          sanitize(tipp.title?.getCurrentPublisher()?.name) + '\t' +
                          sanitize(tipp.title?.imprint?.name) + '\t' +
                          sanitize(tipp.title?.publishedFrom) + '\t' +
                          sanitize(tipp.title?.publishedTo) + '\t' +
                          sanitize(tipp.title?.medium?.value) + '\t' +
                          sanitize(tipp.title?.OAStatus?.value) + '\t' +
                          sanitize(tipp.title?.continuingSeries?.value) + '\t' +
                          sanitize(tipp.getIdentifierValue('ISSN') ?: tipp.title?.getIdentifierValue('ISSN')) + '\t' +
                          sanitize(tipp.getIdentifierValue('eISSN') ?: tipp.title?.getIdentifierValue('eISSN')) + '\t' +
                          sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title?.getIdentifierValue('ZDB')) + '\t' +
                          sanitize(pkg.name) + '\t' + sanitize(pkg.getId()) + '\t' +
                          '\t' +
                          sanitize(tipp.hostPlatform.name) + '\t' +
                          sanitize(tipp.hostPlatform.primaryUrl) + '\t' +
                          sanitize(tipp.hostPlatform.getId()) + '\t' +
                          '\t' +
                          sanitize(tipp.editStatus?.value) + '\t' +
                          sanitize(tipp.accessStartDate) + '\t' +
                          sanitize(tipp.accessEndDate) + '\t' +
                          sanitize(tcs.startDate) + '\t' +
                          sanitize(tcs.startVolume) + '\t' +
                          sanitize(tcs.startIssue) + '\t' +
                          sanitize(tcs.endDate) + '\t' +
                          sanitize(tcs.endVolume) + '\t' +
                          sanitize(tcs.endIssue) + '\t' +
                          sanitize(tcs.embargo) + '\t' +
                          sanitize(tcs.coverageDepth) + '\t' +
                          sanitize(tcs.coverageNote) + '\t' +
                          sanitize(tipp.hostPlatform.primaryUrl) + '\t' +
                          sanitize(tipp.format?.value) + '\t' +
                          sanitize(tipp.paymentType?.value) + '\t' +
                          sanitize(tipp.getIdentifierValue('DOI') ?: tipp.title?.getIdentifierValue('DOI')) + '\t' +
                          sanitize(tipp.getIdentifierValue('ISBN') ?: tipp.title?.getIdentifierValue('ISBN')) + '\t' +
                          sanitize(tipp.getIdentifierValue('pISBN') ?: tipp.title?.getIdentifierValue('pISBN')) +
                          '\n');
                }
              }
              else {
                writer.write(
                    sanitize(tipp.getId()) + '\t' +
                        sanitize(tipp.url) + '\t' +
                        sanitize(tipp.title?.getId()) + '\t' +
                        sanitize(tipp.name ?: tipp.title?.name) + '\t' +
                        sanitize(tipp.status.value) + '\t' +
                        sanitize(tipp.title?.getCurrentPublisher()?.name) + '\t' +
                        sanitize(tipp.title?.imprint?.name) + '\t' +
                        sanitize(tipp.title?.publishedFrom) + '\t' +
                        sanitize(tipp.title?.publishedTo) + '\t' +
                        sanitize(tipp.title?.medium?.value) + '\t' +
                        sanitize(tipp.title?.OAStatus?.value) + '\t' +
                        sanitize(tipp.title?.continuingSeries?.value) + '\t' +
                        sanitize(tipp.getIdentifierValue('ISSN') ?: tipp.title?.getIdentifierValue('ISSN')) + '\t' +
                        sanitize(tipp.getIdentifierValue('eISSN') ?: tipp.title?.getIdentifierValue('eISSN')) + '\t' +
                        sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title?.getIdentifierValue('ZDB')) + '\t' +
                        sanitize(pkg.name) + '\t' + sanitize(pkg.getId()) + '\t' +
                        '\t' +
                        sanitize(tipp.hostPlatform?.name) + '\t' +
                        sanitize(tipp.hostPlatform?.primaryUrl) + '\t' +
                        sanitize(tipp.hostPlatform?.getId()) + '\t' +
                        '\t' +
                        sanitize(tipp.editStatus?.value) + '\t' +
                        sanitize(tipp.accessStartDate) + '\t' +
                        sanitize(tipp.accessEndDate) + '\t' +
                        sanitize(tipp.startDate) + '\t' +
                        sanitize(tipp.startVolume) + '\t' +
                        sanitize(tipp.startIssue) + '\t' +
                        sanitize(tipp.endDate) + '\t' +
                        sanitize(tipp.endVolume) + '\t' +
                        sanitize(tipp.endIssue) + '\t' +
                        sanitize(tipp.embargo) + '\t' +
                        sanitize(tipp.coverageDepth) + '\t' +
                        sanitize(tipp.coverageNote) + '\t' +
                        sanitize(tipp.hostPlatform?.primaryUrl) + '\t' +
                        sanitize(tipp.format?.value) + '\t' +
                        sanitize(tipp.paymentType?.value) + '\t' +
                        sanitize(tipp.getIdentifierValue('DOI') ?: tipp.title?.getIdentifierValue('DOI')) + '\t' +
                        sanitize(tipp.getIdentifierValue('ISBN') ?: tipp.title?.getIdentifierValue('ISBN')) + '\t' +
                        sanitize(tipp.getIdentifierValue('pISBN') ?: tipp.title?.getIdentifierValue('pISBN')) +
                        '\n')
              }

              if (ctr % 50 == 0) {
                tsession.flush()
                tsession.clear()
              }
              ctr++

              if (Thread.currentThread().isInterrupted()) {
                break
              }
            }
          }
          tipps.close()
          writer.close()
        }
      }
    }
    catch (Exception e) {
      log.error("Problem with writing tsv export file", e);
    }
  }

  void sendFile(Package pkg, ExportType type, def response) {
    String fileName = generateExportFileName(pkg, type)

    try {
      File file = new File(exportFilePath() + fileName)

      if (!file.isFile()) {
        if (type in [ExportType.KBART_TIPP, ExportType.KBART_TITLE])
          createKbartExport(pkg, type)
        else
          createTsvExport(pkg)
        file = new File(exportFilePath() + fileName)
      }
      else if (Duration.between(Instant.ofEpochMilli(file.lastModified()), Instant.now()).getSeconds() < 2) {
        while (Duration.between(Instant.ofEpochMilli(file.lastModified()), Instant.now()).getSeconds() < 2) {
          sleep(1000)
        }
      }
      InputStream inFile = new FileInputStream(file)

      response.setContentType('text/tab-separated-values')
      response.setHeader("Content-Disposition", "attachment; filename=\"${fileName.substring(0, fileName.length() - 13)}.tsv\"")
      response.setHeader("Content-Encoding", "UTF-8")
      response.setContentLength(file.bytes.length)

      def out = response.outputStream
      IOUtils.copy(inFile, out)
      inFile.close()
      out.close()
    }
    catch (Exception e) {
      log.error("Problem with sending export", e)
    }
  }

  public void sendZip(Collection packs, ExportType type, def response) {
    def pathPrefix = UUID.randomUUID().toString()
    File tempDir = new File(exportFilePath() + "/" + pathPrefix)
    tempDir.mkdir()
    // step one: collect data files in temp directory
    packs.each { pkg ->
      String fileName = generateExportFileName(pkg, type)
      try {
        File src = new File(exportFilePath() + fileName)
        if (!src.isFile()) {
          if (type in [ExportType.KBART_TIPP, ExportType.KBART_TITLE])
            createKbartExport(pkg, type)
          else
            createTsvExport(pkg)
          src = new File(exportFilePath() + fileName)
        }
        File dest = new File("${exportFilePath()}/${pathPrefix}/${fileName.substring(0, fileName.length() - 13)}.tsv")
        FileCopyUtils.copy(src, dest)
      } catch (IOException iox) {
        log.error("Problem while collecting data", iox)
      }
    }

    // step two: zip data
    def zipFileName = exportFilePath() + "gokbExport_${pathPrefix}.zip"
    ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(zipFileName))
    new File("${exportFilePath()}/$pathPrefix").eachFile() { file ->
      //check if file
      if (file.isFile()) {
        zipFile.putNextEntry(new ZipEntry(file.name))
        def buffer = new byte[file.size()]
        file.withInputStream {
          zipFile.write(buffer, 0, it.read(buffer))
        }
        zipFile.closeEntry()
      }
    }
    zipFile.close()

    // step three: copy the zipfile into the response
    File file = new File(zipFileName)
    response.setContentType('application/octet-stream');
    response.setHeader("Content-Disposition", "attachment; filename=\"gokbExport.zip\"")
    response.setHeader("Content-Description", "File Transfer")
    response.setHeader("Content-Transfer-Encoding", "binary")
    response.setContentLength(file.length())

    InputStream input = new FileInputStream(file)
    OutputStream output = response.outputStream
    IOUtils.copy(input, output)
    output.close()
    input.close()
  }

  def updateFromSource(Package p, def user = null, Job job = null, CuratoryGroup activeGroup = null) {
    log.debug("updateFromSource ${p.name}")
    def result = null
    def activeJobs = concurrencyManagerService.getComponentJobs(p.id)

    if (activeJobs.size() == 0) {
      log.debug("UpdateFromSource started")
      result = startSourceUpdate(p, user, job, activeGroup)
    }
    else {
      log.debug("update skipped - already running")
      result = 'ALREADY_RUNNING'
    }
    result
  }

  private def startSourceUpdate(pkg, user, job, activeGroup) {
    log.debug("Source update start..")
    def result = [result: 'OK']

    Package.withNewSession {
      Package p = Package.get(pkg.id)
      DataFile datafile = null
      def platform_url = p.nominalPlatform.primaryUrl
      def pkg_source = p.source
      def preferred_group = activeGroup ?: (p.curatoryGroups?.size() > 0 ? CuratoryGroup.deproxy(p.curatoryGroups[0]) : null)
      def title_ns = pkg_source.targetNamespace ?: (p.provider?.titleNamespace ?: null)

      if (job && !job.startTime) {
        job.startTime = new Date()
      }

      if (pkg_source?.url) {
        def src_url = null
        def dynamic_date = false
        LocalDate extracted_date
        def file_info = [:]

        try {
          src_url = new URL(pkg_source.url)
        }
        catch (Exception e) {
          log.debug("Invalid source URL!")
          result.result = 'ERROR'
          result.messageCode = 'kbart.errors.url.invalid'
          result.message = "Provided URL is not valid!"

          return result
        }

        if (src_url) {
          def existing_string = src_url.toString()
          String local_date_string = LocalDate.now().toString()

          if (existing_string =~ /\{YYYY-MM-DD\}\.(tsv|txt)$/) {
            log.debug("URL contains date placeholder ..")
            src_url = new URL(existing_string.replace('{YYYY-MM-DD}', local_date_string))
            dynamic_date = true
          }
          else {
            def date_pattern_match = (existing_string =~ /([12][0-9]{3}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]))\.(tsv|txt)$/)

            if (date_pattern_match && date_pattern_match[0].size() > 0) {
              String matched_date_string = date_pattern_match[0][1]
              log.debug("${matched_date_string}")
              extracted_date = LocalDate.parse(matched_date_string)
            }
          }
        }
        else {
          log.debug("No source URL!")
          result.result = 'ERROR'
          result.messageCode = 'kbart.errors.url.missing'
          result.message = "Package source does not have an URL!"

          return result
        }

        if (src_url?.getProtocol() in ['http', 'https']) {
          def deposit_token = java.util.UUID.randomUUID().toString()
          File tmp_file = createTempFile(deposit_token)
          def lastRunLocal = pkg_source.lastRun ? pkg_source.lastRun.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null

          pkg_source.lastRun = new Date()
          pkg_source.save(flush: true)

          if (!extracted_date || !lastRunLocal || extracted_date > lastRunLocal) {
            log.debug("Request initial URL..")
            file_info = fetchKbartFile(tmp_file, src_url)
          }

          if (file_info.accessError) {
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.html'
            result.message = "URL returned HTML, indicating provider configuration issues!"

            return result
          } else if (file_info.status == 403) {
            log.debug("URL request failed!")
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.denied'
            result.message = "URL request returned 403 ACCESS DENIED, skipping further tries!"

            return result
          }

          if (!file_info.file_name && (dynamic_date || extracted_date)) {
            LocalDate active_date = LocalDate.now()
            src_url = new URL(src_url.toString().replaceFirst(/[0-9]{4}-[0-9]{2}-[0-9]{2}/, active_date.toString()))
            log.debug("Fetching dated URL for today..")
            file_info = fetchKbartFile(tmp_file, src_url)

            // Look at first of this month
            if (!file_info.file_name) {
              sleep(500)
              log.debug("Fetching first of the month..")
              def som_date_url = new URL(src_url.toString().replaceFirst(/[0-9]{4}-[0-9]{2}-[0-9]{2}/, active_date.withDayOfMonth(1).toString()))
              file_info = fetchKbartFile(tmp_file, som_date_url)
            }

            // Check all days of this month
            while (active_date.isAfter(LocalDate.now().minusDays(30)) && !file_info.file_name) {
              active_date = active_date.minusDays(1)
              src_url = new URL(src_url.toString().replaceFirst(/[0-9]{4}-[0-9]{2}-[0-9]{2}/, active_date.toString()))
              log.debug("Fetching dated URL for date ${active_date}")
              sleep(500)
              file_info = fetchKbartFile(tmp_file, src_url)
            }
          }

          log.debug("Got mime type ${file_info.content_mime_type} for file ${file_info.file_name}")

          if (file_info.file_name) {
            if ((file_info.file_name?.endsWith('.tsv') || file_info.file_name?.endsWith('.txt')) &&
                (file_info.content_mime_type?.startsWith("text/plain") ||
                file_info.content_mime_type?.startsWith("text/tab-separated-values") ||
                file_info.content_mime_type == 'application/octet-stream')
            ) {
              try {
                MessageDigest md5_digest = MessageDigest.getInstance("MD5")
                UniversalDetector detector = new UniversalDetector()
                FileInputStream fis = new FileInputStream(tmp_file)
                BufferedInputStream inputStream = new BufferedInputStream(fis)
                int total_size = 0
                byte[] dataBuffer = new byte[4096]
                int bytesRead

                while ((bytesRead = inputStream.read(dataBuffer, 0, 4096)) != -1) {
                  md5_digest.update(dataBuffer, 0, bytesRead)
                  detector.handleData(dataBuffer, 0, bytesRead)
                  total_size += bytesRead
                }

                log.debug("Read $total_size bytes..")

                detector.dataEnd()
                byte[] md5sum = md5_digest.digest()
                file_info.md5sumHex = new BigInteger(1, md5sum).toString(16)

                String encoding = detector.getDetectedCharset()

                if (encoding in ['UTF-8', 'US-ASCII']) {
                  datafile = DataFile.findByMd5(file_info.md5sumHex)

                  if (!datafile) {
                    log.debug("Create new datafile")
                    datafile = new DataFile(
                                            guid: deposit_token,
                                            md5: file_info.md5sumHex,
                                            uploadName: file_info.file_name,
                                            name: file_info.file_name,
                                            filesize: total_size,
                                            encoding: encoding,
                                            uploadMimeType: file_info.content_mime_type).save()
                    datafile.fileData = tmp_file.getBytes()
                    datafile.save(failOnError:true,flush:true)
                    log.debug("Saved new datafile : ${datafile.id}")
                  }
                  else {
                    log.debug("Found existing datafile ${datafile}")
                  }
                }
                else {
                  log.debug("Illegal charset ${encoding} found..")
                  result.result = 'ERROR'
                  result.messageCode = 'kbart.errors.url.charset'
                  result.message = "KBART is not UTF-8!"
                  return result
                }
              } catch (IOException e) {
                  // handle exception
                  e.printStackTrace()
              }

              if (datafile) {
                if (job) {
                  result = TSVIngestionService.updatePackage(pkg,
                                                             datafile,
                                                             title_ns,
                                                             (user ? true : false),
                                                             false,
                                                             user,
                                                             activeGroup,
                                                             false,
                                                             job)
                }
                else {
                  Job update_job = concurrencyManagerService.createJob { Job j ->
                    TSVIngestionService.updatePackage(pkg,
                                                      datafile,
                                                      title_ns,
                                                      (user ? true : false),
                                                      false,
                                                      user,
                                                      activeGroup,
                                                      false,
                                                      j)
                  }

                  if (preferred_group) {
                    update_job.groupId = preferred_group.id
                  }

                  if (user) {
                    update_job.ownerId = user.id
                  }

                  update_job.description = "KBART REST ingest (${pkg.name})".toString()
                  update_job.type = RefdataCategory.lookup('Job.Type', 'KBARTIngest')
                  update_job.linkedItem = [name: pkg.name, type: "Package", id: pkg.id, uuid: pkg.uuid]
                  update_job.message("Starting upsert for Package ${pkg.name}".toString())
                  update_job.startOrQueue()
                  result.job_result = update_job.get()
                }
              }
              else {
                log.debug("Unable to reference DataFile")
                result.result = 'ERROR'
                result.messageCode = 'kbart.errors.url.unknown'
                result.message = "There were errors saving the KBART file!"
              }
            }
            else {
              result.result = 'ERROR'
              result.messageCode = 'kbart.errors.url.mimeType'
              result.message = "KBART URL returned a wrong content type!"
              log.debug("KBART url ${src_url} returned MIME type ${file_info.content_mime_type}")
            }
          }
          else {
            result.message = "No KBART found for provided URL!"
            log.debug("KBART url ${src_url} returned MIME type ${file_info.content_mime_type}")
          }
        }
        // else if (src_url.getProtocol() in ['ftp', 'sftp']) {
        else {
          result.result = 'ERROR'
          result.messageCode = 'kbart.errors.url.protocol'
          result.message = "KBART URL has an unsupported protocol!"
        }
      }
    }

    result
  }

  def fetchKbartFile(File tmp_file, URL src_url) {
    def result = [content_mime_type: null, file_name: null]
    HttpURLConnection connection

    try {
      connection = (HttpURLConnection) src_url.openConnection()
      connection.addRequestProperty("User-Agent", "GOKb KBART Update")
      connection.setInstanceFollowRedirects(true)
    }
    catch (IOException e) {
        e.printStackTrace()
    }
    connection.connect()
    result.status = connection.getResponseCode()
    log.debug("Fetching ${src_url}")
    log.debug("HEADERS: ${connection.getHeaderFields()}")

    if (result.status == HttpURLConnection.HTTP_OK) {
      result.content_mime_type = connection.getContentType()

      if (result.content_mime_type?.startsWith('text/html')) {
        log.warn("Got HTML result at KBART URL ${src_url}!")
        result.accessError = true
      }
      else {
        log.debug("${result.content_mime_type} ${connection.getHeaderFields()}")
        def file_name = connection.getHeaderField("Content-Disposition")

        if (file_name) {
          file_name = file_name.split('filename=')[1]
        } else if (result.content_mime_type == 'text/plain') (
          file_name = src_url.toString().split('/')[src_url.toString().split('/').size() - 1]
        )

        if (file_name?.trim()) {
          result.file_name = file_name.replaceAll(/\"/, '')
          InputStream content = connection.getInputStream()
          FileUtils.copyInputStreamToFile(content, tmp_file)
          content.close()
          log.debug("Wrote ${tmp_file.length()}")
        }
      }
    }
    else if (result.status != HttpURLConnection.HTTP_NOT_FOUND) {
      log.debug("KBART fetch status: ${result.status}")
      log.debug("${connection.getContent()}")
    }
    else {
      log.debug("KBART fetch status: ${result.status}")
    }

    result
  }

  def analyseFile(temp_file) {
    def result = [:]
    result.filesize = 0

    log.debug("analyze...")

    // Create a checksum for the file..
    MessageDigest md5_digest = MessageDigest.getInstance("MD5")
    FileInputStream fis = new FileInputStream(temp_file)
    BufferedInputStream md5_is = new BufferedInputStream(fis)
    UniversalDetector detector = new UniversalDetector()
    byte[] md5_buffer = new byte[8192]
    int md5_read = 0

    while( (md5_read = md5_is.read(md5_buffer, 0, 8192)) >= 0) {
      md5_digest.update(md5_buffer, 0, md5_read)
      detector.handleData(md5_buffer, 0, md5_read)
      result.filesize += md5_read
    }

    detector.dataEnd()
    md5_is.close()
    byte[] md5sum = md5_digest.digest()
    result.md5sumHex = new BigInteger(1, md5sum).toString(16)
    result.encoding = detector.getDetectedCharset()

    log.debug("MD5 is ${result.md5sumHex}, encoding is ${result.encoding}")
    result
  }

  private byte[] getByteContent(InputStream inputStream){
    ByteArrayOutputStream baos = new ByteArrayOutputStream()
    byte[] buf = new byte[4096]
    int n = 0
    while ((n = inputStream.read(buf)) >= 0){
      baos.write(buf, 0, n)
    }
    baos.toByteArray()
  }

  static String urlStringToFileString(String url){
    url.replace("://", "_").replace(".", "_").replace("/", "_")
  }

  def copyUploadedFile(inputfile, deposit_token) {
    def baseUploadDir = grailsApplication.config.baseUploadDir ?: '/tmp/gokb/ingest'
    log.debug("copyUploadedFile...");
    def sub1 = deposit_token.substring(0,2)
    def sub2 = deposit_token.substring(2,4)
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}")
    def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}"
    def temp_file = new File(temp_file_name)

    // Copy the upload file to a temporary space
    inputfile.transferTo(temp_file);

    temp_file
  }

  def createTempFile(deposit_token) {
    def baseUploadDir = grailsApplication.config.baseUploadDir ?: '/tmp/gokb/ingest'
    def sub1 = deposit_token.substring(0,2)
    def sub2 = deposit_token.substring(2,4)
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}")
    def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}"
    def temp_file = new File(temp_file_name)

    temp_file
  }

  private def validateUploadDir(path) {
    File f = new File(path);
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }

  private String generateExportFileName(Package pkg, ExportType type) {
    String lastUpdate = dateFormatService.formatTimestamp(pkg.lastUpdated)
    StringBuilder name = new StringBuilder()
    if (type in [ExportType.KBART_TIPP, ExportType.KBART_TITLE] ) {
      name.append(toCamelCase(pkg.provider?.name ? pkg.provider.name : "Unknown Provider")).append('_')
          .append(toCamelCase(pkg.global.value)).append('_')
          .append(toCamelCase(pkg.name))
          .append(type == ExportType.KBART_TITLE ? '_Processed' : '')
    }
    else {
      name.append("GoKBPackage-").append(pkg.id)
    }
    name.append('_').append(lastUpdate).append('.tsv')
    return name.toString()
  }

  private String exportFilePath() {
    String exportPath = grailsApplication.config.gokb.tsvExportTempDirectory ?: "/tmp/gokb/export"
    Files.createDirectories(Paths.get(exportPath))
    exportPath.endsWith('/') ? exportPath : exportPath + '/'
  }

  private String toCamelCase(String before) {
    StringBuilder ret = new StringBuilder()
    before.split("\\W").each { word ->
      if (word.length() > 0)
        ret.append(word.substring(0, 1).toUpperCase())
            .append(word.substring(1, word.length()).toLowerCase())
    }
    ret.toString()
  }

  private static String sanitize(def what) {
    return (what && (what.toString().trim() != '')) ? what.toString().trim() : ''
  }

  private String pick(def tippPropValue, def titlePropValue, ExportType exportType) {
    if (tippPropValue && titlePropValue){
      return (exportType==ExportType.KBART_TIPP) ? tippPropValue : titlePropValue
    }
    else if (tippPropValue){
      return tippPropValue
    } else if (titlePropValue){
      return titlePropValue
    }
    return ''
  }

  private String selectDateField(tippPropValue, titlePropValue, ExportType exportType) {
    if (tippPropValue && titlePropValue){
      return (exportType==ExportType.KBART_TIPP) ? dateFormatService.formatDate(tippPropValue) : dateFormatService.formatDate(titlePropValue)
    }
    else if (tippPropValue){
      return dateFormatService.formatDate(tippPropValue)
    } else if (titlePropValue){
      return dateFormatService.formatDate(titlePropValue)
    }
    return ''
  }

  private def kbartRecordsFor (TitleInstancePackagePlatform tipp, ExportType exportType) {
    def recordList = []
    def record = [:]
    record.publication_title = pick(tipp.name, tipp.title?.name, exportType)
    record.publication_type = pick(tipp.publicationType, tipp.title?.niceName == 'Book' ? 'Monograph' : 'Serial', exportType)
    if (pick (tipp.dateFirstInPrint, tipp.title?.hasProperty('dateFirstInPrint'), exportType) != '') {
      record.print_identifier = pick(tipp.getIdentifierValue('pISBN'), tipp.title?.getIdentifierValue('pISBN'), exportType)
      record.online_identifier = pick(tipp.getIdentifierValue('ISBN'), tipp.title?.getIdentifierValue('ISBN'), exportType)
    }
    else{
      record.print_identifier = pick(tipp.getIdentifierValue('ISSN'), tipp.title?.getIdentifierValue('ISSN'), exportType)
      record.online_identifier = pick(tipp.getIdentifierValue('eISSN'), tipp.title?.getIdentifierValue('eISSN'), exportType)
    }
    record.title_url = tipp.url
    record.first_author = pick(tipp.firstAuthor, tipp.title?.hasProperty('firstAuthor') ? tipp.title.firstAuthor : null, exportType)
    record.first_editor = pick(tipp.firstEditor, tipp.title?.hasProperty('firstEditor') ? tipp.title.firstEditor : null, exportType)
    record.date_monograph_published_print = selectDateField(tipp.dateFirstInPrint, tipp.title?.hasProperty('dateFirstInPrint') ? tipp.title.dateFirstInPrint : null, exportType)
    record.date_monograph_published_online = selectDateField(tipp.dateFirstOnline, tipp.title?.hasProperty('dateFirstOnline') ? tipp.title.dateFirstOnline : null, exportType)
    record.monograph_volume = pick(tipp.volumeNumber, tipp.title?.hasProperty('volumeNumber') ? tipp.title.volumeNumber : null, exportType)
    record.monograph_edition = pick(tipp.editionStatement, tipp.title?.hasProperty('editionStatement') ? tipp.title.editionStatement : null, exportType)
    record.title_id = pick(tipp.importId, tipp.title?.id, exportType)
    record.publisher_name = pick(tipp.publisherName, tipp.title?.getCurrentPublisher()?.name, exportType)
    record.preceding_publication_title_id = pick(tipp.precedingPublicationTitleId, tipp.title?.getPrecedingTitleId(), exportType)
    record.parent_publication_title_id = tipp.parentPublicationTitleId
    record.access_type = pick((tipp.paymentType && ['OA','Uncharged'].contains(tipp.paymentType) ? 'F' : 'P'), null, exportType)
    record.zdb_id = pick(tipp.getIdentifierValue('ZDB'), tipp.title?.getIdentifierValue('ZDB'), exportType)
    record.gokb_tipp_uid = tipp.uuid
    record.gokb_title_uid = tipp.title?.uuid

    if (tipp.coverageStatements.size() > 0 ){
      // several records
      tipp.coverageStatements.each { cst ->
        record.date_first_issue_online = cst.startDate ? dateFormatService.formatDate(cst.startDate) : null
        record.num_first_issue_online = cst.startIssue
        record.num_first_vol_online = cst.startVolume
        record.date_last_issue_online = cst.endDate ? dateFormatService.formatDate(cst.endDate) : null
        record.num_last_issue_online = cst.endIssue
        record.num_last_vol_online = cst.endVolume
        record.embargo_info = cst.embargo
        record.coverage_depth = cst.coverageDepth
        record.coverage_notes = cst.coverageNote

        recordList << record.clone()
      }
    }
    else{
      // just one
      record.date_first_issue_online = tipp.startDate ? dateFormatService.formatDate(tipp.startDate) : null
      record.num_first_issue_online = tipp.startIssue
      record.num_first_vol_online = tipp.startVolume
      record.date_last_issue_online = tipp.endDate ? dateFormatService.formatDate(tipp.endDate) : null
      record.num_last_issue_online = tipp.endIssue
      record.num_last_vol_online = tipp.endVolume
      record.embargo_info = tipp.embargo
      record.coverage_depth = tipp.coverageDepth
      record.coverage_notes = tipp.coverageNote

      recordList << record
    }

    return recordList
  }


  def synchronized cachePackageXml(boolean force = false, Job job = null) {
    def result = null

    if (activeCaching == false) {
      log.debug("CachePackageXml started ..")
      activeCaching = true

      result = updatePackageCaches(force, job)

      activeCaching = false
    }
    else {
      log.debug("Caching already in Progress")
      result = 'ALREADY_RUNNING'
    }
    result
  }

  private def updatePackageCaches(force, job) {
    def result = 'OK'
    def attr = [:]
    File dir = new File(grailsApplication.config.gokb.packageXmlCacheDirectory)
    boolean cancelled = false
    File tempDir = new File('/tmp/gokb/oai/')
    job?.startTime = new Date()

    Package.withNewSession {
      def ids = Package.executeQuery("select id from Package")

      for (id in ids) {
        Package item = Package.get(id)

        if (item) {
          try {
            if (!dir.exists()) {
              dir.mkdirs()
            }

            if (!tempDir.exists()) {
              tempDir.mkdirs()
            }

            attr["xmlns:gokb"] = 'http://gokb.org/oai_metadata/'
            def identifier_prefix = "uri://gokb/${grailsApplication.config.sysid}/title/"

            def fileName = "${item.uuid}_${dateFormatService.formatIsoMsTimestamp(item.lastUpdated)}.xml"
            File cachedRecord = new File("${dir}/${fileName}")
            def currentCacheFile = null
            Date currentCacheDate

            for (File file : dir.listFiles()) {
              if (file.name.contains(item.uuid)) {
                def datepart = file.name.split('_')[1]
                currentCacheFile = file
                currentCacheDate = dateFormatService.parseIsoMsTimestamp(datepart.substring(0, datepart.length() - 4))
              }
            }

            if (force || (Duration.between(item.lastUpdated.toInstant(), Instant.now()).getSeconds() > 30 && (!currentCacheFile || item.lastUpdated > currentCacheDate))) {
              File tmpFile = new File("${tempDir}/${fileName}.tmp")

              if (tmpFile.exists()) {
                tmpFile.delete()
              }

              def fileWriter = new BufferedWriter(new FileWriter(tmpFile, true))

              def refdata_package_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Tipps');
              def refdata_hosted_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTipps');
              def refdata_ti_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipps');
              def refdata_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted');
              String tipp_hql = "from TitleInstancePackagePlatform as tipp where exists (select 1 from Combo where fromComponent = :pkg and toComponent = tipp and type = :ctype)"
              def tipp_hql_params = [pkg: item, ctype: refdata_package_tipps]
              def tipps_count = item.status != refdata_deleted ? TitleInstancePackagePlatform.executeQuery("select count(tipp.id) " + tipp_hql, tipp_hql_params, [readOnly: true])[0] : 0
              def refdata_ids = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids')
              def status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
              def pkg_ids = Identifier.executeQuery('''select i.namespace.value, i.namespace.name, i.value, i.namespace.family from Identifier as i,
                                                        Combo as c where c.fromComponent = ?
                                                        and c.type = ?
                                                        and c.toComponent = i
                                                        and c.status = ?''',
                                                        [item, refdata_ids, status_active],
                                                        [readOnly: true])
              String cName = item.class.name

              log.info("Starting package caching for ${item.name} with ${tipps_count} TIPPs..")

              fileWriter << new StreamingMarkupBuilder().bind {
                mkp.declareNamespace(xsd:'http://www.w3.org/2001/XMLSchema')
                'gokb'(attr) {
                  'package'('id': (item.id), 'uuid': (item.uuid)) {

                    // Single props.
                    'name'(item.name)
                    'status'(item.status?.value)
                    'editStatus'(item.editStatus?.value)
                    'language'(item.language?.value)
                    'lastUpdated'(item.lastUpdated ? dateFormatService.formatIsoTimestamp(item.lastUpdated) : null)
                    'descriptionURL'(item.descriptionURL)
                    'description'(item.description)
                    'shortcode'(item.shortcode)

                    // Identifiers
                    'identifiers' {
                      pkg_ids?.each { tid ->
                        'identifier'('namespace': tid[0], 'namespaceName': tid[1], 'value': tid[2], 'type': tid[3])
                      }
                    }

                    // Variant Names
                    'variantNames' {
                      item.variantNames.each { vn ->
                        'variantName'(vn.variantName)
                      }
                    }

                    'scope'(item.scope?.value)
                    'listStatus'(item.listStatus?.value)
                    'breakable'(item.breakable?.value)
                    'consistent'(item.consistent?.value)
                    'fixed'(item.fixed?.value)
                    'paymentType'(item.paymentType?.value)
                    'global'(item.global?.value)
                    'globalNote'(item.globalNote)
                    'contentType'(item.contentType?.value)

                    if (item.nominalPlatform) {
                      'nominalPlatform'(id: item.nominalPlatform.id, uuid: item.nominalPlatform.uuid) {
                        'primaryUrl'(item.nominalPlatform.primaryUrl)
                        'name'(item.nominalPlatform.name)
                      }
                    }

                    if (item.provider) {
                      'nominalProvider'(id: item.provider.id, uuid: item.provider.uuid) {
                        'name'(item.provider.name)
                      }
                    }

                    'listVerifiedDate'(item.listVerifiedDate ? dateFormatService.formatIsoTimestamp(item.listVerifiedDate) : null)

                    'curatoryGroups' {
                      item.curatoryGroups.each { cg ->
                        'group' {
                          'name'(cg.name)
                        }
                      }
                    }

                    if (item.source) {
                      'source' {
                        'name'(item.source.name)
                        'url'(item.source.url)
                        'defaultAccessURL'(item.source.defaultAccessURL)
                        'explanationAtSource'(item.source.explanationAtSource)
                        'contextualNotes'(item.source.contextualNotes)
                        'frequency'(item.source.frequency?.value)
                      }
                    }

                    'dateCreated'(dateFormatService.formatIsoTimestamp(item.dateCreated))
                    'TIPPs'(count: tipps_count) {
                      int offset = 0
                      while (offset < tipps_count) {
                        log.debug("Fetching TIPPs batch ${offset}/${tipps_count}")
                        def tipps = TitleInstancePackagePlatform.executeQuery(tipp_hql + " order by tipp.id", tipp_hql_params, [readOnly: true, max: 50, offset: offset])
                        log.debug("fetch complete ..")
                        offset += 50
                        tipps.each { tipp ->
                          'TIPP'(['id': tipp.id, 'uuid': tipp.uuid]) {
                            'status'(tipp.status?.value)
                            'name'(tipp.name)
                            'lastUpdated'(tipp.lastUpdated ? dateFormatService.formatIsoTimestamp(tipp.lastUpdated) : null)
                            'series'(tipp.series)
                            'subjectArea'(tipp.subjectArea)
                            'publisherName'(tipp.publisherName)
                            'dateFirstInPrint'(tipp.dateFirstInPrint ? dateFormatService.formatDate(tipp.dateFirstInPrint) : null)
                            'dateFirstOnline'(tipp.dateFirstOnline ? dateFormatService.formatDate(tipp.dateFirstOnline) : null)
                            'medium'(tipp.format?.value)
                            'format'(tipp.medium?.value)
                            'volumeNumber'(tipp.volumeNumber)
                            'editionStatement'(tipp.editionStatement)
                            'firstAuthor'(tipp.firstAuthor)
                            'firstEditor'(tipp.firstEditor)
                            'parentPublicationTitleId'(tipp.parentPublicationTitleId)
                            'precedingPublicationTitleId'(tipp.precedingPublicationTitleId)
                            'lastChangedExternal'(tipp.lastChangedExternal ? dateFormatService.formatDate(tipp.lastChangedExternal) : null)
                            'publicationType'(tipp.publicationType?.value)
                            if (tipp.title) {
                              'title'('id': tipp.title.id, 'uuid': tipp.title.uuid) {
                                'name'(tipp.title.name?.trim())
                                'type'(getTitleClass(tipp.title.id))
                                'status'(tipp.title.status?.value)
                                if (getTitleClass(tipp.title.id) == 'BookInstance') {
                                  'dateFirstInPrint'(tipp.title.dateFirstInPrint ? dateFormatService.formatDate(tipp.title.dateFirstInPrint) : null)
                                  'dateFirstOnline'(tipp.title.dateFirstOnline ? dateFormatService.formatDate(tipp.title.dateFirstOnline) : null)
                                  'editionStatement'(tipp.title.editionStatement)
                                  'volumeNumber'(tipp.title.volumeNumber)
                                  'firstAuthor'(tipp.title.firstAuthor)
                                  'firstEditor'(tipp.title.firstEditor)
                                }
                                'identifiers' {
                                  getTitleIds(tipp.title.id).each { tid ->
                                    'identifier'('namespace': tid[0], 'namespaceName': tid[3], 'value': tid[1], 'type': tid[2])
                                  }
                                }
                              }
                            }
                            else {
                              'title'()
                            }
                            'identifiers' {
                              getTippIds(tipp.id).each { tid ->
                                'identifier'('namespace': tid[0], 'namespaceName': tid[3], 'value': tid[1], 'type': tid[2])
                              }
                            }
                            'platform'(id: tipp.hostPlatform.id, 'uuid': tipp.hostPlatform.uuid) {
                              'primaryUrl'(tipp.hostPlatform.primaryUrl?.trim())
                              'name'(tipp.hostPlatform.name?.trim())
                            }
                            'access'(start: tipp.accessStartDate ? dateFormatService.formatDate(tipp.accessStartDate) : null, end: tipp.accessEndDate ? dateFormatService.formatDate(tipp.accessEndDate) : null)
                            def cov_statements = getCoverageStatements(tipp.id)
                            if (cov_statements?.size() > 0) {
                              cov_statements.each { tcs ->
                                'coverage'(
                                  startDate: (tcs.startDate ? dateFormatService.formatDate(tcs.startDate) : null),
                                  startVolume: (tcs.startVolume),
                                  startIssue: (tcs.startIssue),
                                  endDate: (tcs.endDate ? dateFormatService.formatDate(tcs.endDate) : null),
                                  endVolume: (tcs.endVolume),
                                  endIssue: (tcs.endIssue),
                                  coverageDepth: (tcs.coverageDepth?.value ?: null),
                                  coverageNote: (tcs.coverageNote),
                                  embargo: (tcs.embargo)
                                )
                              }
                            }
                            'url'(tipp.url ?: "")
                          }
                        }
                        cleanUpGorm()
                        if (Thread.currentThread().isInterrupted()) {
                          cancelled = true
                          break
                        }

                        log.debug("Batch complete ..")
                      }
                    }
                  }
                }
              }
              log.info("Finished processing ${tipps_count} TIPPs ..")
              fileWriter.close()

              if (!cancelled) {
                def removal = removeCacheEntriesForItem(item.uuid)

                if (removal) {
                  log.debug("Removed stale cache files ..")
                }

                FileUtils.moveFile(tmpFile, cachedRecord)

                if (!force || !currentCacheFile || item.lastUpdated > currentCacheDate) {
                  Package.executeUpdate("update Package p set p.lastCachedDate = ? where p.id = ?", [new Date(cachedRecord.lastModified()), item.id])
                }

                log.info("Caching KBART ..")
                createKbartExport(item, ExportType.KBART_TIPP)
                createKbartExport(item, ExportType.KBART_TITLE)
                createTsvExport(item)
                log.info("Finished caching KBART file")
              }
              else {
                result = 'CANCELLED'
              }
            }
            else if (currentCacheFile && item.lastUpdated <= currentCacheDate) {
              result = 'SKIPPED_NO_CHANGE'
            }
            else if (Duration.between(item.lastUpdated.toInstant(), Instant.now()).getSeconds() <= 30) {
              result = 'SKIPPED_CURRENTLY_CHANGING'
            }
            else {
              result = 'SKIPPED_DEFAULT'
            }
          }
          catch (Exception e) {
            log.error("Exception in Package Caching for ID ${id}!", e)
          }
        }
        else {
          result = 'ERROR'
          log.debug("Unable to reference package by id!")
        }
        cleanUpGorm()

        if (Thread.currentThread().isInterrupted()) {
          cancelled = true
          result = 'CANCELLED'
          break
        }
      }
    }
    job?.endTime = new Date()

    result
  }

  private def getTitleIds(Long title_id) {
    def refdata_ids = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids');
    def status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    def result = Identifier.executeQuery('''select i.namespace.value, i.value, i.namespace.family, i.namespace.name from Identifier as i,
                                            Combo as c
                                            where c.fromComponent.id = ?
                                            and c.type = ?
                                            and c.toComponent = i
                                            and c.status = ?''',
                                            [title_id, refdata_ids, status_active],
                                            [readOnly: true])
    result
  }

  private def getTippIds(Long tipp_id) {
    def refdata_ids = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids');
    def status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    def result = Identifier.executeQuery('''select i.namespace.value, i.value, i.namespace.family, i.namespace.name from Identifier as i,
                                            Combo as c
                                            where c.fromComponent.id = ?
                                            and c.type = ?
                                            and c.toComponent = i
                                            and c.status = ?''',
                                            [tipp_id, refdata_ids, status_active],
                                            [readOnly: true])
    result
  }

  private def getTitleClass(Long title_id) {
    def result = KBComponent.get(title_id)?.class.getSimpleName()

    result
  }

  private def getCoverageStatements(Long tipp_id) {
    def result = TIPPCoverageStatement.executeQuery("from TIPPCoverageStatement as tcs where tcs.owner.id = :tipp", ['tipp': tipp_id], [readOnly: true])
    result
  }

  private boolean removeCacheEntriesForItem(uuid) {
    boolean result = true
    File dir = new File(grailsApplication.config.gokb.packageXmlCacheDirectory)
    File[] files = dir.listFiles()

    for (def file : files) {
      if (file.name.contains(uuid)) {
        result &= file.delete()
      }
    }

    result
  }
}
