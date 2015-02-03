package org.gokb.cred

class DataFile extends KBComponent {

  String guid
  String md5
  String uploadName
  String uploadMimeType
  String filesize
  String doctype
  byte[] fileData

  static constraints = {
    guid (nullable:false, blank:false)
    md5 (nullable:false, blank:false)
    uploadName (nullable:true, blank:false)
    uploadMimeType (nullable:true, blank:false)
    filesize (nullable:true, blank:false)
    doctype (nullable:true, blank:false)
    fileData(nullable:true,blank:false,maxSize: 1024 * 1024 * 2)
  }

  static mapping = {
    guid column:'df_guid'
    md5 column:'df_md5'
    uploadName column:'df_upload_name'
    uploadMimeType column:'df_mime_type'
    filesize column:'df_filesize'
    doctype column:'df_doctype'
    fileData column:'df_file_data'
  }

  static manyByCombo = [
    attachedToComponents : KBComponent
  ]

  static mappedByCombo = [
    attachedToComponents : 'fileAttachments'
  ]

}
