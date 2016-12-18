package org.gokb.cred

import javax.persistence.Transient

class Org extends KBComponent {

  RefdataValue mission
  String homepage

  def availableActions() {
    [
      [code:'org::deprecateReplace', label:'Replace Publisher With...'],
      [code:'org::deprecateDelete', label:'Remove Publisher name from title records...'],
      // [code:'title::reconcile', label:'Title Reconcile']
    ]
  }


  static manyByCombo = [
    providedPackages  : Package,
    children    : Org,
    publishedTitles    : TitleInstance,
    issuedTitles    : TitleInstance,
    providedPlatforms  : Platform,
    brokeredPackages  : Package,
    licensedPackages  : Package,
    vendedPackages    : Package,
    offeredLicenses    : License,
    heldLicenses    : License,
    offices         : Office,
    //  ids      : Identifier
  ]

  static hasByCombo = [
    parent          :  Org,
    'previous'         :  Org,
    successor         :  Org
  ]

  static mappedByCombo = [
    providedPackages    : 'provider',
    providedPlatforms   : 'provider',
    publishedTitles      : 'publisher',
    issuedTitles    : 'issuer',
    children        : 'parent',
    successor      : 'previous',
    brokeredPackages  : 'broker',
    licensedPackages  : 'licensor',
    vendedPackages    : 'vendor',
    offeredLicenses    : 'licensor',
    heldLicenses    : 'licensee',
    offices    : 'org',
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
    mission column:'org_mission_fk_rv'
    homepage column:'org_homepage'
  }

  static constraints = {
    mission(nullable:true, blank:true)
  }

  //  @Transient
  //  def getPermissableCombos() {
  //  [
  //  ]
  //  }

  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = Org.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

  static Org lookupUsingComponentIdOrAlternate(ids) {
    def located_org = null

    switch (ids) {

      case List :

      // Assume [identifierType : "", identifierValue : "" ] format.
      // See if we can locate the item using any of the custom identifiers.
        ids.each { ci ->

          // We've already located an org for this identifier, the new identifier should be new (And therefore added to this org) or
          // resolve to this org. If it resolves to some other org, then there is a conflict and we fail!
          located_org = lookupByIO(ci.identifierType,ci.identifierValue)
          if (located_org) return located_org
        }
        break
      case Identifier :
        located_org = lookupByIO(
        ids.ns.ns,
        ids.value
        )
        break
    }
    located_org
  }

  public String getNiceName() {
    return "Organization";
  }
  
  @Transient
  static def oaiConfig = [
    id:'orgs',
    textDescription:'Organization repository for GOKb',
    query:" from Org as o where o.status.value != 'Deleted'",
    pageSize:50
  ]

  /**
   *  Render this package as OAI_dc
   */
  @Transient
  def toOaiDcXml(builder, attr) {
    builder.'dc'(attr) {
      'dc:title' (name)
    }
  }

  /**
   *  Render this package as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def publishes = getPublishedTitles()
    def issues = getIssuedTitles()
    def provides = getProvidedPackages()
    def identifiers = getIds()
    
    builder.'gokb' (attr) {
      builder.'org' (['id':(id)]) {
       
        addCoreGOKbXmlFields ( builder, attr )
        builder.'homepage' (homepage)

        if ( roles ) {
          builder.'roles' {
            roles.each { role ->
              builder.'role' (role.value)
            }
          }
        }

        if ( mission ) {
          builder.mission ( mission.value )
        }
        
        if (publishes) {
          'publishedTitles' {
            publishes.each { title ->
              builder.'title' (['id':title.id]) {
                builder.'name' (title.name)
                builder.'identifiers' {
                  title.ids?.each { tid ->
                    builder.'identifier' (['namespace':tid.namespace.value], tid.value)
                  }
                }
              }
            }
          }
        }

        if (issues) {
          'issuedTitles' {
            issues.each { title ->
              builder.'title' (['id':title.id]) {
                builder.'name' (title.name)
                builder.'identifiers' {
                  title.ids?.each { tid ->
                    builder.'identifier' (['namespace':tid.namespace.value], tid.value)
                  }
                }
              }
            }
          }
        }
        
        if (provides) {
          'providedPackages' {
            provides.each { pkg ->
              builder.'package' (['id':pkg.id]) {
                builder.'name' (pkg.name)
                builder.'identifiers' {
                  pkg.ids?.each { tid ->
                    builder.'identifier' (['namespace':tid.namespace.value], tid.value)
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
    def result=[:]
    Combo.executeUpdate("delete from Combo where toComponent.id = ?",[this.getId()]);
    Combo.executeUpdate("delete from Combo where fromComponent.id = ?",[this.getId()]);
    result
  }
}
