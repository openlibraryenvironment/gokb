package org.gokb.cred

import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.persistence.Transient
import grails.util.GrailsNameUtils

abstract class KBComponent {

  static auditable = true

  String impId
  // Canonical name field - title for a title instance, name for an org, etc, etc, etc
  String name
  String normname
  String shortcode
  Set tags = []
  List additionalProperties = []

  static mappedBy = [ids: 'component',
    outgoingCombos: 'fromComponent',
    incomingCombos:'toComponent',
    orgs: 'linkedComponent',
    additionalProperties: 'fromComponent']

  static hasMany = [ids: IdentifierOccurrence,
    orgs: OrgRole,
    tags:RefdataValue,
    outgoingCombos:Combo,
    incomingCombos:Combo,
    additionalProperties:KBComponentAdditionalProperty]

  static mapping = {
    id column:'kbc_id'
    version column:'kbc_version'
    impId column:'kbc_imp_id', index:'kbc_imp_id_idx'
    name column:'kbc_name'
    normname column:'kbc_normname'
    shortcode column:'kbc_shortcode', index:'kbc_shortcode_idx'
    tags joinTable: [name: 'kb_component_refdata_value', key: 'kbcrdv_kbc_id', column: 'kbcrdv_rdv_id']
  }

  static constraints = {
    impId(nullable:true, blank:false)
    name(nullable:true, blank:false, maxSize:2048)
    shortcode(nullable:true, blank:false, maxSize:128)
    normname(nullable:true, blank:false, maxSize:2048)
  }

  @Transient
  String getIdentifierValue(idtype) {
    def result=null
    ids?.each { id ->
      if ( id.identifier?.ns?.ns == idtype )
        result = id.identifier?.value
    }
    result
  }

  def beforeInsert() {
    if ( name ) {
      if ( !shortcode ) {
        shortcode = generateShortcode(name);
      }
      normname = name.toLowerCase().trim();
    }
  }

  def beforeUpdate() {
    if ( name ) {
      if ( !shortcode ) {
        shortcode = generateShortcode(name);
      }
      normname = name.toLowerCase().trim();
    }
  }

  def generateShortcode(name) {
    def candidate = name.trim().replaceAll(" ","_")

    if ( candidate.length() > 100 )
      candidate = candidate.substring(0,100)

    return incUntilUnique(candidate);
  }

  def incUntilUnique(name) {
    def result = name;
    if ( KBComponent.findByShortcode(result) ) {
      // There is already a shortcode for that identfier
      int i = 2;
      while ( Org.findByShortcode("${name}_${i}") ) {
        i++
      }
      result = "${name}_${i}"
    }

    result;
  }

  @Transient
  static def lookupByIO(String idtype, String idvalue) {
    // println("lookupByIdentifier(${idtype},${idvalue})");
    def result = null
    def crit = KBComponent.createCriteria()
    def lr = crit.list {
      ids {
        identifier {
          eq('value',idvalue)
          ns {
            eq('ns',idtype)
          }
        }
      }
    }

    // println("res: ${lr}");

    if ( lr && lr.size() == 1 )
      result=lr.get(0);

    // println("result: ${result}");
    result
  }

  @Transient
  abstract getPermissableCombos();
  
  private String comboPropertyKey(String propertyName) {
    String capProp
    Class c
    def mappedByProp = getStaticMap('mappedByCombo').get(propertyName)
    if (mappedByProp) {
      // We need to look up the relationship the other way round.
      // First find the class type mapped to.
      Class mappedByClass = getStaticMap('manyByCombo').get(propertyName)
      mappedByClass = mappedByClass ?: getStaticMap('hasByCombo').get(propertyName)
      
      if (mappedByClass) {
        // Found the class, we can now use this information to build up our string.
        if (mappedByProp.length() > 1) {
          capProp = mappedByProp[0].toUpperCase() + mappedByProp[1..-1]
        } else {
          capProp = mappedByProp.toUpperCase()
        }
        
        // Set the class also.
        c = mappedByClass
      }
    } else {
      if (propertyName.length() > 1) {
        capProp = propertyName[0].toUpperCase() + propertyName[1..-1]
      } else {
        capProp = propertyName.toUpperCase();
      }
      
      // Set the class also.
      c = this.class
    }
    
    // Return the constructed key.
    GrailsNameUtils.getShortName(c) + ".${capProp}"
  }
  
  private boolean reverseLookup (String propertyName) {
    propertyName && getStaticMap('mappedByCombo').get(propertyName)
  }
  
  /**
   * Create a combo to mirror the behaviour of a property on this method mapping to another class.
   * @param toComponent - The component that is to become a child of this one.
   * @return the Combo for the relationship
   */
  @Transient
  private void setComboProperty (String propertyName, def value) {
    Class typeClass
    switch (value) {
      case Collection : 
        // Check the many relationships
        typeClass = getStaticMap('manyByCombo').get(propertyName)
        break
      default:
        // Check single properties
        typeClass = getStaticMap('hasByCombo').get(propertyName)
    }
    
    if (typeClass) {
      
      // Capitalise the propertyName.
      removeComboPropertyVals(propertyName)
      
      if (value) {
      
        // Generate the type.
        String type = comboPropertyKey(propertyName)
        
        // Go through each item and generate a value.
        switch (value) {
          case Collection :
            
            if (reverseLookup(propertyName)) {
              // Reverse
              value.each {
                if (typeClass.isInstance(it)) {
                  Combo combo = new Combo(
                    toComponent : this,
                    fromComponent : (it),
                    type : RefdataCategory.lookupOrCreate("Combo.Type", type),
                    status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
                  ).save()
                }
              }
            } else {
              value.each {
                if (typeClass.isInstance(it)) {
                  Combo combo = new Combo(
                    fromComponent : this,
                    toComponent : (it),
                    type : RefdataCategory.lookupOrCreate("Combo.Type", type),
                    status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
                  ).save()
                }
              }
            }
            break
          default:
            // Check single properties
            typeClass = getStaticMap('hasByCombo').get(propertyName)
            if (typeClass.isInstance(value)) {
              
              if (reverseLookup(propertyName)) {
                Combo combo = new Combo(
                  toComponent : this,
                  fromComponent : (value),
                  type : RefdataCategory.lookupOrCreate("Combo.Type", type),
                  status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
                ).save()
              } else {
                Combo combo = new Combo(
                  fromComponent : this,
                  toComponent : (value),
                  type : RefdataCategory.lookupOrCreate("Combo.Type", type),
                  status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
                ).save()
              }
            }
        }
        
        // Add to the cache.
        comboPropertyCache.put(propertyName, value)
      }
    } else throw new MissingPropertyException(propertyName, this.class)
    
  }
  
  private Map comboPropertyCache = [:]
  
  /**
   * Create a combo to mirror the behaviour of a property on this method mapping to another class.
   * @param toComponent - The component that is to become a child of this one.
   * @return the Combo for the relationship
   */
  @Transient
  private <T> T getComboProperty (String propertyName) {
    
    // Return from cache hashmap if present.
    
    // Test this way to allow us to cache null values.
    if (comboPropertyCache.containsKey(propertyName)) return comboPropertyCache[propertyName];
    
    // Check the type.
    Class typeClass = getStaticMap('manyByCombo').get(propertyName)
      
    // Generate the type.
    String type = comboPropertyKey(propertyName)
    
    if (typeClass) {
      
      def result = null
      
      if (reverseLookup(propertyName)) {
        // Reverse.
        def combos = Combo.findAllWhere(
          toComponent : this,
          type : RefdataCategory.lookupOrCreate("Combo.Type", type)
        )
        
        if (combos) {
          combos.each {
            result.add(it.fromComponent)
          }
        }
        
      } else {
        def combos = Combo.findAllWhere(
          fromComponent : this,
          type : RefdataCategory.lookupOrCreate("Combo.Type", type)
        )
        
        if (combos) {
          combos.each {
            result.add(it.toComponent)
          }
        }
      }
      
      // Add the result to the cache.
      comboPropertyCache.put(propertyName, result)
      
      return result
      
    } else {
    
      // Try singular.
      typeClass = getStaticMap('hasByCombo').get(propertyName)
      
      if (typeClass) {
        def result = null
        if (reverseLookup(propertyName)) {
        
          // Just return the component.
          Combo combo = Combo.findWhere(
            toComponent : this,
            type : RefdataCategory.lookupOrCreate("Combo.Type", type)
          )
          
          if (combo) result = combo.fromComponent
        } else {
          Combo combo = Combo.findWhere(
            fromComponent : this,
            type : RefdataCategory.lookupOrCreate("Combo.Type", type)
          )
        
          if (combo) result = combo.toComponent
        }
      
        // Add the result to the cache.
        comboPropertyCache.put(propertyName, result)
        
        return result
      }
        
      // If we get here then throw an exception.
      throw new MissingPropertyException(propertyName, this.class)
    }
    
  }
  
  /**
   * Remove the current values for the property.
   * @param propertyName
   * @return
   */
  private removeComboPropertyVals (propertyName) {
    // Generate the type.
    String type = comboPropertyKey(propertyName)
    
    // Get all..
    List<Combo> combos    
    if (reverseLookup(propertyName)) {
      // Reverse
      combos = Combo.findAllWhere(
        toComponent : this,
        type : RefdataCategory.lookupOrCreate("Combo.Type", type)
      )
    } else {
      combos = Combo.findAllWhere(
        fromComponent : this,
        type : RefdataCategory.lookupOrCreate("Combo.Type", type)
      )
    }
    
    // Delete each.
    combos.each {
      it.delete()
    }
    
    // Clear the cached value too if present.
    comboPropertyCache.remove(propertyName)
  }
  
  private static staticMapsCache = [:]  
  private static staticMapGet (String mapName, Class c) {
    
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
  
  @Transient
  protected getStaticMap (String mapName) {
    staticMapGet(mapName, this.class)
  }
  
  public static hasByCombo = [:]
  
  public static manyByCombo = [:]
  
  public static mappedByCombo = [:]
  
  /**
   * Called when trying to set missing property.
   */
  def propertyMissing(String name, value) {
    setComboProperty(name, value)
  }
  
  /**
   * Called when trying to get missing property.
   */
  def propertyMissing(String name) {
    getComboProperty(name)
  }
  
  /**
   *  Override method missing.
   */
  def methodMissing(String methodName, args) {
    
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
        
        return invokeMethod(methodToCall, argVals.toArray())
         
      } catch (MissingPropertyException ex) {
      
        // Try running the original.
        throw new MissingMethodException(methodName, this.class, args)
      }
      
    } else throw new MissingMethodException(methodName, this.class, args)
  }

  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = KBComponent.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

}
