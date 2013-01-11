package org.gokb.cred

class Combo {

  RefdataValue status
  RefdataValue type

  // Participant 1 - One of these
  KBComponent from

  // Participant 2 - One of these
  KBComponent to

  static mapping = {
                id column:'combo_id'
           version column:'combo_version'
            status column:'combo_status_rv_fk'
              type column:'combo_type_rv_fk'
           fromOrg column:'combo_from_fk'
             toOrg column:'combo_to_fk'
  }

  static constraints = {
    status(nullable:true, blank:false)
    type(nullable:true, blank:false)
    from(nullable:true, blank:false)
    to(nullable:true, blank:false)
  }
}
