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

class PackagesController {

  def genericOIDService
  def springSecurityService
  def concurrencyManagerService
  def TSVIngestionService

  def index() {
    def result = [:]
    // User user = springSecurityService.currentUser
    // For now, just get all the items owned by this user - eventually have a folder structure
    // result.saved_items = SavedSearch.executeQuery('Select ss from SavedSearch as ss where ss.owner = ?',[user]);
    log.debug("Packages::index ${params}.");
    result
  }

  def packageContent() {
    log.debug("packageContent::${params}")
  }

  def deposit() {
    def result = [:]
    log.debug("deposit::${params}")

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

          DataFile.withNewTransaction { status ->

            def info = analyse(temp_file);

            log.debug("Got file with md5 ${info.md5sumHex}.. lookup by md5");
            def existing_file = DataFile.findByMd5(info.md5sumHex);

            if ( existing_file != null ) {
              log.debug("Found a match !")
              redirect(controller:'resource',action:'show',id:"org.gokb.cred.DataFile:${existing_file.id}")
            }
            else {
              log.debug("Create new datafile");
              def new_datafile = new DataFile(
                                          guid:deposit_token,
                                          md5:info.md5sumHex,
                                          uploadName:upload_filename,
                                          name:upload_filename,
                                          filesize:info.filesize,
                                          uploadMimeType:upload_mime_type).save(failOnError:true, flush:true)

              log.debug("Saved new datafile : ${new_datafile.id}");
              new_datafile_id = new_datafile.id
              new_datafile.fileData = temp_file.getBytes()
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
                                          job)
            }
            catch ( Exception e ) {
              log.error("Problem",e)
            }
            finally {
              log.debug ("Async Data insert complete")
            }
          }

          background_job.startOrQueue()


        }
        else {
          log.error("Missing parameters :: ${params}");
        }
      }
    }
    
    result
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
