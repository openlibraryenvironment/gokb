package org.gokb.cred

import gokbg3.MessageService
import grails.util.Holders
import groovy.transform.Synchronized
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

import javax.persistence.Transient
import java.util.regex.Pattern
import groovy.util.logging.*


@Slf4j
class Identifier extends KBComponent {

  static def messageService

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
    'ignore'      : [
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
    namespace(nullable: false, blank: false)
    value(validator: { val, obj ->
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

      if (pattern && !(val ==~ pattern)) {
        return ['illegalIdForm']
      }
    })
  }

  static mapping = {
    includes KBComponent.mapping
    value column: 'id_value', index: 'id_value_idx'
    namespace column: 'id_namespace_fk', index: 'id_namespace_idx'
  }

  static manyByCombo = [
    identifiedComponents: KBComponent
  ]

  static mappedByCombo = [
    identifiedComponents: 'ids',
  ]

  @Override
  protected def generateNormname() {
    if (!normname && value) {
      normname = Identifier.normalizeIdentifier(value)
    }
  }

  public static normalizeIdentifier(String id) {
    return id.toLowerCase().trim().replaceAll("\\W", "")
  }

  @Override
  protected def generateShortcode() {
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

  @Transient
  public static def validateDTOs(JSONArray identifierDTOs, Locale locale) {
    def id_errors = [:]
    def to_remove = []
    identifierDTOs?.each { idobj ->
      def id_def = [:]
      def ns_obj = null
      if (idobj instanceof Map) {
        def id_ns = idobj.type ?: (idobj.namespace ?: null)

        id_def.value = idobj.value

        if (id_ns instanceof String) {
          log.debug("Default namespace handling for ${id_ns}..")
          ns_obj = IdentifierNamespace.findByValueIlike(id_ns)
        }
        else if (id_ns) {
          log.debug("Handling namespace def ${id_ns}")
          ns_obj = IdentifierNamespace.get(id_ns)
        }

        if (!ns_obj) {
          id_errors.put('namespace', [message: messageService.resolveCode('default.not.found.message', ["Namespace", id_ns], locale), baddata: id_ns])
          to_remove.add(idobj)
        }
        else {
          id_def.type = ns_obj.value
        }
      }
      else if (idobj instanceof Integer) {
        Identifier the_id = Identifier.get(idobj)

        if (!the_id) {
          id_errors.put(idobj.type, [message: messageService.resolveCode('crossRef.error.lookup', ["Identifier", "ID"], locale), baddata: idobj.value])
          to_remove.add(idobj)
        }
      }
      else {
        log.warn("Missing information in id object ${idobj}")
        id_errors.put(idobj.type, [message: "missing information", baddata: idobj.value])
        to_remove.add(idobj)
      }

      if (ns_obj && id_def.size() > 0) {
        if (!Identifier.findByNamespaceAndNormname(ns_obj, Identifier.normalizeIdentifier(id_def.value))) {
          if (ns_obj.pattern && !(id_def.value ==~ ns_obj.pattern)) {
            log.warn("Validation for ${id_def.type}:${id_def.value} failed!")
            id_errors.put(idobj.type, [message: "validation failed", baddata: idobj.value])
            to_remove.add(idobj)
          }
          else {
            log.debug("New identifier ..")
          }
        }
        else {
          log.debug("Found existing identifier ..")
        }
      }
    }
    identifierDTOs.removeAll(to_remove)
    return id_errors
  }
}
