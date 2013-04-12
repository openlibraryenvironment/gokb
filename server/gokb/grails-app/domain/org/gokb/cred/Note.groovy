package org.gokb.cred

class Note {
  String ownerOid
  String note
  User creator
  Date dateCreated
  Date lastUpdated 

  static mapping = {
    note column:'note_txt', type:'text'

  }

  static constraints = {
    ownerOid(nullable:false, blank:false)
    note(nullable:false, blank:false)
    creator(nullable:false, blank:false)
    dateCreated(nullable:true, blank:true)
    lastUpdated(nullable:true, blank:true)
  }
}
