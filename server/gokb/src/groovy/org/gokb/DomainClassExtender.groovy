package org.gokb

import java.util.Map;

import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.gokb.cred.KBComponent
import org.gokb.cred.Combo
import org.gokb.cred.RefdataValue
import grails.util.GrailsNameUtils

class DomainClassExtender {

  public static extend = { DefaultGrailsDomainClass domainClass ->
    // Get the actual class that is represented by this domain class object.
    Class actualClass = domainClass.getClazz()

    if (!KBComponent.class.is(actualClass)) {
      if (KBComponent.class.isAssignableFrom(actualClass)) {
      
        // Extends KBCombonent.
        
        // Extend to handle ComboMapped Properties.
        DomainClassExtender.extendMethodMissing (domainClass)
        DomainClassExtender.addGetComboMap (domainClass)
        DomainClassExtender.addLookupComboMapping (domainClass)
        DomainClassExtender.addGetComboTypeValue (domainClass)
        DomainClassExtender.addIsComboReverse (domainClass)
        
        DomainClassExtender.addGetComboMapFor (domainClass)
        DomainClassExtender.addLookupComboMappingFor (domainClass)
        DomainClassExtender.addGetComboTypeValueFor (domainClass)
      }
    } else {
    
      // Is KBCombonent class. Just add the static helper methods.
      DomainClassExtender.addGetComboMapFor (domainClass)
      DomainClassExtender.addLookupComboMappingFor (domainClass)
      DomainClassExtender.addGetComboTypeValueFor (domainClass)
    }
  }
  
  private static addIsComboReverse = { DefaultGrailsDomainClass domainClass ->
    domainClass.getMetaClass().isComboReverse = {String propertyName ->
      (lookupComboMapping (Combo.MAPPED_BY, propertyName) != null)
    }
  }
  
  private static addGetComboMapFor = { DefaultGrailsDomainClass domainClass ->
    
    // Get the metaclass.
    MetaClass mc = domainClass.getMetaClass()
    mc.static.getComboMapFor = { Class forClass, String mapName ->
      
      // Return from cache if present.
      def cacheKey = "${GrailsNameUtils.getShortName(forClass)}:${mapName}"
      def value = comboMappingCache[cacheKey]
      if (value) return value
      
      try {
        // Lookup the value using the metaclass allowing superclass traversal.
        value = mc.getProperty(forClass, delegate, mapName, true, true)
      } catch (Exception e) { value = [:] }
      
      // Cache it.
      comboMappingCache.put(cacheKey, value)
      
      // Return the value.
      value
    }
  }

  private static Map comboMappingCache = [:]
  private static addGetComboMap = { DefaultGrailsDomainClass domainClass ->
    
    // Get the metaclass.
    domainClass.getMetaClass().getComboMap = { String mappingName ->
      getComboMapFor (domainClass.getClazz(), mappingName)
    }
  }

  private static addLookupComboMappingFor = { DefaultGrailsDomainClass domainClass ->
    
    // Get the metaclass.
    MetaClass mc = domainClass.getMetaClass()
    mc.static.lookupComboMappingFor = {Class forClass, String mappingName, String propertyName ->
      
      // Get the map.
      def map = getComboMapFor (forClass, mappingName)
      
      // Return the property.
      map[propertyName]
    }
  }
  
  private static addLookupComboMapping = { DefaultGrailsDomainClass domainClass ->
    
    // Get the metaclass.
    domainClass.getMetaClass().lookupComboMapping = {String mappingName, String propertyName ->
      
      lookupComboMappingFor (domainClass.getClazz(), mappingName, propertyName)
    }
  }

  private static extendMethodMissing = { DefaultGrailsDomainClass domainClass ->
    System.out.println("Extending methodMissing for ${domainClass.getClazz().getName()}")

    // Get the metaclass.
    MetaClass mc = domainClass.getMetaClass()

    // Save the old version of methodMissing so it can be used if needed
    MetaMethod oldMethodMissing = mc.methods.find { it.name == 'methodMissing' }

    mc.methodMissing = { String methodName, args ->

      String prefix;
      def propertyName = methodName[3].toLowerCase() + methodName[4..-1]

      // Add the propertyName as the first argument.
      def argVals = [propertyName]
      argVals.addAll(args)

      String methodToCall
      switch (methodName[0..2]) {
        case "get" :// Property name.
          methodToCall = "getComboProperty"
          break
        case "set" :
          methodToCall = "setComboProperty"
          break
      }

      // Invoke it.
      if (methodToCall) {
        try {
          Object result
          
          if (argVals[0] != argVals[1]) {
            result = delegate.invokeMethod(methodToCall, argVals.toArray())
  
            // Add the metaclass method to speed up future calls.
            mc."${methodToCall}" = { Object[] varArgs ->
              delegate.invokeMethod(methodToCall, varArgs)
            }

            return result
          }

        } catch (MissingPropertyException ex) {
          /* Do nothing as the code should drop through and try and run original method */
        }
      }

      // Invoke the old methodMissing...
      if (oldMethodMissing) {
        return oldMethodMissing.invoke(delegate, args)
      }

      // Finally throw an exception if no luck.
      throw new MissingMethodException(methodName, this.class, args)
    }
  }
  
  private static addGetComboTypeValueFor = {DefaultGrailsDomainClass domainClass ->
    
    // Get the metaclass.
    MetaClass mc = domainClass.getMetaClass()
    mc.static.getComboTypeValueFor = {Class forClass, String propertyName  ->
      String capProp
      Class mappedByClass
      def mappedByProp = lookupComboMappingFor(forClass, Combo.MAPPED_BY, propertyName)
      if (mappedByProp) {
        // We need to look up the relationship the other way round.
        // First find the class type mapped to.
        mappedByClass = lookupComboMappingFor(forClass, Combo.MANY, propertyName)
        mappedByClass = mappedByClass ?: lookupComboMappingFor(forClass, Combo.HAS, propertyName)
  
        if (mappedByClass) {
          // Found the class, we can now use this information to build up our string.
          if (mappedByProp.length() > 1) {
            capProp = mappedByProp[0].toUpperCase() + mappedByProp[1..-1]
          } else {
            capProp = mappedByProp.toUpperCase()
          }
        }
      } else {
        if (propertyName.length() > 1) {
          capProp = propertyName[0].toUpperCase() + propertyName[1..-1]
        } else {
          capProp = propertyName.toUpperCase();
        }
  
        // Set the class also.
        mappedByClass = forClass
      }
  
      // Return the constructed key.
      GrailsNameUtils.getShortName(mappedByClass) + ".${capProp}"
    }
  }
  
  private static addGetComboTypeValue = {DefaultGrailsDomainClass domainClass ->
    
    // Get the metaclass.
    domainClass.getMetaClass().getComboTypeValue = {String propertyName  ->
      getComboTypeValueFor(domainClass.getClazz(), propertyName)
    }
  }
}
