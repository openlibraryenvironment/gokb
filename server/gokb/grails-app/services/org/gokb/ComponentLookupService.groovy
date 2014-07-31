package org.gokb

import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.validation.types.LookedUpValue
import grails.util.GrailsNameUtils

class ComponentLookupService {
  
  static scope = 'request'

  def grailsApplication

  public <T extends KBComponent> Map<String, T> lookupComponents(String... comp_name_strings) {
    Map<String, T> results = [:]
    for (String comp_name_string : comp_name_strings) {
      T comp = lookupComponent (comp_name_string)
      if (comp) {
        // Add the result.
        results["${comp_name_string}"] = comp
      }
    }
    
    results
  }
  
  public <T extends KBComponent> Map<String, T> lookupComponents(Collection<String> comp_name_strings) {
    Map<String, T> results = [:]
    for (String comp_name_string : comp_name_strings) {
      T comp = lookupComponent (comp_name_string)
      if (comp) {
        // Add the result.
        results["${comp_name_string}"] = comp
      }
    }
    
    results
  }
  
  private Map<String, ?> vals = [:].withDefault { String key ->
    lookupComponentDB (key)
  }  
  
  public <T extends KBComponent> T lookupComponent(String comp_name_string) {
    return (T)vals.get(comp_name_string)
  }
  
  private <T extends KBComponent> T lookupComponentDB (String comp_name_string) {

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
          
          if (the_id > 0) {
  
            // Try and get the component.
            comp = c.get(the_id)
  
            if (!c) log.debug ("No component with that ID. Return null.")
          } else {
            log.debug ("Attempting to create a new component.")
            comp = c.newInstance()
          }

        } catch (Throwable t) {
          // Suppress errors here. Just return null.
          log.debug("Unable to parse component string.", t)
        }
      }
    }

    comp
  }
}
