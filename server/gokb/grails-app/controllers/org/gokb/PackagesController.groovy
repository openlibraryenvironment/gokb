package org.gokb

import grails.converters.*
import org.springframework.security.acls.model.NotFoundException
import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*
import grails.plugin.gson.converters.GSON
import org.springframework.web.multipart.MultipartHttpServletRequest
import com.k_int.ConcurrencyManagerService;
import com.k_int.ConcurrencyManagerService.Job
import java.security.MessageDigest
import grails.converters.JSON

import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.type.*
import org.hibernate.Hibernate



class PackagesController {

  def genericOIDService
  def springSecurityService
  def concurrencyManagerService
  def TSVIngestionService
  def ESWrapperService
  def grailsApplication
  def sessionFactory

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

  def index() {
    log.debug("packageContent::${params}")
    def result = [:]
    org.elasticsearch.groovy.node.GNode esnode = ESWrapperService.getNode()
    org.elasticsearch.groovy.client.GClient esclient = esnode.getClient()
    try {

      if ( params.q && params.q.length() > 0) {

        // Comment out replacement of ' by " so we can do exact string searching on identifiers - not sure what the use case
        // was for this anyway. Pls document in comment and re-add if needed.
        // params.q = params.q.replace('"',"'")
        params.q = params.q.replace('[',"(")
        params.q = params.q.replace(']',")")

        result.max = params.max ? Integer.parseInt(params.max) : 10;
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        def query_str = 'componentType:Package AND '+(params.q?:'*');

        log.debug("Searching for ${query_str}");

        def search = esclient.search {
                       indices grailsApplication.config.globalSearch.indices
                       types 'component'
                       source {
                         from = result.offset
                         size = result.max
                         query {
                           query_string (query: query_str)
                         }
                       }
                     }

        result.hits = search.response.hits

        if(search.response.hits.maxScore == Float.NaN) { //we cannot parse NaN to json so set to zero...
          search.response.hits.maxScore = 0;
        }

        result.resultsTotal = search.response.hits.totalHits
        log.debug("found ${result.resultsTotal} records")

        // We pre-process the facet response to work around some translation issues in ES

        if ( search.response.facets != null ) {
          result.facets = [:]
          search.response.facets.facets.each { facet ->
            def facet_values = []
            facet.value.entries.each { fe ->
              facet_values.add([term: fe.term,display:fe.term,count:"${fe?.count}"])
            }
            result.facets[facet.key] = facet_values
          }
        }
      }
    }
    catch ( Exception e ) {
      log.debug("Problem",e)
    }
    finally {
    }

    result;

  }

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
  def deposit() {
    def result = [:]
    log.debug("deposit::${params}")
    def jobid = null;
    Job background_job = null;

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
                new_datafile_id = existing_file.id
              }
              else {
                // redirect(controller:'resource',action:'show',id:"org.gokb.cred.DataFile:${existing_file.id}")
                result.message="Datafile already present with internal id org.gokb.cred.DataFile:${existing_file.id}";
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
  
  
                log.debug("Saved new datafile : ${new_datafile.id}");
                new_datafile_id = new_datafile.id
              }
            }
  
  
            log.debug("Create background job");
            def incremental_flag = params.incremental
            // Transactional part done. now queue the job
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
                                                         incremental_flag)
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
            jobid = background_job.id
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

    def pkg = genericOIDService.resolveOID(params.id)

    def sdf = new java.text.SimpleDateFormat('yyyy-MM-dd')
    def export_date = sdf.format(new java.util.Date());

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
                       'access_type\n');

          // scroll(ScrollMode.FORWARD_ONLY)
          def session = sessionFactory.getCurrentSession()
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status.value <> 'Deleted' and c.type.value = 'Package.Tipps' order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p',pkg.id, Hibernate.LONG)


          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          while (tipps.next()) {
            def tipp_id = tipps.get(0);

              TitleInstancePackagePlatform.withNewSession {
                def tipp = TitleInstancePackagePlatform.get(tipp_id)
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

}
