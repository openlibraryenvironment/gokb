package org.gokb.cred

import javax.persistence.Transient
import groovy.util.logging.*

@Log4j
class TitleInstancePackagePlatform extends KBComponent {

  Date startDate
  String startVolume
  String startIssue
  String embargo
  RefdataValue coverageDepth
  String coverageNote
  RefdataValue format
  RefdataValue delayedOA
  String delayedOAEmbargo
  RefdataValue hybridOA
  String hybridOAUrl
  RefdataValue primary
  RefdataValue paymentType
  Date endDate
  String endVolume
  String endIssue
  String url
  Date accessStartDate
  Date accessEndDate

  private static refdataDefaults = [
    "format"        : "Electronic",
    "delayedOA"     : "Unknown",
    "hybridOA"      : "Unknown",
    "primary"       : "No",
    "paymentType"   : "Paid",
    "coverageDepth" : "Fulltext"
  ]
  
  static touchOnUpdate = [
    "pkg"
  ]

  static hasByCombo = [
    pkg                 : Package,
    hostPlatform        : Platform,
    title               : TitleInstance,
    derivedFrom         : TitleInstancePackagePlatform,
    masterTipp          : TitleInstancePackagePlatform,
  ]

  static mappedByCombo = [
    pkg                 : 'tipps',
    hostPlatform        : 'hostedTipps',
    additionalPlatforms : 'linkedTipps',
    title               : 'tipps',
    derivatives         : 'derivedFrom'
  ]

  static manyByCombo = [
    derivatives           : TitleInstancePackagePlatform,
    additionalPlatforms   : Platform,
  ]

  static hasMany = [
    coverageStatements : TIPPCoverageStatement
  ]

  static mappedBy = [
    coverageStatements : 'owner'
  ]

  public getPersistentId() {
    "gokb:TIPP:${title?.id}:${hostPlatform?.id}:${pkg?.id}"
  }
  
  public static isTypeCreatable(boolean defaultValue = false) {
    return defaultValue;
  }

  static mapping = {
    includes KBComponent.mapping
    startDate column:'tipp_start_date'
    startVolume column:'tipp_start_volume'
    startIssue column:'tipp_start_issue'
    endDate column:'tipp_end_date'
    endVolume column:'tipp_end_volume'
    endIssue column:'tipp_end_issue'
    embargo column:'tipp_embargo'
    coverageDepth column:'tipp_coverage_depth'
    coverageNote column:'tipp_coverage_note',type: 'text'
    format column:'tipp_format_rv_fk'
    delayedOA column:'tipp_delayed_oa'
    delayedOAEmbargo column:'tipp_delayed_oa_embargo'
    hybridOA column:'tipp_hybrid_oa'
    hybridOAUrl column:'tipp_hybrid_oa_url'
    primary column:'tipp_primary'
    paymentType column:'tipp_payment_type'
    accessStartDate column: 'tipp_access_start_date'
    accessEndDate column: 'tipp_access_end_date'
  }

  static constraints = {
    startDate (nullable:true, blank:true)
    startVolume (nullable:true, blank:true)
    startIssue (nullable:true, blank:true)
    endDate (nullable:true, blank:true)
    endVolume (nullable:true, blank:true)
    endIssue (nullable:true, blank:true)
    embargo (nullable:true, blank:true)
    coverageDepth (nullable:true, blank:true)
    coverageNote (nullable:true, blank:true)
    format (nullable:true, blank:true)
    delayedOA (nullable:true, blank:true)
    delayedOAEmbargo (nullable:true, blank:true)
    hybridOA (nullable:true, blank:true)
    hybridOAUrl (nullable:true, blank:true)
    primary (nullable:true, blank:true)
    paymentType (nullable:true, blank:true)
    accessStartDate (nullable:true, blank:false)
    accessEndDate (nullable:true, blank:false)
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

  def availableActions() {
    [ [code:'method::deleteSoft', label:'Delete'],
      [code:'method::retire', label:'Retire']
    ]
  }

  public String getNiceName() {
	return "TIPP";
  }

  /**
   * Create a new TIPP being mindful of the need to create TIPLs
   */
  public static tiplAwareCreate(tipp_fields = [:]) {

    def result = new TitleInstancePackagePlatform(tipp_fields)
    result.title = tipp_fields.title
    result.hostPlatform = tipp_fields.hostPlatform
    result.pkg = tipp_fields.pkg
    
    // See if there is a TIPL
    TitleInstancePlatform.ensure(tipp_fields.title, tipp_fields.hostPlatform, tipp_fields.url);

    result
  }

  @Override
  @Transient
  public String getDisplayName() {
    return name ?: "${pkg?.name} / ${title?.name} / ${hostPlatform?.name}"
  }

  /**
   * Please see https://github.com/k-int/gokb-phase1/wiki/tipp_dto
   */ 
  @Transient
  public static boolean validateDTO(tipp_dto) {
    def result = true;
    result &= tipp_dto.package?.internalId != null
    result &= tipp_dto.platform?.internalId != null
    result &= tipp_dto.title?.internalId != null

    if ( !result ) 
      log.warn("Tipp failed validation: ${tipp_dto} - pkg:${tipp_dto.package?.internalId} plat:${tipp_dto.platform?.internalId} ti:${tipp_dto.title?.internalId}");

    result;
  }

  /**
   * Please see https://github.com/k-int/gokb-phase1/wiki/tipp_dto
   */ 
  @Transient
  public static TitleInstancePackagePlatform upsertDTO(tipp_dto) {
    def result = null;
    log.debug("upsertDTO(${tipp_dto})");
    def pkg = Package.get(tipp_dto.package?.internalId)
    def plt = Platform.get(tipp_dto.platform?.internalId)
    def ti = TitleInstance.get(tipp_dto.title?.internalId)
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')
    def status_retired = RefdataCategory.lookupOrCreate('KBComponent.Status','Retired')

    if ( pkg && plt && ti ) {
      log.debug("See if we already have a tipp");
      def tipps = TitleInstance.executeQuery('select tipp from TitleInstancePackagePlatform as tipp, Combo as pkg_combo, Combo as title_combo, Combo as platform_combo  '+
                                           'where pkg_combo.toComponent=tipp and pkg_combo.fromComponent=?'+
                                           'and platform_combo.toComponent=tipp and platform_combo.fromComponent = ?'+
                                           'and title_combo.toComponent=tipp and title_combo.fromComponent = ?',
                                          [pkg,plt,ti])
      def tipp = null;
      def matched = false;
      switch ( tipps.size() ) {
        case 1:
          log.debug("found");

          tipp = tipps[0]

          if( tipp_dto.url && tipp_dto.url.trim().size() > 0 ) {
            if( tipp.url == tipp_dto.url ){
              log.debug("matched tipp with identical URL..")
            }else if (!tipp.url) {
              log.debug("matched tipp without an URL..")
            }
            else {
              log.debug("matched tipp has a different URL..")
            }
          }

          break;
        case 0:
          log.debug("not found");
          
          break;
        default:
          if ( tipp_dto.url && tipp_dto.url.trim().size() > 0 ) {
            tipps = tipps.findAll { !it.url || it.url == tipp_dto.url };
            log.debug("found ${tipps.size()} tipps for URL ${tipp_dto.url}")
          }
        
          def cur_tipps = tipps.findAll { it.status == status_current };
          def ret_tipps = tipps.findAll { it.status == status_retired };
          
          if ( cur_tipps.size() > 0 ){
            tipp = cur_tipps[0]
            
            log.warn("found ${cur_tipps.size()} current TIPPs!")
          }
          else if ( ret_tipps.size() > 0 ) {
            tipp = ret_tipps[0]
            
            log.warn("only found ${ret_tipps.size()} retired TIPPs!")
          }
          else {
            log.debug("None of the matched TIPPs are 'Current' or 'Retired'!")
          }
            
          break;
      }

      if ( !tipp ) {
      
        log.debug("Creating new TIPP..")
        tipp=new TitleInstancePackagePlatform()
        tipp.pkg = pkg;
        tipp.title = ti;
        tipp.hostPlatform = plt;
      }
      else {
        matched = true
      }

      if ( tipp ) {
        tipp.save(flush:true,failOnError:true);
        def changed = false
        
        if ( plt.isRetired() ) {
          ReviewRequest.raise(
            tipp,
            "Please validate the URL and Platform.",
            "The existing platform matched for this TIPP (${plt}) is marked as ${plt.status?.value}! "
          )
        }
        
        if ( tipp_dto.status == "Current" && ( tipp.isDeleted() || tipp.isRetired() ) ) {
          tipp.status = status_current
          
          if ( !tipp.accessStartDate ) {
            tipp.accessStartDate = new Date()

            ReviewRequest.raise(
              tipp,
              "Please review TIPP access dates.",
              "The TIPP matched for a package update was previously marked as Retired!"
            )
          }
          changed = true
        }
        else if (tipp_dto.status == 'Retired') {
          if ( tipp.isCurrent() ){

            tipp.status = status_retired

            if (!tipp.accessEndDate && !tipp_dto.accessEndDate) {
              tipp.accessEndDate = new Date()
            }
            changed = true
          }
          else if ( tipp.isRetired() ) {
            if (!tipp.accessEndDate && !tipp_dto.accessEndDate) {
              ReviewRequest.raise(
                tipp,
                "Retired TIPP needs an Access End Date.",
                "TIPP ${tipp} was already marked as Retired but does not have a specified Access End Date."
              )
            }
          }
        }
        
        if ( tipp_dto.paymentType && tipp_dto.paymentType.length() > 0 ) {
          
          def payment_ref = RefdataCategory.getOID("TitleInstancePackagePlatform.PaymentType", tipp_dto.paymentType)
          
          if (payment_ref) {
            def rdv_id = payment_ref.split(':')[1]
            tipp.paymentType = RefdataValue.get(rdv_id)
            changed = true
          }
        }

        changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'url', tipp_dto.url)
        changed |= com.k_int.ClassUtils.setDateIfPresent(tipp_dto.accessStartDate,tipp,'accessStartDate')
        changed |= com.k_int.ClassUtils.setDateIfPresent(tipp_dto.accessEndDate,tipp,'accessStartDate')

        tipp_dto.coverage.each { c ->
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startVolume', c.startVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startIssue', c.startIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endVolume', c.endVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endIssue', c.endIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'embargo', c.embargo)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'coverageNote', c.coverageNote)
          changed |= com.k_int.ClassUtils.setDateIfPresent(c.startDate,tipp,'startDate')
          changed |= com.k_int.ClassUtils.setDateIfPresent(c.endDate,tipp,'endDate')
          // refdata setStringIfDifferent(tipp, 'coverageDepth', c.coverageDepth)
        }

        if ( changed ) {
//           ReviewRequest.raise(
//             tipp,
//             "TIPP details changed.",
//             "This TIPP was changed during a package update."
//           )
          log.debug("TIPP information changed!")
        }

        tipp.save(flush:true, failOnError:true);
      }
      result = tipp;
    }

    result;
  }

}
