package org.gokb.cred

class Identifier extends KBComponent {

  IdentifierNamespace namespace
  String value

  static constraints = {
    namespace (nullable:true, blank:true)
    value (nullable:true, blank:true)
  }

  static mapping = {
    namespace column:'id_namespace_fk', index:'id_value_idx'
        value column:'id_value', index:'id_value_idx'
  }

  static manyByCombo = [
    identifiedComponents  :  KBComponent
  ]

  static mappedByCombo = [
    identifiedComponents  :  'ids',
  ]

  @Override
  protected def generateNormname () {
	if (!normname && namespace && value) {
	  normname = "${namespace.value}:${value}".toLowerCase().trim()
	}
  }
  
  @Override
  protected def generateShortcode () {
	if (!shortcode && namespace && value) {
	  // Generate the short code.
	  shortcode = generateShortcode("${namespace.value}:${value}")
	}
  }

  static def lookupOrCreateCanonicalIdentifier(ns, value) {
    // log.debug("lookupOrCreateCanonicalIdentifier(${ns},${value})");
    def namespace = IdentifierNamespace.findByValue(ns) ?: new IdentifierNamespace(value:ns).save(failOnError:true);
    def identifier = Identifier.findByNamespaceAndValue(namespace,value) ?: new Identifier(namespace:namespace, value:value).save(failOnError:true, flush:true)
    identifier
  }

  @Override
  public boolean equals(Object obj) {
	if (obj != null) {
		def dep = KBComponent.deproxy(obj)
		if (dep instanceof Identifier) {
		  return this.value == dep.value &&
		  	this.namespace == dep.namespace
		}
	}
	
	return false
  }
}
