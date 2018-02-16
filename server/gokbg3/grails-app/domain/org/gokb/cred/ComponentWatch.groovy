package org.gokb.cred

/**
 * This class records the reltionship between any component and a user. It is used to allow users
 * to watch components - for example - "My Titles" can be used by users wanting to be alerted when
 * specific titles are added or removed from packages in general, or from packages marked as "My Packages"
 */


class ComponentWatch {

  KBComponent component
  User user

  static hasMany = [
  ]

  static mappedBy = [
  ]

  static constraints = {
    component blank: false, nullable:false
    user blank: false, nullable:false
  }

  static mapping = {
    id column:'cw_id'
    component column: 'cw_component'
    user column: 'cw_user'
  }

 
}
