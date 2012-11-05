package org.gokb.cred

class Org extends KBComponent{

  String name
  String address
  String ipRange
  String sector
  String scope
  Date dateCreated
  Date lastUpdated

  // Used to generate friendly semantic URLs
  String shortcode

  Set ids = []

  static mappedBy = [ids: 'component', 
                     outgoingCombos: 'fromOrg', 
                     incomingCombos:'toOrg',
                     links: 'org' ]

  static hasMany = [ids: IdentifierOccurrence, 
                    outgoingCombos: Combo,  
                    incomingCombos:Combo,
                    links: OrgRole]

  static mapping = {
         id column:'org_id'
    version column:'org_version'
       name column:'org_name', index:'org_name_idx'
    address column:'org_address'
    ipRange column:'org_ip_range'
  shortcode column:'org_shortcode'
      scope column:'org_scope'
  }

  static constraints = {
    address(nullable:true, blank:true,maxSize:256);
    ipRange(nullable:true, blank:true, maxSize:1024);
    sector(nullable:true, blank:true, maxSize:128);
    shortcode(nullable:true, blank:true, maxSize:128);
    scope(nullable:true, blank:true, maxSize:128);
  }

  def beforeInsert() {
    if ( !shortcode ) {
      shortcode = generateShortcode(name);
    }
  }

  def beforeUpdate() {
    if ( !shortcode ) {
      shortcode = generateShortcode(name);
    }
  }

  def generateShortcode(name) {
    def candidate = name.trim().replaceAll(" ","_")
    return incUntilUnique(candidate);
  }

  def incUntilUnique(name) {
    def result = name;
    if ( Org.findByShortcode(result) ) {
      // There is already a shortcode for that identfier
      int i = 2;
      while ( Org.findByShortcode("${name}_${i}") ) {
        i++
      }
      result = "${name}_${i}"
    }

    result;
  }

  def getIdentifierByType(idtype) {
    def result = null
    ids.each { id ->
      if ( id.identifier.ns.ns == idtype ) {
        result = id.identifier;
      }
    }
    result
  }
}
