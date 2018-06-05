package org.gokb

import org.gokb.cred.KBComponent
import com.k_int.ClassUtils

class Annotation {
    
    String componentType
    String viewType
    String propertyName
    String value
    
    def afterUpdate() {
      // Update listener that removes updates the cache for this item.
      replaceInCache(this)
    }

    static mapping = {
      value column:'value', type:'text'
    }

    static constraints = {
      componentType (nullable:false,  blank:false)
      viewType      (nullable:false,  blank:false)
      propertyName  (nullable:false,  blank:false)
      value         (nullable:true,   blank:false)
    }
    
    private static final Map<String, Annotation> ANNOTATION_CACHE = [:]
    private static String createCacheKey (Object object, String propertyName, String viewType) {
      
      // Ensure it isn't proxy.
      object = ClassUtils.deproxy(object)
      
      // Now create the key.
      String key = "${object.class.name}::${propertyName}::${viewType}"
      
      key
    }
    
    public static replaceInCache(Annotation annotation) {
      String key = "${annotation.componentType}::${annotation.propertyName}::${annotation.viewType}"
      ANNOTATION_CACHE.put(key, annotation)
    }
    
    private static Annotation getFor (Object object, String propertyName, String viewType) {
      
      // Create the cache key.
      String key = createCacheKey(object, propertyName, viewType)
      
      // Check the cache.
      Annotation annotation = ANNOTATION_CACHE.get(key)
      if (annotation == null) {
        
        // Chances are if we are missing one Annotation,we will need to fetch the rest for this
        // object/view combination too.
        Annotation.createCriteria().list {
          and {
            eq ("componentType", (object.class.name))
            eq ("viewType", (viewType))
          }
        }.each { Annotation l ->
          
          // Create key.
          String new_key = "${l.componentType}::${l.propertyName}::${l.viewType}"
          
          // Set Annotation if we find the correct Annotation.
          if (l.propertyName == propertyName) annotation = l
          
          // Add to cache.
          ANNOTATION_CACHE.put(new_key, l)
        }
        
        // We still might not have found our Annotation if one wasn't present in the DB.
        if (annotation == null) {
          annotation = new Annotation ([
            "componentType"   : (object.class.name),
            "propertyName"    : (propertyName),
            "viewType"        : (viewType) 
          ])
          
          // Save and return.
          annotation.save(failOnError:true)
          
          // Cache the annotation.
          ANNOTATION_CACHE.put(key, annotation)
        }
      }
      
      annotation
    }
}
