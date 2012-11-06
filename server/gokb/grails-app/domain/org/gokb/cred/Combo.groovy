package org.gokb.cred

class Combo {

  RefdataValue status
  RefdataValue type

  // Participant 1 - One of these
  Org fromOrg

  // Participant 2 - One of these
  Org toOrg



  static mapping = {
                id column:'combo_id'
           version column:'combo_version'
            status column:'combo_status_rv_fk'
              type column:'combo_type_rv_fk'
           fromOrg column:'combo_from_org_fk'
             toOrg column:'combo_to_org_fk'
  }

  static constraints = {
    status(nullable:true, blank:false)
    type(nullable:true, blank:false)
    fromOrg(nullable:true, blank:false)
    toOrg(nullable:true, blank:false)
  }
}
