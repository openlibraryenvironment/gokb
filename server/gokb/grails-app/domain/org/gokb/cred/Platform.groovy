package org.gokb.cred

import javax.persistence.Transient
import groovy.util.logging.*

@Log4j
class Platform extends KBComponent {

  String primaryUrl
  RefdataValue authentication
  RefdataValue software
  RefdataValue service
  RefdataValue ipAuthentication
  RefdataValue shibbolethAuthentication
  RefdataValue passwordAuthenitcation

  static hasMany = [roles: RefdataValue]
  
  static hasByCombo = [
    provider : Org
  ]
  
  private static refdataDefaults = [
    "authentication"  : "Unknown",
    "roles"      : ["Host"]
  ]
  
  static manyByCombo = [
    hostedTipps : TitleInstancePackagePlatform,
    linkedTipps : TitleInstancePackagePlatform,
    curatoryGroups  : CuratoryGroup
  ]

  static mapping = {
    includes KBComponent.mapping
    primaryUrl column:'plat_primary_url',  index:'platform_primary_url_idx'
    authentication column:'plat_authentication_fk_rv'
    software column:'plat_sw_fk_rv'
    service column:'plat_svc_fk_rv'
    ipAuthentication column:'plat_auth_by_ip_fk_rv'
    shibbolethAuthentication column:'plat_auth_by_shib_fk_rv'
    passwordAuthenitcation column:'plat_auth_by_pass_fk_rv'
  }

  static constraints = {
    primaryUrl    (nullable:true, blank:false)
    authentication  (nullable:true, blank:false)
    software  (nullable:true, blank:false)
    service  (nullable:true, blank:false)
    ipAuthentication  (nullable:true, blank:false)
    shibbolethAuthentication  (nullable:true, blank:false)
    passwordAuthenitcation  (nullable:true, blank:false)
  }

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
        
        addCoreGOKbXmlFields(builder, attr)

        builder.'primaryUrl' (primaryUrl)
        builder.'authentication' (authentication?.value)
        builder.'software' (software?.value)
        builder.'service' (service?.value)
        
        builder.'authentication' (authentication?.value)
        if (ipAuthentication) builder.'ipAuthentication' (ipAuthentication.value)
        if (shibbolethAuthentication) builder.'shibbolethAuthentication' (shibbolethAuthentication.value)
        if (passwordAuthenitcation) builder.'passwordAuthenitcation' (passwordAuthenitcation.value)
        
        builder.'provider' (provider?.name)
        if ( roles ) {
          builder.'roles' {
            roles.each { role ->
              builder.'role' (role.value)
            }
          }
        }
        
        builder.curatoryGroups {
          curatoryGroups.each { cg ->
            builder.group {
              builder.name(cg.name)
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

  /**
   *  {
   *    name:'name',
   *    platformUrl:'platformUrl',
   *  }
   */
  @Transient
  public static boolean validateDTO(platformDTO) {
    def result = true;
    result &= platformDTO != null
    result &= platformDTO.name != null
    result &= platformDTO.name.trim().length() > 0

    if ( !result ) {
      log.error("platform failed validation ${platformDTO}");
    }

    result;
  }

  @Transient
  public static Platform upsertDTO(platformDTO) {
    // Ideally this should be done on platformUrl, but we fall back to name here
    def result = Platform.findByName(platformDTO.name) ?: new Platform(name:platformDTO.name).save(flush:true,failOnError:true)
    result;
  }

}
