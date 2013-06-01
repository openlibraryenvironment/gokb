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

  static mappedByCombo = [
    identifiedComponents      :  'ids'
  ]


  @Override
  protected def generateNormname () {
	if (!normname && namespace && value) {
	  normname = "${namespace.value}:${value}".toLowerCase().trim()
	}
  }

  static def lookupOrCreateCanonicalIdentifier(ns, value) {
    // log.debug("lookupOrCreateCanonicalIdentifier(${ns},${value})");
    def namespace = IdentifierNamespace.findByValue(ns) ?: new IdentifierNamespace(value:ns).save(flush:true,failOnError:true);
    Identifier.findByNamespaceAndValue(namespace,value) ?: new Identifier(namespace:namespace, value:value).save(flush:true,failOnError:true)
  }

}
