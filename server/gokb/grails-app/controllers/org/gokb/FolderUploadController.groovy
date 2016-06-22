package org.gokb

import java.security.MessageDigest
import org.springframework.security.access.annotation.Secured;
import grails.converters.*
import org.gokb.cred.*



class FolderUploadController {

  def folderService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processSubmission() {
    log.debug("FolderUploadController::processSubmission(${params})");
    if ( request.method == 'POST' ) {
      def temp_file
      def default_folder = params.defaultFolder ? Folder.get(params.defaultFolder) : null;
      def org = params.ownerOrg ? Party.get(params.ownerOrg) : null;

      log.debug("Converted ${params.ownerOrg} to org ${org}");

      try {
        def upload_mime_type = request.getFile("submissionFile")?.contentType
        def upload_filename = request.getFile("submissionFile")?.getOriginalFilename()

        // store input stream locally
        def deposit_token = java.util.UUID.randomUUID().toString();
        temp_file = copyUploadedFile(request.getFile("submissionFile"), deposit_token);

        folderService.enqueTitleList(temp_file, default_folder, request.user, org, [:]);
      }
      catch ( Exception e ) {
        log.error("Problem processing uploaded file",e);
      }finally{
        // temp_file?.delete()
      }
    }

    log.debug("Send back to referer");

    redirect(url: request.getHeader('referer'))
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

    log.debug("temp file is ${temp_file_name}");
    temp_file
  }

  private def validateUploadDir(path) {
    File f = new File(path);
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }

}
