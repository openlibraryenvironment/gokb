package org.gokb.cred

class Identifier extends KBComponent {

  IdentifierNamespace namespace
  String value

  @Override
  Collection<String> getLogIncluded() {
      ['value']
  }

  @Override
  String getLogEntityId() {
      "${this.class.name}:${id}"
  }

  private static nameSpaceRules = [
    "issn" : "^\\d{4}\\-\\d{3}[\\dX]\$",
    "issnl" : "^\\d{4}\\-\\d{3}[\\dX]\$",
    "eissn" : "^\\d{4}\\-\\d{3}[\\dX]\$",
    "isbn" : "^(97(8|9))?\\d{9}[\\dX]\$",
    "zdb" : "^\\d+\\-[\\dX]\$"
  ]

  static constraints = {
    namespace (nullable:false, blank:false)
    value (validator: { val, obj ->
      if (!val || val.trim().size() == 0) {
        return ['notNull']
      }

      if (nameSpaceRules[obj.namespace.value] && val !=~ nameSpaceRules[obj.namespace.value]) {
        return ['IllegalIDForm']
      }
    })
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
    def identifier = Identifier.findByNamespaceAndNormname(namespace,Identifier.normalizeIdentifier(value))

    if (!identifier) {
      def new_id = new Identifier(namespace:namespace, value:value)

      if (new_id.validate()) {
        identifier = new_id.save(flush:true, failOnError:true)
      }
    }

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

  @Override
  public String toString() {
    "${namespace.value}:${value} (${getNiceName()} ${id})".toString()
  }
}
