package org.gokb

import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.validation.types.LookedUpValue
import grails.util.GrailsNameUtils

class ComponentLookupService {

  def grailsApplication
  
  public <T extends KBComponent> T lookupComponent(String comp_name_string) {
	
	// The Component
	T comp = null
	if (comp_name_string) {
	  def component_match = comp_name_string =~ "${LookedUpValue.REGEX_TEMPLATE[0]}([^\\:]+)${LookedUpValue.REGEX_TEMPLATE[1]}\$"
	  
	  if (component_match) {
		
		log.debug ("Matched the component syntax \"Display Text::{ComponentType:ID}\".")
		
		try {
		  
		  log.debug("Try and lookup ${component_match[0][1]} with ID ${component_match[0][2]}")
		  
		  // We have a match.
		  Class<? extends KBComponent> c = grailsApplication.getClassLoader().loadClass(
			"org.gokb.cred.${GrailsNameUtils.getClassNameRepresentation(component_match[0][1])}"
		  )

		  // Parse the long.
		  long the_id = Long.parseLong( component_match[0][2] )

		  // Try and get the component.
		  comp = c.get(the_id)
		  
		  if (!c) log.debug ("No component with that ID. Return null.")
		  
		} catch (Throwable t) {
		  // Suppress errors here. Just return null.
		  log.debug("Unable to parse component string.", t)
		}
	  }
	}
	
	comp
  }
}
