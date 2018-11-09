package org.gokb.cred

class Note {
  String ownerClass
  Long ownerId
  String note
  RefdataValue locale
  User creator
  Date dateCreated
  Date lastUpdated 

  static hasMany = [
    mentions:NoteMention
  ]

  static mappedBy = [
    mentions:'owner'
  ]

  static mapping = {
    id column:'note_id'
    note column:'note_txt', type:'text'
  }

  static constraints = {
    ownerClass(nullable:false, blank:false)
    ownerId(nullable:false, blank:false)
    note(nullable:false, blank:false)
    creator(nullable:false, blank:false)
    dateCreated(nullable:true, blank:true)
    lastUpdated(nullable:true, blank:true)
  }
}
