package org.gokb.cred

class Package extends KBComponent {

  // Owens defaults:
  // Status default to 'Current'
  // Scope default to 'Front File'
  // Breakable?: Y
  // Parent?: N
  // Global?: Y
  // Fixed?: Y
  // Consistent?: N

  String identifier
  RefdataValue packageType
  RefdataValue packageStatus
  RefdataValue packageListStatus
  RefdataValue packageScope
  RefdataValue breakable
  RefdataValue parent
  RefdataValue global
  RefdataValue fixed
  RefdataValue consistent
  Platform nominalPlatform
  Date dateCreated
  Date lastUpdated


  static hasMany = [tipps: TitleInstancePackagePlatform]
  static mappedBy = [tipps: 'pkg']


  static mapping = {
           identifier column:'pkg_identifier'
          packageType column:'pkg_type_rv_fk'
        packageStatus column:'pkg_status_rv_fk'
    packageListStatus column:'pkg_list_status_rv_fk'
      nominalPlatform column:'pkg_nominal_platform_fk'
                tipps sort:'title.name', order: 'asc'
  }

  static constraints = {
          packageType(nullable:true, blank:false)
        packageStatus(nullable:true, blank:false)
      nominalPlatform(nullable:true, blank:false)
    packageListStatus(nullable:true, blank:false)
  }
}
