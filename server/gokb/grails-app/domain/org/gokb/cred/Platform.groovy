package org.gokb.cred

import javax.persistence.Transient

class Platform extends KBComponent {

  String primaryUrl
  RefdataValue authentication

  static hasMany = [roles: RefdataValue]
  
  static hasByCombo = [
	provider			: Org
  ]
  
  static refdataDefaults = [
	"authentication"	: "Unknown",
	"roles"				: ["Host"]
  ]
  
  static manyByCombo = [
	hostedTipps : TitleInstancePackagePlatform,
	linkedTipps : TitleInstancePackagePlatform,
	territories	: Territory
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

//  @Transient
//  def getPermissableCombos() {
//    [
//    ]
//  }

}
