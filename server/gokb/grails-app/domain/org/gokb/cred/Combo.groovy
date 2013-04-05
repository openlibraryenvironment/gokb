package org.gokb.cred

/**
 * @author sosguthorpe
 *
 */

import grails.util.GrailsNameUtils

class Combo {

  RefdataValue status
  RefdataValue type

  // Participant 1 - One of these
  KBComponent fromComponent

  // Participant 2 - One of these
  KBComponent toComponent

  static mapping = {
                id column:'combo_id'
           version column:'combo_version'
            status column:'combo_status_rv_fk'
              type column:'combo_type_rv_fk'
     fromComponent column:'combo_from_fk'
       toComponent column:'combo_to_fk'
  }

  static constraints = {
    status(nullable:true, blank:false)
    type(nullable:true, blank:false)
    fromComponent(nullable:true, blank:false)
    toComponent(nullable:true, blank:false)
  }
  
  private void defaultValues () {
	
	// Default to active if not present.
	status ?: RefdataCategory.lookupOrCreate("Combo.Status", "Active")
	
	// Check type.
	if (!type && fromComponent && toComponent) {
	  
	  // Use the class names to create the combo type.
	  String typeName = GrailsNameUtils.getShortName(fromComponent) + "->" + GrailsNameUtils.getShortName(toComponent)
	  
	  // Set the type.
	  type = RefdataCategory.lookupOrCreate("Combo.Type", typeName)
	}
  }
  
  
  /**
   * Override constructor so we can derive type if not present.
   */
  public Combo (Map parmeters) {
	
	// Ensure we set the parameters from the map in the normal way.
	parmeters?.each { k,v ->
	  this."$k" = v
	}
	
	// Set the default if it isn't already.
	defaultValues();
  }
}
