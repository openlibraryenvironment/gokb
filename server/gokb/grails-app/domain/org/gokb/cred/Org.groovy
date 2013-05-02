package org.gokb.cred

import javax.persistence.Transient

class Org extends KBComponent {

  String address
  String ipRange
  String sector
  String scope
  Date dateCreated
  Date lastUpdated

  static manyByCombo = [
	providedPackages	: Package,
	children			: Org,
	publishedTitles		: TitleInstance,
  ]

  static hasByCombo = [
	parent      		:  Org,
  ]

  static mappedByCombo = [
	providedPackages    : 'provider',
	publishedTitles	    : 'publisher',
	children    		: 'parent',
  ]

  //  static mappedBy = [
  //    ids: 'component',
  //  ]

  static hasMany = [
	//    ids: IdentifierOccurrence,
	roles: RefdataValue,
  ]

  static mapping = {
	//         id column:'org_id'
	//    version column:'org_version'
	address column:'org_address'
	ipRange column:'org_ip_range'
	scope column:'org_scope'
  }

  static constraints = {
	address(nullable:true, blank:true,maxSize:256);
	ipRange(nullable:true, blank:true, maxSize:1024);
	sector(nullable:true, blank:true, maxSize:128);
	shortcode(nullable:true, blank:true, maxSize:128);
	scope(nullable:true, blank:true, maxSize:128);
  }

  @Transient
  def getPermissableCombos() {
	[
	]
  }

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
