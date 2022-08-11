package org.gokb

import org.gokb.cred.*
import org.springframework.security.access.annotation.Secured;
import java.security.MessageDigest
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

class IngestController {

  def concurrencyManagerService
  def genericOIDService
  def TSVIngestionService
  def springSecurityService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result=[:]
    result.existingProfiles = IngestionProfile.findAll()
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def addProfile() {
    log.debug("addProfile ${params}")

    def result=[:]

    def pkg_source = genericOIDService.resolveOID(params.sourceId)
    def package_type = genericOIDService.resolveOID(params.packageType)

    log.debug("Adding new profile, source=${pkg_source} ${pkg_source?.class.name}, type=${package_type}, packageName:${params.packageName}")

    if ( pkg_source != null &&
         package_type != null &&
         params.packageName != null ) {

      log.debug("Creating...1")

      def new_profile = new IngestionProfile(
        name:params.profileName,
        packageName:params.packageName,
        packageType:package_type,
        platformUrl:params.platformUrl
      )
      new_profile.source=pkg_source

      log.debug("Create2")
      log.debug("\n\nCreated ${new_profile} ${new_profile.packageName}- now save")

      if ( new_profile.save(flush:true, failOnError:true) ) {
        log.debug("Saved new profile ${new_profile}");
      }
      else {
        log.error("Problem creating new profile");
        new_profile.errors.each { 
          log.error("Problem: ${it}");
        }
      }
    }
    else {
      log.debug("Missing source, type or package name")
    }

    redirect(action:'index')
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def profile() {
    log.debug("profile")

    def result = [:]
    User user = springSecurityService.currentUser

    result.ip = IngestionProfile.get(params.id);
    def ingestion_profile = result.ip


    if ( request.method=='POST' && result.ip ) {

      def ingestion_profile_id = params.id;
      def new_datafile_id = null;

      // Create a new transaction so we can commit it and have everything cleaned up by the time we
      // either submit the job to a background task or wait for it to complete synchronously.
      DataFile.withNewTransaction { status ->

        log.debug("Form post")
        def upload_mime_type = request.getFile("submissionFile")?.contentType
        def upload_filename = request.getFile("submissionFile")?.getOriginalFilename()
        def deposit_token = java.util.UUID.randomUUID().toString();
        def temp_file = copyUploadedFile(request.getFile("submissionFile"), deposit_token);
        def info = analyse(temp_file);
  
        log.debug("Got file with md5 ${info.md5sumHex}.. lookup by md5");
        def existing_file = DataFile.findByMd5(info.md5sumHex);
  
        if ( existing_file != null ) {
          log.debug("Found a match !")

          new_datafile_id = existing_file.id
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
  
          if (!ingestion_profile.ingestions) {
                   ingestion_profile.ingestions=[]
          }
          ingestion_profile.ingestions << new ComponentIngestionSource(profile:ingestion_profile, component:new_datafile)
  
          new_datafile.save(failOnError:true,flush:true)
          ingestion_profile.save(flush:true)
          log.debug("Saved file on database ")
  
          redirect(controller:'resource',action:'show',id:"org.gokb.cred.IngestionProfile:${ingestion_profile.id}")
        }
  
      }

      if (new_datafile_id) {
        log.debug("First transaction completed - datafile read and saved, move on to processing");

        // Transactional part done. now queue the job
        Job background_job = concurrencyManagerService.createJob { Job job ->
          // Create a new session to run the ingest.
          try {
            TSVIngestionService.ingest(ingestion_profile_id, new_datafile_id, job, null, null, user)
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
        log.warn("No datafile id!");
      }
    }
    else {
      log.debug("get")
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
