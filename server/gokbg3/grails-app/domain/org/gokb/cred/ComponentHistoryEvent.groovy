package org.gokb.cred

import grails.plugins.orm.auditable.Auditable

class ComponentHistoryEvent implements Auditable {
  
  Date eventDate
  Set participants
  // Timestamps
  Date dateCreated
  Date lastUpdated
  Long lastSeen

  static hasMany = [ participants:ComponentHistoryEventParticipant ]
  static mappedBy = [ participants:'event' ]


  static constraints = {
    dateCreated(nullable:true, blank:true)
    lastUpdated(nullable:true, blank:true)
  }


  String getLogEntityId() {
      "${this.class.name}:${id}"
  }


  def afterInsert() {
    lastSeen = new Date().getTime()
    touchParticipants(lastSeen)
  }


  def afterUpdate() {
    lastSeen = new Date().getTime()
    touchParticipants(lastSeen)
  }


  def beforeDelete() {
    lastSeen = new Date().getTime()
    touchParticipants(lastSeen)
  }


  private void touchParticipants(Long lastSeen){
    for (ComponentHistoryEventParticipant participant in participants){
      participant.participant.lastSeen = lastSeen
    }
  }
}
