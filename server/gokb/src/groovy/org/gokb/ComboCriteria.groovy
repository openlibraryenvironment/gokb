package org.gokb

import grails.orm.HibernateCriteriaBuilder
import org.gokb.cred.*

class ComboCriteria {
  
  private HibernateCriteriaBuilder crit
  private static ComboCriteria comboCrit
  
  private ComboCriteria (HibernateCriteriaBuilder crit) {
    crit.createAlias("incomingCombos", "incomingCombos")
    this.crit = crit
  }
  
  public static ComboCriteria createFor(HibernateCriteriaBuilder crit) {
    if (comboCrit == null || comboCrit.crit != crit) comboCrit = new ComboCriteria (crit)
    this.comboCrit
  }
  
  public static ComboCriteria add (String propertyName, String operator, Object... args ) {
    // TODO: Add some error trapping for null pointer and throw a meaningful error message.
    comboCrit.addCriteria(propertyName, operator, args)
  }

  private ComboCriteria addCriteria (String propertyName, String operator, Object... args ) {
    
      // Property exists.
      boolean hasProp = ((KBComponent.lookupComboMappingFor(crit.targetClass, Combo.HAS, propertyName) != null)
        || (KBComponent.lookupComboMappingFor(crit.targetClass, Combo.MANY,propertyName) != null))
      
      if (hasProp) {
        // Combo Type.
        RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", KBComponent.getComboTypeValueFor(crit.targetClass, propertyName))
        
        // Incoming combo?
        boolean incoming = (KBComponent.lookupComboMappingFor(crit.targetClass, Combo.MAPPED_BY, propertyName) != null)
        
        // Build the criteria string ids.
        String combName, propName
        if (incoming) {
          
          combName = "incomingCombos"
          propName = "fromComponent"
          
        } else {
          combName = "outgoingCombos"
          propName = "toComponent"
        }
        
        // Add the type comparison.
        crit.eq ("${combName}.type", type)
        
        // Add our derived property name to the args and try and execute the method on the criteria.        
        def methodParams = ["${combName}.${propName}".toString()]
        methodParams.addAll(args)
        
        // Try and invoke a method.
        crit.invokeMethod(operator, methodParams.toArray())
      }
      
      this
  }
}
