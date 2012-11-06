package org.gokb.cred

class KBComponent {

  String impId

  static mapping = {
         id column:'kbc_id'
    version column:'kbc_version'
      impId column:'kbc_imp_id', index:'kbc_imp_id_idx'

  }

  static constraints = {
    impId(nullable:true, blank:false)
  }
}
