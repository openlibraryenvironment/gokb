package org.gokb.cred
import groovy.transform.Synchronized
import java.util.regex.Pattern

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

  static jsonMapping = [
    'ignore': [
      'lastUpdatedBy',
      'dateCreated',
      'editStatus',
      'name',
      'status',
      'lastUpdated'
    ],
    'defaultLinks': [
      'namespace'
    ],
    'defaultEmbeds': [
    ]
  ]

  private static nameSpaceRules = [
    "issn" : "^\\d{4}\\-\\d{3}[\\dX]\$",
    "issnl" : "^\\d{4}\\-\\d{3}[\\dX]\$",
    "eissn" : "^\\d{4}\\-\\d{3}[\\dX]\$",
    "zdb" : "^\\d+\\-[\\dxX]\$"
  ]

  static constraints = {
    namespace (nullable:false, blank:false)
    value (validator: { val, obj ->
      if (obj.hasChanged('value')) {
        if (!val || val.trim().size() == 0) {
          return ['notNull']
        }

        def norm_id = Identifier.normalizeIdentifier(val)
        def dupes = Identifier.findByNamespaceAndNormname(obj.namespace, norm_id)
        def pattern = obj.namespace.pattern ? ~"${obj.namespace.pattern}" : null

        if (dupes && dupes != obj) {
          return ['notUnique']
        }

        if ( (nameSpaceRules[obj.namespace.value] && !(val ==~ nameSpaceRules[obj.namespace.value])) || (pattern && !(val ==~ pattern)) )  {
          return ['illegalIdForm.' + obj.namespace.value ]
        }
      }
    })
  }

  static mapping = {
    includes KBComponent.mapping
               value column:'id_value', index:'id_value_idx'
           namespace column:'id_namespace_fk', index:'id_namespace_idx'
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

  static def lookupOrCreateCanonicalIdentifier(ns, value, def ns_create = true) {
    def lock = true
    return findOrCreateId(ns, value, ns_create, lock)
  }

  private static final findLock = new Object()

  @Synchronized("findLock")
  private static def findOrCreateId(ns, value, def ns_create = true, lock) {
    // log.debug("lookupOrCreateCanonicalIdentifier(${ns},${value})");
    def namespace = null
    def identifier = null
    def namespaces = IdentifierNamespace.findAllByValue(ns.toLowerCase())

    switch ( namespaces.size() ) {
      case 0:
        if (ns_create) {
          namespace = new IdentifierNamespace(value:ns.toLowerCase()).save(failOnError:true);
        }
        break;
      case 1:
        namespace = namespaces[0]
        break;
      default:
        throw new RuntimeException("Multiple Namespaces with value ${ns}");
        break;
    }

    if (namespace) {
      def norm_id = Identifier.normalizeIdentifier(value)
      identifier = Identifier.findByNamespaceAndNormname(namespace,norm_id)

      def final_val = value
      if (!identifier) {
        if (namespace.family == 'isxn') {
          final_val = final_val.replaceAll("x","X")
        }

        identifier = new Identifier(namespace:namespace, value:final_val, normname:norm_id).save(flush:true, failOnError:true)
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
