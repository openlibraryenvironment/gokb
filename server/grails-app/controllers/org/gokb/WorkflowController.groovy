package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.Transactional

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import org.gokb.cred.*
import org.springframework.security.access.annotation.Secured


class WorkflowController{

  def genericOIDService
  def springSecurityService
  def reviewRequestService
  def componentLookupService
  def packageCSVExportService
  def packageSourceUpdateService
  def dateFormatService
  def concurrencyManagerService
  def titleAugmentService
  def platformService
  def orgService
  def tippUpsertService

  def actionConfig = [
      'method::deleteSoft'     : [actionType: 'simple'],
      'title::transfer'        : [actionType: 'workflow', view: 'titleTransfer'],
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
      'org::transferPackages'  : [actionType: 'workflow', view: 'transferProviderPackages'],
      'org::deprecateReplace'  : [actionType: 'workflow', view: 'deprecateOrg'],
      'org::deprecateDelete'   : [actionType: 'workflow', view: 'deprecateDeleteOrg'],
      'verifyTitleList'        : [actionType: 'process', method: 'verifyTitleList']
  ]

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
            qresult.qbetemplate = grailsApplication.config.getProperty("globalSearchTemplates.$global_qbe_template_shortcode")
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

  def startTitleChange(){
    log.debug("startTitleChange(${params})")

    RefdataValue active_status = RefdataCategory.lookup('Activity.Status', 'Active')
    RefdataValue transfer_type = RefdataCategory.lookup('Activity.Type', 'TitleChange')
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    Map titleChangeData = [:]
    titleChangeData.title_ids = []
    titleChangeData.tipps = [:]
    titleChangeData.beforeTitles = params.list('beforeTitles')
    titleChangeData.afterTitles = params.list('afterTitles')
    titleChangeData.eventDate = params.list('eventDate')
    String first_title = null

    StringWriter sw = new StringWriter()

    // Iterate through before titles.. For each one of these will will close out any existing tipps
    params.list('beforeTitles').each { title_oid ->
      log.debug("process ${title_oid}")
      if (first_title == null){
        first_title = title_oid
      }
      else{
        sw.write(', ')
      }

      TitleInstance title_obj = genericOIDService.resolveOID2(title_oid)
      sw.write(title_obj.name)

      titleChangeData.title_ids.add(title_obj.id)

      List tipps = TitleInstancePackagePlatform.executeQuery(
          'select tipp from TitleInstancePackagePlatform as tipp where tipp.title = :ti tipp.status <> :sd',
          [ti: title_obj, sd: status_deleted])

      tipps.each{ tipp ->
        if ((tipp.status != status_deleted) && (tipp.pkg.scope?.value != 'GOKb Master')) {
          log.debug("Add tipp to discontinue ${tipp}")

          titleChangeData.tipps[tipp.id] = [
              oldTippValue: [
                title_id: tipp.title?.id,
                package_id: tipp.pkg.id,
                platform_id: tipp.hostPlatform.id,
                startDate: tipp.startDate ? dateFormatService.formatDate(tipp.startDate) : null,
                startVolume: tipp.startVolume,
                startIssue: tipp.startIssue,
                endDate: tipp.endDate ? dateFormatService.formatDate(tipp.endDate) : null,
                endVolume: tipp.endVolume,
                endIssue: tipp.endIssue,
                url: tipp.url
              ],
              newtipps: []
          ]

          params.list('afterTitles').each{ new_title_oid ->
            TitleInstance new_title_obj = genericOIDService.resolveOID2(new_title_oid)
            Map new_tipp_info = [
              title_id: new_title_obj.id,
              package_id: tipp.pkg.id,
              platform_id: tipp.hostPlatform.id,
              startDate: tipp.startDate ? dateFormatService.formatDate(tipp.startDate) : null,
              startVolume: tipp.startVolume,
              startIssue: tipp.startIssue,
              endDate: tipp.endDate ? dateFormatService.formatDate(tipp.endDate) : null,
              endVolume: tipp.endVolume,
              url: tipp.url,
              endIssue: tipp.endIssue
            ]

            titleChangeData.tipps[tipp.id].newtipps.add(new_tipp_info)
          }
        }
      }
    }

    Activity new_activity = new Activity(
      activityName: "Title Change ${sw.toString()}",
      activityData: (titleChangeData as JSON).toString(),
      owner: request.user,
      status: active_status,
      type: transfer_type).save(flush: true)

    log.debug("redirect to edit activity (Really title) ${titleChangeData}")

    // if ( first_title )
    //   redirect(controller:'resource', action:'show', id:first_title)
    // else
    //   redirect(controller:'home', action:'index')

    redirect(action: 'editTitleChange', id: new_activity.id)
  }

  def startTitleMerge() {
    log.debug("startTitleMerge(${params})")

    User user = springSecurityService.currentUser
    RefdataValue active_status = RefdataCategory.lookup('Activity.Status', 'Active')
    RefdataValue transfer_type = RefdataCategory.lookup('Activity.Type', 'TitleMerge')
    String first_title = null

    Map result = [
      oldTitles: []
    ]

    Map activity_data = [:]

    List oldIds = params.list('beforeTitles')

    activity_data.oldTitles = params.list('beforeTitles')
    activity_data.newTitle = params.newTitle

    StringWriter sw = new StringWriter()

    log.debug("Titles to replace: ${oldIds}")

    oldIds.each { oid ->
      if (first_title == null){
        first_title = oid
      }
      else{
        sw.write(', ')
      }

      TitleInstance title_obj = genericOIDService.resolveOID2(oid)

      sw.write(title_obj.name)

      result.oldTitles.add(title_obj)
    }

    result.newTitle = genericOIDService.resolveOID2(params.newTitle)

    Activity new_activity = new Activity(
      activityName: "Title Merge ${sw.toString()}",
      activityData: (titleChangeData as JSON).toString(),
      owner: user,
      status: active_status,
      type: transfer_type).save(flush: true)

    log.debug("redirect to edit activity (Really title) ${titleChangeData}")

    // if ( first_title )
    //   redirect(controller:'resource', action:'show', id:first_title)
    // else
    //   redirect(controller:'home', action:'index')

    redirect(action: 'editTitleMerge', id: new_activity.id)
  }

  def editTitleMerge() {
    log.debug("editTitleMerge() - ${params}")

    Activity activity_record = Activity.get(params.id)
    Map activity_data = new JsonSlurper().parseText(activity_record.activityData)
    Map merge_params = [:]

    request.getParameterNames().each { pn ->
      if (pn.startsWith("merge_")) {
        merge_params[pn] = request.getParameter(pn)
      }
    }

    if (params.update){
      log.debug("Update...")
      activity_record.activityData = (activity_data as JSON).toString()
      activity_record.save(flush: true)
    }
    else if (params.process){
      log.debug("Process...")
      activity_record.activityData = (activity_data as JSON).toString()
      activity_record.save(flush: true)

      processTitleMerge(activity_record, activity_data, merge_params)

      if (activity_data.newTitle?.size() > 0) {
        redirect(controller: 'resource', action: 'show', id: activity_data.newTitle)
      }
      else {
        redirect(controller: 'home', action: 'index')
      }
    }
    else if (params.abandon) {
      log.debug("**ABANDON**...")

      activity_record.status = RefdataCategory.lookup('Activity.Status', 'Abandoned')
      activity_record.save(flush: true)

      if (activity_data.oldTitles?.size() > 0) {
        redirect(controller: 'resource', action: 'show', id: activity_data.oldTitles[0])
      }
      else{
        redirect(controller: 'home', action: 'index')
      }
    }

    log.debug("Processing...")

    Map result = [:]
    result.oldTitles = []
    result.newTitle = genericOIDService.resolveOID2(activity_data.newTitle)
    result.d = activity_record

    activity_data.oldTitles.each { oid ->
      result.oldTitles.add(genericOIDService.resolveOID2(oid))
    }

    result.id = params.id

    result
  }

  @Transactional
  def processTitleMerge(activity_record, activity_data, merge_params){
    log.debug("processTitleMerge ${params}\n\n ${activity_data}")
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    RefdataValue rr_status_current = RefdataCategory.lookup('ReviewRequest.Status', 'Open')
    RefdataValue rr_status_closed = RefdataCategory.lookup('ReviewRequest.Status', 'Closed')

    TitleInstance new_ti = genericOIDService.resolveOID2(activity_data.newTitle)
    List new_he = new_ti.getTitleHistory()

    activity_data.oldTitles.each { oid ->
      TitleInstance old_ti = genericOIDService.resolveOID2(oid)

      if (!old_ti.name.equals(new_ti.name)) {
        new_ti.addVariantTitle(old_ti.name)
      }

      if (merge_params['merge_ids']) {
        log.debug("Looking for new IDs to add")

        old_ti.ids.each { old_id ->
          ComponentIdentifier old_ci = ComponentIdentifier.findByComponentAndIdentifier(old_ti, old_id)
          List dupes = ComponentIdentifier.executeQuery("from ComponentIdentifier as c where c.identifier.id = :ido and c.component.id = :ti", [ido: old_id.id, ti: new_ti.id])

          if (!dupes || dupes.size() == 0) {
            log.debug("Adding Identifier ${old_id} to ${new_ti}")
            ComponentIdentifier new_id = new ComponentIdentifier(identifier: old_id, component: new_ti, status: old_ci.status).save(flush: true, failOnError: true)
          }
          else {
            log.debug("Identifier ${old_id} is already connected to ${new_ti}..")
          }
        }
      }

      if (merge_params['merge_vn']) {
        old_ti.variantNames.each{ old_vn ->
          new_ti.addVariantTitle(old_vn.variantName)
        }
      }

      if (merge_params['merge_pb']) {
        old_ti.publisher.each{ old_pb ->
          if (!new_ti.publisher.contains(old_pb)) {
            new_ti.publisher.add(old_pb)
          }
        }
      }

      if (merge_params['merge_he']) {
        List ti_history = old_ti.getTitleHistory()

        ti_history.each { ohe ->
          List new_from = []
          List new_to = []
          boolean dupe = false

          if (ohe.to.contains(old_ti)) {
            ohe.to.removeIf { it == old_ti }
            ohe.to.add(new_ti)
            new_to = ohe.to

            ohe.from.each { hep ->
              List he_match = ComponentHistoryEvent.executeQuery('''select che from ComponentHistoryEvent as che
                                                                    where exists (
                                                                      select chep from ComponentHistoryEventParticipant as chep
                                                                      where chep.event = che
                                                                      and chep.participant = :fromPart
                                                                    )
                                                                    AND exists (
                                                                      select chep from ComponentHistoryEventParticipant as chep
                                                                      where chep.event = che
                                                                      and chep.participant = :toPart
                                                                    )''', [fromPart: hep, toPart: new_ti])
              if (he_match) {
                dupe = true
              }
            }
            new_from = ohe.from
          }
          else if (ohe.from.contains(old_ti)) {
            ohe.from.removeIf { it == old_ti }
            ohe.from.add(new_ti)
            new_from = ohe.from

            ohe.from.each { hep ->
              def he_match = ComponentHistoryEvent.executeQuery('''select che from ComponentHistoryEvent as che
                                                                    where exists (
                                                                      select chep from ComponentHistoryEventParticipant as chep
                                                                      where chep.event = che
                                                                      and chep.participant = :fromPart
                                                                    )
                                                                    AND exists (
                                                                      select chep from ComponentHistoryEventParticipant as chep
                                                                      where chep.event = che
                                                                      and chep.participant = :toPart
                                                                    )''', [fromPart: new_ti, toPart: hep])
              if (he_match) {
                dupe = true
              }
            }
            new_to = ohe.to
          }
          if (!dupe) {
            ComponentHistoryEvent he = new ComponentHistoryEvent()

            if (ohe.date) {
              he.eventDate = ohe.date
            }

            he.save(flush: true, failOnError: true)

            new_from.each {
              new ComponentHistoryEventParticipant(event: he, participant: it, participantRole: 'in').save(flush: true, failOnError: true)
            }

            new_to.each {
              new ComponentHistoryEventParticipant(event: he, participant: it, participantRole: 'out').save(flush: true, failOnError: true)
            }
          }
        }
      }

      List events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant = :component", [component: old_ti])

      events_to_delete.each {
        it.delete(flush: true)
      }

      old_ti.tipps.each { old_tipp ->
        if (merge_params['merge_tipps'] && old_tipp.status == status_current) {
          Map tipp_dto = [:]
          tipp_dto.package = ['internalId': old_tipp.pkg.id]
          tipp_dto.platform = ['internalId': old_tipp.hostPlatform.id]
          tipp_dto.title = ['internalId': new_ti.id]

          if (old_tipp.paymentType?.value) tipp_dto.paymentType = old_tipp.paymentType?.value
          tipp_dto.url = old_tipp.url ?: ""
          tipp_dto.coverage = []

          old_tipp.coverageStatements.each { otcs ->
            Map cst = [
              startVolume: otcs.startVolume ?: "",
              startIssue: otcs.startIssue ?: "",
              endVolume: otcs.endVolume ?: "",
              endIssue: otcs.endIssue ?: "",
              embargo: otcs.embargo ?: "",
              coverageNote: otcs.coverageNote ?: "",
              startDate: otcs.startDate ? dateFormatService.formatTimestampMs(otcs.startDate) : "",
              endDate: otcs.endDate ? dateFormatService.formatTimestampMs(otcs.endDate) : "",
              coverageDepth: old_tipp.coverageDepth?.value ?: ""
            ]
            tipp_dto.coverage.add(cst)
          }

          TitleInstancePackagePlatform new_tipp = tippUpsertService.upsertDTO(tipp_dto, request.user)
          log.debug("Added new TIPP ${new_tipp} to TI ${new_ti}")
        }
        old_tipp.status = status_deleted
        old_tipp.save(flush: true)
      }

      old_ti.reviewRequests.each { rr ->
        Map rr_context = [:]
        rr_context['user'] = request.user

        if (rr.status == rr_status_current) {
          rr.status = rr_status_closed
          rr.save(flush: true)
        }
      }

      old_ti.status = status_deleted
      old_ti.save(flush: true)
    }

    activity_record.status = RefdataCategory.lookup('Activity.Status', 'Complete')
    activity_record.save(flush: true)
  }

  def processPlatformReplacement() {
    Map result = [
      result: 'OK',
      old: []
    ]
    User user = springSecurityService.currentUser

    Platform new_platform = genericOIDService.resolveOID2(params.newplatform)
    result.target = [name: new_platform.name, id: new_platform.id]

    List active_platform_jobs = concurrencyManagerService.getActiveJobsForType('Admin Platform Merge')
    List active_org_jobs = concurrencyManagerService.getActiveJobsForType('Admin Org Merge')

    if (active_platform_jobs || active_org_jobs) {
      result.result = 'ERROR'
      result.message = "There is an existing merge job running."
      flash.error = "There is an existing merge job running."
    }
    else {
      params.each { p ->
        log.debug("Testing ${p.key}")

        if ((p.key.startsWith('tt')) && (p.value) && (p.value instanceof String)){
          String tt = p.key.substring(3)
          log.debug("Platform to replace: '${tt}'")
          Platform old_platform = Platform.get(tt)

          log.debug("old: ${old_platform} new: ${new_platform}")
          result.old << [name: old_platform.name, id: old_platform.id]


          Job background_job = concurrencyManagerService.createJob { Job job ->
            platformService.merge(old_platform.id, new_platform.id, job)
          }

          background_job.ownerId = user?.id ?: null
          background_job.description = "Platform merge ${old_platform} into ${new_platform}".toString()
          background_job.type = RefdataCategory.lookup('Job.Type', 'Admin Platform Merge')
          background_job.message("Start merging ${old_platform} -> ${new_platform}".toString())
          background_job.startOrQueue()
          background_job.startTime = new Date()

          result.job_id = background_job.uuid
        }
      }
    }

    withFormat {
      html {
        render view: 'platformReplacementResult', model: [result: result]
      }
      json {
        render result as JSON
      }
    }
  }

  @Transactional
  def processTippRetire(){
    log.debug("processTippRetire ${params}")
    RefdataValue retired_status = RefdataCategory.lookup('KBComponent.Status', 'Retired')
    Map result = [:]

    params.list('beforeTipps').each { title_oid ->
      log.debug("process ${title_oid}")
      TitleInstancePackagePlatform tipp_obj = genericOIDService.resolveOID2(title_oid)
      tipp_obj.status = retired_status

      if (params.endDateSelect == 'select' && params.selectedDate) {
        tipp_obj.accessEndDate = params.date('selectedDate', 'yyyy-MM-dd')
      }
      else if (params.endDateSelect == 'now') {
        tipp_obj.accessEndDate = new Date()
      }

      tipp_obj.save(flush: true, failOnError: true)
    }

    redirect(url: params.ref)
  }

  @Transactional
  def processTippMove(){
    log.debug("processTippMove ${params}")
    RefdataValue deleted_status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    User user = springSecurityService.currentUser
    Package new_package = params.newpackage ? genericOIDService.resolveOID2(params.newpackage) : null
    Platform new_platform = params.newplatform ? genericOIDService.resolveOID2(params.newplatform) : null
    List tipps_to_action = params.list('beforeTipps')
    TitleInstance new_title = params.newtitle ? genericOIDService.resolveOID2(params.newtitle) : null

    params.list('beforeTipps').each { tipp_oid ->
      log.debug("process ${tipp_oid}")
      TitleInstancePackagePlatform tipp_obj = genericOIDService.resolveOID2(tipp_oid)
      List coverage = []

      tipp_obj.coverageStatements.each { cst ->
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

      TitleInstancePackagePlatform new_tipp = tippUpsertService.upsertDTO([
          package : ['internalId': (new_package ? new_package.id : tipp_obj.pkg.id)],
          platform: ['internalId': (new_platform ? new_platform.id : tipp_obj.hostPlatform.id)],
          title   : ['internalId': (new_title ? new_title.id : tipp_obj.title.id)],
          coverage: coverage,
          url     : tipp_obj.url
      ], user)

      log.debug("Created new TIPP ${new_tipp}")
      tipp_obj.status = deleted_status
      tipp_obj.save(flush: true, failOnError: true)
    }

    redirect(url: params.ref)
  }

  def download(){
    log.debug("Download ${params}")
    DataFile df = DataFile.findByGuid(params.id)

    if (df != null) {
      //HTML is causing problems, browser thinks it should render something, other way around this?
      response.setContentType("application/octet-stream")
      response.addHeader("Content-Disposition", "attachment; filename=\"${df.uploadName}\"")
      response.outputStream << df.fileData
    }
  }

  @Secured("hasRole('ROLE_ADMIN') and isFullyAuthenticated()")
  def processCreateWebHook(){
    log.debug("processCreateWebHook ${params}")
    Map result = [ref: params.from]

    try {
      WebHookEndpoint webook_endpoint = null

      if ((params.existingHook != null) && (params.existingHook.length() > 0)) {
        log.debug("From existing hook")
        webook_endpoint = genericOIDService.resolveOID2(params.existingHook)
      }
      else {
        webook_endpoint = new WebHookEndpoint(name: params.newHookName,
            url: params.newHookUrl,
            authmethod: Long.parseLong(params.newHookAuth),
            principal: params.newHookPrin,
            credentials: params.newHookCred,
            owner: request.user)
        if (webook_endpoint.save(flush: true)) {
        }
        else {
          log.error("Problem saving new webhook endpoint : ${webook_endpoint.errors}")
        }
      }


      params.each { p ->
        if ((p.key.startsWith('tt:')) && (p.value) && (p.value instanceof String)) {
          String tt = p.key.substring(3)
          WebHook wh = new WebHook(oid: tt, endpoint: webook_endpoint)

          if (wh.save(flush: true)) {
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
  def processRRTransfer(){
    Map result = [ref: params.from]
    log.debug("processRRTransfer ${params}")
    User new_user_alloc = genericOIDService.resolveOID2(params.allocToUser)

    params.each { p ->
      if ((p.key.startsWith('tt:')) && (p.value) && (p.value instanceof String)) {
        String tt = p.key.substring(3)
        ReviewRequest rr = ReviewRequest.get(tt)
        log.debug("Process ${tt} - ${rr}")
        rr.needsNotify = true
        rr.allocatedTo = new_user_alloc
        rr.save(flush: true)

        new ReviewRequestAllocationLog(note: params.note, allocatedTo: new_user_alloc, rr: rr).save(flush: true)
      }
    }

    redirect(url: result.ref)
  }

  @Transactional
  def newRRLink() {
    def new_rr = null
    log.debug("newRRLink ${params}")
    User user = springSecurityService.currentUser
    RefdataValue rr_manual = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Manual Request')

    if (params.id){
      KBComponent component = KBComponent.findByUuid(params.id)

      if (!component) {
        component = KBComponent.get(params.long('id'))
      }

      new_rr = reviewRequestService.raise(
        component,
        params.request,
        "Manual Request",
        user,
        null,
        rr_manual,
        componentLookupService.findCuratoryGroupOfInterest(component, user)
      )
    }

    redirect(url: request.getHeader('referer') + '#review')
  }

  @Transactional
  def createTitleHistoryEvent() {
    log.debug("createTitleHistoryEvent")
    Map result = [result: 'OK']

    try {
      if ((params.afterTitles != null) && (params.beforeTitles != null)) {
        if (params.afterTitles instanceof java.lang.String) {
          params.afterTitles = [params.afterTitles]
        }

        if (params.beforeTitles instanceof java.lang.String) {
          params.beforeTitles = [params.beforeTitles]
        }

        ComponentHistoryEvent newTitleHistoryEvent = new ComponentHistoryEvent(eventDate: params.date('EventDate', 'yyyy-MM-dd')).save(flush: true)

        params.afterTitles?.each { at ->
          TitleInstance component = genericOIDService.resolveOID2(at)
          new ComponentHistoryEventParticipant(event: newTitleHistoryEvent, participant: component, participantRole: 'out').save(flush: true)
        }

        params.beforeTitles?.each { bt ->
          TitleInstance component = genericOIDService.resolveOID2(bt)
          new ComponentHistoryEventParticipant(event: newTitleHistoryEvent, participant: component, participantRole: 'in').save(flush: true)
        }
      }

    }
    catch (Exception e) {
      log.error("problem creating title history event", e)
      result.result = "ERROR"
      flash.error = "History event could not be created!"
      result.message = "There was an error creating the event."
    }
    finally {
      log.debug("Completed createTitleHistoryEvent")
    }

    withFormat {
      html {
        result.ref = request.getHeader('referer')
        redirect(url: result.ref)
      }
      json {
        result.params = (params)

        if (result.result != "ERROR") {
          result.message = "History event was sucessfully created."
        }

        render result as JSON
      }
    }
  }

  @Transactional
  def deleteTitleHistoryEvent() {

    Map result = [:]
    result.ref = request.getHeader('referer')
    ComponentHistoryEvent he = ComponentHistoryEvent.get(params.id)

    if (he != null) {
      he.delete(flush: true)
    }
    redirect(url: result.ref)
  }


  // @Transactional(readOnly = true)
  private def packageKBartExport(id) {
    def type = params.exportType == 'title' ? PackageCSVExportService.ExportType.KBART_TITLE : PackageCSVExportService.ExportType.KBART_TIPP
    Package pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)

    if (pkg) {
      packageCSVExportService.sendFile(pkg, type, response)
    }
    else {
      log.debug("Unable to resolve package by ID ${params.id}!")
      response.status = 404
    }
  }

  private def packageTSVExport(id) {
    Package pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)

    if (pkg) {
      packageCSVExportService.sendFile(pkg, PackageCSVExportService.ExportType.TSV, response)
    }
    else {
      log.debug("Unable to resolve package by ID ${params.id}!")
      response.status = 404
    }
  }

  @Transactional
  @Secured("hasRole('ROLE_ADMIN') and isFullyAuthenticated()")
  def transferPackages() {
    Map result = [result: 'OK']
    List errors = []

    if (params.orgsToDeprecate && params.neworg) {
      List orgs = params.list('orgsToDeprecate')
      Org new_org = genericOIDService.resolveOID2(params.neworg)

      orgs.each { org_id ->
        Org old_org = Org.get(org_id)

        if (old_org && new_org) {
          Map transfer_result = orgService.transferPackages(old_org, new_org)

          if (transfer_result.result == 'ERROR') {
            result.result = 'ERROR'
            errors << "${old_org}"
          }
        }
        else {
          result.result = 'ERROR'
          errors << "${org_id}"
        }
      }

      if (result.result == 'OK') {
        flash.success = "Package Reallocation Complete".toString()
      }
      else {
        flash.errors = "Package Reallocation Failed for ${errors}!".toString()
      }

      redirect(controller: 'resource', action: 'show', id: "${new_org.class.name}:${new_org.id}")
    }
  }

  @Transactional
  @Secured("hasRole('ROLE_ADMIN') and isFullyAuthenticated()")
  def deprecateOrg() {
    Map result = [result: 'OK']
    List errors = []
    User user = springSecurityService.currentUser

    if (params.orgsToDeprecate && params.neworg) {
      List orgs = params.list('orgsToDeprecate')
      Org new_org = genericOIDService.resolveOID2(params.neworg)

      List active_platform_jobs = concurrencyManagerService.getActiveJobsForType('Admin Platform Merge')
      List active_org_jobs = concurrencyManagerService.getActiveJobsForType('Admin Platform Merge')

      if (active_platform_jobs || active_org_jobs) {
        result.result = 'ERROR'
        result.message = "There is an existing Org merge job running."
        flash.errors = "There is an existing Org merge job running."
      }
      else {
        orgs.each { org_id ->
          Org old_org = Org.get(org_id)

          if (old_org && new_org) {
            Job background_job = concurrencyManagerService.createJob { Job job ->
              orgService.mergeDuplicate(old_org.id, new_org.id, job)
            }

            background_job.ownerId = user?.id ?: null
            background_job.description = "Org merge ${old_org} into ${new_org}".toString()
            background_job.type = RefdataCategory.lookup('Job.Type', 'Admin Org Merge')
            background_job.message("Start merging ${old_org} -> ${new_org}".toString())
            background_job.startOrQueue()
            background_job.startTime = new Date()

            result.job_id = background_job.uuid
          }
          else{
            result.result = 'ERROR'
            errors << "${org_id}"
          }
        }

        if (result.result == 'OK') {
          flash.success = "Org merge started! Check admin jobs view for result.".toString()
        }
        else {
          flash.errors = "Org deprecation failed for ${errors}!".toString()
        }
      }
    }
    else {
      result.result = 'ERROR'
      flash.errors = "Missing selection!".toString()
    }

    redirect(controller: 'resource', action: 'show', id: "${params.neworg}")
  }

  @Transactional
  @Secured("hasRole('ROLE_ADMIN') and isFullyAuthenticated()")
  def deprecateDeleteOrg() {
    log.debug("deprecateDeleteOrg ${params}")
    Map result = [:]

    if (params.orgsToDeprecate) {
      List orgs = params.list('orgsToDeprecate')

      orgs.each { org_id ->
        Org o = Org.get(org_id)

        if (o) {
          o.deprecateDelete()
        }
      }
    }
    result
  }

  @Transactional
  private def verifyTitleList(packages_to_verify) {
    User user = springSecurityService.currentUser

    packages_to_verify.each { ptv ->
      Package pkgObj = Package.get(ptv.id)
      Boolean curated_pkg = false
      boolean is_curator = null

      if (pkgObj.curatoryGroups && pkgObj.curatoryGroups?.size() > 0){
        is_curator = user.curatoryGroups*.id.intersect(pkgObj.curatoryGroups*.id).size() > 0
        curated_pkg = true
      }

      if (pkgObj?.isEditable() && (is_curator || !curated_pkg || user.superUserStatus)) {
        pkgObj.listStatus = RefdataCategory.lookup('Package.ListStatus', 'Checked')
        pkgObj.userListVerifier = user
        pkgObj.listVerifiedDate = new Date()
        pkgObj.save(flush: true, failOnError: true)
      }
    }

    redirect(url: request.getHeader('referer'))
  }

  private def triggerSourceUpdate(packages_to_update) {
    log.info("triggerSourceUpdate for Packages ${packages_to_update}..")
    User user = springSecurityService.currentUser
    Map pars = [:]
    boolean denied = false
    boolean restrictSize = !user.isAdmin()

    if (packages_to_update.size() > 1) {
      flash.error = "Please select a single Package to update!"
    }
    else{
      packages_to_update.each { ptv ->
        Package pkgObj = Package.get(ptv.id)
        boolean curated_pkg = false
        List is_curator = []

        if (pkgObj && pkgObj.source?.url){
          if (pkgObj.curatoryGroups && pkgObj.curatoryGroups?.size() > 0){
            is_curator = user.curatoryGroups?.id.intersect(pkgObj.curatoryGroups?.id)
            curated_pkg = true
          }

          if (pkgObj?.isEditable() && (is_curator || !curated_pkg || user.superUserStatus)) {
            Job background_job = concurrencyManagerService.createJob { Job job ->
              packageSourceUpdateService.updateFromSource(pkgObj.id, user.id, job, null, false, restrictSize)
            }

            background_job.groupId = is_curator?.size() > 0 ? is_curator[0] : null
            background_job.ownerId = user?.id ?: null
            background_job.description = "KBART Source ingest (${pkgObj.name})".toString()
            background_job.type = RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
            background_job.linkedItem = [name: pkgObj.name, type: "Package", id: pkgObj.id, uuid: pkgObj.uuid]
            background_job.message("Starting upsert for Package ${pkgObj.name}".toString())
            background_job.startOrQueue()
            background_job.startTime = new Date()

            if (background_job.begun){
              flash.success = "Update successfully started!"
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
