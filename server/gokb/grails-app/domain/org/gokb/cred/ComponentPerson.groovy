package org.gokb.cred

import javax.persistence.Transient

class ComponentPerson {

  KBComponent component
  Person person
  RefdataValue role

  static hasMany = [
  ]

  static mappedBy = [
  ]

  static mapping = {
    component column:'cp_comp_fk'
    person column:'cp_person_fk'
    role column:'cp_role_fk'
  }

  static constraints = {
    component(nullable:false, blank:false)
    person(nullable:false, blank:false)
    role(nullable:false, blank:false)
  } 
}
