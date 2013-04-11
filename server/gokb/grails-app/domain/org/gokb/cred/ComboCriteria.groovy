package org.gokb.cred

import grails.orm.HibernateCriteriaBuilder
import java.lang.reflect.Method
import org.gokb.Utils
import org.grails.datastore.mapping.query.api.Criteria
import org.hibernate.SessionFactory;
import org.hibernate.criterion.CriteriaSpecification;

class ComboCriteria {

  private static <T extends KBComponent> Criteria add (Class<T> c, HibernateCriteriaBuilder crit, String propertyName, String operator, Object val) {
    
      // Property exists.
      boolean hasProp = ((Utils.staticMapGet('hasByCombo', c).get(propertyName) != null)
        || (Utils.staticMapGet('manyByCombo', c).get(propertyName) != null))
      
      if (hasProp) {
        // Combo Type.
        RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", Utils.createComboKey(propertyName, c))
        
        // Incoming combo?
        boolean incoming = (Utils.staticMapGet('mappedByCombo', c).get(propertyName) != null)
        
        // Build the criteria string ids.
        String combName, propName
        if (incoming) {
          
          combName = "incomingCombos"
          propName = "fromComponent"
          
        } else {
          combName = "outgoingCombos"
          propName = "toComponent"
        }
        
        // Add alias to ensure table is linked.      
        crit.createAliasIfNeccessary(combName, combName, CriteriaSpecification.INNER_JOIN)
        
        // Add the type comparison.
        crit.eq ("${combName}.type", type)
        
        // Add our derived property name to the args.
        
        // Try and execute the method on the criteria.
        Method m = crit.getClass().getMethod(operator, String.class, Object.class)
        
        m.invoke(crit, "${combName}.${propName}".toString(), val)
        
  //      crit..invokeMethod(operator, newArgs.toArray())
        return crit
      }
  }
}
