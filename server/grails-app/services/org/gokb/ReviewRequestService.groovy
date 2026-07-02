package org.gokb

import grails.gorm.transactions.*

import groovy.json.JsonOutput

import org.gokb.cred.*

@Transactional
class ReviewRequestService {

  def grailsApplication

  def raise(KBComponent forComponent, String actionRequired, String cause = null, User raisedBy = null, additionalInfo = null, RefdataValue stdDesc = null, CuratoryGroup group = null) {
    // Create a request.
    ReviewRequest req = new ReviewRequest(
      status: RefdataCategory.lookup('ReviewRequest.Status', 'Open'),
      raisedBy: (raisedBy),
      descriptionOfCause: (cause),
      reviewRequest: (actionRequired),
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

  def restUpdate(obj, reqBody) {
    Map result = [result: 'OK']
    Map errors = [:]

    if (reqBody.status) {
      def new_status = null

      if (reqBody.status instanceof Integer) {
        def rdc = RefdataCategory.findByDesc("ReviewRequest.Status")
        def rdv = RefdataValue.get(reqBody.status)

        if (rdv?.owner == rdc) {
          new_status = rdv
        }
      } else {
        new_status = RefdataCategory.lookup("ReviewRequest.Status", reqBody.status)
      }

      if (new_status) {
        obj.status = new_status
      }
      else {
        errors.status = [[message: "Illegal status value provided.", code: 404, baddata:reqBody.status]]
      }
    }

    if (reqBody.stdDesc) {
      def rdv_desc = null

      if (reqBody.stdDesc instanceof Integer) {
        def rdc = RefdataCategory.findByDesc("ReviewRequest.StdDesc")
        def rdv = RefdataValue.get(reqBody.stdDesc)

        if (rdv?.owner == rdc) {
          rdv_desc = rdv
        }
      } else {
        rdv_desc = RefdataCategory.lookup("ReviewRequest.StdDesc", reqBody.stdDesc)
      }

      if (rdv_desc) {
        obj.stdDesc = rdv_desc
      }
      else {
        errors.stdDesc = [[message: "Illegal standard description provided.", code: 404, baddata:reqBody.stdDesc]]
      }
    }

    if (reqBody.additionalInfo) {
      obj.additionalInfo = JsonOutput.toJson(reqBody.additionalInfo)
    }

    if (reqBody.allocatedTo) {
      def allocatedUser = User.findById(reqBody.allocatedTo)

      if (allocatedUser) {
        obj.allocatedTo = allocatedUser
      }
      else {
        errors.allocatedTo = [[message:"Unable to update allocated User for ID ${reqBody.allocatedTo}", baddata: reqBody.allocatedTo]]
      }
    }

    if (reqBody.reviewedBy) {
      def reviewedByUser = User.findById(reqBody.reviewedBy)

      if (reviewedByUser) {
        obj.reviewedBy = reviewedByUser
      }
      else {
        errors.reviewedBy = [[message:"Unable to update reviewedBy User for ID ${reqBody.reviewedBy}", baddata: reqBody.reviewedBy]]
      }
    }

    if (reqBody.needsNotify) {
      def nn = params.boolean(reqBody.needsNotify)

      if (nn) {
        obj.needsNotify = nn
      }
      else {
        errors.needsNotify = [[message:"Expected boolean value for needsNotify!", baddata: reqBody.needsNotify]]
      }
    }

    if (reqBody.reviewRequest?.trim()) {
      obj.reviewRequest = reqBody.reviewRequest.trim()
    }

    if (reqBody.descriptionOfCause?.trim()) {
      obj.descriptionOfCause = reqBody.descriptionOfCause.trim()
    }

    if (reqBody.componentToReview && reqBody.componentToReview != obj.componentToReview.id) {
      errors.componentToReview = [[message: "Changing the connected component of an existing review is not allowed!", baddata: reqBody.componentToReview]]
    }

    if (reqBody.editingNotes != null) {
      obj.editingNotes = reqBody.editingNotes.trim() ?: null
    }

    if (errors) {
      result.result = 'ERROR'
      result.errors = errors
    }
    else if (obj.validate()) {
      obj.save(flush: true)
    }
    else {
      result.result = 'ERROR'
    }

    result
  }

  AllocatedReviewGroup escalate(ReviewRequest review, CuratoryGroup escalatingGroup, CuratoryGroup target) {
    RefdataValue status_inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
    RefdataValue status_ip = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
    AllocatedReviewGroup escalating_arg, new_arg
    List escalating_candidates = AllocatedReviewGroup.findAllByGroupAndReview(escalatingGroup, review)

    if (escalating_candidates.size() == 1) {
      escalating_arg = escalating_candidates[0]

      escalating_arg.status = status_inactive
      escalating_arg.save(flush: true)

      new_arg = AllocatedReviewGroup.findByGroupAndReview(target, review)

      if (!new_arg) {
        new_arg = AllocatedReviewGroup.create(target, review, false)
        new_arg.escalatedFrom = escalating_arg
        new_arg.save(flush: true)
      }
      else if (new_arg.status == status_inactive) {
        new_arg.escalatedFrom = escalating_arg
        new_arg.status = status_ip
        new_arg.save(flush: true)
      }
      else {
        log.debug("Review is already escalated to group ${target}!")
      }
    }
    else if (escalating_candidates.size() > 1) {
      log.error("Found multiple AllocatedReviewGroup for escalation of review ${review}!")
    }
    else (!escalating_candidates) {
      log.debug("NO AllocatedReviewGroup found to escalate for review ${review}!")
    }

    new_arg
  }

  AllocatedReviewGroup deescalate(ReviewRequest review, CuratoryGroup deescalatingGroup) {
    RefdataValue status_inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
    RefdataValue status_ip = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')

    AllocatedReviewGroup deescArg = AllocatedReviewGroup.findByGroupAndReview(deescalatingGroup, review)
    AllocatedReviewGroup targetArg = deescArg?.escalatedFrom ?: null

    if (deescArg && targetArg){
      def inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
      def inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
      deescArg.status = inactive
      deescArg.escalatedFrom = null
      deescArg.save()

      targetArg.status = inProgress
      targetArg.save()

      return targetArg
    }
    else {
      log.error("Unable to deescalate ${deescArg} to ${targetArg}!")
    }

    return null
  }

  public void expungeReview(obj) {
    ReviewRequest.withTransaction {
      ReviewRequestAllocationLog.executeUpdate("delete from ReviewRequestAllocationLog where rr = :rr",[rr: obj])
      AllocatedReviewGroup.removeAll(obj)
      obj.delete(failOnError: true)
    }
  }

  Map checkEscalationForActiveGroup(ReviewRequest rr, CuratoryGroup activeGroup) {
    Map result = [
      result:'OK',
      isEscalatable: false,
      escalationTargetGroup: null
    ]

    CuratoryGroup escalatedToCG

    RefdataValue inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
    List<AllocatedReviewGroup> argCandidates = AllocatedReviewGroup.findAllByGroupAndReviewAndStatus(activeGroup, rr, inProgress)

    if (argCandidates.size() == 1) {
      result.escalatingGroup = [
        id: activeGroup.id,
        name: activeGroup.name,
        uuid: activeGroup.uuid
      ]
    }
    else if (argCandidates.size() > 1) {
      log.error("Unable to find a unique AllocatedReviewGroup for escalation (${rr}, ${activeGroup})!")
      result.code = 500
      result.result = 'ERROR'

      return result
    }
    else {
      result.message = "This active group is not linked to this review."
      result.code = 403
      result.result = 'ERROR'

      return result
    }

    // Escalate from title augment curators to admin

    CuratoryGroup ext_zdb_group = CuratoryGroup.findByName(grailsApplication.config.getProperty("gokb.zdbAugment.rrCurators"))
    CuratoryGroup ext_ezb_group = CuratoryGroup.findByName(grailsApplication.config.getProperty("gokb.ezbAugment.rrCurators"))

    if (activeGroup == ext_zdb_group || activeGroup == ext_ezb_group) {
      String adminGroupName = grailsApplication.config.getProperty("gokb.centralGroups.admin")
      CuratoryGroup adminGroup = adminGroupName ? CuratoryGroup.findByNameIlike(adminGroupName) : null

      if (adminGroup) {
        escalatedToCG = adminGroup
      }
      else {
        log.error("Unable to reference configured admin group '${adminGroup}'!")
        result.code = 500
        result.result = 'ERROR'

        return result
      }
    }

    Class componentClass = rr.componentToReview?.class
    String centralGroup = grailsApplication.config.getProperty("gokb.centralGroups.${componentClass.simpleName}")

    CuratoryGroup editorialGroup = centralGroup ? CuratoryGroup.findByNameIlike(centralGroup) : null

    if (!escalatedToCG) {
      escalatedToCG = activeGroup.superordinatedGroup
    }

    if (!escalatedToCG && editorialGroup && activeGroup != editorialGroup) {
      escalatedToCG = editorialGroup
    }

    if (escalatedToCG) {
      result.escalationTargetGroup = [
        id: escalatedToCG.id,
        name: escalatedToCG.name,
        uuid: escalatedToCG.uuid
      ]
    }
    else {
      result.message = "There is no superordinated/editorial group to escalate to."
      return result
    }


    // check if the type of the linked component allows for escalation
    if (!componentClass || !(componentClass in [BookInstance, DatabaseInstance, JournalInstance, OtherInstance, TitleInstancePackagePlatform, Package])) {
      result.message = "ReviewRequest belongs to the un-escalatable class '${componentClass.simpleName}'"
      return result
    }

    result.isEscalatable = true
    result.message = "The requested ReviewRequest can be escalated."
    return result
  }


  Map checkDeescalationForActiveGroup(ReviewRequest rr, CuratoryGroup activeGroup) {
    Map result = [
      result:'OK',
      isDeescalatable: false,
      escalationTargetGroup: null
    ]

    AllocatedReviewGroup deescArg, targetArg
    deescArg = AllocatedReviewGroup.findByGroupAndReview(activeGroup, rr)
    targetArg = deescArg?.escalatedFrom ?: null

    if (deescArg) {
      result.deescalatingGroup = [
        id: activeGroup.id,
        name: activeGroup.name,
        uuid: activeGroup.uuid
      ]
    }
    else {
      result.message = "The active group is not assigned to this review."
      result.code = 403
      result.result = 'ERROR'
    }

    if (deescArg && targetArg) {
      result.isDeescalatable = true
      result.escalationTargetGroup = [
        id: targetArg.group.id,
        name: targetArg.group.name,
        uuid: targetArg.group.uuid
      ]
      result.message = "The requested ReviewRequest can be deescalated."
    }
    else if (!targetArg) {
      result.message = "Could not find a target to deescalate to."
      result.code = 400
      result.result = 'ERROR'
    }

    result
  }
}
