package org.gokb.cred

import javax.persistence.Transient
import java.text.SimpleDateFormat
import com.k_int.ClassUtils
import groovy.util.logging.*

@Slf4j
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
    "gokb:TIPP:${title?.id}:${pkg?.id}"
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
    url (nullable:true, blank:true)
  }

  def availableActions() {
    [ [code:'method::retire', label:'Retire'],
      [code:'tipp::retire', label:'Retire (with Date)'],
      [code:'method::deleteSoft', label:'Delete', perm:'delete'],
      [code:'method::setExpected', label:'Mark Expected'],
      [code:'method::setActive', label:'Set Current']
    ]
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

  @Override
  public String getNiceName() {
	return "TIPP";
  }

  def afterUpdate() {
  }

  /**
   * Create a new TIPP being mindful of the need to create TIPLs
   */
  public static tiplAwareCreate(tipp_fields = [:]) {

//     def result = new TitleInstancePackagePlatform(tipp_fields)
//     result.title = tipp_fields.title
//     result.hostPlatform = tipp_fields.hostPlatform
//     result.pkg = tipp_fields.pkg
    
    def result = new TitleInstancePackagePlatform().save(failOnError: true)

    if ( result ) {
      def combo_status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)

      def pkg_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Tipps')
      def pkg_combo = new Combo(toComponent:result, fromComponent:tipp_fields.pkg, type:pkg_combo_type, status:combo_status_active).save(flush:true, failOnError:true);

      def plt_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTipps')
      def plt_combo = new Combo(toComponent:result, fromComponent:tipp_fields.hostPlatform, type:plt_combo_type, status:combo_status_active).save(flush:true, failOnError:true);

      def ti_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipps')
      def ti_combo = new Combo(toComponent:result, fromComponent:tipp_fields.title, type:ti_combo_type, status:combo_status_active).save(flush:true, failOnError:true);

      def tipl = TitleInstancePlatform.ensure(tipp_fields.title, tipp_fields.hostPlatform, tipp_fields.url);
    }
    else {
      log.error("TIPP creation failed!")
    }

//     // See if there is a TIPL
//     TitleInstancePlatform.ensure(tipp_fields.title, tipp_fields.hostPlatform, tipp_fields.url);

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
  static TitleInstancePackagePlatform upsertDTO(tipp_dto, def user = null) {
    def result = null
    log.debug("upsertDTO(${tipp_dto})");
    def pkg = Package.get(tipp_dto.package?.internalId)
    def plt = Platform.get(tipp_dto.platform?.internalId)
    def ti = TitleInstance.get(tipp_dto.title?.internalId)
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')
    def status_retired = RefdataCategory.lookupOrCreate('KBComponent.Status','Retired')
    def trimmed_url = tipp_dto.url ? tipp_dto.url.trim() : null

    if ( pkg && plt && ti ) {
      log.debug("See if we already have a tipp");
      def tipps = TitleInstancePackagePlatform.executeQuery('select tipp from TitleInstancePackagePlatform as tipp, Combo as pkg_combo, Combo as title_combo, Combo as platform_combo  '+
                                           'where pkg_combo.toComponent=tipp and pkg_combo.fromComponent=?'+
                                           'and platform_combo.toComponent=tipp and platform_combo.fromComponent = ?'+
                                           'and title_combo.toComponent=tipp and title_combo.fromComponent = ?',
                                          [pkg,plt,ti])
      def tipp = null;
      switch ( tipps.size() ) {
        case 1:
          log.debug("found");

          if( trimmed_url && trimmed_url.size() > 0 ) {
            if( !tipps[0].url || tipps[0].url == trimmed_url ){
              tipp = tipps[0]
            }
            else{
              log.debug("matched tipp has a different url..")
            }
          }
          else {
            tipp = tipps[0]
          } 
          break;
        case 0:
          log.debug("not found");
          
          break;
        default:
          if ( trimmed_url && trimmed_url.size() > 0 ) {
            tipps = tipps.findAll { !it.url || it.url == trimmed_url };
            log.debug("found ${tipps.size()} tipps for URL ${trimmed_url}")
          }
        
          def cur_tipps = tipps.findAll { it.status == status_current };
          def ret_tipps = tipps.findAll { it.status == status_retired };
          
          if ( cur_tipps.size() > 0 ){
            tipp = cur_tipps[0]
            
            log.warn("found ${cur_tipps.size()} current TIPPs!")
          }
          else if ( ret_tipps.size() > 0 ) {
            tipp = ret_tipps[0]
            
            log.warn("found ${ret_tipps.size()} retired TIPPs!")
          }
          else {
            log.debug("None of the matched TIPPs are 'Current' or 'Retired'!")
          }
          break;
      }

      if ( !tipp ) {
        log.debug("Creating new TIPP..")
        tipp = tiplAwareCreate(['pkg': pkg, 'title': ti, 'hostPlatform': plt, 'url': trimmed_url]).save(failOnError: true)
        // Hibernate problem

        if (!tipp){
          log.error("TIPP creation failed!")
        }
      }
      else {
        TitleInstancePlatform.ensure(ti, plt, trimmed_url)
      }

      if ( tipp ) {
        def changed = false
        if (plt.status != status_current) {
          log.warn("TIPP platform is marked as ${plt.status?.value}!")
          ReviewRequest.raise(
            tipp,
            "The existing platform matched for this TIPP (${plt}) is marked as ${plt.status?.value}! Please review the URL/Platform for validity.",
            "Platform not marked as current.",
            user
          )
        }
        if (tipp_dto.status && tipp_dto.status == "Retired") {
          tipp.retire()
        }
        else if ( tipp.isRetired() ) {
          tipp.status = status_current

          ReviewRequest.raise(
            tipp,
            "This TIPP was previously marked as Retired, but has now been set back to Current again.",
            "Retired TIPP reenabled.",
            user
          )
          
          if ( tipp.accessEndDate ) {
            tipp.accessEndDate = null
          }

          changed = true
        }else if( tipp.isDeleted() ) {
          ReviewRequest.raise(
            tipp,
            "Matched TIPP was marked as Deleted.",
            "Check TIPP Status.",
            user
          )
        }
        
        if ( tipp_dto.paymentType && tipp_dto.paymentType.length() > 0 ) {

          def payment_statement = null

          if( tipp_dto.paymentType == 'P' ) {
            payment_statement = 'Paid'
          }
          else if ( tipp_dto.paymentType == 'F' ) {
            payment_statement = 'OA'
          }
          else {
            payment_statement = tipp_dto.paymentType
          }
          
          def payment_ref = RefdataCategory.lookup("TitleInstancePackagePlatform.PaymentType", payment_statement)
          
          if (payment_ref) tipp.paymentType = payment_ref
        }

        changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'url', trimmed_url)
        changed |= com.k_int.ClassUtils.setDateIfPresent(tipp_dto.accessStartDate,tipp,'accessStartDate')
        changed |= com.k_int.ClassUtils.setDateIfPresent(tipp_dto.accessEndDate,tipp,'accessEndDate')

        tipp_dto.coverage.each { c ->
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startVolume', c.startVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startIssue', c.startIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endVolume', c.endVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endIssue', c.endIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'embargo', c.embargo)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'coverageNote', c.coverageNote)
          changed |= com.k_int.ClassUtils.setDateIfPresent(c.startDate,tipp,'startDate')
          changed |= com.k_int.ClassUtils.setDateIfPresent(c.endDate,tipp,'endDate')

          def sdfs = [
              "yyyy-MM-dd' 'HH:mm:ss.SSS",
              "yyyy-MM-dd'T'HH:mm:ss'Z'"
          ]

          def parsedStart = null
          def parsedEnd = null

          if ( c.startDate?.trim().size() > 0 ) {

            sdfs.each { s ->
              try {
                SimpleDateFormat sdf = new SimpleDateFormat(s)

                parsedStart = sdf.parse(c.startDate)
              }
              catch (Exception e) {
              }
            }
          }

          if ( c.endDate?.trim().size() > 0 ) {

            sdfs.each { s ->
              try {
                SimpleDateFormat sdf = new SimpleDateFormat(s)

                parsedEnd = sdf.parse(c.endDate)
              }
              catch (Exception e) {
              }
            }
          }

          if (RefdataCategory.getOID('TitleInstancePackagePlatform.CoverageDepth', c.coverageDepth.capitalize())) {
            changed |= com.k_int.ClassUtils.setRefdataIfPresent(c.coverageDepth.capitalize(), tipp.id, 'coverageDepth', 'TitleInstancePackagePlatform.CoverageDepth')
          }

          def cs_match = false

          tipp.coverageStatements?.each { tcs ->

            if ( !cs_match && (
                (tcs.startVolume && tcs.startVolume == c.startVolume) ||
                (tcs.startDate && tcs.startDate == parsedStart) ||
                (!cs_match && !tcs.startVolume && !tcs.startDate && !tcs.endVolume && !tcs.endDate))
            ) {
                changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startIssue', c.startIssue)
                changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startVolume', c.startVolume)
                changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endVolume', c.endVolume)
                changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endIssue', c.endIssue)
                changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'embargo', c.embargo)
                changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'coverageNote', c.coverageNote)
                changed |= com.k_int.ClassUtils.setDateIfPresent(c.startDate,tcs,'startDate')
                changed |= com.k_int.ClassUtils.setDateIfPresent(c.endDate,tcs,'endDate')

                cs_match = true
            }
            else if (cs_match) {
              log.debug("Matched new coverage ${c} on multiple existing coverages!")
            }
          }

          if (!cs_match) {
            tipp.addToCoverageStatements('startVolume': c.startVolume, 'startIssue':c.startIssue, 'endVolume': c.endVolume, 'endIssue': c.endIssue, 'embargo':c.embargo, 'coverageNote': c.coverageNote, 'startDate': parsedStart, 'endDate': parsedEnd)
          }
          // refdata setStringIfDifferent(tipp, 'coverageDepth', c.coverageDepth)
        }
//         tipp.save(flush:true, failOnError:true);
      }
      result = tipp;
    }

    result;
  }

}
