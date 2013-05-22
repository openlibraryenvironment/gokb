package org.gokb.cred

class Identifier extends KBComponent {

  IdentifierNamespace namespace
  String value
  KBComponent component

  static constraints = {
    namespace (nullable:true, blank:true)
    value (nullable:true, blank:true)
	component (nullable:true, blank:true)
  }

  static mapping = {
    namespace column:'id_namespace_fk', index:'id_namespace_fk_idx'
    value column:'id_value', index:'id_value_idx'
	component column:'id_component_fk', index:'id_component_fk_idx'
  }
  
  @Override
  protected def generateNormname () {
	if (!normname && namespace && value) {
	  normname = "${namespace.value}:${value}".toLowerCase().trim()
	}
  }

  static def lookupOrCreateCanonicalIdentifier(ns, value) {
    // log.debug("lookupOrCreateCanonicalIdentifier(${ns},${value})");
    def namespace = IdentifierNamespace.findByValue(ns) ?: new IdentifierNamespace(value:ns).save(failOnError:true);
	
    Identifier.findByNamespaceAndValue(namespace,value) ?: new Identifier(namespace:(namespace), value: (value));
  }

}
