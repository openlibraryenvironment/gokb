package org.gokb.cred

class KBComponentAdditionalProperty {

  KBComponent fromComponent
  AdditionalPropertyDefinition propertyDefn
  String apValue

  static mapping = {
              id column:'kbcap_id'
   fromComponent column:'kbcap_kbc_fk'
    propertyDefn column:'kbcap_apd_fk'
         apValue column:'kbcap_value'
  }


}
