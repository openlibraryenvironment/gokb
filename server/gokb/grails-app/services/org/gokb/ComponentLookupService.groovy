package org.gokb

import grails.util.GrailsNameUtils

import org.gokb.cred.KBComponent
import org.gokb.validation.types.LookedUpValue
import grails.util.Holders


class ComponentLookupService {
  

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
  
//  private Map<String, ?> vals = [:].withDefault { String key ->
//    lookupComponentDB (key)
//  }  
//  
//  public <T extends KBComponent> T lookupComponent(String comp_name_string) {
//    
//    // Merge this object into the current session if needed.
//    T object = (T)vals.get(comp_name_string)
//    if (object != null && !object.isAttached()) {
//      object = object.merge()
//      vals.put(comp_name_string, object)
//    }
//    return object
//  }
  
  private <T extends KBComponent> T lookupComponent (String comp_name_string) {
    return lookupComponent(comp_name_string,false)
  }

  private <T extends KBComponent> T lookupComponent (String comp_name_string, boolean lock) {

    // The Component
    T comp = null
    if (comp_name_string) {
      def component_match

      if ((component_match = comp_name_string =~ "${LookedUpValue.ID_REGEX_TEMPLATE[0]}([^\\:]+)${LookedUpValue.ID_REGEX_TEMPLATE[1]}\$") || 
        (component_match = comp_name_string =~ "${LookedUpValue.REGEX_TEMPLATE[0]}([^\\:]+)${LookedUpValue.REGEX_TEMPLATE[1]}\$")) {

        log.debug ("Matched the component syntax \"Display Text::{ComponentType:ID}\".")

        try {
          
          // Partial or complete class name.
          String cls_name = component_match[0][1]
          if (!cls_name.contains('.')) {
            cls_name = "org.gokb.cred.${GrailsNameUtils.getClassNameRepresentation(cls_name)}"
          }
          
          log.debug("Try and lookup ${cls_name} with ID ${component_match[0][2]}")
          
          // We have a match.
          Class<? extends KBComponent> c = Holders.grailsApplication.getClassLoader().loadClass("${cls_name}")

          // Parse the long.
          long the_id = Long.parseLong( component_match[0][2] )
          
          if (the_id > 0) {
  
            // Try and get the component.
            if ( lock ) {
              comp = c.lock(the_id)
            }
            else {
              comp = c.get(the_id)
            }
  
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

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }
}
