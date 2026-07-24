package org.gokb

import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

import org.gokb.cred.*

class AugmentEzbJob{

  static concurrent = false

  def titleAugmentService
  def sessionFactory

  static final String query = '''from JournalInstance as ti
                                  where ti.status = :current
                                  and (
                                    ti.dateCreated > :lastRun
                                    or (
                                      not exists (
                                        Select ci from ComponentIdentifier as ci
                                        where ci.component = ti
                                        and ci.identifier.namespace = :ns
                                      )
                                      and exists (
                                        Select ci from ComponentIdentifier as ci
                                        where ci.component = ti
                                        and ci.identifier.namespace IN :issns
                                      )
                                    )
                                  )'''

  static triggers = {
    // see Bootstrap.groovy
  }

  def execute() {
    long breakInMs = Long.valueOf(grailsApplication.config.getProperty('gokb.ezbAugment.breakInMs', Long, 0L))
    if (grailsApplication.config.getProperty('gokb.ezbAugment.enabled', Boolean.class)) {
      log.info("Starting EZB augment job.")
      RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      IdentifierNamespace ezbNs = IdentifierNamespace.findByValue('ezb')
      List<IdentifierNamespace> issnNs = []
      issnNs << IdentifierNamespace.findByValue('issn')
      issnNs << IdentifierNamespace.findByValue('eissn')

      int offset = 0
      int batchSize = 50
      Instant start = Instant.now()
      ZonedDateTime zdt = ZonedDateTime.ofInstant(start, ZoneId.systemDefault()).minus(1, ChronoUnit.HOURS)
      Date startDate = Date.from(zdt.toInstant())

      int count_journals_without_ezb_id = JournalInstance.executeQuery("select count(ti.id) ${query}".toString(),[current: status_current, ns: ezbNs, issns: issnNs, lastRun: startDate])[0]

      while (offset < count_journals_without_ezb_id) {
        List<Long> journals_without_ezb_id = JournalInstance.executeQuery("select ti.id ${query}".toString(),[current: status_current, ns: ezbNs, issns: issnNs, lastRun: startDate], [offset: offset, max: batchSize])
        log.debug("Processing ${count_journals_without_ezb_id} journals.")

        journals_without_ezb_id.each { ti_id ->
          def ti = TitleInstance.get(ti_id)
          log.debug("Attempting ezb augment on ${ti.id} ${ti.name}")
          titleAugmentService.augmentEzb(ti)
          Thread.sleep(breakInMs)
        }
        cleanUpGorm()
        offset += batchSize

        if (Thread.currentThread().isInterrupted()) {
          break
        }
      }
      log.info("Finished EZB augment job, augmenting ${count_journals_without_ezb_id} Journals.")
    }
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM")
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }
}
