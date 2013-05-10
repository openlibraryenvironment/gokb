package org.gokb.cred

class Identifier {

  IdentifierNamespace ns
  String value

  static hasMany = [ 
	occurrences		: IdentifierOccurrence
  ]
  static mappedBy = [
	occurrences		: 'identifier'
  ]
  
//  public static manyByCombo = [occurrences:IdentifierOccurrence]

  static constraints = {
  }

  static mapping = {
       id column:'id_id'
       ns column:'id_ns_fk', index:'id_value_idx'
    value column:'id_value', index:'id_value_idx'
  }

  static def lookupOrCreateCanonicalIdentifier(ns, value) {
    // log.debug("lookupOrCreateCanonicalIdentifier(${ns},${value})");
    def namespace = IdentifierNamespace.findByNs(ns) ?: new IdentifierNamespace(ns:ns).save(failOnError:true);
    Identifier.findByNsAndValue(namespace,value) ?: new Identifier(ns:namespace, value:value).save(failOnError:true);
  }

}
