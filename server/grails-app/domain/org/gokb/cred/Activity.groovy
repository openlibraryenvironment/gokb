package org.gokb.cred

class Activity {

  String activityName
  String activityData
  User owner
  Date dateCreated
  Date lastUpdated 
  RefdataValue status
  RefdataValue type

  static mapping = {
    id column:'act_id'
    activityName column:'act_name'
    activityData column:'act_data', type:'text'
    owner column:'act_owner_fk'
    status column:'act_status_fk'
  }

  static constraints = {
    activityName(nullable:false, blank:false)
    activityData(nullable:false, blank:false)
    owner(nullable:false, blank:false)
    dateCreated(nullable:true, blank:true)
    lastUpdated(nullable:true, blank:true)
  }

  transient
  getActivityAction() {
    def result = null
    if ( type != null ) {
      if ( type.value == 'TitleTransfer' ) {
        result = 'editTitleTransfer'
      }
    }
    result;
  }
}
