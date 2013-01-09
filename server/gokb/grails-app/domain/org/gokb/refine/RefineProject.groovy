package org.gokb.refine

import org.gokb.cred.Org

class RefineProject {

   String name
   String description
     Date modified
   String file
  Boolean checkedIn
   String checkedOutBy
     Long localProjectID
   String hash
      Org provider
   String lastValidationResult

  static mapping = {
    table 'refine_project'

                      id column: 'rp_id'
             description column: 'rp_desc'
                modified column: 'rp_modified'
                    file column: 'rp_file'
               checkedIn column: 'rp_checked_in'
            checkedOutBy column: 'rp_checked_out_by'
          localProjectID column: 'rp_local_project_id'
                    hash column: 'rp_hash'
                provider column: 'rp_prov_fk'
    lastValidationResult column: 'rp_last_validation_result', type: 'text'
  }

  
  static constraints = {
                    hash nullable: true
            checkedOutBy nullable: true
             description nullable: true
          localProjectID nullable: true
    lastValidationResult nullable: true
                provider nullable: true
  }
}
