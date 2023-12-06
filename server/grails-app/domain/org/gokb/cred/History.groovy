package org.gokb.cred

class History {

  String title
  String controller
  String action
  User owner
  Date activityDate = new Date()
  String actionid

  static mapping = {
    id column:'hist_id'
    title column:'hist_title', type:'text'
    controller column:'hist_controller'
    action column:'hist_action'
    actionid column:'hist_actionid'
    activityDate column:'hist_activity_date'
    owner column:'hist_owner_fk'
  }

  static constraints = {
    title(nullable:true, blank:false, maxSize:2048);
  }
}
