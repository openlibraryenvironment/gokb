package org.gokb.cred

import javax.persistence.Transient

class KBComponent {

  String impId
  // Canonical name field - title for a title instance, name for an org, etc, etc, etc
  String name
  String normname
  String shortcode

  static mappedBy = [ids: 'component',  orgs: 'linkedComponent']
  static hasMany = [ids: IdentifierOccurrence, orgs: OrgRole]

  static mapping = {
           id column:'kbc_id'
      version column:'kbc_version'
        impId column:'kbc_imp_id', index:'kbc_imp_id_idx'
         name column:'kbc_name'
     normname column:'kbc_normname'
    shortcode column:'kbc_shortcode', index:'kbc_shortcode_idx'

  }

  static constraints = {
    impId(nullable:true, blank:false)
    name(nullable:true, blank:false)
    shortcode(nullable:true, blank:false)
    normname(nullable:true, blank:false)
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

  // Included for backward compat.. deprecated
  @Transient
  def getIdentifierByType(idtype) {
    def result = null
    ids.each { id ->
      if ( id.identifier.ns.ns == idtype ) {
        result = id.identifier;
      }
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
