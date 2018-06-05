package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*
import static grails.async.Promises.*


@Log4j
class BookInstance extends TitleInstance {

  @Transient
  def titleLookupService

  String editionNumber
  String editionDifferentiator
  String editionStatement
  String volumeNumber
  Date dateFirstInPrint
  Date dateFirstOnline
  String summaryOfContent

 static mapping = {
    includes TitleInstance.mapping
            editionNumber column:'bk_ednum'
    editionDifferentiator column:'bk_editionDifferentiator'
         editionStatement column:'bk_editionStatement'
             volumeNumber column:'bk_volume'
         dateFirstInPrint column:'bk_dateFirstInPrint'
          dateFirstOnline column:'bk_dateFirstOnline'
         summaryOfContent column:'bk_summaryOfContent'
  }

  static constraints = {
            editionNumber (nullable:true, blank:false)
    editionDifferentiator (nullable:true, blank:false)
         editionStatement (nullable:true, blank:false)
             volumeNumber (nullable:true, blank:false)
         dateFirstInPrint (nullable:true, blank:false)
          dateFirstOnline (nullable:true, blank:false)
         summaryOfContent (nullable:true, blank:false)
  }

  /**
   * Auditable plugin, on change
   *
   * See if properties that might impact the mapping of this instance to a work have changed.
   * If so, fire the appropriate event to cause a remap. 
   */
  @Transient
  def onChange = { oldMap,newMap ->

    log.debug("BookInstance::onChange handler");
    println("onChange handler");

    // Currently, serial items are mapped based on the name of the journal. We may need to add a discriminator property
    if ( ( oldMap.name != newMap.name ) ||
         ( oldMap.editionStatement != newMap.editionStatement ) ||
         ( oldMap.componentDiscriminator != newMap.componentDiscriminator ) ) {
      log.debug("BookInstance::onChange detected an update to properties that might change the work mapping. Looking up");
      submitRemapWorkTask(newMap);
    }
  }

  @Transient
  def onSave = { newMap ->  
    log.debug("BookInstance::onSave handler");
    submitRemapWorkTask(newMap);
  }

  def submitRemapWorkTask(newMap) {
    log.debug("BookInstance::submitRemapWorkTask");
    def tls = grailsApplication.mainContext.getBean("titleLookupService")
    def map_work_task = task {
      // Wait for the onSave to complete, and the system to release the session, thus freeing the data to
      // other transactions
      synchronized(this) {
        Thread.sleep(3000);
      }
      tls.remapTitleInstance('org.gokb.cred.BookInstance:'+this.id)
    }

    // We cannot wait for the task to complete as the transaction has to complete in order
    // for the Instance to become visible to other transactions. Therefore there has to be
    // a delay between completing the Instance update, and attempting to resolve the work.
    onComplete([map_work_task]) { mapResult ->
      // Might want to add a message to the system log here
    }
  }

}
