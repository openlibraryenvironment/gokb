package org.gokb.cred

import javax.persistence.Transient
import groovy.util.logging.*
import org.gokb.GOKbTextUtils

@Log4j
class Platform extends KBComponent {

  String primaryUrl
  RefdataValue authentication
  RefdataValue software
  RefdataValue service
  RefdataValue ipAuthentication
  RefdataValue shibbolethAuthentication
  RefdataValue passwordAuthentication

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
    passwordAuthentication column:'plat_auth_by_pass_fk_rv'
  }

  static constraints = {
    primaryUrl    (nullable:true, blank:false)
    authentication  (nullable:true, blank:false)
    software  (nullable:true, blank:false)
    service  (nullable:true, blank:false)
    ipAuthentication  (nullable:true, blank:false)
    shibbolethAuthentication  (nullable:true, blank:false)
    passwordAuthentication  (nullable:true, blank:false)
  }

  @Transient
  static def oaiConfig = [
    id:'platforms',
    textDescription:'Platform repository for GOKb',
    query:" from Platform as o ",
    statusFilter:"where o.status.value != 'Deleted'"
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
        
        if (ipAuthentication) builder.'ipAuthentication' (ipAuthentication.value)
        if (shibbolethAuthentication) builder.'shibbolethAuthentication' (shibbolethAuthentication.value)
        if (passwordAuthentication) builder.'passwordAuthentication' (passwordAuthentication.value)
        
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
        if( !params.filter1 || t.status.value == params.filter1 ){
          result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
        }
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
  public static Platform upsertDTO(platformDTO, def user = null, def project = null) {
    // Ideally this should be done on platformUrl, but we fall back to name here
    
    def result = false;
    Boolean skip = false;
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')
    def name_candidates = []
    def url_candidates = [];
    Boolean viable_url = false;
    
    if(platformDTO.name.startsWith("http")){
      try {
        log.debug("checking if platform name is an URL..")
        
        def url_as_name = new URL(platformDTO.name)
        
        if(url_as_name.getProtocol()){ 
          if(!platformDTO.primaryUrl || platformDTO.primaryUrl.trim.size() == 0){
            log.debug("identified URL as platform name")
            platformDTO.primaryUrl = platformDTO.name
          }
          
          platformDTO.name = url_as_name.getHost()
          
          if (platformDTO.name.startsWith("www.")) {
            platformDTO.name = platformDTO.name.substring(4)
          }
          
          log.debug("New platform name is ${platformDTO.name}.")
        }
      } catch( MalformedURLException ){
        log.debug("Platform name is no valid URL")
      }
      name_candidates = Platform.executeQuery("from Platform where name = ?", [platformDTO.name]);
    }
    
    if(platformDTO.primaryUrl && platformDTO.primaryUrl.trim().size() > 0){
      try {
        def inc_url = new URL(platformDTO.primaryUrl);
        def other_candidates = []
        
        if(inc_url){
          viable_url = true;
          String urlHost = inc_url.getHost();
          
          if(urlHost.startsWith("www.")){
            urlHost = urlHost.substring(4)
            def url_no_schema = (platformDTO.primaryUrl - ~/www\./)
            other_candidates = Platform.executeQuery("select distinct pl from Platform as pl where ( pl.primaryUrl = ? or pl.name = ? ) ", [url_no_schema, urlHost]);
          }
          
          url_candidates = Platform.executeQuery("select distinct pl from Platform as pl where ( pl.primaryUrl = ? or pl.name = ? ) ", [platformDTO.primaryUrl, urlHost]);
          
          
          // Use platform found without 'www.' in URL
          
          if ( url_candidates.size() == 0 && other_candidates.size() > 0) {
            url_candidates = other_candidates
          }
          
          // Cross-check for alternative schema
          
          if( url_candidates.size() == 0 && inc_url.getProtocol() == "http" ){
            URL alt_scheme = new URL("https", inc_url.getHost(), inc_url.getPort(), inc_url.getFile(), inc_url.getRef());
            log.debug("Also trying URL: ${alt_scheme.toString()}")
            url_candidates = Platform.executeQuery("select distinct pl from Platform as pl where pl.primaryUrl = ? ", [alt_scheme.toString()]);
          }
          else if( url_candidates.size() == 0 && inc_url.getProtocol() == "https" ){
            URL alt_scheme = new URL("http", inc_url.getHost(), inc_url.getPort(), inc_url.getFile(), inc_url.getRef());
            log.debug("Also trying URL: ${alt_scheme.toString()}")
            url_candidates = Platform.executeQuery("select distinct pl from Platform as pl where pl.primaryUrl = ? ", [alt_scheme.toString()]);
          }
        }
      } catch( MalformedURLException ex ) {
        log.error("URL of ingest Platform ${platformDTO} is broken!")
      }
    }
    
    if(name_candidates.size() == 0){
      log.debug("No platforms matched by name!")

      def variant_normname = GOKbTextUtils.normaliseString(platformDTO.name)

      def varname_candidates = Platform.executeQuery("select distinct pl from Platform as pl join pl.variantNames as v where v.normVariantName = ? and pl.status = ? ",[variant_normname, status_current])

      if(varname_candidates.size() == 1){
        log.debug("Platform matched by variant name!")
        result = varname_candidates[0]
      }

    }
    else if(name_candidates.size() == 1 && name_candidates[0].status == status_current){
      log.debug("Platform ${platformDTO.name} matched by name!")
      result = name_candidates[0];
    }
    else{
      log.warn("Could not match a specific current platform for ${platformDTO.name}!");
    }
    
    if(!result && viable_url){
      log.debug("Trying to match platform by primary URL..")
      
      if(url_candidates.size() == 0){
        log.debug("Could not match an existing platform!")
      }
      else if(url_candidates.size() == 1){
        log.debug("Matched existing platform by URL!")
        result = url_candidates[0];
      }
      else if(url_candidates.size() > 1) {
        log.warn("Matched multiple platforms by URL!")

        def current_platforms = url_candidates.findAll { it.status == status_current }

        if (current_platforms.size() == 1){
          result = current_platforms[0]
          
          if ( !result.primaryUrl ) {
            result.primaryUrl = platformDTO.primaryUrl
            result.save(flush:true,failOnError:true)
          }
        }
        else if ( current_platforms.size() == 0 ) {
          log.error("Matched only non-current platforms by URL!")
          result = url_candidates[0]
        }
        else{
        
          // Picking randomly from multiple results is bad, but right now a result is always expected. Maybe this should be skipped...
          // skip = true
        
          log.error("Multiple matched current platforms: ${current_platforms}")
          result = current_platforms[0]
        }
      }
    }
    
    if(!result && !skip){
      log.debug("Creating new platform for: ${platformDTO}")
      result = new Platform(name:platformDTO.name, normname: KBComponent.generateNormname(platformDTO.name), primaryUrl: (viable_url ? platformDTO.primaryUrl : null )).save(flush:true,failOnError:true)
      
      ReviewRequest.raise(
        result,
        "The platform ${result} did not exist and was newly created.",
        "New platform created",
        user, project
      )
    }
    
    result;
  }

}
