package org.gokb.cred

class ComponentAttachment {
  static final String RD_STATUS = 'ComponentAttachment.Status'
  static final String STATUS_ACTIVE = "Active"
  static final String STATUS_SUPERSEDED = "Superseded"
  static final String STATUS_DELETED = "Deleted"
  static final String STATUS_EXPIRED = "Expired"


  KBComponent component
  DataFile file
  String importName

  Date dateCreated
  Date lastUpdated

  static mapping = {
    component column:'ca_comp_fk', index: 'ca_cmp_idx,ci_full_idx'
    file column:'ca_file_fk', index: 'ca_file_idx,ci_full_idx'
    dateCreated column:'ci_date_created', index: 'ci_created_idx'
    lastUpdated column:'ci_last_updated',
    importName column: 'ci_import_name'
  }

  static constraints = {
    component(nullable:false, blank:false)
    file(nullable:false, blank:false)
    importName(nullable: true, blank:false)
  }

  static void removeAll(KBComponent comp) {
    executeUpdate 'DELETE FROM ComponentAttachment WHERE component = :comp', [comp: comp]
  }
}
