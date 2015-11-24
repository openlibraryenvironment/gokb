package org.gokb

import grails.converters.*
import org.springframework.security.acls.model.NotFoundException
import grails.plugins.springsecurity.Secured

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*

import grails.plugin.gson.converters.GSON

import org.springframework.web.multipart.MultipartHttpServletRequest


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
        log.debug("Multipart")
        def upload_mime_type = request.getFile("content")?.contentType  // getPart?
        def upload_filename = request.getFile("content")?.getOriginalFilename()

        if ( upload_mime_type && 
             upload_filename &&
             params.package &&
             params.platformUrl &&
             params.format &&
             params.source ) {
          log.debug("Got file content")

          DataFile.withNewTransaction { status ->

            def deposit_token = java.util.UUID.randomUUID().toString();
            def temp_file = copyUploadedFile(request.getFile("submissionFile"), deposit_token);
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
              TSVIngestionService.ingest(RefdataCategory.lookupOrCreate('ingest.filetype',params.format).save(),
                                         params.package,
                                         params.platformUrl,
                                         Source.findByName(params.source),
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
      }
    }
    
    result
  }



}
