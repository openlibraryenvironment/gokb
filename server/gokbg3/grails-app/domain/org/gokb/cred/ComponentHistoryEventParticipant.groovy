package org.gokb.cred

class ComponentHistoryEventParticipant {

  def ComponentHistoryEvent event
  def KBComponent participant
  def String participantRole // in/out
  
  static belongsTo = [ event:ComponentHistoryEvent ]

  def afterInsert() {
    participant.lastSeen = new Date().getTime()
  }
}
