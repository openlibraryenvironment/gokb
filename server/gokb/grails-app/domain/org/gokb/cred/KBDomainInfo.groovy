package org.gokb.cred

class KBDomainInfo {

  String dcName
  String displayName
  RefdataValue type

  public String getNiceName() {
    return "Domain Info";
  }

  static mapping = {
    id column:'kbd_id'
    dcName column:'kbd_name'
    displayName column:'kbd_display_name'
    type column:'kbd_type'
  }

  static constraints = {
    dcName (nullable:false, blank:false)
    displayName (nullable:false, blank:false)
    type (nullable:true, blank:false)
  }

}
