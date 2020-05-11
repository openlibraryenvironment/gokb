package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*
import static grails.async.Promises.*

@Slf4j
class JournalInstance extends TitleInstance {

  private static refdataDefaults = [
    "TitleInstance.medium"		: "Journal"
  ]

  static mapping = {
    includes TitleInstance.mapping
  }

  static constraints = {
  }

  @Override
  String getLogEntityId() {
      "${this.class.name}:${id}"
  }

  @Override
  public String getNiceName() {
    return "Journal";
  }

  public static final String restPath = "/titles"

  /**
   * Auditable plugin, on change
   *
   * See if properties that might impact the mapping of this instance to a work have changed.
   * If so, fire the appropriate event to cause a remap.
   */

  def afterUpdate() {
    // Currently, serial items are mapped based on the name of the journal. We may need to add a discriminator property
    if ( ( hasChanged('name') ) ||
         ( hasChanged('componentDiscriminator') )) {
//       submitRemapWorkTask();
    }
    touchAllDependants()
  }


  // audit plugin, onSave fires on a new item - we always want to map a work in this case, so directly call and wait
  def afterInsert() {

//     submitRemapWorkTask();
  }

  def submitRemapWorkTask() {
    log.debug("BookInstance::submitRemapWorkTask");
    def tls = grailsApplication.mainContext.getBean("titleLookupService")
    def map_work_task = task {
      // Wait for the onSave to complete, and the system to release the session, thus freeing the data to
      // other transactions
      synchronized(this) {
        Thread.sleep(2000);
      }

      tls.remapTitleInstance('org.gokb.cred.JournalInstance:'+this.id)
    }

    // We cannot wait for the task to complete as the transaction has to complete in order
    // for the Instance to become visible to other transactions. Therefore there has to be
    // a delay between completing the Instance update, and attempting to resolve the work.
    // thats why we use onComplete instead of map_work_task.get()
    onComplete([map_work_task]) { mapResult ->
      // Might want to add a message to the system log here
    }
  }

}
