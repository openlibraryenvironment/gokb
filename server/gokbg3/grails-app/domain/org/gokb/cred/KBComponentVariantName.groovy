package org.gokb.cred

import org.gokb.GOKbTextUtils

class KBComponentVariantName {

  KBComponent owner
  RefdataValue variantType
  RefdataValue locale
  RefdataValue status

  String variantName
  String normVariantName

  static mapping = {
        id column:'cvn_id'
        version column:'cvn_version'
        owner column:'cvn_kbc_fk'
        variantName column:'cvn_variant_name'
        normVariantName column:'cvn_norm_variant_name', index:'cvn_norm_variant_name_idx'
        variantType column:'cvn_type_rv_fk'
        locale column:'cvn_locale_rv_fk'
        status column:'cvn_status_rv_fk'
  }

  static constraints = {
        variantName (nullable:false, blank:false, maxSize:2048)
        normVariantName  (nullable:true, blank:true, maxSize:2048)
        variantType (nullable:true, blank:false)
        locale (nullable:true, blank:false)
        status (nullable:true, blank:false)
  }

  String getLogEntityId() {
      "${this.class.name}:${id}"
  }

  static belongsTo = [owner: KBComponent]

  static jsonMapping = [
    'ignore': [
      'status',
      'normVariantName',
      'status',
      'owner'
    ]
  ]

  def beforeInsert() {
    // Generate the any necessary values.
    normVariantName = GOKbTextUtils.normaliseString(variantName);
  }

  def beforeUpdate() {
    normVariantName = GOKbTextUtils.normaliseString(variantName);
  }
}
