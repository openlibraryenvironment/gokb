package org.gokb

import java.lang.reflect.Field

import grails.util.GrailsNameUtils

class Utils {

  private static staticMapsCache = [:]
  public static staticMapGet (String mapName, Class c) {

    // Return from cache if present.
    def cacheKey = GrailsNameUtils.getShortName(c) + "." + mapName
    def cachedValue = staticMapsCache[cacheKey]
    if (cachedValue != null) return cachedValue

    // No cached value lets merge all the super classes with this one.

    // Combined map.
    def combinedVals = [:]
    Field f

    // Add this classes values first.
    try {

      // Get the field for the class.
      f = c.getDeclaredField(mapName)

      // Add all the values in the map returned by the field.
      if (f) combinedVals.putAll(f.get(c))

    } catch (NoSuchFieldException e) {
      // Just ignore this here as not all classes will declare the static fields.
    }

    // Add superclasses values.
    Class superClass = c.superclass

    // Recurse this method to ensure caching at all levels.
    if(superClass != java.lang.Object) {
      combinedVals.putAll(staticMapGet (mapName, superClass))
    }

    // Add the result to speed this whole process up.
    staticMapsCache[cacheKey] = combinedVals
  }
  
  private static String createComboKey(String propertyName, Class c) {
    String capProp
    Class mappedByClass
    def mappedByProp = staticMapGet('mappedByCombo', c).get(propertyName)
    if (mappedByProp) {
      // We need to look up the relationship the other way round.
      // First find the class type mapped to.
      mappedByClass = staticMapGet('manyByCombo', c).get(propertyName)
      mappedByClass = mappedByClass ?: staticMapGet('hasByCombo', c).get(propertyName)

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
      mappedByClass = c
    }

    // Return the constructed key.
    GrailsNameUtils.getShortName(mappedByClass) + ".${capProp}"
  }
}
