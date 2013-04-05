package org.gokb.cred

import javax.persistence.Transient

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

  public List getChildren(Class type) {
    lookupCombos (type, "children")
  }

  public List getParents (Class type) {
    lookupCombos (type, "parents")
  }

  private List lookupCombos (Class type, String direction = "children") {
    def result = []

    // Try and resolve any combos mapping to this type.
    if (type) {
      
      String typeName;
      def combos;
      switch (direction) {
        case "children" :
          
          // Build the type name.
          typeName = this.class.getName() + "->" + type.getName()
          
          // Now query for the results.
          combos = Combo.findAll {
            fromComponent : this
            type : RefdataCategory.lookupOrCreate("Combo.Type", typeName)
          }
          
          // Add each fromComponent to the list.
          combos.each {
            result[] = it.toComponent
          }
          
          break
          
        default :
          // Assume parent.
          typeName = type.getName() + "->" + this.class.getName()// Now query for the results.
          
          // Now query for the combos.
          combos = Combo.findAll {
            toComponent : this
            type : RefdataCategory.lookupOrCreate("Combo.Type", typeName)
          }
          
          // Add each fromComponent to the list.
          combos.each {
            result[] = it.fromComponent
          }
          break
      }
    }

    result
  }
}