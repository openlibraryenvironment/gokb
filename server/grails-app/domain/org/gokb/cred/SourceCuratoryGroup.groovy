package org.gokb.cred

class SourceCuratoryGroup {
  Source source
  CuratoryGroup group

  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'cgs_id'
    source column:'cgs_src_fk', index: 'cgs_cmp_idx,cgs_full_idx'
    group column:'cgs_group_fk', index: 'cgs_file_idx,cgs_full_idx'
    dateCreated column:'cgs_date_created', index: 'cgs_created_idx'
    lastUpdated column:'cgs_last_updated'
  }

  static constraints = {
    source(nullable:false, blank:false)
    group(nullable:false, blank:false)
  }

  static int removeAllForComponent(Source comp) {
    return executeUpdate('DELETE FROM SourceCuratoryGroup WHERE source = :comp', [comp: comp])
  }
}
