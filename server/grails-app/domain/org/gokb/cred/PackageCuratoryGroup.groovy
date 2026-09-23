package org.gokb.cred

class PackageCuratoryGroup {
  Package pkg
  CuratoryGroup group

  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'cgpa_id'
    pkg column:'cgpa_pkg_fk', index: 'cgpa_cmp_idx,cgpa_full_idx'
    group column:'cgpa_group_fk', index: 'cgpa_file_idx,cgpa_full_idx'
    dateCreated column:'cgpa_date_created', index: 'cgpa_created_idx'
    lastUpdated column:'cgpa_last_updated'
  }

  static constraints = {
    pkg(nullable:false, blank:false)
    group(nullable:false, blank:false)
  }

  static int removeAllForComponent(Package comp) {
    return executeUpdate('DELETE FROM PackageCuratoryGroup WHERE pkg = :comp', [comp: comp])
  }
}
