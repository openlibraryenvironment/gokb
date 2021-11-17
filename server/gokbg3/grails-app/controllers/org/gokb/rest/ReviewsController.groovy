package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import groovy.json.JsonOutput
import org.apache.commons.lang.StringUtils
import org.gokb.cred.AllocatedReviewGroup
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.ReviewRequest
import org.gokb.cred.User

@Transactional(readOnly = true)
class ReviewsController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def reviewRequestService
  def componentLookupService
  def componentUpdateService

  @Secured(['ROLE_CONTRIBUTOR', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = []
    def base = grailsApplication.config.serverURL + "/rest"
    User user = User.get(springSecurityService.principal.id)
    result = componentLookupService.restLookup(user, ReviewRequest, params)

    render result as JSON
  }

  @Secured(['ROLE_CONTRIBUTOR', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def obj = ReviewRequest.get(genericOIDService.oidToId(params.id))
    def base = grailsApplication.config.serverURL + "/rest"
    def includes = params['_include'] ? params['_include'].split(',') : []
    def embeds = params['_embed'] ? params['_embed'].split(',') : []
    User user = User.get(springSecurityService.principal.id)

    if (obj?.isReadable()) {
      result = restMappingService.mapObjectToJson(obj, params, user)
      result._links = generateLinks(obj, user)
      result.additionalInfo = obj.additional
    }
    else if (!obj) {
      result.message = "Object ID could not be resolved!"
      response.setStatus(404)
      result.code = 404
      result.result = 'ERROR'
    }
    else {
      result.message = "Access to object was denied!"
      response.setStatus(403)
      result.code = 403
      result.result = 'ERROR'
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def immutable = ['raisedBy', 'componentToReview', 'dateCreated', 'lastUpdated', 'id']
    def user = User.get(springSecurityService.principal.id)
    def obj = ReviewRequest.get(genericOIDService.oidToId(params.id))

    if (obj && reqBody) {
      obj.lock()

      def curator = componentUpdateService.isUserCurator(obj, user)

      if (curator || user.isAdmin()) {
        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.setStatus(409)
          result.message = message(code: "default.update.errors.message")
          render result as JSON
        }

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
            obj.reviewedBy = nn
          }
          else {
            errors.reviewedBy = [[message:"Expected boolean value for needsNotify!", baddata: reqBody.needsNotify]]
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

        if( obj.validate() ) {
          if(errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)

            result._links = generateLinks(obj, user)
          }
          else {
            response.setStatus(400)
            result.message = message(code:"default.update.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Package not found or empty request body!"
    }

    if(errors.size() > 0) {
      result.error = errors
    }
    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def save() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)

    if ( ReviewRequest.isTypeCreatable() ) {
      def obj = null
      def pars = [
        componentToReview: null,
        reviewRequest: null,
        descriptionOfCause: null,
        additionalInfo: null,
        stdDesc: null
      ]

      if (reqBody.reviewRequest?.trim())
        pars.reviewRequest = reqBody.reviewRequest.trim()

      if (reqBody.descriptionOfCause?.trim())
        pars.descriptionOfCause = reqBody.descriptionOfCause.trim()

      if (reqBody.componentToReview instanceof Integer) {
        def comp = KBComponent.get(reqBody.componentToReview)
        if (comp) {
          pars.componentToReview = comp
        }
        else {
          errors.componentToReview = [[message: "Unable to lookup component to be reviewed!", baddata: reqBody.componentToReview]]
        }
      }
      else {
        errors.componentToReview = [[message: "Missing component to be reviewed!"]]
        result.message = "Request payload must contain the component to be reviewed"
        response.setStatus(400)
      }

      if (reqBody.additionalInfo) {
        try {
          pars.additionalInfo = JsonOutput.toJson(reqBody.additionalInfo)
        }
        catch (Exception e) {
          errors.additionalInfo = [[message: "Unable to save additional Info", baddata: reqBody.additionalInfo]]
        }
      }

      if (reqBody.stdDesc || reqBody.type) {
        def desc = null
        def reqDesc = reqBody?.stdDesc ?: reqBody.type
        def cat = RefdataCategory.findByLabel('ReviewRequest.StdDesc')

        if (reqDesc instanceof Integer) {
          def rdv = RefdataValue.get(reqBody.stdDesc)

          if (rdv && rdv in cat.values) {
            desc = rdv
          }
        }
        else {
          desc = RefdataCategory.lookup('ReviewRequest.StdDesc', reqDesc)
        }

        if (desc) {
          pars.stdDesc = desc
        }
        else {
          errors.stdDesc = [[message: "Illegal value for standard description provided!", baddata: reqDesc]]
        }
      }

      if (errors.size() == 0) {
        try {
          obj = reviewRequestService.raise(
              pars.componentToReview,
              pars.reviewRequest,
              pars.descriptionOfCause,
              user,
              null,
              pars.additionalInfo,
              pars.stdDesc,
              componentLookupService.findCuratoryGroupOfInterest(pars.componentToReview, user, reqBody.activeGroup)
          )

          if (obj) {
            result = restMappingService.mapObjectToJson(obj, params, user)
            response.status = 201

            result._links = generateLinks(obj, user)
          }
          else {
            response.setStatus(500)
            result.result = 'ERROR'
            result.message = "Unable to create request for review!"
          }
        }
        catch (Exception e) {
          log.error("Error creating Review", e)
          response.setStatus(500)
          result.result = 'ERROR'
          result.message = "There was an error creating the request."
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(400)
        result.errors = errors
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to delete this component!"
    }
    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = ReviewRequest.get(genericOIDService.oidToId(params.id))

    if ( obj && obj.isDeletable() ) {
      def curator = componentUpdateService.isUserCurator(obj, user)

      if ( curator || user.isAdmin() ) {
        obj.status = RefdataCategory.lookup('ReviewRequest.Status','Deleted')
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing title to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "ReviewRequest not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to delete this ReviewRequest!"
    }
    render result as JSON
  }


  /**
   * Check if the ReviewRequest given by @params.id can be escalated.
   */
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  def isEscalatable() {
    def result = getEscalationTargetGroupId(params.id, params.activeGroupId, params)
    if (response.getStatus() != 404){
      // errors are fine here, unless the ReviewRequest can't be found.
      // No need to trigger an error then,
      // the date of interest is transferred by `isEscalatable`
      result.result = 'OK'
      response.setStatus(200)
    }
    render result as JSON
  }


  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def escalate(){
    def result = getEscalationTargetGroupId(params.id, request.JSON?.activeGroup, params)
    if (result.target?.result == 'OK'){
      CuratoryGroup escalatingGroup = CuratoryGroup.findById(result.target.escalatingGroup)
      CuratoryGroup targetGroup = escalatingGroup.superordinatedGroup
      AllocatedReviewGroup arg = AllocatedReviewGroup.findByGroup(escalatingGroup)
      AllocatedReviewGroup newArg = reviewRequestService.escalate(arg, targetGroup)
      if (newArg){
        ReviewRequest rr = ReviewRequest.get(genericOIDService.oidToId(params.id))
        def inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
        rr.allocatedGroups.each{ ag -> ag.status = inactive }
        newArg.status = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
        result.target.message = "The requested ReviewRequest has been escalated."
      }
      else{
        result.target.message = "All preconditions for an escalation have been met. Could not escalate anyway."
        result.target.result = 'ERROR'
        response.setStatus(500)
      }
    }
    render result as JSON
  }


  /**
   * Check if the ReviewRequest given by @params.id can be deescalated.
   */
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  def isDeescalatable() {
    def result = getDeescalationTargetGroupId(params.id, params.activeGroupId, params)
    if (response.getStatus() != 404){
      // errors are fine here, unless the ReviewRequest can't be found.
      // No need to trigger an error then,
      // the date of interest is transferred by `isEscalatable`
      result.result = 'OK'
      response.setStatus(200)
    }
    render result as JSON
  }


  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def deescalate(){
    def result = getDeescalationTargetGroupId(params.id, request.JSON.activeGroup, params)
    if (result.target?.result == 'OK'){
      ReviewRequest rr = ReviewRequest.get(genericOIDService.oidToId(params.id))
      CuratoryGroup deescalatingGroup = CuratoryGroup.findById(result.target.deescalatingGroup)
      AllocatedReviewGroup deescArg = AllocatedReviewGroup.findByGroupAndReview(deescalatingGroup, rr)
      AllocatedReviewGroup targetArg = deescArg?.escalatedFrom ?: null
      if (deescArg && targetArg){
        def inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
        def inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
        deescArg.status = inactive
        deescArg.escalatedFrom = null
        targetArg.status = inProgress
        result.target.result = 'OK'
        response.setStatus(200)
      }
      else{
        result.target.result = 'ERROR'
        response.setStatus(400)
      }
    }
    render result as JSON
  }


  @Secured(value=["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def bulk() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)

    if (params['_field']?.trim() && params['_value']?.trim()) {
      def report = componentUpdateService.bulkUpdateField(user, ReviewRequest, params)

      if (report.errors > 0) {
        result.result = 'ERROR'
        result.report = report
        response.setStatus(403)
        result.message = "Unable to change ${params['_field']} for ${report.error} of ${report.total} items."
      } else {
        result.message = "Successfully changed ${params['_field']} for ${report.total} items."
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.message = "Missing required params '_field' and '_value'"
    }
    render result as JSON
  }


  private def isUserCurator(obj, user) {
    def curator = false
    if (obj.allocatedTo == user) {
      curator = true
    }
    else if (obj.allocatedGroups?.group.id.intersect(user.curatoryGroups?.id)) {
      curator = true
    }
    curator
  }


  private def generateLinks(obj,user) {
    def base = grailsApplication.config.serverURL + "/rest" + obj.restPath + "/${obj.id}"
    def linksObj = [self:[href:base]]
    def curator = componentUpdateService.isUserCurator(obj, user)

    if (curator || user.isAdmin()) {
      linksObj.update = [href:base]
      linksObj.delete = [href:base]
    }
    return linksObj
  }


  private def getEscalationTargetGroupId(def rrId, def activeGroupId, def params){
    def result = ['result':'ERROR', 'isEscalatable':false, 'params': params]

    def rr = ReviewRequest.get(genericOIDService.oidToId(rrId))
    if (!rr){
      response.setStatus(404)
      result.message = "ReviewRequest not found for id ${rrId}."
      return result
    }
    if (!rr.isEditable()){
      response.setStatus(403)
      result.message = "ReviewRequest for id ${rrId} may not be edited."
      return result
    }

    CuratoryGroup escalatingGroup = null
    if (activeGroupId){
      String id = String.valueOf(activeGroupId)
      if (!StringUtils.isEmpty(id)){
        CuratoryGroup toBeChecked = CuratoryGroup.findById(id)
        List<AllocatedReviewGroup> argCandidates = AllocatedReviewGroup.findAllByGroupAndReview(toBeChecked, rr)
        if (argCandidates.size() == 1){
          escalatingGroup = toBeChecked
        }
      }
    }
    if (!escalatingGroup){
      // Couldn't get escalation group by activeGroup, try allocated groups
      def argsExisting = rr.allocatedGroups
      if (!argsExisting || argsExisting.isEmpty()){
        response.setStatus(409)
        result.message = "The requested ReviewRequest can not be escalated due to missing both, active group and group allocations."
        return result
      }
      def inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
      List<AllocatedReviewGroup> inProgressARGs = AllocatedReviewGroup.findAllByStatus(inProgress)
      if (inProgressARGs.isEmpty()){
        response.setStatus(409)
        result.message = "The requested ReviewRequest can not be escalated due to missing group allocations being in progress."
        return result
      }
      if (inProgressARGs.size() > 1){
        response.setStatus(409)
        result.message = "The requested ReviewRequest can not be escalated due to multiple group allocations being in progress."
        return result
      }
      escalatingGroup = inProgressARGs.get(0).group
      result.escalatingAllocatedReviewGroup = inProgressARGs.get(0).id
    }

    if (!escalatingGroup){
      response.setStatus(404)
      result.message = "Could not get curatory group to be escalated from."
      return result
    }

    CuratoryGroup escalatedToCG = escalatingGroup.superordinatedGroup
    if (!escalatedToCG){
      response.setStatus(409)
      result.message = "Could not escalate due to missing superordinated group."
      return result
    }

    // all conditions are met
    response.setStatus(200)
    result.result = 'OK'
    result.isEscalatable = true
    result.escalatingGroup = escalatingGroup.id
    result.escalationTargetGroup = escalatedToCG.id
    result.message = "The requested ReviewRequest can be escalated."
    return result
  }

  private JSON getDeescalationTargetGroupId(def rrId, def activeGroupId, def params){
    def result = ['result':'ERROR', 'isDeescalatable':false, 'params': params]
    ReviewRequest rr = params.id ? ReviewRequest.get(genericOIDService.oidToId(params.id)) : null
    if (!rr){
      response.setStatus(404)
      result.message = "ReviewRequest not found for id ${rrId}."
      return result
    }
    CuratoryGroup deescalatingGroup = CuratoryGroup.findById(activeGroupId)
    if (!deescalatingGroup){
      response.setStatus(404)
      result.message = "Could not get curatory group to be deescalated from."
      return result
    }
    AllocatedReviewGroup deescArg, targetArg
    deescArg = AllocatedReviewGroup.findByGroupAndReview(deescalatingGroup, rr)
    targetArg = deescArg?.escalatedFrom ?: null
    if (deescArg && targetArg){
      response.setStatus(200)
      result.result = 'OK'
      result.isDeescalatable = true
      result.deescalatingGroup = deescalatingGroup.id
      result.escalationTargetGroup = targetArg.group.id
      result.message = "The requested ReviewRequest can be deescalated."
    }
    return result
  }
}
