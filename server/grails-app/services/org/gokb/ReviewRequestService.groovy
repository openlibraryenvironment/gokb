package org.gokb

import grails.gorm.transactions.*

import org.gokb.cred.*

@Transactional
class ReviewRequestService {

  def raise(KBComponent forComponent, String actionRequired, String cause = null, User raisedBy = null,
            refineProject = null, additionalInfo = null, RefdataValue stdDesc = null, CuratoryGroup group = null) {
    // Create a request.
    ReviewRequest req = new ReviewRequest(
      status: RefdataCategory.lookup('ReviewRequest.Status', 'Open'),
      raisedBy: (raisedBy),
      descriptionOfCause: (cause),
      reviewRequest: (actionRequired),
      refineProject: (refineProject),
      stdDesc: (stdDesc),
      additionalInfo: (additionalInfo),
      componentToReview: (forComponent)
    ).save(flush:true);

    if (req) {
      if (raisedBy) {
        new ReviewRequestAllocationLog(allocatedTo: raisedBy, rr: req).save(failOnError: true)
      }

      if (group) {
        AllocatedReviewGroup.create(group, req, true)
      }
      else if (KBComponent.has(forComponent, 'curatoryGroups')) {
        log.debug("Using Component groups for ${forComponent} -> ${forComponent.class?.name}..")

        forComponent.curatoryGroups?.each { gr ->
          CuratoryGroup cg = CuratoryGroup.get(gr.id)
          log.debug("Allocating Package Group ${gr} to review ${req}")
          AllocatedReviewGroup.create(cg, req, true)
        }
      }
      else if (forComponent.class == TitleInstancePackagePlatform) {
        Package.withSession {
          Package pkg = Package.get(forComponent.pkg.id)
          log.debug("Using TIPP pkg groups ..")

          pkg?.curatoryGroups?.each { gr ->
            CuratoryGroup cg = CuratoryGroup.get(gr.id)
            log.debug("Allocating TIPP Pkg Group ${gr} to review ${req}")
            AllocatedReviewGroup.create(cg, req, true)
          }
        }
      }
      else if (raisedBy) {
        log.debug("Using User groups ..")
        AllocatedReviewGroup.withSession {
          User user = User.get(raisedBy.id)

          user.curatoryGroups?.each { gr ->
            log.debug("Allocating User Group ${gr} to review ${req}")
            AllocatedReviewGroup.create(gr, req, true)
          }
        }
      }
    }
    req
  }

  AllocatedReviewGroup escalate(AllocatedReviewGroup arg, CuratoryGroup cg){
    arg.status = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
    AllocatedReviewGroup result = AllocatedReviewGroup.findByGroupAndReview(cg, arg.review) ?:
        AllocatedReviewGroup.create(cg, arg.review, false)
    result.escalatedFrom = arg
    result.status = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
    result.save()
    result
  }


  def expungeReview(obj) {
    ReviewRequest.withTransaction {
      ReviewRequestAllocationLog.executeUpdate("delete from ReviewRequestAllocationLog where rr = :rr",[rr: obj])
      AllocatedReviewGroup.removeAll(obj)
      obj.delete(failOnError: true)
    }
  }
}
