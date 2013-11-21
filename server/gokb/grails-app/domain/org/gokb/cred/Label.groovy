package org.gokb.cred

class Label {
    
    String componentType
    String viewType
    String propertyName
    String value

    static constraints = {
      componentType (nullable:false,  blank:false)
      viewType      (nullable:false,  blank:false)
      propertyName  (nullable:false,  blank:false)
      value         (nullable:true,   blank:false)
    }
    
    private static final Map<String, Label> LABEL_CACHE = [:]
    private static String createCacheKey (Object object, String propertyName, String viewType) {
      
      // Ensure it isn't proxy.
      object = KBComponent.deproxy(object)
      
      // Now create the key.
      String key = "${object.class.name}::${propertyName}::${viewType}"
      
      key
    }
    
    
    private static Label getFor (Object object, String propertyName, String viewType) {
      
      // Create the cache key.
      String key = createCacheKey(object, propertyName, viewType)
      
      // Check the cache.
      Label label = LABEL_CACHE[key]
      if (label == null) {
        
        // Chances are if we are missing one label,we will need to fetch the rest for this
        // object/view combination too.
        Label.createCriteria().list {
          and {
            eq ("componentType", (object.class.name))
            eq ("viewType", (viewType))
          }
        }.each { Label l ->
          
          // Create key.
          String new_key = createCacheKey(l, propertyName, viewType)
          
          // Set label if we find the correct label.
          if (l.propertyName == propertyName) label = l
          
          // Add to cache.
          LABEL_CACHE[new_key] = l
        }
        
        // We still might not have found our label if one wasn't present in the DB.
        if (label == null) {
          label = new Label ([
            "componentType"   : (object.class.name),
            "propertyName"    : (propertyName),
            "viewType"        : (viewType) 
          ])
          
          // Save and return.
          label.save(failOnError:true)
        }
      }
      
      label
    }
}
