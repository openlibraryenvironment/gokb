package org.gokb.cred

class Identifier extends KBComponent {

  IdentifierNamespace namespace
  String value

  static constraints = {
    namespace (nullable:true, blank:true)
    value (nullable:true, blank:true)
  }

  static mapping = {
    includes KBComponent.mapping
               value column:'id_value', index:'id_value_idx'
           namespace column:'id_namespace_fk', index:'id_value_idx'
  }

  static manyByCombo = [
    identifiedComponents  :  KBComponent
  ]

  static mappedByCombo = [
    identifiedComponents  :  'ids',
  ]

  @Override
  protected def generateNormname () {
    if (!normname && value) {
      normname = Identifier.normalizeIdentifier(value)
    }
  }

  public static normalizeIdentifier(String id) {
    return id.toLowerCase().trim().replaceAll("\\W", "")
  }
  
  @Override
  protected def generateShortcode () {
    if (!shortcode && namespace && value) {
      // Generate the short code.
      shortcode = generateShortcode("${namespace.value}:${value}").replaceAll("\\W", "-")
    }
  }

  static def lookupOrCreateCanonicalIdentifier(ns, value) {
    // log.debug("lookupOrCreateCanonicalIdentifier(${ns},${value})");
    def namespace = null;
    def namespaces = IdentifierNamespace.findAllByValue(ns.toLowerCase())
    switch ( namespaces.size() ) {
      case 0:
        namespace = new IdentifierNamespace(value:ns.toLowerCase()).save(failOnError:true);
        break;
      case 1:
        namespace = namespaces[0]
        break;
      default:
        throw new RuntimeException("Multiple Namespaces with value ${ns}");
        break;
    }
    def identifier = Identifier.findByNamespaceAndNormname(namespace,Identifier.normalizeIdentifier(value)) ?: 
                                    new Identifier(namespace:namespace, value:value).save(failOnError:true, flush:true)
    identifier
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null) {
      def dep = KBComponent.deproxy(obj)
      if (dep instanceof Identifier) {
        return this.normname == dep.normname &&
          this.namespace == dep.namespace
      }
    }
    return false
  }

  @Override
  public String getName() {
    return value
  }
}
