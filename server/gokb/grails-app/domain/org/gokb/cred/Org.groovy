package org.gokb.cred

class Org extends KBComponent{

  String address
  String ipRange
  String sector
  String scope
  Date dateCreated
  Date lastUpdated

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
}
