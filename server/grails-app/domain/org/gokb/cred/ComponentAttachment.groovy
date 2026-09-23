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
    id column:'ca_id'
    component column:'ca_comp_fk', index: 'ca_cmp_idx,ca_full_idx'
    file column:'ca_file_fk', index: 'ca_file_idx,ca_full_idx'
    dateCreated column:'ca_date_created', index: 'ca_created_idx'
    lastUpdated column:'ca_last_updated'
    importName column: 'ca_import_name'
  }

  static constraints = {
    component(nullable:false, blank:false)
    file(nullable:false, blank:false)
    importName(nullable: true, blank:false)
  }

  static int removeAllForComponent(KBComponent comp) {
    return executeUpdate('DELETE FROM ComponentAttachment WHERE component = :comp', [comp: comp])
  }
}
