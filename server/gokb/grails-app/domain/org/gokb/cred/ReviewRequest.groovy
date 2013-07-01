package org.gokb.cred

class ReviewRequest {

  KBComponent componentToReview
  String descriptionOfCause
  String reviewRequest
  Date requestTimestamp
  RefdataValue status

  // Timestamps
  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'rr_id'
    descriptionOfCause column:'rr_cause_txt', type:'text'
    reviewRequest column:'rr_req_txt', type:'text'
    requestTimestamp column:'rr_timestamp'
  }

  static constraints = {
    componentToReview(nullable:false, blank:false)
    descriptionOfCause(nullable:true, blank:true)
    reviewRequest(nullable:false, blank:false)
    requestTimestamp(nullable:false, blank:false)
    status(nullable:false, blank:false)
    dateCreated(nullable:true, blank:true)
    lastUpdated(nullable:true, blank:true)
  }

}
