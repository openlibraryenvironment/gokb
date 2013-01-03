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
  
  static constraints = {
              hash nullable: true
      checkedOutBy nullable: true
       description nullable: true
    localProjectID nullable: true
          provider nullable: true
  }
}
