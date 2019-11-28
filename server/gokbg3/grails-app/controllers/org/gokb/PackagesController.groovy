package org.gokb

import grails.converters.*
import grails.gorm.transactions.*
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.access.annotation.Secured;
import org.gokb.cred.*
import org.springframework.web.multipart.MultipartHttpServletRequest
import com.k_int.ConcurrencyManagerService;
import com.k_int.ConcurrencyManagerService.Job
import java.security.MessageDigest
import grails.converters.JSON
import grails.core.GrailsClass
import groovyx.net.http.URIBuilder

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.type.*
import org.hibernate.Hibernate


@Transactional(readOnly = true)
class PackagesController {

  def genericOIDService
  def springSecurityService
  def concurrencyManagerService
  def TSVIngestionService
  def ESWrapperService
  def ESSearchService
  def sessionFactory
  def messageService

  public static String TIPPS_QRY = 'select tipp from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=? and c.toComponent=tipp  and c.type.value = ? order by tipp.id';

  def packageContent() {
    log.debug("packageContent::${params}")
    def result = [:]
    if ( params.id ) {
      def pkg_id_components = params.id.split(':');
      def pkg_id = pkg_id_components[1]
      result.pkgData = Package.executeQuery('select p.id, p.name from Package as p where p.id=?',[Long.parseLong(pkg_id)])
      result.pkgId = result.pkgData[0][0]
      result.pkgName = result.pkgData[0][1]
      log.debug("Tipp qry name: ${result.pkgName}");
      result.tipps = TitleInstancePackagePlatform.executeQuery(TIPPS_QRY,[result.pkgId, 'Package.Tipps'],[offset:0,max:10])
      log.debug("Tipp qry done ${result.tipps?.size()}");
    }
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def connectedRRs() {
    log.debug("connectedRRs::${params}")
    def result = [:]
    if ( params.id ) {
      def pkg = Package.get(params.id)
      def open_only = true
      def restr = false
      result.restriction = 'open'

      if (params.getAll) {
        open_only = false
        result.restriction = 'all'
      }
      if (params.restriction == 'Current'){
        restr = true
      }

      result.reviewRequests = pkg.getReviews(open_only, restr)
    }
    withFormat {
      html { render template: 'revreqtabpkg', model: [d:result], contentType:'text/html' }
      json { render result as JSON }
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def preflight() {
   def result = [:]
    log.debug("preflight::${params}")
    def jobid = null;

    if ( request.method=='POST') {
      log.debug("Handling post")

      if ( request instanceof MultipartHttpServletRequest ) {

        def upload_mime_type = request.getFile("content")?.contentType  // getPart?
        def upload_filename = request.getFile("content")?.getOriginalFilename()
        def new_datafile_id = null

        log.debug("Multipart")

        if ( upload_mime_type &&
             upload_filename &&
             params.pkg &&
             params.platformUrl &&
             params.fmt &&
             params.source ) {

          def deposit_token = java.util.UUID.randomUUID().toString();
          def temp_file = copyUploadedFile(request.getFile("content"), deposit_token);
          log.debug("Got file content")
          def format_rdv = RefdataCategory.lookupOrCreate('ingest.filetype',params.fmt).save()
          def pkg = params.pkg
          def platformUrl = params.platformUrl
          def source = Source.findByName(params.source) ?: new Source(name:params.source).save(flush:true, failOnError:true);
          def providerName = params.providerName
          def providerIdentifierNamespace = params.providerIdentifierNamespace

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
    def jobid = null;

    Job background_job = null;
    User user = springSecurityService.currentUser

    if ( request.method=='POST') {
    
      log.debug("Handling post")

      DataFile.withNewSession() {

        if ( request instanceof MultipartHttpServletRequest ) {
  
          def upload_mime_type = request.getFile("content")?.contentType  // getPart?
          def upload_filename = request.getFile("content")?.getOriginalFilename()
          def new_datafile_id = null
  
          log.debug("Multipart ${upload_mime_type} ${upload_filename}")
  
          if ( upload_mime_type &&
               upload_filename &&
               params.pkg &&
               params.platformUrl &&
               params.fmt &&
               params.source ) {
  
            def deposit_token = java.util.UUID.randomUUID().toString();
            def temp_file = copyUploadedFile(request.getFile("content"), deposit_token);
            log.debug("Got file content")
            def format_rdv = RefdataCategory.lookupOrCreate('ingest.filetype',params.fmt).save()
            def pkg = params.pkg
            def platformUrl = params.platformUrl
            // def source = params.source
            def source = Source.findByName(params.source) ?: new Source(name:params.source).save(flush:true, failOnError:true);
            def providerName = params.providerName
            def providerIdentifierNamespace = params.providerIdentifierNamespace
  
            def info = analyse(temp_file);
  
            log.debug("Got file with md5 ${info.md5sumHex}.. lookup by md5");
            def existing_file = DataFile.findByMd5(info.md5sumHex);
  
            if ( existing_file != null ) {
              log.debug("Found a match !")
              if ( params.reprocess=='Y' ) {
                log.debug("Located existing file, reprocess=Y, continuing");
                new_datafile_id = existing_file.getId()
              }
              else {
                // redirect(controller:'resource',action:'show',id:"org.gokb.cred.DataFile:${existing_file.id}")
                result.message="Datafile already present with internal id org.gokb.cred.DataFile:${existing_file.getId()}";
                return
              }
            }
            else {
              log.debug("Create new datafile");
              DataFile.withNewTransaction {
                def new_datafile = new DataFile(
                                            guid:deposit_token,
                                            md5:info.md5sumHex,
                                            uploadName:upload_filename,
                                            name:upload_filename,
                                            filesize:info.filesize,
                                            uploadMimeType:upload_mime_type).save(failOnError:true, flush:true)
  
                new_datafile.fileData = temp_file.getBytes()
                new_datafile.save(flush:true, failOnError:true)
  
  
                log.debug("Saved new datafile : ${new_datafile.getId()}");
                new_datafile_id = new_datafile.getId()
              }
            }
  
  
            log.debug("Create background job");
            def incremental_flag = params.incremental
            Map additional_params = [ 
                                     curatoryGroup:params.curatoryGroup,
                                     description:params.description 
                                    ];

            // Trying to create an extensible way to pass package level properties to the ingest processing routine.
            // Passing params as a map is a bad idea as the scope of params is restricted to the request and the ingest can
            // (and probably will) outlive the http request.
            params.each { k,v ->
              if ( k.toLowerCase().startsWith('pkg.') ) {
                additional_params[k] = v;
              }
            }
            log.debug("Additional params will be ${additional_params}");

            background_job = concurrencyManagerService.createJob { Job job ->
              def job_result = null;
              // Create a new session to run the ingest.
              try {
                log.debug("Launching ingest");

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
                                                         additional_params);
              }
              catch ( Exception e ) {
                log.error("Problem",e)
              }
              finally {
                log.debug ("Async Data insert complete")
              }

              log.debug("Got job result: ${job_result}");
              return job_result;
            }
  
            background_job.description="Deposit datafile ${upload_filename}(as ${params.fmt} from ${source} ) and create/update package ${pkg}"
            background_job.startOrQueue()
            jobid = background_job.getId()
            log.debug("Background job started");
          }
          else {
            log.error("Missing parameters :: ${params}");
          }
        }
        else {
          log.error("Not multipart");
        }
      }
    }
    else {
      log.debug("Get");
    }


    if ( params.synchronous=='Y' ) {
      log.debug("Waiting for job to complete");
      result.jobResult = background_job.get()
    }

    withFormat {
      html result
      json { render result as JSON }
      xml { render result as XML }
    }
  }

  def copyUploadedFile(inputfile, deposit_token) {
    def baseUploadDir = grailsApplication.config.project_dir ?: '.'
    log.debug("copyUploadedFile...");
    def sub1 = deposit_token.substring(0,2);
    def sub2 = deposit_token.substring(2,4);
    validateUploadDir("${baseUploadDir}");
    validateUploadDir("${baseUploadDir}/${sub1}");
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}");
    def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}";
    def temp_file = new File(temp_file_name);

    // Copy the upload file to a temporary space
    inputfile.transferTo(temp_file);

    temp_file
  }

  private def validateUploadDir(path) {
    File f = new File(path);
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }

  def analyse(temp_file) {

    def result=[:]
    result.filesize = 0;

    log.debug("analyze...");

    // Create a checksum for the file..
    MessageDigest md5_digest = MessageDigest.getInstance("MD5");
    InputStream md5_is = new FileInputStream(temp_file);
    byte[] md5_buffer = new byte[8192];
    int md5_read = 0;
    while( (md5_read = md5_is.read(md5_buffer)) >= 0) {
      md5_digest.update(md5_buffer, 0, md5_read);
      result.filesize += md5_read
    }
    md5_is.close();
    byte[] md5sum = md5_digest.digest();
    result.md5sumHex = new BigInteger(1, md5sum).toString(16);

    log.debug("MD5 is ${result.md5sumHex}");
    result
  }




  // @Transactional(readOnly = true)
  def kbart() {

    def pkg = Package.findByUuid(params.id)

    if(!pkg) {
      pkg = genericOIDService.resolveOID(params.id)
    }

    if (pkg) {
      def sdf = new java.text.SimpleDateFormat('yyyy-MM-dd')
      def export_date = sdf.format(new Date());

      def filename = "GOKb Export : ${pkg.name} : ${export_date}.tsv"

      try {
        response.setContentType('text/tab-separated-values');
        response.setHeader("Content-disposition", "attachment; filename=\"${filename}\"")
        response.contentType = "text/tab-separated-values" // "text/tsv"

        def out = response.outputStream
        out.withWriter { writer ->

          def sanitize = { it ? "${it}".trim() : "" }

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
                        'access_type\t'+
                        'zdb_id\n');

          // scroll(ScrollMode.FORWARD_ONLY)
          def session = sessionFactory.getCurrentSession()
          def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
          def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status = :sc and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p',pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sc', status_current)
          query.setParameter('ct', combo_tipps)

          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          while (tipps.next()) {
            def tipp_id = tipps.get(0);

              TitleInstancePackagePlatform.withNewSession {
                def tipp = TitleInstancePackagePlatform.get(tipp_id)

                if (tipp.coverageStatements?.size() > 0) {
                  tipp.coverageStatements.each { cst ->
                    writer.write(
                                sanitize( tipp.title.name ) + '\t' +
                                (tipp.title.hasProperty('dateFirstInPrint') ? sanitize( tipp.title.getIdentifierValue('pISBN') ) : sanitize( tipp.title.getIdentifierValue('ISSN') ) )+ '\t' +
                                (tipp.title.hasProperty('dateFirstInPrint') ? sanitize( tipp.title.getIdentifierValue('ISBN') ) : sanitize( tipp.title.getIdentifierValue('eISSN') ) )+ '\t' +
                                sanitize( cst.startDate ) + '\t' +
                                sanitize( cst.startVolume ) + '\t' +
                                sanitize( cst.startIssue ) + '\t' +
                                sanitize( cst.endDate ) + '\t' +
                                sanitize( cst.endVolume ) + '\t' +
                                sanitize( cst.endIssue ) + '\t' +
                                sanitize( tipp.url ) + '\t' +
                                (tipp.title.hasProperty('firstAuthor') ? sanitize( tipp.title.firstAuthor ) : '') + '\t'+
                                sanitize( tipp.title.getId() ) + '\t' +
                                sanitize( cst.embargo ) + '\t' +
                                sanitize( cst.coverageDepth ) + '\t' +
                                sanitize( cst.coverageNote ) + '\t' +
                                sanitize( tipp.title.getCurrentPublisher()?.name ) + '\t' +
                                sanitize( tipp.title.getPrecedingTitleId() ) + '\t' +
                                (tipp.title.hasProperty('dateFirstInPrint') ? sanitize( tipp.title.dateFirstInPrint ) : '') + '\t' +
                                (tipp.title.hasProperty('dateFirstOnline') ? sanitize( tipp.title.dateFirstOnline ) : '') + '\t' +
                                (tipp.title.hasProperty('volumeNumber') ? sanitize( tipp.title.volumeNumber ) : '') + '\t' +
                                (tipp.title.hasProperty('editionStatement') ? sanitize( tipp.title.editionStatement ) : '') + '\t' +
                                (tipp.title.hasProperty('firstEditor') ? sanitize( tipp.title.firstEditor ) : '') + '\t' +
                                '\t' +  // parent_publication_title_id
                                sanitize( tipp.title?.medium?.value ) + '\t' +  // publication_type
                                sanitize( tipp.paymentType?.value ) + '\t' +  // access_type
                                sanitize( tipp.title.getIdentifierValue('ZDB')) +
                                '\n');
                  }
                }
                else {
                    writer.write(
                                sanitize( tipp.title.name ) + '\t' +
                                (tipp.title.hasProperty('dateFirstInPrint') ? sanitize( tipp.title.getIdentifierValue('pISBN') ) : sanitize( tipp.title.getIdentifierValue('ISSN') ) )+ '\t' +
                                (tipp.title.hasProperty('dateFirstInPrint') ? sanitize( tipp.title.getIdentifierValue('ISBN') ) : sanitize( tipp.title.getIdentifierValue('eISSN') ) )+ '\t' +
                                sanitize( tipp.startDate ) + '\t' +
                                sanitize( tipp.startVolume ) + '\t' +
                                sanitize( tipp.startIssue ) + '\t' +
                                sanitize( tipp.endDate ) + '\t' +
                                sanitize( tipp.endVolume ) + '\t' +
                                sanitize( tipp.endIssue ) + '\t' +
                                sanitize( tipp.url ) + '\t' +
                                (tipp.title.hasProperty('firstAuthor') ? sanitize( tipp.title.firstAuthor ) : '') + '\t'+
                                sanitize( tipp.title.getId() ) + '\t' +
                                sanitize( tipp.embargo ) + '\t' +
                                sanitize( tipp.coverageDepth ) + '\t' +
                                sanitize( tipp.coverageNote ) + '\t' +
                                sanitize( tipp.title.getCurrentPublisher()?.name ) + '\t' +
                                sanitize( tipp.title.getPrecedingTitleId() ) + '\t' +
                                (tipp.title.hasProperty('dateFirstInPrint') ? sanitize( tipp.title.dateFirstInPrint ) : '') + '\t' +
                                (tipp.title.hasProperty('dateFirstOnline') ? sanitize( tipp.title.dateFirstOnline ) : '') + '\t' +
                                (tipp.title.hasProperty('volumeNumber') ? sanitize( tipp.title.volumeNumber ) : '' + '\t') +
                                (tipp.title.hasProperty('editionStatement') ? sanitize( tipp.title.editionStatement ) : '') + '\t' +
                                (tipp.title.hasProperty('firstEditor') ? sanitize( tipp.title.firstEditor ) : '') + '\t' +
                                '\t' +  // parent_publication_title_id
                                sanitize( tipp.title?.medium?.value ) + '\t' +  // publication_type
                                sanitize( tipp.paymentType?.value ) + '\t' +  // access_type
                                sanitize( tipp.title.getIdentifierValue('ZDB')) +
                                '\n');
                }
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
    else {
      response.sendError(404, "No Package was not found for ${params.id}!".toString())
    }
  }

  def packageTSVExport() {


    def sdf = new java.text.SimpleDateFormat('yyyy-MM-dd')
    def export_date = sdf.format(new Date());


    def pkg = Package.findByUuid(params.id)

    if(!pkg) {
      pkg = genericOIDService.resolveOID(params.id)
    }

    if (pkg) {
      def filename = "GoKBPackage-${params.id}.tsv";

      try {
        response.setContentType('text/tab-separated-values');
        response.setHeader("Content-disposition", "attachment; filename=\"${filename}\"")
        response.contentType = "text/tab-separated-values" // "text/tsv"

        def out = response.outputStream
        out.withWriter { writer ->

          def sanitize = { it ? "${it}".trim() : "" }


          // As per spec header at top of file / section
          writer.write("GOKb Export : ${pkg.provider?.name} : ${pkg.name} : ${export_date}\n");

          writer.write('TIPP ID\t'+
                        'TIPP URL\t'+
                        'Title ID\t'+
                        'Title\t'+
                        'TIPP Status\t'+
                        '[TI] Publisher\t'+
                        '[TI] Imprint\t'+
                        '[TI] Published From\t'+
                        '[TI] Published to\t'+
                        '[TI] Medium\t'+
                        '[TI] OA Status\t'+
                        '[TI] Continuing series\t'+
                        '[TI] ISSN\t'+
                        '[TI] EISSN\t'+
                        '[TI] ZDB-ID\t'+
                        'Package\t'+
                        'Package ID\t'+
                        'Package URL\t'+
                        'Platform\t'+
                        'Platform URL\t'+
                        'Platform ID\t'+
                        'Reference\t'+
                        'Edit Status\t'+
                        'Access Start Date\t'+
                        'Access End Date\t'+
                        'Coverage Start Date\t'+
                        'Coverage Start Volume\t'+
                        'Coverage Start Issue\t'+
                        'Coverage End Date\t'+
                        'Coverage End Volume\t'+
                        'Coverage End Issue\t'+
                        'Embargo\t'+
                        'Coverage depth\t'+
                        'Coverage note\t'+
                        'Host Platform URL\t'+
                        'Format\t'+
                        'Payment Type\t'+
                        '[TI] DOI\t'+
                        '[TI] ISBN\t'+
                        '[TI] pISBN'+
                        '\n');

          def session = sessionFactory.getCurrentSession()
          def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status <> :sd and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p', pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sd', status_deleted)
          query.setParameter('ct', combo_tipps)

          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          while (tipps.next()) {

            def tipp_id = tipps.get(0);
            TitleInstancePackagePlatform.withNewSession {
              TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

              if(tipp.coverageStatements?.size() > 0) {
                tipp.coverageStatements.each { tcs ->
                  writer.write(
                          sanitize(tipp.getId()) + '\t' +
                          sanitize(tipp.url) + '\t' +
                          sanitize(tipp.title.getId()) + '\t' +
                          sanitize(tipp.title.name) + '\t' +
                          sanitize(tipp.status.value) + '\t' +
                          sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                          sanitize(tipp.title.imprint?.name) + '\t' +
                          sanitize(tipp.title.publishedFrom) + '\t' +
                          sanitize(tipp.title.publishedTo) + '\t' +
                          sanitize(tipp.title.medium?.value) + '\t' +
                          sanitize(tipp.title.OAStatus?.value) + '\t' +
                          sanitize(tipp.title.continuingSeries?.value) + '\t' +
                          sanitize(tipp.title.getIdentifierValue('ISSN')) + '\t' +
                          sanitize(tipp.title.getIdentifierValue('eISSN')) + '\t' +
                          sanitize(tipp.title.getIdentifierValue('ZDB')) + '\t' +
                          sanitize(pkg.name) + '\t' + sanitize(pkg.getId()) + '\t' +
                          '\t' +
                          sanitize(tipp.hostPlatform.name) + '\t' +
                          sanitize(tipp.hostPlatform.primaryUrl) + '\t' +
                          sanitize(tipp.hostPlatform.getId()) + '\t' +
                          '\t' +
                          sanitize(tipp.editStatus?.value) + '\t' +
                          sanitize(tipp.accessStartDate) + '\t' +
                          sanitize(tipp.accessEndDate) + '\t' +
                          sanitize(tcs.startDate) + '\t' +
                          sanitize(tcs.startVolume) + '\t' +
                          sanitize(tcs.startIssue) + '\t' +
                          sanitize(tcs.endDate) + '\t' +
                          sanitize(tcs.endVolume) + '\t' +
                          sanitize(tcs.endIssue) + '\t' +
                          sanitize(tcs.embargo) + '\t' +
                          sanitize(tcs.coverageDepth) + '\t' +
                          sanitize(tcs.coverageNote) + '\t' +
                          sanitize(tipp.hostPlatform.primaryUrl) + '\t' +
                          sanitize(tipp.format?.value) + '\t' +
                          sanitize(tipp.paymentType?.value) + '\t' +
                          sanitize(tipp.title.getIdentifierValue('DOI')) + '\t' +
                          sanitize(tipp.title.getIdentifierValue('ISBN')) + '\t' +
                          sanitize(tipp.title.getIdentifierValue('pISBN')) +
                          '\n');
                }
              }
              else {
                writer.write(
                        sanitize(tipp.getId()) + '\t' +
                        sanitize(tipp.url) + '\t' +
                        sanitize(tipp.title.getId()) + '\t' +
                        sanitize(tipp.title.name) + '\t' +
                        sanitize(tipp.status.value) + '\t' +
                        sanitize(tipp.title.getCurrentPublisher()?.name) + '\t' +
                        sanitize(tipp.title.imprint?.name) + '\t' +
                        sanitize(tipp.title.publishedFrom) + '\t' +
                        sanitize(tipp.title.publishedTo) + '\t' +
                        sanitize(tipp.title.medium?.value) + '\t' +
                        sanitize(tipp.title.OAStatus?.value) + '\t' +
                        sanitize(tipp.title.continuingSeries?.value) + '\t' +
                        sanitize(tipp.title.getIdentifierValue('ISSN')) + '\t' +
                        sanitize(tipp.title.getIdentifierValue('eISSN')) + '\t' +
                        sanitize(tipp.title.getIdentifierValue('ZDB')) + '\t' +
                        sanitize(pkg.name) + '\t' + sanitize(pkg.getId()) + '\t' +
                        '\t' +
                        sanitize(tipp.hostPlatform.name) + '\t' +
                        sanitize(tipp.hostPlatform.primaryUrl) + '\t' +
                        sanitize(tipp.hostPlatform.getId()) + '\t' +
                        '\t' +
                        sanitize(tipp.editStatus?.value) + '\t' +
                        sanitize(tipp.accessStartDate) + '\t' +
                        sanitize(tipp.accessEndDate) + '\t' +
                        sanitize(tipp.startDate) + '\t' +
                        sanitize(tipp.startVolume) + '\t' +
                        sanitize(tipp.startIssue) + '\t' +
                        sanitize(tipp.endDate) + '\t' +
                        sanitize(tipp.endVolume) + '\t' +
                        sanitize(tipp.endIssue) + '\t' +
                        sanitize(tipp.embargo) + '\t' +
                        sanitize(tipp.coverageDepth) + '\t' +
                        sanitize(tipp.coverageNote) + '\t' +
                        sanitize(tipp.hostPlatform.primaryUrl) + '\t' +
                        sanitize(tipp.format?.value) + '\t' +
                        sanitize(tipp.paymentType?.value) + '\t' +
                        sanitize(tipp.title.getIdentifierValue('DOI')) + '\t' +
                        sanitize(tipp.title.getIdentifierValue('ISBN')) + '\t' +
                        sanitize(tipp.title.getIdentifierValue('pISBN')) +
                        '\n');
              }
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
    else {
      response.sendError(404, "No Package was not found for ${params.id}!".toString())
    }
  }
}
