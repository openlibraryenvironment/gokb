package org.gokb.cred

class OrgCuratoryGroup {
  Org org
  CuratoryGroup group

  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'cgo_id'
    org column:'cgo_org_fk', index: 'cgo_cmp_idx,cgo_full_idx'
    group column:'cgo_group_fk', index: 'cgo_file_idx,cgo_full_idx'
    dateCreated column:'cgo_date_created', index: 'cgo_created_idx'
    lastUpdated column:'cgo_last_updated'
  }

  static constraints = {
    org(nullable:false, blank:false)
    group(nullable:false, blank:false)
  }

  static int removeAllForComponent(Org comp) {
    return executeUpdate('DELETE FROM OrgCuratoryGroup WHERE org = :comp', [comp: comp])
  }
}
