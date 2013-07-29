package org.gokb.cred

import javax.persistence.Transient

class Org extends KBComponent {

  RefdataValue mission

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
  ]

  //  static mappedBy = [
  //    ids: 'component',
  //  ]

  static hasMany = [
    roles: RefdataValue,
  ]

  static mapping = {
  //         id column:'org_id'
  //    version column:'org_version'
    mission column:'org_mission_fk_rv'
  //  roles joinTable: [name: 'org_role']
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
}
