package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*
import static grails.async.Promises.*


@Log4j
class BookInstance extends TitleInstance {

  String editionNumber
  String editionDifferentiator
  String editionStatement
  String volumeNumber
  Date dateFirstInPrint
  Date dateFirstOnline
  String summaryOfContent

 static mapping = {
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

    // Currently, serial items are mapped based on the name of the journal. We may need to add a discriminator property
    if ( ( oldMap.name != newMap.name ) ||
         ( oldMap.editionNumber != newMap.editionNumber ) ||
         ( oldMap.componentDiscriminator != newMap.componentDiscriminator ) ) {
      def map_work_task = task {
        tls = grailsApplication.mainContext.getBean("titleLookupsService")
        tls.remapTitleInstance('org.gokb.cred.BookInstance:'+newMap.id)
      }

      onComplete([map_work_task]) { mapResult ->
        // Might want to add a message to the system log here
      }
    }
  }

  def remapWork() {
    log.debug('remapWork');
    // BKM:TITLE + then FIRSTAUTHOR if duplicates found

  }

}
