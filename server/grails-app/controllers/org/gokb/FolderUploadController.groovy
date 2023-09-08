package org.gokb

import java.security.MessageDigest
import org.springframework.security.access.annotation.Secured;
import grails.converters.*
import org.gokb.cred.*



class FolderUploadController {

  def folderService
  def TSVIngestionService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def processSubmission() {
    log.debug("FolderUploadController::processSubmission(${params})")
    if ( request.method == 'POST' ) {
      def temp_file
      def default_folder = params.defaultFolder ? Folder.get(params.defaultFolder) : null
      def org = params.ownerOrg ? Party.get(params.ownerOrg) : null

      log.debug("Converted ${params.ownerOrg} to org ${org}")

      try {
        def upload_mime_type = request.getFile("submissionFile")?.contentType
        def upload_filename = request.getFile("submissionFile")?.getOriginalFilename()

        // store input stream locally
        def deposit_token = java.util.UUID.randomUUID().toString()
        temp_file = TSVIngestionService.handleTempFile(deposit_token, request.getFile("submissionFile"))

        folderService.enqueTitleList(temp_file, default_folder, request.user, org, [:])
      }
      catch ( Exception e ) {
        log.error("Problem processing uploaded file",e)
      }finally{
        // temp_file?.delete()
      }
    }

    log.debug("Send back to referer")

    redirect(url: request.getHeader('referer'))
  }

}
