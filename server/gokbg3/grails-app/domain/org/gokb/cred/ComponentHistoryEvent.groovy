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


  def afterUpdate() {
    touchParticipants()
  }


  def beforeDelete() {
    touchParticipants()
  }


  private void touchParticipants(){
    lastSeen = new Date().getTime()
    for (ComponentHistoryEventParticipant participant in participants){
      participant.participant.lastSeen = lastSeen
    }
  }
}
