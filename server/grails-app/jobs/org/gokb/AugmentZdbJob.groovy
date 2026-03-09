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

  static final String query_full = '''from JournalInstance as ti
                              where ti.status = :current
                              and (
                                ti.dateCreated > :lastRun
                                or (
                                  not exists (
                                    Select ci from Combo as ci
                                    where ci.type = :ctype
                                    and ci.status = :cstatus
                                    and ci.fromComponent = ti
                                    and ci.toComponent.namespace = :ns
                                  )
                                  and exists (
                                    Select ci from Combo as ci
                                    where ci.type = :ctype
                                    and ci.status = :cstatus
                                    and ci.fromComponent = ti
                                    and ci.toComponent.namespace IN (:issns)
                                  )
                                )
                              )'''


  static final String query_new_only = '''from JournalInstance as ti
                              where ti.status = :current
                              ti.dateCreated > :lastRun'''

  public void execute(JobExecutionContext context) {
    if (grailsApplication.config.getProperty('gokb.zdbAugment.enabled', Boolean.class)) {
      def active_jobs = concurrencyManagerService.getActiveJobsForType(RefdataCategory.lookup("Job.Type", "Sync ZDB data"))

      if (!active_jobs) {
        Map result = [
          result:'STARTED',
          counts:[:]
        ]
        JobDataMap dataMap = context.mergedJobDataMap
        dataMap.put('start', new Date())
        dataMap.put('progress', '0')
        dataMap.put('errors', 0)

        log.info("Starting ZDB augment job.")
        Float reduced_rate = null
        def status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
        def idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        def combo_active = RefdataCategory.lookup("Combo.Status", "Active")
        def zdbNs = IdentifierNamespace.findByValue('zdb')d
        def issnNs = []
        issnNs << IdentifierNamespace.findByValue('issn')
        issnNs << IdentifierNamespace.findByValue('eissn')
        int offset = 0
        ZonedDateTime zdt_minus_one = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).minus(1, ChronoUnit.HOURS)
        boolean run_full_update = (ZonedDateTime.now(ZoneId.of("Europe/Berlin")).getHour() == 23) // -> 23:30
        Date lastStart = context.getPreviousFireTime() ?: Date.from(zdt_minus_one.toInstant())

        def qry_params = [
          current: status_current,
          ctype: idComboType,
          cstatus: combo_active,
          ns: zdbNs,
          issns: issnNs,
          lastRun: lastStart
        ]

        def count_journals_without_zdb_id = JournalInstance.executeQuery("select count(ti.id) ${run_full_update ? query_full : query_new_only}".toString(), qry_params)[0]
        def journals_without_zdb_id = JournalInstance.executeQuery("select ti.id ${run_full_update ? query_full : query_new_only}".toString(), qry_params)

        log.debug("Processing ${count_journals_without_zdb_id}")

        for (ti_id in journals_without_zdb_id) {
          def ti = TitleInstance.get(ti_id)
          log.debug("Attempting augment on ${ti.id} ${ti.name}")

          if (reduced_rate) {
            sleep((int) (reduced_rate * 1000))
          }

          def augment_result = titleAugmentService.augmentZdb(ti)

          if (!result[augment_result.result]) {
            result[augment_result.result] = 1
          }
          else {
            result[augment_result.result]++
          }

          if (augment_result.result == 'ERROR_RESPONSE') {
            if (augment_result.status == 503) {
              result.result = 'CANCELLED_UNAVAILABLE'
              break
            }
            else if (augment_result.status == 429 && augment_result.rate_limit) {

              try {
                reduced_rate = Float.parseFloat(augment_result.rate_limit)
              }
              catch (Exception e) {
                log.error("Unable to parse rate limit ${augment_result.rate_limit}!")
                result.result = 'CANCELLED_RATE_LIMIT_PARSE_ERROR'
                break
              }

              if (reduced_rate > 1) {
                result.result = 'CANCELLED_MAX_RATE_LIMIT'
                break
              }
            }
          }

          offset++

          if (offset % 50 == 0) {
            cleanUpGorm()
          }

          dataMap.progress = "${Math.floor(offset.div(count_journals_without_zdb_id) * 100)}%".toString()

          if (interrupted || Thread.currentThread().isInterrupted()) {
            result.result = 'INTERRUPTED'
            break
          }
        }

        if (result.result == 'STARTED') {
          result.result = 'FINISHED'
        }

        log.info("Finished ZDB augment job, augmenting ${count_journals_without_zdb_id} Journals. (${result})")
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
    TitleInstance.withTransaction {
      def session = sessionFactory.currentSession
      session.flush()
      session.clear()
    }
  }
}
