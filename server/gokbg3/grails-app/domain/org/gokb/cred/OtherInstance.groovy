package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*
import static grails.async.Promises.*


@Slf4j
class OtherInstance extends TitleInstance {

  @Transient
  def titleLookupService

  String summaryOfContent

  private static refdataDefaults = [
    "TitleInstance.medium"		: "Other"
  ]

  static mapping = {
    includes TitleInstance.mapping
         summaryOfContent column:'bk_summaryOfContent'
  }

  static constraints = {
         summaryOfContent (nullable:true, blank:false)
  }

  /**
   * Auditable plugin, on change
   *
   * See if properties that might impact the mapping of this instance to a work have changed.
   * If so, fire the appropriate event to cause a remap.
   */

  def afterUpdate() {

    // Currently, serial items are mapped based on the name of the journal. We may need to add a discriminator property
    if ( hasChanged('name') ) {
      log.debug("Detected an update to properties for ${id} that might change the work mapping. Looking up");
      submitRemapWorkTask();
    }
  }

  def afterInsert() {
    submitRemapWorkTask();
  }

  def submitRemapWorkTask(newMap) {
    log.debug("OtherInstance::submitRemapWorkTask");
    def tls = grailsApplication.mainContext.getBean("titleLookupService")
    def map_work_task = task {
      // Wait for the onSave to complete, and the system to release the session, thus freeing the data to
      // other transactions
      synchronized(this) {
        Thread.sleep(3000);
      }
      tls.remapTitleInstance('org.gokb.cred.OtherInstance:'+this.id)
    }

    // We cannot wait for the task to complete as the transaction has to complete in order
    // for the Instance to become visible to other transactions. Therefore there has to be
    // a delay between completing the Instance update, and attempting to resolve the work.
    onComplete([map_work_task]) { mapResult ->
      // Might want to add a message to the system log here
    }
  }

}
