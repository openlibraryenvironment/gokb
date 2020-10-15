package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import com.k_int.ClassUtils

class Org extends KBComponent {

  RefdataValue mission
  String homepage
  IdentifierNamespace titleNamespace
  IdentifierNamespace packageNamespace

  def availableActions() {
    [
      [code: 'org::deprecateReplace', label: 'Replace Publisher With...'],
      [code: 'org::deprecateDelete', label: 'Remove Publisher name from title records...'],
      [code: 'method::deleteSoft', label: 'Delete Org', perm: 'delete'],
      [code: 'method::retire', label: 'Retire Org', perm: 'admin'],
      [code: 'method::setActive', label: 'Set Current']
    ]
  }


  static manyByCombo = [
    providedPackages : Package,
    children         : Org,
    'previous'       : Org,
    ownedImprints    : Imprint,
    curatoryGroups   : CuratoryGroup,
    publishedTitles  : TitleInstance,
    issuedTitles     : TitleInstance,
    providedPlatforms: Platform,
    brokeredPackages : Package,
    licensedPackages : Package,
    vendedPackages   : Package,
    offeredLicenses  : License,
    heldLicenses     : License,
    offices          : Office,
    //  ids      : Identifier
  ]

  static hasByCombo = [
    parent   : Org,
    successor: Org,
    imprint  : Imprint
  ]

  static mappedByCombo = [
    providedPackages : 'provider',
    providedPlatforms: 'provider',
    publishedTitles  : 'publisher',
    issuedTitles     : 'issuer',
    children         : 'parent',
    successor        : 'previous',
    brokeredPackages : 'broker',
    licensedPackages : 'licensor',
    vendedPackages   : 'vendor',
    offeredLicenses  : 'licensor',
    heldLicenses     : 'licensee',
    offices          : 'org',
  ]

  //  static mappedBy = [
  //    ids: 'component',
  //  ]

  static hasMany = [
    roles: RefdataValue,
  ]

  static mapping = {
    // From TitleInstance
    includes KBComponent.mapping
    mission column: 'org_mission_fk_rv'
    homepage column: 'org_homepage'
  }

  static constraints = {
    mission(nullable: true, blank: true)
    homepage(nullable: true, blank: true, url: true)
    name(validator: { val, obj ->
      if (obj.hasChanged('name')) {
        if (val && val.trim()) {
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def dupes = Org.findAllByNameIlikeAndStatusNotEqual(val, status_deleted);
          if (dupes?.size() > 0 && dupes.any { it != obj }) {
            return ['notUnique']
          }
        } else {
          return ['notNull']
        }
      }
    })
    titleNamespace(nullable: true)
    packageNamespace(nullable: true)
  }

  static jsonMapping = [
    'ignore'       : [
    ],
    'es'           : [
    ],
    'defaultLinks' : [

    ],
    'defaultEmbeds': [
      'ids',
      'variantNames',
      'curatoryGroups',
      'providedPlatforms'
    ]
  ]

  //  @Transient
  //  def getPermissableCombos() {
  //  [
  //  ]
  //  }

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
    pageSize       : 10
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
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def publishes = getPublishedTitles()
    def issues = getIssuedTitles()
    def provides = getProvidedPackages()
    def platforms = getProvidedPlatforms()
    def offices = getOffices()
    def identifiers = getIds()

    builder.'gokb'(attr) {
      builder.'org'(['id': (id), 'uuid': (uuid)]) {

        addCoreGOKbXmlFields(builder, attr)
        builder.'homepage'(homepage)
        builder.'titleNamespace'('namespaceName': titleNamespace.name, 'value': titleNamespace.value, 'id': titleNamespace.id)
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

//         if (publishes) {
//           'publishedTitles' {
//             publishes.each { title ->
//               builder.'title' (['id':title.id]) {
//                 builder.'name' (title.name)
//                 builder.'identifiers' {
//                   title.ids?.each { tid ->
//                     builder.'identifier' (['namespace':tid.namespace?.value], tid.value)
//                   }
//                 }
//               }
//             }
//           }
//         }
//
//         if (issues) {
//           'issuedTitles' {
//             issues.each { title ->
//               builder.'title' (['id':title.id]) {
//                 builder.'name' (title.name)
//                 builder.'identifiers' {
//                   title.ids?.each { tid ->
//                     builder.'identifier' (['namespace':tid.namespace?.value], tid.value)
//                   }
//                 }
//               }
//             }
//           }
//         }

        if (provides) {
          'providedPackages' {
            provides.each { pkg ->
              builder.'package'(['id': pkg.id, 'uuid': pkg.uuid]) {
                builder.'name'(pkg.name)
                builder.'identifiers' {
                  pkg.ids?.each { tid ->
                    builder.'identifier'(['namespace': tid.namespace?.value, 'namespaceName': tid.namespace?.name, 'value': tid.value, 'datatype': tid.namespace.datatype?.value])
                  }
                }
                builder.'curatoryGroups' {
                  pkg.curatoryGroups?.each { cg ->
                    builder.'group'(['id': cg.id]) {
                      builder.'name'(cg.name)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def deprecateDelete(context) {
    log.debug("deprecateDelete");
    def result = [:]
    Combo.executeUpdate("delete from Combo where toComponent.id = ?", [this.getId()]);
    Combo.executeUpdate("delete from Combo where fromComponent.id = ?", [this.getId()]);
    result
  }

  @Transient
  public static Org upsertDTO(orgDTO, def user = null) {
    log.info("Upsert package with header ${orgDTO}");
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def org_normname = Org.generateNormname(orgDTO.name)

    log.debug("Checking by normname ${org_normname} ..")
    def name_candidates = Org.executeQuery("from Org as p where p.normname = ? and p.status <> ?", [org_normname, status_deleted])
    def full_matches = []
    def created = false
    def result = orgDTO.uuid ? Org.findByUuid(orgDTO.uuid) : null;
    boolean changed = false;

    if (!result && name_candidates.size() > 0 && orgDTO.identifiers?.size() > 0) {
      log.debug("Got ${name_candidates.size()} matches by name. Checking against identifiers!")
      name_candidates.each { mp ->
        if (mp.ids.size() > 0) {
          def id_match = false;

          orgDTO.identifiers.each { rid ->

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
        log.debug("Matched org by name + identifier!")
        result = full_matches[0]
      } else if (full_matches.size() == 0 && name_candidates.size() == 1) {
        result = name_candidates[0]
        log.debug("Found a single match by name!")
      } else {
        log.warn("Found multiple possible matches for org! Aborting..")
        return result
      }
    } else if (!result && name_candidates.size() == 1) {
      log.debug("Matched org by name!")
      result = name_candidates[0]
    } else if (result && result.name != orgDTO.name) {
      def current_name = result.name
      changed |= ClassUtils.setStringIfDifferent(result, 'name', orgDTO.name)

      if (!result.variantNames.find { it.variantName == current_name }) {
        def new_variant = new KBComponentVariantName(owner: result, variantName: current_name).save(flush: true, failOnError: true)
      }
    }

    if (!result) {
      log.debug("Did not find a match via name, trying existing variantNames..")
      def variant_normname = GOKbTextUtils.normaliseString(orgDTO.name)
      def variant_candidates = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

      if (variant_candidates.size() == 1) {
        result = variant_candidates[0]
        log.debug("Package matched via existing variantName.")
      }
    }

    if (!result && orgDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      orgDTO.variantNames.each {

        if (it.trim().size() > 0) {
          result = Org.findByName(it)

          if (result) {
            log.debug("Found existing package name for variantName ${it}")
          } else {

            def variant_normname = GOKbTextUtils.normaliseString(it)
            def variant_candidates = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ", [variant_normname, status_deleted]);

            if (variant_candidates.size() == 1) {
              log.debug("Found existing Org variant name for variantName ${it}")
              result = variant_candidates[0]
            }
          }
        }
      }
    }

    if (!result) {
      log.debug("No existing Org matched. Creating new Org..")

      result = new Org(name: orgDTO.name, normname: org_normname)

      created = true

      if (orgDTO.uuid && orgDTO.uuid.trim().size() > 0) {
        result.uuid = orgDTO.uuid
      }

      result.save(flush: true, failOnError: true)
    } else if (user && !user.hasRole('ROLE_SUPERUSER') && result.curatoryGroups && result.curatoryGroups?.size() > 0) {
      def cur = user.curatoryGroups?.id.intersect(result.curatoryGroups?.id)

      if (!cur) {
        log.debug("No curator!")
        return result
      }
    }
  }
}
