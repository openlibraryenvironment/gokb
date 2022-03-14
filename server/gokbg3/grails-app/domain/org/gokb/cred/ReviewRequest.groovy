package org.gokb.cred

import javax.persistence.Transient
import org.gokb.refine.RefineProject
import grails.converters.JSON
import grails.plugins.orm.auditable.Auditable

class ReviewRequest implements Auditable {

  @Transient
  def springSecurityService

  def allComboPropertyNames = []

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
                                     additionalInfo = null,
                                     RefdataValue stdDesc = null) {

    // Create a request.
    ReviewRequest req = new ReviewRequest (
        status : RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open'),
        raisedBy : (raisedBy),
        descriptionOfCause : (cause),
        reviewRequest : (actionRequired),
        refineProject : (refineProject),
        stdDesc : (stdDesc),
        additionalInfo : (additionalInfo),
        componentToReview : (forComponent)
        ).save(failOnError:true);

    // Just return the request.

    if ( req && raisedBy ) {
      new ReviewRequestAllocationLog(allocatedTo:raisedBy, rr:req).save(failOnError:true)
    }

    req
  }

  public static final String restPath = "/reviews"

  static jsonMapping = [
    'ignore'       : [
      'refineProject',
      'additionalInfo',
      'needsNotify'
    ],
    'defaultEmbeds': [
      'allocatedGroups'
    ]
  ]

  String getLogEntityId() {
      "${this.class.name}:${id}"
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


  public void RRClose(rrcontext) {
    log.debug("Close review request ${id} (${this.class.name}) - user=${rrcontext.user}");

    setStatus(RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Closed'))
    setClosedBy(rrcontext.user)
    save(failOnError:true)
    log.debug("Changed status - ${status} ${closedBy}")
  }

  public String getNiceName() {
    return "Review Request";
  }

  def getAllocatedGroups() {
    return AllocatedReviewGroup.findAllByReview(this)
  }


  def afterInsert() {
    def user = springSecurityService?.currentUser
    if ( user != null ) {
      if ( raisedBy == null )
        raisedBy = user
    }
  }

  def beforeUpdate() {
    if ( isDirty('status') ) {
      log.debug("RR Status changed > ${this.status}")
      reviewedBy = springSecurityService.currentUser
    }
  }

  def beforeValidate() {
    if ( this.id == null && !isDirty('status') ) {
      setStatus(RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open'))
    }
  }

  def getAdditional() {
    def result = null
    if (additionalInfo && additionalInfo.length() > 0 ) {
      result = JSON.parse(additionalInfo);
    }
    result;
  }

  def getAllocationLog() {
    def result = ReviewRequestAllocationLog.executeQuery("from ReviewRequestAllocationLog where rr = ?",[this])
    result
  }

  def expunge() {
    ReviewRequestAllocationLog.executeUpdate("delete from ReviewRequestAllocationLog where rr = ?",[this])
    this.delete(failOnError: true)
  }

  @Transient
  public userAvailableActions() {
    def user = springSecurityService.currentUser
    def allActions = []
    def result = []

    if (this.respondsTo('availableActions')) {
      allActions = this.availableActions()

      allActions.each { ao ->
        if (ao.perm == "delete" && !this.isDeletable()) {
        }
        else if (ao.perm == "admin" && !this.isAdministerable()) {
        }
        else {
          result.add(ao)
        }
      }
    }
    result
  }
}
