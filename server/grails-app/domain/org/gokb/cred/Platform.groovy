package org.gokb.cred

import javax.persistence.Transient
import groovy.util.logging.*
import org.gokb.GOKbTextUtils

@Slf4j
class Platform extends KBComponent {

  String primaryUrl
  RefdataValue authentication
  RefdataValue software
  RefdataValue service
  RefdataValue ipAuthentication
  RefdataValue shibbolethAuthentication
  RefdataValue passwordAuthentication
  Org provider

  Set roles = []

  static hasMany = [
    roles: RefdataValue
  ]

  static mappedBy = [
    linkedCuratoryGroups: 'platform'
  ]

  static mapping = {
    includes KBComponent.mapping
    primaryUrl column: 'plat_primary_url', index: 'platform_primary_url_idx'
    authentication column: 'plat_authentication_fk_rv'
    software column: 'plat_sw_fk_rv'
    service column: 'plat_svc_fk_rv'
    ipAuthentication column: 'plat_auth_by_ip_fk_rv'
    shibbolethAuthentication column: 'plat_auth_by_shib_fk_rv'
    passwordAuthentication column: 'plat_auth_by_pass_fk_rv'
    provider column: 'plat_provider_fk'
  }

  static constraints = {
    primaryUrl(url: true, nullable: true, blank: false)
    authentication(nullable: true, blank: false)
    software(nullable: true, blank: false)
    service(nullable: true, blank: false)
    ipAuthentication(nullable: true, blank: false)
    shibbolethAuthentication(nullable: true, blank: false)
    passwordAuthentication(nullable: true, blank: false)
    name(validator: { val, obj ->
      if (obj.hasChanged('name')) {
        if (val && val.trim()) {
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def dupes = Platform.findAllByNameIlikeAndStatusNotEqual(val, status_deleted)

          if (dupes?.size() > 0 && dupes.any {it != obj}) {
            return ['notUnique']
          }
        } else {
          return ['notNull']
        }
      }
    })
    provider(nullable: true)
  }

  private static refdataDefaults = [
    "authentication": "Unknown"
  ]

  public static final String restPath = "/platforms"

  static jsonMapping = [
    'ignore'       : [
      'service',
      'software'
    ],
    'es'           : [
      'providerUuid': "provider.uuid",
      'providerName': "provider.name",
      'provider'    : "provider.id",
      'cpname'      : "provider.name"
    ],
    'defaultLinks' : [
      'provider',
      'curatoryGroups'
    ],
    'defaultEmbeds': [
      'ids',
      'variantNames',
      'curatoryGroups',
      'comments'
    ]
  ]

  @Transient
  static def oaiConfig = [
    id             : 'platforms',
    textDescription: 'Platform repository for GOKb',
    query          : " from Platform as o ",
    curators       : 'Platform.CuratoryGroups',
    statusFilter   : ["Deleted"]
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
    def identifiers = getIds()

    builder.'gokb'(attr) {
      builder.'platform'(['id': (id), 'uuid': (uuid)]) {

        addCoreGOKbXmlFields(builder, attr)

        builder.'primaryUrl'(primaryUrl)
        builder.'authentication'(authentication?.value)
        builder.'software'(software?.value)
        builder.'service'(service?.value)

        if (ipAuthentication) builder.'ipAuthentication'(ipAuthentication.value)
        if (shibbolethAuthentication) builder.'shibbolethAuthentication'(shibbolethAuthentication.value)
        if (passwordAuthentication) builder.'passwordAuthentication'(passwordAuthentication.value)

        if (provider) {
          builder.'provider'([id: provider.id, uuid: (provider.uuid)]) {
            builder.'name'(provider.name)
          }
        }
        if (roles) {
          builder.'roles' {
            roles.each { role ->
              builder.'role'(role.value)
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
    def status_deleted = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    def status_filter = null

    if (params.filter1) {
      status_filter = RefdataCategory.lookup('KBComponent.Status', params.filter1)
    }

    def ql = null;
    ql = Platform.findAllByNameIlikeAndStatusNotEqual("${params.q}%", status_deleted, params)

    if (ql) {
      ql.each { t ->
        if (!status_filter || t.status == status_filter) {
          result.add([id: "${t.class.name}:${t.id}", text: "${t.name}", status: "${t.status?.value}"])
        }
      }
    }

    result
  }

  public int getProvidedPackagesCount() {
    RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    int result = Package.executeQuery("select count(id) from Package where status != :sd and nominalPlatform = :plt", [plt: this, sd: status_deleted])[0]

    return result
  }

  public int getHostedTippsCount() {
    RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    int result = TitleInstancePackagePlatform.executeQuery("select count(id) from TitleInstancePackagePlatform where status != :sd and hostPlatform = :plt", [plt: this, sd: status_deleted])[0]

    return result
  }

  /*
  * Methods for replicating dynamic handling of curatoryGroups
  */

  public List getCuratoryGroups() {
    List result = CuratoryGroup.executeQuery('''from CuratoryGroup as c
                                                where exists (
                                                  select 1 from PlatformCuratoryGroup
                                                  where platform = :comp
                                                  and group = c
                                                )''', [comp: this])

    result
  }

  public Platform addToCuratoryGroups(CuratoryGroup group) {
    PlatformCuratoryGroup dupe = PlatformCuratoryGroup.findByPlatformAndGroup(this, group)

    if (!dupe) {
      if (linkedCurators == null) {
        linkedCurators = []
      }

      PlatformCuratoryGroup new_obj = new PlatformCuratoryGroup(group: group, platform: this)
      this.addToLinkedCurators(new_obj)
      new_obj.save(flush: true)
    }

    return this
  }

  public Platform removeFromCuratoryGroups(CuratoryGroup group) {
    PlatformCuratoryGroup to_remove = PlatformCuratoryGroup.findByPlatformAndGroup(this, group)

    if (to_remove) {
      this.removeFromLinkedCurators(to_remove)
      to_remove.delete(flush: true)
    }

    return this
  }

  public Platform retainCuratoryGroups(List<CuratoryGroup> retain_groups) {
    boolean changed = false
    List current = getCuratoryGroups()

    retain_groups.each { rg ->
      if (!current.contains(rg)) {
        addToCuratoryGroups(rg)
        changed = true
      }
    }

    current.each { ccg ->
      if (!retain_groups.contains(ccg)) {
        removeFromCuratoryGroups(ccg)
        changed = true
      }
    }

    if (changed && update_comment) {
      this.lastUpdateComment = "Retained curatory groups: ${retain_groups}"
      this.save(flush: true)
    }

    return this
  }

  public List availableActions() {
    return [
      [code: 'platform::replacewith', label: 'Replace platform with...', perm: 'admin'],
      [code: 'method::deleteSoft', label: 'Delete Platform', perm: 'delete'],
      [code: 'method::retire', label: 'Retire Platform (with hosted TIPPs)', perm: 'admin']
    ]
  }

  public void retire() {
    log.debug("platform::retire")
    RefdataValue status_retired = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)

    this.status = status_retired
    this.save()

    // Delete the tipps too as a TIPP should not exist without the associated,
    // package.
    log.debug("platform::retire -- Retiring tipps")

    int retired_tipps_count = TitleInstancePackagePlatform.executeUpdate('''update TitleInstancePackagePlatform
                              set status = :retired,
                              lastUpdated = :now
                              where hostPlatform = :plt
                              and status = :current''', [retired: status_retired, now: new Date(), plt: this, current: status_current])

    log.debug("platform::retire -- Retired ${retired_tipps_count} TIPPs.")
  }

  /**
   *{*    name:'name',
   *    platformUrl:'platformUrl',
   *}*/

  public static Map validateDTO(platformDTO) {
    Map result = ['valid': true, 'errors': [:]]

    if (platformDTO?.name?.trim()) {
    } else {
      result.valid = false
      result.errors.name = [[message: "Platform name is missing!", baddata: (platformDTO?.name ?: null)]]
    }

    if (!result.valid) {
      log.error("platform failed validation ${platformDTO}");
    }

    result
  }
}
