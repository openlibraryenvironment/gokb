package org.gokb

import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

import org.gokb.cred.*
import org.quartz.InterruptableJob
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.UnableToInterruptJobException

class AugmentZdbJob implements InterruptableJob {

  static concurrent = false

  private boolean interrupted = false

  def titleAugmentService
  def sessionFactory
  def concurrencyManagerService

  static triggers = {
    // see Bootstrap.groovy
  }

  static final String query = "from JournalInstance as ti where ti.status = :current and (ti.dateCreated > :lastRun or (not exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace = :ns) and exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace IN :issns)))"

  public void execute(JobExecutionContext context) {
    if (grailsApplication.config.gokb.zdbAugment.enabled) {
      def active_jobs = concurrencyManagerService.getActiveJobsForType(RefdataCategory.lookup("Job.Type", "Sync ZDB data"))

      if (!active_jobs) {
        JobDataMap dataMap = context.mergedJobDataMap
        dataMap.put('start', new Date())
        dataMap.put('progress', '0')
        dataMap.put('errors', 0)

        log.info("Starting ZDB augment job.")
        def status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
        def idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        def zdbNs = IdentifierNamespace.findByValue('zdb')
        def issnNs = []
        issnNs << IdentifierNamespace.findByValue('issn')
        issnNs << IdentifierNamespace.findByValue('eissn')
        int offset = 0
        int batchSize = 50
        Instant start = Instant.now()
        ZonedDateTime zdt = ZonedDateTime.ofInstant(start, ZoneId.systemDefault()).minus(1, ChronoUnit.HOURS)
        Date startDate = Date.from(zdt.toInstant())

        def count_journals_without_zdb_id = JournalInstance.executeQuery("select count(ti.id) ${query}".toString(), [current: status_current, ctype: idComboType, ns: zdbNs, issns: issnNs, lastRun: startDate])[0]

        while (offset < count_journals_without_zdb_id) {
          def journals_without_zdb_id = JournalInstance.executeQuery("select ti.id ${query}".toString(), [current: status_current, ctype: idComboType, ns: zdbNs, issns: issnNs, lastRun: startDate], [offset: offset, max: batchSize])

          log.debug("Processing ${count_journals_without_zdb_id}")

          for (ti_id in journals_without_zdb_id) {
            def ti = TitleInstance.get(ti_id)
            log.debug("Attempting augment on ${ti.id} ${ti.name}")
            def result = titleAugmentService.augmentZdb(ti)

            if (interrupted || Thread.currentThread().isInterrupted()) {
              break
            }
          }
          cleanUpGorm()
          offset += batchSize
          dataMap.progress = "${Math.floor(offset.div(count_journals_without_zdb_id) * 100)}%".toString()

          if (interrupted || Thread.currentThread().isInterrupted()) {
            break
          }
        }
        log.info("Finished ZDB augment job, augmenting ${count_journals_without_zdb_id} Journals.")
        dataMap.remove('progress')
        interrupted = false
      }
      else {
        log.info("Not starting ZDB augment job because of an already running manually triggered job")
      }
    }
  }

  public void interrupt () throws UnableToInterruptJobException {
    log.info("Interrupting ZBD augment job ..")
    interrupted = true
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM")
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }
}
