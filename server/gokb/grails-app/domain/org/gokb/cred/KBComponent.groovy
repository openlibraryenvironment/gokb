package org.gokb.cred

import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.persistence.Transient
import grails.util.GrailsNameUtils
import org.gokb.Utils
abstract class KBComponent {

  static auditable = true

  String impId
  // Canonical name field - title for a title instance, name for an org, etc, etc, etc
  String name
  String normname
  String shortcode
  Set tags = []
  List additionalProperties = []
  List outgoingCombos
  List incomingCombos

  static mappedBy = [
    ids: 'component',
    outgoingCombos: 'fromComponent',
    incomingCombos:'toComponent',
    orgs: 'linkedComponent',
    additionalProperties: 'fromComponent']

  static hasMany = [
    ids: IdentifierOccurrence,
    orgs: OrgRole,
    tags:RefdataValue,
    outgoingCombos:Combo,
    incomingCombos:Combo,
    additionalProperties:KBComponentAdditionalProperty]

  //  public static hasByCombo = [:]
  //
  //  public static manyByCombo = [ids : IdentifierOccurrence]
  //
  //  public static mappedByCombo = [:]

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

  static def generateShortcode(name) {
    def candidate = name.trim().replaceAll(" ","_")

    if ( candidate.length() > 100 )
      candidate = candidate.substring(0,100)

    return incUntilUnique(candidate);
  }

  static def incUntilUnique(name) {
    def result = name;
    if ( KBComponent.findWhere([shortcode : (name)]) ) {
      // There is already a shortcode for that identfier
      int i = 2;
      while ( KBComponent.findWhere([shortcode : "${name}_${i}"]) ) {
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
    Utils.createComboKey(propertyName, this.class)
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
        RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", comboPropertyKey(propertyName))

        // Go through each item and generate a value.
        switch (value) {
          case Collection :

            if (reverseLookup(propertyName)) {
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
          // Check single properties
            typeClass = getStaticMap('hasByCombo').get(propertyName)
            if (typeClass.isInstance(value)) {

              if (reverseLookup(propertyName)) {
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
  private def getComboProperty (String propertyName) {

    // Return from cache hashmap if present.

    // Test this way to allow us to cache null values.
    if (comboPropertyCache.containsKey(propertyName)) return comboPropertyCache[propertyName];

    // Check the type.
    Class typeClass = getStaticMap('manyByCombo').get(propertyName)

    // Generate the type.
    RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", comboPropertyKey(propertyName))

    if (typeClass) {

      def result = null

      if (reverseLookup(propertyName)) {
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
      comboPropertyCache.put(propertyName, result)

      return result

    } else {

      // Try singular.
      typeClass = getStaticMap('hasByCombo').get(propertyName)

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
    RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", comboPropertyKey(propertyName))

    // Get all..
    List<Combo> combos
    if (reverseLookup(propertyName)) {
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
    comboPropertyCache.remove(propertyName)
  }

  @Transient
  protected getStaticMap (String mapName) {
    Utils.staticMapGet(mapName, this.class)
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
//  def methodMissing(String methodName, args) {
//
//    String prefix;
//    def propertyName = methodName[3].toLowerCase() + methodName[4..-1]
//
//    // Add the propertyName as the first argument.
//    def argVals = [propertyName]
//    argVals.addAll(args)
//
//    String methodToCall
//    switch (methodName[0..2]) {
//      case "get" :// Property name.
//        methodToCall = "getComboProperty"
//        break
//      case "set" :
//        methodToCall = "setComboProperty"
//        break
//    }
//
//    // Invoke it.
//    if (methodToCall) {
//      try {
//
//        return invokeMethod(methodToCall, argVals.toArray())
//
//      } catch (MissingPropertyException ex) {
//
//        // Try running the original.
//        throw new MissingMethodException(methodName, this.class, args)
//      }
//
//    } else throw new MissingMethodException(methodName, this.class, args)
//  }

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
  
  public void setProperty (String name, Object value) {
    System.out.println("Running set Property");
  }

}
