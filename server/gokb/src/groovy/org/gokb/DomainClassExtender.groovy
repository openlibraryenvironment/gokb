package org.gokb

import grails.util.GrailsNameUtils

import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.gokb.cred.Combo
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue

class DomainClassExtender {

  private static addGetComboMap = { DefaultGrailsDomainClass domainClass ->

    // Get the metaclass.
    domainClass.getMetaClass().getComboMap = { String mappingName ->
      getComboMapFor (domainClass.getClazz(), mappingName)
    }
  }

  private static addGetComboMapFor = { DefaultGrailsDomainClass domainClass ->

    // Get the metaclass.
    ExpandoMetaClass mc = domainClass.getMetaClass()
    mc.static.getComboMapFor = { Class forClass, String mapName ->

      // Return from cache if present.
      String cacheKey = "${GrailsNameUtils.getShortName(forClass)}:${mapName}"
      def value = DomainClassExtender.comboMappingCache[cacheKey]
      if (value) return value

      try {
        // Lookup the value using the metaclass allowing superclass traversal.
        value = mc.getProperty(delegate.getClass(), forClass, mapName, true, true)
      } catch (Exception e) { value = [:] }

      if (value == null) value = [:]
      
      // Cache it.
      DomainClassExtender.comboMappingCache[cacheKey] = value

      // Return the value.
      value
    }
  }

  private static addGetComboProperty = { DefaultGrailsDomainClass domainClass ->
    MetaClass mc = domainClass.getMetaClass()
    mc.getComboProperty {String propertyName ->

      // Return from cache hashmap if present.

      // Test this way to allow us to cache null values.
      String cacheKey = "${delegate.toString()}.${propertyName}"
      if (DomainClassExtender.comboPropertyCache.containsKey(cacheKey)) return DomainClassExtender.comboPropertyCache[cacheKey];

      // Check the type.
      Class typeClass = lookupComboMapping(Combo.MANY, propertyName)

      // Generate the type.
      RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", getComboTypeValue(propertyName))

      if (typeClass) {

        def result = null

        if (isComboReverse(propertyName)) {
          // Reverse.
          def combos = incomingCombos.findAll {
            "type" == (type)
          }

          if (combos) {
            combos.each {
              result.add(it.fromComponent)
            }
          }

        } else {
          def combos = outgoingCombos.findAll {
            "type" == (type)
          }

          if (combos) {
            combos.each {
              result.add(it.toComponent)
            }
          }
        }

        // Add the result to the cache.
        DomainClassExtender.comboPropertyCache.put(propertyName, result)

        return result

      } else {

        // Try singular.
        typeClass = lookupComboMapping(Combo.HAS, propertyName)

        if (typeClass) {
          def result = null
          if (reverseLookup(propertyName)) {

            // Just return the component.
            Combo combo = incomingCombos.findWhere(type : (type))

            if (combo) result = combo.fromComponent
          } else {
            Combo combo = outgoingCombos.findWhere(type : (type))

            if (combo) result = combo.toComponent
          }

          // Add the result to the cache.
          DomainClassExtender.comboPropertyCache.put(propertyName, result)

          return result
        }

        // If we get here then throw an exception.
        throw new MissingPropertyException(propertyName, this.class)
      }

    }
  }
  
  private static addGetComboTypeValue = {DefaultGrailsDomainClass domainClass ->

    // Get the metaclass.
    domainClass.getMetaClass().getComboTypeValue = {String propertyName  ->
      getComboTypeValueFor(domainClass.getClazz(), propertyName)
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

  private static addIsComboReverse = { DefaultGrailsDomainClass domainClass ->
    domainClass.getMetaClass().isComboReverse = {String propertyName ->
      (lookupComboMapping (Combo.MAPPED_BY, propertyName) != null)
    }
  }

  private static addLookupComboMapping = { DefaultGrailsDomainClass domainClass ->

    // Get the metaclass.
    domainClass.getMetaClass().lookupComboMapping = {String mappingName, String propertyName ->

      lookupComboMappingFor (domainClass.getClazz(), mappingName, propertyName)
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
  private static addRemoveComboPropertyVals = { DefaultGrailsDomainClass domainClass ->
    MetaClass mc = domainClass.getMetaClass()
    mc.removeComboPropertyVals {String propertyName ->
      
      // Generate the type.
      RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", getComboTypeValue(propertyName))
  
      // Get all..
      List<Combo> combos
      if (isComboReverse(propertyName)) {
        // Reverse.
        combos = incomingCombos.findAll {
          "type" == (type)
        }
      } else {
        combos = outgoingCombos.findAll {
          "type" == (type)
        }
      }
  
      // Delete each.
      combos.each {
        it.delete()
      }
  
      // Clear the cached value too if present.
      DomainClassExtender.comboPropertyCache.remove("${delegate.toString()}.${propertyName}")
    }
  }

  private static addSetComboProperty = {DefaultGrailsDomainClass domainClass ->
    MetaClass mc = domainClass.getMetaClass()
    mc.setComboProperty = {String propertyName, def value ->
      Class typeClass
      switch (value) {
        case Collection :
        // Check the many relationships
          typeClass = lookupComboMapping(Combo.MANY, propertyName)
          break
        default:
        // Check single properties
          typeClass = lookupComboMapping(Combo.HAS, propertyName)
      }

      if (typeClass) {

        // Capitalise the propertyName.
        removeComboPropertyVals(propertyName)

        if (value) {

          // Generate the type.
          RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", getComboTypeValue(propertyName))

          // Go through each item and generate a value.
          switch (value) {
            case Collection :

              if (isComboReverse(propertyName)) {
                // Reverse
                value.each {
                  if (typeClass.isInstance(it)) {
                    Combo combo = new Combo(
                        fromComponent : (it),
                        type : (type),
                        status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
                        ).save()

                    // Add to the incoming collection
                    addToIncomingCombos(combo)
                  }
                }
              } else {
                value.each {
                  if (typeClass.isInstance(it)) {
                    Combo combo = new Combo(
                        toComponent : (it),
                        type : (type),
                        status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
                        ).save()

                    // Add to the outgoing collection
                    addToOutgoingCombos(combo)
                  }
                }
              }
              break
            default:
            // Check single properties.
              typeClass = lookupComboMapping(Combo.HAS, propertyName)
              if (typeClass.isInstance(value)) {

                if (isComboReverse(propertyName)) {
                  Combo combo = new Combo(
                      fromComponent : (value),
                      type : (type),
                      status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
                      ).save()

                  // Add to the incoming collection
                  addToIncomingCombos(combo)
                } else {
                  Combo combo = new Combo(
                      toComponent : (value),
                      type : (type),
                      status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
                      ).save()

                  // Add to the outgoing collection
                  addToOutgoingCombos(combo)
                }
              }
          }

          // Add to the cache.
          DomainClassExtender.comboPropertyCache.put("${delegate.toString()}.${propertyName}", value)
        }
      } else throw new MissingPropertyException(propertyName, this.class)
    }
  }

  private static Map comboMappingCache = [:]
  private static Map comboPropertyCache = [:]

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
        DomainClassExtender.addSetComboProperty(domainClass)
        DomainClassExtender.addGetComboProperty(domainClass)
        DomainClassExtender.addRemoveComboPropertyVals(domainClass)
        DomainClassExtender.addPropertyMissing(domainClass)

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

          if (argVals[0] != "comboProperty") {
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
  
  private static addPropertyMissing = {DefaultGrailsDomainClass domainClass ->
    // Get the metaclass.
    MetaClass mc = domainClass.getMetaClass()

    // Save the old version of methodMissing so it can be used if needed
    MetaMethod oldPropertyMissing = mc.methods.find { it.name == 'propertyMissing' }
    
    mc.propertyMissing = {String name, value = null ->
      
      def result
      
      switch (name) {
        case Combo.HAS :
        case Combo.MAPPED_BY :
        case Combo.MANY :
          return null
          break
        default :
      
          // Execute the existing propertyMissing.
          if (value == null) {
            if (oldPropertyMissing != null) result = oldPropertyMissing.doMethodInvoke(delegate, [name].toArray())
            
            result = result ?: getComboProperty(name)
          } else {
            if (oldPropertyMissing != null) result = oldPropertyMissing.doMethodInvoke(delegate, [name, value].toArray())
            result = result ?: getComboProperty(name, value)
          }
      }
      result
    }
  }
}
