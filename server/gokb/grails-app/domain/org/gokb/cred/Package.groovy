package org.gokb.cred

class Package extends KBComponent {

  String identifier
  String name
  // String impId
  RefdataValue packageType
  RefdataValue packageStatus
  RefdataValue packageListStatus
  Org contentProvider
  Platform nominalPlatform
  Date dateCreated
  Date lastUpdated


  static hasMany = [tipps: TitleInstancePackagePlatform, 
                    orgs: OrgRole]

  static mappedBy = [tipps: 'pkg', orgs: 'pkg']


  static mapping = {
  //                 id column:'pkg_id'
  //            version column:'pkg_version'
           identifier column:'pkg_identifier'
                 name column:'pkg_name'
  //               impId column:'pkg_imp_id', index:'pkg_imp_id_idx'
          packageType column:'pkg_type_rv_fk'
        packageStatus column:'pkg_status_rv_fk'
    packageListStatus column:'pkg_list_status_rv_fk'
      nominalPlatform column:'pkg_nominal_platform_fk'
                tipps sort:'title.title', order: 'asc'
//                 orgs sort:'org.name', order: 'asc'
  }

  static constraints = {
          packageType(nullable:true, blank:false)
        packageStatus(nullable:true, blank:false)
      contentProvider(nullable:true, blank:false)
      nominalPlatform(nullable:true, blank:false)
    packageListStatus(nullable:true, blank:false)
  }
}
