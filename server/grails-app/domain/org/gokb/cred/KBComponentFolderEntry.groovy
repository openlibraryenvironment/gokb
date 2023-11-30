package org.gokb.cred

import javax.persistence.Transient

/**
 * A folder entry that points to a component
 */
class KBComponentFolderEntry extends FolderEntry {

  KBComponent linkedComponent

  @Transient
  @Override
  public Object getLinkedItem() {
    linkedComponent;
  }

  @Transient
  @Override
  public String getDisplayName() {
    linkedComponent.name?.toString()
  }

}
