package org.gokb.cred

import javax.persistence.Transient

class Platform extends KBComponent {

  // String impId
  String primaryUrl
  String provenance
  RefdataValue type
  RefdataValue status
  Date dateCreated
  Date lastUpdated


//  static mappedBy = [tipps: 'platform']
//  static hasMany = [tipps: TitleInstancePackagePlatform]
  public static manyByCombo = [tipps : TitleInstancePackagePlatform]

  static mapping = {
        provenance column:'plat_data_provenance'
        primaryUrl column:'plat_primary_url'
              type column:'plat_type_rv_fk'
            status column:'plat_status_rv_fk'
//             tipps sort: 'title.name', order: 'asc'
  }

  static constraints = {
  //  impId(nullable:true, blank:false)
    primaryUrl(nullable:true, blank:false)
    provenance(nullable:true, blank:false)
    type(nullable:true, blank:false)
    status(nullable:true, blank:false)
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

}
