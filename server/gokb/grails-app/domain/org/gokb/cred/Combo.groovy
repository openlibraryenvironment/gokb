package org.gokb.cred

/**
 * @author sosguthorpe
 *
 */

import grails.util.GrailsNameUtils

class Combo {
  
  public static final String MAPPED_BY = "mappedByCombo"
  public static final String HAS = "hasByCombo"
  public static final String MANY = "manyByCombo"
  

  RefdataValue status
  RefdataValue type

  // Participant 1 - One of these
  KBComponent fromComponent

  // Participant 2 - One of these
  KBComponent toComponent

  static mapping = {
                id column:'combo_id'            , index:'combo_id_idx'
           version column:'combo_version'
            status column:'combo_status_rv_fk'  , index:'combo_status_rv_idx'
              type column:'combo_type_rv_fk'    , index:'combo_type_rv_idx'
     fromComponent column:'combo_from_fk'       , index:'combo_from_idx'
       toComponent column:'combo_to_fk'         , index:'combo_to_idx'
  }

  static constraints = {
    status(nullable:true, blank:false)
    type(nullable:true, blank:false)
    fromComponent(nullable:true, blank:false)
    toComponent(nullable:true, blank:false)
  }
}
