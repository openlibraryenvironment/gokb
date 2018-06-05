package org.gokb.cred

/**
 * @author sosguthorpe
 *
 */


class Combo {
  
  static final String RD_STATUS = "Combo.Status"
  static final String RD_TYPE = "Combo.Type"
  static final String STATUS_ACTIVE = "Active"
  static final String STATUS_SUPERSEDED = "Superseded"

  public static final String MAPPED_BY = "mappedByCombo"
  public static final String HAS = "hasByCombo"
  public static final String MANY = "manyByCombo"

  RefdataValue status
  RefdataValue type
  
  // All Combos should have a start date.
  Date startDate = new Date()
  
  // The Combos without an end date are the "current" values.
  Date endDate

  // Participant 1 - One of these
  KBComponent fromComponent

  // Participant 2 - One of these
  KBComponent toComponent

  static mapping = {
                id column:'combo_id'
           version column:'combo_version'
     fromComponent column:'combo_from_fk'       , index:'combo_from_idx,combo_full_idx'
       toComponent column:'combo_to_fk'         , index:'combo_to_idx,combo_full_idx'
              type column:'combo_type_rv_fk'    , index:'combo_from_idx,combo_to_idx,combo_full_idx'
            status column:'combo_status_rv_fk' //  , index:'combo_from_idx,combo_to_idx'
           endDate column:'combo_end_date'
         startDate column:'combo_start_date'
  }

  static constraints = {
    status(nullable:true, blank:false)
    type(nullable:true, blank:false)
    fromComponent(nullable:false, blank:false)
    toComponent(nullable:false, blank:false)
    endDate(nullable:true, blank:false)
    startDate(nullable:true, blank:false)
  }
  
  public Date expire (Date endDate = null) {
	
	if (endDate == null) endDate = new Date ()
	
	// Expire this combo...
	setStatus (RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_SUPERSEDED))
	setEndDate(endDate)
	save()
	endDate
  }
}
