package org.gokb

import org.gokb.cred.*;
import grails.gorm.*
import grails.converters.*
import org.springframework.security.access.annotation.Secured;


class CoreferenceController {

  def index() {
    Map result = [:]
    result.count = -1
    log.debug("coreference::index")

    if ( params.idpart ) {
      log.debug("Lookup ${params.nspart}:${params.idpart}.")
      String normVal = Identifier.normalizeIdentifier(params.idpart)
      IdentifierNamespace namespace
      List matched_ids = []

      if (params.nspart) {
        namespace = IdentifierNamespace.findByValueIlike(params.nspart)

        if (!namespace) {
          result.result = 'ERROR'
          result.status = 404
        }
        else {
          matched_ids = Identifier.findAllByNamespaceAndNormname(params.idpart)
        }
      }

      log.debug("Query matched ${matched_ids.size()} identifers");

      result.matched_identifiers = []

      if ( matched_ids ) {
        matched_ids.each { Identifier int_id ->
          def matched_id = [:]
          log.debug("Recognised identifier.. find all occurrences")

  	      ComboCriteria crit = ComboCriteria.createFor(KBComponent.createCriteria())

          matched_id.identifier = int_id

          matched_id.records = int_id.activeIdentifiedComponents

	        matched_id.count = matched_id.records.size()

          result.matched_identifiers.add(matched_id)
        }

	      result.count = result.matched_identifiers.size()
      }

      log.debug("result: ${result}")
    }

    Map api_response = [:]

    if ( ( response.format == 'json' ) || ( response.format == 'xml' ) ) {
      api_response = [
        'requestedNS':params.nspart,
        'requestedID':params.idpart,
        'count':result.count ?: 0,
        'matchedIdentifiers':[]
      ]

      result.matched_identifiers?.each { r ->
        Map rec_identifier = [
          'namespace':'gokb',
          'internalIdentifier':"${r.identifier.class.name}:${r.identifier.id}",
          'namespace':r.identifier.namespace.value,
          'value':r.identifier.value,
          'linkedComponents':[]
        ]

        r.records.each { cr ->
          List rec_identifiers = []

          rec_identifier.linkedComponents.add([
            'type':cr.class.name,
            'id':cr.id,
            'name':cr.name,
            'gokbIdentifier':"${cr.class.name}:${cr.id}",
            'sameAs':rec_identifiers
          ])

          cr.ids.each { rid ->
            rec_identifiers.add(['namespace': rid.namespace.value, 'identifier': rid.value])
          }
        }

        api_response.matchedIdentifiers.add(rec_identifier)
      }
    }


    withFormat {
      html result
      json { render api_response as JSON }
      xml { render api_response as XML }
    }
  }

  def search() {
  }
}
