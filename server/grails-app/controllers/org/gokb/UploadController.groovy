package org.gokb

import java.security.MessageDigest
import org.springframework.security.access.annotation.Secured;
import grails.converters.*
import org.gokb.cred.*



class UploadController {

  def uploadAnalysisService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processSubmission() {
    if ( request.method == 'POST' ) {
      def temp_file
      try {
        def upload_mime_type = request.getFile("submissionFile")?.contentType
        def upload_filename = request.getFile("submissionFile")?.getOriginalFilename()
        // def input_stream = request.getFile("submissionFile")?.inputStream

        // store input stream locally
        def deposit_token = java.util.UUID.randomUUID().toString();
        temp_file = copyUploadedFile(request.getFile("submissionFile"), deposit_token);

        if ( temp_file.exists() ) {
          def info = analyse(temp_file);

          log.debug("Got file with md5 ${info.md5sumHex}.. lookup");

          def existing_file = DataFile.findByMd5(info.md5sumHex);

          if ( existing_file != null ) {
            log.debug("Found a match !")
            redirect(controller:'resource',action:'show',id:"org.gokb.cred.DataFile:${existing_file.id}")
          }
          else {
            def new_datafile = new DataFile(
                                            guid:deposit_token,
                                            md5:info.md5sumHex,
                                            uploadName:upload_filename,
                                            name:upload_filename,
                                            filesize:info.filesize,
                                            uploadMimeType:upload_mime_type).save(flush:true)

            uploadAnalysisService.analyse(temp_file, new_datafile);
            log.debug("Completed Analysis");

            new_datafile.fileData = temp_file.getBytes()
            new_datafile.save(flush:true)
            log.debug("Saved file on database ")
            redirect(controller:'resource',action:'show',id:"org.gokb.cred.DataFile:${new_datafile.id}")
          }
        }else{
          log.error("Could not create temp file!")
        }
      }
      catch ( Exception e ) {
        log.error("Problem processing uploaded file",e);
      }finally{
        temp_file?.delete()
      }
    }
    render(view:'index');
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

    temp_file.createNewFile()

    if( temp_file?.canWrite() ) {
    // Copy the upload file to a temporary space

      inputfile.transferTo(temp_file);
    } else {
      log.debug("copy to ${temp_file_name} failed..")
    }

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
