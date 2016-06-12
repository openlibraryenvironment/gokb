package org.gokb.cred


public class FolderEntry {

  Folder folder
  static BelongsTo = [ Folder ]

  public Object getLinkedItem() {
    return null;
  }

  public String getDisplayName() {
    return "none"
  }

}
