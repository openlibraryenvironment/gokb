package org.gokb.cred

class IdentifierOccurrence {

  Identifier identifier

  static belongsTo = [
    component:KBComponent
  ]

  static mapping = {
            id column:'io_id'
    identifier column:'io_canonical_id'
     component column:'io_component_fk'
  }

  static constraints = {
  }
}
