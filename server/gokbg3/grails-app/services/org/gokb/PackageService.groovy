package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ClassUtils
import grails.gorm.transactions.Transactional
import grails.io.IOUtils
import groovy.util.logging.Slf4j
import groovyx.net.http.RESTClient
import org.apache.commons.lang.RandomStringUtils
import org.gokb.cred.*
import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.Session
import org.hibernate.type.StandardBasicTypes
import org.springframework.util.FileCopyUtils

import java.nio.file.Files
import java.nio.file.Paths
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
  ComponentLookupService componentLookupService
  def grailsApplication
  def messageService
  def concurrencyManagerService
  def dateFormatService

  public static boolean running = false;
  public static final enum ExportType {
    KBART, TSV
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

    } else {

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
    log.debug("Cleaning up GORM")
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
      } else {

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
            } else {
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
    } else {

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
    } else {
      pkg_list << pkg_id
    }

    def msg_list = []
    def rdv_journal = RefdataCategory.lookup("TitleInstance.Medium", "Journal")
    def rdv_book = RefdataCategory.lookup("TitleInstance.Medium", "Book")
    def rdv_db = RefdataCategory.lookup("TitleInstance.Medium", "Database")
    def ctr = 0

    for (pkg in pkg_list) {

      Package.withNewTransaction {

        def pkg_obj = Package.get(pkg)
        def has_db = pkg_obj.tipps.title.find { it.medium == rdv_db }
        def has_journal = pkg_obj.tipps.title.find { it.medium == rdv_journal }
        def has_book = pkg_obj.tipps.title.find { it.medium == rdv_book }

        if (has_db && !has_journal && !has_book) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Database')
          result.db++
        } else if (has_journal && !has_db && !has_book) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Journal')
          result.journal++
        } else if (has_book && !has_db && !has_journal) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Book')
          result.book++
        } else if (has_book && has_journal) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Mixed')
          result.mixed++
        } else if (!has_book && !has_journal && !has_db) {
          log.debug("No content to categorize for ${pkg_obj.name}")
        } else {
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

    if (packageHeaderDTO.ids?.size() > 0) {
      ids_list.each { rid ->
        Identifier the_id = null

        if (rid instanceof Integer) {
          the_id = Identifier.get(rid)
        } else {
          def ns_field = rid.type ?: rid.namespace
          def ns = null

          if (ns_field) {
            if (ns_field instanceof Integer) {
              ns = IdentifierNamespace.get(ns_field)
            } else {
              ns = IdentifierNamespace.findByValueIlike(ns_field)
            }

            if (ns) {
              def match = Package.lookupByIO(ns.value, rid.value)

              if (match) {
                if (!matches["${nc.id}"])
                  matches["${nc.id}"] = []

                matches["${nc.id}"] << [field: 'ids', value: rid.value, message: "An existing package was matched by a supplied identifier!"]
              }
            }
          }
        }
      }
    }

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
        } else if (it instanceof Map) {
          variant = it.variantName?.trim() ?: null
        }

        if (variant) {
          def name_matches = Package.findAllByName(variant)

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
   * Definitive rules for a valid package header
   */

  def validateDTO(packageHeaderDTO, locale) {
    def result = [valid: true, errors: [:], match: false]

    if (!packageHeaderDTO.name?.trim()) {
      result.valid = false
      result.errors.name = [[message: messageService.resolveCode('crossRef.package.error.name', null, locale), baddata: packageHeaderDTO.name]]
    }

    def ids_list = packageHeaderDTO.identifiers ?: packageHeaderDTO.ids
    def id_errors = []

    ids_list?.each { idobj ->
      def id_def = [:]
      def ns_obj = null

      if (idobj instanceof Map) {
        def id_ns = idobj.type ?: (idobj.namespace ?: null)

        id_def.value = idobj.value

        if (id_ns instanceof String) {
          log.debug("Default namespace handling for ${id_ns}..")
          ns_obj = IdentifierNamespace.findByValueIlike(id_ns)
        } else if (id_ns) {
          log.debug("Handling namespace def ${id_ns}")
          ns_obj = IdentifierNamespace.get(id_ns)
        }

        if (!ns_obj) {
          id_errors.add([message: messageService.resolveCode('default.not.found.message', ["Namespace", id_ns], locale)])
        } else {
          id_def.type = ns_obj.value
        }
      } else if (idobj instanceof Integer) {
        Identifier the_id = Identifier.get(id_inc)

        if (!the_id) {
          id_errors.add([message: messageService.resolveCode('crossRef.error.lookup', ["Identifier", "ID"], locale), baddata: idobj])
          result.valid = false
        }
      } else {
        log.warn("Missing information in id object ${idobj}")
        id_errors.add([message: messageService.resolveCode('crossRef.error.format', ["Identifiers"], locale), baddata: idobj])
        result.valid = false
      }

      if (ns_obj && id_def.size() > 0) {
        if (!Identifier.findByNamespaceAndNormname(ns_obj, Identifier.normalizeIdentifier(id_def.value))) {
          if (ns_obj.pattern && !(id_def.value ==~ ns_obj.pattern)) {
            log.warn("Validation for ${id_def.type}:${id_def.value} failed!")
            id_errors.add([message: messageService.resolveCode('identifier.validate.error', [ns_obj.value, id_def.value], locale), baddata: idobj])
            result.valid = false
          } else {
            log.debug("New identifier ..")
          }
        } else {
          log.debug("Found existing identifier ..")
        }
      }
    }

    if (id_errors.size() > 0) {
      result.errors.ids = id_errors
    }

    if (result.valid) {
      def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
      def pkg_normname = Package.generateNormname(packageHeaderDTO.name)

      def name_candidates = Package.executeQuery("from Package as p where p.normname = ? and p.status <> ?", [pkg_normname, status_deleted])
      def full_matches = []

      if (packageHeaderDTO.uuid) {
        result.match = Package.findByUuid(packageHeaderDTO.uuid) ? true : false
      }

      if (!result.match && name_candidates.size() == 1) {
        result.match = true
      }

      if (!result.match) {
        def variant_normname = GOKbTextUtils.normaliseString(packageHeaderDTO.name)
        def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

        if (variant_candidates.size() == 1) {
          result.match = true
          log.debug("Package matched via existing variantName.")
        }
      }

      if (!result.match) {
        log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
        packageHeaderDTO.variantNames.each {

          if (it.trim().size() > 0) {
            def var_pkg = Package.findByName(it)

            if (var_pkg) {
              log.debug("Found existing package name for variantName ${it}")
            } else {

              def variant_normname = GOKbTextUtils.normaliseString(it)
              def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

              if (variant_candidates.size() == 1) {
                log.debug("Found existing package variant name for variantName ${it}")
                result.match = true
              }
            }
          }
        }
      }
    }

    if (packageHeaderDTO.provider && packageHeaderDTO.provider instanceof Integer) {
      def prov = Org.get(packageHeaderDTO.provider)

      if (!prov) {
        result.errors.provider = [[message: messageService.resolveCode('crossRef.error.lookup', ["Provider", "ID"], locale), code: 404, baddata: packageHeaderDTO.provider]]
        result.valid = false
      }
    }

    if (packageHeaderDTO.nominalPlatform && packageHeaderDTO.nominalPlatform instanceof Integer) {
      def prov = Platform.get(packageHeaderDTO.nominalPlatform)

      if (!prov) {
        result.errors.nominalPlatform = [[message: messageService.resolveCode('crossRef.error.lookup', ["Platform", "ID"], locale), code: 404, baddata: packageHeaderDTO.nominalPlatform]]
        result.valid = false
      }
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
      } else if (full_matches.size() == 0 && name_candidates.size() == 1) {
        result = name_candidates[0]
        log.debug("Found a single match by name!")
      } else {
        log.warn("Found multiple possible matches for package! Aborting..")
        return result
      }
    } else if (!result && name_candidates.size() == 1) {
      log.debug("Matched package by name!")
      result = name_candidates[0]
    } else if (result && result.name != packageHeaderDTO.name) {
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
          } else {

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
    } else if (user && !user.hasRole('ROLE_SUPERUSER') && result.curatoryGroups && result.curatoryGroups?.size() > 0) {
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
      } else {
        log.warn("Unable to find username for list verifier ${packageHeaderDTO.userListVerifier}");
      }
    }

    // Platform

    if (packageHeaderDTO.nominalPlatform) {
      def platformDTO = [:];

      if (packageHeaderDTO.nominalPlatform instanceof String && packageHeaderDTO.nominalPlatform.trim()) {
        platformDTO['name'] = packageHeaderDTO.nominalPlatform
      } else if (packageHeaderDTO.nominalPlatform instanceof Integer) {
        platformDTO['id'] = (Long) packageHeaderDTO.nominalPlatform
      } else if (packageHeaderDTO.nominalPlatform.name && packageHeaderDTO.nominalPlatform.name.trim().size() > 0) {
        platformDTO = packageHeaderDTO.nominalPlatform
      }

      if (platformDTO) {
        def np = null

        if (platformDTO.uuid) {
          np = Platform.findByUuid(platformDTO.uuid)
        } else if (platformDTO.id) {
          np = Platform.get(platformDTO.id)
        } else if (platformDTO.name) {
          np = Platform.findByName(platformDTO.name)
        }

        if (!np && platformDTO.name) {
          np = Platform.upsertDTO(platformDTO)
        }

        if (np) {
          if (result.nominalPlatform != np) {
            result.nominalPlatform = np;
            changed = true
          } else {
            log.debug("Platform already set")
          }
        } else {
          log.warn("Unable to locate nominal platform ${packageHeaderDTO.nominalPlatform}");
        }
      } else {
        log.warn("Could not extract platform information from JSON!")
      }
    }

    // Provider

    if (packageHeaderDTO.nominalProvider) {

      def providerDTO = [:]

      if (packageHeaderDTO.nominalProvider instanceof String && packageHeaderDTO.nominalProvider.trim()) {
        providerDTO['name'] = packageHeaderDTO.nominalProvider
      } else if (packageHeaderDTO.nominalProvider.name && packageHeaderDTO.nominalProvider.name.trim()) {
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
          } else if (candidate_orgs.size() == 0) {
            log.debug("No org match for provider ${packageHeaderDTO.nominalProvider}. Creating new org..")
            prov = new Org(name: providerDTO.name, normname: norm_prov_name, uuid: providerDTO.uuid ?: null).save(flush: true, failOnError: true);
          } else {
            log.warn("Multiple org matches for provider ${packageHeaderDTO.nominalProvider}. Skipping..");
          }
        }
      }

      if (prov) {
        if (result.provider != prov) {
          result.provider = prov;

          log.debug("Provider ${prov.name} set.")
          changed = true
        } else {
          log.debug("No provider change")
        }
      }
    } else {
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
      } else if (it instanceof String) {
        String normname = CuratoryGroup.generateNormname(it)
        cgname = it

        cg = CuratoryGroup.findByNormname(normname)
      } else if (it.id) {
        cg = CuratoryGroup.get(it.id)
      } else if (it.name) {
        String normname = CuratoryGroup.generateNormname(it.name)
        cgname = it.name

        cg = CuratoryGroup.findByNormname(normname)
      }

      if (cg) {
        if (result.curatoryGroups.find { it.name == cg.name }) {
        } else {

          result.curatoryGroups.add(cg)
          changed = true;
        }
      } else if (cgname) {
        def new_cg = new CuratoryGroup(name: cgname).save(flush: true, failOnError: true)
        result.curatoryGroups.add(new_cg)
        changed = true
      }
    }

    if (packageHeaderDTO.source) {
      def src = null

      if (packageHeaderDTO.source instanceof Integer) {
        src = Source.get(packageHeaderDTO.source)
      } else if (packageHeaderDTO.source instanceof Map) {
        def sourceMap = packageHeaderDTO.source

        if (sourceMap.id) {
          src = Source.get(sourceMap.id)
        } else {
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
              zdbMatch       : (sourceMap.zdbMatch ?: false),
              automaticUpdate: (sourceMap.automaticUpdate ?: false),
              targetNamespace: namespace
            ]

            src = new Source(source_config).save(flush: true)

            result.curatoryGroups.each { cg ->
              src.curatoryGroups.add(cg)
            }
          } else {
            src = result.source

            changed |= ClassUtils.setStringIfDifferent(src, 'frequency', sourceMap.frequency)
            changed |= ClassUtils.setStringIfDifferent(src, 'url', sourceMap.url)
            changed |= ClassUtils.setBooleanIfDifferent(src, 'ezbMatch', sourceMap.ezbMatch)
            changed |= ClassUtils.setBooleanIfDifferent(src, 'zdbMatch', sourceMap.zdbMatch)
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
  public void createKbartExport(Package pkg) {
    if (pkg) {
      def exportFileName = generateExportFileName(pkg, ExportType.KBART)
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

          def sanitize = { it ? "${it}".trim() : "" }

          // As per spec header at top of file / section
          // II: Need to add in preceding_publication_title_id
          writer.write('publication_title\t' +
            'print_identifier\t' +
            'online_identifier\t' +
            'date_first_issue_online\t' +
            'num_first_vol_online\t' +
            'num_first_issue_online\t' +
            'date_last_issue_online\t' +
            'num_last_vol_online\t' +
            'num_last_issue_online\t' +
            'title_url\t' +
            'first_author\t' +
            'title_id\t' +
            'embargo_info\t' +
            'coverage_depth\t' +
            'coverage_notes\t' +
            'publisher_name\t' +
            'preceding_publication_title_id\t' +
            'date_monograph_published_print\t' +
            'date_monograph_published_online\t' +
            'monograph_volume\t' +
            'monograph_edition\t' +
            'first_editor\t' +
            'parent_publication_title_id\t' +
            'publication_type\t' +
            'access_type\t' +
            'zdb_id\t' +
            'gokb_tipp_uid\t' +
            'gokb_title_uid\n');

          def session = sessionFactory.getCurrentSession()
          def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
          def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status = :sc and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p', pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sc', status_current)
          query.setParameter('ct', combo_tipps)

          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          while (tipps.next()) {
            def tipp_id = tipps.get(0);

            TitleInstancePackagePlatform.withNewSession {
              def tipp = TitleInstancePackagePlatform.get(tipp_id)
              def pub_type = tipp.title?.niceName == 'Book' ? 'Monograph' : 'Serial'
              def print_id = ""
              def online_id = ""

              if (tipp.title.hasProperty('dateFirstInPrint')) {
                def pid = tipp.getIdentifierValue('pISBN') ?: tipp.title.getIdentifierValue('pISBN')
                def oid = tipp.getIdentifierValue('ISBN') ?: tipp.title.getIdentifierValue('ISBN')

                if (pid) {
                  print_id = pid
                }
                if (oid) {
                  online_id = oid
                }
              } else {
                def pid = tipp.getIdentifierValue('ISSN') ?: tipp.title.getIdentifierValue('ISSN')
                def oid = tipp.getIdentifierValue('eISSN') ?: tipp.title.getIdentifierValue('eISSN')

                if (pid) {
                  print_id = pid
                }
                if (oid) {
                  online_id = oid
                }
              }

              if (tipp.coverageStatements?.size() > 0) {
                tipp.coverageStatements.each { cst ->
                  writer.write(
                    sanitize(tipp.name ?: tipp.title.name) + '\t' +
                      print_id + '\t' +
                      online_id + '\t' +
                      sanitize(cst.startDate) + '\t' +
                      sanitize(cst.startVolume) + '\t' +
                      sanitize(cst.startIssue) + '\t' +
                      sanitize(cst.endDate) + '\t' +
                      sanitize(cst.endVolume) + '\t' +
                      sanitize(cst.endIssue) + '\t' +
                      sanitize(tipp.url) + '\t' +
                      (tipp.title.hasProperty('firstAuthor') ? sanitize(tipp.title.firstAuthor) : '') + '\t' +
                      sanitize(tipp.title.getId()) + '\t' +
                      sanitize(cst.embargo) + '\t' +
                      sanitize(cst.coverageDepth).toLowerCase() + '\t' +
                      sanitize(cst.coverageNote) + '\t' +
                      sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                      sanitize(tipp.title.getPrecedingTitleId()) + '\t' +
                      (tipp.title.hasProperty('dateFirstInPrint') ? sanitize(tipp.title.dateFirstInPrint) : '') + '\t' +
                      (tipp.title.hasProperty('dateFirstOnline') ? sanitize(tipp.title.dateFirstOnline) : '') + '\t' +
                      (tipp.title.hasProperty('volumeNumber') ? sanitize(tipp.title.volumeNumber) : '') + '\t' +
                      (tipp.title.hasProperty('editionStatement') ? sanitize(tipp.title.editionStatement) : '') + '\t' +
                      (tipp.title.hasProperty('firstEditor') ? sanitize(tipp.title.firstEditor) : '') + '\t' +
                      '\t' +  // parent_publication_title_id
                      sanitize(pub_type) + '\t' +  // publication_type
                      sanitize(tipp.paymentType?.value) + '\t' +  // access_type
                      sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title.getIdentifierValue('ZDB')) + '\t' +
                      sanitize(tipp.uuid) + '\t' +
                      sanitize(tipp.title.uuid) +
                      '\n');
                }
              } else {
                writer.write(
                  sanitize(tipp.name ?: tipp.title.name) + '\t' +
                    print_id + '\t' +
                    online_id + '\t' +
                    sanitize(tipp.startDate) + '\t' +
                    sanitize(tipp.startVolume) + '\t' +
                    sanitize(tipp.startIssue) + '\t' +
                    sanitize(tipp.endDate) + '\t' +
                    sanitize(tipp.endVolume) + '\t' +
                    sanitize(tipp.endIssue) + '\t' +
                    sanitize(tipp.url) + '\t' +
                    (tipp.title.hasProperty('firstAuthor') ? sanitize(tipp.title.firstAuthor) : '') + '\t' +
                    sanitize(tipp.title.getId()) + '\t' +
                    sanitize(tipp.embargo) + '\t' +
                    sanitize(tipp.coverageDepth).toLowerCase() + '\t' +
                    sanitize(tipp.coverageNote) + '\t' +
                    sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                    sanitize(tipp.title.getPrecedingTitleId()) + '\t' +
                    (tipp.title.hasProperty('dateFirstInPrint') ? sanitize(tipp.title.dateFirstInPrint) : '') + '\t' +
                    (tipp.title.hasProperty('dateFirstOnline') ? sanitize(tipp.title.dateFirstOnline) : '') + '\t' +
                    (tipp.title.hasProperty('volumeNumber') ? sanitize(tipp.title.volumeNumber) : '' + '\t') +
                    (tipp.title.hasProperty('editionStatement') ? sanitize(tipp.title.editionStatement) : '') + '\t' +
                    (tipp.title.hasProperty('firstEditor') ? sanitize(tipp.title.firstEditor) : '') + '\t' +
                    '\t' +  // parent_publication_title_id
                    sanitize(pub_type) + '\t' +  // publication_type
                    sanitize(tipp.paymentType?.value) + '\t' +  // access_type
                    sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title.getIdentifierValue('ZDB')) + '\t' +
                    sanitize(tipp.uuid) + '\t' +
                    sanitize(tipp.title.uuid) +
                    '\n');
              }
            }
          }
          tipps.close()

          writer.flush();
          writer.close();
        }
      }
      catch (Exception e) {
        log.error("Problem with creating KBART export data", e);
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

          while (tipps.next()) {

            def tipp_id = tipps.get(0);
            TitleInstancePackagePlatform.withNewSession {
              TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

              if (tipp.coverageStatements?.size() > 0) {
                tipp.coverageStatements.each { tcs ->
                  writer.write(
                    sanitize(tipp.getId()) + '\t' +
                      sanitize(tipp.url) + '\t' +
                      sanitize(tipp.title.getId()) + '\t' +
                      sanitize(tipp.name ?: tipp.title.name) + '\t' +
                      sanitize(tipp.status.value) + '\t' +
                      sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                      sanitize(tipp.title.imprint?.name) + '\t' +
                      sanitize(tipp.title.publishedFrom) + '\t' +
                      sanitize(tipp.title.publishedTo) + '\t' +
                      sanitize(tipp.title.medium?.value) + '\t' +
                      sanitize(tipp.title.OAStatus?.value) + '\t' +
                      sanitize(tipp.title.continuingSeries?.value) + '\t' +
                      sanitize(tipp.getIdentifierValue('ISSN') ?: tipp.title.getIdentifierValue('ISSN')) + '\t' +
                      sanitize(tipp.getIdentifierValue('eISSN') ?: tipp.title.getIdentifierValue('eISSN')) + '\t' +
                      sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title.getIdentifierValue('ZDB')) + '\t' +
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
                      sanitize(tipp.getIdentifierValue('DOI') ?: tipp.title.getIdentifierValue('DOI')) + '\t' +
                      sanitize(tipp.getIdentifierValue('ISBN') ?: tipp.title.getIdentifierValue('ISBN')) + '\t' +
                      sanitize(tipp.getIdentifierValue('pISBN') ?: tipp.title.getIdentifierValue('pISBN')) +
                      '\n');
                }
              } else {
                writer.write(
                  sanitize(tipp.getId()) + '\t' +
                    sanitize(tipp.url) + '\t' +
                    sanitize(tipp.title.getId()) + '\t' +
                    sanitize(tipp.name ?: tipp.title.name) + '\t' +
                    sanitize(tipp.status.value) + '\t' +
                    sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                    sanitize(tipp.title.imprint?.name) + '\t' +
                    sanitize(tipp.title.publishedFrom) + '\t' +
                    sanitize(tipp.title.publishedTo) + '\t' +
                    sanitize(tipp.title.medium?.value) + '\t' +
                    sanitize(tipp.title.OAStatus?.value) + '\t' +
                    sanitize(tipp.title.continuingSeries?.value) + '\t' +
                    sanitize(tipp.getIdentifierValue('ISSN') ?: tipp.title.getIdentifierValue('ISSN')) + '\t' +
                    sanitize(tipp.getIdentifierValue('eISSN') ?: tipp.title.getIdentifierValue('eISSN')) + '\t' +
                    sanitize(tipp.getIdentifierValue('ZDB') ?: tipp.title.getIdentifierValue('ZDB')) + '\t' +
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
                    sanitize(tipp.getIdentifierValue('DOI') ?: tipp.title.getIdentifierValue('DOI')) + '\t' +
                    sanitize(tipp.getIdentifierValue('ISBN') ?: tipp.title.getIdentifierValue('ISBN')) + '\t' +
                    sanitize(tipp.getIdentifierValue('pISBN') ?: tipp.title.getIdentifierValue('pISBN')) +
                    '\n');
              }
              tipp.discard();
            }
          }
          tipps.close()

          writer.flush();
          writer.close();
        }
      }
    }
    catch (Exception e) {
      log.error("Problem with writing tsv export file", e);
    }
  }

  public void sendFile(Package pkg, ExportType type, def response) {
    String fileName = generateExportFileName(pkg, type)
    try {
      File file = new File(exportFilePath() + fileName)
      if (!file.isFile()) {
        if (type == ExportType.KBART)
          createKbartExport(pkg)
        else
          createTsvExport(pkg)
        file = new File(exportFilePath() + fileName)
      }
      InputStream inFile = new FileInputStream(file)

      response.setContentType('text/tab-separated-values');
      response.setHeader("Content-Disposition", "attachment; filename=\"${fileName.substring(0, fileName.length() - 13)}.tsv\"")
      response.setHeader("Content-Encoding", "UTF-8")
      response.setContentLength(file.bytes.length)

      def out = response.outputStream
      IOUtils.copy(inFile, out)
      inFile.close()
      out.close()
    }
    catch (Exception e) {
      log.error("Problem with sending export", e);
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
          if (type == ExportType.KBART)
            createKbartExport(pkg)
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

  def synchronized updateFromSource(Package p, def user = null) {
    log.debug("updateFromSource")
    def result = null
    boolean started = false
    if (running == false) {
      running = true
      log.debug("UpdateFromSource started")
      result = startSourceUpdate(p, user) ? 'OK' : 'ERROR'
      running = false
    } else {
      log.debug("update skipped - already running")
      result = 'ALREADY_RUNNING'
    }
    result
  }

  /**
   * this method calls Ygor to perform an automated Update on this package.
   * Bad configurations will result in failure.
   * The frequency is ignored: the update starts immediately, setting the
   * lastRun to today if the import was successful.
   */
  private boolean startSourceUpdate(Package p, def user = null) {
    log.debug("Source update start..")
    boolean error = false
    def ygorBaseUrl = grailsApplication.config.gokb.ygorUrl

    if (ygorBaseUrl?.endsWith('/')) {
      ygorBaseUrl = ygorBaseUrl.length() - 1
    }

    def updateTrigger
    def tokenValue = p.updateToken?.value ?: null
    def respData

    if (user) {
      String charset = (('a'..'z') + ('0'..'9')).join()
      tokenValue = RandomStringUtils.random(255, charset.toCharArray())

      if (p.updateToken) {
        def currentToken = p.updateToken
        p.updateToken = null
        currentToken.delete(flush: true)
      }

      def newToken = new UpdateToken(pkg: p, updateUser: user, value: tokenValue).save(flush: true)
    }

    if (tokenValue && ygorBaseUrl) {
      def path = "/enrichment/processGokbPackage?pkgId=${p.id}&updateToken=${tokenValue}"
      updateTrigger = new RESTClient(ygorBaseUrl + path)

      try {
        log.debug("GET ygor/enrichment/processGokbPackage?pkgId=${p.id}&updateToken=${tokenValue}")
        updateTrigger.request(GET) { request ->
          response.success = { resp, data ->
            log.debug("GET ygor/enrichment/processGokbPackage?pkgId=${p.id}&updateToken=${tokenValue} => success")
            // wait for ygor to finish the enrichment
            boolean processing = true
            respData = data
            if (!respData || !respData.jobId) {
              log.error("no ygor job Id received, skipping update of ${p.id}!")
              if (respData?.message) {
                log.error("ygor message: ${respData.message}")
              }
              processing = false
              return
            }
            def statusService = new RESTClient(ygorBaseUrl + "/enrichment/getStatus?jobId=${respData.jobId}")

            while (processing == true) {
              log.debug("GET ygor/enrichment/getStatus?jobId=${respData.jobId}")
              statusService.request(GET) { req ->
                response.success = { statusResp, statusData ->
                  log.debug("GET ygor/enrichment/getStatus?jobId=${respData.jobId} => success")
                  log.debug("status of Ygor ${statusData.status} gokbJob #${statusData.gokbJobId}")
                  if (statusData.status == 'FINISHED_UNDEFINED' ) {
                    processing = false
                    log.debug("No valid URLs found.")
                  }

                  if (statusData.gokbJobId) {
                    processing = false
                    task {
                      log.debug("task start...")
                      Job job = concurrencyManagerService.getJob(Integer.parseInt(statusData.gokbJobId))
                      while (!job.isDone() && job.get() == null) {
                        this.wait(5000) // 5 sec
                        log.debug("checking xRefPackage status...")
                      }
                      log.debug("xRefPackage Job done!")
                      def xRefResult = job.get()
                      if (xRefResult) {
                        if (xRefResult.result == "OK") {
                          log.debug("xRefPackage result OK")
                          Package.withNewSession {
                            def pkg = Package.get(xRefResult.pkgId)
                            pkg.source.lastRun = new Date()
                            pkg.source.save(flush: true)
                          }
                          log.debug("set ${p.source.getNormname()}.lastRun = now")
                        }
                      }
                    }
                  } else {
                    this.wait(10000) // 10 sec
                  }
                }
                response.failure = { statusResp, statusData ->
                  log.error("GET ygor/enrichment/getStatus?jobId=${respData.jobId} => failure")
                  log.error("ygor response message: $statusData.message")
                  processing = false
                  error = true
                }
              }
            }
          }
          response.failure = { resp ->
            log.error("GET ygor/enrichment/processGokbPackage?pkgId=${p.id}&updateToken=${tokenValue} => failure")
            log.error("ygor response: ${resp.responseBase}")
            error = true
          }
        }
      } catch (Exception e) {
        log.error("SourceUpdate Exception:", e);
        error = true
      }
    } else {
      log.debug("No user provided and no existing updateToken found!")
    }
    return !error
  }

  private String generateExportFileName(Package pkg, ExportType type) {
    String lastUpdate = dateFormatService.formatTimestamp(pkg.lastUpdated)
    StringBuilder name = new StringBuilder()
    if (type == ExportType.KBART) {
      name.append(toCamelCase(pkg.provider?.name ? pkg.provider.name : "unknown Provider")).append('_')
        .append(toCamelCase(pkg.global.value)).append('_')
        .append(toCamelCase(pkg.name))
    } else {
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
}
