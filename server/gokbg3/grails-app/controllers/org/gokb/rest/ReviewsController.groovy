package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import groovy.json.JsonOutput
import org.gokb.cred.AllocatedReviewGroup
import org.gokb.cred.BookInstance
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.DatabaseInstance
import org.gokb.cred.JournalInstance
import org.gokb.cred.OtherInstance
import org.gokb.cred.KBComponent
import org.gokb.cred.Package
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstancePackagePlatform
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
    def base = grailsApplication.config.getProperty('serverURL', String, "") + "/rest"
    User user = User.get(springSecurityService.principal.id)
    result = componentLookupService.restLookup(user, ReviewRequest, params)

    render result as JSON
  }

  @Secured(['ROLE_CONTRIBUTOR', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def obj = ReviewRequest.get(genericOIDService.oidToId(params.id))
    def base = grailsApplication.config.getProperty('serverURL', String, "") + "/rest"
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
      response.status = 404
      result.code = 404
      result.result = 'ERROR'
    }
    else {
      result.message = "Access to object was denied!"
      response.status = 403
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
          response.status = 409
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

        if (obj.validate()) {
          if (errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)
            result.additionalInfo = obj.additional

            result._links = generateLinks(obj, user)
          }
          else {
            response.status = 400
            result.message = message(code:"default.update.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.status = 400
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
      }
      else {
        result.result = 'ERROR'
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 404
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
      response.status = 400
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
          result.additionalInfo = obj.additional
          response.status = 201

          result._links = generateLinks(obj, user)
        }
        else {
          response.status = 500
          result.result = 'ERROR'
          result.message = "Unable to create request for review!"
        }
      }
      catch (Exception e) {
        log.error("Error creating Review", e)
        response.status = 500
        result.result = 'ERROR'
        result.message = "There was an error creating the request."
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 400
      result.errors = errors
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
        response.status = 403
        result.message = "User must belong to at least one curatory group of an existing title to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.status = 404
      result.message = "ReviewRequest not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.status = 403
      result.message = "User is not allowed to delete this request!"
    }
    render result as JSON
  }


  /**
   * Check if the ReviewRequest given by @params.id can be escalated.
   */
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  def isEscalatable() {
    def result = [result: 'OK']
    ReviewRequest obj = ReviewRequest.findById(params.id)

    if (obj) {
      result = getEscalationTargetGroupId(obj, params.activeGroupId, params)
    }
    else {
      response.status = 404
      result.result = 'ERROR'
      result.message = "Unable to lookup request object."
      result.error = [object: [[message: "Unable to lookup request object by id.", code: '404', value: params.id]]]
    }

    render result as JSON
  }


  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def escalate() {
    def result = [result: 'OK']
    ReviewRequest obj = ReviewRequest.findById(params.id)
    User user = User.get(springSecurityService.principal.id)
    CuratoryGroup escalatingGroup = CuratoryGroup.findById(request.JSON?.activeGroup)

    if (obj && escalatingGroup) {
      result = getEscalationTargetGroupId(obj, escalatingGroup.id, params)

      if (result.isEscalatable){
        if (user.curatoryGroups.contains(escalatingGroup)) {
          AllocatedReviewGroup arg = AllocatedReviewGroup.findByGroupAndReview(escalatingGroup, ReviewRequest.findById(params.id))
          AllocatedReviewGroup newArg = reviewRequestService.escalate(arg, CuratoryGroup.findById(result.escalationTargetGroup.id))

          if (newArg){
            ReviewRequest rr = ReviewRequest.get(genericOIDService.oidToId(params.id))
            def inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
            rr.allocatedGroups.each { ag ->
              ag.status = inactive
              ag.save()
            }
            newArg.status = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
            result.message = "The requested ReviewRequest has been escalated."
          }
          else{
            result.message = "All preconditions for an escalation have been met. Could not escalate anyway."
            result.result = 'ERROR'
            response.status = 500
          }
        }
        else {
          response.status = 403
          result.result = 'ERROR'
          result.message = "User does not belong to the active group."
        }
      }
    }
    else {
      response.status = 404
      result.result = 'ERROR'

      if (!obj) {
        result.message = "Unable to lookup request object."
        result.error = [object: [[message: "Unable to lookup request object by id.", code: '404', value: params.id]]]
      }
      else {
        result.message = "Unable to lookup active group."
        result.error = [activeGroup: [[message: "Unable to lookup active group object by id.", code: '404', value: request.JSON?.activeGroup]]]
      }
    }

    render result as JSON
  }


  /**
   * Check if the ReviewRequest given by @params.id can be deescalated.
   */
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  def isDeescalatable() {
    def result = [result: 'OK']
    ReviewRequest obj = ReviewRequest.findById(params.id)

    if (obj) {
      result = getDeescalationTargetGroupId(obj, params.activeGroupId, params)
    }
    else {
      response.status = 404
      result.result = 'ERROR'
      result.message = "Unable to lookup request object."
      result.errors = [object: [[message: "Unable to lookup request object by id.", code: '404', value: params.id]]]
    }

    render result as JSON
  }


  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def deescalate(){
    def result = [result: 'OK']
    User user = User.get(springSecurityService.principal.id)
    ReviewRequest obj = ReviewRequest.findById(params.id)
    CuratoryGroup deescalatingGroup = CuratoryGroup.findById(request.JSON?.activeGroup)

    if (obj && deescalatingGroup) {
      result = getDeescalationTargetGroupId(obj, request.JSON?.activeGroup, params)

      if (result.isDeescalatable){
        if (user.curatoryGroups.contains(deescalatingGroup)) {
          AllocatedReviewGroup deescArg = AllocatedReviewGroup.findByGroupAndReview(deescalatingGroup, obj)
          AllocatedReviewGroup targetArg = deescArg?.escalatedFrom ?: null

          if (deescArg && targetArg){
            def inactive = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'Inactive')
            def inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
            deescArg.status = inactive
            deescArg.escalatedFrom = null
            deescArg.save()
            targetArg.status = inProgress
            targetArg.save()
            response.status = 200
          }
          else{
            result.result = 'ERROR'
            response.status = 400
          }
        }
        else {
          response.status = 403
          result.result = 'ERROR'

        }
      }
    }
    else {
      response.status = 404
      result.result = 'ERROR'

      if (!obj) {
        result.message = "Unable to lookup request object."
        result.errors = [object: [[message: "Unable to lookup request object by id.", code: '404', value: params.id]]]
      }
      else {
        result.message = "Unable to lookup active group."
        result.errors = [activeGroup: [[message: "Unable to lookup active group object by id.", code: '404', value: request.JSON?.activeGroup]]]
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
        response.status = 403
        result.message = "Unable to change ${params['_field']} for ${report.error} of ${report.total} items."
      } else {
        result.report = report
        result.message = "Successfully changed ${params['_field']} for ${report.total} items."
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 400
      result.message = "Missing required params '_field' and '_value'"
    }
    render result as JSON
  }


  private def generateLinks(obj,user) {
    def base = grailsApplication.config.getProperty('serverURL', String, "") + "/rest" + obj.restPath + "/${obj.id}"
    def linksObj = [self:[href:base]]
    def curator = componentUpdateService.isUserCurator(obj, user)

    if (curator || user.isAdmin()) {
      linksObj.update = [href:base]
      linksObj.delete = [href:base]
    }
    return linksObj
  }


  private def getEscalationTargetGroupId(rr, def activeGroupId, def params) {
    def result = ['result':'OK', 'isEscalatable':false, 'params': params]

    if (!rr) {
      response.status = 404
      result.result = 'ERROR'
      result.message = "ReviewRequest not found for id ${rrId}."
      return result
    }

    CuratoryGroup escalatingGroup = null

    if (activeGroupId) {
      CuratoryGroup toBeChecked = CuratoryGroup.findById(activeGroupId)
      RefdataValue inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')
      List<AllocatedReviewGroup> argCandidates = AllocatedReviewGroup.findAllByGroupAndReviewAndStatus(toBeChecked, rr, inProgress)

      if (argCandidates.size() == 1) {
        escalatingGroup = toBeChecked
        result.escalatingGroup = [id: escalatingGroup.id, name: escalatingGroup.name, uuid: escalatingGroup.uuid]
      }
      else if (argCandidates.size() > 1) {
        response.status = 409
        result.result = 'ERROR'
        result.message = "Could not get curatory group to be escalated from due to multiple group candidates."
        return result
      }
      else{
        response.status = 404
        result.result = 'ERROR'
        result.message = "Could not get curatory group to be escalated from due to missing active curatory group."
        return result
      }
    }
    else{
      response.status = 404
      result.result = 'ERROR'
      result.message = "Could not get curatory group to be escalated from due to missing active curatory group id."
      return result
    }

    String componentClass = rr.componentToReview?.class.getSimpleName()
    def centralGroup = grailsApplication.config.getProperty("gokb.centralGroups.$componentClass")

    CuratoryGroup editorialGroup = centralGroup ? CuratoryGroup.findByNameIlike(centralGroup) : null
    CuratoryGroup escalatedToCG = escalatingGroup.superordinatedGroup

    if (!escalatedToCG && editorialGroup && escalatingGroup != editorialGroup) {
      escalatedToCG = editorialGroup
    }

    if (escalatedToCG) {
      result.escalationTargetGroup = [id: escalatedToCG.id, name: escalatedToCG.name, uuid: escalatedToCG.uuid]
    }
    else {
      result.message = "There is no superordinated/editorial group to escalate to."
      return result
    }


    // check if the type of the linked component allows for escalation
    if (!componentClass || !(componentClass in [BookInstance.class.simpleName, DatabaseInstance.class.simpleName, JournalInstance.class.simpleName, OtherInstance.class.simpleName, TitleInstancePackagePlatform.class.simpleName, Package.class.simpleName])) {
      result.message = "ReviewRequest belongs to the un-escalatable class ${componentClass}"
      return result
    }

    // all conditions are met
    result.isEscalatable = true
    result.message = "The requested ReviewRequest can be escalated."
    return result
  }

  private def getDeescalationTargetGroupId(rr, def activeGroupId, def params) {
    def result = ['result':'OK', 'isDeescalatable': false, 'params': params]

    CuratoryGroup deescalatingGroup = CuratoryGroup.findById(activeGroupId)

    if (!deescalatingGroup) {
      response.status = 404
      result.result = 'ERROR'
      result.message = "Could not get curatory group to check for deescalation privileges."
      return result
    }

    AllocatedReviewGroup deescArg, targetArg
    deescArg = AllocatedReviewGroup.findByGroupAndReview(deescalatingGroup, rr)
    targetArg = deescArg?.escalatedFrom ?: null

    if (deescArg && targetArg) {
      result.isDeescalatable = true
      result.deescalatingGroup = [id: deescalatingGroup.id, name: deescalatingGroup.name, uuid: deescalatingGroup.uuid]
      result.escalationTargetGroup = [id: targetArg.group.id, name : targetArg.group.name, uuid: targetArg.group.uuid]
      result.message = "The requested ReviewRequest can be deescalated."
    }
    else if (!deescArg) {
      result.message = "The active group is not assigned to this review."
    }
    else if (!targetArg) {
      result.deescalatingGroup = [id: deescalatingGroup.id, name: deescalatingGroup.name, uuid: deescalatingGroup.uuid]
      result.message = "Could not find a target to deescalate to."
    }
    return result
  }
}
