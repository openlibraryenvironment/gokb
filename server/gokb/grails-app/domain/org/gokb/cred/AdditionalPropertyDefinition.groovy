package org.gokb.cred

class AdditionalPropertyDefinition {

  String propertyName

  static mapping = {
              id column:'apd_id'
    propertyName column:'apd_prop_name', index:'apd_prop_name_idx'
  }

}
