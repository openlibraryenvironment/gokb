package org.gokb

import org.gokb.cred.*;
import grails.gorm.*
import grails.converters.*

class CoreferenceController {

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

      def int_id = q.get()

      if ( int_id ) {
        log.debug("Recognised identifier.. find all occurrences");
        def q2 = new DetachedCriteria(KBComponent).build {
          ids {
            eq ("id", int_id.id)
          }
        }
        result.identifier = int_id
        result.count = q2.count()
        result.records = q2.list()
        log.debug("result: ${result.identifier} ${result.count} ${result.records}");
      }
    }

    def api_response;
    if ( ( response.format == 'json' ) || ( response.format == 'xml' ) ) {
      api_response = ['requestedNS':params.nspart,
                       'requestedID':params.idpart, 
                       'gokbIdentifier': result.identifier ? "${result.identifier.class.name}:${result.identifier.id}" : "UNKNOWN",
                       'count':result.count ?: 0,
                       'records':[]]
      result.records?.each { r ->
        def rec_identifiers = []
        rec_identifiers.add(['namespace':'gokb','identifier':"${result.identifier.class.name}:${result.identifier.id}"])
        r.ids.each { rid ->
          rec_identifiers.add(['namespace':rid.namespace.value,'identifier':rid.value])
        }
        api_response.records.add(['type':r.class.name,
                                   'id':r.id,
                                   'name':r.name,
                                   'gokbIdentifier':"${r.class.name}:${r.id}",
                                   'sameAs':rec_identifiers])
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
