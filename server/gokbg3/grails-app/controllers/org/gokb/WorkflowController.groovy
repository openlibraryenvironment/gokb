package org.gokb

import org.gokb.cred.*
import org.springframework.security.access.annotation.Secured
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import grails.converters.JSON
import grails.gorm.transactions.Transactional


class WorkflowController{

  def genericOIDService
  def springSecurityService
  def reviewRequestService
  def packageService
  def dateFormatService

  def actionConfig = [
      'method::deleteSoft'     : [actionType: 'simple'],
      'title::transfer'        : [actionType: 'workflow', view: 'titleTransfer'],
      'title::change'          : [actionType: 'workflow', view: 'titleChange'],
      'platform::replacewith'  : [actionType: 'workflow', view: 'platformReplacement'],
      'method::registerWebhook': [actionType: 'workflow', view: 'registerWebhook'],
      'method::RRTransfer'     : [actionType: 'workflow', view: 'revReqTransfer'],
      'method::RRClose'        : [actionType: 'simple'],
      'packageUrlUpdate'       : [actionType: 'process', method: 'triggerSourceUpdate'],
      'title::reconcile'       : [actionType: 'workflow', view: 'titleReconcile'],
      'title::merge'           : [actionType: 'workflow', view: 'titleMerge'],
      'tipp::retire'           : [actionType: 'workflow', view: 'tippRetire'],
      'tipp::move'             : [actionType: 'workflow', view: 'tippMove'],
      'exportPackage'          : [actionType: 'process', method: 'packageTSVExport'],
      'kbartExport'            : [actionType: 'process', method: 'packageKBartExport'],
      'method::retire'         : [actionType: 'simple'],
      'method::setActive'      : [actionType: 'simple'],
      'method::setExpected'    : [actionType: 'simple'],
      'setStatus::Retired'     : [actionType: 'simple'],
      'setStatus::Current'     : [actionType: 'simple'],
      'setStatus::Expected'    : [actionType: 'simple'],
      'setStatus::Deleted'     : [actionType: 'simple'],
      'org::deprecateReplace'  : [actionType: 'workflow', view: 'deprecateOrg'],
      'org::deprecateDelete'   : [actionType: 'workflow', view: 'deprecateDeleteOrg'],
      'verifyTitleList'        : [actionType: 'process', method: 'verifyTitleList']
  ]

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def action(){
    log.debug("WorkflowController::action(${params})")
    def result = [:]
    result.ref = request.getHeader('referer')

    def action_config = actionConfig[params.selectedBulkAction]

    if (action_config){
      result.objects_to_action = []

      if (params.batch_on == 'all'){
        log.debug("Requested batch_on all.. so evaluate the query and do the right thing...")
        if (params.qbe){
          def qresult = [:]
          if (params.qbe.startsWith('g:')){
            // Global template, look in config
            def global_qbe_template_shortcode = params.qbe.substring(2, params.qbe.length())
            // log.debug("Looking up global template ${global_qbe_template_shortcode}")
            qresult.qbetemplate = grailsApplication.config.globalSearchTemplates[global_qbe_template_shortcode]
            // log.debug("Using template: ${result.qbetemplate}")
          }

          // Looked up a template from somewhere, see if we can execute a search
          if (qresult.qbetemplate){
            log.debug("Execute query")
            // doQuery(result.qbetemplate, params, result)
            def target_class = grailsApplication.getArtefact("Domain", qresult.qbetemplate.baseclass)
            com.k_int.HQLBuilder.build(grailsApplication, qresult.qbetemplate, params, qresult, target_class, genericOIDService)

            qresult.recset.each{
              def oid_to_action = "${it.class.name}:${it.id}"
              result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
            }
          }
        }
      }
      else{
        log.debug("Assuming standard selection of rows to action")
        params.each{ p ->
          if ((p.key.startsWith('bulk:')) && (p.value) && (p.value instanceof String)){
            def oid_to_action = p.key.substring(5)
            result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
          }
        }
      }

      switch (action_config.actionType){
        case 'simple':
          def method_config = params.selectedBulkAction.split(/\:\:/) as List
          switch (method_config[0]){
            case "method":
              def context = [user: request.user]
              // Everything after the first 2 "parts" are args for the method.
              def method_params = []
              method_params.add(context)
              if (method_config.size() > 2){
                method_params.addAll(method_config.subList(2, method_config.size()))
              }
              // We should just call the method on the targets.
              result.objects_to_action.each{ def target ->
                log.debug("Target: ${target} (${target.class.name})")
                log.debug("Attempting to fire method ${method_config[1]} (${method_params})")
                // Wrap in a transaction.
                KBComponent.withTransaction{ def trans_status ->
                  try{
                    // Just try and fire the method.
                    target.invokeMethod("${method_config[1]}", method_params ? method_params as Object[] : null)
                    // Save the object.
                    target.save(failOnError: true)
                  }
                  catch (Throwable t){
                    // Rollback and log error.
                    trans_status.setRollbackOnly()
                    t.printStackTrace()
                    log.error("${t}")
                  }
                }
                // target.save(flush: true, failOnError:true)
                log.debug("After transaction: ${target?.status}")
              }
              result.objects_to_action.each{
                log.debug("${it.status}")
              }
              break
            case "setStatus":
              log.debug("SetStatus: ${method_config[1]}")
              def status_to_set = RefdataCategory.lookup('KBComponent.Status', method_config[1])
              // def ota_ids = result.objects_to_action.collect{ it.id }
              if (status_to_set){
                def res = KBComponent.executeUpdate("update KBComponent as kbc set kbc.status = :st where kbc IN (:clist)", [st: status_to_set, clist: result.objects_to_action])
                log.debug("Updated status of ${res} components")
              }
              break
          }
          // Do stuff
          redirect(url: result.ref)
          break
        case 'workflow':
          render view: action_config.view, model: result
          break
        case 'process':
          this."${action_config.method}"(result.objects_to_action)
          break
        default:
          flash.error = "Invalid action type information: ${action_config.actionType}".toString()
          break
      }
    }
    else{
      flash.error = "Unable to locate action config for ${params.selectedBulkAction}".toString()
      log.warn("Unable to locate action config for ${params.selectedBulkAction}")
      redirect(url: result.ref)
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def startTitleChange(){

    log.debug("startTitleChange(${params})")

    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save()
    def transfer_type = RefdataCategory.lookupOrCreate('Activity.Type', 'TitleChange').save()
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    def combo_ti_tipps = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')

    def titleChangeData = [:]
    titleChangeData.title_ids = []
    titleChangeData.tipps = [:]
    titleChangeData.beforeTitles = params.list('beforeTitles')
    titleChangeData.afterTitles = params.list('afterTitles')
    titleChangeData.eventDate = params.list('eventDate')
    def first_title = null

    def sw = new StringWriter()

    // Iterate through before titles.. For each one of these will will close out any existing tipps
    params.list('beforeTitles').each{ title_oid ->
      log.debug("process ${title_oid}")
      if (first_title == null){
        first_title = title_oid
      }
      else{
        sw.write(', ')
      }

      def title_obj = genericOIDService.resolveOID2(title_oid)
      sw.write(title_obj.name)

      titleChangeData.title_ids.add(title_obj.id)

      def tipps = TitleInstancePackagePlatform.executeQuery(
          'select tipp from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent=? and c.toComponent=tipp  and tipp.status <> ? and c.type = ?',
          [title_obj, status_deleted, combo_ti_tipps])
      tipps.each{ tipp ->

        if ((tipp.status != status_deleted) && (tipp.pkg.scope?.value != 'GOKb Master')){

          log.debug("Add tipp to discontinue ${tipp}")

          titleChangeData.tipps[tipp.id] = [
              oldTippValue: [
                  title_id   : tipp.title.id,
                  package_id : tipp.pkg.id,
                  platform_id: tipp.hostPlatform.id,
                  startDate  : tipp.startDate ? dateFormatService.formatDate(tipp.startDate) : null,
                  startVolume: tipp.startVolume,
                  startIssue : tipp.startIssue,
                  endDate    : tipp.endDate ? dateFormatService.formatDate(tipp.endDate) : null,
                  endVolume  : tipp.endVolume,
                  endIssue   : tipp.endIssue,
                  url        : tipp.url
              ],
              newtipps    : []
          ]

          params.list('afterTitles').each{ new_title_oid ->
            def new_title_obj = genericOIDService.resolveOID2(new_title_oid)
            def new_tipp_info = [
                title_id   : new_title_obj.id,
                package_id : tipp.pkg.id,
                platform_id: tipp.hostPlatform.id,
                startDate  : tipp.startDate ? dateFormatService.formatDate(tipp.startDate) : null,
                startVolume: tipp.startVolume,
                startIssue : tipp.startIssue,
                endDate    : tipp.endDate ? dateFormatService.formatDate(tipp.endDate) : null,
                endVolume  : tipp.endVolume,
                url        : tipp.url,
                endIssue   : tipp.endIssue]
            titleChangeData.tipps[tipp.id].newtipps.add(new_tipp_info)
          }
        }
      }
    }

    def builder = new JsonBuilder()
    builder(titleChangeData)

    def new_activity = new Activity(
        activityName: "Title Change ${sw.toString()}",
        activityData: builder.toString(),
        owner: request.user,
        status: active_status,
        type: transfer_type).save(flush: true)

    log.debug("redirect to edit activity (Really title) ${builder.toString()}")

    // if ( first_title )
    //   redirect(controller:'resource', action:'show', id:first_title)
    // else
    //   redirect(controller:'home', action:'index')

    redirect(action: 'editTitleChange', id: new_activity.id)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def startTitleMerge(){

    log.debug("startTitleMerge(${params})")

    def user = springSecurityService.currentUser
    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save()
    def transfer_type = RefdataCategory.lookupOrCreate('Activity.Type', 'TitleMerge').save()
    def first_title = null

    def result = [:]
    result.oldTitles = []

    def activity_data = [:]

    def oldIds = params.list('beforeTitles')

    activity_data.oldTitles = params.list('beforeTitles')
    activity_data.newTitle = params.newTitle

    def sw = new StringWriter()

    log.debug("Titles to replace: ${oldIds}")

    oldIds.each{ oid ->
      if (first_title == null){
        first_title = oid
      }
      else{
        sw.write(', ')
      }

      def title_obj = genericOIDService.resolveOID2(oid)

      sw.write(title_obj.name)

      result.oldTitles.add(title_obj)
    }

    result.newTitle = genericOIDService.resolveOID2(params.newTitle)

    def builder = new JsonBuilder()
    builder(activity_data)

    def new_activity = new Activity(
        activityName: "Title Merge ${sw.toString()}",
        activityData: builder.toString(),
        owner: user,
        status: active_status,
        type: transfer_type).save(flush: true)

    log.debug("redirect to edit activity (Really title) ${builder.toString()}")

    // if ( first_title )
    //   redirect(controller:'resource', action:'show', id:first_title)
    // else
    //   redirect(controller:'home', action:'index')

    redirect(action: 'editTitleMerge', id: new_activity.id)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def editTitleMerge(){
    log.debug("editTitleMerge() - ${params}")

    def activity_record = Activity.get(params.id)
    def activity_data = new JsonSlurper().parseText(activity_record.activityData)
    def merge_params = [:]

    request.getParameterNames().each{ pn ->
      if (pn.startsWith("merge_")){
        merge_params[pn] = request.getParameter(pn)
      }
    }

    if (params.update){
      log.debug("Update...")
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString()
      activity_record.save(flush: true)
    }
    else if (params.process){
      log.debug("Process...")

      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString()
      activity_record.save(flush: true)

      processTitleMerge(activity_record, activity_data, merge_params)
      if (activity_data.newTitle?.size() > 0){
        redirect(controller: 'resource', action: 'show', id: activity_data.newTitle)
      }
      else{
        redirect(controller: 'home', action: 'index')
      }
    }
    else if (params.abandon){
      log.debug("**ABANDON**...")
      activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned')
      activity_record.save(flush: true)
      if (activity_data.oldTitles?.size() > 0){
        redirect(controller: 'resource', action: 'show', id: activity_data.oldTitles[0])
      }
      else{
        redirect(controller: 'home', action: 'index')
      }
    }

    log.debug("Processing...")

    def result = [:]
    result.oldTitles = []
    result.newTitle = genericOIDService.resolveOID2(activity_data.newTitle)
    result.d = activity_record

    activity_data.oldTitles.each{ oid ->
      result.oldTitles.add(genericOIDService.resolveOID2(oid))
    }

    result.id = params.id

    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def startTitleTransfer(){
    log.debug("startTitleTransfer")
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
    params.each{ p ->
      if ((p.key.startsWith('tt:')) && (p.value) && (p.value instanceof String)){
        def tt = p.key.substring(3)
        log.debug("Title to transfer: \"${tt}\"")
        def title_instance = TitleInstance.get(tt)
        // result.objects_to_action.add(genericOIDService.resolveOID2(oid_to_action))
        // Find all tipps for the title and add to tipps
        if (title_instance){
          if (first == true){
            first = false
          }
          else{
            sw.write(", ")
          }

          sw.write(title_instance.name)

          result.titles.add(title_instance)
          titleTransferData.title_ids.add(title_instance.id)
          title_instance.tipps.each{ tipp ->
            if ((tipp.status?.value != 'Deleted') && (tipp.pkg.scope?.value != 'GOKb Master')){
              result.tipps.add(tipp)
              titleTransferData.tipps[tipp.id] = [
                  oldTippValue: [
                      title_id   : tipp.title.id,
                      package_id : tipp.pkg.id,
                      platform_id: tipp.hostPlatform.id,
                      startDate  : tipp.startDate ? dateFormatService.formatDate(tipp.startDate) : null,
                      startVolume: tipp.startVolume,
                      startIssue : tipp.startIssue,
                      endDate    : tipp.endDate ? dateFormatService.formatDate(tipp.endDate) : null,
                      endVolume  : tipp.endVolume,
                      endIssue   : tipp.endIssue,
                      url        : tipp.url
                  ],
                  newtipps    : []
              ]
            }
          }
        }
        else{
          log.error("Unable to locate title with that ID")
        }
      }
    }

    log.debug("loaded Title Data.. Looking up publisher")
    result.newPublisher = genericOIDService.resolveOID2(params.title)

    log.debug("Assigning new publisher")
    titleTransferData.newPublisherId = result.newPublisher.id

    log.debug("Build title transfer record")
    def builder = new JsonBuilder()
    builder(titleTransferData)

    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save()
    def transfer_type = RefdataCategory.lookupOrCreate('Activity.Type', 'TitleTransfer').save()


    log.debug("Create activity")
    def new_activity = new Activity(
        activityName: "Title transfer ${sw.toString()} to ${result.newPublisher.name}",
        activityData: builder.toString(),
        owner: user,
        status: active_status,
        type: transfer_type).save(flush: true)

    log.debug("Redirect to edit title transfer activity")
    redirect(action: 'editTitleTransfer', id: new_activity.id)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def editTitleTransfer(){
    log.debug("editTitleTransfer() - ${params}")

    def activity_record = Activity.get(params.id)
    def activity_data = new JsonSlurper().parseText(activity_record.activityData)

    // Pull in all updated tipp properties like start volumes, etc.
    request.getParameterNames().each{ pn ->
      def value = request.getParameter(pn)
      log.debug("Checking ${pn} : ${value}")
      if (pn.startsWith('_tippdata')){
        def key_components = pn.split(':')
        if (activity_data.tipps[key_components[1]] != null){
          if ((value != null) && (value.length() > 0)){
            activity_data.tipps[key_components[1]].newtipps[Integer.parseInt(key_components[2])][key_components[3]] = value
          }
          else{
            activity_data.tipps[key_components[1]].newtipps[Integer.parseInt(key_components[2])][key_components[3]] = null
          }
        }
        else{
          log.error("Unable to locate data for tipp ${key_components[1]} in ${activity_data}")
        }
      }
      else if (pn.startsWith('_oldtipp')){
        def key_components = pn.split(':')

        if (activity_data.tipps[key_components[1]].oldTippValue == null){
          activity_data.tipps[key_components[1]].oldTippValue = [:]
        }

        if ((value != null) && (value.length() > 0)){
          activity_data.tipps[key_components[1]].oldTippValue[key_components[2]] = value
        }
        else{
          activity_data.tipps[key_components[1]].oldTippValue[key_components[2]] = null
        }
      }
    }

    if (params.addTransferTipps){
      // Add Transfer tipps
      log.debug("Add transfer tipps")
      if ((params.Package != null) && (params.Platform != null)){
        def new_tipp_package = genericOIDService.resolveOID2(params.Package)
        def new_tipp_platform = genericOIDService.resolveOID2(params.Platform)
        if ((new_tipp_package != null) && (new_tipp_platform != null)){
          params.each{ p ->
            if (p.key.startsWith('addto-')){
              def tipp_id = p.key.substring(6)
              log.debug("Add new tipp for ${new_tipp_package}, ${new_tipp_platform} to replace ${tipp_id}")
              def old_tipp = KBComponent.get(tipp_id)
              log.debug("Old Tipp: ${old_tipp}")
              def tipp_info = activity_data.tipps[tipp_id]

              if (tipp_info != null){

                if (tipp_info.newtipps == null){
                  tipp_info.newtipps = [:]
                }

                def new_tipp_info = [
                    title_id   : old_tipp.title.id,
                    package_id : new_tipp_package.id,
                    platform_id: new_tipp_platform.id,
                    startDate  : old_tipp.startDate ? dateFormatService.formatDate(old_tipp.startDate) : null,
                    startVolume: old_tipp.startVolume,
                    startIssue : old_tipp.startIssue,
                    endDate    : old_tipp.endDate ? dateFormatService.formatDate(old_tipp.endDate) : null,
                    endVolume  : old_tipp.endVolume,
                    endIssue   : old_tipp.endIssue,
                    url        : old_tipp.url]
                log.debug("new_tipp_info :: ${new_tipp_info}")
                tipp_info.newtipps.add(new_tipp_info)
              }
              else{
                log.error("Unable to find key (${tipp_id}) In map: ${activity_data.tipps}")
              }
            }
          }

          // Update the activity data in the database
          def builder = new JsonBuilder()
          builder(activity_data)
          activity_record.activityData = builder.toString()
          activity_record.save(flush: true)
        }
        else{
          log.error("Add transfer tipps but failed to resolve package(${params.Package}) or platform(${params.Platform})")
        }
      }
      else{
        log.error("Add transfer tipps but package or platform not set")
      }
    }
    else if (params.update){
      log.debug("Update...")
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString()
      activity_record.save(flush: true)
    }
    else if (params.remove){
      log.debug("remove... ${params.remove}")
      def remove_components = params.remove.split(':')
      activity_data.tipps[remove_components[0]].newtipps.remove(Integer.parseInt(remove_components[1]))
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString()
      activity_record.save(flush: true)
    }
    else if (params.process){
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString()
      activity_record.save(flush: true)

      log.debug("Process...")

      processTitleTransfer(activity_record, activity_data)

      if (activity_data.title_ids?.size() > 0){
        redirect(controller: 'resource', action: 'show', id: 'org.gokb.cred.TitleInstance:' + activity_data.title_ids[0])
      }
      else{
        redirect(controller: 'home', action: 'index')
      }
    }
    else if (params.abandon){
      log.debug("**ABANDON**...")
      activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned')
      activity_record.save(flush: true)
      if (activity_data.title_ids?.size() > 0){
        redirect(controller: 'resource', action: 'show', id: 'org.gokb.cred.TitleInstance:' + activity_data.title_ids[0])
      }
      else{
        redirect(controller: 'home', action: 'index')
      }
    }

    log.debug("Processing...")

    def result = [:]
    result.titles = []
    result.tipps = []
    result.d = activity_record

    activity_data.title_ids.each{ tid ->
      result.titles.add(TitleInstance.get(tid))
    }


    activity_data.tipps.each{ tipp_info ->
      def tipp_object = TitleInstancePackagePlatform.get(tipp_info.key)
      result.tipps.add([
          id          : tipp_object.id,
          type        : 'CURRENT',
          title       : tipp_object.title,
          pkg         : tipp_object.pkg,
          hostPlatform: tipp_object.hostPlatform,
          startDate   : tipp_info.value.oldTippValue?.startDate,
          startVolume : tipp_info.value.oldTippValue?.startVolume,
          startIssue  : tipp_info.value.oldTippValue?.startIssue,
          endDate     : tipp_info.value.oldTippValue?.endDate,
          endVolume   : tipp_info.value.oldTippValue?.endVolume,
          endIssue    : tipp_info.value.oldTippValue?.endIssue,
          url         : tipp_info.value.oldTippValue?.url
      ])
      int seq = 0
      // .value because tipp_info is a map...
      tipp_info.value.newtipps.each{ newtipp_info ->
        result.tipps.add([
            type        : 'NEW',
            parent      : tipp_object.id,
            seq         : seq++,
            title       : KBComponent.get(newtipp_info.title_id),
            pkg         : KBComponent.get(newtipp_info.package_id),
            hostPlatform: KBComponent.get(newtipp_info.platform_id),
            startDate   : newtipp_info.startDate,
            startVolume : newtipp_info.startVolume,
            startIssue  : newtipp_info.startIssue,
            endDate     : newtipp_info.endDate,
            endVolume   : newtipp_info.endVolume,
            endIssue    : newtipp_info.endIssue,
            review      : newtipp_info.review,
            url         : newtipp_info.url
        ])
      }
    }

    result.newPublisher = Org.get(activity_data.newPublisherId)
    result.id = params.id

    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def editTitleChange(){
    log.debug("editTitleChange() - ${params}")

    def activity_record = Activity.get(params.id)
    def activity_data = new JsonSlurper().parseText(activity_record.activityData)


    // Pull in all updated tipp properties like start volumes, etc.
    request.getParameterNames().each{ pn ->
      def value = request.getParameter(pn)
      if (pn.startsWith('_tippdata')){
        def key_components = pn.split(':')

        log.debug("Set ${key_components} = ${value}")

        if (activity_data.tipps[key_components[1]] != null){
          if ((value != null) && (value.length() > 0)){
            activity_data.tipps[key_components[1]].newtipps[Integer.parseInt(key_components[2])][key_components[3]] = value
          }
          else{
            activity_data.tipps[key_components[1]].newtipps[Integer.parseInt(key_components[2])][key_components[3]] = null
          }
        }
        else{
          log.error("Unable to locate data for tipp ${key_components[1]} in ${activity_data}")
        }
      }
      else if (pn.startsWith('_oldtipp')){
        def key_components = pn.split(':')
        log.debug("Set ${key_components} = ${value}")
        if (activity_data.tipps[key_components[1]].oldTippValue == null){
          activity_data.tipps[key_components[1]].oldTippValue = [:]
        }

        if ((value != null) && (value.length() > 0)){
          activity_data.tipps[key_components[1]].oldTippValue[key_components[2]] = value
        }
        else{
          activity_data.tipps[key_components[1]].oldTippValue[key_components[2]] = null
        }
      }
    }

    if (params.update){
      log.debug("Update...")
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString()
      activity_record.save(flush: true)
    }
    else if (params.remove){
      log.debug("remove... ${params.remove}")
      def remove_components = params.remove.split(':')
      activity_data.tipps[remove_components[0]].newtipps.remove(Integer.parseInt(remove_components[1]))
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString()
      activity_record.save(flush: true)
    }
    else if (params.process){
      log.debug("Process...")
      def builder = new JsonBuilder()
      builder(activity_data)
      activity_record.activityData = builder.toString()
      activity_record.save(flush: true)

      processTitleChange(activity_record, activity_data)
      if (activity_data.title_ids?.size() > 0){
        redirect(controller: 'resource', action: 'show', id: 'org.gokb.cred.TitleInstance:' + activity_data.title_ids[0])
      }
      else{
        redirect(controller: 'home', action: 'index')
      }
    }
    else if (params.abandon){
      log.debug("**ABANDON**...")
      activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned')
      activity_record.save(flush: true)
      if (activity_data.title_ids?.size() > 0){
        redirect(controller: 'resource', action: 'show', id: 'org.gokb.cred.TitleInstance:' + activity_data.title_ids[0])
      }
      else{
        redirect(controller: 'home', action: 'index')
      }
    }

    log.debug("Processing...")
    def result = [:]
    result.titles = []
    result.tipps = []
    result.d = activity_record

    activity_data.title_ids.each{ tid ->
      result.titles.add(TitleInstance.get(tid))
    }


    activity_data.tipps.each{ tipp_info ->
      def tipp_object = TitleInstancePackagePlatform.get(tipp_info.key)
      result.tipps.add([
          id          : tipp_object.id,
          type        : 'CURRENT',
          title       : tipp_object.title,
          pkg         : tipp_object.pkg,
          hostPlatform: tipp_object.hostPlatform,
          startDate   : tipp_info.value.oldTippValue?.startDate,
          startVolume : tipp_info.value.oldTippValue?.startVolume,
          startIssue  : tipp_info.value.oldTippValue?.startIssue,
          endDate     : tipp_info.value.oldTippValue?.endDate,
          endVolume   : tipp_info.value.oldTippValue?.endVolume,
          endIssue    : tipp_info.value.oldTippValue?.endIssue,
          url         : tipp_info.value.oldTippValue?.url
      ])
      int seq = 0
      tipp_info.value.newtipps.each{ newtipp_info ->
        result.tipps.add([
            type        : 'NEW',
            parent      : tipp_object.id,
            seq         : seq++,
            title       : KBComponent.get(newtipp_info.title_id),
            pkg         : KBComponent.get(newtipp_info.package_id),
            hostPlatform: KBComponent.get(newtipp_info.platform_id),
            startDate   : newtipp_info.startDate,
            startVolume : newtipp_info.startVolume,
            startIssue  : newtipp_info.startIssue,
            endDate     : newtipp_info.endDate,
            endVolume   : newtipp_info.endVolume,
            endIssue    : newtipp_info.endIssue,
            review      : newtipp_info.review,
            url         : newtipp_info.url
        ])
      }
    }

    result.id = params.id

    result
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processTitleChange(activity_record, activity_data){

    activity_data.tipps.each{ tipp_map_entry ->

      def current_tipp = TitleInstancePackagePlatform.get(tipp_map_entry.key)

      tipp_map_entry.value.newtipps.each{ newtipp ->
        log.debug("Process new tipp : ${newtipp}")
        def new_package = Package.get(newtipp.package_id)
        def new_platform = Platform.get(newtipp.platform_id)

        def new_title = TitleInstance.get(newtipp.title_id)
        // def new_tipp = new TitleInstancePackagePlatform(
        def new_tipp = TitleInstancePackagePlatform.upsertDTO([
            package    : ['internalId': new_package.id],
            platform   : ['internalId': new_platform.id],
            title      : ['internalId': current_tipp.title.id],
            startDate  : newtipp.startDate,
            startVolume: newtipp.startVolume,
            startIssue : newtipp.startIssue,
            endDate    : newtipp.endDate,
            endVolume  : newtipp.endVolume,
            endIssue   : newtipp.endIssue,
            url        : newtipp.url
        ], user).save(flush: true, failOnError: true)

        if (newtipp.review == 'on'){
          reviewRequestService.raise(new_tipp, 'New tipp - please review', 'A Title change cause this new tipp to be created', request.user)
        }
      }


      // Update old tipp
      def parsed_start_date = null
      def parsed_end_date = null
      try{
        parsed_start_date = tipp_map_entry.value.oldTippValue.startDate ? dateFormatService.parseDate(tipp_map_entry.value.oldTippValue.startDate) : null
        parsed_end_date = tipp_map_entry.value.oldTippValue.endDate ? dateFormatService.parseDate(tipp_map_entry.value.oldTippValue.endDate) : null
      }
      catch (Exception e){
      }

      current_tipp.startDate = parsed_start_date
      current_tipp.startVolume = tipp_map_entry.value.oldTippValue.startVolume
      current_tipp.startIssue = tipp_map_entry.value.oldTippValue.startIssue
      current_tipp.endDate = parsed_end_date
      current_tipp.endVolume = tipp_map_entry.value.oldTippValue.endVolume
      current_tipp.endIssue = tipp_map_entry.value.oldTippValue.endIssue
      log.debug("Saving current tipp")
      current_tipp.save()


      // Retire the tipp if
      if (params["oldtipp_close:${tipp_map_entry.key}"] == 'on'){
        log.debug("Retiring old tipp")
        current_tipp.status = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
        if (params["oldtipp_review:${tipp_map_entry.key}"] == 'on'){
          reviewRequestService.raise(current_tipp, 'please review TIPP record', 'A Title change has affected this tipp [new tipps have been generated]. The user chose to retire this tipp', request.user)
        }
      }
      else{
        if (params["oldtipp_review:${tipp_map_entry.key}"] == 'on'){
          reviewRequestService.raise(current_tipp, 'please review TIPP record', 'A Title change has affected this tipp [new tipps have been generated]. The user did not retire this tipp', request.user)
        }
      }
    }


    // Default to today if not set
    def event_date = activity_data.eventDate ?: dateFormatService.formatDate(new Date())
// Create title history event
    def newTitleHistoryEvent = new ComponentHistoryEvent(eventDate: dateFormatService.parseDate(event_date)).save()

    activity_data.afterTitles?.each{ at ->
      def component = genericOIDService.resolveOID2(at)
      def after_participant = new ComponentHistoryEventParticipant(event: newTitleHistoryEvent,
          participant: component,
          participantRole: 'out').save()
    }

    activity_data.beforeTitles?.each{ bt ->
      def component = genericOIDService.resolveOID2(bt)
      def after_participant = new ComponentHistoryEventParticipant(event: newTitleHistoryEvent,
          participant: component,
          participantRole: 'in').save()
    }


    activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
    activity_record.save(flush: true)
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processTitleMerge(activity_record, activity_data, merge_params){
    log.debug("processTitleMerge ${params}\n\n ${activity_data}")
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
    def rr_status_current = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open')

    def new_ti = genericOIDService.resolveOID2(activity_data.newTitle)
    def new_he = new_ti.getTitleHistory()

    activity_data.oldTitles.each{ oid ->
      def old_ti = genericOIDService.resolveOID2(oid)

      if (!old_ti.name.equals(new_ti.name)){
        def added = new_ti.addVariantTitle(old_ti.name)
      }

      if (merge_params['merge_ids']){
        log.debug("Looking for new IDs to add")
        def id_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids')

        old_ti.ids.each{ old_id ->

          def old_combo = Combo.findByFromComponentAndToComponent(old_ti, old_id)

          def dupes = Combo.executeQuery("Select c from Combo as c where c.toComponent.id = ? and c.fromComponent.id = ? and c.type.id = ?", [old_id.id, new_ti.id, id_combo_type.id])
          if (!dupes || dupes.size() == 0){
            log.debug("Adding Identifier ${old_id} to ${new_ti}")
            Combo new_id = new Combo(toComponent: old_id, fromComponent: new_ti, type: id_combo_type, status: old_combo.status).save(flush: true, failOnError: true)
          }
          else{
            log.debug("Identifier ${old_id} is already connected to ${new_ti}..")
          }
        }
      }
      if (merge_params['merge_vn']){
        old_ti.variantNames.each{ old_vn ->
          new_ti.addVariantTitle(old_vn.variantName)
        }
      }
      if (merge_params['merge_pb']){
        old_ti.publisher.each{ old_pb ->
          if (!new_ti.publisher.contains(old_pb)){
            new_ti.publisher.add(old_pb)
          }
        }
      }
      if (merge_params['merge_he']){
        def ti_history = old_ti.getTitleHistory()
        ti_history.each{ ohe ->
          def new_from = []
          def new_to = []
          def dupe = false
          if (ohe.to.contains(old_ti)){
            ohe.to.removeIf{ it == old_ti }
            ohe.to.add(new_ti)
            new_to = ohe.to
            ohe.from.each{ hep ->
              def he_match = ComponentHistoryEvent.executeQuery("select che from ComponentHistoryEvent as che where exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = :fromPart) AND exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = :toPart)", [fromPart: hep, toPart: new_ti])
              if (he_match){
                dupe = true
              }
            }
            new_from = ohe.from
          }
          else if (ohe.from.contains(old_ti)){
            ohe.from.removeIf{ it == old_ti }
            ohe.from.add(new_ti)
            new_from = ohe.from
            ohe.from.each{ hep ->
              def he_match = ComponentHistoryEvent.executeQuery("select che from ComponentHistoryEvent as che where exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = :fromPart) AND exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = :toPart)", [fromPart: new_ti, toPart: hep])
              if (he_match){
                dupe = true
              }
            }
            new_to = ohe.to
          }
          if (!dupe){
            def he = new ComponentHistoryEvent()
            if (ohe.date){
              he.eventDate = ohe.date
            }
            he.save(flush: true, failOnError: true)
            new_from.each{
              def hep = new ComponentHistoryEventParticipant(event: he, participant: it, participantRole: 'in')
              hep.save(flush: true, failOnError: true)
            }
            new_to.each{
              def hep = new ComponentHistoryEventParticipant(event: he, participant: it, participantRole: 'out')
              hep.save(flush: true, failOnError: true)
            }
          }
        }
      }

      def events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant = :component", [component: old_ti])

      events_to_delete.each{
        it.delete(flush: true)
      }
      old_ti.tipps.each{ old_tipp ->
        if (merge_params['merge_tipps'] && old_tipp.status == status_current){
          def tipp_dto = [:]
          tipp_dto.package = ['internalId': old_tipp.pkg.id]
          tipp_dto.platform = ['internalId': old_tipp.hostPlatform.id]
          tipp_dto.title = ['internalId': new_ti.id]
          if (old_tipp.paymentType?.value) tipp_dto.paymentType = old_tipp.paymentType?.value
          tipp_dto.url = old_tipp.url ?: ""
          tipp_dto.coverage = []

          old_tipp.coverageStatements.each{ otcs ->
            def cst = [
                'startVolume'  : otcs.startVolume ?: "",
                'startIssue'   : otcs.startIssue ?: "",
                'endVolume'    : otcs.endVolume ?: "",
                'endIssue'     : otcs.endIssue ?: "",
                'embargo'      : otcs.embargo ?: "",
                'coverageNote' : otcs.coverageNote ?: "",
                'startDate'    : otcs.startDate ? dateFormatService.formatTimestampMs(otcs.startDate) : "",
                'endDate'      : otcs.endDate ? dateFormatService.formatTimestampMs(otcs.endDate) : "",
                'coverageDepth': old_tipp.coverageDepth?.value ?: ""
            ]
            tipp_dto.coverage.add(cst)
          }
          def new_tipp = TitleInstancePackagePlatform.upsertDTO(tipp_dto, request.user)
          log.debug("Added new TIPP ${new_tipp} to TI ${new_ti}")
        }
        old_tipp.status = status_deleted
      }
      old_ti.reviewRequests.each{ rr ->
        def rr_context = [:]
        rr_context['user'] = request.user

        if (rr.status == rr_status_current){
          rr.RRClose(rr_context)
        }
      }
      old_ti.status = status_deleted
    }

    activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
    activity_record.save(flush: true)
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processTitleTransfer(activity_record, activity_data){
    log.debug("processTitleTransfer ${params}\n\n ${activity_data}")
    def user = springSecurityService.currentUser

    def publisher = Org.get(activity_data.newPublisherId)
    // Step one : Close off existing title publisher links and create new publisher links
    activity_data.title_ids.each{ title_id ->
      log.debug("Process title_id ${title_id} and change publisher to ${publisher}")
      def title = TitleInstance.get(title_id)
      title.changePublisher(publisher)
      title.save(flush: true)
    }

    // Step two : Process TIPP adjustments
    activity_data.tipps.each{ tipp_map_entry ->
      def current_tipp = TitleInstancePackagePlatform.get(tipp_map_entry.key)
      log.debug("Processing current tipp : ${current_tipp.id}")
      tipp_map_entry.value.newtipps.each{ newtipp ->
        log.debug("Process new tipp : ${newtipp}")
        if (tipp_map_entry.value.oldTippValue?.startDate){
          try{
            current_tipp.startDate = dateFormatService.parseDate(tipp_map_entry.value.oldTippValue?.startDate)
          }
          catch (Exception e){
          }
        }
        if (tipp_map_entry.value.oldTippValue?.startVolume){
          current_tipp.startVolume = tipp_map_entry.value.oldTippValue?.startVolume
        }
        if (tipp_map_entry.value.oldTippValue?.startIssue){
          current_tipp.startIssue = tipp_map_entry.value.oldTippValue?.startIssue
        }

        if (tipp_map_entry.value.oldTippValue?.endDate){
          try{
            current_tipp.endDate = dateFormatService.parseDate(tipp_map_entry.value.oldTippValue?.endDate)
          }
          catch (Exception e){
          }
        }
        if (tipp_map_entry.value.oldTippValue?.endVolume){
          current_tipp.endVolume = tipp_map_entry.value.oldTippValue?.endVolume
        }
        if (tipp_map_entry.value.oldTippValue?.endIssue){
          current_tipp.endIssue = tipp_map_entry.value.oldTippValue?.endIssue
        }

        def new_package = Package.get(newtipp.package_id)
        def new_platform = Platform.get(newtipp.platform_id)

        // def new_tipp = new TitleInstancePackagePlatform(
        def new_tipp = TitleInstancePackagePlatform.upsertDTO([
            package    : ['internalId': new_package.id],
            platform   : ['internalId': new_platform.id],
            title      : ['internalId': current_tipp.title.id],
            startDate  : newtipp.startDate,
            startVolume: newtipp.startVolume,
            startIssue : newtipp.startIssue,
            endDate    : newtipp.endDate,
            endVolume  : newtipp.endVolume,
            endIssue   : newtipp.endIssue,
            url        : newtipp.url
        ], user).save(flush: true, failOnError: true)

        if (newtipp.review == 'on'){
          log.debug("User requested a review request be generated for this new tipp")
          reviewRequestService.raise(new_tipp, 'New tipp - please review', 'A Title transfer cause this new tipp to be created', request.user)
        }
      }

      // Retire the tipp if
      log.debug("Checking close flags..${params}")
      if (params["oldtipp_close:${tipp_map_entry.key}"] == 'on'){
        log.debug("Retiring old tipp")
        current_tipp.status = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
        if (params["oldtipp_review:${tipp_map_entry.key}"] == 'on'){
          reviewRequestService.raise(current_tipp, 'please review TIPP record', 'A Title transfer has affected this tipp [new tipps have been generated]. The user chose to retire this tipp', request.user)
        }
      }
      else{
        if (params["oldtipp_review:${tipp_map_entry.key}"] == 'on'){
          reviewRequestService.raise(current_tipp, 'please review TIPP record', 'A Title transfer has affected this tipp [new tipps have been generated]. The user did not retire this tipp', request.user)
        }
      }

      def parsed_start_date = null
      def parsed_end_date = null
      try{
        parsed_start_date = tipp_map_entry.value.oldTippValue.startDate ? dateFormatService.parseDate(tipp_map_entry.value.oldTippValue.startDate) : null
        parsed_end_date = tipp_map_entry.value.oldTippValue.endDate ? dateFormatService.parseDate(tipp_map_entry.value.oldTippValue.endDate) : null
      }
      catch (Exception e){
      }

      current_tipp.startDate = parsed_start_date
      current_tipp.startVolume = tipp_map_entry.value.oldTippValue.startVolume
      current_tipp.startIssue = tipp_map_entry.value.oldTippValue.startIssue
      current_tipp.endDate = parsed_end_date
      current_tipp.endVolume = tipp_map_entry.value.oldTippValue.endVolume
      current_tipp.endIssue = tipp_map_entry.value.oldTippValue.endIssue
      log.debug("Saving current tipp")
      current_tipp.save(flush: true, failOnError: true)
    }

    activity_record.status = RefdataCategory.lookupOrCreate('Activity.Status', 'Complete')
    activity_record.save(flush: true)
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processPackageReplacement(){
    def retired_status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Retired')
    def result = [:]
    result['old'] = []
    result['new'] = ''
    result['count'] = 0

    params.each{ p ->
      log.debug("Testing ${p.key}")
      if ((p.key.startsWith('tt')) && (p.value) && (p.value instanceof String)){
        def tt = p.key.substring(3)
        log.debug("Platform to replace: \"${tt}\"")
        def old_platform = Platform.get(tt)
        def new_platform = genericOIDService.resolveOID2(params.newplatform)
        log.debug("old: ${old_platform} new: ${new_platform}")
        try{
          def updates_count = Combo.executeQuery("select count(combo) from Combo combo where combo.fromComponent = ?", [old_platform])
          Combo.executeUpdate("update Combo combo set combo.fromComponent = ? where combo.fromComponent = ?", [new_platform, old_platform])
          result['count'] += updates_count
          result['old'] += old_platform.name
          result['new'] = new_platform.name
          old_platform.status = retired_status
          old_platform.save(flush: true)
        }
        catch (Exception e){
          log.debug("Problem executing update")
        }
      }
    }
    render view: 'platformReplacementResult', model: [result: result]
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processTippRetire(){
    log.debug("processTippRetire ${params}")
    def retired_status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Retired')
    def result = [:]

    params.list('beforeTipps').each{ title_oid ->
      log.debug("process ${title_oid}")
      def tipp_obj = genericOIDService.resolveOID2(title_oid)
      tipp_obj.status = retired_status
      if (params.endDateSelect == 'select' && params.selectedDate){
        tipp_obj.accessEndDate = params.date('selectedDate', 'yyyy-MM-dd')
      }
      else if (params.endDateSelect == 'now'){
        tipp_obj.accessEndDate = new Date()
      }
      tipp_obj.save(flush: true, failOnError: true)
    }
    redirect(url: params.ref)
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processTippMove(){
    log.debug("processTippMove ${params}")
    def deleted_status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def user = springSecurityService.currentUser
    def new_package = params.newpackage ? genericOIDService.resolveOID2(params.newpackage) : null
    def new_platform = params.newplatform ? genericOIDService.resolveOID2(params.newplatform) : null
    def tipps_to_action = params.list('beforeTipps')
    def new_title = params.newtitle ? genericOIDService.resolveOID2(params.newtitle) : null

    params.list('beforeTipps').each{ tipp_oid ->
      log.debug("process ${tipp_oid}")

      def tipp_obj = genericOIDService.resolveOID2(tipp_oid)
      def coverage = []

      tipp_obj.coverageStatements.each{ cst ->
        coverage.add(['startVolume'  : cst.startVolume ?: "",
                      'startIssue'   : cst.startIssue ?: "",
                      'endVolume'    : cst.endVolume ?: "",
                      'endIssue'     : cst.endIssue ?: "",
                      'embargo'      : cst.embargo ?: "",
                      'coverageNote' : cst.coverageNote ?: "",
                      'startDate'    : cst.startDate ? dateFormatService.formatTimestampMs(cst.startDate) : "",
                      'endDate'      : cst.endDate ? dateFormatService.formatTimestampMs(cst.endDate) : "",
                      'coverageDepth': cst.coverageDepth?.value ?: ""
        ])
      }

      def new_tipp = TitleInstancePackagePlatform.upsertDTO([
          package : ['internalId': (new_package ? new_package.id : tipp_obj.pkg.id)],
          platform: ['internalId': (new_platform ? new_platform.id : tipp_obj.hostPlatform.id)],
          title   : ['internalId': (new_title ? new_title.id : tipp_obj.title.id)],
          coverage: coverage,
          url     : tipp_obj.url
      ], user).save(flush: true, failOnError: true)

      log.debug("Created new TIPP ${new_tipp}")
      tipp_obj.status = deleted_status
      tipp_obj.save(flush: true, failOnError: true)
    }

    redirect(url: params.ref)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def download(){
    log.debug("Download ${params}")
    DataFile df = DataFile.findByGuid(params.id)
    if (df != null){
      //HTML is causing problems, browser thinks it should render something, other way around this?
      response.setContentType("application/octet-stream")
      response.addHeader("Content-Disposition", "attachment; filename=\"${df.uploadName}\"")
      response.outputStream << df.fileData
    }
  }

  /**
   *  authorizeVariant : Used to replace the name of a component by one of its variant names.
   * @param id : The id of the variant name
   */

  // Deprecated – use action in AjaxSupport instead
  @Deprecated
  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def authorizeVariant(){
    log.debug("${params}")
    def result = ['result': 'OK', 'params': params]
    def variant = KBComponentVariantName.get(params.id)

    if (variant != null && variant.owner.isEditable()){
      // Does the current owner.name exist in a variant? If not, we should create one so we don't loose the info
      def current_name_as_variant = variant.owner.variantNames.find{ it.variantName == variant.owner.name }
      if (current_name_as_variant == null){
        log.debug("No variant name found for current name: ${variant.owner.name} ")
        def variant_name = variant.owner.getId()
        if (variant.owner.name){
          variant_name = variant.owner.name
        }
        else if (variant.owner?.respondsTo('getDisplayName') && variant.owner.getDisplayName()){
          variant_name = variant.owner.getDisplayName()?.trim()
        }
        else if (variant.owner?.respondsTo('getName')){
          variant_name = variant.owner?.getName()?.trim()
        }
        def new_variant = new KBComponentVariantName(owner: variant.owner, variantName: variant_name).save(flush: true)
      }
      else{
        log.debug("Found existing variant name: ${current_name_as_variant}")
      }
      variant.variantType = RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType', 'Authorized')
      variant.owner.name = variant.variantName

      if (variant.owner.validate()){
        variant.owner.save(flush: true)
      }
      else{
        result.result = 'ERROR'
        result.code = 400
        result.message = "This name already belongs to another component of the same type!"
        flash.error = "This name already belongs to another component of the same type!"
      }
    }
    else if (!variant){
      result.result = 'ERROR'
      result.code = 404
      result.message = "Could not find variant!"
    }
    else{
      result.result = 'ERROR'
      result.code = 403
      result.message = "Owner object is not editable!"
      flash.error = "Owner object is not editable!"
    }

    withFormat{
      html{
        def redirect_to = request.getHeader('referer')

        if (params.redirect){
          redirect_to = params.redirect
        }
        else if ((params.fragment) && (params.fragment.length() > 0)){
          redirect_to = "${redirect_to}#${params.fragment}"
        }
      }
      json{
        render result as JSON
      }
    }
  }

  /**
   *  deleteVariant : Used to delete a variant name of a component.
   * @param id : The id of the variant name
   */

  // Deprecated – use action in AjaxSupport instead
  @Deprecated
  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deleteVariant(){
    log.debug("${params}")
    def result = ['result': 'OK', 'params': params]
    def variant = KBComponentVariantName.get(params.id)

    if (variant != null && variantOwner.isEditable()){
      def variantOwner = variant.owner
      def variantName = variant.variantName

      variant.delete()
      variantOwner.lastUpdateComment = "Deleted Alternate Name ${variantName}."
      variantOwner.save(flush: true)

      result.owner_oid = "${variantOwner.class.name}:${variantOwner.id}"
      result.deleted_variant = "${variantName}"
    }
    else if (!variant){
      result.result = 'ERROR'
      result.code = 404
      result.message = "Could not find variant!"
    }
    else{
      result.result = 'ERROR'
      result.code = 403
      result.message = "Owner object is not editable!"
    }

    withFormat{
      html{
        def redirect_to = request.getHeader('referer')

        if (params.redirect){
          redirect_to = params.redirect
        }
        else if ((params.fragment) && (params.fragment.length() > 0)){
          redirect_to = "${redirect_to}#${params.fragment}"
        }
      }
      json{
        render result as JSON
      }
    }
  }

  /**
   *  deleteCoverageStatement : Used to delete a TIPPCoverageStatement.
   * @param id : The id of the coverage statement object
   */

  // Deprecated – use action in AjaxSupport instead
  @Deprecated
  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deleteCoverageStatement(){
    log.debug("${params}")
    def result = ['result': 'OK', 'params': params]
    def tcs = TIPPCoverageStatement.get(params.id)
    def tipp = tcs.owner

    if (tcs != null && tipp.isEditable()){
      tcs.delete()
      tipp.lastUpdateComment = "Deleted Coverage Statement."
      tipp.save(flush: true)
    }
    else if (!tcs){
      result.result = 'ERROR'
      result.code = 404
      result.message = "Could not find coverage statement!"
    }
    else{
      result.result = 'ERROR'
      result.code = 403
      result.message = "This TIPP is not editable!"
    }

    withFormat{
      html{
        def redirect_to = request.getHeader('referer')

        if (params.redirect){
          redirect_to = params.redirect
        }
        else if ((params.fragment) && (params.fragment.length() > 0)){
          redirect_to = "${redirect_to}#${params.fragment}"
        }
      }
      json{
        render result as JSON
      }
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processCreateWebHook(){

    log.debug("processCreateWebHook ${params}")
    def result = [:]

    result.ref = params.from

    try{

      def webook_endpoint = null
      if ((params.existingHook != null) && (params.existingHook.length() > 0)){
        log.debug("From existing hook")
        webook_endpoint = genericOIDService.resolveOID2(params.existingHook)
      }
      else{
        webook_endpoint = new WebHookEndpoint(name: params.newHookName,
            url: params.newHookUrl,
            authmethod: Long.parseLong(params.newHookAuth),
            principal: params.newHookPrin,
            credentials: params.newHookCred,
            owner: request.user)
        if (webook_endpoint.save(flush: true)){
        }
        else{
          log.error("Problem saving new webhook endpoint : ${webook_endpoint.errors}")
        }
      }


      params.each{ p ->
        if ((p.key.startsWith('tt:')) && (p.value) && (p.value instanceof String)){
          def tt = p.key.substring(3)
          def wh = new WebHook(oid: tt, endpoint: webook_endpoint)
          if (wh.save(flush: true)){
          }
          else{
            log.error(wh.errors)
          }
        }
      }
    }
    catch (Exception e){
      log.error("Problem", e)
    }

    redirect(url: result.ref)
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processRRTransfer(){
    def result = [:]
    log.debug("processRRTransfer ${params}")
    def new_user_alloc = genericOIDService.resolveOID2(params.allocToUser)

    params.each{ p ->
      if ((p.key.startsWith('tt:')) && (p.value) && (p.value instanceof String)){
        def tt = p.key.substring(3)
        def ReviewRequest rr = ReviewRequest.get(tt)
        log.debug("Process ${tt} - ${rr}")
        rr.needsNotify = true
        rr.allocatedTo = new_user_alloc
        rr.save(flush: true)
        def rra = new ReviewRequestAllocationLog(note: params.note, allocatedTo: new_user_alloc, rr: rr).save(flush: true)
      }
    }

    result.ref = params.from
    redirect(url: result.ref)
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def newRRLink(){
    def new_rr = null
    log.debug("newRRLink ${params}")
    User user = springSecurityService.currentUser
    def stdDesc = params.stdDesc ?: null

    if (params.id){
      def component = KBComponent.findByUuid(params.id)

      if (!component){
        component = KBComponent.get(params.long('id'))
      }

      new_rr = reviewRequestService.raise(component, params.request, "Manual Request", user, null, null, stdDesc)
    }

    redirect(url: request.getHeader('referer') + '#review')
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def createTitleHistoryEvent(){
    log.debug("createTitleHistoryEvent")
    def result = [result: 'OK']

    try{
      if ((params.afterTitles != null) && (params.beforeTitles != null)){
        if (params.afterTitles instanceof java.lang.String){
          params.afterTitles = [params.afterTitles]
        }
        if (params.beforeTitles instanceof java.lang.String){
          params.beforeTitles = [params.beforeTitles]
        }
        def newTitleHistoryEvent = new ComponentHistoryEvent(eventDate: params.date('EventDate', 'yyyy-MM-dd')).save(flush: true)

        params.afterTitles?.each{ at ->
          def component = genericOIDService.resolveOID2(at)
          def after_participant = new ComponentHistoryEventParticipant(event: newTitleHistoryEvent,
              participant: component,
              participantRole: 'out').save(flush: true)
        }
        params.beforeTitles?.each{ bt ->
          def component = genericOIDService.resolveOID2(bt)
          def after_participant = new ComponentHistoryEventParticipant(event: newTitleHistoryEvent,
              participant: component,
              participantRole: 'in').save(flush: true)
        }
      }

    }
    catch (Exception e){
      log.error("problem creating title history event", e)
      result.result = "ERROR"
      flash.error = "History event could not be created!"
      result.message = "There was an error creating the event."
    }
    finally{
      log.debug("Completed createTitleHistoryEvent")
    }

    withFormat{
      html{
        result.ref = request.getHeader('referer')
        redirect(url: result.ref)
      }
      json{
        result.params = (params)

        if (result.result != "ERROR"){
          result.message = "History event was sucessfully created."
        }

        render result as JSON
      }
    }
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deleteTitleHistoryEvent(){

    def result = [:]
    result.ref = request.getHeader('referer')
    def he = ComponentHistoryEvent.get(params.id)
    if (he != null){
      he.delete(flush: true)
    }
    redirect(url: result.ref)
  }


  // @Transactional(readOnly = true)
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  private def packageKBartExport(packages_to_export){
    def filename = null
    if (packages_to_export.size() == 0){
      return
    }

    def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    def combo_pkg_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
    def export_date = dateFormatService.formatDate(new Date())
    if (packages_to_export.size() == 1){
      filename = "GOKb Export : ${packages_to_export[0].provider?.name} : ${packages_to_export[0].name} : ${export_date}.tsv"
    }
    else{
      filename = "GOKb Export : multiple_packages : ${export_date}.tsv"
    }

    try{
      response.setContentType('text/tab-separated-values')
      response.setHeader("Content-disposition", "attachment; filename=\"${filename}\"")
      response.contentType = "text/tab-separated-values" // "text/tsv"
      packages_to_export.each{ pkg ->
        packageService.sendFile(pkg, PackageService.ExportType.KBART, response)
      }
    }
    catch (Exception e){
      log.error("Problem with export", e)
    }
  }


  private writeExportLine(Writer writer, Closure<String> sanitize, TitleInstancePackagePlatform tipp, def tippCoverageStatement){
    writer.write(
        sanitize(tipp.title.name) + '\t' +
            (tipp.title.hasProperty('dateFirstInPrint') ? sanitize(tipp.title.getIdentifierValue('pISBN')) : sanitize(tipp.title.getIdentifierValue('ISSN'))) + '\t' +
            (tipp.title.hasProperty('dateFirstInPrint') ? sanitize(tipp.title.getIdentifierValue('ISBN')) : sanitize(tipp.title.getIdentifierValue('eISSN'))) + '\t' +
            sanitize(tippCoverageStatement.startDate) + '\t' +
            sanitize(tippCoverageStatement.startVolume) + '\t' +
            sanitize(tippCoverageStatement.startIssue) + '\t' +
            sanitize(tippCoverageStatement.endDate) + '\t' +
            sanitize(tippCoverageStatement.endVolume) + '\t' +
            sanitize(tippCoverageStatement.endIssue) + '\t' +
            sanitize(tipp.url) + '\t' +
            (tipp.title.hasProperty('firstAuthor') ? sanitize(tipp.title.firstAuthor) : '') + '\t' +
            sanitize(tipp.title.getId()) + '\t' +
            sanitize(tippCoverageStatement.embargo) + '\t' +
            sanitize(tippCoverageStatement.coverageDepth) + '\t' +
            sanitize(tippCoverageStatement.coverageNote) + '\t' +
            sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
            sanitize(tipp.title.getPrecedingTitleId()) + '\t' +
            (tipp.title.hasProperty('dateFirstInPrint') ? sanitize(tipp.title.dateFirstInPrint) : '') + '\t' +
            (tipp.title.hasProperty('dateFirstOnline') ? sanitize(tipp.title.dateFirstOnline) : '') + '\t' +
            (tipp.title.hasProperty('volumeNumber') ? sanitize(tipp.title.volumeNumber) : '') + '\t' +
            (tipp.title.hasProperty('editionStatement') ? sanitize(tipp.title.editionStatement) : '') + '\t' +
            (tipp.title.hasProperty('firstEditor') ? sanitize(tipp.title.firstEditor) : '') + '\t' +
            '\t' +  // parent_publication_title_id
            sanitize(tipp.title?.medium?.value) + '\t' +  // publication_type
            sanitize(tipp.paymentType?.value) + '\t' +  // access_type
            sanitize(tipp.title.getIdentifierValue('ZDB')) +
            '\n')
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  private def packageTSVExport(packages_to_export){
    def filename = null
    if (packages_to_export.size() == 0){
      return
    }

    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    def combo_pkg_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
    def export_date = dateFormatService.formatDate(new Date())
    if (packages_to_export.size() == 1){
      filename = "GOKb Export : ${packages_to_export[0].provider?.name} : ${packages_to_export[0].name} : ${export_date}.tsv"
    }
    else{
      filename = "GOKb Export : multiple_packages : ${export_date}.tsv"
    }

    try{
      response.setContentType('text/tab-separated-values')
      response.setHeader("Content-disposition", "attachment; filename=\"${filename}\"")
      response.contentType = "text/tab-separated-values" // "text/tsv"

      packages_to_export.each{ pkg ->
        packageService.sendFile(pkg, PackageService.ExportType.TSV, response)
      }
    }
    catch (Exception e){
      log.error("Problem with export", e)
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def addToRulebase(){
    def result = [:]


    result.ref = request.getHeader('referer')
    log.debug("${params.sourceName}")
    log.debug("${params.sourceId}")

    def source = Source.get(params.sourceId)

    log.debug("Process existing rulebase:: ${source.ruleset}")

    // See if the source rulebase has been initialised
    def parsed_rulebase = source.ruleset ? JSON.parse(source.ruleset) : null
    if (parsed_rulebase == null){
      parsed_rulebase = [rules: [:]]
    }

    def num_probs = params.int('prob_seq_count')

    for (int i = 0; i < num_probs; i++){
      log.debug("addToRulebase ${params.pr['prob_res_' + i]}")
      def resolution = params.pr['prob_res_' + i]

      // If the user has specified what happens in this case, then store the rule in the source for subsequent use
      if (resolution.ResolutionOption){
        log.debug("When ${resolution.probfingerprint} Then ${resolution.ResolutionOption}")
        def rule_resolution = [ruleResolution: "${resolution.ResolutionOption}"]
        parsed_rulebase.rules[resolution.probfingerprint] = rule_resolution
      }
    }

    source.ruleset = parsed_rulebase as JSON
    source.save(flush: true, failOnError: true)

    redirect(url: result.ref)
  }

  @Transactional
  @Secured(['ROLE_EDITOR', 'IS_AUTHENTICATED_FULLY'])
  def deprecateOrg(){
    def result = [:]
    log.debug("Params: ${params}")
    log.debug("otd: ${params.orgsToDeprecate}")
    log.debug("neworg: ${params.neworg}")
    if (params.orgsToDeprecate && params.neworg){
      def orgs = params.list('orgsToDeprecate')
      def neworg = genericOIDService.resolveOID2(params.neworg)

      orgs.each{ org_id ->

        def old_org = Org.get(org_id)

        if (old_org && neworg && old_org.isEditable()){
          log.debug("Got org to deprecate and neworg...  Process now")
          // Updating all combo.toComponent
          // Updating all combo.fromComponent
          def old_from_combos = Combo.executeQuery("from Combo where fromComponent = ?", [old_org])
          def old_to_combos = Combo.executeQuery("from Combo where toComponent = ?", [old_org])

          old_from_combos.each{ oc ->
            def existing_new = Combo.executeQuery("from Combo where type = ? and fromComponent = ? and toComponent = ?", [oc.type, neworg, oc.toComponent])

            if (existing_new?.size() == 0 && oc.toComponent != neworg){
              oc.fromComponent = neworg
              oc.save(flush: true)
            }
            else{
              log.debug("New Combo already exists, or would link item to itself.. deleting instead!")
              oc.status = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
              oc.save(flush: true)
            }
          }
          old_to_combos.each{ oc ->
            def existing_new = Combo.executeQuery("from Combo where type = ? and toComponent = ? and fromComponent = ?", [oc.type, neworg, oc.fromComponent])

            if (existing_new?.size() == 0 && oc.fromComponent != neworg){
              oc.toComponent = neworg
              oc.save(flush: true)
            }
            else{
              log.debug("New Combo already exists, or would link item to itself.. deleting instead!")
              oc.status = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
              oc.save(flush: true)
            }
          }
          flash.success = "Org Deprecation Completed".toString()
        }
        else{
          flash.errors = "Org Deprecation Failed!".toString()
        }
      }
      redirect(controller: 'resource', action: 'show', id: "${neworg.class.name}:${neworg.id}")
    }
  }

  @Transactional
  @Secured(['ROLE_EDITOR', 'IS_AUTHENTICATED_FULLY'])
  def deprecateDeleteOrg(){
    log.debug("deprecateDeleteOrg ${params}")
    def result = [:]
    if (params.orgsToDeprecate){
      def orgs = params.list('orgsToDeprecate')

      orgs.each{ org_id ->
        def o = Org.get(org_id)
        if (o){
          o.deprecateDelete()
        }
      }
    }
    result
  }

  /**
   *  deleteCombo : Used to delete a combo object.
   * @param id : The id of the combo object
   */

  // Deprecated – use action in AjaxSupport instead
  @Deprecated
  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deleteCombo(){
    Combo c = Combo.get(params.id)
    if (c.fromComponent.isEditable()){
      log.debug("Delete combo..")
      c.delete(flush: true)
    }
    else{
      log.debug("Not deleting combo.. no edit permissions on fromComponent!")
    }

    withFormat{
      html{
        def redirect_to = request.getHeader('referer')

        if (params.redirect){
          redirect_to = params.redirect
        }
        else if ((params.fragment) && (params.fragment.length() > 0)){
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to)
      }
      json{
        render result as JSON
      }
    }
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  private def verifyTitleList(packages_to_verify){
    def user = springSecurityService.currentUser

    packages_to_verify.each{ ptv ->
      def pkgObj = Package.get(ptv.id)
      Boolean curated_pkg = false
      def is_curator = null

      if (pkgObj.curatoryGroups && pkgObj.curatoryGroups?.size() > 0){
        is_curator = user.curatoryGroups?.id.intersect(pkgObj.curatoryGroups?.id)
        curated_pkg = true
      }

      if (pkgObj?.isEditable() && (is_curator || !curated_pkg || user.authorities.contains(Role.findByAuthority('ROLE_SUPERUSER')))){
        pkgObj.listStatus = RefdataCategory.lookupOrCreate('Package.ListStatus', 'Checked')
        pkgObj.userListVerifier = user
        pkgObj.listVerifiedDate = new Date()
        pkgObj.save(flush: true, failOnError: true)
      }
    }
    redirect(url: request.getHeader('referer'))
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  private def triggerSourceUpdate(packages_to_update){
    log.info("triggerSourceUpdate for Packages ${packages_to_update}..")
    def user = springSecurityService.currentUser
    def pars = [:]
    def denied = false

    if (packages_to_update.size() > 1){
      flash.error = "Please select a single Package to update!"
    }
    else{
      packages_to_update.each{ ptv ->
        def pkgObj = Package.get(ptv.id)
        Boolean curated_pkg = false
        def is_curator = null

        if (pkgObj && pkgObj.source?.url){
          if (pkgObj.curatoryGroups && pkgObj.curatoryGroups?.size() > 0){
            is_curator = user.curatoryGroups?.id.intersect(pkgObj.curatoryGroups?.id)
            curated_pkg = true
          }

          if (pkgObj?.isEditable() && (is_curator || !curated_pkg || user.authorities.contains(Role.findByAuthority('ROLE_SUPERUSER')))){
            def result = packageService.updateFromSource(pkgObj, user)

            if (result == 'OK'){
              flash.success = "Update successfully started!"
            }
            else if (result == 'ALREADY_RUNNING'){
              flash.warning = "Another update is already running. Please try again later."
            }
            else{
              flash.error = "There have been errors running the job. Please check Source & Package info."
            }
          }
          else{
            flash.error = "Insufficient permissions to update this Package!"
          }
        }
        else if (!pkgObj){
          flash.error = "Unable to reference provided Package!"
        }
        else{
          flash.error = "Please check the Package Source for validity!"
        }
      }
    }
    log.debug('triggerSourceUpdate() done - redirecting')
    redirect(url: request.getHeader('referer'))
  }
}
