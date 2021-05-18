package org.gokb

import org.gokb.cred.*
import com.k_int.ClassUtils

class ReviewRequestService {

  def raise(KBComponent forComponent, String actionRequired, String cause = null, User raisedBy = null, refineProject = null, additionalInfo = null, RefdataValue stdDesc = null, CuratoryGroup group = null) {
    // Create a request.
    ReviewRequest req = new ReviewRequest(
      status: RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open'),
      raisedBy: (raisedBy),
      descriptionOfCause: (cause),
      reviewRequest: (actionRequired),
      refineProject: (refineProject),
      stdDesc: (stdDesc),
      additionalInfo: (additionalInfo),
      componentToReview: (forComponent)
    ).save();

    if (req) {
      if (raisedBy) {
        new ReviewRequestAllocationLog(allocatedTo: raisedBy, rr: req).save(flush: true, failOnError: true)

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
      else if (forComponent.class == TitleInstancePackagePlatform && forComponent.pkg?.curatoryGroups?.size() > 0) {
        log.debug("Using TIPP pkg groups ..")
        forComponent.pkg?.curatoryGroups?.each { gr ->
          CuratoryGroup cg = CuratoryGroup.get(gr.id)
          log.debug("Allocating TIPP Pkg Group ${gr} to review ${req}")
          AllocatedReviewGroup.create(cg, req, true)
        }
      }
      else if (raisedBy?.curatoryGroups?.size() > 0) {
        log.debug("Using User groups ..")
        raisedBy.curatoryGroups.each { gr ->
          log.debug("Allocating User Group ${gr} to review ${req}")
          AllocatedReviewGroup.create(gr, req, true)
        }
      }
    }

    req
  }
}
