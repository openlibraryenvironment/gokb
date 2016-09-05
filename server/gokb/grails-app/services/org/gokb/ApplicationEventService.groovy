package org.gokb

class ApplicationEventService {

  def publishApplicationEvent(topic, priority, message) {
    log.debug("publishApplicationEvent(${topic},${priority},${message})");
  }

}
