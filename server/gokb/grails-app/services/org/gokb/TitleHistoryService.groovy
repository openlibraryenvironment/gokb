package org.gokb


import grails.transaction.Transactional
import org.gokb.FTControl
import org.hibernate.ScrollMode
import java.nio.charset.Charset
import java.util.GregorianCalendar


@Transactional
class TitleHistoryService {

  def executorService
  def sessionFactory

  def updateTitleHistories() {
    log.debug("updateTitleHistories");
    def future = executorService.submit({
      doTitleHistoryUpdate()
    } as java.util.concurrent.Callable)
    log.debug("updateTitleHistories returning");
  }

  def doTitleHistoryUpdate() {
    log.debug("doTitleHistoryUpdate");
  }
}
