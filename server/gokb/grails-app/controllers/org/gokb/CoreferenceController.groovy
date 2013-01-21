package org.gokb

import org.gokb.cred.*;
import grails.gorm.*

class CoreferenceController {

  def index() { 
    def result = [:]
    result.count = -1
    log.debug("coreference::index")
    if ( params.idpart ) {

      log.debug("Lookup ${params.nspart}:${params.idpart}.");

      def q = new DetachedCriteria(Identifier).build {
        if ( params.nspart ) {
          ns {
            eq('ns',params.nspart)
          }
        }
        eq('value',params.idpart)
      }

      def int_id = q.get()

      if ( int_id ) {
        log.debug("Recognised identifier.. find all occurrences");
        def q2 = new DetachedCriteria(KBComponent).build {
          ids {
            eq('identifier',int_id)
          }
        }
        result.identifier = int_id
        result.count = q2.count()
        result.records = q2.list()
        log.debug("result: ${result.identifier} ${result.count} ${result.records}");
      }
    }
    result
  }
}
