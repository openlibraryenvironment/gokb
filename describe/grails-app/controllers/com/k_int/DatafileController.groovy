package com.k_int

import grails.plugins.springsecurity.Secured
import grails.converters.*
import org.elasticsearch.groovy.common.xcontent.*
import groovy.xml.MarkupBuilder
import com.k_int.describe.*;
import com.k_int.gokb.*


class DatafileController {

  def springSecurityService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    // List active
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def upload() { 
    def upload_file = request.getFile("upload_file")  // ?.inputStream
    def original_filename = request.getFile("upload_file")?.originalFilename

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
