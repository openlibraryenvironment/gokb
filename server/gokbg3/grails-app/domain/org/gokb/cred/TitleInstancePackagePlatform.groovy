package org.gokb.cred

import javax.persistence.Transient
import com.k_int.ClassUtils
import org.gokb.GOKbTextUtils
import groovy.util.logging.*
import java.time.LocalDateTime
import java.time.ZoneId

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

  static jsonMapping = [
    'ignore': [
      'format',
      'paymentType',
      'startIssue',
      'delayedOA',
      'hybridOA',
      'coverageNote',
      'primary',
      'delayedOAEmbargo',
      'coverageDepth',
      'startVolume',
      'endDate',
      'embargo',
      'startDate',
      'endIssue',
      'endVolume',
      'description',
      'hybridOAUrl',
      'name'
    ],
    'es': [
      'hostPlatformUuid': "hostPlatform.uuid",
      'hostPlatformName': "hostPlatform.name",
      'hostPlatform': "hostPlatform.id",
      'tippTitleUuid': "title.uuid",
      'tippTitleName': "title.name",
      'tippTitle': "title.id",
      'tippPackageUuid': "pkg.uuid",
      'tippPackageName': "pkg.name",
      'tippPackage': "pkg.id",
      'titleType': "title.niceName",
      'coverage': "coverageStatements"
    ],
    'defaultLinks': [
      'pkg',
      'title',
      'hostPlatform'
    ],
    'defaultEmbeds': [
      'coverageStatements'
    ]
  ];

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
    "${uuid ?: 'gokb:TIPP:' + title?.id + ':' + pkg?.id + ':' + hostPlatform?.id}"
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
    endDate (validator: { val, obj ->
      if(obj.startDate && val && (obj.hasChanged('endDate') || obj.hasChanged('startDate')) && obj.startDate > val) {
        return ['endDate.endPriorToStart']
      }
    })
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
    accessEndDate (validator: { val, obj ->
      if(obj.accessStartDate && val && (obj.hasChanged('accessEndDate') || obj.hasChanged('accessStartDate')) && obj.accessStartDate > val) {
        return ['accessEndDate.endPriorToStart']
      }
    })
    url (nullable:true, blank:true)
  }

  public static final String restPath = "/tipps"

  def availableActions() {
    [ [code:'setStatus::Retired', label:'Retire'],
      [code:'tipp::retire', label:'Retire (with Date)'],
      [code:'setStatus::Deleted', label:'Delete', perm:'delete'],
      [code:'setStatus::Expected', label:'Mark Expected'],
      [code:'setStatus::Current', label:'Set Current'],
      [code:'tipp::move', label:'Move TIPP']
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

  /**
   * Create a new TIPP being mindful of the need to create TIPLs
   */
  public static tiplAwareCreate(tipp_fields = [:]) {

//     def result = new TitleInstancePackagePlatform(tipp_fields)
//     result.title = tipp_fields.title
//     result.hostPlatform = tipp_fields.hostPlatform
//     result.pkg = tipp_fields.pkg
    def tipp_status = tipp_fields.status ? RefdataCategory.lookup('KBComponent.Status', tipp_fields.status) : null
    def tipp_editstatus = tipp_fields.editStatus ? RefdataCategory.lookup('KBComponent.EditStatus', tipp_fields.editStatus) :null
    def result = new TitleInstancePackagePlatform(uuid: tipp_fields.uuid, status: tipp_status, editStatus: tipp_editstatus).save(failOnError: true)

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
   * Please see https://github.com/openlibraryenvironment/gokb/wiki/tipp_dto
   */
  @Transient
  public static def validateDTO(tipp_dto) {
    def result = ['valid':true, 'errors':[]]

    result.valid &= tipp_dto.package?.internalId != null
    result.valid &= tipp_dto.platform?.internalId != null
    result.valid &= tipp_dto.title?.internalId != null

    for(def coverage : tipp_dto.coverage){
        LocalDateTime parsedStart = GOKbTextUtils.completeDateString(coverage.startDate)
        LocalDateTime parsedEnd = GOKbTextUtils.completeDateString(coverage.endDate, false)

        if (coverage.startDate && !parsedStart) {
          result.valid = false
          result.errors.add("Unable to parse coverage start date ${coverage.startDate}!")
        }

        if (coverage.endDate && !parsedEnd) {
          result.valid = false
          result.errors.add("Unable to parse coverage end date ${coverage.endDate}!")
        }

        if ( !['fulltext', 'selected articles', 'abstracts'].contains(coverage.coverageDepth?.toLowerCase()) ) {
          result.valid = false
          result.errors.add("Unrecognized value '${coverage.coverageDepth}' for coverage depth")
        }

        if (parsedStart && parsedEnd && (parsedEnd < parsedStart)) {
          result.valid = false
          result.errors.add("Coverage end date must not be prior to its start date!")
        }
    }

    if ( !result.valid )  {
      log.warn("Tipp failed validation: ${tipp_dto} - pkg:${tipp_dto.package?.internalId} plat:${tipp_dto.platform?.internalId} ti:${tipp_dto.title?.internalId} -- Errors: ${result.errors}");
    }

    return result
  }

  /**
   * Please see https://github.com/openlibraryenvironment/gokb/wiki/tipp_dto
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
      def tipp = tipp_dto.uuid ? TitleInstancePackagePlatform.findByUuid(tipp_dto.uuid) : null;

      if  ( !tipp ) {
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
      }

      if ( !tipp ) {
        log.debug("Creating new TIPP..")
        def tmap = [
          'pkg': pkg,
          'title': ti,
          'hostPlatform': plt,
          'url': trimmed_url,
          'uuid': (tipp_dto.uuid ?: null),
          'status': (tipp_dto.status ?: null),
          'editStatus': (tipp_dto.editStatus ?: null)
        ]

        tipp = tiplAwareCreate(tmap)
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
        if ( tipp.status == status_current && plt.status != status_current ) {
          log.warn("TIPP platform is marked as ${plt.status?.value}!")
          ReviewRequest.raise(
            tipp,
            "The existing platform matched for this TIPP (${plt}) is marked as ${plt.status?.value}! Please review the URL/Platform for validity.",
            "Platform not marked as current.",
            user
          )
        }

        if ( tipp.isRetired() && tipp_dto.status == "Current" ) {

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
        }
        else if( tipp.isDeleted() && tipp_dto.status != "Deleted" ) {

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
          def parsedStart = GOKbTextUtils.completeDateString(c.startDate)
          def parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)

          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startVolume', c.startVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startIssue', c.startIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endVolume', c.endVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endIssue', c.endIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'embargo', c.embargo)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'coverageNote', c.coverageNote)
          changed |= com.k_int.ClassUtils.setDateIfPresent(parsedStart,tipp,'startDate')
          changed |= com.k_int.ClassUtils.setDateIfPresent(parsedEnd,tipp,'endDate')

          if (RefdataCategory.getOID('TitleInstancePackagePlatform.CoverageDepth', c.coverageDepth.capitalize())) {
            changed |= com.k_int.ClassUtils.setRefdataIfPresent(c.coverageDepth.capitalize(), tipp, 'coverageDepth', 'TitleInstancePackagePlatform.CoverageDepth')
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
                changed |= com.k_int.ClassUtils.setDateIfPresent(parsedStart,tcs,'startDate')
                changed |= com.k_int.ClassUtils.setDateIfPresent(parsedEnd,tcs,'endDate')

                cs_match = true
            }
            else if (cs_match) {
              log.debug("Matched new coverage ${c} on multiple existing coverages!")
            }
          }

          if (!cs_match) {

            def cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', c.coverageDepth) ?: RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', "Fulltext")

            tipp.addToCoverageStatements('startVolume': c.startVolume, \
             'startIssue':c.startIssue, \
             'endVolume': c.endVolume, \
             'endIssue': c.endIssue, \
             'embargo':c.embargo, \
             'coverageDepth': cov_depth, \
             'coverageNote': c.coverageNote, \
             'startDate': (parsedStart ? Date.from( parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null), \
             'endDate': (parsedEnd ? Date.from( parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
            )
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
