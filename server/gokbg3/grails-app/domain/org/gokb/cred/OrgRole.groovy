package org.gokb.cred

class OrgRole {

//  static belongsTo = [
//    org:Org
//  ]

  RefdataValue roleType
//  KBComponent linkedComponent

  static mapping = {
                 id column:'or_id'
            version column:'or_version'
                org column:'or_org_fk', index:'or_org_rt_idx'
           roleType column:'or_roletype_fk', index:'or_org_rt_idx'
//    linkedComponent column:'or_component_fk'
  }

  static constraints = {
    roleType(nullable:true, blank:false)
//    linkedComponent(nullable:false, blank:false)
  }
}
