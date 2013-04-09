package org.gokb.cred

import java.lang.reflect.Method
import javax.persistence.Transient
import grails.util.GrailsNameUtils
abstract class KBComponent {

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
    if (propertyName.length() > 1) {
      capProp = propertyName[0].toUpperCase() + propertyName[1..-1]
    } else {
      capProp = propertyName.toUpperCase();
    }
    GrailsNameUtils.getShortName(this.class) + ".${capProp}"
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
        typeClass = manyByCombo().get(propertyName)
        break
      default:
        // Check single properties
        typeClass = hasByCombo().get(propertyName)
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
            // Check the many relationships
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
            break
          default:
            // Check single properties
            typeClass = hasByCombo().get(propertyName)
            if (typeClass.isInstance(value)) {
              Combo combo = new Combo(
                fromComponent : this,
                toComponent : (value),
                type : RefdataCategory.lookupOrCreate("Combo.Type", type),
                status : RefdataCategory.lookupOrCreate("Combo.Status", "Active")
              ).save()
            }
        }
        
        // Add to the cache.
        comboPropertyCache.put(propertyName, value)
      }
    }
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
    def cachedResult = comboPropertyCache[propertyName];
    if (cachedResult) return cachedResult;
    
    // Check the type.
    Class typeClass = manyByCombo().get(propertyName)
      
    // Generate the type.
    String type = comboPropertyKey(propertyName)
    
    if (typeClass) {
      
      def result = []
      
      def combos = Combo.findAllWhere(
        fromComponent : this,
        type : RefdataCategory.lookupOrCreate("Combo.Type", type)
      )
      
      combos.each {
        result.add(it.toComponent)
      }
      
      // Add the result to the cache.
      comboPropertyCache.put(propertyName, result)
      
      return result
    } else {
    
      // Try singular.
      typeClass = hasByCombo().get(propertyName)
      
      if (typeClass) {
        // Just return the component.
        def result = Combo.findWhere(
          fromComponent : this,
          type : RefdataCategory.lookupOrCreate("Combo.Type", type)
        ).toComponent
      
        // Add the result to the cache.
        comboPropertyCache.put(propertyName, result)
        
        return result
      }
    }
    
  }
  
  /**
   * Remove the curent values for the property.
   * @param propertyName
   * @return
   */
  private removeComboPropertyVals (propertyName) {
    // Generate the type.
    String type = comboPropertyKey(propertyName)
    
    // Get all..
    List<Combo> combos = Combo.findAllWhere(
      fromComponent : this,
      type : RefdataCategory.lookupOrCreate("Combo.Type", type)
    )
    
    // Delete each.
    combos.each {
      it.delete()
    }
    
    // Clear the cached value too if present.
    comboPropertyCache.remove(propertyName)
  }
  
  def hasByCombo() {
    [:]
  }
  
  def manyByCombo() {
    [:]
  }
  
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
    invokeMethod(methodToCall, argVals.toArray())
  }
}