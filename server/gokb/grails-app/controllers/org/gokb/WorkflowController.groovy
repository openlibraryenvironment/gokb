package org.gokb

import org.gokb.cred.*
import grails.plugins.springsecurity.Secured
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

class WorkflowController {

  def genericOIDService
  def springSecurityService

  def actionConfig = [
    'object::statusDeleted':[actionType:'simple'],
    'title::transfer':      [actionType:'workflow', view:'titleTransfer']
  ];

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def action() { 
    log.debug("WorkflowController::action(${params})");
    def result = [:]
    result.ref=request.getHeader('referer')
    def action_config = actionConfig[params.selectedBulkAction];
    if ( action_config ) {

      result.objects_to_action = []

      params.each { p ->
        if ( ( p.key.startsWith('bulk:') ) && ( p.value ) && ( p.value instanceof String ) ) {
          def oid_to_action = p.key.substring(5);
          log.debug("Action oid: ${oid_to_action}");
          result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
        }
      }

      switch ( action_config.actionType ) {
        case 'simple': 
          // Do stuff
          redirect(url: result.ref)
          break;
        case 'workflow':
          render view:action_config.view, model:result
          break;
        default:
          flash.message="Invalid action type information: ${action_config.actionType}";
          break;
      }
    }
    else {
      flash.message="Unable to locate action config for ${params.selectedBulkAction}";
      log.warn("Unable to locate action config for ${params.selectedBulkAction}");
      redirect(url: result.ref)
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processTitleChange() {
    log.debug("processTitleChange");
    def user = springSecurityService.currentUser
    def result = [:]
    result.titles = []
    result.tipps = []
    result.newtipps = [:]

    def titleTransferData = [:]
    titleTransferData.title_ids = []
    titleTransferData.tipps = [:]

    params.each { p ->
      if ( ( p.key.startsWith('tt:') ) && ( p.value ) && ( p.value instanceof String ) ) {
        def tt = p.key.substring(3);
        log.debug("Title to transfer: \"${tt}\"");
        def title_instance = TitleInstance.get(tt)
        // result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
        // Find all tipps for the title and add to tipps
        if ( title_instance ) {
          result.titles.add(title_instance) 
          titleTransferData.title_ids.add(title_instance.id)
          title_instance.tipps.each { tipp ->
            result.tipps.add(tipp)
            titleTransferData.tipps[tipp.id] = [newtipps:[]]
          }
        }
        else {
          log.error("Unable to locate title with that ID");
        }
      }
    }

    result.newPublisher = genericOIDService.resolveOID2(params.title)
    titleTransferData.newPublisherId = result.newPublisher.id

    def builder = new JsonBuilder()
    builder(titleTransferData)

    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save()
    def transfer_type = RefdataCategory.lookupOrCreate('Activity.Type', 'TitleTransfer').save()


    def new_activity = new Activity(activityData:builder.toString(),
                                    owner:user,
                                    status:active_status, 
                                    type:transfer_type).save()
    
    redirect(action:'editTitleTransfer',id:new_activity.id)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def editTitleTransfer() {
    def result = [:]
    result.titles = []
    result.tipps = []
    result.newtipps = [:]

    def activity_record = Activity.get(params.id)
    def activity_data = new JsonSlurper().parseText(activity_record.activityData)

    activity_data.title_ids.each { tid ->
      result.titles.add(TitleInstance.get(tid))
    }

    activity_data.tipps.each { tipp ->
      result.tipps.add(TitleInstancePackagePlatform.get(tipp.key))
    }

    result.newPublisher = Org.get(activity_data.newPublisherId)

    result
  }
}
