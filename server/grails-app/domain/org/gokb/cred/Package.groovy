package org.gokb.cred

import com.k_int.ClassUtils
import grails.gorm.transactions.Transactional
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender

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
  Date lastCachedDate
  String descriptionURL

  private static refdataDefaults = [
    "scope"      : "Front File",
    "listStatus" : "In Progress",
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
      'curatoryGroups',
      'subjects'
    ]
  ]

  static def refdataFind(params) {
    def result = [];
    def status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
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
  public getTitles(Boolean onlyCurrent = true, Integer max = 10, Integer offset = 0) {
    def all_titles = null
    log.debug("getTitles :: current ${onlyCurrent} - max ${max} - offset ${offset}")

    if (this.id) {
      if (onlyCurrent) {
        def refdata_current = RefdataCategory.lookup('KBComponent.Status', 'Current')

        all_titles = TitleInstance.executeQuery('''select distinct title
          from TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=:pkg
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title
            and tipp.status = :stipp
            and title.status = :stitle'''
          , [pkg: this, stipp: refdata_current, stitle: refdata_current], [max: max, offset: offset])
      }
      else {
        all_titles = TitleInstance.executeQuery('''select distinct title
          from TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=:pkg
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title'''
          , [pkg: this], [max: max, offset: offset])
      }
    }

    return all_titles
  }

  @Transient
  public getCurrentTitleCount() {
    def refdata_current = RefdataCategory.lookup('KBComponent.Status', 'Current');

    int result = TitleInstance.executeQuery('''select count(distinct title.id)
      from TitleInstance as title,
        Combo as pkgCombo,
        Combo as titleCombo,
        TitleInstancePackagePlatform as tipp
      where pkgCombo.toComponent=tipp
        and pkgCombo.fromComponent=:pkg
        and titleCombo.toComponent=tipp
        and titleCombo.fromComponent=title
        and tipp.status = :stipp
        and title.status = :stitle'''
      , [pkg: this, stipp: refdata_current, stitle: refdata_current])[0]

    result
  }

  @Transient
  public getCurrentTippCount() {
    def refdata_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')

    int result = Combo.executeQuery("select count(c.id) from Combo as c where c.fromComponent = :pkg and c.type = :ct and c.toComponent.status = :sc"
      , [pkg: this, ct: combo_tipps, sc: refdata_current])[0]

    result
  }

  @Transient
  public int getTippCountForStatus(status) {
    def refdata_status = RefdataCategory.lookup('KBComponent.Status', status)
    def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')

    int result = Combo.executeQuery("select count(c.id) from Combo as c where c.fromComponent = :pkg and c.type = :ct and c.toComponent.status = :sc"
            , [pkg: this, ct: combo_tipps, sc: refdata_status])[0]

    result
  }

  @Transient
  public getReviews(boolean onlyOpen = true, boolean onlyCurrent = false, int max = 0, int offset = 0) {
    def qry = '''select rr from ReviewRequest as rr,
            TitleInstance as title,
            Combo as pkgCombo,
            Combo as titleCombo,
            TitleInstancePackagePlatform as tipp
          where pkgCombo.toComponent=tipp
            and pkgCombo.fromComponent=:pkg
            and titleCombo.toComponent=tipp
            and titleCombo.fromComponent=title
            and rr.componentToReview = title'''

    def qry_params = [
      pkg: this
    ]

    if (onlyOpen) {
      def refdata_open = RefdataCategory.lookup('ReviewRequest.Status', 'Open')
      qry_params.rs = refdata_open
      qry = qry + ' and rr.status = :rs'
    }

    if (onlyCurrent) {
      def refdata_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      qry_params.stipp = refdata_current
      qry = qry + ' and tipp.status = :stipp'
    }

    def all_rrs = ReviewRequest.executeQuery(qry, qry_params, [max: max, offset: offset])

    log.debug("Got ${all_rrs.size()} indirect reviews")

    return all_rrs
  }

  public void deleteSoft() {
    def deleted_status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    this.status = deleted_status
    this.save()

    // Delete the tipps too as a TIPP should not exist without the associated,
    // package.

    setRemainingTippsStatus(deleted_status)
  }


  public void retire() {
    log.debug("package::retire")
    // Call the delete method on the superClass.
    log.debug("Updating package status to retired")
    RefdataValue retired_status = RefdataCategory.lookup('KBComponent.Status', 'Retired')

    this.status = retired_status
    this.save()

    // Retire the tipps too as a TIPP should not exist without the associated,
    // package.

    setRemainingTippsStatus(retired_status)
  }

  public void retireAt(Date date) {
    log.debug("package::retireAt (${date})")
    RefdataValue retired_status = RefdataCategory.lookup('KBComponent.Status', 'Retired')

    this.status = retired_status
    this.save()

    setRemainingTippsStatus(retired_status, date)
  }

  private void setRemainingTippsStatus(new_status, Date date = new Date()) {
    log.debug("Setting active TIPPs to ${new_status.value} ..")
    RefdataValue current_status = RefdataCategory.lookup('KBComponent.Status', 'Current')
    RefdataValue expected_status = RefdataCategory.lookup('KBComponent.Status', 'Expected')
    RefdataValue rr_open = RefdataCategory.lookup('ReviewRequest.Status', 'Open')
    RefdataValue rr_closed = RefdataCategory.lookup('ReviewRequest.Status', 'Closed')
    RefdataValue combo_type = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')

    def qry_params = [
      ret: new_status,
      sce: [expected_status, current_status],
      comment: "Status set to ${new_status.value} due to package change!",
      pkg: this.id,
      ctype: combo_type,
      now: new Date(),
      rdate: date
    ]

    if (new_status.value == 'Deleted') {
      qry_params.sce << RefdataCategory.lookup('KBComponent.Status', 'Retired')
    }

    def qry = '''update TitleInstancePackagePlatform as t
                  set t.status = :ret,
                  t.lastUpdateComment = :comment,
                  t.lastUpdated = :now,
                  t.accessEndDate = :rdate
                  where t.status in :sce
                  and exists (
                    select 1 from Combo
                    where fromComponent.id = :pkg
                    and toComponent.id = t.id
                    and type = :ctype
                  )'''

    def rr_qry = '''update ReviewRequest as rr
                    set rr.status = :closed,
                    rr.lastUpdated = :now
                    where rr.status = :open
                    and exists (
                      select 1 from Combo
                      where fromComponent.id = :pkg
                      and toComponent.id = rr.componentToReview.id
                      and type = :ctype
                    )'''

    def params_rr = [
      closed: rr_closed,
      open: rr_open,
      pkg: this.id,
      ctype: combo_type,
      now: new Date()
    ]

    TitleInstancePackagePlatform.executeUpdate(qry, qry_params)
    ReviewRequest.executeUpdate(rr_qry, params_rr)
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
    pageSize       : 3,
    uriPath        : '/package'
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

    def identifier_prefix = "uri://gokb/${grailsApplication.config.getProperty('sysid')}/title/"

    def refdata_package_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
    def refdata_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    String tipp_hql = "from TitleInstancePackagePlatform as tipp where exists (select 1 from Combo where fromComponent = :pkg and toComponent = tipp and type = :ctype)"
    def tipp_hql_params = [pkg: this, ctype: refdata_package_tipps]

    // Get the tipps manually rather than iterating over the collection - For better management
    def tipps_count = this.status != refdata_deleted ? TitleInstancePackagePlatform.executeQuery("select count(tipp.id) " + tipp_hql, tipp_hql_params, [readOnly: true])[0] : 0
    log.debug("Query complete...");

    builder.'gokb'(attr) {
      builder.'package'(['id': (id), 'uuid': (uuid)]) {
        addCoreGOKbXmlFields(builder, attr)

        builder.'scope'(scope?.value)
        builder.'listStatus'(listStatus?.value)
        builder.'breakable'(breakable?.value)
        builder.'consistent'(consistent?.value)
        builder.'fixed'(fixed?.value)
        builder.'paymentType'(paymentType?.value)
        builder.'global'(global?.value)
        builder.'globalNote'(globalNote)
        builder.'contentType'(contentType?.value)

        if (nominalPlatform) {
          builder.'nominalPlatform'([id: nominalPlatform.id, uuid: nominalPlatform.uuid]) {
            builder.'primaryUrl'(nominalPlatform.primaryUrl)
            builder.'name'(nominalPlatform.name)
          }
        }

        if (provider) {
          builder.'nominalProvider'([id: provider.id, uuid: provider.uuid]) {
            builder.'name'(provider.name)
          }
        }

        builder.'listVerifiedDate'(listVerifiedDate ? dateFormatService.formatIsoTimestamp(listVerifiedDate) : null)

        builder.'curatoryGroups' {
          curatoryGroups.each { cg ->
            builder.'group' {
              builder.'name'(cg.name)
            }
          }
        }

        builder.'dateCreated'(dateFormatService.formatIsoTimestamp(dateCreated))
        'TIPPs'(count: tipps_count) {
          int offset = 0
          while (offset < tipps_count) {
            log.debug("Fetching TIPPs batch ${offset}/${tipps_count}")
            TitleInstancePackagePlatform[] tipps = TitleInstancePackagePlatform.executeQuery(tipp_hql + " order by tipp.id", tipp_hql_params, [readOnly: true, max: 50, offset: offset])
            log.debug("fetch complete ..")
            offset += 50
            tipps.each { TitleInstancePackagePlatform tipp ->
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
                  def title = TitleInstance.deproxy(tipp.title)

                  builder.'title'(['id': title.id, 'uuid': title.uuid]) {
                    builder.'name'(title.name?.trim())
                    builder.'type'(getTitleClass(title.id))
                    builder.'status'(title.status?.value)
                    builder.'identifiers' {
                      title.activeIdInfo.each { tid ->
                        builder.'identifier'(tid)
                      }
                    }
                  }
                }
                else {
                  builder.'title'()
                }
                builder.'identifiers' {
                  tipp.activeIdInfo.each { tid ->
                    builder.'identifier'(tid)
                  }
                }
                'platform'([id: tipp.hostPlatform.id, 'uuid': tipp.hostPlatform.uuid]) {
                  'primaryUrl'(tipp.hostPlatform.primaryUrl?.trim())
                  'name'(tipp.hostPlatform.name?.trim())
                }
                'access'(
                  start: tipp.accessStartDate ? dateFormatService.formatIsoTimestamp(tipp.accessStartDate) : null,
                  end: tipp.accessEndDate ? dateFormatService.formatIsoTimestamp(tipp.accessEndDate) : null
                )
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

    log.debug("toGoKBXml complete...")
  }

  @Transient
  private static getTitleClass(Long title_id) {
    def result = KBComponent.get(title_id)?.class.getSimpleName()

    result
  }

  @Transient
  private static getCoverageStatements(Long tipp_id) {
    def result = TIPPCoverageStatement.executeQuery("from TIPPCoverageStatement as tcs where tcs.owner.id = :tipp", ['tipp': tipp_id], [readOnly: true])
    result
  }

  @Transient
  public getRecentActivity(Integer count, Integer offset = 0) {
    def result = []

    if (this.id) {
      RefdataValue status_deleted = RefdataCategory.lookup(super.RD_STATUS, super.STATUS_DELETED)
      def changes = TitleInstancePackagePlatform.executeQuery('select tipp from TitleInstancePackagePlatform as tipp, Combo as c ' +
        'where c.fromComponent= :pkg and c.toComponent=tipp order by tipp.lastUpdated DESC',
        [pkg: this], [max: count, offset: offset])

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

      result.sort { it[1] }
      result = result.reverse()
    }

    return result;
  }

  def beforeUpdate() {
    def deleted_status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    if (this.isDirty('name')) {
      this.shortcode = generateShortcode(this.name)
      generateNormname()
      generateComponentHash()
    }

    if (this.isDirty('status') && this.status == deleted_status) {
      if (this.source && this.source.name == this.name) {
        this.source.status = deleted_status
      }
    }

    def user = springSecurityService?.currentUser
    if (user != null) {
      this.lastUpdatedBy = user
    }
  }

  public void addCuratoryGroupIfNotPresent(String cgname) {
    boolean add_needed = true;
    curatoryGroups.each { cgtest ->
      if (cgtest.name.equalsIgnoreCase(cgname))
        add_needed = false;
    }

    if (add_needed) {
      def cg = CuratoryGroup.findByName(cgname) ?: new CuratoryGroup(name: cgname).save(flush: true, failOnError: true)
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
      def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      def pkg_normname = GOKbTextUtils.cleanTitleString(packageHeaderDTO.name)

      def name_candidates = Package.findAllByNameIlikeAndStatusNotEqual(pkg_normname, status_deleted)
      def full_matches = []

      if (packageHeaderDTO.uuid) {
        result.match = Package.findByUuid(packageHeaderDTO.uuid) ? true : false
      }

      if (!result.match && name_candidates.size() == 1) {
        result.match = true
      }

      if (!result.match) {
        def variant_normname = GOKbTextUtils.normaliseString(packageHeaderDTO.name)
        def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = :nvn and p.status <> :sd ", [nvn: variant_normname, sd: status_deleted])

        if (variant_candidates.size() == 1) {
          result.match = true
          log.debug("Package matched via existing variantName.")
        }
      }

      if (!result.match) {
        log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
        packageHeaderDTO.variantNames.each {
          def vname = null

          if (it instanceof String) {
            if (it.trim()) {
              vname = it.trim()
            }
          } else if (it instanceof Map) {
            if (it.variantName.trim()) {
              vname = it.variantName.trim()
            }
          }

          if (vname) {
            def var_pkg = Package.findByName(vname)

            if (var_pkg) {
              log.debug("Found existing package name for variantName ${vname}")
            }
            else {

              def variant_normname = GOKbTextUtils.normaliseString(vname)
              def variant_candidates = Package.executeQuery("select distinct p from Package as p join p.variantNames as v where v.normVariantName = :nvn and p.status <> :sd ", [nvn: variant_normname, sd: status_deleted])

              if (variant_candidates.size() == 1) {
                log.debug("Found existing package variant name for variantName ${vname}")
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
