package org.gokb.cred

import javax.persistence.Transient
import org.gokb.refine.RefineProject
import grails.converters.JSON

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
  String additionalInfo

  // Timestamps
  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'rr_id'
    descriptionOfCause column:'rr_cause_txt', type:'text'
    reviewRequest column:'rr_req_txt', type:'text'
    additionalInfo column:'rr_additional_info', type:'text'
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
    additionalInfo(nullable:true, blank:true)
  }

  public static ReviewRequest raise (KBComponent forComponent,
                                     String actionRequired,
                                     String cause = null,
                                     User raisedBy = null,
                                     refineProject = null,
                                     additionalInfo = null) {

    // Create a request.
    ReviewRequest req = new ReviewRequest (
        status	: RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open'),
        raisedBy : (raisedBy),
        allocatedTo : (raisedBy),
        descriptionOfCause : (cause),
        reviewRequest : (actionRequired),
        refineProject: (refineProject),
        additionalInfo: (additionalInfo),
        componentToReview: (forComponent)
        ).save(flush:true, failOnError:true);

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

  def getAdditional() {
    def result = null
    if (additionalInfo && additionalInfo.length() > 0 ) {
      result = JSON.parse(additionalInfo);
    }
    result;
  }
}
