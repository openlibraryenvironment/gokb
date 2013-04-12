package org.gokb.cred

class IdentifierOccurrence {

  static belongsTo = [
    component:KBComponent,
    identifier:Identifier
  ]
  
//  public static hasByCombo = [
//    component:KBComponent,
//    identifier:Identifier
//  ]
//  
//  public static mappedByCombo = [
//    component : 'ids',
//    identifier: 'occurrences'
//  ]
  
  static mapping = {
            id column:'io_id'
    identifier column:'io_canonical_id'
     component column:'io_component_fk'
  }

  static constraints = {
  }
}
