package org.gokb.cred

class PlatformCuratoryGroup {
  Platform platform
  CuratoryGroup group

  Date dateCreated
  Date lastUpdated

  static mapping = {
    id column:'plcg_id'
    platform column:'plcg_platform_fk', index: 'plcg_cmp_idx,plcg_full_idx'
    group column:'plcg_group_fk', index: 'plcg_file_idx,plcg_full_idx'
    dateCreated column:'plcg_date_created', index: 'plcg_created_idx'
    lastUpdated column:'plcg_last_updated'
  }

  static constraints = {
    platform(nullable:false, blank:false)
    group(nullable:false, blank:false)
  }

  static int removeAllForComponent(Platform comp) {
    return executeUpdate('DELETE FROM PlatformCuratoryGroup WHERE platform = :comp', [comp: comp])
  }
}
