package org.gokb.cred

class KBComponentAdditionalProperty {

  AdditionalPropertyDefinition propertyDefn
  String apValue

  static belongsTo = [ fromComponent:KBComponent ]

  static mapping = {
              id column:'kbcap_id'
   fromComponent column:'kbcap_kbc_fk'
    propertyDefn column:'kbcap_apd_fk'
         apValue column:'kbcap_value', type:'text'
  }


}
