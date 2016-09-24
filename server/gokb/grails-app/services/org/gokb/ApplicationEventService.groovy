package org.gokb

// import grails.events.Listener
import org.gokb.cred.KBComponent

class ApplicationEventService {

  // @Listener(namespace = 'gorm')
  void afterInsert(KBComponent component) {
    log.debug("after save component");
  }

  // @grails.events.Listener(topic = 'DataProblem')
  def dataProblemListener(d){
    log.debug("Data problem ${d}");
  }
}
