package org.gokb

import org.gokb.cred.*
import org.gokb.TitleAugmentService

class AugmentJob {

  static concurrent = false

  def titleAugmentService
  def sessionFactory

  // Every five minutes
  static triggers = {
    cron name: 'TitleAugmentJobTrigger', cronExpression: "0 0 22 * * ?", startDelay:60000
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

      def count_journals_without_zdb_id = JournalInstance.executeQuery("select count(ti.id) from JournalInstance as ti where ti.status = :current and not exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace = :ns) and exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace IN :issns)",[current: status_current, ctype: idComboType, ns: zdbNs, issns: issnNs])[0]

      // find the next 100 titles that don't have a suncat ID
      while (offset < count_journals_without_zdb_id) {
        def journals_without_zdb_id = JournalInstance.executeQuery("select ti.id from JournalInstance as ti where ti.status = :current and not exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace = :ns) and exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace IN :issns)",[current: status_current, ctype: idComboType, ns: zdbNs, issns: issnNs],[offset: offset, max: 20])

        log.debug("Processing ${count_journals_without_zdb_id}");

        journals_without_zdb_id.each { ti_id ->
          def ti = TitleInstance.get(ti_id)
          log.debug("Attempting augment on ${ti.id} ${ti.name}");
          titleAugmentService.augment(ti)

          if (offset++ % 40 == 0) {
            cleanUpGorm()
          }
        }
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
