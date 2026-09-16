package org.gokb

import grails.converters.*
import grails.gorm.transactions.*
import org.springframework.security.access.annotation.Secured;
import org.gokb.cred.*
import org.springframework.web.multipart.MultipartHttpServletRequest
import com.k_int.ConcurrencyManagerService.Job

@Transactional(readOnly = true)
class PackagesController {

  def dateFormatService
  def genericOIDService
  def springSecurityService
  def concurrencyManagerService
  def TSVIngestionService
  def packageService
  def packageCSVExportService

  public static String TIPPS_QRY = 'select tipp from TitleInstancePackagePlatform as tipp where pkg.id = :pkg order by tipp.id'

  def packageContent() {
    log.debug("packageContent::${params}")
    Map result = [:]

    if (params.id) {
      List pkg_id_components = params.id.split(':')
      String pkg_id = pkg_id_components[1]
      result.pkgData = Package.executeQuery('select p.id, p.name from Package as p where p.id = :pkg', [pkg: Long.parseLong(pkg_id)])
      result.pkgId = result.pkgData[0][0]
      result.pkgName = result.pkgData[0][1]
      log.debug("Tipp qry name: ${result.pkgName}")
      result.tipps = TitleInstancePackagePlatform.executeQuery(TIPPS_QRY, [pkg: result.pkgId, ct: 'Package.Tipps'], [offset: 0, max: 10])
      log.debug("Tipp qry done ${result.tipps?.size()}")
    }
    result
  }

  @Secured("hasAnyRole('ROLE_ADMIN', 'ROLE_POWERUSER') and isFullyAuthenticated()")
  def compareContents() {
    log.debug("compareContents")
    Map result = [params: params, result: 'OK']
    User user = springSecurityService.currentUser

    if (params.one && params.two) {
      def date = params.date ? dateFormatService.parseDate(params.date)  : null
      boolean full = params.full ? params.boolean('full') : false
      List listOne = params.list('one')
      List listTwo = params.list('two')

      if (params.wait) {
        result = packageService.compareLists(listOne, listTwo, full, date)
      }
      else {
        def background_job = concurrencyManagerService.createJob { Job job ->
          packageService.compareLists(listOne, listTwo, full, date, job)
        }.startOrQueue()

        background_job.description = "Package comparison"
        background_job.type = RefdataCategory.lookup('Job.Type', 'PackageComparison')
        background_job.ownerId = user.id
        result.job_id = background_job.uuid
      }
    }
    else {
      log.debug("Missing info..")
    }

    render result as JSON
  }

  @Secured("hasAnyRole('ROLE_ADMIN', 'ROLE_POWERUSER') and isFullyAuthenticated()")
  def connectedRRs() {
    log.debug("connectedRRs::${params}")
    Map result = [:]

    if (params.id) {
      Package pkg = Package.get(params.id)
      boolean open_only = true
      boolean restr = false
      result.restriction = 'open'

      if (params.getAll) {
        open_only = false
        result.restriction = 'all'
      }
      if (params.restriction == 'Current') {
        restr = true
      }

      result.reviewRequests = pkg.getReviews(open_only, restr)
    }
    withFormat {
      html { render template: 'revreqtabpkg', model: [d: result], contentType: 'text/html' }
      json { render result as JSON }
    }
  }

  @Secured("hasAnyRole('ROLE_ADMIN', 'ROLE_POWERUSER') and isFullyAuthenticated()")
  @Transactional
  def deposit() {
    Map result = [:]
    log.debug("deposit::${params}")

    Job background_job = null
    User user = springSecurityService.currentUser

    if (request.method == 'POST') {

      log.debug("Handling post")

      DataFile.withNewSession() {

        if (request instanceof MultipartHttpServletRequest) {

          String upload_mime_type = request.getFile("content")?.contentType  // getPart?
          String upload_filename = request.getFile("content")?.getOriginalFilename()
          Long new_datafile_id = null

          log.debug("Multipart ${upload_mime_type} ${upload_filename}")

          if (upload_mime_type &&
            upload_filename &&
            params.pkg &&
            params.platformUrl &&
            params.fmt &&
            params.source) {

            String deposit_token = java.util.UUID.randomUUID().toString()
            File temp_file = TSVIngestionService.handleTempFile(deposit_token, request.getFile("content"))
            log.debug("Got file content")
            RefdataValue format_rdv = RefdataCategory.lookupOrCreate('ingest.filetype', params.fmt).save()
            String pkg = params.pkg
            String platformUrl = params.platformUrl
            // def source = params.source
            Source source = Source.findByName(params.source) ?: new Source(name: params.source).save(flush: true, failOnError: true)
            String providerName = params.providerName
            Org providerObj = Org.findByName(providerName) ?: null
            IdentifierNamespace providerIdentifierNamespace = IdentifierNamespace.findByValue(params.providerIdentifierNamespace)

            if (providerObj?.titleNamespace) {
              providerIdentifierNamespace = providerObj?.titleNamespace
            }

            Map info = TSVIngestionService.analyseFile(temp_file)

            log.debug("Got file with md5 ${info.md5sumHex}.. lookup by md5")
            DataFile existing_file = DataFile.findByMd5(info.md5sumHex)

            if (existing_file != null) {
              log.debug("Found a match !")
              if (params.reprocess == 'Y') {
                log.debug("Located existing file, reprocess=Y, continuing")
                new_datafile_id = existing_file.getId()
              } else {
                // redirect(controller:'resource',action:'show',id:"org.gokb.cred.DataFile:${existing_file.id}")
                result.message = "Datafile already present with internal id org.gokb.cred.DataFile:${existing_file.getId()}"
                return
              }
            } else {
              log.debug("Create new datafile")

              DataFile.withNewTransaction {
                DataFile new_datafile = new DataFile(
                  guid: deposit_token,
                  md5: info.md5sumHex,
                  uploadName: upload_filename,
                  name: upload_filename,
                  filesize: info.filesize,
                  uploadMimeType: upload_mime_type).save(failOnError: true, flush: true)

                new_datafile.fileData = temp_file.getBytes()
                new_datafile.save(flush: true, failOnError: true)


                log.debug("Saved new datafile : ${new_datafile.getId()}")
                new_datafile_id = new_datafile.getId()
              }
            }


            log.debug("Create background job")
            String incremental_flag = params.incremental
            Map additional_params = [
              curatoryGroup: params.curatoryGroup,
              description  : params.description
            ];

            // Trying to create an extensible way to pass package level properties to the ingest processing routine.
            // Passing params as a map is a bad idea as the scope of params is restricted to the request and the ingest can
            // (and probably will) outlive the http request.
            params.each { k, v ->
              if (k.toLowerCase().startsWith('pkg.')) {
                additional_params[k] = v
              }
            }
            log.debug("Additional params will be ${additional_params}")

            background_job = concurrencyManagerService.createJob { Job job ->
              def job_result = null
              // Create a new session to run the ingest.
              try {
                log.debug("Launching ingest")

                job_result = TSVIngestionService.ingest2(format_rdv,
                  pkg,
                  new java.net.URL(platformUrl),
                  source,
                  new_datafile_id,
                  job,
                  providerName,
                  providerIdentifierNamespace,
                  null, //  ip_id
                  null, //  ingest_cfg
                  incremental_flag,
                  additional_params,
                  user);
              }
              catch (Exception e) {
                log.error("Problem", e)
              }
              finally {
                log.debug("Async Data insert complete")
              }

              log.debug("Got job result: ${job_result}")
              return job_result
            }

            background_job.description = "Deposit datafile ${upload_filename}(as ${params.fmt} from ${source} ) and create/update package ${pkg}"
            background_job.type = RefdataCategory.lookupOrCreate('Job.Type', 'DepositDatafile')
            background_job.ownerId = user.id
            background_job.startOrQueue()

            log.debug("Background job started")
          } else {
            log.error("Missing parameters :: ${params}")
          }
        } else {
          log.error("Not multipart")
        }
      }
    } else {
      log.debug("Get")
    }


    if (params.synchronous == 'Y') {
      log.debug("Waiting for job to complete")
      result.jobResult = background_job.get()
    }

    withFormat {
      html result
      json { render result as JSON }
      xml { render result as XML }
    }
  }

  @Transactional(readOnly = true)
  def kbart() {
    if (request.method == "POST") {
      List packs = []
      def type = request.JSON.data.exportType == 'title' ? PackageCSVExportService.ExportType.KBART_TITLE : PackageCSVExportService.ExportType.KBART_TIPP

      request.JSON.data.ids.each { id ->
        Package pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)

        if (pkg)
          packs << pkg
      }

      packageCSVExportService.sendZip(packs, type, response)
    }
    else {
      List ids = params.list('pkg') ?: [params.id]
      def type = params.exportType == 'title' ? PackageCSVExportService.ExportType.KBART_TITLE : PackageCSVExportService.ExportType.KBART_TIPP

      if (!ids) {
        response.status = 400
      }
      else if (ids.size() == 1) {
        Package pkg = Package.findByUuid(ids[0]) ?: (genericOIDService.oidToId(ids[0]) ? Package.get(genericOIDService.oidToId(ids[0])) : null)

        if (pkg) {
          packageCSVExportService.sendFile(pkg, type, response)
        }
        else {
          log.error("Cant find package with ID ${ids[0]}")
          response.status = 404
        }
      }
      else {
        List packs = []

        ids.each { id ->
          def pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)

          if (pkg)
            packs << pkg
        }

        packageCSVExportService.sendZip(packs, type, response)
      }
    }
  }

  @Transactional(readOnly = true)
  def packageTSVExport() {
    if (request.method == "POST") {
      List packs = []

      request.JSON.data.ids.each { id ->
        Package pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)

        if (pkg)
          packs << pkg
      }

      packageCSVExportService.sendZip(packs, PackageCSVExportService.ExportType.TSV, response)
    }
    else {
      List ids = params.list('pkg') ?: [params.id]

      if (!ids) {
        response.status = 400
      }
      else if (ids.size() == 1) {
        Package pkg = Package.findByUuid(ids[0]) ?: (genericOIDService.oidToId(ids[0]) ? Package.get(genericOIDService.oidToId(ids[0])) : null)

        if (pkg) {
          packageCSVExportService.sendFile(pkg, PackageCSVExportService.ExportType.TSV, response)
        }
        else {
          log.error("Cant find package with ID ${ids[0]}")
          response.status = 404
        }
      } else {
        List packs = []

        ids.each { id ->
          Package pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)

          if (pkg)
            packs << pkg
        }

        packageCSVExportService.sendZip(packs, PackageCSVExportService.ExportType.TSV, response)
      }
    }
  }
}
