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
  RefdataValue listStatus
  RefdataValue breakable
  RefdataValue consistent
  RefdataValue fixed
  RefdataValue paymentType
  RefdataValue global
  RefineProject lastProject
  String listVerifier
  Date listVerifiedDate
  
  private static refdataDefaults = [
    "scope"       : "Front File",
    "listStatus"  : "Checked",
    "breakable"   : "Unknown",
    "consistent"  : "Unknown",
    "fixed"       : "Unknown",
    "paymentType" : "Unknown",
    "global"      : "Global"
  ]
  
  static manyByCombo = [
    tipps         : TitleInstancePackagePlatform,
    children      : Package,
    territories      : Territory
  ]
  
  static hasByCombo = [
             parent : Package,
             broker : Org,
           provider : Org,
           licensor : Org,
             vendor : Org,
    nominalPlatform : Platform,
         'previous' : Package,
          successor : Package
  ]
  
  static mappedByCombo = [
     children : 'parent',
    successor : 'previous',
  ]

  static mapping = {
    listStatus column:'pkg_list_status_rv_fk'
    lastProject column:'pkg_refine_project_fk'
    scope column:'pkg_scope_rv_fk'
    breakable column:'pkg_breakable_rv_fk'
    consistent column:'pkg_consistent_rv_fk'
    fixed column:'pkg_fixed_rv_fk'
    paymentType column:'pkg_payment_type_rv_fk'
    global column:'pkg_global_rv_fk'
    listVerifier column:'pkg_list_verifier'
  }

  static constraints = {
    lastProject    (nullable:true, blank:false)
    scope       (nullable:true, blank:false)
    listStatus    (nullable:true, blank:false)
    breakable    (nullable:true, blank:false)
    consistent    (nullable:true, blank:false)
    fixed      (nullable:true, blank:false)
    paymentType    (nullable:true, blank:false)
    global      (nullable:true, blank:false)
    lastProject    (nullable:true, blank:false)
  }

  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = Package.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }
  
  public void deleteSoft (context) {
    // Call the delete method on the superClass.
    super.deleteSoft(context)
    
    // Delete the tipps too as a TIPP should not exist without the associated,
    // package.
    def tipps = getTipps()
     
    tipps.each { def tipp ->
      
      // Ensure they aren't the javassist type classes here, as we will get a NoSuchMethod exception
      // thrown below if we don't.
      tipp = deproxy(tipp)
      
      tipp.deleteSoft()
    }
  }
  
  @Transient
  def availableActions() {
    [
      [code:'method::deleteSoft', label:'Delete (with associated TIPPs)'],
      [code:'method::registerWebhook', label:'Register Web Hook']
    ]
  }

  @Transient
  def getWebHooks() {
    def result=[]

    result.hooks = WebHook.findAllByOid("org.gokb.cred.Package:${this.id}");

    result
  }

  @Transient
  static def oaiConfig = [
    id:'packages',
    textDescription:'Package repository for GOKb',
    query:" from Package as o where o.status.value != 'Deleted'",
    pageSize:3
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

    log.debug("toGoKBXml...");

    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    def identifier_prefix = "uri://gokb/${grailsApplication.config.sysid}/title/"

    def refdata_package_tipps = RefdataCategory.lookupOrCreate('Combo.Type','Package.Tipps');
    def refdata_hosted_tipps = RefdataCategory.lookupOrCreate('Combo.Type','Platform.HostedTipps');
    def refdata_ti_tipps = RefdataCategory.lookupOrCreate('Combo.Type','TitleInstance.Tipps');
    def refdata_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status','Deleted');

    // Get the tipps manually rather than iterating over the collection - For better management
    // def tipp_ids = TitleInstancePackagePlatform.executeQuery("select tipp.id from TitleInstancePackagePlatform as tipp where tipp.status.value != 'Deleted' and exists ( select ic from tipp.incomingCombos as ic where ic.fromComponent = ? ) order by tipp.id",this);
    def tipps = TitleInstancePackagePlatform.executeQuery("""select tipp.id, titleCombo.fromComponent.name, titleCombo.fromComponent.id, hostPlatformCombo.fromComponent.name, hostPlatformCombo.fromComponent.id, tipp.startDate, tipp.startVolume, tipp.startIssue, tipp.endDate, tipp.endVolume, tipp.endIssue, tipp.coverageDepth, tipp.coverageNote, tipp.url, tipp.status, tipp.accessStartDate, tipp.accessEndDate from TitleInstancePackagePlatform as tipp, Combo as hostPlatformCombo, Combo as titleCombo, Combo as pkgCombo
where pkgCombo.toComponent=tipp
  and pkgCombo.fromComponent= ?
  and pkgCombo.type= ?
  and hostPlatformCombo.toComponent=tipp 
  and hostPlatformCombo.type = ?
  and titleCombo.toComponent=tipp 
  and titleCombo.type = ?
  and tipp.status != ?
order by tipp.id""",[this, refdata_package_tipps, refdata_hosted_tipps, refdata_ti_tipps,refdata_deleted],[readOnly: true]); // , fetchSize:250]);

    log.debug("Query complete...");
    
    builder.'gokb' (attr) {
      builder.'package' (['id':(id)]) {
        'scope' ( scope?.value )
        'listStatus' ( listStatus?.value )
        'breakable' ( breakable?.value )
        'consistent' ( consistent?.value )
        'fixed' ( fixed?.value )
        'paymentType' ( paymentType?.value )
        'global' ( global?.value )
        'name' (name)
        'TIPPs'(count:tipps?.size()) {
          tipps.each { tipp ->
            builder.'TIPP' (['id':tipp[0]]) {
              builder.'status' (tipp[14]?.value)
              builder.'title' (['id':identifier_prefix+tipp[2]]) {
                builder.'name' (tipp[1]?.trim())
                builder.'identifiers' {
                  getTitleIds(tipp[2]).each { tid ->
                    builder.'identifier'('namespace':tid[0], 'value':tid[1])
                  }
                }
              }
              'platform'([id:tipp[4]]) {
                'name' (tipp[3]?.trim())
              }
              'access'(start:tipp[15]?sdf.format(tipp[15]):null,end:tipp[16]?sdf.format(tipp[16]):null)
              'coverage'(
                startDate:(tipp[5]?sdf.format(tipp[5]):null),
                startVolume:tipp[6],
                startIssue:tipp[7],
                endDate:(tipp[8]?sdf.format(tipp[8]):null),
                endVolume:tipp[9],
                endIssue:tipp[10],
                coverageDepth:tipp[11]?.value,
                coverageNote:tipp[12])
              if ( tipp[13] != null ) { 'url'(tipp[13]) }
            }
          }
        }
      }
    }

    log.debug("toGoKBXml complete...");
  }

  @Transient
  private static getTitleIds(Long title_id) {
    def refdata_ids = RefdataCategory.lookupOrCreate('Combo.Type','KBComponent.Ids');
    def result = Identifier.executeQuery("select i.namespace.value, i.value from Identifier as i, Combo as c where c.fromComponent.id = ? and c.type = ? and c.toComponent = i",[title_id,refdata_ids],[readOnly:true]);
    result
  }

}
