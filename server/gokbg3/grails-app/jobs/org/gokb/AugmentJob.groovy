package org.gokb

import org.gokb.cred.*
import org.gokb.TitleAugmentService

import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class AugmentJob {

  static concurrent = false

  def titleAugmentService
  def sessionFactory

  // Every five minutes
  static triggers = {
    cron name: 'TitleAugmentJobTrigger', cronExpression: "0 30 * * * ? *", startDelay:60000
  }

  def execute() {
    aug()
  }

  def aug() {
    if (grailsApplication.config.gokb.zdbAugment.enabled) {
      log.info("Starting ZDB augment job.");
      def status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      def idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
      def zdbNs = IdentifierNamespace.findByValue('zdb')
      def issnNs = []
      issnNs << IdentifierNamespace.findByValue('issn')
      issnNs << IdentifierNamespace.findByValue('eissn')
      def journals
      int offset = 0
      int batchSize = 50
      Instant start = Instant.now()
      ZonedDateTime zdt = ZonedDateTime.ofInstant(start, ZoneId.systemDefault()).minus(1, ChronoUnit.HOURS)
      Date startDate = Date.from(zdt.toInstant())

      def query = "from JournalInstance as ti " +
        "where ti.status = :current " +
        "and (" +
          "ti.dateCreated > :lastRun " +
          "or (" +
            "not exists (Select ci from Combo as ci " +
              "where ci.type = :ctype and ci.fromComponent = ti " +
              "and ci.toComponent.namespace = :ns) " +
            "and exists (Select ci from Combo as ci " +
              "where ci.type = :ctype " +
              "and ci.fromComponent = ti " +
              "and ci.toComponent.namespace IN :issns)" +
          ")" +
        ")"


      def count_journals_without_zdb_id = JournalInstance.executeQuery("select count(ti.id) ${query}".toString(), [current: status_current, ctype: idComboType, ns: zdbNs, issns: issnNs, lastRun: startDate])[0]

      // find the next 100 titles that don't have a ZDB-ID
      while (offset < count_journals_without_zdb_id) {
        def journals_without_zdb_id = JournalInstance.executeQuery("select ti.id ${query}".toString(), [current: status_current, ctype: idComboType, ns: zdbNs, issns: issnNs, lastRun: startDate], [offset: offset, max: batchSize])

        log.debug("Processing ${count_journals_without_zdb_id}")

        journals_without_zdb_id.each { ti_id ->
          def ti = TitleInstance.get(ti_id)
          log.debug("Attempting augment on ${ti.id} ${ti.name}")
          titleAugmentService.augment(ti)
        }

        cleanUpGorm()

        offset += batchSize
      }

      log.info("Finished augmenting ${count_journals_without_zdb_id} Journals")
    }
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }
}
