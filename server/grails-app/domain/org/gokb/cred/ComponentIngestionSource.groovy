package org.gokb.cred

import javax.persistence.Transient

class ComponentIngestionSource {

  KBComponent component
  IngestionProfile profile

  static hasMany = [
  ]

  static mappedBy = [
  ]

  static mapping = {
    component column:'cs_comp_fk'
    profile column:'cs_profile_fk'
  }

  static constraints = {
    component(nullable:false, blank:false)
    profile(nullable:false, blank:false)
  }

}
