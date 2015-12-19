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


class PackagesController {

  def genericOIDService
  def springSecurityService
  def concurrencyManagerService
  def TSVIngestionService
  def ESWrapperService
  def grailsApplication


  def packageContent() {
    log.debug("packageContent::${params}")
    def result = [:]
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
    log.debug("deposit::${params}")
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
          def source = params.source
          def providerName = params.providerName
          def providerIdentifierNamespace = params.providerIdentifierNamespace

          def info = analyse(temp_file);

          TSVIngestionService.preflight(format_rdv,
                                        pkg,
                                        new java.net.URL(platformUrl),
                                        Source.findByName(source),
                                        request.getFile("content"),
                                        providerName,
                                        providerIdentifierNamespace)

        }
      }
    }
  }

  def deposit() {
    def result = [:]
    log.debug("deposit::${params}")
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
          def source = params.source
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
              redirect(controller:'resource',action:'show',id:"org.gokb.cred.DataFile:${existing_file.id}")
              return
            }
          }
          else {
            log.debug("Create new datafile");
            DataFile.withTransaction {
              def new_datafile = new DataFile(
                                          guid:deposit_token,
                                          md5:info.md5sumHex,
                                          uploadName:upload_filename,
                                          name:upload_filename,
                                          filesize:info.filesize,
                                          uploadMimeType:upload_mime_type).save(failOnError:true, flush:true)

              new_datafile.fileData = temp_file.getBytes()
              new_datafile.save(flush:true)


              log.debug("Saved new datafile : ${new_datafile.id}");
              new_datafile_id = new_datafile.id
            }
          }


          // Transactional part done. now queue the job
          Job background_job = concurrencyManagerService.createJob { Job job ->
            // Create a new session to run the ingest.
            try {
              TSVIngestionService.ingest2(format_rdv,
                                          pkg,
                                          new java.net.URL(platformUrl),
                                          Source.findByName(source),
                                          new_datafile_id,
                                          job,
                                          providerName,
                                          providerIdentifierNamespace)
            }
            catch ( Exception e ) {
              log.error("Problem",e)
            }
            finally {
              log.debug ("Async Data insert complete")
            }
          }

          background_job.description="Deposit datafile ${upload_filename}(as ${params.fmt} from ${source} ) and create/update package ${pkg}"
          background_job.startOrQueue()
          jobid = background_job.id
        }
        else {
          log.error("Missing parameters :: ${params}");
        }
      }
    }


    // Redirect to list of jobs
    redirect(controller:'admin', action:'jobs', params:[format:params.format, highlightJob:jobid]);
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



}
