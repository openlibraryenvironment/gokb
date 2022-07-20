package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import grails.converters.JSON
import org.gokb.cred.*
import org.hibernate.criterion.CriteriaSpecification

import org.springframework.security.access.annotation.Secured
import org.springframework.security.acls.domain.BasePermission

import java.util.concurrent.CancellationException

import grails.gorm.transactions.*

class AdminController {

  def uploadAnalysisService
  def FTUpdateService
  def packageService
  def gokbAclService
  def componentStatisticService
  def aclUtilService
  def grailsCacheAdminService
  def titleAugmentService
  ConcurrencyManagerService concurrencyManagerService
  CleanupService cleanupService
  TippService tippService

  def logViewer() {
    // cache "until_changed"
    // def f = new File ("${grailsApplication.config.log_location}")
    // return [file: "${f.canonicalPath}"]
    redirect(controller: 'admin', action: 'jobs');
  }

  def ensureUuids() {

    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.ensureUuids(j)
    }.startOrQueue()

    j.description = "Ensure UUIDs for components"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'EnsureUUIDs')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())

  }

  def ensureTipls() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.ensureTipls(j)
    }.startOrQueue()

    j.description = "Ensure TIPLs for all TIPPs"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'EnsureTIPLs')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def convertTippCoverages() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.addMissingCoverageObjects(j)
    }.startOrQueue()

    j.description = "Generate missing TIPPCoverageStatements"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'GenerateTIPPCoverage')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def markInconsistentDates() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.reviewDates(j)
    }.startOrQueue()

    j.description = "Mark insonsistent date ranges"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'MarkInconsDateRanges')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def copyUploadedFile(inputfile, deposit_token) {

    def baseUploadDir = grailsApplication.config.baseUploadDir ?: '.'

    log.debug("copyUploadedFile...");
    def sub1 = deposit_token.substring(0, 2);
    def sub2 = deposit_token.substring(2, 4);
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
    if (!f.exists()) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }

  def updateTextIndexes() {
    log.debug("Call to update Elasticsearch indices");

    Job j = concurrencyManagerService.createJob {
      FTUpdateService.updateFTIndexes()
    }.startOrQueue()

    j.description = "Update Free Text Indexes"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'UpdateFreeTextIndexes')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def resetTextIndexes() {
    log.debug("Call to update indexe")
    Job j = concurrencyManagerService.createJob {
      FTUpdateService.clearDownAndInit()
    }.startOrQueue()

    j.description = "Reset Free Text Indexes"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'ResetFreeTextIndexes')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def masterListUpdate() {
    log.debug("Force master list update")
    Job j = concurrencyManagerService.createJob {
      packageService.updateAllMasters(true)
    }.startOrQueue()

    j.description = "Master List Update"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'MasterListUpdate')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def clearBlockCache() {
    // clear the cache used by the blocks tag…
    log.debug("Clearing block cache .. ")
    grailsCacheAdminService.clearBlocksCache()

    forward(controller: 'home', action: 'index', params: [reset: true])
  }

  def triggerEnrichments() {
    Job j = concurrencyManagerService.createJob {
      log.debug("manually trigger enrichment service");
      titleAugmentService.doEnrichment();
    }.startOrQueue()

    j.description = "Enrichment Service"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'EnrichmentService')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def addPackageTypes() {
    Job j = concurrencyManagerService.createJob { Job j ->
      log.debug("Generating missing package content types ..")
      packageService.generatePackageTypes(j)
    }.startOrQueue()

    j.description = "Generate Package Types"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'GeneratePackageTypes')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def jobs() {
    log.debug("Jobs");
    def result = [:]
    log.debug("Sort");
    result.jobs = concurrencyManagerService.jobs.sort { a, b -> b.value.startTime <=> a.value.startTime }
    log.debug("concurrency manager service");
    result.cms = concurrencyManagerService

    result.jobs.each { k, j ->
      if (j.isDone() && !j.endTime) {

        try {
          def job_res = j.get()

          if (job_res && job_res instanceof Date) {
            j.endTime = j.get()
          }
        }
        catch (CancellationException e) {
          log.debug("Cancelled")
        }
        catch (Exception e) {
          log.debug("Exception in Job ${j.uuid}:")
          e.printStackTrace()

          if (j.messages?.size() == 0) {
            j.message("There has been an exception processing this job! Please check the logs!")
          }
        }
      }
    }

    log.debug("Render");
    if (request.format == 'JSON') {
      log.debug("JSON Render");
      render result as JSON
    }

    log.debug("Return");
    result
  }

  def cleanJobList() {
    log.debug("clean job list..")
    def jobs = concurrencyManagerService.jobs

    jobs.each { k, j ->
      if (j.isDone()) {
        jobs.remove(k)
      }
    }
    redirect(url: request.getHeader('referer'))
  }

  def cancelJob() {
    Job j = concurrencyManagerService.getJob(params.id)

    j?.forceCancel()
    render(view: "logViewer", model: logViewer())
  }

  def housekeeping() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.housekeeping(j)
    }.startOrQueue()

    j.description = "Housekeeping"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'Housekeeping')
    j.startTime = new Date()

    log.debug "Triggering housekeeping task. Started job #${j.uuid}"

    render(view: "logViewer", model: logViewer())
  }

  def rebuildPackageCaches() {
    log.debug("Call to recache all packages")

    Job j = concurrencyManagerService.createJob {
      packageService.cachePackageXml(true)
    }.startOrQueue()

    j.description = "Recache packages"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'Package Re-Caching')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def cleanup() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.expungeDeletedComponents(j)
    }.startOrQueue()

    log.debug "Triggering cleanup task. Started job #${j.uuid}"

    j.description = "Cleanup Deleted Components"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'CleanupDeletedComponents')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def cleanupRejected() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.expungeRejectedComponents(j)
    }.startOrQueue()

    log.debug "Triggering cleanup task. Started job #${j.uuid}"

    j.description = "Cleanup Rejected Components"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'CleanupRejectedComponents')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def cleanupOrphanedTipps() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.deleteOrphanedTipps(j)
    }.startOrQueue()

    log.debug("Triggering cleanup task. Started job #${j.uuid}")

    j.description = "TIPP Cleanup"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'TIPPCleanup')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def copyTitleData() {
    log.debug("copy Identifiers")
    Job j = concurrencyManagerService.createJob { Job j ->
      tippService.copyTitleData(j)
    }.startOrQueue()

    log.debug("started data transfer task")

    j.description = "TIPP: copy title data"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'TIPPfillUp')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def rejectWrongTitles() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.rejectWrongTitles(j)
    }.startOrQueue()

    log.debug("Reject wrong titles. Started job #${j.uuid}")

    j.description = "Set status of TitleInstances without package+history to 'Deleted'"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'DeleteTIWithoutHistory')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def rejectNoIdTitles() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.rejectNoIdTitles(j)
    }.startOrQueue()

    log.debug("Reject wrong titles. Started job #${j.uuid}")

    j.description = "Set status of TitleInstances without identifiers+tipps to 'Rejected'"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'RejectTIWithoutIdentifier')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def cleanupPlatforms() {
    Job j = concurrencyManagerService.createJob { Job j ->
      cleanupService.deleteNoUrlPlatforms(j)
    }.startOrQueue()

    log.debug("Triggering cleanup task. Started job #${j.uuid}")

    j.description = "Platform Cleanup"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'PlatformCleanup')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def exportGroups() {
    def result = [:]
    CuratoryGroup.createCriteria().list({
      createAlias('status', 'cstatus', CriteriaSpecification.LEFT_JOIN)
      or {
        isNull 'status'
        and {
          ne 'cstatus.value', KBComponent.STATUS_DELETED
          ne 'cstatus.value', KBComponent.STATUS_RETIRED
        }
      }
    })?.each { CuratoryGroup group ->
      result["${group.name}"] = [
          users     : group.users.collect { it.username },
          owner     : group.owner?.username,
          status    : group.status?.value,
          editStatus: group.editStatus?.value
      ]
    }

    render result as JSON
  }

  def recalculateStats() {
    Job j = concurrencyManagerService.createJob {
      componentStatisticService.updateCompStats(12, 0, true)
    }.startOrQueue()

    log.debug "Triggering statistics rewrite, job #${j.uuid}"
    j.description = "Recalculate Statistics"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'RecalculateStatistics')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  def zdbSync() {
    Job j = concurrencyManagerService.createJob { job ->
      titleAugmentService.syncZdbInfo(job)
    }.startOrQueue()

    log.debug "Triggering ZDB sync, job #${j.uuid}"
    j.description = "Update journal information from ZDB data"
    j.type = RefdataCategory.lookupOrCreate('Job.Type', 'Sync ZDB data')
    j.startTime = new Date()

    render(view: "logViewer", model: logViewer())
  }

  @Secured(['ROLE_SUPERUSER', 'IS_AUTHENTICATED_FULLY'])
  def setupAcl() {

    def default_dcs = ["BookInstance", "JournalInstance", "TitleInstancePackagePlatform", "DatabaseInstance", "Office", "Imprint", "Package", "ReviewRequest", "Org", "Platform", "Source", "KBComponentVariantName", "TitleInstancePlatform", "TIPPCoverageStatement"]

    default_dcs.each { dcd ->

      def dc_org = KBDomainInfo.findByDcName("org.gokb.cred.${dcd}")

      aclUtilService.addPermission(dc_org, 'ROLE_USER', BasePermission.READ)

      aclUtilService.addPermission(dc_org, 'ROLE_CONTRIBUTOR', BasePermission.READ)
      aclUtilService.addPermission(dc_org, 'ROLE_CONTRIBUTOR', BasePermission.WRITE)
      aclUtilService.addPermission(dc_org, 'ROLE_CONTRIBUTOR', BasePermission.CREATE)

      aclUtilService.addPermission(dc_org, 'ROLE_EDITOR', BasePermission.READ)
      aclUtilService.addPermission(dc_org, 'ROLE_EDITOR', BasePermission.WRITE)
      aclUtilService.addPermission(dc_org, 'ROLE_EDITOR', BasePermission.CREATE)
      aclUtilService.addPermission(dc_org, 'ROLE_EDITOR', BasePermission.DELETE)

      aclUtilService.addPermission(dc_org, 'ROLE_ADMIN', BasePermission.READ)
      aclUtilService.addPermission(dc_org, 'ROLE_ADMIN', BasePermission.WRITE)
      aclUtilService.addPermission(dc_org, 'ROLE_ADMIN', BasePermission.CREATE)
      aclUtilService.addPermission(dc_org, 'ROLE_ADMIN', BasePermission.DELETE)
      aclUtilService.addPermission(dc_org, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)
    }

    def dc_cmb = KBDomainInfo.findByDcName("org.gokb.cred.Combo")

    aclUtilService.addPermission(dc_cmb, 'ROLE_CONTRIBUTOR', BasePermission.CREATE)
    aclUtilService.addPermission(dc_cmb, 'ROLE_CONTRIBUTOR', BasePermission.DELETE)

    aclUtilService.addPermission(dc_cmb, 'ROLE_EDITOR', BasePermission.CREATE)
    aclUtilService.addPermission(dc_cmb, 'ROLE_EDITOR', BasePermission.DELETE)

    aclUtilService.addPermission(dc_cmb, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_cmb, 'ROLE_ADMIN', BasePermission.DELETE)

    def dc_tit = KBDomainInfo.findByDcName("org.gokb.cred.TitleInstance")

    aclUtilService.addPermission(dc_tit, 'ROLE_USER', BasePermission.READ)

    aclUtilService.addPermission(dc_tit, 'ROLE_CONTRIBUTOR', BasePermission.READ)
    aclUtilService.addPermission(dc_tit, 'ROLE_CONTRIBUTOR', BasePermission.WRITE)

    aclUtilService.addPermission(dc_tit, 'ROLE_EDITOR', BasePermission.READ)
    aclUtilService.addPermission(dc_tit, 'ROLE_EDITOR', BasePermission.WRITE)
    aclUtilService.addPermission(dc_tit, 'ROLE_EDITOR', BasePermission.DELETE)

    aclUtilService.addPermission(dc_tit, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_tit, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_tit, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_tit, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_id = KBDomainInfo.findByDcName('org.gokb.cred.Identifier')

    aclUtilService.addPermission(dc_id, 'ROLE_USER', BasePermission.READ)

    aclUtilService.addPermission(dc_id, 'ROLE_CONTRIBUTOR', BasePermission.READ)
    aclUtilService.addPermission(dc_id, 'ROLE_CONTRIBUTOR', BasePermission.CREATE)
    aclUtilService.addPermission(dc_id, 'ROLE_CONTRIBUTOR', BasePermission.DELETE)

    aclUtilService.addPermission(dc_id, 'ROLE_EDITOR', BasePermission.READ)
    aclUtilService.addPermission(dc_id, 'ROLE_EDITOR', BasePermission.CREATE)
    aclUtilService.addPermission(dc_id, 'ROLE_EDITOR', BasePermission.DELETE)

    aclUtilService.addPermission(dc_id, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_id, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_id, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_id, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_cg = KBDomainInfo.findByDcName('org.gokb.cred.CuratoryGroup')

    aclUtilService.addPermission(dc_cg, 'ROLE_CONTRIBUTOR', BasePermission.READ)
    aclUtilService.addPermission(dc_cg, 'ROLE_CONTRIBUTOR', BasePermission.WRITE)
    aclUtilService.addPermission(dc_cg, 'ROLE_CONTRIBUTOR', BasePermission.CREATE)

    aclUtilService.addPermission(dc_cg, 'ROLE_EDITOR', BasePermission.READ)
    aclUtilService.addPermission(dc_cg, 'ROLE_EDITOR', BasePermission.WRITE)
    aclUtilService.addPermission(dc_cg, 'ROLE_EDITOR', BasePermission.CREATE)

    aclUtilService.addPermission(dc_cg, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_cg, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_cg, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_cg, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_cg, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_uo = KBDomainInfo.findByDcName('org.gokb.cred.UserOrganisation')

    aclUtilService.addPermission(dc_uo, 'ROLE_CONTRIBUTOR', BasePermission.READ)
    aclUtilService.addPermission(dc_uo, 'ROLE_CONTRIBUTOR', BasePermission.WRITE)
    aclUtilService.addPermission(dc_uo, 'ROLE_CONTRIBUTOR', BasePermission.CREATE)

    aclUtilService.addPermission(dc_uo, 'ROLE_EDITOR', BasePermission.READ)
    aclUtilService.addPermission(dc_uo, 'ROLE_EDITOR', BasePermission.WRITE)
    aclUtilService.addPermission(dc_uo, 'ROLE_EDITOR', BasePermission.CREATE)

    aclUtilService.addPermission(dc_uo, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_uo, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_uo, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_uo, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_uo, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_rdc = KBDomainInfo.findByDcName('org.gokb.cred.RefdataCategory')

    aclUtilService.addPermission(dc_rdc, 'ROLE_CONTRIBUTOR', BasePermission.READ)

    aclUtilService.addPermission(dc_rdc, 'ROLE_EDITOR', BasePermission.READ)

    aclUtilService.addPermission(dc_rdc, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_rdc, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_rdc, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_rdc, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_rdc, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_rdv = KBDomainInfo.findByDcName('org.gokb.cred.RefdataValue')

    aclUtilService.addPermission(dc_rdv, 'ROLE_USER', BasePermission.READ)

    aclUtilService.addPermission(dc_rdv, 'ROLE_CONTRIBUTOR', BasePermission.READ)

    aclUtilService.addPermission(dc_rdv, 'ROLE_EDITOR', BasePermission.READ)

    aclUtilService.addPermission(dc_rdv, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_rdv, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_rdv, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_rdv, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_rdv, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_ns = KBDomainInfo.findByDcName('org.gokb.cred.IdentifierNamespace')

    aclUtilService.addPermission(dc_ns, 'ROLE_USER', BasePermission.READ)

    aclUtilService.addPermission(dc_ns, 'ROLE_CONTRIBUTOR', BasePermission.READ)

    aclUtilService.addPermission(dc_ns, 'ROLE_EDITOR', BasePermission.READ)

    aclUtilService.addPermission(dc_ns, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_ns, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_ns, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_ns, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_ns, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_usr = KBDomainInfo.findByDcName('org.gokb.cred.User')

    aclUtilService.addPermission(dc_usr, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_usr, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_usr, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_kbd = KBDomainInfo.findByDcName('org.gokb.cred.KBDomainInfo')

    aclUtilService.addPermission(dc_kbd, 'ROLE_ADMIN', BasePermission.READ)

    // DecisionSupport

    def dc_dsc = KBDomainInfo.findByDcName('org.gokb.cred.DSCriterion')

    aclUtilService.addPermission(dc_dsc, 'ROLE_EDITOR', BasePermission.READ)

    aclUtilService.addPermission(dc_dsc, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_dsc, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_dsc, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_dsc, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_dsc, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_dscat = KBDomainInfo.findByDcName('org.gokb.cred.DSCategory')

    aclUtilService.addPermission(dc_dscat, 'ROLE_EDITOR', BasePermission.READ)

    aclUtilService.addPermission(dc_dscat, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_dscat, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_dscat, 'ROLE_ADMIN', BasePermission.CREATE)
    aclUtilService.addPermission(dc_dscat, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_dscat, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    def dc_kbc = KBDomainInfo.findByDcName('org.gokb.cred.KBComponent')

    aclUtilService.addPermission(dc_kbc, 'ROLE_USER', BasePermission.READ)

    aclUtilService.addPermission(dc_kbc, 'ROLE_CONTRIBUTOR', BasePermission.READ)

    aclUtilService.addPermission(dc_kbc, 'ROLE_EDITOR', BasePermission.READ)

    aclUtilService.addPermission(dc_kbc, 'ROLE_ADMIN', BasePermission.READ)
    aclUtilService.addPermission(dc_kbc, 'ROLE_ADMIN', BasePermission.WRITE)
    aclUtilService.addPermission(dc_kbc, 'ROLE_ADMIN', BasePermission.DELETE)
    aclUtilService.addPermission(dc_kbc, 'ROLE_ADMIN', BasePermission.ADMINISTRATION)

    render(view: "logViewer", model: logViewer())
  }
}