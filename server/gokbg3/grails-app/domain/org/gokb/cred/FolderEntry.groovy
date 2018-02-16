package org.gokb.cred


public class FolderEntry {

  Folder folder
  static BelongsTo = [ Folder ]
  // Timestamps
  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'fe_id'
    dateCreated column:'fe_date_created'
    lastUpdated column:'fe_last_updated'
  }

  static constraints = {
    dateCreated (nullable:true, blank:false)
    lastUpdated (nullable:true, blank:false)
  }


  public Object getLinkedItem() {
    return null;
  }

  public String getDisplayName() {
    return "none"
  }

}
