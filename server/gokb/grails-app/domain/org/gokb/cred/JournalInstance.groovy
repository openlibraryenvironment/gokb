package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*
import static grails.async.Promises.*

@Log4j
class JournalInstance extends TitleInstance {

  static mapping = {
  }

  static constraints = {
  }

  /**
   * Auditable plugin, on change
   *
   * See if properties that might impact the mapping of this instance to a work have changed.
   * If so, fire the appropriate event to cause a remap. 
   */
  @Transient
  def onChange = { oldMap,newMap ->

    // Currently, serial items are mapped based on the name of the journal. We may need to add a discriminator property
    if ( ( oldMap.name != newMap.name ) ||
         ( oldMap.componentDiscriminator != newMap.componentDiscriminator ) ) {
      def map_work_task = task { 
        tls = grailsApplication.mainContext.getBean("titleLookupsService")
        tls.remapTitleInstance('org.gokb.cred.JournalInstance:'+newMap.id)
      }

      onComplete([map_work_task]) { mapResult ->
        // Might want to add a message to the system log here
      }
    }
  }

  def remapWork() {
    log.debug('remapWork');

    // SER:TITLE  || BKM:TITLE + then FIRSTAUTHOR
  }
}
