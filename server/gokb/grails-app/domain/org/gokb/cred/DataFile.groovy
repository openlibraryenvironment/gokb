package org.gokb.cred

class DataFile extends KBComponent {

  String guid
  String md5
  String uploadName
  String uploadMimeType

  static constraints = {
    guid (nullable:false, blank:false)
    md5 (nullable:false, blank:false)
    uploadName (nullable:true, blank:false)
    uploadMimeType (nullable:true, blank:false)
  }
}
