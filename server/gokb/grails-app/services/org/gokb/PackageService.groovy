package org.gokb

import org.gokb.cred.*
import org.hibernate.Session

class PackageService {

  ComponentLookupService componentLookupService
  
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
  def findCorrectPackage (Map<String, Boolean> retired_packages, String package_name, boolean incremental) {

    log.debug("Trying to find a package for ${!incremental ? 'none-incremental' : 'incremental'} update using ${package_name}.")

    // Package.
    Package pkg = componentLookupService.lookupComponent(package_name)

    // If we don't have a package then we need to create one and the incremental flag
    // becomes irrelevant.
    if (!pkg) {

      log.error ("No package found for package string supplied via refine. This should not happen as all packages should be looked up.")

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
          if ( pkg.save(failOnError:true) ) {
            log.debug ("Retired and saved package ${pkg.id}.")
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
    pkg.save(failOnError:true)

    pkg
  }

  /**
   * Method to update all Master list type packages.
   */
  def updateAllMasters(delta = true) {

    // Create the criteria.
    getAllProviders().each { Org pr ->
      Org.withNewSession ({ long prov_id, Session sess ->
        updateMasterFor (pr.id, delta)
      }.curry(pr.id))
    }
  }
  
  def sessionFactory
  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP

  private def cleanUpGorm() {
    log.debug ("Cleaning up GORM")
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
    propertyInstanceMap.get().clear()
  }

  /**
   * Method to create or update a Package containing a list of all titles
   * provided by the supplied Org.
   */
  def updateMasterFor (long provider_id, delta = true) {
      
    // Read in a provider.
    Org provider = Org.get(provider_id)

    if (provider) {
      log.debug ("Update or create master list for ${provider.name}")

      // Get the current master Package for this provider.
      ComboCriteria c = ComboCriteria.createFor( Package.createCriteria() )
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
        log.debug ("Found package ${master.id} for ${provider.name}")
  
        delta = delta ? master.lastUpdated : false
      } else {
  
        // Set delta to false...
        delta = false
  
        master = new Package()
  
        // Need to pass the system_save parameter to flag as systemComponent.
        master.save(failOnError:true)
  
        // Create new...
        log.debug ("Created Master Package ${master.id} for ${provider.name}.")
      }
  
      master.setName("${provider.name}: Master List")
      master.setScope(getMasterScope())
      master.setProvider(provider)
      master.setSystemComponent(true)

      // Save.
      master.save(failOnError:true)
      provider.save(failOnError:true, flush:true)
      
      log.debug("Saved Master package ${master.id}")

      // Now query for all packages for the modified since the delta.
      c = ComboCriteria.createFor( Package.createCriteria() )
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
  
      log.debug ("${pkgs.size() ?: 'No'} packages have been updated since the last time this master was updated.")
  
      
      for (Package pkg in pkgs) {
  
        // We should now have a definitive list of tipps that have been changed since the last update.
        
        // Go through the tipps in chunks.
        //def tipps = pkg.tipps.collect { it.id }

        def tipps = TitleInstancePackagePlatform.executeQuery('select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp',[pkg]);
        
        log.debug("Query returns ${tipps.size()} tipps");

        TitleInstancePackagePlatform.withNewSession {
         
          int counter = 1
  
          for (def t in tipps) {
            
            TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(t)
              
            // Do we need to update this tipp.
            if (!delta || (delta && tipp.lastUpdated > delta)) {
              TitleInstancePackagePlatform mt = setOrUpdateMasterTippFor (tipp.id, master.id)
            } else {
              log.debug ("TIPP ${tipp.id} has not been updated since last run. Skipping.")
            }
            log.debug ("TIPP ${counter} of ${tipps.size()} examined.")
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
  public TitleInstancePackagePlatform setOrUpdateMasterTippFor (long tipp_id, long master_id) {
    
    Package master = Package.get(master_id)
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)
    
    // Check the tipp isn't already a master.
    Package pkg = tipp.pkg
    if (pkg.status == getMasterScope()) {
      // Throw an exception.
      log.debug ("getMasterTipp called for TIPP {tipp.id} that is already a master. Returning supplied tipp.")
      return tipp
    }
    
    // Master TIPP
    TitleInstancePackagePlatform master_tipp = tipp.masterTipp
    
    if (!master_tipp) {
      
      log.debug ("No master TIPP associated with this TIPP directly. We should query for one.")
      
      // Now let's try and read an existing tipp from the master package.
      def mtp = master.getTipps().find {
        (it.title == tipp.getTitle()) &&
        (it.hostPlatform == tipp.getHostPlatform())
      }
      master_tipp = (mtp ? KBComponent.deproxy( mtp ) : null)
    }
    
    if (!master_tipp) {
      // Create a new master tipp.
      master_tipp = tipp.clone().save(failOnError:true)
      log.debug("Added master tipp ${master_tipp.id} to tipp ${tipp.id}")
    } else {
      
      log.debug("Found master tipp ${master_tipp.id} to tipp ${tipp.id}")
      master_tipp = tipp.sync(master_tipp)
    }
    
    // Ensure certain values are correct.
    master_tipp.with {
      setName(null)
      setPkg (master)
      setSystemComponent(true)
    }
    
    
    // Save the master tipp.
    master_tipp.save(failOnError:true)
    master.save(failOnError:true)
    tipp.save(failOnError:true, flush:true)
    
    // Set as master for faster lookup.
    tipp.setMasterTipp(master_tipp)
    tipp.save(failOnError:true, flush:true)
    
    // Return the TIPP.
    master_tipp
  }
  
  /**
   * Get the Set of Orgs currently acting as a provider.
   */
  public Set<Org> getAllProviders () {

    log.debug ("Looking for all providers.")

    // The results set.
    LinkedHashSet results = []
    
    // Create the criteria.
    ComboCriteria c = ComboCriteria.createFor( Package.createCriteria() )

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
}
