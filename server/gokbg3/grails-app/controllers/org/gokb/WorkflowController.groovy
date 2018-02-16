package org.gokb

import org.gokb.cred.*
import org.springframework.security.access.annotation.Secured;
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import grails.converters.JSON
import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.type.*
import org.hibernate.Hibernate


class WorkflowController {

  def grailsApplication
  def genericOIDService
  def springSecurityService
  def sessionFactory

  def actionConfig = [
    'method::deleteSoft':[actionType:'simple'],
    'title::transfer':      [actionType:'workflow', view:'titleTransfer'],
    'title::change':      [actionType:'workflow', view:'titleChange'],
    'platform::replacewith':[actionType:'workflow', view:'platformReplacement'],
    'method::registerWebhook':[actionType:'workflow', view:'registerWebhook'],
    'method::RRTransfer':[actionType:'workflow', view:'revReqTransfer'],
    'method::RRClose':[actionType:'simple' ],
    'title::reconcile':[actionType:'workflow', view:'titleReconcile' ],
    'title::merge':[actionType:'workflow', view:'titleMerge' ],
    'exportPackage':[actionType:'process', method:'packageTSVExport'],
    'kbartExport':[actionType:'process', method:'packageKBartExport'],
    'method::retire':[actionType:'simple' ],
    'org::deprecateReplace':[actionType:'workflow', view:'deprecateOrg'],
    'org::deprecateDelete':[actionType:'workflow', view:'deprecateDeleteOrg']
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
    titleChangeData.title_ids = []
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

      titleChangeData.title_ids.add(title_obj.id)

      def tipps = TitleInstancePackagePlatform.executeQuery(
                         'select tipp from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp  and tipp.status.value <> ? and c.type.value = ?',
                         [title_obj, 'Deleted','TitleInstance.Tipps']);
      tipps.each { tipp ->

        if ( ( tipp.status?.value != 'Deleted' ) && ( tipp.pkg.scope?.value != 'GOKb Master' ) ) {

          log.debug("Add tipp to discontinue ${tipp}");

          titleChangeData.tipps[tipp.id] = [
            oldTippValue:[
              title_id:tipp.title.id,
              package_id:tipp.pkg.id,
              platform_id:tipp.hostPlatform.id,
              startDate:tipp.startDate ? sdf.format(tipp.startDate) : null,
              startVolume:tipp.startVolume,
              startIssue:tipp.startIssue,
              endDate:tipp.endDate? sdf.format(tipp.endDate) : null,
              endVolume:tipp.endVolume,
              endIssue:tipp.endIssue,
              url:tipp.url
            ],
            newtipps:[]
          ]

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
                                 url:tipp.url,
                                 endIssue:tipp.endIssue]
            titleChangeData.tipps[tipp.id].newtipps.add(new_tipp_info)
          }
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
  def startTitleMerge() {

    log.debug("startTitleMerge(${params})");

    def user = springSecurityService.currentUser
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save()
    def transfer_type = RefdataCategory.lookupOrCreate('Activity.Type', 'TitleMerge').save()
    def first_title = null
    
    def result = [:]
    result.oldTitles = []
    
    def activity_data = [:]
    
    def oldIds = params.list('beforeTitles')
    
    activity_data.oldTitles = params.list('beforeTitles')
    activity_data.newTitle = params.newTitle
    
    def sw = new StringWriter();
    
    log.debug("Titles to replace: ${oldIds}");
    
    oldIds.each { oid ->
      if ( first_title == null ) {
        first_title = oid
      }
      else {
        sw.write(', ');
      }
      
      def title_obj = genericOIDService.resolveOID2(oid)
      
      sw.write(title_obj.name);
      
      result.oldTitles.add(title_obj)
    }
    
    result.newTitle = genericOIDService.resolveOID2(params.newTitle)
    
    def builder = new JsonBuilder()
    builder(activity_data)

    def new_activity = new Activity(
                                    activityName:"Title Merge ${sw.toString()}",
                                    activityData:builder.toString(),
                                    owner:user,
                                    status:active_status,
                                    type:transfer_type).save()

    log.debug("redirect to edit activity (Really title) ${builder.toString()}");

    // if ( first_title )
    //   redirect(controller:'resource', action:'show', id:first_title);
    // else
    //   redirect(controller:'home', action:'index');

    redirect(action:'editTitleMerge',id:new_activity.id)
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def editTitleMerge() {
    log.debug("editTitleMerge() - ${params}");
    
    // def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
    def activity_record = Activity.get(params.id)
    def activity_data = new JsonSlurper().parseText(activity_record.activityData)
    def merge_params = [:]

    request.getParameterNames().each { pn ->
      if( pn.startsWith("merge_") ){
        merge_params[pn] = request.getParameter(pn)
      }
    }

    if ( params.update ) {
      log.debug("Update...");
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString();
      activity_record.save()
    }
    else if ( params.process ) {
      log.debug("Process...");

      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString();
      activity_record.save()

      processTitleMerge(activity_record, activity_data, merge_params);
      if ( activity_data.newTitle?.size() > 0 ) {
        redirect(controller:'resource',action:'show', id:activity_data.newTitle);
      }
      else {
        redirect(controller:'home', action:'index');
      }
    }
    else if ( params.abandon ) {
      log.debug("**ABANDON**...");
      activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned')
      activity_record.save()
      if ( activity_data.oldTitles?.size() > 0 ) {
        redirect(controller:'resource',action:'show', id:activity_data.oldTitles[0]);
      }
      else {
        redirect(controller:'home', action:'index');
      }
    }

    log.debug("Processing...");

    def result = [:]
    result.oldTitles = []
    result.newTitle = genericOIDService.resolveOID2(activity_data.newTitle)
    result.d = activity_record

    activity_data.oldTitles.each { oid ->
      result.oldTitles.add(genericOIDService.resolveOID2(oid))
    }

    result.id = params.id

    result
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
          def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

          result.titles.add(title_instance)
          titleTransferData.title_ids.add(title_instance.id)
          title_instance.tipps.each { tipp ->
            if ( ( tipp.status?.value != 'Deleted' ) && ( tipp.pkg.scope?.value != 'GOKb Master' ) ) {
              result.tipps.add(tipp)
              titleTransferData.tipps[tipp.id] = [
                oldTippValue:[
                  title_id:tipp.title.id,
                  package_id:tipp.pkg.id,
                  platform_id:tipp.hostPlatform.id,
                  startDate:tipp.startDate ? sdf.format(tipp.startDate) : null,
                  startVolume:tipp.startVolume,
                  startIssue:tipp.startIssue,
                  endDate:tipp.endDate? sdf.format(tipp.endDate) : null,
                  endVolume:tipp.endVolume,
                  endIssue:tipp.endIssue,
                  url:tipp.url
                ],
                newtipps:[]
              ]
            }
          }
        }
        else {
          log.error("Unable to locate title with that ID");
        }
      }
    }

    log.debug("loaded Title Data.. Looking up publisher");
    result.newPublisher = genericOIDService.resolveOID2(params.title)

    log.debug("Assigning new publisher");
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
      log.debug("Checking ${pn} : ${value}");
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
      else if ( pn.startsWith('_oldtipp') ) {
        def key_components = pn.split(':');

        if ( activity_data.tipps[key_components[1]].oldTippValue == null ) { activity_data.tipps[key_components[1]].oldTippValue = [:] }

        if ( ( value != null ) && ( value.length() > 0 ) ) {
          activity_data.tipps[key_components[1]].oldTippValue[key_components[2]] = value
        }
        else {
          activity_data.tipps[key_components[1]].oldTippValue[key_components[2]] = null
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
                                        endIssue:old_tipp.endIssue,
                                        url:old_tipp.url]
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
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString();
      activity_record.save()
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
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString();
      activity_record.save()

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
                        startDate: tipp_info.value.oldTippValue?.startDate,
                        startVolume:tipp_info.value.oldTippValue?.startVolume,
                        startIssue:tipp_info.value.oldTippValue?.startIssue,
                        endDate:tipp_info.value.oldTippValue?.endDate,
                        endVolume:tipp_info.value.oldTippValue?.endVolume,
                        endIssue:tipp_info.value.oldTippValue?.endIssue,
                        url:tipp_info.value.oldTippValue?.url
                        ])
      int seq=0;
      // .value because tipp_info is a map...
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
                          url:newtipp_info.url
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

        log.debug("Set ${key_components} = ${value}");

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
      else if ( pn.startsWith('_oldtipp') ) {
        def key_components = pn.split(':');

        log.debug("Set ${key_components} = ${value}");

        if ( activity_data.tipps[key_components[1]].oldTippValue == null ) { activity_data.tipps[key_components[1]].oldTippValue = [:] }

        if ( ( value != null ) && ( value.length() > 0 ) ) {
          activity_data.tipps[key_components[1]].oldTippValue[key_components[2]] = value
        }
        else {
          activity_data.tipps[key_components[1]].oldTippValue[key_components[2]] = null
        }
      }
    }

    if ( params.update ) {
      log.debug("Update...");
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString();
      activity_record.save()
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

      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString();
      activity_record.save()

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
                        startDate: tipp_info.value.oldTippValue?.startDate,
                        startVolume:tipp_info.value.oldTippValue?.startVolume,
                        startIssue:tipp_info.value.oldTippValue?.startIssue,
                        endDate:tipp_info.value.oldTippValue?.endDate,
                        endVolume:tipp_info.value.oldTippValue?.endVolume,
                        endIssue:tipp_info.value.oldTippValue?.endIssue,
                        url:tipp_info.value.oldTippValue?.url
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
                          url:newtipp_info.url
                          ])
      }
    }

    result.id = params.id

    result
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processTitleChange(activity_record, activity_data) {

    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

    activity_data.tipps.each { tipp_map_entry ->

      def current_tipp = TitleInstancePackagePlatform.get(tipp_map_entry.key)

      tipp_map_entry.value.newtipps.each { newtipp ->
        log.debug("Process new tipp : ${newtipp}");

        def new_package = Package.get(newtipp.package_id)
        def new_platform = Platform.get(newtipp.platform_id)

        def new_title = TitleInstance.get(newtipp.title_id);

        def parsed_start_date = null;
        def parsed_end_date = null;
        try {
          parsed_start_date = ( newtipp.startDate?.length() > 0 ) ? sdf.parse(newtipp.startDate) : null;
          parsed_end_date = ( newtipp.endDate?.length() > 0 ) ? sdf.parse(newtipp.endDate) : null;
        }
        catch ( Exception e ) {
        }

        // def new_tipp = new TitleInstancePackagePlatform(
        def new_tipp = TitleInstancePackagePlatform.tiplAwareCreate([
                                   pkg:new_package,
                                   hostPlatform:new_platform,
                                   title:new_title,
                                   startDate: parsed_start_date,
                                   startVolume:newtipp.startVolume,
                                   startIssue:newtipp.startIssue,
                                   endDate: parsed_end_date,
                                   endVolume:newtipp.endVolume,
                                   endIssue:newtipp.endIssue,
                                   url:newtipp.url,
                                   ]).save()

        if ( newtipp.review == 'on' ) {
          ReviewRequest.raise(new_tipp, 'New tipp - please review' , 'A Title change cause this new tipp to be created', request.user)
        }
      }


      // Update old tipp
      def parsed_start_date = null
      def parsed_end_date = null
      try {
        parsed_start_date = tipp_map_entry.value.oldTippValue.startDate ? sdf.parse(tipp_map_entry.value.oldTippValue.startDate) : null;
        parsed_end_date = tipp_map_entry.value.oldTippValue.endDate ? sdf.parse(tipp_map_entry.value.oldTippValue.endDate) : null;
      }
      catch ( Exception e ) {
      }

      current_tipp.startDate = parsed_start_date;
      current_tipp.startVolume = tipp_map_entry.value.oldTippValue.startVolume;
      current_tipp.startIssue = tipp_map_entry.value.oldTippValue.startIssue;
      current_tipp.endDate = parsed_end_date;
      current_tipp.endVolume = tipp_map_entry.value.oldTippValue.endVolume;
      current_tipp.endIssue = tipp_map_entry.value.oldTippValue.endIssue;

      log.debug("Saving current tipp");
      current_tipp.save()



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
    def event_date = activity_data.eventDate ?: sdf.format(new Date());

    // Create title history event
    def newTitleHistoryEvent = new ComponentHistoryEvent(eventDate:sdf.parse(event_date)).save()

    activity_data.afterTitles?.each { at ->
      def component = genericOIDService.resolveOID2(at)
      def after_participant = new ComponentHistoryEventParticipant (event:newTitleHistoryEvent,
                                                                    participant:component,
                                                                    participantRole:'out').save()
    }

    activity_data.beforeTitles?.each { bt ->
      def component = genericOIDService.resolveOID2(bt)
      def after_participant = new ComponentHistoryEventParticipant (event:newTitleHistoryEvent,
                                                                    participant:component,
                                                                    participantRole:'in').save()
    }


    activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
    activity_record.save()
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processTitleMerge(activity_record, activity_data, merge_params) {
    log.debug("processTitleMerge ${params}\n\n ${activity_data}");
    
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status','Deleted')
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')
    def rr_status_current = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open')
    
    def new_ti = genericOIDService.resolveOID2(activity_data.newTitle)
    def new_he = new_ti.getTitleHistory()
    
    activity_data.oldTitles.each { oid ->
      def old_ti = genericOIDService.resolveOID2(oid)
      
      if( !old_ti.name.equals(new_ti.name) ) {
        new_ti.addVariantTitle(old_ti.name)
      }
      
      if( merge_params['merge_ids'] ){
        log.debug("Looking for new IDs to add")
        def id_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids')
        
        old_ti.ids.each { old_id ->
        
          def dupes = Combo.executeQuery("Select c from Combo as c where c.toComponent.id = ? and c.fromComponent.id = ? and c.type.id = ?",[old_id.id,new_ti.id,id_combo_type.id]);
          
          if ( !dupes || dupes.size() == 0 ){
            log.debug("Adding Identifier ${old_id} to ${new_ti}")
            Combo new_id = new Combo(toComponent:old_id, fromComponent:new_ti, type:id_combo_type).save(flush:true, failOnError:true);
          }else{
            log.debug("Identifier ${old_id} is already connected to ${new_ti}..")
          }
        }
      }
      
      if( merge_params['merge_vn'] ){ 
        old_ti.variantNames.each{ old_vn ->
          new_ti.addVariantTitle(old_vn.variantName)
        }
      }
      
      if( merge_params['merge_pb'] ){ 
        old_ti.publisher.each{ old_pb ->
          if ( !new_ti.publisher.contains(old_pb) ){
            new_ti.publisher.add(old_pb)
          }
        }
      }
      
      if( merge_params['merge_he'] ){
        def ti_history = old_ti.getTitleHistory()
        
        ti_history.each { ohe -> 
        
          def new_from = []
          def new_to = []
          def dupe = false
        
          if ( ohe.to.contains(old_ti) ) {
            
            new_to = ohe.to.removeIf { it == old_ti }.add(new_ti)
            
            ohe.from.each { hep ->
              def he_match = ComponentHistoryEvent.executeQuery("select che from ComponentHistoryEvent as che where exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = :fromPart) AND exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = :toPart)",[fromPart:hep,toPart:new_ti])
              
              if (he_match) {
                dupe = true
              } 
            }
            
            new_from = ohe.from
          }
          else if ( ohe.from.contains(old_ti) ) {
          
            new_from = ohe.from.removeIf { it == old_ti }.add(new_ti)
          
            ohe.from.each { hep ->
              def he_match = ComponentHistoryEvent.executeQuery("select che from ComponentHistoryEvent as che where exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = :fromPart) AND exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = :toPart)",[fromPart:new_ti,toPart:hep])
              
              if (he_match) {
                dupe = true
              } 
            }
            
            new_to = ohe.to
          }
          
          if( !dupe ) {
            def he = new ComponentHistoryEvent()
            
            if ( ohe.date ) {
              he.eventDate = sdf.parse(ohe.date);
            }
            
            he.save(flush:true, failOnError:true);
            
            new_from.each {
              def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'in');
              hep.save(flush:true, failOnError:true);
            }
            
            new_to.each {
              def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'out');
              hep.save(flush:true, failOnError:true);
            }
          }
        }
      }
      
      def events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant = :component",[component:old_ti])

      events_to_delete.each {
        it.delete(flush:true)
      }

      
      old_ti.tipps.each { old_tipp ->
        
        if( merge_params['merge_tipps'] && old_tipp.status == status_current ){
          def dupe = false
          
          new_ti.tipps.each { new_tipp ->
            if ( new_tipp.pkg == old_tipp.pkg && new_tipp.hostPlatform == old_tipp.hostPlatform && new_tipp.url == old_tipp.url ) {
              dupe = true
            }
          }
          
          if ( !dupe ) {
            def new_tipp = TitleInstancePackagePlatform.tiplAwareCreate([
                                    pkg:old_tipp.pkg,
                                    hostPlatform:old_tipp.hostPlatform,
                                    title:new_ti,
                                    startDate:old_tipp.startDate,
                                    startVolume:old_tipp.startVolume,
                                    startIssue:old_tipp.startIssue,
                                    endDate:old_tipp.endDate,
                                    endVolume:old_tipp.endVolume,
                                    endIssue:old_tipp.endIssue,
                                    url:old_tipp.url,
                                    ]).save()
            log.debug("Added new TIPP ${new_tipp} to TI ${new_ti}")
          }
        }
        old_tipp.status = status_deleted
      }
      old_ti.reviewRequests.each { rr ->
        def rr_context = [:]
        rr_context['user'] = request.user
        
        if ( rr.status == rr_status_current ) {
          rr.RRClose(rr_context)
        }
      }
      
      old_ti.status = status_deleted
    }
    
    activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
    activity_record.save()
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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

        if ( tipp_map_entry.value.oldTippValue?.startDate ) {
          try {
            current_tipp.startDate = sdf.parse(tipp_map_entry.value.oldTippValue?.startDate)
          }
          catch ( Exception e ) {
          }
        }
        if ( tipp_map_entry.value.oldTippValue?.startVolume )
          current_tipp.startVolume = tipp_map_entry.value.oldTippValue?.startVolume
        if ( tipp_map_entry.value.oldTippValue?.startIssue )
          current_tipp.startIssue = tipp_map_entry.value.oldTippValue?.startIssue

        if ( tipp_map_entry.value.oldTippValue?.endDate ) {
          try {
            current_tipp.endDate = sdf.parse(tipp_map_entry.value.oldTippValue?.endDate)
          }
          catch ( Exception e ) {
          }
        }
        if ( tipp_map_entry.value.oldTippValue?.endVolume )
          current_tipp.endVolume = tipp_map_entry.value.oldTippValue?.endVolume
        if ( tipp_map_entry.value.oldTippValue?.endIssue )
          current_tipp.endIssue = tipp_map_entry.value.oldTippValue?.endIssue

        def new_package = Package.get(newtipp.package_id)
        def new_platform = Platform.get(newtipp.platform_id)

        def parsed_start_date = null;
        def parsed_end_date = null;
        try {
          parsed_start_date = ( newtipp.startDate?.length() > 0 ) ? sdf.parse(newtipp.startDate) : null;
          parsed_end_date = ( newtipp.endDate?.length() > 0 ) ? sdf.parse(newtipp.endDate) : null;
        }
        catch ( Exception e ) {
        }

        // def new_tipp = new TitleInstancePackagePlatform(
        def new_tipp = TitleInstancePackagePlatform.tiplAwareCreate([
                                   pkg:new_package,
                                   hostPlatform:new_platform,
                                   title:current_tipp.title,
                                   startDate: parsed_start_date,
                                   startVolume:newtipp.startVolume,
                                   startIssue:newtipp.startIssue,
                                   endDate: parsed_end_date,
                                   endVolume:newtipp.endVolume,
                                   endIssue:newtipp.endIssue,
                                   url:newtipp.url
                                ]).save()

        if ( newtipp.review == 'on' ) {
          log.debug("User requested a review request be generated for this new tipp");
          ReviewRequest.raise(new_tipp, 'New tipp - please review' , 'A Title transfer cause this new tipp to be created', request.user)
        }
      }

      // Retire the tipp if
      log.debug("Checking close flags..${params}");

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

      def parsed_start_date = null
      def parsed_end_date = null
      try {
        parsed_start_date = tipp_map_entry.value.oldTippValue.startDate ? sdf.parse(tipp_map_entry.value.oldTippValue.startDate) : null;
        parsed_end_date = tipp_map_entry.value.oldTippValue.endDate ? sdf.parse(tipp_map_entry.value.oldTippValue.endDate) : null;
      }
      catch ( Exception e ) {}

      current_tipp.startDate = parsed_start_date;
      current_tipp.startVolume = tipp_map_entry.value.oldTippValue.startVolume;
      current_tipp.startIssue = tipp_map_entry.value.oldTippValue.startIssue;
      current_tipp.endDate = parsed_end_date;
      current_tipp.endVolume = tipp_map_entry.value.oldTippValue.endVolume;
      current_tipp.endIssue = tipp_map_entry.value.oldTippValue.endIssue;

      log.debug("Saving current tipp");
      current_tipp.save()
    }

    activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
    activity_record.save()
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processPackageReplacement() {
    def retired_status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Retired')
    def result = [:]
    result['old'] = []
    result['new'] = ''
    result['count'] = 0

    params.each { p ->
      log.debug("Testing ${p.key}");
      if ( ( p.key.startsWith('tt') ) && ( p.value ) && ( p.value instanceof String ) ) {
         def tt = p.key.substring(3);
         log.debug("Platform to replace: \"${tt}\"");
         def old_platform = Platform.get(tt)
         def new_platform = genericOIDService.resolveOID2(params.newplatform)
         log.debug("old: ${old_platform} new: ${new_platform}");
         try {
           def updates_count = Combo.executeQuery("select count(combo) from Combo combo where combo.fromComponent = ?",[old_platform]);
           Combo.executeUpdate("update Combo combo set combo.fromComponent = ? where combo.fromComponent = ?",[new_platform,old_platform]);
           result['count'] += updates_count
           result['old'] += old_platform.name
           result['new'] = new_platform.name
           old_platform.status = retired_status
           old_platform.save(flush:true)
         }
         catch ( Exception e ) {
           log.debug("Problem executing update");
         }
      }
    }
    render view:'platformReplacementResult' , model:[result:result]
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def download() {
    log.debug("Download ${params}");

    DataFile df = DataFile.findByGuid(params.id)
    if ( df != null ) {
      //HTML is causing problems, browser thinks it should render something, other way around this?
      response.setContentType("application/octet-stream");
      response.addHeader("Content-Disposition", "attachment; filename=\"${df.uploadName}\"")
      response.outputStream << df.fileData
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def authorizeVariant() {
    log.debug(params);
    def result = [:]
    result.ref=request.getHeader('referer')
    def variant = KBComponentVariantName.get(params.id)

    if ( variant != null ) {
      // Does the current owner.name exist in a variant? If not, we should create one so we don't loose the info
      def current_name_as_variant = variant.owner.variantNames.find { it.variantName == variant.owner.name }
      if ( current_name_as_variant == null ) {
        log.debug("No variant name found for current name: ${variant.owner.name} ")
        def variant_name = variant.owner.getId();
        if(variant.owner.name){
          variant_name = variant.owner.name
        }else if (variant.owner?.respondsTo('getDisplayName') && variant.owner.getDisplayName()){
          variant_name = variant.owner.getDisplayName()?.trim()
        }else if(variant.owner?.respondsTo('getName') ) {
           variant_name = variant.owner?.getName()?.trim()
        }
        def new_variant = new KBComponentVariantName(owner:variant.owner,variantName:variant_name).save(flush:true);

      }else{
          log.debug("Found existing variant name: ${current_name_as_variant}")
      }
      variant.owner.name = variant.variantName
      variant.owner.save(flush:true);
    }
    redirect(url: result.ref)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deleteVariant() {
    log.debug(params);
    User user = springSecurityService.currentUser
    def result = [:]
    result.ref=request.getHeader('referer')
    def variant = KBComponentVariantName.get(params.id)
    def variantOwner = variant.owner
    def variantName = variant.variantName
    if (variant != null ) {
      variant.delete()
      variantOwner.lastUpdateComment = "Deleted Alternate Name ${variantName}."
      variantOwner.save(flush: true)
    }
    redirect(url: result.ref)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def createTitleHistoryEvent() {

    log.debug("createTitleHistoryEvent")

    def result=[:]

    try {
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

    }
    catch ( Exception e ) {
      log.error("problem creating title history event",e)
    }
    finally{
      log.debug("Completed createTitleHistoryEvent")
    }

    result.ref=request.getHeader('referer')
    redirect(url: result.ref)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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


  // @Transactional(readOnly = true)
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  private def packageKBartExport(packages_to_export) {
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
      response.setContentType('text/tab-separated-values');
      response.setHeader("Content-disposition", "attachment; filename=\"${filename}\"")
      response.contentType = "text/tab-separated-values" // "text/tsv"

      def out = response.outputStream
      out.withWriter { writer ->

        def sanitize = { it ? "${it}".trim() : "" }

        packages_to_export.each { pkg ->

          // As per spec header at top of file / section
          // II: Need to add in preceding_publication_title_id
          writer.write('publication_title\t'+
                       'print_identifier\t'+
                       'online_identifier\t'+
                       'date_first_issue_online\t'+
                       'num_first_vol_online\t'+
                       'num_first_issue_online\t'+
                       'date_last_issue_online\t'+
                       'num_last_vol_online\t'+
                       'num_last_issue_online\t'+
                       'title_url\t'+
                       'first_author\t'+
                       'title_id\t'+
                       'embargo_info\t'+
                       'coverage_depth\t'+
                       'coverage_notes\t'+
                       'publisher_name\t'+
                       'preceding_publication_title_id\t'+
                       'date_monograph_published_print\t'+
                       'date_monograph_published_online\t'+
                       'monograph_volume\t'+
                       'monograph_edition\t'+
                       'first_editor\t'+
                       'parent_publication_title_id\t'+
                       'publication_type\t'+
                       'access_type\n');

          // scroll(ScrollMode.FORWARD_ONLY)
          // def session = sessionFactory.getCurrentSession()
          // def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status.value <> 'Deleted' and c.type.value = 'Package.Tipps' order by tipp.id")
          // query.setReadOnly(true)
          // query.setParameter('p',pkg.id, Hibernate.LONG)
          // query.setParameter('s':'Deleted',StringType.class)
          // query.setParameter('c':'Package.Tipps',StringType.class)

          def tipps = TitleInstancePackagePlatform.executeQuery(
                         'select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp  and tipp.status.value <> ? and c.type.value = ? order by tipp.id',
                         [pkg, 'Deleted', 'Package.Tipps'],[readOnly: true, fetchSize: 30]);



          tipps.each { tipp_id ->

          // ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          // while (tipps.next()) {
          //   Object tipp_id = tipps.get(0);
            TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)
            writer.write(
                          sanitize( tipp.title.name ) + '\t' +
                          sanitize( tipp.title.getIdentifierValue('ISSN') ) + '\t' +
                          sanitize( tipp.title.getIdentifierValue('eISSN') ) + '\t' +
                          sanitize( tipp.startDate ) + '\t' +
                          sanitize( tipp.startVolume ) + '\t' +
                          sanitize( tipp.startIssue ) + '\t' +
                          sanitize( tipp.endDate ) + '\t' +
                          sanitize( tipp.endVolume ) + '\t' +
                          sanitize( tipp.endIssue ) + '\t' +
                          sanitize( tipp.url ) + '\t' +
                          '\t'+  // First Author
                          sanitize( tipp.title.id ) + '\t' +
                          sanitize( tipp.embargo ) + '\t' +
                          sanitize( tipp.coverageDepth ) + '\t' +
                          sanitize( tipp.coverageNote ) + '\t' +
                          sanitize( tipp.title.getCurrentPublisher()?.name ) + '\t' +
                          sanitize( tipp.title.getPrecedingTitleId() ) + '\t' +
                          '\t' +  // date_monograph_published_print
                          '\t' +  // date_monograph_published_online
                          '\t' +  // monograph_volume
                          '\t' +  // monograph_edition
                          '\t' +  // first_editor
                          '\t' +  // parent_publication_title_id
                          sanitize( tipp.title?.medium?.value ) + '\t' +  // publication_type
                          sanitize( tipp.paymentType?.value ) +  // access_type
                          '\n');
            tipp.discard();
          }
        }
        tipps.close()

        writer.flush();
        writer.close();
      }
      out.close()
    }
    catch ( Exception e ) {
      log.error("Problem with export",e);
    }
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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
      response.setContentType('text/tab-separated-values');
      response.setHeader("Content-disposition", "attachment; filename=\"${filename}\"")
      response.contentType = "text/tab-separated-values" // "text/tsv"

      def out = response.outputStream
      out.withWriter { writer ->

        def sanitize = { it ? "${it}".trim() : "" }

        packages_to_export.each { pkg ->

          // As per spec header at top of file / section
          writer.write("GOKb Export : ${pkg.provider?.name} : ${pkg.name} : ${export_date}\n");

          writer.write('TIPP ID	TIPP URL	Title ID	Title	TIPP Status	[TI] Publisher	[TI] Imprint	[TI] Published From	[TI] Published to	[TI] Medium	[TI] OA Status	'+
                     '[TI] Continuing series	[TI] ISSN	[TI] EISSN	Package	Package ID	Package URL	Platform	'+
                     'Platform URL	Platform ID	Reference	Edit Status	Access Start Date	Access End Date	Coverage Start Date	'+
                     'Coverage Start Volume	Coverage Start Issue	Coverage End Date	Coverage End Volume	Coverage End Issue	'+
                     'Embargo	Coverage note	Host Platform URL	Format	Payment Type\n');

          def tipps = TitleInstancePackagePlatform.executeQuery(
                         'select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp  and tipp.status.value <> ? and c.type.value = ? order by tipp.id',
                         [pkg, 'Deleted', 'Package.Tipps']);



          tipps.each { tipp_id ->
            TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)
            writer.write( sanitize( tipp.id ) + '\t' + sanitize( tipp.url ) + '\t' + sanitize( tipp.title.id ) + '\t' + sanitize( tipp.title.name ) + '\t' +
                          sanitize( tipp.status.value ) + '\t' + sanitize( tipp.title.getCurrentPublisher()?.name ) + '\t' + sanitize( tipp.title.imprint?.name ) + '\t' + sanitize( tipp.title.publishedFrom ) + '\t' +
                          sanitize( tipp.title.publishedTo ) + '\t' + sanitize( tipp.title.medium?.value ) + '\t' + sanitize( tipp.title.oa?.status ) + '\t' +
                          sanitize( tipp.title.continuingSeries?.value ) + '\t' +
                          sanitize( tipp.title.getIdentifierValue('ISSN') ) + '\t' +
                          sanitize( tipp.title.getIdentifierValue('eISSN') ) + '\t' +
                          sanitize( pkg.name ) + '\t' + sanitize( pkg.id ) + '\t' + '\t' + sanitize( tipp.hostPlatform.name ) + '\t' +
                          sanitize( tipp.hostPlatform.primaryUrl ) + '\t' + sanitize( tipp.hostPlatform.id ) + '\t\t' + sanitize( tipp.status?.value ) + '\t' + sanitize( tipp.accessStartDate )  + '\t' +
                          sanitize( tipp.accessEndDate ) + '\t' + sanitize( tipp.startDate ) + '\t' + sanitize( tipp.startVolume ) + '\t' + sanitize( tipp.startIssue ) + '\t' + sanitize( tipp.endDate ) + '\t' +
                          sanitize( tipp.endVolume ) + '\t' + sanitize( tipp.endIssue ) + '\t' + sanitize( tipp.embargo ) + '\t' + sanitize( tipp.coverageNote ) + '\t' + sanitize( tipp.hostPlatform.primaryUrl ) + '\t' +
                          sanitize( tipp.format?.value ) + '\t' + sanitize( tipp.paymentType?.value ) +
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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def addToRulebase() {
    def result = [:]


    result.ref=request.getHeader('referer')
    log.debug("${params.sourceName}");
    log.debug("${params.sourceId}");

    def source = Source.get(params.sourceId);

    log.debug("Process existing rulebase:: ${source.ruleset}");

    // See if the source rulebase has been initialised
    def parsed_rulebase = source.ruleset ? JSON.parse(source.ruleset) : null;

    if ( parsed_rulebase == null ) {
      parsed_rulebase = [rules:[:]]
    }

    def num_probs = params.int('prob_seq_count')

    for ( int i = 0; i< num_probs; i++ ) {
      log.debug("addToRulebase ${params.pr['prob_res_'+i]}");
      def resolution = params.pr['prob_res_'+i]

      // If the user has specified what happens in this case, then store the rule in the source for subsequent use
      if ( resolution.ResolutionOption ) {
        log.debug("When ${resolution.probfingerprint} Then ${resolution.ResolutionOption}");
        def rule_resolution = [ ruleResolution:"${resolution.ResolutionOption}" ]
        parsed_rulebase.rules[resolution.probfingerprint] = rule_resolution;
      }
    }

    source.ruleset = parsed_rulebase as JSON
    source.save(flush:true, failOnError:true);

    redirect(url: result.ref)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deprecateOrg() {
    def result=[:]
    log.debug("Params: ${params}");
    log.debug("otd: ${params.orgsToDeprecate}");
    log.debug("neworg: ${params.neworg}");
    if ( params.orgsToDeprecate && params.neworg ) {
      def otd = Org.get(params.orgsToDeprecate)
      def neworg = genericOIDService.resolveOID2(params.neworg)

      if ( otd && neworg ) {
        log.debug("Got org to deprecate and neworg...  Process now");
        // Updating all combo.toComponent
        // Updating all combo.fromComponent
        Combo.executeUpdate("update Combo set toComponent = ? where toComponent = ?",[neworg,otd]);
        Combo.executeUpdate("update Combo set fromComponent = ? where fromComponent = ?",[neworg,otd]);
        flash.message="Org Deprecation Completed"
      }
    }
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deprecateDeleteOrg() {
    log.debug("deprecateDeleteOrg ${params}");
    def result=[:]
    if ( params.orgsToDeprecate  ) {
      def o = Org.get(params.orgsToDeprecate)
      if ( o ) {
        o.deprecateDelete()
      }
    }
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deleteCombo() {
    Combo c = Combo.get(params.id);
    c.delete();
    redirect(url: request.getHeader('referer'));
  }
}
