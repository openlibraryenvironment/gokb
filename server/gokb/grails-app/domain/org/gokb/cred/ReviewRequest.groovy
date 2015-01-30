package org.gokb.cred

import javax.persistence.Transient
import org.gokb.refine.RefineProject

class ReviewRequest {

  static auditable = true

  @Transient
  def springSecurityService

  KBComponent componentToReview
  String descriptionOfCause
  String reviewRequest
  RefdataValue status
  RefdataValue stdDesc
  User raisedBy
  User allocatedTo
  User closedBy
  User reviewedBy
  Boolean needsNotify
  RefineProject refineProject

  // Timestamps
  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'rr_id'
    descriptionOfCause column:'rr_cause_txt', type:'text'
    reviewRequest column:'rr_req_txt', type:'text'
  }

  transient public postCreateClosure = { ctx ->
    log.debug("postCreateClosure(${ctx})");
    if ( ctx.user != null ) {
      if ( raisedBy == null )
        raisedBy = ctx.user;
      if ( allocatedTo == null )
        allocatedTo = ctx.user;
    }
  }

  static constraints = {
    componentToReview(nullable:false, blank:false)
    descriptionOfCause(nullable:true, blank:true)
    reviewRequest(nullable:false, blank:false)
    status(nullable:false, blank:false)
    stdDesc(nullable:true, blank:false)
    raisedBy(nullable:true, blank:false)
    reviewedBy(nullable:true, blank:false)
    allocatedTo(nullable:true, blank:false)
    closedBy(nullable:true, blank:false)
    dateCreated(nullable:true, blank:true)
    lastUpdated(nullable:true, blank:true)
    needsNotify(nullable:true, blank:true)
    refineProject(nullable:true, blank:true)
  }

  public static ReviewRequest raise (Map args) {
    
    // Check for necessary args in map.
    if (args?.keySet().intersect(["forComponent", "reviewRequest"]).size() < 2)
      throw new IllegalArgumentException("You must suplpy at least the forComponent and reviewRequest arguments.")
      
    // Now let's add any defaults.
    def arguments = [
      status  : RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open'),
      "allocatedTo" : args['raisedBy'],
    ] + args
    
    // Create a review request from a map.
    ReviewRequest req = new ReviewRequest(arguments)
    
    // Add to the list of requests for the component.
    args['forComponent'].addToReviewRequests( req )
  }
  
  public static ReviewRequest raise (KBComponent forComponent, String reviewRequest, String descriptionOfCause = null, User raisedBy = null, refineProject = null) {

    // Create a request.
    ReviewRequest req = raise (
      "forComponent"  : forComponent,
      "reviewRequest" : (reviewRequest),
      "descriptionOfCause" : (descriptionOfCause),
      "raisedBy" : (raisedBy),
      "refineProject" : (refineProject)
    )

    // Just return the request.
    req
  }

  @Transient
  def availableActions() {
    [
      [code:'method::RRTransfer', label:'Transfer To...'],
      [code:'method::RRClose', label:'Close']
    ]
  }

  @Transient
  static def globalActions() {
    [
      [code:'method::RRTransfer', label:'Transfer To...'],
      [code:'method::RRClose', label:'Close']
    ]
  }


  def RRClose(rrcontext) {
    log.debug("Close review request ${id} - user=${rrcontext.user}");
    this.status=RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Closed')
    this.closedBy = rrcontext.user
  }

  public String getNiceName() {
    return "Review Request";
  }

  def beforeUpdate() {
    if ( isDirty('status') ) {
      reviewedBy = springSecurityService.currentUser
    }
  }
}
