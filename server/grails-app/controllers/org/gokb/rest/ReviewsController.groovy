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
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    User user = User.get(springSecurityService.principal.id)
    result = componentLookupService.restLookup(user, ReviewRequest, params)

    if (result.result == 'ERROR') {
      response.status = (result.status ?: 500)
    }

    render result as JSON
  }

  @Secured(['ROLE_CONTRIBUTOR', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def obj = ReviewRequest.get(genericOIDService.oidToId(params.id))
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
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
      def curator = componentUpdateService.isUserCurator(obj, user)

      if (curator || user.isAdmin()) {
        if (reqBody.version && obj.version > Long.valueOf(reqBody.version)) {
          response.status = 409
          result.message = message(code: "default.update.errors.message")
          render result as JSON
          return
        }

        def update_result = reviewRequestService.restUpdate(obj, reqBody)

        if (update_result.result == 'OK') {
          result = restMappingService.mapObjectToJson(obj, params, user)
          result.additionalInfo = obj.additional

          result._links = generateLinks(obj, user)
        }
        else {
          result.result = 'ERROR'
          result.errors = update_result.errors
          response.status = 400
          result.message = message(code: "default.update.errors.message")
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
    Map result = [result:'OK', params: params]
    def reqBody = request.JSON
    Map errors = [:]
    RefdataValue type_ext = RefdataCategory.lookup('ReviewRequest.StdDesc', 'External Editorial Request')
    User user = User.get(springSecurityService.principal.id)
    CuratoryGroup editorialTargetGroup
    ReviewRequest obj
    Map pars = [:]

    if (reqBody.reviewRequest) {
      pars.reviewRequest = reqBody.reviewRequest.trim()
    }

    if (reqBody.descriptionOfCause) {
      pars.descriptionOfCause = reqBody.descriptionOfCause.trim()
    }

    if (reqBody.editingNotes) {
      pars.editingNotes = reqBody.editingNotes.trim()
    }

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

    if (reqBody.targetGroup) {
      if (pars.stdDesc == type_ext) {
        editorialTargetGroup = CuratoryGroup.findById(reqBody.targetGroup)
        List external_groups = []

        CuratoryGroup zdb_admin = grailsApplication.config.getProperty("gokb.zdbAugment.rrCurators") ? CuratoryGroup.findByNameIlike(grailsApplication.config.getProperty("gokb.zdbAugment.rrCurators")) : null
        CuratoryGroup ezb_admin = grailsApplication.config.getProperty("gokb.ezbAugment.rrCurators") ? CuratoryGroup.findByNameIlike(grailsApplication.config.getProperty("gokb.ezbAugment.rrCurators")) : null

        if (zdb_admin) {
          external_groups << zdb_admin
        }
        if (ezb_admin) {
          external_groups << ezb_admin
        }

        if (!editorialTargetGroup) {
          errors.targetGroup = [
            [
              message: 'Unable to reference target group!',
              baddata: reqBody.targetGroup
            ]
          ]
        }
        else if (!external_groups.contains(editorialTargetGroup)) {
          errors.targetGroup = [
            [
              message: 'Provided targetGroup is not configured as augment editorial group!',
              baddata: reqBody.targetGroup
            ]
          ]
        }
      }
      else {
        log.debug("Ignoring manual target group for review of type ${pars.stdDesc}..")
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
            editorialTargetGroup ?: componentLookupService.findCuratoryGroupOfInterest(pars.componentToReview, user, reqBody.activeGroup)
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
        result.message = "There was an error creating the request for review."
      }
    }
    else {
      result.result = 'ERROR'
      response.status = 400
      result.errors = errors

      if (!result.message) {
        result.message = 'There have been errors creating the request for review.'
      }
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_ADMIN')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def transfer() {
    Map result = [result: 'OK', params: params]
    def reqBody = request.JSON

    ReviewRequest obj = ReviewRequest.get(genericOIDService.oidToId(params.id))
    CuratoryGroup target = CuratoryGroup.get(reqBody.target)

    if (obj && target) {
      AllocatedReviewGroup.removeAll(obj)
      AllocatedReviewGroup.create(target, obj, true)
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Unable to reference review by ID ${params.id}!"
      result.messageCode
    }
    else if (!target) {
      result.result = 'ERROR'
      response.status = 404
      result.message = "Unable to reference target group by ID ${reqBody.target}!"
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    def result = ['result':'OK', 'params': params]
    User user = User.get(springSecurityService.principal.id)
    ReviewRequest obj = ReviewRequest.get(genericOIDService.oidToId(params.id))

    if ( obj && obj.isDeletable() ) {
      def curator = componentUpdateService.isUserCurator(obj, user)

      if ( curator || user.isAdmin() ) {
        obj.status = RefdataCategory.lookup('ReviewRequest.Status','Deleted')
        obj.save()
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
    def result = [
      result: 'OK',
      isEscalatable: true,
      escalationTargetGroup: null
    ]
    Map errors = [:]
    ReviewRequest obj = ReviewRequest.findById(params.id)
    User user = User.get(springSecurityService.principal.id)
    CuratoryGroup activeGroup

    if (params.activeGroupId) {
      activeGroup = CuratoryGroup.get(params.activeGroupId)

      if (!activeGroup) {
        response.status = 400
        errors['activeGroupId'] = [
          [
            message: "Unable to lookup activeGroup.",
            code: 404,
            value: params.activeGroupId
          ]
        ]
      }
      else {
        if (!user.curatoryGroups.contains(activeGroup) && !user.isAdmin()) {
          errors['activeGroupId'] = [
            [
              message: "User is not permitted to escalate from activeGroup!",
              code: 403,
              value: params.activeGroupId
            ]
          ]
        }
      }
    }

    if (!obj) {
      response.status = 404
      errors['id'] = [
        [
          message: "Unable to lookup request object by id.",
          code: 404,
          value: params.id
        ]
      ]
    }

    if (errors) {
      result.result = 'ERROR'
      result.message = "There have been errors checking the escalation status!"
      result.isEscalatable = false
      result.errors = errors
    }
    else if (activeGroup) {
      result = reviewRequestService.checkEscalationForActiveGroup(obj, activeGroup)
    }
    else {
      result.isEscalatable = false
    }

    result.params = params

    render result as JSON
  }


  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def escalate() {
    def result = [result: 'OK']
    def reqBody = request.JSON
    ReviewRequest obj = ReviewRequest.findById(params.id)
    User user = User.get(springSecurityService.principal.id)
    CuratoryGroup escalatingGroup = CuratoryGroup.findById(reqBody?.activeGroup)

    if (obj && escalatingGroup) {
      result = reviewRequestService.checkEscalationForActiveGroup(obj, escalatingGroup)

      if (result.code) {
        response.status = result.code
      }

      if (result.isEscalatable) {
        if (user.curatoryGroups.contains(escalatingGroup) || user.isAdmin()) {
          CuratoryGroup new_group = CuratoryGroup.findById(result.escalationTargetGroup.id)
          AllocatedReviewGroup new_arg = reviewRequestService.escalate(obj, escalatingGroup, new_group)

          if (new_arg){
            result.message = "The ReviewRequest has been escalated."
          }
          else {
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
        result.error = [
          object: [
            [
              message: "Unable to lookup request object by id.",
              code: '404',
              value: params.id
            ]
          ]
        ]
      }
      else {
        result.message = "Unable to lookup active group."
        result.error = [
          activeGroup: [
            [
              message: "Unable to lookup active group object by id.",
              code: '404',
              value: reqBody?.activeGroup
            ]
          ]
        ]
      }
    }

    result.params = params

    render result as JSON
  }


  /**
   * Check if the ReviewRequest given by @params.id can be deescalated.
   */
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  def isDeescalatable() {
    def result = [
      result: 'OK',
      isDeescalatable: true
    ]
    Map errors = [:]
    User user = User.get(springSecurityService.principal.id)
    ReviewRequest obj = ReviewRequest.findById(params.id)
    CuratoryGroup activeGroup

    if (params.activeGroupId) {
      activeGroup = CuratoryGroup.get(params.activeGroupId)

      if (!activeGroup) {
        response.status = 400
        result.result = 'ERROR'
        errors['activeGroupId'] = [
          [
            message: "Unable to lookup activeGroup.",
            code: 404,
            value: params.activeGroupId
          ]
        ]
      }
      else {
        if (!user.curatoryGroups.contains(activeGroup) && !user.isAdmin()) {
          errors['activeGroupId'] = [
            [
              message: "User is not permitted to deescalate from activeGroup!.",
              code: 403,
              value: params.activeGroupId
            ]
          ]
        }
      }
    }

    if (!obj) {
      response.status = 404
      result.result = 'ERROR'
      errors['id'] = [
        [
          message: "Unable to lookup request object by id.",
          code: 404,
          value: params.id
        ]
      ]
    }

    if (errors) {
      result.message = "This review cannot be deescalated for this group."
      result.errors = errors
      result.isDeescalatable = false
    }
    else if (activeGroup) {
      result = reviewRequestService.checkDeescalationForActiveGroup(obj, activeGroup)
    }
    else {
      result.isDeescalatable = false
    }

    result.params = params

    render result as JSON
  }


  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def deescalate(){
    def result = [result: 'OK']
    User user = User.get(springSecurityService.principal.id)
    def reqBody = request.JSON
    ReviewRequest obj = ReviewRequest.findById(params.id)
    CuratoryGroup deescalatingGroup = CuratoryGroup.findById(reqBody?.activeGroup)

    if (obj && deescalatingGroup) {
      result = reviewRequestService.checkDeescalationForActiveGroup(obj, deescalatingGroup)

      if (result.code) {
        response.status = result.code
      }

      if (result.isDeescalatable){
        if (user.curatoryGroups.contains(deescalatingGroup) || user.isAdmin()) {
          AllocatedReviewGroup targetArg = reviewRequestService.deescalate(obj, deescalatingGroup)

          if (!targetArg) {
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
        result.errors = [
          object: [
            [
              message: "Unable to lookup request object by id.",
              code: '404',
              value: params.id
            ]
          ]
        ]
      }
      else {
        result.message = "Unable to lookup active group."
        result.errors = [
          activeGroup: [
            [
              message: "Unable to lookup active group object by id.",
              code: '404',
              value: reqBody?.activeGroup
            ]
          ]
        ]
      }
    }

    result.params = params

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
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest" + obj.restPath + "/${obj.id}"
    def linksObj = [self:[href:base]]
    def curator = componentUpdateService.isUserCurator(obj, user)

    if (curator || user.isAdmin()) {
      linksObj.update = [href:base]
      linksObj.delete = [href:base]
    }
    return linksObj
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  def editorialGroups() {
    def result = [
      external: [],
      typed: [:]
    ]

    List errors = []
    List external_groups = []

    CuratoryGroup zdb_admin = grailsApplication.config.getProperty("gokb.zdbAugment.rrCurators") ? CuratoryGroup.findByNameIlike(grailsApplication.config.getProperty("gokb.zdbAugment.rrCurators")) : null
    CuratoryGroup ezb_admin = grailsApplication.config.getProperty("gokb.ezbAugment.rrCurators") ? CuratoryGroup.findByNameIlike(grailsApplication.config.getProperty("gokb.ezbAugment.rrCurators")) : null

    if (zdb_admin) {
      external_groups.add(zdb_admin)
    }

    if (ezb_admin) {
      external_groups.add(ezb_admin)
    }

    external_groups.each {
      result.external << restMappingService.mapObjectToJson(it, [:])
    }

    Map typed_groups = grailsApplication.config.getProperty("gokb.centralGroups", Map, [:])

    typed_groups.each { type, val ->
      CuratoryGroup tg = CuratoryGroup.findByName(val)

      if (tg) {
        result.typed[type] = restMappingService.mapObjectToJson(tg, [:])
      }
      else {
        errors << [message: "Unable to reference configured central group ${val}!"]
      }
    }

    render result as JSON
  }
}
