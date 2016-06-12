package org.gokb

import org.gokb.cred.*
import org.hibernate.Session

class FolderService {

  // https://www.javacodegeeks.com/2014/11/executorservice-10-tips-and-tricks.html
  // http://sysgears.com/articles/thread-synchronization-in-grails-application-using-hazelcast/

  def executorService
  def sessionFactory

  def enqueTitleList(file, folder_id, config) {
    def future = executorService.submit({
      processTitleList(file, folder_id, config)
    } as java.util.concurrent.Callable)
  }

  def processTitleList(file, folder_id, config) {

    log.debug("processTitleList(${file}, ${folder_id}, ${config})");

    // Open File
    if ( file ) {
      log.debug("Got file ${file}");
    }

    // Process
    // Read as csv file...

    // Delete file
    log.debug("Delete temp file");
    file.delete()

    // Return
    return
  }
}
