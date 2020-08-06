package org.gokb.cred
import groovy.transform.Synchronized
import java.util.regex.Pattern
import groovy.util.logging.*
import grails.validation.ValidationException


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
    def namespaces = IdentifierNamespace.findAllByValueIlike(ns)

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
      def existing = Identifier.executeQuery("from Identifier where normname = ? and namespace = ?",[norm_id, namespace])

      if ( existing.size() == 1 ) {
        identifier = existing[0]
      }
      else if ( existing.size() > 1 ) {
        log.error("Conflicting identifiers found: ${existing}")
        throw new RuntimeException("Found duplicates for Identifier: ${existing}");
      }
      else {
        def final_val = value

        if (!identifier) {
          if (namespace.family == 'isxn') {
            final_val = final_val.replaceAll("x","X")
          }
          log.debug("Creating new Identifier ${namespace}:${value} ..")

          try {
            identifier = new Identifier(namespace:namespace, value:final_val, normname: norm_id).save(flush:true, failOnError:true)
          }
          catch (ValidationException ve) {
            log.debug("Caught validation exception: ${ve.message}")
            if (ve.message.contains('already exists')) {
              def dupe = Identifier.executeQuery("from Identifier where normname = ? and namespace = ?",[norm_id, namespace])

              if (dupe.size() == 1) {
                identifier = dupe[0]
              }
              log.error("Thread synchronization failed for ID ${dupe} ...")
            }
            else {
              throw new ValidationException(ve.message, ve.errors)
            }
          }
        }
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
