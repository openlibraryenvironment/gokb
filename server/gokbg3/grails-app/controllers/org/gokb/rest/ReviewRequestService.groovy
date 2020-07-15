package org.gokb

import grails.gorm.transactions.Transactional

import org.gokb.cred.*

class ReviewRequestService {

  def raise(KBComponent forComponent, String actionRequired, String cause = null, User raisedBy = null, refineProject = null, additionalInfo = null, RefdataValue stdDesc = null) {

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
        ).save(flush:true, failOnError:true);

    if (req) {
      if (raisedBy) {
        new ReviewRequestAllocationLog(allocatedTo: raisedBy, rr: req).save(flush:true,failOnError:true)
      }

      if (KBComponent.has(forComponent, 'curatoryGroups')) {
        componentToReview.curatoryGroups.each {
          new AllocatedReviewGroup(group: it, review: req).save(flush:true,failOnError:true)
        }
      }
      else if (forComponent.class == TitleInstancePackagePlatform && forComponent.pkg.curatoryGroups?.size() > 0) {
        forComponent.pkg.curatoryGroups.each {
          new AllocatedReviewGroup(group: it, review: req).save(flush:true,failOnError:true)
        }
      }
      else if (raisedBy?.curatoryGroups?.size() > 0) {
        raisedBy.curatoryGroups.each {
          new AllocatedReviewGroup(group: it, review: req).save(flush:true,failOnError:true)
        }
      }
    }

    req
  }

  def allocateGroups(rr, user) {

  }
}