package org.gokb

import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.validation.types.LookedUpValue
import grails.util.GrailsNameUtils

class ComponentLookupService {

  def grailsApplication
  
  public <T extends KBComponent> T lookupComponent(String comp_name_string) {
	
	// The Org
	T comp = null
	if (comp_name_string) {
	  def publisher_match = comp_name_string =~ "${LookedUpValue.REGEX_TEMPLATE[0]}([^\\:]+)${LookedUpValue.REGEX_TEMPLATE[1]}"
	  
	  if (publisher_match) {
		
		try {
		  // We have a match.
		  Class<? extends KBComponent> c = grailsApplication.getClassLoader().loadClass(
			"org.gokb.cred.${GrailsNameUtils.getClassNameRepresentation(publisher_match[0][1])}"
		  )

		  // Parse the long.
		  long the_id = Long.parseLong( publisher_match[0][2] )

		  // Try and get the component.
		  comp = c.get(the_id)
		  
		} catch (Throwable t) { /* Do nothing */ }
	  }
	}
	
	comp
  }
}
