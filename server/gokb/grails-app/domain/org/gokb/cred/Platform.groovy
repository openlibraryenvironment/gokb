package org.gokb.cred

class Platform extends KBComponent {

  // String impId
  String name
  String normname
  String primaryUrl
  String provenance
  RefdataValue type
  RefdataValue status
  Date dateCreated
  Date lastUpdated


  static mappedBy = [tipps: 'platform']
  static hasMany = [tipps: TitleInstancePackagePlatform]

  static mapping = {
  //              id column:'plat_id'
  //         version column:'plat_version'
  //           impId column:'plat_imp_id', index:'plat_imp_id_idx'
              name column:'plat_name'
          normname column:'plat_normalised_name'
        provenance column:'plat_data_provenance'
        primaryUrl column:'plat_primary_url'
              type column:'plat_type_rv_fk'
            status column:'plat_status_rv_fk'
             tipps sort: 'title.title', order: 'asc'
  }

  static constraints = {
  //  impId(nullable:true, blank:false)
    primaryUrl(nullable:true, blank:false)
    provenance(nullable:true, blank:false)
    type(nullable:true, blank:false)
    status(nullable:true, blank:false)
  }
}
