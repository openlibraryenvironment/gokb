package org.gokb

import org.gokb.cred.*
import org.springframework.transaction.TransactionStatus

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
  def updateAllMasters(delta = true) {

    // Create the criteria.
    getAllProviders().eachWithIndex { Org pr, index ->
      long prid = pr.id
      Package.withNewTransaction {
        updateMasterFor (prid, delta)
      }
      
      if (index % 25 == 0) cleanUpGorm()
    }
  }
  
  def sessionFactory
  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP
  
  private def cleanUpGorm() {
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
    Org provider = Org.get(provider_id)
    
    if (provider) {
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
  
        delta = delta ? master.lastUpdated : false
      } else {
        
        // Set delta to false...
        delta = false
        
        // Create new...
        log.debug ("No current Master for ${provider.id}. Creating one.")
  
        master = new Package()
  
        // Need to pass the system_save parameter to flag as systemComponent.
        if (!master.save("system_save" : true)) {
          // Error.
          log.error("Failed to save new master package.")
        }
      }
  
      master.setName("${provider.name}: Master List")
      master.setScope(scope)
      master.setProvider(provider)
      master.setSystemComponent(true)
      master.save(failOnError:true, flush:true)
  
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
  
      log.debug ("${pkgs.size() ?: 'No'} packages have been updated since the last time this master was updated.")
  
      for (Package pkg in pkgs) {
  
        // We should now have a definitive list of tipps that have been changed since the last update.
        for (def t in pkg.tipps) {
          TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.deproxy(t)
          TitleInstancePackagePlatform mt = tipp.masterTipp
          if (!mt) {
            // No Master tipp so we should add one.
            log.debug ("No master tipp for tipp ${tipp.id} so we need to add one.")
            mt = tipp.clone().save(failOnError:true)
            tipp.masterTipp = mt
  
            if (!mt.save(errorOnSave:true)) {
              log.error("Error saving master TIPP.")
            }
            if (!tipp.save(failOnError:true)) {
              log.error("Error saving normal TIPP.")
            }
            log.debug("Added master tipp ${mt.id} to tipp ${tipp.id}")
          } else {
            // Add all the property vals from this tipp.
            log.debug("Found master tipp ${mt.id} to tipp ${tipp.id}")
            mt = tipp.sync (mt)
            mt.save(failOnError:true)
          }
  
          // Save the original tipp.
          tipp.save(failOnError:true)
          
          // Set the package to the master, and flag as system component.
          mt.setName (null)
          mt.setPkg (master)
          mt.setSystemComponent(true)
          
          // Ensure package is systemComponent.
          mt.save(failOnError:true, flush:true)
        }
      }
  
      // Save the master package again.
      master.save(failOnError:true)
      provider.save(failOnError:true, flush:true)
      log.debug("Finished updating master package ${master.id}")
    }
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
