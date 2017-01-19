package org.gokb.cred

import javax.persistence.Transient
import groovy.util.logging.Log4j
import com.k_int.ClassUtils


import org.gokb.refine.*

@Log4j
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
  User userListVerifier
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
    curatoryGroups: CuratoryGroup
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
    includes KBComponent.mapping
    listStatus column:'pkg_list_status_rv_fk'
    lastProject column:'pkg_refine_project_fk'
    scope column:'pkg_scope_rv_fk'
    breakable column:'pkg_breakable_rv_fk'
    consistent column:'pkg_consistent_rv_fk'
    fixed column:'pkg_fixed_rv_fk'
    paymentType column:'pkg_payment_type_rv_fk'
    global column:'pkg_global_rv_fk'
    listVerifier column:'pkg_list_verifier'
    userListVerifier column:'pkg_list_verifier_user_fk'
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
  
  private static OAI_PKG_CONTENTS_QRY = '''
select tipp.id, 
       title.name, 
       title.id, 
       plat.name, 
       plat.id, 
       tipp.startDate, 
       tipp.startVolume, 
       tipp.startIssue, 
       tipp.endDate, 
       tipp.endVolume, 
       tipp.endIssue, 
       tipp.coverageDepth, 
       tipp.coverageNote, 
       tipp.url, 
       tipp.status, 
       tipp.accessStartDate, 
       tipp.accessEndDate, 
       tipp.format, 
       tipp.embargo, 
       plat.primaryUrl 
    from TitleInstancePackagePlatform as tipp, 
         Combo as hostPlatformCombo, 
         Combo as titleCombo,  
         Combo as pkgCombo,
         Platform as plat,
         TitleInstance as title
    where pkgCombo.toComponent=tipp 
      and pkgCombo.fromComponent= ?  
      and pkgCombo.type= ?  
      and hostPlatformCombo.toComponent=tipp 
      and hostPlatformCombo.type = ?  
      and hostPlatformCombo.fromComponent = plat
      and titleCombo.toComponent=tipp 
      and titleCombo.type = ?
      and titleCombo.fromComponent=title
      and tipp.status != ?  
    order by tipp.id''';

  public void deleteSoft (context) {
    // Call the delete method on the superClass.
    super.deleteSoft(context)
    
    // Delete the tipps too as a TIPP should not exist without the associated,
    // package.
    def tipps = getTipps()
     
    tipps.each { def tipp ->
      
      // Ensure they aren't the javassist type classes here, as we will get a NoSuchMethod exception
      // thrown below if we don't.
      tipp = KBComponent.deproxy(tipp)
      
      tipp.deleteSoft()
    }
  }
  

  public void retire (context) {
    log.debug("package::retire");
    // Call the delete method on the superClass.
    log.debug("Updating package status to retired");
    this.status = RefdataCategory.lookupOrCreate('KBComponent.Status','Retired');
    this.save();

    // Delete the tipps too as a TIPP should not exist without the associated,
    // package.
    log.debug("Retiring tipps");
    def tipps = getTipps()

    tipps.each { def t ->
      log.debug("deroxy ${t} ${t.class.name}");
      
      // SO: There are 2 deproxy methods. One in the static context that takes in an argument and one,
      // against an instance which attempts to deproxy this component. Calling deproxy(t) here will invoke the method
      // against the current package. this.deproxy(t).
      // So Package.deproxy(t) or t.deproxy() should work...
      def tipp = Package.deproxy(t)
      log.debug("Retiring tipp ${tipp.id}");
      tipp.status = RefdataCategory.lookupOrCreate('KBComponent.Status','Retired');
      tipp.save()
    }
  }


  @Transient
  def availableActions() {
    [
      [code:'method::deleteSoft', label:'Delete (with associated TIPPs)'],
      [code:'method::retire', label:'Retire Package (with associated TIPPs)'],
      [code:'exportPackage', label:'TSV Export'],
      [code:'kbartExport', label:'KBART Export'],
      // [code:'method::registerWebhook', label:'Register Web Hook']
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

    log.debug("Running package contents qry : ${OAI_PKG_CONTENTS_QRY}");

    // Get the tipps manually rather than iterating over the collection - For better management
    def tipps = TitleInstancePackagePlatform.executeQuery(OAI_PKG_CONTENTS_QRY, [this, refdata_package_tipps, refdata_hosted_tipps, refdata_ti_tipps,refdata_deleted],[readOnly: true]); // , fetchSize:250]);

    log.debug("Query complete...");
    
    builder.'gokb' (attr) {
      builder.'package' (['id':(id)]) {
        addCoreGOKbXmlFields(builder, attr)
        
        'scope' ( scope?.value )
        'listStatus' ( listStatus?.value )
        'breakable' ( breakable?.value )
        'consistent' ( consistent?.value )
        'fixed' ( fixed?.value )
        'paymentType' ( paymentType?.value )
        'global' ( global?.value )
        'nominalPlatform' ( nominalPlatform?.name )
        'nominalProvider' ( nominalPlatform?.provider?.name )
        'listVerifier' ( listVerifier )
        'userListVerifier' ( userListVerifier?.username )
        'listVerifiedDate' ( listVerifiedDate ? sdf.format(listVerifiedDate) : null )

        builder.curatoryGroups {
          curatoryGroups.each { cg ->
            builder.group {
              builder.name(cg.name)
            }
          }
        }
        'dateCreated' (sdf.format(dateCreated))
        'TIPPs'(count:tipps?.size()) {
          tipps.each { tipp ->
            builder.'TIPP' (['id':tipp[0]]) {
              builder.'status' (tipp[14]?.value)
              builder.'medium' (tipp[17]?.value)
              builder.'title' (['id':tipp[2]]) {
                builder.'name' (tipp[1]?.trim())
                builder.'identifiers' {
                  getTitleIds(tipp[2]).each { tid ->
                    builder.'identifier'('namespace':tid[0], 'value':tid[1], 'datatype':tid[2])
                  }
                }
              }
              'platform'([id:tipp[4]]) {
                'primaryUrl' (tipp[19]?.trim())
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
                coverageNote:tipp[12],
                embargo: tipp[18] )
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
    def result = Identifier.executeQuery("select i.namespace.value, i.value, datatype.value from Identifier as i, Combo as c left join i.namespace.datatype as datatype where c.fromComponent.id = ? and c.type = ? and c.toComponent = i",[title_id,refdata_ids],[readOnly:true]);
    result
  }

  @Transient
  public getRecentActivity(n) {
    def result = [];

    if ( this.id ) {

      // select tipp, accessStartDate, 'Added' from tipps UNION select tipp, accessEndDate, 'Removed' order by date

      def additions = TitleInstancePackagePlatform.executeQuery('select tipp, tipp.accessStartDate, \'Added\' ' +
                       'from TitleInstancePackagePlatform as tipp, Combo as c '+
                       'where c.fromComponent=? and c.toComponent=tipp order by tipp.accessStartDate DESC',
                      [this], [max:n]);
      def deletions = TitleInstancePackagePlatform.executeQuery('select tipp, tipp.accessEndDate, \'Removed\' ' +
                       'from TitleInstancePackagePlatform as tipp, Combo as c '+
                       'where c.fromComponent=? and c.toComponent=tipp and tipp.accessEndDate is not null order by tipp.accessEndDate DESC',
                       [this], [max:n]);

      result.addAll(additions)
      result.addAll(deletions)
      result.sort {it[1]}
      result = result.reverse();
    }

    return result;
  }

  /**
   * Definitive rules for a valid package header
   */
  @Transient
  public static boolean validateDTO(packageHeaderDTO) {
    def result = true;
    result &= ( packageHeaderDTO != null )
    result &= ( packageHeaderDTO.name != null )
    result &= ( packageHeaderDTO.name.trim().length() > 0 )
    result;
  }

  /**
   * Definitive rules for taking a package header DTO and inserting or updating an existing package based on package name
   *
   * listStatus:'Checked',
   * status:'Current',
   * breakable:'Unknown',
   * consistent:'Unknown',
   * fixed:'Unknown',
   * paymentType:'Unknown',
   * global:'Global',
   * nominalPlatform:'Content Select'
   * nominalProvider:'Preselect.media'
   * listVerifier:'',
   * userListVerifier:'benjamin_ahlborn'
   * listVerifierDate:'2015-06-19T00:00:00Z'
   * source:[
   *   url:'http://www.zeitschriftendatenbank.de'
   *   defaultAccessURL:''
   *   explanationAtSource:''
   *   contextualNotes:''
   *   frequency:''
   *   ruleset:''
   *   defaultSupplyMethod:'Other'
   *   defaultDataFormat:'Other'
   *   responsibleParty:''
   * ]
   * name:'Campus: All Journals'
   * curatoryGroups:[
   *   curatoryGroup:"SuUB Bremen"
   * ]
   * variantNames : [
   *   variantName:"Campus: All Journals"
   * ]
   */
  @Transient
  public static Package upsertDTO(packageHeaderDTO) {
    def result = null
    log.info("Upsert package with header ${packageHeaderDTO}");
    result = Package.findByName(packageHeaderDTO.name) ?: new Package(name:packageHeaderDTO.name).save(flush:true, failOnError:true);

    boolean changed = false;

    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.listStatus, result, 'listStatus')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.status, result, 'status')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.editStatus, result, 'editStatus')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.scope, result, 'scope')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.breakable, result, 'breakable')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.consistent, result, 'consistent')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.fixed, result, 'fixed')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.paymentType, result, 'paymentType')
    changed |= ClassUtils.setRefdataIfPresent(packageHeaderDTO.global, result, 'global')
    changed |= ClassUtils.setStringIfDifferent(result, 'listVerifier', packageHeaderDTO.listVerifier?.toString())
    // User userListVerifier
    changed |= ClassUtils.setDateIfPresent(packageHeaderDTO.listVerifiedDate, result, 'listVerifiedDate');

    if ( packageHeaderDTO.userListVerifier ) {
      def looked_up_user = User.findByUsername(packageHeaderDTO.userListVerifier)
      if ( looked_up_user && ( ( result.userListVerifier == null ) || ( result.userListVerifier?.id != looked_up_user?.id )  ) ) {
        result.userListVerifier = looked_up_user
        changed = true
      }
      else {
        log.warn("Unable to find username for list verifier ${packageHeaderDTO.userListVerifier}");
      }
    }

    if ( packageHeaderDTO.nominalPlatform ) {
      def np = Platform.findByName(packageHeaderDTO.nominalPlatform)
      if ( np ) {
        result.nominalPlatform = np;
        changed = true
      }
      else {
        log.warn("Unable to locate nominal platform ${packageHeaderDTO.nominalPlatform}");
      }
    }

    if ( packageHeaderDTO.nominalProvider ) {
      def prov = Org.findByName(packageHeaderDTO.nominalProvider)
      if ( prov ) {
        result.provider = prov;
        changed = true
      }
      else {
        log.warn("Unable to locate nominal provider ${packageHeaderDTO.nominalProvider}");
      }
    }

    if ( packageHeaderDTO.source?.url ) {
      def src = Source.findByUrl(packageHeaderDTO.source.url)
      if ( src ) {
        result.source = src
        changed = true
      }
    }

    packageHeaderDTO.variantNames?.each {
      result.ensureVariantName(it)
      changed=true
    }

    packageHeaderDTO.curatoryGroups?.each {

      String normname = CuratoryGroup.generateNormname(it)
      
      def cg = CuratoryGroup.findByNormname(normname)

      if ( cg ) {
        if ( result.curatoryGroups.find {it.name == cg.name } ) {
        }
        else {
          result.curatoryGroups.add(cg)
          changed=true;
        }
      }
    }
    
    result.save(flush:true, failOnError:true);


    result
  }
}
