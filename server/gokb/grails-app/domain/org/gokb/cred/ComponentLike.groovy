package org.gokb.cred

class ComponentLike {

  String ownerClass
  Long ownerId
  User user

  static mapping = {
    id column:'like_id'
  }

  static constraints = {
    ownerClass(nullable:false, blank:false)
    ownerId(nullable:false, blank:false)
    user(nullable:false, blank:false)
  }
}

