package org.gokb

import java.security.MessageDigest
import org.springframework.security.access.annotation.Secured;
import grails.converters.*
import org.gokb.cred.*



class UploadController {

  def uploadAnalysisService
  def TSVIngestionService

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
        def deposit_token = java.util.UUID.randomUUID().toString()
        temp_file = TSVIngestionService.handleTempFile(deposit_token, request.getFile("submissionFile"))

        if ( temp_file.exists() ) {
          def info = TSVIngestionService.analyseFile(temp_file)

          log.debug("Got file with md5 ${info.md5sumHex}.. lookup")

          def existing_file = DataFile.findByMd5(info.md5sumHex)

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

            uploadAnalysisService.analyse(temp_file, new_datafile)
            log.debug("Completed Analysis")

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
        log.error("Problem processing uploaded file",e)
      }finally{
        temp_file?.delete()
      }
    }
    render(view:'index')
  }
}
