package org.gokb.cred

class ComponentHistoryEvent {
  
  Date eventDate
  Set participants

  static hasMany = [ participants:ComponentHistoryEventParticipant ]
  static mappedBy = [ participants:'event' ]
}
