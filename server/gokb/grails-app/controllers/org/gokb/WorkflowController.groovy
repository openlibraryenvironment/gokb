package org.gokb

import org.gokb.cred.*
import grails.plugins.springsecurity.Secured
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import grails.converters.JSON

class WorkflowController {

  def grailsApplication
  def genericOIDService
  def springSecurityService

  def actionConfig = [
    'method::deleteSoft':[actionType:'simple'],
    'title::transfer':      [actionType:'workflow', view:'titleTransfer'],
    'title::change':      [actionType:'workflow', view:'titleChange'],
    'platform::replacewith':[actionType:'workflow', view:'platformReplacement'],
    'method::registerWebhook':[actionType:'workflow', view:'registerWebhook'],
    'method::RRTransfer':[actionType:'workflow', view:'revReqTransfer'],
    'method::RRClose':[actionType:'simple' ],
    'title::reconcile':[actionType:'workflow', view:'titleReconcile' ],
    'exportPackage':[actionType:'process', method:'packageTSVExport']
  ];

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def action() { 
    log.debug("WorkflowController::action(${params})");
    def result = [:]
    result.ref=request.getHeader('referer')

    def action_config = actionConfig[params.selectedBulkAction];

    if ( action_config ) {

      result.objects_to_action = []

      if ( params.batch_on == 'all' ) {
        log.debug("Requested batch_on all.. so evaluate the query and do the right thing...");
        if ( params.qbe ) {
          def qresult = [:]
          if ( params.qbe.startsWith('g:') ) {
            // Global template, look in config
            def global_qbe_template_shortcode = params.qbe.substring(2,params.qbe.length());
            // log.debug("Looking up global template ${global_qbe_template_shortcode}");
            qresult.qbetemplate = grailsApplication.config.globalSearchTemplates[global_qbe_template_shortcode]
            // log.debug("Using template: ${result.qbetemplate}");
          }

          // Looked up a template from somewhere, see if we can execute a search
          if ( qresult.qbetemplate ) {
            log.debug("Execute query");
            // doQuery(result.qbetemplate, params, result)
            def target_class = grailsApplication.getArtefact("Domain",qresult.qbetemplate.baseclass);
            com.k_int.HQLBuilder.build(grailsApplication, qresult.qbetemplate, params, qresult, target_class, genericOIDService)

            qresult.recset.each {
              def oid_to_action = "${it.class.name}:${it.id}"
              log.debug("Action oid: ${oid_to_action}");
              result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
            }
          }
        }
      }
      else {
        log.debug("Assuming standard selection of rows to action");
        params.each { p ->
          if ( ( p.key.startsWith('bulk:') ) && ( p.value ) && ( p.value instanceof String ) ) {
            def oid_to_action = p.key.substring(5);
            log.debug("Action oid: ${oid_to_action}");
            result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
          }
        }
      }

      switch ( action_config.actionType ) {
        case 'simple':
          
          def method_config = params.selectedBulkAction.split(/\:\:/) as List
          
          switch (method_config[0]) {
            
            case "method" : 

              def context = [ user:request.user, params:params ]
          
              // Everything after the first 2 "parts" are args for the method.
              def method_params = []

              method_params.add(context)

              if (method_config.size() > 2) {
                method_params.addAll(method_config.subList(2, method_config.size()))
              }

              // We should just call the method on the targets.
              result.objects_to_action.each {def target ->
                
                log.debug ("Attempting to fire method ${method_config[1]} (${method_params})")
                
                // Wrap in a transaction.
                KBComponent.withNewTransaction {def trans_status ->
                  try {
                    
                    // Just try and fire the method.
                    target.invokeMethod("${method_config[1]}", method_params ? method_params as Object[] : null)
                    
                    // Save the object.
                    target.save(failOnError:true)
                  } catch (Throwable t) {
                  
                    // Rollback and log error.
                    trans_status.setRollbackOnly()
                    t.printStackTrace()
                    log.error(t)
                  }
                }
              }
              break
          }
          // Do stuff
          redirect(url: result.ref)
          break;
        case 'workflow':
          render view:action_config.view, model:result
          break;
        case 'process':
          this."${action_config.method}"(result.objects_to_action);
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
  def startTitleChange() {

    log.debug("startTitleChange(${params})");

    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save()
    def transfer_type = RefdataCategory.lookupOrCreate('Activity.Type', 'TitleChange').save()

    def titleChangeData = [:]
    titleChangeData.tipps = [:]
    titleChangeData.beforeTitles = params.list('beforeTitles')
    titleChangeData.afterTitles = params.list('afterTitles')
    titleChangeData.eventDate = params.list('eventDate')
    def first_title = null

    def sw = new StringWriter();

    // Iterate through before titles.. For each one of these will will close out any existing tipps
    params.list('beforeTitles').each { title_oid ->
      log.debug("process ${title_oid}");
      if ( first_title == null ) {
        first_title = title_oid
      }
      else {
        sw.write(', ');
      }

      def title_obj = genericOIDService.resolveOID2(title_oid)
      sw.write(title_obj.name);

      def tipps = TitleInstancePackagePlatform.executeQuery(
                         'select tipp from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp  and tipp.status.value <> ? and c.type.value = ?',
                         [title_obj, 'Deleted','TitleInstance.Tipps']);
      tipps.each { tipp ->
        log.debug("Add tipp to discontinue ${tipp}");
        titleChangeData.tipps[tipp.id] = [newtipps:[]]

        params.list('afterTitles').each { new_title_oid ->
          def new_title_obj = genericOIDService.resolveOID2(new_title_oid)
          def new_tipp_info = [
                               title_id:new_title_obj.id,
                               package_id:tipp.pkg.id,
                               platform_id:tipp.hostPlatform.id,
                               startDate:tipp.startDate ? sdf.format(tipp.startDate) : null,
                               startVolume:tipp.startVolume,
                               startIssue:tipp.startIssue,
                               endDate:tipp.endDate? sdf.format(tipp.endDate) : null,
                               endVolume:tipp.endVolume,
                               endIssue:tipp.endIssue]
          titleChangeData.tipps[tipp.id].newtipps.add(new_tipp_info)
        }
      }
    }

    def builder = new JsonBuilder()
    builder(titleChangeData)

    def new_activity = new Activity(
                                    activityName:"Title Change ${sw.toString()}",
                                    activityData:builder.toString(),
                                    owner:request.user,
                                    status:active_status, 
                                    type:transfer_type).save()

    log.debug("redirect to edit activity (Really title) ${builder.toString()}");
    
    // if ( first_title )
    //   redirect(controller:'resource', action:'show', id:first_title);
    // else
    //   redirect(controller:'home', action:'index');

    redirect(action:'editTitleChange',id:new_activity.id)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def startTitleTransfer() {
    log.debug("startTitleTransfer");
    def user = springSecurityService.currentUser
    def result = [:]
    result.titles = []
    result.tipps = []
    result.newtipps = [:]

    def titleTransferData = [:]
    titleTransferData.title_ids = []
    titleTransferData.tipps = [:]

    def sw = new StringWriter()

    boolean first = true
    params.each { p ->
      if ( ( p.key.startsWith('tt:') ) && ( p.value ) && ( p.value instanceof String ) ) {
        def tt = p.key.substring(3);
        log.debug("Title to transfer: \"${tt}\"");
        def title_instance = TitleInstance.get(tt)
        // result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
        // Find all tipps for the title and add to tipps
        if ( title_instance ) {
          if ( first == true ) {
            first=false
          }
          else {
            sw.write(", ");
          }

          sw.write(title_instance.name);

          result.titles.add(title_instance) 
          titleTransferData.title_ids.add(title_instance.id)
          title_instance.tipps.each { tipp ->
            if ( tipp.status?.value != 'Deleted' ) {
              result.tipps.add(tipp)
              titleTransferData.tipps[tipp.id] = [newtipps:[]]
            }
          }
        }
        else {
          log.error("Unable to locate title with that ID");
        }
      }
    }

    log.debug("loaded Title Data");

    result.newPublisher = genericOIDService.resolveOID2(params.title)
    titleTransferData.newPublisherId = result.newPublisher.id

    log.debug("Build title transfer record");
    def builder = new JsonBuilder()
    builder(titleTransferData)

    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save()
    def transfer_type = RefdataCategory.lookupOrCreate('Activity.Type', 'TitleTransfer').save()


    log.debug("Create activity");
    def new_activity = new Activity(
                                    activityName:"Title transfer ${sw.toString()} to ${result.newPublisher.name}",
                                    activityData:builder.toString(),
                                    owner:user,
                                    status:active_status, 
                                    type:transfer_type).save()
    
    log.debug("Redirect to edit title transfer activity");
    redirect(action:'editTitleTransfer',id:new_activity.id)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def editTitleTransfer() {
    log.debug("editTitleTransfer() - ${params}");

    // def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
    def activity_record = Activity.get(params.id)
    def activity_data = new JsonSlurper().parseText(activity_record.activityData)

    // Pull in all updated tipp properties like start volumes, etc.
    request.getParameterNames().each { pn ->
      def value = request.getParameter(pn)
      if ( pn.startsWith('_tippdata') ) {
        def key_components = pn.split(':');
        if (  activity_data.tipps[key_components[1]] != null ) {
          if ( ( value != null ) && ( value.length() > 0 ) ) {
            activity_data.tipps[key_components[1]].newtipps[Integer.parseInt(key_components[2])][key_components[3]] = value
          }
          else {
            activity_data.tipps[key_components[1]].newtipps[Integer.parseInt(key_components[2])][key_components[3]] = null
          }
        }
        else {
          log.error("Unable to locate data for tipp ${key_components[1]} in ${activity_data}");
        }
      }
    }

    if ( params.addTransferTipps ) {
      // Add Transfer tipps
      log.debug("Add transfer tipps");
      if ( ( params.Package != null ) && ( params.Platform != null ) ) {
        def new_tipp_package = genericOIDService.resolveOID2(params.Package);
        def new_tipp_platform = genericOIDService.resolveOID2(params.Platform);
        if ( ( new_tipp_package != null ) && ( new_tipp_platform != null ) ) {
          params.each { p ->
            if ( p.key.startsWith('addto-') ) {
              def tipp_id = p.key.substring(6)
              log.debug("Add new tipp for ${new_tipp_package}, ${new_tipp_platform} to replace ${tipp_id}");
              def old_tipp = KBComponent.get(tipp_id);
              log.debug("Old Tipp: ${old_tipp}");
              def tipp_info = activity_data.tipps[tipp_id]

              if ( tipp_info != null ) {

                if ( tipp_info.newtipps == null )
                  tipp_info.newtipps = [:]

                def new_tipp_info = [
                                        title_id:old_tipp.title.id, 
                                        package_id:new_tipp_package.id, 
                                        platform_id:new_tipp_platform.id,
                                        startDate:old_tipp.startDate ? sdf.format(old_tipp.startDate) : null,
                                        startVolume:old_tipp.startVolume,
                                        startIssue:old_tipp.startIssue,
                                        endDate:old_tipp.endDate? sdf.format(old_tipp.endDate) : null,
                                        endVolume:old_tipp.endVolume,
                                        endIssue:old_tipp.endIssue]
                log.debug("new_tipp_info :: ${new_tipp_info}");
                tipp_info.newtipps.add(new_tipp_info);
              }
              else {
                log.error("Unable to find key (${tipp_id}) In map: ${activity_data.tipps}");
              }
            }
          }

          // Update the activity data in the database
          def builder = new JsonBuilder()
          builder(activity_data)
          activity_record.activityData = builder.toString();
          activity_record.save()
        }
        else {
          log.error("Add transfer tipps but failed to resolve package(${params.Package}) or platform(${params.Platform})");
        }
      }
      else {
          log.error("Add transfer tipps but package or platform not set");
      }
    }
    else if ( params.update ) {
      log.debug("Update...");
    }
    else if ( params.remove ) {
      log.debug("remove... ${params.remove}");
      def remove_components = params.remove.split(':');
      activity_data.tipps[remove_components[0]].newtipps.remove(Integer.parseInt(remove_components[1]))
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString();
      activity_record.save()
    }
    else if ( params.process ) {
      log.debug("Process...");
      processTitleTransfer(activity_record, activity_data);
      if ( activity_data.title_ids?.size() > 0 ) {
        redirect(controller:'resource',action:'show', id:'org.gokb.cred.TitleInstance:'+activity_data.title_ids[0]);
      }
      else {
        redirect(controller:'home', action:'index');
      }
    }
    else if ( params.abandon ) {
      log.debug("**ABANDON**...");
      activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned')
      activity_record.save()
      if ( activity_data.title_ids?.size() > 0 ) {
        redirect(controller:'resource',action:'show', id:'org.gokb.cred.TitleInstance:'+activity_data.title_ids[0]);
      }
      else {
        redirect(controller:'home', action:'index');
      }
    }

    log.debug("Processing...");

    def result = [:]
    result.titles = []
    result.tipps = []
    result.d = activity_record

    activity_data.title_ids.each { tid ->
      result.titles.add(TitleInstance.get(tid))
    }


    activity_data.tipps.each { tipp_info ->
      def tipp_object = TitleInstancePackagePlatform.get(tipp_info.key)
      result.tipps.add([
                        id:tipp_object.id,
                        type:'CURRENT',
                        title:tipp_object.title, 
                        pkg:tipp_object.pkg, 
                        hostPlatform:tipp_object.hostPlatform,
                        startDate:tipp_object.startDate,
                        startVolume:tipp_object.startVolume,
                        startIssue:tipp_object.startIssue,
                        endDate:tipp_object.endDate,
                        endVolume:tipp_object.endVolume,
                        endIssue:tipp_object.endIssue
                        ])
      int seq=0;
      tipp_info.value.newtipps.each { newtipp_info ->
        result.tipps.add([
                          type:'NEW',
                          parent:tipp_object.id,
                          seq:seq++,
                          title:KBComponent.get(newtipp_info.title_id),
                          pkg:KBComponent.get(newtipp_info.package_id),
                          hostPlatform:KBComponent.get(newtipp_info.platform_id),
                          startDate: newtipp_info.startDate,
                          startVolume:newtipp_info.startVolume,
                          startIssue:newtipp_info.startIssue,
                          endDate:newtipp_info.endDate,
                          endVolume:newtipp_info.endVolume,
                          endIssue:newtipp_info.endIssue,
                          review:newtipp_info.review,
                          ])
      }
    }

    result.newPublisher = Org.get(activity_data.newPublisherId)
    result.id = params.id

    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def editTitleChange() {
    log.debug("editTitleChange() - ${params}");

    // def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
    def activity_record = Activity.get(params.id)
    def activity_data = new JsonSlurper().parseText(activity_record.activityData)

    // Pull in all updated tipp properties like start volumes, etc.
    request.getParameterNames().each { pn ->
      def value = request.getParameter(pn)
      if ( pn.startsWith('_tippdata') ) {
        def key_components = pn.split(':');
        if (  activity_data.tipps[key_components[1]] != null ) {
          if ( ( value != null ) && ( value.length() > 0 ) ) {
            activity_data.tipps[key_components[1]].newtipps[Integer.parseInt(key_components[2])][key_components[3]] = value
          }
          else {
            activity_data.tipps[key_components[1]].newtipps[Integer.parseInt(key_components[2])][key_components[3]] = null
          }
        }
        else {
          log.error("Unable to locate data for tipp ${key_components[1]} in ${activity_data}");
        }
      }
    }

    if ( params.update ) {
      log.debug("Update...");
    }
    else if ( params.remove ) {
      log.debug("remove... ${params.remove}");
      def remove_components = params.remove.split(':');
      activity_data.tipps[remove_components[0]].newtipps.remove(Integer.parseInt(remove_components[1]))
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString();
      activity_record.save()
    }
    else if ( params.process ) {
      log.debug("Process...");
      processTitleChange(activity_record, activity_data);
      if ( activity_data.title_ids?.size() > 0 ) {
        redirect(controller:'resource',action:'show', id:'org.gokb.cred.TitleInstance:'+activity_data.title_ids[0]);
      }
      else {
        redirect(controller:'home', action:'index');
      }
    }
    else if ( params.abandon ) {
      log.debug("**ABANDON**...");
      activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned')
      activity_record.save()
      if ( activity_data.title_ids?.size() > 0 ) {
        redirect(controller:'resource',action:'show', id:'org.gokb.cred.TitleInstance:'+activity_data.title_ids[0]);
      }
      else {
        redirect(controller:'home', action:'index');
      }
    }

    log.debug("Processing...");

    def result = [:]
    result.titles = []
    result.tipps = []
    result.d = activity_record

    activity_data.title_ids.each { tid ->
      result.titles.add(TitleInstance.get(tid))
    }


    activity_data.tipps.each { tipp_info ->
      def tipp_object = TitleInstancePackagePlatform.get(tipp_info.key)
      result.tipps.add([
                        id:tipp_object.id,
                        type:'CURRENT',
                        title:tipp_object.title, 
                        pkg:tipp_object.pkg, 
                        hostPlatform:tipp_object.hostPlatform,
                        startDate:tipp_object.startDate,
                        startVolume:tipp_object.startVolume,
                        startIssue:tipp_object.startIssue,
                        endDate:tipp_object.endDate,
                        endVolume:tipp_object.endVolume,
                        endIssue:tipp_object.endIssue
                        ])
      int seq=0;
      tipp_info.value.newtipps.each { newtipp_info ->
        result.tipps.add([
                          type:'NEW',
                          parent:tipp_object.id,
                          seq:seq++,
                          title:KBComponent.get(newtipp_info.title_id),
                          pkg:KBComponent.get(newtipp_info.package_id),
                          hostPlatform:KBComponent.get(newtipp_info.platform_id),
                          startDate: newtipp_info.startDate,
                          startVolume:newtipp_info.startVolume,
                          startIssue:newtipp_info.startIssue,
                          endDate:newtipp_info.endDate,
                          endVolume:newtipp_info.endVolume,
                          endIssue:newtipp_info.endIssue,
                          review:newtipp_info.review,
                          ])
      }
    }

    result.id = params.id

    result
  }


  def processTitleChange(activity_record, activity_data) {

    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

    activity_data.tipps.each { tipp_map_entry ->
      tipp_map_entry.value.newtipps.each { newtipp ->
        log.debug("Process new tipp : ${newtipp}");

        def new_package = Package.get(newtipp.package_id)
        def new_platform = Platform.get(newtipp.platform_id)

        // def new_tipp = new TitleInstancePackagePlatform(
        def new_tipp = TitleInstancePackagePlatform.tiplAwareCreate([
                                   pkg:new_package,
                                   hostPlatform:new_platform,
                                   title:current_tipp.title,
                                   startDate: ( newtipp.startDate?.length() > 0 ) ? sdf.parse(newtipp.startDate) : null,
                                   startVolume:newtipp.startVolume,
                                   startIssue:newtipp.startIssue,
                                   endDate: (newtipp.endDate?.length() > 0 ) ? sdf.parse(newtipp.endDate) : null,
                                   endVolume:newtipp.endVolume,
                                   endIssue:newtipp.endIssue]).save()

        if ( newtipp.review == 'on' ) {
          ReviewRequest.raise(new_tipp, 'New tipp - please review' , 'A Title change cause this new tipp to be created', request.user)
        }
      }

      // Retire the tipp if
      if ( params["oldtipp_close:${tipp_map_entry.key}"] == 'on' ) {
        log.debug("Retiring old tipp");
        current_tipp.status = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
        if ( params["oldtipp_review:${tipp_map_entry.key}"] == 'on' ) {
          ReviewRequest.raise(current_tipp, 'please review TIPP record' , 'A Title change has affected this tipp [new tipps have been generated]. The user chose to retire this tipp', request.user)
        }
      }
      else {
        if ( params["oldtipp_review:${tipp_map_entry.key}"] == 'on' ) {
          ReviewRequest.raise(current_tipp, 'please review TIPP record' , 'A Title change has affected this tipp [new tipps have been generated]. The user did not retire this tipp', request.user)
        }
      }
    }


    // Default to today if not set
    def event_date = activityData.eventDate ?: sdf.format(new Date());

    // Create title history event
    def newTitleHistoryEvent = new ComponentHistoryEvent(eventDate:sdf.parse(event_date)).save()

    activityData.afterTitles?.each { at ->
      def component = genericOIDService.resolveOID2(at)
      def after_participant = new ComponentHistoryEventParticipant (event:newTitleHistoryEvent,
                                                                    participant:component,
                                                                    participantRole:'out').save()
    }

    activityData.beforeTitles?.each { bt ->
      def component = genericOIDService.resolveOID2(bt)
      def after_participant = new ComponentHistoryEventParticipant (event:newTitleHistoryEvent,
                                                                    participant:component,
                                                                    participantRole:'in').save()
    }


    activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
    activity_record.save()
  }

  def processTitleTransfer(activity_record, activity_data) {
    log.debug("processTitleTransfer ${params}\n\n ${activity_data}");
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

    def publisher = Org.get(activity_data.newPublisherId);

    // Step one : Close off existing title publisher links and create new publisher links
    activity_data.title_ids.each { title_id ->
      log.debug("Process title_id ${title_id} and change publisher to ${publisher}");
      def title = TitleInstance.get(title_id);
      title.changePublisher(publisher)
      title.save()
    }

    // Step two : Process TIPP adjustments
    activity_data.tipps.each { tipp_map_entry ->

      def current_tipp = TitleInstancePackagePlatform.get(tipp_map_entry.key)

      log.debug("Processing current tipp : ${current_tipp.id}");

      tipp_map_entry.value.newtipps.each { newtipp ->
        log.debug("Process new tipp : ${newtipp}");

        def new_package = Package.get(newtipp.package_id)
        def new_platform = Platform.get(newtipp.platform_id)
 
        // def new_tipp = new TitleInstancePackagePlatform(
        def new_tipp = TitleInstancePackagePlatform.tiplAwareCreate([
                                   pkg:new_package,
                                   hostPlatform:new_platform,
                                   title:current_tipp.title,
                                   startDate: ( newtipp.startDate?.length() > 0 ) ? sdf.parse(newtipp.startDate) : null, 
                                   startVolume:newtipp.startVolume,
                                   startIssue:newtipp.startIssue,
                                   endDate: (newtipp.endDate?.length() > 0 ) ? sdf.parse(newtipp.endDate) : null,
                                   endVolume:newtipp.endVolume,
                                   endIssue:newtipp.endIssue]).save()

        if ( newtipp.review == 'on' ) {
          log.debug("User requested a review request be generated for this new tipp");
          ReviewRequest.raise(new_tipp, 'New tipp - please review' , 'A Title transfer cause this new tipp to be created', request.user)
        }
      }

      // Retire the tipp if
      if ( params["oldtipp_close:${tipp_map_entry.key}"] == 'on' ) {
        log.debug("Retiring old tipp");
        current_tipp.status = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
        if ( params["oldtipp_review:${tipp_map_entry.key}"] == 'on' ) {
          ReviewRequest.raise(current_tipp, 'please review TIPP record' , 'A Title transfer has affected this tipp [new tipps have been generated]. The user chose to retire this tipp', request.user)
        }
      }
      else {
        if ( params["oldtipp_review:${tipp_map_entry.key}"] == 'on' ) {
          ReviewRequest.raise(current_tipp, 'please review TIPP record' , 'A Title transfer has affected this tipp [new tipps have been generated]. The user did not retire this tipp', request.user)
        }
      }

      current_tipp.save()
    }

    activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
    activity_record.save()
  }

  def processPackageReplacement() {
    def deleted_status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    params.each { p ->
      log.debug("Testing ${p.key}");
      if ( ( p.key.startsWith('tt') ) && ( p.value ) && ( p.value instanceof String ) ) {
         def tt = p.key.substring(3);
         log.debug("Platform to replace: \"${tt}\"");
         def old_platform = Platform.get(tt)
         def new_platform = genericOIDService.resolveOID2(params.newplatform)

         log.debug("old: ${old_platform} new: ${new_platform}");
         try {
           Combo.executeUpdate("update Combo combo set combo.fromComponent = ? where combo.fromComponent = ?",[new_platform,old_platform]);

           old_platform.status = deleted_status
           old_platform.save(flush:true)
         }
         catch ( Exception e ) {
           log.debug("Problem executing update");
         }
      }
    }
    render view:'platformReplacementResult'
  }

  def download() {
    log.debug("Download ${params}");

    DataFile df = DataFile.findByGuid(params.id)
    if ( df != null ) {
      response.setContentType(df.uploadMimeType)
      response.addHeader("content-disposition", "attachment; filename=\"${df.uploadName}\"")
      def outs = response.outputStream

      def baseUploadDir = grailsApplication.config.baseUploadDir ?: '.'

      log.debug("copyUploadedFile...");
      def deposit_token = df.guid
      def sub1 = deposit_token.substring(0,2);
      def sub2 = deposit_token.substring(2,4);
      def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}";
      def temp_file = new File(temp_file_name);

      org.apache.commons.io.IOUtils.copy(new FileReader(temp_file), outs);

      outs.flush()
      outs.close()
    }
  }

  def authorizeVariant() {
    log.debug(params);
    def result = [:]
    result.ref=request.getHeader('referer')
    def variant = KBComponentVariantName.get(params.id)

    if ( variant != null ) {
      // Does the current owner.name exist in a variant? If not, we should create one so we don't loose the info
      def current_name_as_variant = variant.owner.variantNames.find { it.variantName == variant.owner.name }

      if ( current_name_as_variant == null ) {
        def new_variant = new KBComponentVariantName(owner:variant.owner,variantName:variant.owner.name).save(flush:true);
      }

      variant.owner.name = variant.variantName
      variant.owner.save(flush:true);
    }

    redirect(url: result.ref)
  }

  def deleteVariant() {
    log.debug(params);
    def result = [:]
    result.ref=request.getHeader('referer')
    def variant = KBComponentVariantName.get(params.id)
    if (variant != null ) {
      variant.delete()
    }
    redirect(url: result.ref)
  }

  def processCreateWebHook() {

    log.debug("processCreateWebHook ${params}");

    def result = [:]

    result.ref=params.from

    try {

      def webook_endpoint = null
      if ( ( params.existingHook != null ) && ( params.existingHook.length() > 0 ) ) {
        log.debug("From existing hook");
        webook_endpoint = genericOIDService.resolveOID2(params.existingHook)
      }
      else {
        webook_endpoint = new WebHookEndpoint(name:params.newHookName, 
                                              url:params.newHookUrl,
                                              authmethod:Long.parseLong(params.newHookAuth),
                                              principal:params.newHookPrin,
                                              credentials:params.newHookCred,
                                              owner:request.user)
        if ( webook_endpoint.save(flush:true) ) {
        }
        else {
          log.error("Problem saving new webhook endpoint : ${webook_endpoint.errors}");
        }
      }


      params.each { p ->
        if ( ( p.key.startsWith('tt:') ) && ( p.value ) && ( p.value instanceof String ) ) {
          def tt = p.key.substring(3);
          def wh = new WebHook( oid:tt, endpoint:webook_endpoint)
          if ( wh.save(flush:true) ) {
          }
          else {
            log.error(wh.errors);
          }
        }
      }
    }
    catch ( Exception e ) {
      log.error("Problem",e);
    }

    redirect(url: result.ref)
  }

  def processRRTransfer() {
    def result = [:]
    log.debug("processRRTransfer ${params}");

    def new_user_alloc = genericOIDService.resolveOID2(params.allocToUser)

    params.each { p ->
      if ( ( p.key.startsWith('tt:') ) && ( p.value ) && ( p.value instanceof String ) ) {
        def tt = p.key.substring(3);
        def ReviewRequest rr = ReviewRequest.get(tt);
        log.debug("Process ${tt} - ${rr}");
        rr.needsNotify = true
        rr.allocatedTo = new_user_alloc
        rr.save()
        def rra = new ReviewRequestAllocationLog(note:params.note,allocatedTo:new_user_alloc,rr:rr).save();
      }
    }

    result.ref=params.from
    redirect(url: result.ref)
  }

  def createTitleHistoryEvent() {

    def result=[:]

    if ( ( params.afterTitles != null ) && ( params.beforeTitles != null ) ) {
    
      if ( params.afterTitles instanceof java.lang.String ) {
        params.afterTitles = [ params.afterTitles ]
      }
  
      if ( params.beforeTitles instanceof java.lang.String ) {
        params.beforeTitles = [ params.beforeTitles ]
      }
  
      def newTitleHistoryEvent = new ComponentHistoryEvent(eventDate:params.date('EventDate', 'yyyy-MM-dd')).save()
  
      params.afterTitles?.each { at ->
        def component = genericOIDService.resolveOID2(at)
        def after_participant = new ComponentHistoryEventParticipant (event:newTitleHistoryEvent,
                                                                      participant:component,
                                                                      participantRole:'out').save()
      }
  
      params.beforeTitles?.each { bt ->
        def component = genericOIDService.resolveOID2(bt)
        def after_participant = new ComponentHistoryEventParticipant (event:newTitleHistoryEvent,
                                                                      participant:component,
                                                                      participantRole:'in').save()
      }
    }
    result.ref=request.getHeader('referer')
    redirect(url: result.ref)
  }

  def deleteTitleHistoryEvent() {
    log.debug(params);
    def result = [:]
    result.ref=request.getHeader('referer')
    def he = ComponentHistoryEvent.get(params.id)
    if (he != null ) {
      he.delete(flush:true)
    }
    redirect(url: result.ref)
  }

  private def packageTSVExport(packages_to_export) {
    def filename = null;

    if ( packages_to_export.size() == 0 ) 
      return

    def sdf = new java.text.SimpleDateFormat('yyyy-MM-dd')
    def export_date = sdf.format(new java.util.Date());

    if ( packages_to_export.size() == 1 ) {
      filename = "GOKb Export : ${packages_to_export[0].provider?.name} : ${packages_to_export[0].name} : ${export_date}.tsv"
    }
    else {
      filename = "GOKb Export : multiple_packages : ${export_date}.tsv"
    }

    try {
      response.setHeader("Content-disposition", "attachment; filename=${filename}")
      response.contentType = "text/tsv"
      def out = response.outputStream
      out.withWriter { writer ->

        packages_to_export.each { pkg ->

          // As per spec header at top of file / section
          writer.write("GOKb Export : ${pkg.provider?.name} : ${pkg.name} : ${export_date}\n");

          writer.write('TIPP ID	TIPP URL	Title ID	Title	TIPP Status	[TI] Publisher	[TI] Imprint	[TI] Published From	[TI] Published to	[TI] Medium	[TI] OA Status	'+
                     '[TI] Continuing series	[TI] ISSN	[TI] EISSN	Package	Package ID	Package URL	Platform	'+
                     'Platform URL	Platform ID	Reference	Edit Status	Access Start Date	Access End Date	Coverage Start Date	'+
                     'Coverage Start Volume	Coverage Start Issue	Coverage End Date	Coverage End Volume	Coverage End Issue	'+
                     'Embargo	Coverage note	Host Platform URL	Format	Payment Type	Delayed OA	Delayed OA Embargo	Hybrid OA	Hybrid OA URL\n');

          def tipps = TitleInstancePackagePlatform.executeQuery(
                         'select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp  and tipp.status.value <> ? and c.type.value = ? order by tipp.id',
                         [pkg, 'Deleted', 'Package.Tipps']);



          tipps.each { tipp_id ->
            TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)
            writer.write( tipp.id + '\t' + tipp.url + '\t' + tipp.title.id + '\t' + tipp.title.name + '\t' +
                          tipp.status.value + '\t' + tipp.title.getCurrentPublisher?.name + '\t' + tipp.title.imprint?.name + '\t' + tipp.title.publishedFrom + '\t' +
                          tipp.title.publishedTo + '\t' + tipp.title.medium?.value + '\t' + tipp.title.oa?.status + '\t' +
                          tipp.title.continuingSeries?.value + '\t' + 
                          'SteveToFix\t' + // tipp.title.getIdentifierValue('ISSN') + '\t' +
                          'SteveToFix\t' + //tipp.title.getIdentifierValue('eISSN') + '\t' 
                          pkg.name + '\t' + pkg.id + '\t' + '\t' + tipp.hostPlatform.name + '\t' +
                          tipp.hostPlatform.primaryUrl + '\t' + tipp.hostPlatform.id + '\t\t' + tipp.status?.value + '\t' + tipp.accessStartDate  + '\t' +
                          tipp.accessEndDate + '\t' + tipp.startDate + '\t' + tipp.startVolume + '\t' + tipp.startIssue + '\t' + tipp.endDate + '\t' +
                          tipp.endVolume + '\t' + tipp.endIssue + '\t' + tipp.embargo + '\t' + tipp.coverageNote + '\t' + tipp.hostPlatform.primaryUrl + '\t' +
                          tipp.format?.value + '\t' + tipp.paymentType?.value + '\t' + tipp.delayedOA?.value + '\t' + tipp.delayedOAEmbargo + '\t' +
                          tipp.hybridOA?.value + '\t' + tipp.hybridOAUrl +
                          '\n');
            tipp.discard();
          }
        }

        writer.flush();
        writer.close();
      }
      out.close()
    }
    catch ( Exception e ) {
      log.error("Problem with export",e);
    }
  }
}
