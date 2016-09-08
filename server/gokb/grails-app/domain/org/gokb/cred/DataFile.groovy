package org.gokb.cred

class DataFile extends KBComponent {

  String guid
  String md5
  String uploadName
  String uploadMimeType
  String filesize
  String doctype

  // II: This NEEDS to be a java.sql.Blob and not a byte[] - See Doc in KBPlus for example
  byte[] fileData
  RefdataValue canEdit
  
  static constraints = {
    guid (nullable:false, blank:false)
    md5 (nullable:false, blank:false)
    uploadName (nullable:true, blank:false)
    uploadMimeType (nullable:true, blank:false)
    filesize (nullable:true, blank:false)
    doctype (nullable:true, blank:false)
    fileData(nullable:true,blank:false,maxSize: 1024 * 1024 * 1024)
    canEdit(nullable:true, blank:false)
  }

  static mapping = {
    includes TitleInstance.mapping
    guid column:'df_guid'
    md5 column:'df_md5'
    uploadName column:'df_upload_name'
    uploadMimeType column:'df_mime_type'
    filesize column:'df_filesize'
    doctype column:'df_doctype'
    fileData column:'df_file_data'
    canEdit column:'df_canEdit'
  }

  static manyByCombo = [
    attachedToComponents : KBComponent
  ]

  static mappedByCombo = [
    attachedToComponents : 'fileAttachments'
  ]

    /**
   *  Override so that we only return DataFiles that are editable on the 
   * typedown searches
   */
  @Override
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    def editable = RefdataCategory.lookupOrCreate('YN', 'Yes').save()

    ql = DataFile.findAllByNameIlikeAndCanEdit("${params.q}%",editable,params)
    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

}
