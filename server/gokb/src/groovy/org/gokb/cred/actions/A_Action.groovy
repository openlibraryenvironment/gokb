package org.gokb.cred.actions

import grails.util.GrailsNameUtils;
import groovy.util.logging.Log4j;

import org.gokb.cred.KBComponent

@Log4j
abstract class A_Action {
  
  abstract String name
  
  private static actionRegistry = [:]
  public static get(String type) {
    
    // Check whether FQ name is used.
    if (!type.contains(".")) {
      // Assume this package.
      type = "org.gokb.cred.actions.${type}"
    }
    
    // Check the registry.
    A_Action a = actionRegistry[type];
    
    if (a == null) {
      
      // Don't bother catching any exceptions, leave it up to the caller to report errors.
      a = Class.forName(type).newInstance()
    }
    
    a
  }
  
  /**
   * Actually complete the action.
   */
  public abstract doAction(def param = [])
}