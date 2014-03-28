package org.gokb

import grails.transaction.Transactional
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.RefdataCategory
import org.gokb.cred.KBComponent
import org.hibernate.Criteria


@Transactional
class MasterListManagerService {

    def updateAll() {
      
      // Create the criteria.
      getAllProviders().each { Org pr ->
        updateMasterListFor (pr)
      }
    }
    
    
    /**
     * Method to create or update a Package containing a list of all titles
     * provided by the supplied Org.
     */
    def updateMasterListFor(Org org) {
      log.debug ("Update or create master list for ${org.name}")
      
    }
    
    /**
     * Get the Set of Orgs currently acting as a provider.
     */
    public Set<Org> getAllProviders () {
      
      // Create the criteria.
      ComboCriteria c = ComboCriteria.createFor( Package.createCriteria() )
      
      // Query for a list of packages and return the providers.
      c.list {
        c.add(
          "status",
          "eq",
          RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT))
        
        c.projections {
          property 'provider'
        }
      } as Set
    }
}
