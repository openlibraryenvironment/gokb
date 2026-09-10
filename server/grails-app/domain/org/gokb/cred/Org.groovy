package org.gokb.cred

import groovy.util.logging.Slf4j

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import com.k_int.ClassUtils


@Slf4j
class Org extends KBComponent {

  RefdataValue mission
  String homepage
  IdentifierNamespace titleNamespace
  IdentifierNamespace packageNamespace
  IdentifierNamespace titleNamespaceSerial
  IdentifierNamespace titleNamespaceMonograph
  String preferredShortname
  Boolean supplyKbart
  Boolean supplyCsv
  Boolean supplyMarc
  Boolean supplyOnix
  RefdataValue preferredSupplyMethod
  String kbartHostUrl
  Boolean autoImportSupported
  Boolean kbartUrlWithDateMask
  RefdataValue kbartUpdateCycle
  Boolean kbartExtensionZdbId
  Boolean kbartExtensionEzbId
  Boolean kbartExtensionLastChanged
  Boolean kbartExtensionAccessStartDate
  Boolean kbartExtensionAccessEndDate
  Boolean kbartExtensionMedium
  Boolean kbartExtensionMonographParentCollectionTitle
  Boolean kbartExtensionSeries
  Boolean kbartExtensionSubjetArea
  Boolean kbartExtensionDoiId
  Date importInfoLastUpdated
  RefdataValue kbartScope
  RefdataValue kbartPublicationType
  Org parent
  Org successor


  Set roles = []

  def availableActions() {
    [
      [code: 'org::transferPackages', label: 'Transfer Packages to...', perm: 'admin'],
      [code: 'org::deprecateReplace', label: 'Merge into...', perm: 'delete'],
      [code: 'org::deprecateDelete', label: 'Remove all links and delete...', perm: 'delete'],
      [code: 'method::deleteSoft', label: 'Delete Org', perm: 'delete'],
      [code: 'method::retire', label: 'Retire Org', perm: 'admin'],
      [code: 'method::setActive', label: 'Set Current']
    ]
  }


  static hasMany = [
    roles: RefdataValue,
    children: Org,
    'previous': Org,
    curatoryGroups: CuratoryGroup,
    offices: Office,
    providedPlatforms: Platform,
    providedPackages: Package
  ]

  static mappedBy = [
    children: 'parent',
    offices: 'org',
    providedPlatforms: 'provider',
    providedPackages: 'provider'
  ]

  static mapping = {
    // From TitleInstance
    includes KBComponent.mapping
    mission column: 'org_mission_fk_rv'
    homepage column: 'org_homepage'
    preferredShortname column: 'org_preferred_shortname'
    parent column: 'org_parent_fk'
    successor column: 'org_successor_fk'
    roles joinTable: 'org_role'
  }

  static constraints = {
    mission(nullable: true, blank: true)
    homepage(nullable: true, blank: true)
    name(validator: { val, obj ->
      if (obj.hasChanged('name')) {
        if (val && val.trim()) {
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def dupes = Org.findAllByNameIlikeAndStatusNotEqual(val, status_deleted)
          if (dupes?.size() > 0 && dupes.any { it != obj }) {
            return ['notUnique']
          }
        }
        else {
          return ['notNull']
        }
      }
    })
    titleNamespace(nullable: true)
    titleNamespaceSerial(nullable: true)
    titleNamespaceMonograph(nullable: true)
    packageNamespace(nullable: true)
    parent(nullable: true)
    successor(nullable: true)
  }

  static jsonMapping = [
    'ignore'       : [],
    'es'           : [],
    'defaultLinks' : [],
    'defaultEmbeds': [
      'ids',
      'variantNames',
      'curatoryGroups',
      'providedPlatforms',
      'offices',
      'roles',
      'comments'
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
    ql = Org.findAllByNameIlikeAndStatusNotEqual("${params.q}%", status_deleted, params)

    if (ql) {
      ql.each { t ->
        if (!status_filter || t.status == status_filter) {
          result.add([id: "${t.class.name}:${t.id}", text: "${t.name}", status: "${t.status?.value}"])
        }
      }
    }

    result
  }

  static Org lookupUsingComponentIdOrAlternate(ids) {
    def located_org = null

    switch (ids) {

      case List:

        // Assume [identifierType : "", identifierValue : "" ] format.
        // See if we can locate the item using any of the custom identifiers.
        ids.each { ci ->

          // We've already located an org for this identifier, the new identifier should be new (And therefore added to this org) or
          // resolve to this org. If it resolves to some other org, then there is a conflict and we fail!
          located_org = lookupByIO(ci.identifierType, ci.identifierValue)
          if (located_org) return located_org
        }
        break
      case Identifier:
        located_org = lookupByIO(
            ids.ns.ns,
            ids.value
        )
        break
    }
    located_org
  }

  public int getProvidedPackagesCount() {
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)

    int result = Package.executeQuery('''select count(*) from Package as p
                                          where p.provider = :prov
                                          and status = :current''',
                                          [prov: this, current: status_current])[0]

    return result
  }

  public int getPublishedTitlesCount() {
    RefdataValue status_active = RefdataCategory.lookup(TitlePublisher.RD_STATUS, TitlePublisher.STATUS_ACTIVE)
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)

    int result = TitleInstance.executeQuery('''select count(*) from TitleInstance as ti
                                                where exists (
                                                  select 1 from TitlePublisher
                                                  where title = ti
                                                  and publisher = :pub
                                                  and status = :active
                                                )
                                                and status = :current''',
                                                [pub: this, active: status_active, current: status_current])[0]

    return result
  }

  @Override
  public String getNiceName() {
    return "Organization";
  }

  @Transient
  static def oaiConfig = [
      id             : 'orgs',
      textDescription: 'Organization repository for GOKb',
      query          : " from Org as o ",
      statusFilter   : ["Deleted"],
      pageSize       : 10,
      uriPath        : '/provider'
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

  public static final String restPath = "/orgs"

  /**
   *  Render this package as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {
    def publishes = getPublishedTitles()
    def issues = getIssuedTitles()
    def platforms = getProvidedPlatforms()
    def offices = getOffices()
    def identifiers = getIds()

    builder.'gokb'(attr) {
      builder.'org'(['id': (id), 'uuid': (uuid)]) {

        addCoreGOKbXmlFields(builder, attr)
        builder.'homepage'(homepage)
        builder.'preferredShortname'(preferredShortname)
        if (titleNamespace)
          builder.'titleNamespace'('namespaceName': titleNamespace.name, 'value': titleNamespace.value, 'id': titleNamespace.id)
        if (packageNamespace)
          builder.'packageNamespace'('namespaceName': packageNamespace.name, 'value': packageNamespace.value, 'id': packageNamespace.id)
        if (roles) {
          builder.'roles' {
            roles.each { role ->
              builder.'role'(role.value)
            }
          }
        }

        builder.'curatoryGroups' {
          curatoryGroups.each { cg ->
            builder.'group' {
              builder.'name'(cg.name)
            }
          }
        }

        if (offices) {
          builder.'offices' {
            offices.each { office ->
              builder.'office' {
                builder.'name'(office.name)
                builder.'website'(office.website)
                builder.'phoneNumber'(office.phoneNumber)
                builder.'otherDetails'(office.otherDetails)
                builder.'addressLine1'(office.addressLine1)
                builder.'addressLine2'(office.addressLine2)
                builder.'city'(office.city)
                builder.'zipPostcode'(office.zipPostcode)
                builder.'region'(office.region)
                builder.'state'(office.state)
                builder.'language'(office.language)
                builder.'function'(office.function)
                if (office.country) {
                  builder.'country'(office.country.value)
                }

                builder.curatoryGroups {
                  office.curatoryGroups.each { ocg ->
                    builder.group {
                      builder.owner(ocg.owner.username)
                      builder.name(ocg.name)
                    }
                  }
                }
              }
            }
          }
        }

        if (mission) {
          builder.'mission'(mission.value)
        }

        if (platforms) {
          'providedPlatforms' {
            platforms.each { plat ->
              builder.'platform'(['id': plat.id, 'uuid': plat.uuid]) {
                builder.'name'(plat.name)
                builder.'primaryUrl'(plat.primaryUrl)
              }
            }
          }
        }
      }
    }
  }

  public Map deprecateDelete() {
    log.debug("deprecateDelete");
    Map result = [result: 'OK']

    result.removedPubs = TitlePublisher.executeUpdate("delete from TitlePublisher where publisher = :ctx", [ctx: this])
    this.deleteSoft()

    result
  }
}
