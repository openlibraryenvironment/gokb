package org.gokb.cred

import javax.persistence.Transient
import org.gokb.refine.*

class Package extends KBComponent {

  // Owens defaults:
  // Status default to 'Current'
  // Scope default to 'Front File'
  // Breakable?: Y
  // Parent?: N // SO: This should not be needed really now. We should be able to test children for empty set.
  // Global?: Y
  // Fixed?: Y
  // Consistent?: N

  // Refdata
  RefdataValue scope
//  RefdataValue type
  RefdataValue listStatus
  RefdataValue breakable
  RefdataValue consistent
  RefdataValue fixed
  RefdataValue paymentType
  RefdataValue global
  
  Date dateCreated
  Date lastUpdated
  RefineProject lastProject

//  static hasMany = [tipps: TitleInstancePackagePlatform]
//  static mappedBy = [tipps: 'pkg']
  static manyByCombo = [
	tipps 				: TitleInstancePackagePlatform,
	subPkgs				: Package,
  ]
  
  static hasByCombo = [
	parent				: Package,
	provider			: Org,
	nominalPlatform		: Platform,
  ]
  
  static mappedByCombo = [
	parent		: 'subPkgs',
  ]

  static mapping = {
//           identifier column:'pkg_identifier'
          packageType column:'pkg_type_rv_fk'
        packageStatus column:'pkg_status_rv_fk'
    packageListStatus column:'pkg_list_status_rv_fk'
//      nominalPlatform column:'pkg_nominal_platform_fk'
          lastProject column:'pkg_refine_project_fk'
//                tipps sort:'title.name', order: 'asc'
  }

  static constraints = {
          packageType(nullable:true, blank:false)
        packageStatus(nullable:true, blank:false)
//      nominalPlatform(nullable:true, blank:false)
    packageListStatus(nullable:true, blank:false)
          lastProject(nullable:true, blank:false)
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

}
