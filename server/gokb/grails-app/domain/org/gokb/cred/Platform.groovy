package org.gokb.cred

import javax.persistence.Transient

class Platform extends KBComponent {

  String primaryUrl
  RefdataValue authentication
  RefdataValue software
  RefdataValue service

  static hasMany = [roles: RefdataValue]
  
  static hasByCombo = [
	provider : Org
  ]
  
  private static refdataDefaults = [
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

  static def refdataFind(params) {
    def result = []; 
    def ql = null;
    ql = Platform.findAllByNameIlike("${params.q}%",params)

    if ( ql ) { 
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }   
    }   

    result
  }

  def availableActions() {
    [ 
      [code:'platform::replacewith', label:'Replace platform with...'] 
    ]
  }

}
