package com.k_int

import grails.plugins.springsecurity.Secured
import grails.converters.*
import org.elasticsearch.groovy.common.xcontent.*
import groovy.xml.MarkupBuilder
import com.k_int.describe.*;
import com.k_int.gokb.*
import java.security.MessageDigest

class DatafileController {

  def springSecurityService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    // List active
    def result = [:]
    result.user = User.get(springSecurityService.principal.id);
    result.filepage = RawInputFile.findAllByOwner(result.user)

    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def upload() { 

    log.debug("Process upload");


    def upload_file = request.getFile("upload_file")  // ?.inputStream
    def original_filename = request.getFile("upload_file")?.originalFilename

    def user = User.get(springSecurityService.principal.id);

    if ( upload_file != null ) {

      // Create a checksum for the file..
      MessageDigest md5_digest = MessageDigest.getInstance("MD5");
      InputStream md5_is = upload_file.inputStream
      byte[] md5_buffer = new byte[8192];
      int md5_read = 0;
      while( (md5_read = md5_is.read(md5_buffer)) >= 0) {
        md5_digest.update(md5_buffer, 0, md5_read);
      }
      md5_is.close();
      byte[] md5sum = md5_digest.digest();
      String md5sumHex = new BigInteger(1, md5sum).toString(16);

      log.debug("MD5 of ${original_filename} is ${md5sumHex}");

      def new_file = new RawInputFile(md5sum:md5sumHex, filename:original_filename, uploadTimestamp:System.currentTimeMillis(),owner:user)
      new_file.save();

      // redirect action: 'index', params:[id:subscriptionInstance?.id], id:subscriptionInstance.id
      redirect controller:'datafile', action: 'identification', id:new_file.id
    }
    else {
      log.debug("upload file is null");
    }


    // Download the file to a temp file, then MD5 it - see aggr2 examples
    // def new_file = RawInputFile().save();
    
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def create() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def identification() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def chunking() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def fileMetadata() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def columns() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def firstPassData() {
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def rules() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def finalData() {
  }

}
