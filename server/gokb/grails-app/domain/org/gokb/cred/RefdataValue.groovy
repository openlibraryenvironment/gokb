package org.gokb.cred

class RefdataValue {

  String value
  String icon

  static belongsTo = [
    owner:RefdataCategory
  ]

  static mapping = {
         id column:'rdv_id'
    version column:'rdv_version'
      owner column:'rdv_owner', index:'rdv_entry_idx'
      value column:'rdv_value', index:'rdv_entry_idx'
       icon column:'rdv_icon'
  }

  static constraints = {
    icon(nullable:true)
  }
}
