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

  @Transient
  static def oaiConfig = [
    id:'platforms',
    textDescription:'Platform repository for GOKb',
    query:" from Platform as o where o.status.value != 'Deleted'"
  ]

  /**
   *  Render this package as OAI_dc
   */
  @Transient
  def toOaiDcXml(builder, attr) {
    builder.'dc'(attr) {
      'dc:title' (name)
    }
  }

  /**
   *  Render this package as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def identifiers = getIds()

    builder.'gokb' (attr) {
      builder.'platform' (['id':(id)]) {
        builder.'name' (name)
        builder.'primaryUrl' (primaryUrl)
        builder.'authentication' (authentication?.value)
        builder.'software' (software?.value)
        builder.'service' (service?.value)
        builder.'status' (status?.value)
        if (identifiers) {
          builder.'identifiers' {
            identifiers.each { tid ->
              builder.'identifier' (['namespace':tid.namespace.value], tid.value)
            }
          }
        }
        if ( variantNames ) {
          builder.'variantNames' {
            variantNames.each { vn ->
              builder.'variantName' ( vn.variantName )
            }
          }
        }
      }
    }
  }

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
