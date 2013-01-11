package org.gokb.cred

import javax.persistence.Transient

class KBComponent {

  String impId
  // Canonical name field - title for a title instance, name for an org, etc, etc, etc
  String name
  String normname
  String shortcode
  Set tags = []

  static mappedBy = [ids: 'component',  
                     outgoingCombos: 'from',
                     incomingCombos:'to',
                     orgs: 'linkedComponent']

  static hasMany = [ids: IdentifierOccurrence, 
                    orgs: OrgRole, 
                    tags:RefdataValue,
                    outgoingCombos:Combo,
                    incomingCombos:Combo]

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
    name(nullable:true, blank:false, maxSize:1024)
    shortcode(nullable:true, blank:false)
    normname(nullable:true, blank:false, maxSize:1024)
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

}
