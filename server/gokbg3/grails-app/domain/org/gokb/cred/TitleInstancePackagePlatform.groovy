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
    accessEndDate (validator: { val, obj ->
      if(obj.accessStartDate && val && (obj.hasChanged('accessEndDate') || obj.hasChanged('accessStartDate')) && obj.accessStartDate > val) {
        return ['accessEndDate.endPriorToStart']
      }
    })
    url (nullable:true, blank:true)
  }

  public static final String restPath = "/package-titles"

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

      def pkg_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Tipps')
      def pkg_combo = new Combo(toComponent:result, fromComponent:tipp_fields.pkg, type:pkg_combo_type).save(flush:true, failOnError:true);

      def plt_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTipps')
      def plt_combo = new Combo(toComponent:result, fromComponent:tipp_fields.hostPlatform, type:plt_combo_type).save(flush:true, failOnError:true);

      def ti_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipps')
      def ti_combo = new Combo(toComponent:result, fromComponent:tipp_fields.title, type:ti_combo_type).save(flush:true, failOnError:true);

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
    def result = ['valid':true, 'errors':[:]]
    def pkgLink = tipp_dto.pkg ?: tipp_dto.package
    def pltLink = tipp_dto.hostPlatform ?: tipp_dto.platform
    def tiLink = tipp_dto.title

    if (!pkgLink) {
      result.valid = false
      result.errors.pkg = [[ message: "Missing package link!", baddata: pkgLink ]]
    }
    else {
      def pkg = null

      if (pkgLink instanceof Map) {
        pkg = Package.get(pkgLink.id ?: pkgLink.internalId)
      }
      else {
        pkg = Package.get(pkgLink)
      }

      if (!pkg) {
        result.valid = false
        result.errors.pkg = [[ message: "Could not resolve package id!", baddata: pkgLink , code: 404]]
      }
    }

    if (!pltLink) {
      result.valid = false
      result.errors.hostPlatform = [[ message: "Missing platform link!", baddata: pltLink ]]
    }
    else {
      def plt = null

      if (pltLink instanceof Map) {
        plt = Platform.get(pltLink.id ?: pltLink.internalId)
      }
      else {
        plt = Platform.get(pltLink)
      }

      if (!plt) {
        result.valid = false
        result.errors.hostPlatform = [[ message: "Could not resolve platform id!", baddata: pltLink, code: 404 ]]
      }
    }

    if (!tiLink) {
      result.valid = false
      result.errors.title = [[ message: "Missing title link!", baddata: tiLink ]]
    }
    else {
      def ti = null

      if (tiLink instanceof Map) {
        ti = TitleInstance.get(tiLink.id ?: tiLink.internalId)
      }
      else {
        ti = TitleInstance.get(tiLink)
      }

      if (!ti) {
        result.valid = false
        result.errors.title = [[message: "Could not resolve title id!", baddata: tiLink, code: 404]]
      }
    }

    if (tipp_dto.coverageStatements && !tipp_dto.coverage) {
      tipp_dto.coverage = tipp_dto.coverageStatements
    }

    for(def coverage : tipp_dto.coverage){
        LocalDateTime parsedStart = GOKbTextUtils.completeDateString(coverage.startDate)
        LocalDateTime parsedEnd = GOKbTextUtils.completeDateString(coverage.endDate, false)

        if (coverage.startDate && !parsedStart) {
          result.valid = false
          result.errors.startDate = [[message:"Unable to parse coverage start date ${coverage.startDate}!", baddata: coverage.startDate]]
        }

        if (coverage.endDate && !parsedEnd) {
          result.valid = false
          result.errors.endDate = [[message: "Unable to parse coverage end date ${coverage.endDate}!", baddata: coverage.endDate]]
        }

        if ( !['fulltext', 'selected articles', 'abstracts'].contains(coverage.coverageDepth?.toLowerCase()) ) {
          result.valid = false
          result.errors.coverageDepth = [[message: "Unrecognized value '${coverage.coverageDepth}' for coverage depth", baddata: coverage.coverageDepth]]
        }

        if (parsedStart && parsedEnd && (parsedEnd < parsedStart)) {
          result.valid = false
          result.errors.endDate = [[message:"Coverage end date must not be prior to its start date!", baddata: coverage.endDate]]
        }
    }

    if ( !result.valid )  {
      log.warn("Tipp failed validation: ${tipp_dto} - pkg:${pkgLink} plat:${pltLink} ti:${tiLink} -- Errors: ${result.errors}");
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
    def pkg = null
    def plt = null
    def ti = null

    if (tipp_dto.pkg || tipp_dto.package) {
      def pkg_info = tipp_dto.package ?: tipp_dto.pkg

      if (pkg_info instanceof Map) {
        pkg = Package.get(pkg_info.id ?: pkg_info.internalId)
      }
      else {
        pkg = Package.get(pkg_info)
      }

      log.debug("Package lookup: ${pkg}")
    }

    if (tipp_dto.hostPlatform || tipp_dto.platform) {
      def plt_info = tipp_dto.hostPlatform ?: tipp_dto.platform

      if (plt_info instanceof Map) {
        plt = Platform.get(plt_info.id ?: plt_info.internalId)
      }
      else {
        plt = Platform.get(plt_info)
      }

      log.debug("Platform lookup: ${plt}")
    }
    
    if (tipp_dto.title) {
      def title_info = tipp_dto.title

      if (title_info instanceof Map) {
        ti = TitleInstance.get(title_info.id ?: title_info.internalId)
      }
      else {
        ti = TitleInstance.get(title_info)
      }

      log.debug("Title lookup: ${ti}")
    }

    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')
    def status_retired = RefdataCategory.lookupOrCreate('KBComponent.Status','Retired')
    def trimmed_url = tipp_dto.url ? tipp_dto.url.trim() : null
    def curator = pkg?.curatoryGroups?.size() > 0 ? (user.adminStatus || user.curatoryGroups?.id.intersect(pkg?.curatoryGroups?.id)) : true

    if ( pkg && plt && ti && curator ) {
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
            user,
            null,
            RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Platform Noncurrent')
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

        if (tipp_dto.coverageStatements && !tipp_dto.coverage) {
          tipp_dto.coverage = tipp_dto.coverageStatements
        }

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
          def startAsDate = (parsedStart ? Date.from( parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
          def endAsDate = (parsedEnd ? Date.from( parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)

          tipp.coverageStatements?.each { tcs ->

            if ( !cs_match && (
                (c.id && tcs.id == c.id) ||
                (tcs.startVolume && tcs.startVolume == c.startVolume) ||
                (tcs.startDate && tcs.startDate == startAsDate) ||
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
             'startDate': startAsDate, \
             'endDate': endAsDate
            )
          }
          // refdata setStringIfDifferent(tipp, 'coverageDepth', c.coverageDepth)
        }
//         tipp.save(flush:true, failOnError:true);
      }
      result = tipp;
    }
    else {
      log.debug("Not able to reference TIPP: ${tipp_dto}")
    }

    result;
  }


  @Transient
  static def oaiConfig = [
    id:'tipps',
    textDescription:'TIPP repository for GOKb',
    query:" from TitleInstancePackagePlatform as o ",
    pageSize:10
  ]

  /**
   *  Render this tipp as OAI_dc
   */
  @Transient
  def toOaiDcXml(builder, attr) {
    builder.'dc'(attr) {
      'dc:title' (title.name)
    }
  }

  /**
   *  Render this TIPP as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    def linked_pkg = getPkg()
    def ti = getTitle()

    builder.'gokb' (attr) {
      builder.'tipp' ([id:(id), uuid:(uuid)]) {
        builder.'status' (status?.value)
        builder.'lastUpdated' (lastUpdated?sdf.format(lastUpdated):null)
        builder.'format' (format?.value)
        builder.'url'(url?:"")
        builder.'title' ([id:ti.id, uuid:ti.uuid]) {
          builder.'name' (ti.name?.trim())
          builder.'type' (titleClass)
          builder.'status' (ti.status?.value)
          builder.'identifiers' {
            titleIds.each { tid ->
              builder.'identifier'([namespace:tid[0], value:tid[1], type:tid[2]])
            }
          }
        }
        builder.'package'([id:linked_pkg.id, uuid: linked_pkg.uuid]) {
          'name' (linked_pkg.name)
          'status' (linked_pkg.status?.value)
          'editStatus' (linked_pkg.editStatus?.value)
          'listStatus' (linked_pkg.listStatus?.value)
          'listVerifiedDate' (linked_pkg.listVerifiedDate?sdf.format(linked_pkg.listVerifiedDate):null)
          'lastUpdated' (linked_pkg.lastUpdated?sdf.format(linked_pkg.lastUpdated):null)
          'contentType' (linked_pkg.contentType?.value)
          if (linked_pkg.provider) {
            builder.'provider' ([id:linked_pkg.provider?.id, uuid:linked_pkg.provider?.uuid]) {
              'name' (linked_pkg.provider?.name)
              'mision' (linked_pkg.provider?.mission?.value)
            }
          }
          else {
            builder.'provider' ()
          }
        }
        builder.'platform'([id:hostPlatform.id, uuid:hostPlatform.uuid]) {
          'primaryUrl' (hostPlatform.primaryUrl?.trim())
          'name' (hostPlatform.name?.trim())
        }
        'access'([start:(accessStartDate ? sdf.format(accessStartDate):null), end:(accessEndDate?sdf.format(accessEndDate):null)])
        def cov_statements = getCoverageStatements()
        if(cov_statements?.size() > 0){
          cov_statements.each { tcs ->
            'coverage'(
              startDate:(tcs.startDate?sdf.format(tcs.startDate):null),
              startVolume:(tcs.startVolume),
              startIssue:(tcs.startIssue),
              endDate:(tcs.endDate?sdf.format(tcs.endDate):null),
              endVolume:(tcs.endVolume),
              endIssue:(tcs.endIssue),
              coverageDepth:(tcs.coverageDepth?.value?:null),
              coverageNote:(tcs.coverageNote),
              embargo: (tcs.embargo)
            )
          }
        }
      }
    }
  }

  @Transient
  public getTitleIds() {
    def refdata_ids = RefdataCategory.lookupOrCreate('Combo.Type','KBComponent.Ids');
    def status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    def result = Identifier.executeQuery("select i.namespace.value, i.value, i.namespace.family from Identifier as i, Combo as c where c.fromComponent = ? and c.type = ? and c.toComponent = i and c.status = ?",[title,refdata_ids,status_active],[readOnly:true]);
    result
  }

  @Transient
  public getTitleClass() {
    def result = KBComponent.get(title.id)?.class.getSimpleName();

    result
  }

}
