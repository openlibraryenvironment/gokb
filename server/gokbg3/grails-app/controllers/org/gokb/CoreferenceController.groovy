package org.gokb

import org.gokb.cred.*;
import grails.gorm.*
import grails.converters.*
import org.springframework.security.access.annotation.Secured;


class CoreferenceController {

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    def result = [:]
    result.count = -1
    log.debug("coreference::index")
    if ( params.idpart ) {

      log.debug("Lookup ${params.nspart}:${params.idpart}.");

      def q = new DetachedCriteria(Identifier).build {
        if ( params.nspart ) {
          namespace {
            eq('value',params.nspart)
          }
        }
        eq('value',params.idpart)
      }

      def matched_ids = q.list()
      log.debug("Query matched ${matched_ids.size()} identifers");

      result.matched_identifiers = []
      if ( matched_ids ) {
        matched_ids.each { int_id ->
          def matched_id = [:]
          log.debug("Recognised identifier.. find all occurrences")
		
  	  ComboCriteria crit = ComboCriteria.createFor(KBComponent.createCriteria())
		
          matched_id.identifier = int_id
          matched_id.records = crit.list {
		crit.add ("ids.id", "eq", int_id.id)
	  }
	  matched_id.count = matched_id.records.size()

	  result.matched_identifiers.add(matched_id);
        }
	result.count = result.matched_identifiers.size()
      }
      log.debug("result: ${result}");
    }

    def api_response;

    if ( ( response.format == 'json' ) || ( response.format == 'xml' ) ) {
      api_response = ['requestedNS':params.nspart,
                       'requestedID':params.idpart, 
                       'count':result.count ?: 0,
                       'matchedIdentifiers':[]]

      result.matched_identifiers?.each { r ->
        def rec_identifier = ['namespace':'gokb',
                              'internalIdentifier':"${r.identifier.class.name}:${r.identifier.id}",
                              'namespace':r.identifier.namespace.value,
                              'value':r.identifier.value,
                              'linkedComponents':[]]

        r.records.each { cr ->
          def rec_identifiers = []
          rec_identifier.linkedComponents.add(['type':cr.class.name,
                                         'id':cr.id,
                                         'name':cr.name,
                                         'gokbIdentifier':"${cr.class.name}:${cr.id}",
                                         'sameAs':rec_identifiers])

          cr.ids.each { rid ->
            rec_identifiers.add(['namespace':rid.namespace.value,'identifier':rid.value])
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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def search() {
  }
}
