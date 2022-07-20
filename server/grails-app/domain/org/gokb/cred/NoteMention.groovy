package org.gokb.cred

class NoteMention {
  String mentionClass
  Long mentionId

  static belongsTo = [ owner:Note ]

  static mapping = {
    id column:'nm_id'
  }

  static constraints = {
    mentionClass(nullable:false, blank:false)
    mentionId(nullable:false, blank:false)
  }
}
