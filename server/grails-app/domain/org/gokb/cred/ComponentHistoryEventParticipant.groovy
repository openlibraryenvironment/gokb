package org.gokb.cred

class ComponentHistoryEventParticipant {

  ComponentHistoryEvent event
  KBComponent participant
  String participantRole // in/out

  static belongsTo = [ event:ComponentHistoryEvent ]

  def afterInsert() {
    participant.lastSeen = new Date().getTime()
  }
}
