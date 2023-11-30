package org.gokb.cred

/**
 * This class records the reltionship between any component and a curatorial group. It is used to allow users
 * to watch components - for example - "My Titles" can be used by users wanting to be alerted when
 * specific titles are added or removed from packages in general, or from packages marked as "My Packages"
 */


class CuratoryGroupWatch {

  KBComponent component
  CuratoryGroup curatoryGroup

  static hasMany = [
  ]

  static mappedBy = [
  ]

  static constraints = {
    component blank: false, nullable:false
    curatoryGroup blank: false, nullable:false
  }

  static mapping = {
    id column:'cgw_id'
    component column: 'cgw_component'
    curatoryGroup column: 'cgw_cg_fk'
  }

 
}
