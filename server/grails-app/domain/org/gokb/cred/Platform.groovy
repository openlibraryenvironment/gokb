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

  static hasMany = [roles: RefdataValue]

  static hasByCombo = [
    provider: Org
  ]

  private static refdataDefaults = [
    "authentication": "Unknown"
  ]

  static manyByCombo = [
    hostedPackages: Package,
    hostedTipps   : TitleInstancePackagePlatform,
    linkedTipps   : TitleInstancePackagePlatform,
    hostedTitles  : TitleInstancePlatform,
    curatoryGroups: CuratoryGroup
  ]

  static mappedByCombo = [
    hostedPackages: 'nominalPlatform'
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
  }

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
      'curatoryGroups'
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

  def availableActions() {
    [
      [code: 'platform::replacewith', label: 'Replace platform with...', perm: 'admin'],
      [code: 'method::deleteSoft', label: 'Delete Platform', perm: 'delete'],
      [code: 'method::retire', label: 'Retire Platform (with hosted TIPPs)', perm: 'admin']
    ]
  }

  /**
   *{*    name:'name',
   *    platformUrl:'platformUrl',
   *}*/

  public void retire(context) {
    log.debug("platform::retire");
    // Call the delete method on the superClass.
    log.debug("Updating platform status to retired");
    this.status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Retired');
    this.save();

    // Delete the tipps too as a TIPP should not exist without the associated,
    // package.
    log.debug("Retiring tipps");
    def tipps = getHostedTipps()

    tipps.each { def t ->
      log.debug("deroxy ${t} ${t.class.name}");

      // SO: There are 2 deproxy methods. One in the static context that takes in an argument and one,
      // against an instance which attempts to deproxy this component. Calling deproxy(t) here will invoke the method
      // against the current package. this.deproxy(t).
      // So Package.deproxy(t) or t.deproxy() should work...
      def tipp = Package.deproxy(t)
      log.debug("Retiring tipp ${tipp.id}");
      tipp.status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Retired');
      tipp.save()
    }
  }


  @Transient
  public static def validateDTO(platformDTO) {
    def result = ['valid': true, 'errors': [:]]

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
