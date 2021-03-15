package org.gokb.cred

import grails.plugins.orm.auditable.Auditable

class ComponentHistoryEvent implements Auditable {
  
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


  String getLogEntityId() {
      "${this.class.name}:${id}"
  }


  def afterInsert() {
    touchParticipants(lastUpdated)
  }


  def afterUpdate() {
    touchParticipants(lastUpdated)
  }


  def beforeDelete() {
    touchParticipants(new Date())
  }


  private void touchParticipants(Date timestamp){
    for (ComponentHistoryEventParticipant participant in participants){
      participant.participant.lastUpdated = timestamp
    }
  }
}
