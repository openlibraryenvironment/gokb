package org.gokb

import grails.orm.HibernateCriteriaBuilder
import org.gokb.cred.*
import org.gokb.Utils

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
      boolean hasProp = ((Utils.staticMapGet('hasByCombo', crit.targetClass).get(propertyName) != null)
        || (Utils.staticMapGet('manyByCombo', crit.targetClass).get(propertyName) != null))
      
      if (hasProp) {
        // Combo Type.
        RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", Utils.createComboKey(propertyName, crit.targetClass))
        
        // Incoming combo?
        boolean incoming = (Utils.staticMapGet('mappedByCombo', crit.targetClass).get(propertyName) != null)
        
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
