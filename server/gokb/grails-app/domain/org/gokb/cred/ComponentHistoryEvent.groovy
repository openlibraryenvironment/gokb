package org.gokb.cred

class ComponentHistoryEvent {

  static auditable = true
  
  Date eventDate
  Set participants
  // Timestamps
  Date dateCreated
  Date lastUpdated

  static hasMany = [ participants:ComponentHistoryEventParticipant ]
  static mappedBy = [ participants:'event' ]

  static constraints = {
    dateCreated(nullable:true, blank:true)
    lastUpdated(nullable:true, blank:true)
  }

}
