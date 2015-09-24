package org.gokb.cred

import javax.persistence.Transient

class ComponentSubject {

  KBComponent component
  Subject subject

  static hasMany = [
  ]

  static mappedBy = [
  ]

  static mapping = {
    component column:'cs_comp_fk'
    subject column:'cs_subj_fk'
  }

  static constraints = {
    component(nullable:false, blank:false)
    subject(nullable:false, blank:false)
  }

}
