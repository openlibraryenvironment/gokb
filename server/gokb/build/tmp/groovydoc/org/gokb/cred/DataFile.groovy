package org.gokb.cred
import org.apache.commons.lang.builder.HashCodeBuilder
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
   * Override so that we only return DataFiles that are editable on the 
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
  
  @Override
  public boolean equals(Object obj) {
    Object o = KBComponent.deproxy(obj)
    if ( o != null ) {
      // Deproxy the object first to ensure it isn't a hibernate proxy.
      return (this.getClassName() == o.getClass().name) && (this.hashCode() == o.hashCode())
    }

    // Return false if we get here.
    false
  }
  
  @Override
  public int hashCode () {
    new HashCodeBuilder(1, 3).
      append(md5).
      toHashCode()
  }
}
