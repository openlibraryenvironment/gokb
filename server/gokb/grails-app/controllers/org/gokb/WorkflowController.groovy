package org.gokb

import org.gokb.cred.*
import grails.plugins.springsecurity.Secured

class WorkflowController {

  def genericOIDService

  def actionConfig = [
    'object::statusDeleted':[actionType:'simple'],
    'title::transfer':      [actionType:'workflow', view:'titleTransfer']
  ];

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

  def processTitleChange() {
    log.debug("processTitleChange");
    def result = [:]
    result.titles = []
    result.tipps = []
    result.newtipps = [:]

    params.each { p ->
      if ( ( p.key.startsWith('tt:') ) && ( p.value ) && ( p.value instanceof String ) ) {
        def tt = p.key.substring(3);
        log.debug("Title to transfer: \"${tt}\"");
        def title_instance = TitleInstance.get(tt)
        // result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
        // Find all tipps for the title and add to tipps
        if ( title_instance ) {
          result.titles.add(title_instance)
          title_instance.tipps.each { tipp ->
            result.tipps.add(tipp)
          }
        }
        else {
          log.error("Unable to locate title with that ID");
        }
      }
    }
    result.newPublisher = genericOIDService.resolveOID2(params.title)
    
    result
  }

  def processTitleChangeTipps() {
  }
}
