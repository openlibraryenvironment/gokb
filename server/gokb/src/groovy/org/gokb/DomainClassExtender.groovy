package org.gokb

import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.gokb.cred.KBComponent

class DomainClassExtender {

  public static extend = { DefaultGrailsDomainClass domainClass ->
    // Get the actual class that is represented by this domain class object.
    Class actualClass = domainClass.getClazz();

    if (!KBComponent.class.is(actualClass) && KBComponent.class.isAssignableFrom(actualClass)) {
      
      // KBComponents only.
      extendMethodMissing (domainClass)
    }
  }

  private static addCompoPropertyLookup = {
    def value
    try {
      value = mc.getProperty(actualClass, delegate, "hasByCombo", true, true)
    } catch (Exception e) { value = [:] }
  }


  private static extendMethodMissing = { DefaultGrailsDomainClass domainClass ->
    System.out.println("Extending methodMissing for ${actualClass.getName()}")

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
          Object result = delegate.invokeMethod(methodToCall, argVals.toArray())

          // Add the metaclass method to speed up future calls.
          mc."${methodToCall}" = { Object[] varArgs ->
            delegate.invokeMethod(methodToCall, varArgs)
          }

          return result

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
}
