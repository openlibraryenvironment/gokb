package org.gokb.cred

class ReviewRequestAllocationLog {

  ReviewRequest rr
  String note
  User allocatedTo
  
  // Timestamps
  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'rral_id'
    note column:'rral_note', type:'text'
  }
  
  static constraints = {
    rr(nullable:false, blank:false)
    note(nullable:true, blank:true)
    allocatedTo(nullable:true, blank:false)
    dateCreated(nullable:true, blank:true)
    lastUpdated(nullable:true, blank:true)
  }
}
