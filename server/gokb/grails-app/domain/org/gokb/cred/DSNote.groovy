package org.gokb.cred

import javax.persistence.Transient

class DSNote {

  DSAppliedCriterion criterion
  String note
  Date dateCreated
  Date lastUpdated
  Boolean isDeleted

  static mapping = {
    id column:'dsn_id'
    criterion column:'dsn_crit_fk'
    note column:'dsn_note_txt', type:'text'
    sort lastUpdated: "desc"
    isDeleted column:'dsn_isDeleted'
  }

  static constraints = {
    criterion(nullable:true, blank:false)
    note(nullable:true, blank:true)
    isDeleted(defaultValue: "false", nullable: true, blank:false)
  }

}
