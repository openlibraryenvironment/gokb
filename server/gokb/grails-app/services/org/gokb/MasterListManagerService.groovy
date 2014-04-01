package org.gokb

import grails.transaction.Transactional
import org.gokb.cred.*
import org.hibernate.Criteria


@Transactional
class MasterListManagerService {

    /**
     * Method to update all Master list type packages.
     */
    def updateAll() {
      
      // Create the criteria 
      getAllProviders().each { Org pr ->
        updateMasterListFor (pr)
      }
    }
    
    
    /**
     * Method to create or update a Package containing a list of all titles
     * provided by the supplied Org.
     */
    def updateMasterListFor(Org provider, Date delta = null) {
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
      } else {
        // Create new...
        log.debug ("No current Master for ${provider.id}. Creating one.")
        
        master = new Package([
          "scope"     : scope,
          "provider"  : provider
        ])
        
        // Set delta to false as we have just created the master package.
        delta = null
      }
      
      // Get the current list of tipps and add to a new list.
      Set<TitleInstancePackagePlatform> currentMasterTIPPs = []
      currentMasterTIPPs.addAll(master.getTipps())
      
      // Now query for all the current active packages for this provider.
      c = ComboCriteria.createFor( TitleInstancePackagePlatform.createCriteria() )
      Set<Package> pkgs = c.get {
        and {
          c.add(
            "id",
            "neq",
            master.id)
          c.add(
            "provider",
            "eq",
            provider)
          c.add(
            "status",
            "eq",
            RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT))
        }
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
          
          c.projections {
            property 'provider'
          }
        }
      } as Set
    
      log.debug("Found ${results.size()} providers.")
      results
    }
}
