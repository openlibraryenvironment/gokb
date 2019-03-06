package org.gokb

import org.gokb.cred.*

import grails.converters.JSON

import org.hibernate.criterion.CriteriaSpecification

import com.k_int.ConcurrencyManagerService;
import com.k_int.ConcurrencyManagerService.Job
import java.util.concurrent.CancellationException


class AdminController {

  def uploadAnalysisService
  def FTUpdateService
  def packageService
  def componentStatisticService
  def grailsCacheAdminService
  def titleAugmentService
  ConcurrencyManagerService concurrencyManagerService
  CleanupService cleanupService

  @Deprecated
  def tidyOrgData() {

    Job j = concurrencyManagerService.createJob {
    
      // Cleanup our problem orgs.
      cleanupService.tidyMissnamedPublishers()
      
      
      def result = [:]

      def publisher_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Publisher');

      result.nonMasterOrgs = Org.executeQuery('''
         select org
         from org.gokb.cred.Org as org
             join org.tags as tag
         where tag.owner.desc = 'Org.Authorized'
           and tag.value = 'N'
      ''');

      result.nonMasterOrgs.each { nmo ->

        if ( nmo.parent != null ) {

            nmo.parent.variantNames.add(new KBComponentVariantName(variantName:nmo.name, owner:nmo.parent))
            nmo.parent.save();

          log.debug("${nmo.id} ${nmo.parent?.id}")
          def combosToDelete = []
          nmo.incomingCombos.each { ic ->
            combosToDelete.add(ic); //ic.delete(flush:true)

            if ( ic.type == publisher_combo_type ) {
              log.debug("Got a publisher combo");
              if ( nmo.parent != null ) {
                def new_pub_combo = new Combo(fromComponent:ic.fromComponent, toComponent:nmo.parent, type:ic.type, status:ic.status).save();
              }
              else {
                def authorized_rdv = RefdataCategory.lookupOrCreate('Org.Authorized', 'Y')
                log.debug("No parent set.. try and find an authorised org with the appropriate name(${ic.toComponent.name})");
                def authorized_orgs = Org.executeQuery("select distinct o from Org o join o.variantNames as vn where ( o.name = ? or vn.variantName = ?) AND ? in elements(o.tags)", [ic.toComponent.name, ic.toComponent.name, authorized_rdv]);
                if ( authorized_orgs.size() == 1 ) {
                  def ao = authorized_orgs.get(0)
                  log.debug("Create new publisher link to ${ao}");
                  def new_pub_combo = new Combo(fromComponent:ic.fromComponent, toComponent:ao, type:ic.type, status:ic.status).save();
                }
              }
            }
          }
          nmo.outgoingCombos.each { oc ->
            combosToDelete.add(oc); //ic.delete(flush:true)
            // oc.delete(flush:true)
          }

          nmo.incomingCombos.clear();
          nmo.outgoingCombos.clear();

          combosToDelete.each { cd ->
            cd.delete(flush:true)
          }

          nmo.delete(flush:true)
        }
      }
    }.startOrQueue()
    
    j.description = "Tidy Orgs Data"
    
    render(view: "logViewer", model: logViewer())
  }

  def logViewer() {
    // cache "until_changed"
    // def f = new File ("${grailsApplication.config.log_location}")
    // return [file: "${f.canonicalPath}"]
    redirect(controller:'admin', action:'jobs');
  }

  def reSummariseLicenses() {

    Job j = concurrencyManagerService.createJob {
      DataFile.executeQuery("select d from DataFile as d where d.doctype=?",['http://www.editeur.org/onix-pl:PublicationsLicenseExpression']).each { df ->
        log.debug(df);
        df.incomingCombos.each { ic ->
          log.debug(ic);
          if ( ic.fromComponent instanceof License ) {
            def source_file
            try {
              log.debug("Regenerate license for ${ic.fromComponent.id}");
              if(df.fileData){
                source_file = copyUploadedFile(df.fileData,df.guid)
                ic.fromComponent.summaryStatement = uploadAnalysisService.generateSummary(source_file);
                ic.fromComponent.save(flush:true);
                log.debug("Completed regeneration... size is ${ic.fromComponent.summaryStatement?.length()}");
              }else{
                log.error("No file data attached to DataFile ${df.guid}")
              }
            }
            catch ( Exception e ) {
              log.error("Problem",e);
            }finally{
              source_file?.delete()
            }
          }
        }
      }
    }.startOrQueue()
    
    j.description = "Regenerate License Summaries"
    j.startTime = new Date()
    
    render(view: "logViewer", model: logViewer())
  }

  def ensureUuids() {

    Job j = concurrencyManagerService.createJob {
      cleanupService.ensureUuids()
    }.startOrQueue()

    j.description = "Ensure UUIDs for components"
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())

  }

  def ensureTipls() {
    Job j = concurrencyManagerService.createJob {
      cleanupService.ensureTipls()
    }.startOrQueue()

    j.description = "Ensure TIPLs for all TIPPs"
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def convertTippCoverages() {
    Job j = concurrencyManagerService.createJob {
      cleanupService.addMissingCoverageObjects()
    }.startOrQueue()

    j.description = "Generate missing TIPPCoverageStatements"
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def copyUploadedFile(inputfile, deposit_token) {

   def baseUploadDir = grailsApplication.config.baseUploadDir ?: '.'

    log.debug("copyUploadedFile...");
    def sub1 = deposit_token.substring(0,2);
    def sub2 = deposit_token.substring(2,4);
    validateUploadDir("${baseUploadDir}");
    validateUploadDir("${baseUploadDir}/${sub1}");
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}");
    def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}";
    def temp_file = new File(temp_file_name)

     OutputStream outStream = null;
     ByteArrayOutputStream byteOutStream = null;
     try {
       outStream = new FileOutputStream(temp_file);
       byteOutStream = new ByteArrayOutputStream();
       // writing bytes in to byte output stream
       byteOutStream.write(inputfile); //data
       byteOutStream.writeTo(outStream);
     } catch (IOException e) {
       e.printStackTrace();
     } finally {
       outStream.close();
     }
    log.debug("Created temp_file ${temp_file.size()}")

    temp_file
  }

  private def validateUploadDir(path) {
    File f = new File(path);
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }

  def updateTextIndexes() {
    log.debug("Call to update indexe");
    
    Job j = concurrencyManagerService.createJob {
      FTUpdateService.updateFTIndexes();
    }.startOrQueue()
    
    j.description = "Update Free Text Indexes"
    j.startTime = new Date()
    
    render(view: "logViewer", model: logViewer())
  }

  def resetTextIndexes() {
    log.debug("Call to update indexe")
    Job j = concurrencyManagerService.createJob {
      FTUpdateService.clearDownAndInitES()
    }.startOrQueue()
    
    j.description = "Reset Free Text Indexes"
    j.startTime = new Date()
    
    render(view: "logViewer", model: logViewer())
  }

  def masterListUpdate() {
    log.debug("Force master list update")
    Job j = concurrencyManagerService.createJob {
      packageService.updateAllMasters(true)
    }.startOrQueue()
    
    j.description = "Master List Update"
    j.startTime = new Date()
    
    render(view: "logViewer", model: logViewer())
  }

  def clearBlockCache() {
    // clear the cache used by the blocks tag…
    log.debug("Clearing block cache .. ")
    grailsCacheAdminService.clearBlocksCache()

    forward(controller: 'home', action:'index', params: [reset: true])
  }

  def triggerEnrichments() {
    Job j = concurrencyManagerService.createJob {
      log.debug("manually trigger enrichment service");
      titleAugmentService.doEnrichment();
    }.startOrQueue()
    
    j.description = "Enrichment Service"
    j.startTime = new Date()
    
    render(view: "logViewer", model: logViewer())
  }

  def jobs() {
    log.debug("Jobs");
    def result=[:]
    log.debug("Sort");
    result.jobs = concurrencyManagerService.jobs.sort { a,b -> b.key <=> a.key }
    log.debug("concurrency manager service");
    result.cms = concurrencyManagerService

    result.jobs.each { k, j ->
      if ( j.isDone() && !j.endTime ) {

        try {
          def job_res = j.get()

          if (job_res && job_res instanceof Date) {
            j.endTime = j.get()
          }
        }
        catch (CancellationException e) {
          log.debug("Cancelled")
        }
      }
    }

    log.debug("Render");
    if ( request.format == 'JSON' ) {
      log.debug("JSON Render");
      render result as JSON
    }

    log.debug("Return");
    result
  }


  def cancelJob() {
    Job j = concurrencyManagerService.getJob(params.int('id'))

    j?.forceCancel()
    render(view: "logViewer", model: logViewer())
  }

  @Deprecated
  def housekeeping() {
    Job j = concurrencyManagerService.createJob {
      def et = cleanupService.housekeeping()
      return et
    }.startOrQueue()
    
    j.description = "Housekeeping"
    j.startTime = new Date()
    
    log.debug "Triggering housekeeping task. Started job #${j.id}"
    
    render(view: "logViewer", model: logViewer())
  }
  
  def cleanup() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.expungeDeletedComponents(j)
    }.startOrQueue()
    
    log.debug "Triggering cleanup task. Started job #${j.id}"
    
    j.description = "Cleanup Deleted Components"
    j.startTime = new Date()
    
    render(view: "logViewer", model: logViewer())
  }

  def cleanupRejected() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.expungeRejectedComponents(j)
    }.startOrQueue()

    log.debug "Triggering cleanup task. Started job #${j.id}"

    j.description = "Cleanup Rejected Components"
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def cleanupOrphanedTipps() {
    Job j = concurrencyManagerService.createJob {
      cleanupService.deleteOrphanedTipps()
    }.startOrQueue()

    log.debug("Triggering cleanup task. Started job #${j.id}")

    j.description = "TIPP Cleanup"
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def cleanupPlatforms() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.deleteNoUrlPlatforms(j)
    }.startOrQueue()

    log.debug("Triggering cleanup task. Started job #${j.id}")

    j.description = "Platform Cleanup"
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def exportGroups () {
    def result = [:]
    CuratoryGroup.createCriteria().list ({
      createAlias ('status', 'cstatus', CriteriaSpecification.LEFT_JOIN)
      or {
        isNull 'status'
        and {
          ne 'cstatus.value', KBComponent.STATUS_DELETED
          ne 'cstatus.value', KBComponent.STATUS_RETIRED
        }
      }
    })?.each { CuratoryGroup group ->
      result["${group.name}"] = [
        users : group.users.collect { it.username },
        owner : group.owner?.username,
        status : group.status?.value,
        editStatus : group.editStatus?.value
      ]
    }
    
    render result as JSON
  }

  def recalculateStats() {
    Job j = concurrencyManagerService.createJob {
      componentStatisticService.updateCompStats(12,0,true)
    }.startOrQueue()

    log.debug "Triggering statistics rewrite, job #${j.id}"
    j.description = "Recalculate Statistics"
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }
}
