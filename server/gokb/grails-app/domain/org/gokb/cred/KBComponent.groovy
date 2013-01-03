package org.gokb.cred

import javax.persistence.Transient

class KBComponent {

  String impId

  static mappedBy = [ids: 'component',  orgs: 'linkedComponent']
  static hasMany = [ids: IdentifierOccurrence, orgs: OrgRole]

  static mapping = {
         id column:'kbc_id'
    version column:'kbc_version'
      impId column:'kbc_imp_id', index:'kbc_imp_id_idx'

  }

  static constraints = {
    impId(nullable:true, blank:false)
  }

  @Transient
  String getIdentifierValue(idtype) {
    def result=null
    ids?.each { id ->
      if ( id.identifier?.ns?.ns == idtype )
        result = id.identifier?.value
    }
    result
  }

}
