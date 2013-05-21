package org.gokb.cred

import javax.persistence.Transient

class Platform extends KBComponent {

  // String impId
  String primaryUrl
  RefdataValue authentication
//  String provenance
//  RefdataValue type
////  RefdataValue status
//  Date dateCreated
//  Date lastUpdated

//  static mappedBy = [tipps: 'platform']
  static hasMany = [roles: RefdataValue]
  static manyByCombo = [
	hostedTipps : TitleInstancePackagePlatform,
	linkedTipps : TitleInstancePackagePlatform,
  ]

  static mapping = {
        primaryUrl column:'plat_primary_url'
    authentication column:'plat_authentication_fk_rv'
//             tipps sort: 'title.name', order: 'asc'
  }

  static constraints = {
  //  impId(nullable:true, blank:false)
    primaryUrl		(nullable:true, blank:false)
    authentication	(nullable:true, blank:false)
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

}
