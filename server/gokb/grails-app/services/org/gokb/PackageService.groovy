package org.gokb

import org.gokb.cred.*

class PackageService {

  ComponentLookupService componentLookupService

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
  def updateAllMasters() {
    
    // Create the criteria.
    getAllProviders().each { Org pr ->
      Package.withNewTransaction {
        updateMasterFor (pr)
      }
    }
  }
  
  
  /**
   * Method to create or update a Package containing a list of all titles
   * provided by the supplied Org.
   */
  def updateMasterFor (Org provider) {
    
    // The delta.
    Date delta = null
    
    log.debug ("Update or create master list for ${provider.name}")
    
    // The Scope.
    RefdataValue scope = RefdataCategory.lookupOrCreate("Package.Scope", "Master File")
    
    // Get the current master Package for this provider.
    ComboCriteria c = ComboCriteria.createFor( Package.createCriteria() )
    Package master = c.get {
      and {
        c.add(
          "scope",
          "eq",
          scope)
        c.add(
          "provider",
          "eq",
          provider)
      }
    }
    
    // Update or create?
    if (master) {
      
      // Update...
      log.debug ("Found package ${master.id} for ${provider.id}")
      
      delta = master.lastUpdated
    } else {
      // Create new...
      log.debug ("No current Master for ${provider.id}. Creating one.")
      
      master = new Package()
      
      // Need to pass the system_save parameter to flag as systemComponent.
      if (!master.save("system_save" : true)) {
        // Error.
        log.error("Failed to save new master package.")
      }
    }
    
    master.with {
      name      = "${provider.name}: Master List"   // Set the title.
      scope     = scope                             // Set the scope.
      provider  = provider                          // Set the provider.
    }
    
    // Now query for all packages for this provider modified since the delta.
    c = ComboCriteria.createFor( Package.createCriteria() )
    Set<Package> pkgs = c.list {
      c.and {
        c.add(
          "id",
          "ne",
          master.id)
        
        c.add(
          "provider",
          "eq",
          provider)
        
        if (delta) {
          c.add(
            "lastUpdated",
            "gt",
            delta)
        }
      }
    } as Set
  
    for (Package pkg in pkgs) {
    
      // We should now have a definitive list of tipps that have been changed since the last update.
      for (def t in pkg.tipps) {
        TitleInstancePackagePlatform tipp = KBComponent.deproxy(t)
        TitleInstancePackagePlatform mt = tipp.masterTipp
        if (!mt) {
          // No Master tipp so we should add one.
          log.debug ("No master tipp for tipp ${tipp.id} so we need to add one.")
          mt = new TitleInstancePackagePlatform().save("system_save" : true, errorOnSave:true)
          tipp.masterTipp = mt
          tipp.save(failOnError:true, flush:true)
          log.debug("Added master tipp ${mt.id} to tipp ${tipp.id}")
        }
        
        // Add all the property vals from this tipp.
        mt = tipp.sync (mt)
        
        // Set the package to the master.
        mt.pkg = master
        
        // The master tipp must be saved with the system flag.
        mt.save("system_save" : true, failOnError:true)
        
        log.debug ("Changes ")
        tipp.save(failOnError:true, flush:true)
        log.debug("Changes saved.")
      }
    }
    
    // Save the master package again.
    master.save("system_save" : true, failOnError:true, flush:true)
  }
  
  /**
   * Get the Set of Orgs currently acting as a provider.
   */
  public Set<Org> getAllProviders () {
    
    log.debug ("Looking for all providers.")
    
    // Create the criteria.
    ComboCriteria c = ComboCriteria.createFor( Package.createCriteria() )
    
    // Query for a list of packages and return the providers.
    def results = c.list {
      and {
        c.add(
          "status",
          "eq",
          RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT))
      }
    }.collect { it.provider } as LinkedHashSet
  
    log.debug("Found ${results.size()} providers.")
    results
  }
}
