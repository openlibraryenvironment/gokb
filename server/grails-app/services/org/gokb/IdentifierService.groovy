package org.gokb

import org.gokb.cred.*

class IdentifierService {

  def grailsApplication

  public Map fetchNamespaces(params) {
    Map result = [:]
    boolean no_isxn = params.boolean('no_isxn') ?: false
    String base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
    List<IdentifierNamespace> nss = []

    if (params.targetType != null) {
      RefdataValue rdv_target = RefdataCategory.lookup('IdentifierNamespace.TargetType', params.targetType)

      if (rdv_target) {
        nss = IdentifierNamespace.findAllByTargetType(rdv_target)

        if (params.targetType == 'Title') {
          List other_title_targets = [
            RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Book'),
            RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Journal'),
            RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Database'),
            RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Other')
          ]

          nss = nss + IdentifierNamespace.findAllByTargetTypeInList(other_title_targets)
        }
      }
      else {
        result.errors = ['targetType': [message: 'Unable to reference parameter targetType', baddata: params.targetType]]
      }
    } else {
      nss = IdentifierNamespace.all
    }

    if (!result.errors) {
      if (params.q?.trim()) {
        nss = nss.findAll { it.name.startsWith(params.q.trim()) }
      }

      if (no_isxn) {
        nss = nss.findAll { it.family != 'isxn' }
      }

      nss.each { ns ->
        result.data << [
          name: ns.name,
          value: ns.value,
          targetType: ns.targetType?.value ?: null,
          id: ns.id,
          pattern: ns.pattern,
          family: ns.family,
          baseUrl: ns.baseUrl
        ]
      }
    }

    result
  }
}