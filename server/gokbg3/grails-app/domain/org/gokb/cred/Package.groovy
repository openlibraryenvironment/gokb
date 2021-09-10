package org.gokb.cred

import com.k_int.ClassUtils
import grails.gorm.transactions.Transactional
import org.gokb.GOKbTextUtils

import javax.persistence.Transient
import groovy.util.logging.*
import groovy.time.TimeCategory


import org.gokb.refine.*

@Slf4j
class Package extends KBComponent {

  def dateFormatService
  static def messageService

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
  RefdataValue contentType
  RefdataValue breakable
  RefdataValue consistent
  RefdataValue fixed
  RefdataValue paymentType
  RefdataValue global
  RefineProject lastProject
  String globalNote
  String listVerifier
  User userListVerifier
  Date listVerifiedDate
  String descriptionURL

  private static refdataDefaults = [
    "scope"      : "Front File",
    "listStatus" : "Checked",
    "breakable"  : "Unknown",
    "consistent" : "Unknown",
    "fixed"      : "Unknown",
    "paymentType": "Unknown",
    "global"     : "Global"
  ]

  static manyByCombo = [
    tipps         : TitleInstancePackagePlatform,
    children      : Package,
    curatoryGroups: CuratoryGroup
  ]

  static hasByCombo = [
    parent         : Package,
    broker         : Org,
    provider       : Org,
    licensor       : Org,
    vendor         : Org,
    nominalPlatform: Platform,
    'previous'     : Package,
    successor      : Package
  ]

  static mappedByCombo = [
    children : 'parent',
    successor: 'previous',
  ]

  static hasOne = [updateToken: UpdateToken]

  static mapping = {
    includes KBComponent.mapping
    listStatus column: 'pkg_list_status_rv_fk'
    lastProject column: 'pkg_refine_project_fk'
    scope column: 'pkg_scope_rv_fk'
    breakable column: 'pkg_breakable_rv_fk'
    consistent column: 'pkg_consistent_rv_fk'
    fixed column: 'pkg_fixed_rv_fk'
    paymentType column: 'pkg_payment_type_rv_fk'
    global column: 'pkg_global_rv_fk'
    globalNote column: 'pkg_global_note'
    listVerifier column: 'pkg_list_verifier'
    userListVerifier column: 'pkg_list_verifier_user_fk'
    descriptionURL column: 'pkg_descr_url'
  }

  static constraints = {
    lastProject(nullable: true, blank: false)
    scope(nullable: true, blank: false)
    listStatus(nullable: true, blank: false)
    breakable(nullable: true, blank: false)
    consistent(nullable: true, blank: false)
    fixed(nullable: true, blank: false)
    paymentType(nullable: true, blank: false)
    global(nullable: true, blank: false)
    globalNote(nullable: true, blank: true)
    lastProject(nullable: true, blank: false)
    descriptionURL(nullable: true, blank: true)
    name(validator: { val, obj ->
      if (obj.hasChanged('name')) {
        if (val && val.trim()) {
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def dupes = Package.findAllByNameIlikeAndStatusNotEqual(val, status_deleted);

          if (dupes?.size() > 0 && dupes.any { it != obj }) {
            return ['notUnique']
          }
        }
        else {
          return ['notNull']
        }
      }
    })
  }

  public String getRestPath() {
    return "/packages"
  }

  static jsonMapping = [
    'ignore'       : [
      'lastProject',
      'updateToken'
    ],
    'es'           : [
      'nominalPlatformUuid': "nominalPlatform.uuid",
      'nominalPlatformName': "nominalPlatform.name",
      'nominalPlatform'    : "nominalPlatform.id",
      'cpname'             : false,
      'provider'           : "provider.id",
      'providerName'       : "provider.name",
      'providerUuid'       : "provider.uuid",
      'paymentType'        : false,
      'listStatus'         : "refdata",
      'contentType'        : "refdata",
      'scope'              : "refdata",
      'global'             : "refdata",
      'editStatus'         : "refdata"
    ],
    'defaultLinks' : [
      'provider',
      'nominalPlatform',
      'curatoryGroups'
    ],
    'defaultEmbeds': [
      'ids',
      'variantNames',
      'curatoryGroups'
    ]
  ]

  static def refdataFind(params) {
    def result = [];
    def status_deleted = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    def status_filter = null

    if (params.filter1) {
      status_filter = RefdataCategory.lookup('KBComponent.Status', params.filter1)
    }

    def ql = null;
    ql = Package.findAllByNameIlikeAndStatusNotEqual("${params.q}%", status_deleted, params)

    if (ql) {
      ql.each { t ->
        if (!status_filter || t.status == status_filter) {
          result.add([id: "${t.class.name}:${t.id}", text: "${t.name}", status: "${t.status?.value}"])
        }
      }
    }

    result
  }

  @Transient
  public getTitles(def onlyCurrent = true, int max = 10, offset = 0) {
    def all_titles = null
    log.debug("getTitles :: current ${onlyCurrent} - max ${max} - offset ${offset}")

    if (this.id) {
      if (onlyCurrent) {
        def refdata_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current');

        all_titles = TitleInstance.executeQuery('''select distinct title
          from TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=?
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title
            and tipp.status = ?
            and title.status = ?'''
          , [this, refdata_current, refdata_current], [max: max, offset: offset]);
      }
      else {
        all_titles = TitleInstance.executeQuery('''select distinct title
          from TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=?
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title'''
          , [this], [max: max, offset: offset]);
      }
    }

    return all_titles;
  }

  @Transient
  public getCurrentTitleCount() {
    def refdata_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current');

    int result = TitleInstance.executeQuery('''select count(distinct title.id)
      from TitleInstance as title,
        Combo as pkgCombo,
        Combo as titleCombo,
        TitleInstancePackagePlatform as tipp
      where pkgCombo.toComponent=tipp
        and pkgCombo.fromComponent=?
        and titleCombo.toComponent=tipp
        and titleCombo.fromComponent=title
        and tipp.status = ?
        and title.status = ?'''
      , [this, refdata_current, refdata_current])[0];

    result
  }

  @Transient
  public getCurrentTippCount() {
    def refdata_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current');
    def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')

    int result = Combo.executeQuery("select count(c.id) from Combo as c where c.fromComponent = ? and c.type = ? and c.toComponent.status = ?"
      , [this, combo_tipps, refdata_current])[0]

    result
  }

  @Transient
  public getReviews(def onlyOpen = true, def onlyCurrent = false) {
    def all_rrs = null
    def refdata_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current');

    if (onlyOpen) {

      log.debug("Looking for more ReviewRequests connected to ${this}")

      def refdata_open = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open');

      if (onlyCurrent) {
        all_rrs = ReviewRequest.executeQuery('''select distinct rr
          from ReviewRequest as rr,
            TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=?
            and tipp.status = ?
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title
            and rr.componentToReview = title
            and rr.status = ?'''
          , [this, refdata_current, refdata_open]);
      }
      else {
        all_rrs = ReviewRequest.executeQuery('''select distinct rr
          from ReviewRequest as rr,
            TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=?
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title
            and rr.componentToReview = title
            and rr.status = ?'''
          , [this, refdata_open]);
      }
    }
    else {
      if (onlyCurrent) {
        all_rrs = ReviewRequest.executeQuery('''select rr
          from ReviewRequest as rr,
            TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=?
            and tipp.status = ?
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title
            and rr.componentToReview = title'''
          , [this, refdata_current]);
      }
      else {
        all_rrs = ReviewRequest.executeQuery('''select rr
          from ReviewRequest as rr,
            TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=?
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title
            and rr.componentToReview = title'''
          , [this]);
      }
    }

    return all_rrs;
  }

  public void deleteSoft(context) {
    // Call the delete method on the superClass.
    super.deleteSoft(context)

    // Delete the tipps too as a TIPP should not exist without the associated,
    // package.
    def tipps = getTipps()
    Date now = new Date()

    if (tipps?.size() > 0) {
      def deleted_status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      def tipp_ids = tipps?.collect { it.id }

      TitleInstancePackagePlatform.executeUpdate("update TitleInstancePackagePlatform as t set t.status = :del, t.lastUpdateComment = 'Deleted via Package delete', t.lastUpdated = :now where t.status != :del and t.id IN (:ttd)", [del: deleted_status, ttd: tipp_ids, now: now])
    }
  }


  public void retire(context) {
    log.debug("package::retire");
    // Call the delete method on the superClass.
    log.debug("Updating package status to retired");
    def retired_status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Retired');
    this.status = retired_status
    this.save();

    // Delete the tipps too as a TIPP should not exist without the associated,
    // package.
    log.debug("Retiring tipps");

    def tipps = getTipps()
    Date now = new Date()

    if (tipps?.size() > 0) {
      def tipp_ids = tipps?.collect { it.id }

      TitleInstancePackagePlatform.executeUpdate("update TitleInstancePackagePlatform as t set t.status = :ret, t.lastUpdateComment = 'Retired via Package retire', t.lastUpdated = :now where t.id IN (:ttd)", [ret: retired_status, ttd: tipp_ids, now: now])
    }
  }


  @Transient
  def availableActions() {
    [
      [code: 'method::deleteSoft', label: 'Delete (with associated TIPPs)', perm: 'delete'],
      [code: 'method::retire', label: 'Retire Package (with associated TIPPs)'],
      [code: 'exportPackage', label: 'TSV Export'],
      [code: 'kbartExport', label: 'KBART Export'],
      [code: 'verifyTitleList', label: 'Verify Title List'],
      [code: 'packageUrlUpdate', label: 'Trigger Update']
      // [code:'method::registerWebhook', label:'Register Web Hook']
    ]
  }

  @Transient
  def getWebHooks() {
    def result = []

    result.hooks = WebHook.findAllByOid("org.gokb.cred.Package:${this.id}");

    result
  }

  @Transient
  static def oaiConfig = [
    id             : 'packages',
    textDescription: 'Package repository for GOKb',
    query          : " from Package as o ",
    curators       : 'Package.CuratoryGroups',
    pageSize       : 3
  ]

  /**
   *  Render this package as OAI_dc
   */
  @Transient
  def toOaiDcXml(builder, attr) {
    builder.'dc'(attr) {
      'dc:title'(name)
    }
  }

  /**
   *  Render this package as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {

    log.debug("toGoKBXml... ${this.class.name}:${id}");

    def identifier_prefix = "uri://gokb/${grailsApplication.config.sysid}/title/"

    def refdata_package_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Tipps');
    def refdata_hosted_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTipps');
    def refdata_ti_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipps');
    def refdata_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted');
    String tipp_hql = "from TitleInstancePackagePlatform as tipp where exists (select 1 from Combo where fromComponent = :pkg and toComponent = tipp and type = :ctype)"
    def tipp_hql_params = [pkg: this, ctype: refdata_package_tipps]

    // Get the tipps manually rather than iterating over the collection - For better management
    def tipps_count = this.status != refdata_deleted ? TitleInstancePackagePlatform.executeQuery("select count(tipp.id) " + tipp_hql, tipp_hql_params, [readOnly: true])[0] : 0
    log.debug("Query complete...");

    builder.'gokb'(attr) {
      builder.'package'(['id': (id), 'uuid': (uuid)]) {
        addCoreGOKbXmlFields(builder, attr)

        'scope'(scope?.value)
        'listStatus'(listStatus?.value)
        'breakable'(breakable?.value)
        'consistent'(consistent?.value)
        'fixed'(fixed?.value)
        'paymentType'(paymentType?.value)
        'global'(global?.value)
        'globalNote'(globalNote)
        'contentType'(contentType?.value)

        if (nominalPlatform) {
          builder.'nominalPlatform'([id: nominalPlatform.id, uuid: nominalPlatform.uuid]) {
            'primaryUrl'(nominalPlatform.primaryUrl)
            'name'(nominalPlatform.name)
          }
        }

        if (provider) {
          builder.'nominalProvider'([id: provider.id, uuid: provider.uuid]) {
            'name'(provider.name)
          }
        }

        'listVerifiedDate'(listVerifiedDate ? dateFormatService.formatIsoTimestamp(listVerifiedDate) : null)

        builder.'curatoryGroups' {
          curatoryGroups.each { cg ->
            builder.'group' {
              builder.'name'(cg.name)
            }
          }
        }

        'dateCreated'(dateFormatService.formatIsoTimestamp(dateCreated))
        'TIPPs'(count: tipps_count) {
          int offset = 0
          while (offset < tipps_count) {
            log.debug("Fetching TIPPs batch ${offset}/${tipps_count}")
            def tipps = TitleInstancePackagePlatform.executeQuery(tipp_hql + " order by tipp.id", tipp_hql_params, [readOnly: true, max: 50, offset: offset])
            log.debug("fetch complete ..")
            offset += 50
            tipps.each { tipp ->
              builder.'TIPP'(['id': tipp.id, 'uuid': tipp.uuid]) {
                builder.'status'(tipp.status?.value)
                builder.'name'(tipp.name)
                builder.'lastUpdated'(tipp.lastUpdated ? dateFormatService.formatIsoTimestamp(tipp.lastUpdated) : null)
                builder.'series'(tipp.series)
                builder.'subjectArea'(tipp.subjectArea)
                builder.'publisherName'(tipp.publisherName)
                builder.'dateFirstInPrint'(tipp.dateFirstInPrint)
                builder.'dateFirstOnline'(tipp.dateFirstOnline)
                builder.'medium'(tipp.format?.value)
                builder.'publicationType'(tipp.publicationType?.value)
                if (tipp.title) {
                  builder.'title'(['id': tipp.title.id, 'uuid': tipp.title.uuid]) {
                    builder.'name'(tipp.title.name?.trim())
                    builder.'type'(getTitleClass(tipp.title.id))
                    builder.'status'(tipp.title.status?.value)
                    builder.'identifiers' {
                      getTitleIds(tipp.title.id).each { tid ->
                        builder.'identifier'('namespace': tid[0], 'namespaceName': tid[3], 'value': tid[1], 'type': tid[2])
                      }
                    }
                  }
                }
                else {
                  builder.'title'()
                }
                builder.'identifiers' {
                  getTippIds(tipp.id).each { tid ->
                    builder.'identifier'('namespace': tid[0], 'namespaceName': tid[3], 'value': tid[1], 'type': tid[2])
                  }
                }
                'platform'([id: tipp.hostPlatform.id, 'uuid': tipp.hostPlatform.uuid]) {
                  'primaryUrl'(tipp.hostPlatform.primaryUrl?.trim())
                  'name'(tipp.hostPlatform.name?.trim())
                }
                'access'(start: tipp.accessStartDate ? dateFormatService.formatIsoTimestamp(tipp.accessStartDate) : null, end: tipp.accessEndDate ? dateFormatService.formatIsoTimestamp(tipp.accessEndDate) : null)
                def cov_statements = getCoverageStatements(tipp.id)
                if (cov_statements?.size() > 0) {
                  cov_statements.each { tcs ->
                    'coverage'(
                      startDate: (tcs.startDate ? dateFormatService.formatIsoTimestamp(tcs.startDate) : null),
                      startVolume: (tcs.startVolume),
                      startIssue: (tcs.startIssue),
                      endDate: (tcs.endDate ? dateFormatService.formatIsoTimestamp(tcs.endDate) : null),
                      endVolume: (tcs.endVolume),
                      endIssue: (tcs.endIssue),
                      coverageDepth: (tcs.coverageDepth?.value ?: null),
                      coverageNote: (tcs.coverageNote),
                      embargo: (tcs.embargo)
                    )
                  }
                }
                'url'(tipp.url ?: "")
              }
            }
            log.debug("Batch complete ..")
          }
        }
      }
    }

    log.debug("toGoKBXml complete...");
  }

  @Transient
  private static getTitleIds(Long title_id) {
    def refdata_ids = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids');
    def status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    def result = Identifier.executeQuery("select i.namespace.value, i.value, i.namespace.family, i.namespace.name from Identifier as i, Combo as c where c.fromComponent.id = ? and c.type = ? and c.toComponent = i and c.status = ?", [title_id, refdata_ids, status_active], [readOnly: true]);
    result
  }

  @Transient
  private static getTippIds(Long tipp_id) {
    def refdata_ids = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids');
    def status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    def result = Identifier.executeQuery("select i.namespace.value, i.value, i.namespace.family, i.namespace.name from Identifier as i, Combo as c where c.fromComponent.id = ? and c.type = ? and c.toComponent = i and c.status = ?", [tipp_id, refdata_ids, status_active], [readOnly: true]);
    result
  }

  @Transient
  private static getTitleClass(Long title_id) {
    def result = KBComponent.get(title_id)?.class.getSimpleName();

    result
  }

  @Transient
  private static getCoverageStatements(Long tipp_id) {
    def result = TIPPCoverageStatement.executeQuery("from TIPPCoverageStatement as tcs where tcs.owner.id = :tipp", ['tipp': tipp_id], [readOnly: true])
    result
  }

  @Transient
  public getRecentActivity(n) {
    def result = [];

    if (this.id) {
      def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')

      // select tipp, accessStartDate, 'Added' from tipps UNION select tipp, accessEndDate, 'Removed' order by date

//       def additions = TitleInstancePackagePlatform.executeQuery('select tipp, tipp.accessStartDate, \'Added\' ' +
//                        'from TitleInstancePackagePlatform as tipp, Combo as c '+
//                        'where c.fromComponent=? and c.toComponent=tipp and tipp.accessStartDate is not null order by tipp.dateCreated DESC',
//                       [this], [max:n]);
//       def deletions = TitleInstancePackagePlatform.executeQuery('select tipp, tipp.accessEndDate, \'Removed\' ' +
//                        'from TitleInstancePackagePlatform as tipp, Combo as c '+
//                        'where c.fromComponent= :pkg and c.toComponent=tipp and tipp.accessEndDate is not null order by tipp.lastUpdated DESC',
//                        [pkg: this], [max:n]);

      def changes = TitleInstancePackagePlatform.executeQuery('select tipp from TitleInstancePackagePlatform as tipp, Combo as c ' +
        'where c.fromComponent= ? and c.toComponent=tipp order by tipp.lastUpdated DESC',
        [this]);

      use(TimeCategory) {
        changes.each {
          if (it.isDeleted()) {
            result.add([it, it.lastUpdated, 'Deleted (status)'])
          }
          else if (it.isRetired()) {
            result.add([it, it.lastUpdated, it.accessEndDate ? "Retired (${it.accessEndDate})" : 'Retired (status)'])
          }
          else if (it.lastUpdated <= it.dateCreated + 1.minute) {
            result.add([it, it.dateCreated, it.accessStartDate ? "Added (${it.accessStartDate})" : 'Newly Added'])
          }
          else {
            result.add([it, it.lastUpdated, 'Updated'])
          }
        }
      }

//       result.addAll(additions)
//       result.addAll(deletions)
      result.sort { it[1] }
      result = result.reverse();
      result = result.take(n);
    }

    return result;
  }

  public void addCuratoryGroupIfNotPresent(String cgname) {
    boolean add_needed = true;
    curatoryGroups.each { cgtest ->
      if (cgtest.name.equalsIgnoreCase(cgname))
        add_needed = false;
    }

    if (add_needed) {
      def cg = CuratoryGroup.findByName(cgname) ?: new CuratoryGroup(name: cgname).save(flush: true, failOnError: true);
      curatoryGroups.add(cg);
    }
  }

  /**
   * Definitive rules for a valid package header
   */
  public static def validateDTO(packageHeaderDTO, locale) {
    def result = [valid: true, errors: [:], match: false]

    if (!packageHeaderDTO.name?.trim()) {
      result.valid = false
      result.errors.name = [[message: messageService.resolveCode('crossRef.package.error.name', null, locale), baddata: packageHeaderDTO.name]]
    }

    String idJsonKey = 'ids'
    def ids_list = packageHeaderDTO[idJsonKey]
    if (!ids_list) {
      idJsonKey = 'identifiers'
      ids_list = packageHeaderDTO[idJsonKey]
    }
    if (ids_list) {
      def id_errors = Identifier.validateDTOs(ids_list, locale)
      if (id_errors.size() > 0) {
        result.errors.put(idJsonKey, id_errors)
      }
    }
    if (result.valid) {
      def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
      def pkg_normname = Package.generateNormname(packageHeaderDTO.name)

      def name_candidates = Package.executeQuery("from Package as p where p.normname = ? and p.status <> ?", [pkg_normname, status_deleted])
      def full_matches = []

      if (packageHeaderDTO.uuid) {
        result.match = Package.findByUuid(packageHeaderDTO.uuid) ? true : false
      }

      if (!result.match && name_candidates.size() == 1) {
        result.match = true
      }

      if (!result.match) {
        def variant_normname = GOKbTextUtils.normaliseString(packageHeaderDTO.name)
        def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

        if (variant_candidates.size() == 1) {
          result.match = true
          log.debug("Package matched via existing variantName.")
        }
      }

      if (!result.match) {
        log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
        packageHeaderDTO.variantNames.each {

          if (it.trim().size() > 0) {
            def var_pkg = Package.findByName(it)

            if (var_pkg) {
              log.debug("Found existing package name for variantName ${it}")
            }
            else {

              def variant_normname = GOKbTextUtils.normaliseString(it)
              def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

              if (variant_candidates.size() == 1) {
                log.debug("Found existing package variant name for variantName ${it}")
                result.match = true
              }
            }
          }
        }
      }
    }

    if (packageHeaderDTO.provider && packageHeaderDTO.provider instanceof Integer) {
      def prov = Org.get(packageHeaderDTO.provider)

      if (!prov) {
        result.errors.provider = [[message: messageService.resolveCode('crossRef.error.lookup', ["Provider", "ID"], locale), code: 404, baddata: packageHeaderDTO.provider]]
        result.valid = false
      }
    }

    if (packageHeaderDTO.nominalPlatform && packageHeaderDTO.nominalPlatform instanceof Integer) {
      def prov = Platform.get(packageHeaderDTO.nominalPlatform)

      if (!prov) {
        result.errors.nominalPlatform = [[message: messageService.resolveCode('crossRef.error.lookup', ["Platform", "ID"], locale), code: 404, baddata: packageHeaderDTO.nominalPlatform]]
        result.valid = false
      }
    }

    result
  }
}
