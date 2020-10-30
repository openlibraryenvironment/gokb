package org.gokb.cred
import groovy.transform.Synchronized
import java.util.regex.Pattern
import groovy.util.logging.*


@Slf4j
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

  public String getRestPath() {
    return "/identifiers"
  }

  static jsonMapping = [
    'ignore': [
      'lastUpdatedBy',
      'dateCreated',
      'editStatus',
      'name',
      'status',
      'lastUpdated',
      'description',
      'source',
      '_links'
    ],
    'defaultLinks': [
      'namespace'
    ]
  ]

  static constraints = {
    namespace (nullable:false, blank:false)
    value (validator: { val, obj ->
      if (!val || !val.trim()) {
        return ['notNull']
      }

      def norm_id = Identifier.normalizeIdentifier(val)
      def dupes = Identifier.findAllByNamespaceAndNormname(obj.namespace, norm_id)
      def pattern = obj.namespace.pattern ? ~"${obj.namespace.pattern}" : null
      def isDupe = false

      dupes.each { d ->
        if (d != obj) {
          isDupe = true
        }
      }
      if (isDupe) {
        return ['notUnique']
      }

      if ( pattern && !(val ==~ pattern) )  {
        return ['illegalIdForm']
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
