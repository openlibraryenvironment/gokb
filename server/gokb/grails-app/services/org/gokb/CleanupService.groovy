package org.gokb

import org.gokb.cred.*;

class CleanupService {

  def expungeDeletedComponents() {

    def result = [:]
    result.report = []

    log.debug("Process delete candidates");

    def delete_candidates = KBComponent.executeQuery('select kbc.id from KBComponent as kbc where kbc.status.value=:deletedStatus',[deletedStatus:'Deleted'])

    delete_candidates.each { component_id ->
      try {
        KBComponent.withNewTransaction {
          log.debug("Expunging ${component_id}");
          def component = KBComponent.get(component_id);
          def expunge_result = component.expunge();
          log.debug(expunge_result);
          result.report.add(expunge_result)
        }
      }
      catch ( Throwable t ) {
        log.error("problem",t);
      }
    }

    log.debug("Done");

    return result
  }
}
