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
                              and ti.dateCreated > :lastRun'''

  public void execute(JobExecutionContext context) {
    if (grailsApplication.config.getProperty('gokb.zdbAugment.enabled', Boolean.class)) {
      List active_jobs = concurrencyManagerService.getActiveJobsForType(RefdataCategory.lookup("Job.Type", "Sync ZDB data"))

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
        RefdataValue status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
        RefdataValue idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        RefdataValue combo_active = RefdataCategory.lookup("Combo.Status", "Active")
        IdentifierNamespace zdbNs = IdentifierNamespace.findByValue('zdb')
        List issnNs = []
        issnNs << IdentifierNamespace.findByValue('issn')
        issnNs << IdentifierNamespace.findByValue('eissn')
        int offset = 0
        ZonedDateTime zdt_minus_one = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).minus(1, ChronoUnit.HOURS)
        boolean run_full_update = (ZonedDateTime.now(ZoneId.of("Europe/Berlin")).getHour() == 22) // -> 23:30
        Date lastStart = context.getPreviousFireTime() ?: Date.from(zdt_minus_one.toInstant())

        def qry_params = [
          current: status_current,
          lastRun: lastStart
        ]

        if (run_full_update) {
          qry_params = [
            current: status_current,
            lastRun: lastStart,
            ctype: idComboType,
            cstatus: combo_active,
            ns: zdbNs,
            issns: issnNs
          ]
        }

        result.total = JournalInstance.executeQuery("select count(ti.id) ${run_full_update ? query_full : query_new_only}".toString(), qry_params)[0]
        List journals_without_zdb_id = JournalInstance.executeQuery("select ti.id ${run_full_update ? query_full : query_new_only}".toString(), qry_params)

        log.debug("Processing ${result.total}")

        for (ti_id in journals_without_zdb_id) {
          TitleInstance ti = TitleInstance.get(ti_id)
          log.debug("Attempting augment on ${ti.id} ${ti.name}")

          if (reduced_rate) {
            sleep((int) (reduced_rate * 1000))
          }

          Map augment_result = titleAugmentService.augmentZdb(ti)

          if (!result.counts[augment_result.result]) {
            result.counts[augment_result.result] = 1
          }
          else {
            result.counts[augment_result.result]++
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

          dataMap.progress = "${Math.floor(offset.div(result.total) * 100)}%".toString()

          if (interrupted || Thread.currentThread().isInterrupted()) {
            result.result = 'INTERRUPTED'
            break
          }
        }

        if (result.result == 'STARTED') {
          result.result = 'FINISHED'
        }

        log.info("Finished ZDB augment job, augmenting ${offset} Journals. (${result})")
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
