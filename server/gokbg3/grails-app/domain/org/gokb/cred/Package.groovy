package org.gokb.cred

import javax.persistence.Transient
import groovy.util.logging.*
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import com.k_int.ClassUtils
import groovy.time.TimeCategory


import org.gokb.refine.*

@Slf4j
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
  RefdataValue contentType
  RefdataValue breakable
  RefdataValue consistent
  RefdataValue fixed
  RefdataValue paymentType
  RefdataValue global
  RefineProject lastProject
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
        } else {
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
      'titleCount'         : false,
      'paymentType'        : false,
      'listStatus'         : "refdata",
      'contentType'        : "refdata",
      'scope'              : "refdata"
    ],
    'defaultLinks' : [
      'tipps',
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
      } else {
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
      } else {
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
    } else {
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
      } else {
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

  private static OAI_PKG_CONTENTS_QRY = '''
select tipp.id,
       title.name,
       title.id,
       plat.name,
       plat.id,
       tipp.url,
       tipp.status,
       tipp.accessStartDate,
       tipp.accessEndDate,
       tipp.format,
       plat.primaryUrl,
       tipp.lastUpdated,
       tipp.uuid,
       title.uuid,
       plat.uuid,
       title.status
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
    order by tipp.id''';

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

    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    def identifier_prefix = "uri://gokb/${grailsApplication.config.sysid}/title/"

    def refdata_package_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'Package.Tipps');
    def refdata_hosted_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTipps');
    def refdata_ti_tipps = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipps');
    def refdata_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted');

    // log.debug("Running package contents qry : ${OAI_PKG_CONTENTS_QRY}");

    // Get the tipps manually rather than iterating over the collection - For better management
    def tipps = this.status != refdata_deleted ? TitleInstancePackagePlatform.executeQuery(OAI_PKG_CONTENTS_QRY, [this, refdata_package_tipps, refdata_hosted_tipps, refdata_ti_tipps], [readOnly: true]) : []

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

        'listVerifiedDate'(listVerifiedDate ? sdf.format(listVerifiedDate) : null)

        builder.'curatoryGroups' {
          curatoryGroups.each { cg ->
            builder.'group' {
              builder.'name'(cg.name)
            }
          }
        }

        'dateCreated'(sdf.format(dateCreated))
        'TIPPs'(count: tipps?.size()) {
          tipps.each { tipp ->
            builder.'TIPP'(['id': tipp[0], 'uuid': tipp[12]]) {
              builder.'status'(tipp[6]?.value)
              builder.'lastUpdated'(tipp[11] ? sdf.format(tipp[11]) : null)
              builder.'medium'(tipp[9]?.value)
              builder.'title'(['id': tipp[2], 'uuid': tipp[13]]) {
                builder.'name'(tipp[1]?.trim())
                builder.'type'(getTitleClass(tipp[2]))
                builder.'status'(tipp[15])
                builder.'identifiers' {
                  getTitleIds(tipp[2]).each { tid ->
                    builder.'identifier'('namespace': tid[0], 'namespaceName': tid[3], 'value': tid[1], 'type': tid[2])
                  }
                }
              }
              builder.'identifiers' {
                getTippIds(tipp[0]).each { tid ->
                  builder.'identifier'('namespace': tid[0], 'namespaceName': tid[3], 'value': tid[1], 'type': tid[2])
                }
              }
              'platform'([id: tipp[4], 'uuid': tipp[14]]) {
                'primaryUrl'(tipp[10]?.trim())
                'name'(tipp[3]?.trim())
              }
              'access'(start: tipp[7] ? sdf.format(tipp[7]) : null, end: tipp[8] ? sdf.format(tipp[8]) : null)
              def cov_statements = getCoverageStatements(tipp[0])
              if (cov_statements?.size() > 0) {
                cov_statements.each { tcs ->
                  'coverage'(
                    startDate: (tcs.startDate ? sdf.format(tcs.startDate) : null),
                    startVolume: (tcs.startVolume),
                    startIssue: (tcs.startIssue),
                    endDate: (tcs.endDate ? sdf.format(tcs.endDate) : null),
                    endVolume: (tcs.endVolume),
                    endIssue: (tcs.endIssue),
                    coverageDepth: (tcs.coverageDepth?.value ?: null),
                    coverageNote: (tcs.coverageNote),
                    embargo: (tcs.embargo)
                  )
                }
              }
              'url'(tipp[5] ?: "")
            }
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
          } else if (it.isRetired()) {
            result.add([it, it.lastUpdated, it.accessEndDate ? "Retired (${it.accessEndDate})" : 'Retired (status)'])
          } else if (it.lastUpdated <= it.dateCreated + 1.minute) {
            result.add([it, it.dateCreated, it.accessStartDate ? "Added (${it.accessStartDate})" : 'Newly Added'])
          } else {
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

  /**
   * Definitive rules for a valid package header
   */
  @Transient
  public static def validateDTO(packageHeaderDTO) {
    def result = [valid: true, errors:[:], match:false]

    if (!packageHeaderDTO.name?.trim()) {
      result.valid = false
      result.errors.name = [[message: "Missing package name!", baddata: packageHeaderDTO.name]]
    }

    def ids_list = packageHeaderDTO.identifiers ?: packageHeaderDTO.ids
    def id_errors = []

    ids_list?.each { idobj ->
      def id_def = [:]
      def ns_obj = null

      if (idobj instanceof Map) {
        def id_ns = idobj.type ?: (idobj.namespace ?: null)

        id_def.value = idobj.value

        if (id_ns instanceof String) {
          log.debug("Default namespace handling for ${id_ns}..")
          ns_obj = IdentifierNamespace.findByValueIlike(id_ns)
        }
        else if (id_ns) {
          log.debug("Handling namespace def ${id_ns}")
          ns_obj = IdentifierNamespace.get(id_ns)
        }

        if (!ns_obj) {
          id_errors.add([message: message(code: 'default.not.found.message', args: ["Namespace", id_ns], default:"unable to lookup identifier namespace ${id_ns}!")])
        }
        else {
          id_def.type = ns_obj.value
        }
      }
      else if (idobj instanceof Integer){
        Identifier the_id = Identifier.get(id_inc)

        if (!the_id) {
          id_errors.add([message:"Unable to lookup identifier object by ID!", baddata: idobj])
          result.valid = false
        }
      }
      else {
        log.warn("Missing information in id object ${idobj}")
        id_errors.add([message:"Missing information for identifier object!", baddata: idobj])
        result.valid = false
      }

      if (ns_obj && id_def.size() > 0) {
        if (!Identifier.findByNamespaceAndNormname(ns_obj, Identifier.normalizeIdentifier(id_def.value))) {
          if ( ns_obj.pattern && !(id_def.value ==~ ns_obj.pattern) ) {
            log.warn("Validation for ${id_def.type}:${id_def.value} failed!")
            id_errors.add([message:"Validation for identifier ${id_def.type}:${id_def.value} failed!", baddata: idobj])
            result.valid = false
          }
          else {
            log.debug("New identifier ..")
          }
        }
        else {
          log.debug("Found existing identifier ..")
        }
      }
    }

    if (id_errors.size() > 0) {
      result.errors.ids = id_errors
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
            } else {

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
        result.errors.provider = [[message: "Unable to find provider via ID", baddata: packageHeaderDTO.provider]]
        result.valid =false
      }
    }

    if (packageHeaderDTO.nominalPlatform && packageHeaderDTO.nominalPlatform instanceof Integer) {
      def prov = Platform.get(packageHeaderDTO.nominalPlatform)

      if (!prov) {
        result.errors.nominalPlatform = [[message: "Unable to find platform via ID", baddata: packageHeaderDTO.nominalPlatform]]
        result.valid = false
      }
    }

    result
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
   * nominalPlatform:54678
   * provider:4325
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
  public static upsertDTO(packageHeaderDTO, def user = null) {
    log.info("Upsert package with header ${packageHeaderDTO}");
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def pkg_normname = Package.generateNormname(packageHeaderDTO.name)

    log.debug("Checking by normname ${pkg_normname} ..")
    def name_candidates = Package.executeQuery("from Package as p where p.normname = ? and p.status <> ?", [pkg_normname, status_deleted])
    def full_matches = []
    def created = false
    def result = packageHeaderDTO.uuid ? Package.findByUuid(packageHeaderDTO.uuid) : null;
    boolean changed = false;

    if (!result && name_candidates.size() > 0 && packageHeaderDTO.identifiers?.size() > 0) {
      log.debug("Got ${name_candidates.size()} matches by name. Checking against identifiers!")
      name_candidates.each { mp ->
        if (mp.ids.size() > 0) {
          def id_match = false;

          packageHeaderDTO.identifiers.each { rid ->

            Identifier the_id = Identifier.lookupOrCreateCanonicalIdentifier(rid.type, rid.value);

            if (mp.ids.contains(the_id)) {
              id_match = true
            }
          }

          if (id_match && !full_matches.contains(mp)) {
            full_matches.add(mp)
          }
        }
      }

      if (full_matches.size() == 1) {
        log.debug("Matched package by name + identifier!")
        result = full_matches[0]
      } else if (full_matches.size() == 0 && name_candidates.size() == 1) {
        result = name_candidates[0]
        log.debug("Found a single match by name!")
      } else {
        log.warn("Found multiple possible matches for package! Aborting..")
        return result
      }
    } else if (!result && name_candidates.size() == 1) {
      log.debug("Matched package by name!")
      result = name_candidates[0]
    } else if (result && result.name != packageHeaderDTO.name) {
      def current_name = result.name

      changed |= ClassUtils.setStringIfDifferent(result, 'name', packageHeaderDTO.name)

      if (!result.variantNames.find { it.variantName == current_name }) {
        result.ensureVariantName(current_name)
      }
    }

    if (!result) {
      log.debug("Did not find a match via name, trying existing variantNames..")
      def variant_normname = GOKbTextUtils.normaliseString(packageHeaderDTO.name)
      def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

      if (variant_candidates.size() == 1) {
        result = variant_candidates[0]
        log.debug("Package matched via existing variantName.")
      }
    }

    if (!result && packageHeaderDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      packageHeaderDTO.variantNames.each {

        if (it.trim().size() > 0) {
          result = Package.findByName(it)

          if (result) {
            log.debug("Found existing package name for variantName ${it}")
          } else {

            def variant_normname = GOKbTextUtils.normaliseString(it)
            def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

            if (variant_candidates.size() == 1) {
              log.debug("Found existing package variant name for variantName ${it}")
              result = variant_candidates[0]
            }
          }
        }
      }
    }

    if (!result) {
      log.debug("No existing package matched. Creating new package..")

      result = new Package(name: packageHeaderDTO.name, normname: pkg_normname)

      created = true

      if (packageHeaderDTO.uuid && packageHeaderDTO.uuid.trim().size() > 0) {
        result.uuid = packageHeaderDTO.uuid
      }

      result.save(flush: true, failOnError: true)
    } else if (user && !user.hasRole('ROLE_SUPERUSER') && result.curatoryGroups && result.curatoryGroups?.size() > 0) {
      def cur = user.curatoryGroups?.id.intersect(result.curatoryGroups?.id)

      if (!cur) {
        log.debug("No curator!")
        return result
      }
    }

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

    // ListVerifier

    if (packageHeaderDTO.userListVerifier) {
      def looked_up_user = User.findByUsername(packageHeaderDTO.userListVerifier)
      if (looked_up_user && ((result.userListVerifier == null) || (result.userListVerifier?.id != looked_up_user?.id))) {
        result.userListVerifier = looked_up_user
        changed = true
      } else {
        log.warn("Unable to find username for list verifier ${packageHeaderDTO.userListVerifier}");
      }
    }

    // Platform

    if (packageHeaderDTO.nominalPlatform) {
      def platformDTO = [:];

      if (packageHeaderDTO.nominalPlatform instanceof String && packageHeaderDTO.nominalPlatform.trim()) {
        platformDTO['name'] = packageHeaderDTO.nominalPlatform
      } else if (packageHeaderDTO.nominalPlatform instanceof Integer) {
        platformDTO['id'] = (Long) packageHeaderDTO.nominalPlatform
      } else if (packageHeaderDTO.nominalPlatform.name && packageHeaderDTO.nominalPlatform.name.trim().size() > 0) {
        platformDTO = packageHeaderDTO.nominalPlatform
      }

      if (platformDTO) {
        def np = null

        if (platformDTO.uuid) {
          np = Platform.findByUuid(platformDTO.uuid)
        } else if (platformDTO.id) {
          np = Platform.get(platformDTO.id)
        } else if (platformDTO.name) {
          np = Platform.findByName(platformDTO.name)
        }

        if (!np && platformDTO.name) {
          np = Platform.upsertDTO(platformDTO)
        }

        if (np) {
          if (result.nominalPlatform != np) {
            result.nominalPlatform = np;
            changed = true
          } else {
            log.debug("Platform already set")
          }
        } else {
          log.warn("Unable to locate nominal platform ${packageHeaderDTO.nominalPlatform}");
        }
      } else {
        log.warn("Could not extract platform information from JSON!")
      }
    }

    // Provider

    if (packageHeaderDTO.nominalProvider) {

      def providerDTO = [:]

      if (packageHeaderDTO.nominalProvider instanceof String && packageHeaderDTO.nominalProvider.trim()) {
        providerDTO['name'] = packageHeaderDTO.nominalProvider
      } else if (packageHeaderDTO.nominalProvider.name && packageHeaderDTO.nominalProvider.name.trim()) {
        providerDTO = packageHeaderDTO.nominalProvider
      }

      log.debug("Trying to set package provider.. ${providerDTO}")
      def prov = null

      if (providerDTO?.uuid) {
        prov = Org.findByUuid(providerDTO.uuid)
      }

      if (providerDTO && !prov) {
        def norm_prov_name = KBComponent.generateNormname(providerDTO.name)

        prov = Org.findByNormname(norm_prov_name)

        if (!prov) {
          log.debug("None found by Normname ${norm_prov_name}, trying variants")
          def variant_normname = GOKbTextUtils.normaliseString(providerDTO.name)
          def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status = ?", [variant_normname, status_deleted]);

          if (candidate_orgs.size() == 1) {
            prov = candidate_orgs[0]
          } else if (candidate_orgs.size() == 0) {
            log.debug("No org match for provider ${packageHeaderDTO.nominalProvider}. Creating new org..")
            prov = new Org(name: providerDTO.name, normname: norm_prov_name, uuid: providerDTO.uuid ?: null).save(flush: true, failOnError: true);
          } else {
            log.warn("Multiple org matches for provider ${packageHeaderDTO.nominalProvider}. Skipping..");
          }
        }
      }

      if (prov) {
        if (result.provider != prov) {
          result.provider = prov;

          log.debug("Provider ${prov.name} set.")
          changed = true
        } else {
          log.debug("No provider change")
        }
      }
    } else {
      log.debug("No provider found!")
    }

    // Source

    if (packageHeaderDTO.source) {
      def src = null

      if (packageHeaderDTO.source instanceof Integer) {
        src = Source.get(packageHeaderDTO.source)
      }
      else if (packageHeaderDTO.source.id) {
        src = Source.get(packageHeaderDTO.source.id)
      }
      else if (packageHeaderDTO.source.url) {
        src = Source.findByUrl(packageHeaderDTO.source.url)
      }

      if (src && result.source != src) {
        result.source = src
        changed = true
      }
    }

    // variantNames are handled in ComponentUpdateService
    // packageHeaderDTO.variantNames?.each {
    //   if ( it.trim().size() > 0 ) {
    //     result.ensureVariantName(it)
    //     changed=true
    //   }
    // }

    // CuratoryGroups

    packageHeaderDTO.curatoryGroups?.each {
      def cg = null
      def cgname = null

      if (it instanceof Integer) {
        cg = CuratoryGroup.get(it)
      }
      else if (it instanceof String) {
        String normname = CuratoryGroup.generateNormname(it)
        cgname = it

        cg = CuratoryGroup.findByNormname(normname)
      }
      else if (it.id){
        cg = CuratoryGroup.get(it.id)
      }
      else if (it.name) {
        String normname = CuratoryGroup.generateNormname(it.name)
        cgname = it.name

        cg = CuratoryGroup.findByNormname(normname)
      }

      if (cg) {
        if (result.curatoryGroups.find { it.name == cg.name }) {
        } else {

          result.curatoryGroups.add(cg)
          changed = true;
        }
      } else if (cgname) {
        def new_cg = new CuratoryGroup(name: cgname).save(flush: true, failOnError: true)
        result.curatoryGroups.add(new_cg)
        changed = true
      }
    }

    result.save(flush:true)


    result
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
}
