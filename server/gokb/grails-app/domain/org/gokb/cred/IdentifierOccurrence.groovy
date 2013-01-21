package org.gokb.cred

class IdentifierOccurrence {

  static belongsTo = [
    component:KBComponent,
    identifier:Identifier
  ]

  static mapping = {
            id column:'io_id'
    identifier column:'io_canonical_id'
     component column:'io_component_fk'
  }

  static constraints = {
  }
}
