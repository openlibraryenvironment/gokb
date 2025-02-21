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

  public static String TIPPS_QRY = 'select tipp from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id = :pkg and c.toComponent = tipp  and c.type.value = :ct order by tipp.id';

  def packageContent() {
    log.debug("packageContent::${params}")
    def result = [:]
    if (params.id) {
      def pkg_id_components = params.id.split(':');
      def pkg_id = pkg_id_components[1]
      result.pkgData = Package.executeQuery('select p.id, p.name from Package as p where p.id = :pkg', [pkg: Long.parseLong(pkg_id)])
      result.pkgId = result.pkgData[0][0]
      result.pkgName = result.pkgData[0][1]
      log.debug("Tipp qry name: ${result.pkgName}");
      result.tipps = TitleInstancePackagePlatform.executeQuery(TIPPS_QRY, [pkg: result.pkgId, ct: 'Package.Tipps'], [offset: 0, max: 10])
      log.debug("Tipp qry done ${result.tipps?.size()}");
    }
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def compareContents() {
    log.debug("compareContents")
    def result = [params: params, result: 'OK']
    def user = springSecurityService.currentUser

    if (params.one && params.two) {
      def date = params.date ? dateFormatService.parseDate(params.date)  : null
      def full = params.full ? params.boolean('full') : false
      def listOne = params.list('one')
      def listTwo = params.list('two')

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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def connectedRRs() {
    log.debug("connectedRRs::${params}")
    def result = [:]
    if (params.id) {
      def pkg = Package.get(params.id)
      def open_only = true
      def restr = false
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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def preflight() {
    def result = [:]
    log.debug("preflight::${params}")
    def jobid = null;

    if (request.method == 'POST') {
      log.debug("Handling post")

      if (request instanceof MultipartHttpServletRequest) {

        def upload_mime_type = request.getFile("content")?.contentType  // getPart?
        def upload_filename = request.getFile("content")?.getOriginalFilename()
        def new_datafile_id = null

        log.debug("Multipart")

        if (upload_mime_type &&
          upload_filename &&
          params.pkg &&
          params.platformUrl &&
          params.fmt &&
          params.source) {

          def deposit_token = java.util.UUID.randomUUID().toString()
          def temp_file = TSVIngestionService.handleTempFile(deposit_token, request.getFile("content"))
          log.debug("Got file content")
          def format_rdv = RefdataCategory.lookupOrCreate('ingest.filetype', params.fmt).save()
          def pkg = params.pkg
          def platformUrl = params.platformUrl
          def source = Source.findByName(params.source) ?: new Source(name: params.source).save(flush: true, failOnError: true)
          def providerName = params.providerName
          def providerObj = Org.findByName(providerName) ?: null
          def providerIdentifierNamespace = IdentifierNamespace.findByValue(params.providerIdentifierNamespace)

          if (providerObj?.titleNamespace) {
            providerIdentifierNamespace = providerObj?.titleNamespace
          }

          def info = analyse(temp_file);

          TSVIngestionService.preflight(format_rdv,
            pkg,
            new java.net.URL(platformUrl),
            source,
            request.getFile("content"),
            providerName,
            providerIdentifierNamespace)

        }
      }
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def deposit() {
    def result = [:]
    log.debug("deposit::${params}")
    def jobid = null

    Job background_job = null
    User user = springSecurityService.currentUser

    if (request.method == 'POST') {

      log.debug("Handling post")

      DataFile.withNewSession() {

        if (request instanceof MultipartHttpServletRequest) {

          def upload_mime_type = request.getFile("content")?.contentType  // getPart?
          def upload_filename = request.getFile("content")?.getOriginalFilename()
          def new_datafile_id = null

          log.debug("Multipart ${upload_mime_type} ${upload_filename}")

          if (upload_mime_type &&
            upload_filename &&
            params.pkg &&
            params.platformUrl &&
            params.fmt &&
            params.source) {

            def deposit_token = java.util.UUID.randomUUID().toString()
            def temp_file = TSVIngestionService.handleTempFile(deposit_token, request.getFile("content"))
            log.debug("Got file content")
            def format_rdv = RefdataCategory.lookupOrCreate('ingest.filetype', params.fmt).save()
            def pkg = params.pkg
            def platformUrl = params.platformUrl
            // def source = params.source
            def source = Source.findByName(params.source) ?: new Source(name: params.source).save(flush: true, failOnError: true)
            def providerName = params.providerName
            def providerObj = Org.findByName(providerName) ?: null
            def providerIdentifierNamespace = IdentifierNamespace.findByValue(params.providerIdentifierNamespace)

            if (providerObj?.titleNamespace) {
              providerIdentifierNamespace = providerObj?.titleNamespace
            }

            def info = TSVIngestionService.analyseFile(temp_file)

            log.debug("Got file with md5 ${info.md5sumHex}.. lookup by md5")
            def existing_file = DataFile.findByMd5(info.md5sumHex)

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
                def new_datafile = new DataFile(
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


            log.debug("Create background job");
            def incremental_flag = params.incremental
            Map additional_params = [
              curatoryGroup: params.curatoryGroup,
              description  : params.description
            ];

            // Trying to create an extensible way to pass package level properties to the ingest processing routine.
            // Passing params as a map is a bad idea as the scope of params is restricted to the request and the ingest can
            // (and probably will) outlive the http request.
            params.each { k, v ->
              if (k.toLowerCase().startsWith('pkg.')) {
                additional_params[k] = v;
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
            jobid = background_job.uuid
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
      def packs = []
      def type = request.JSON.data.exportType=='title'?PackageService.ExportType.KBART_TITLE:PackageService.ExportType.KBART_TIPP

      request.JSON.data.ids.each { id ->
        def pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)

        if (pkg)
          packs << pkg
      }

      packageService.sendZip(packs, type, response)
    }
    else {
      def ids = params.list('pkg')
      def type = params.exportType == 'title' ? PackageService.ExportType.KBART_TITLE : PackageService.ExportType.KBART_TIPP

      if (!ids || ids.size() <= 1) {
        def pkg = Package.findByUuid(params.id) ?: (genericOIDService.oidToId(params.id) ? Package.get(genericOIDService.oidToId(params.id)) : null)

        if (pkg)
          packageService.sendFile(pkg, type, response)
        else
          log.error("Cant find package with ID ${params.id}")
          response.status = 404
      }
      else {
        def packs = []

        ids.each { id ->
          def pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)
          if (pkg)
            packs << pkg
        }

        packageService.sendZip(packs, type, response)
      }
    }
  }

  @Transactional(readOnly = true)
  def packageTSVExport() {
    if (request.method == "POST") {
      def packs = []

      request.JSON.data.ids.each { id ->
        def pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)

        if (pkg)
          packs << pkg
      }

      packageService.sendZip(packs, PackageService.ExportType.TSV, response)
    }
    else {
      def ids = params.list('pkg')

      if (ids?.size() == 0) {
        if (params.id == "all") {
          Package.all.each { pack ->
            packageService.createTsvExport(pack)
          }
          return response
        }
        def pkg = Package.findByUuid(params.id) ?: (genericOIDService.oidToId(params.id) ? Package.get(genericOIDService.oidToId(params.id)) : null)
        if (pkg)
          packageService.sendFile(pkg, PackageService.ExportType.TSV, response)
        else
          log.error("Cant find package with ID ${params.id}")
          response.status = 404
      } else {
        def packs = []

        ids.each { id ->
          def pkg = Package.findByUuid(id) ?: (genericOIDService.oidToId(id) ? Package.get(genericOIDService.oidToId(id)) : null)
          if (pkg)
            packs << pkg
        }

        packageService.sendZip(packs, PackageService.ExportType.TSV, response)
      }
    }
  }
}
