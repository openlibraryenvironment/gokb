package org.gokb

import org.gokb.cred.*

class AugmentJob {

  static concurrent = false

  def titleAugmentService
  def sessionFactory

  // Every five minutes
  static triggers = {
    cron name: 'TitleAugmentJobTrigger', cronExpression: "0 30 * * * ? *", startDelay:60000
  }

  def execute() {
    augZdb()
    augEzb()
  }

  def augZdb() {
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

      def count_journals_without_zdb_id = JournalInstance.executeQuery("select count(ti.id) from JournalInstance as ti where ti.status = :current and not exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace = :ns) and exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace IN :issns)",[current: status_current, ctype: idComboType, ns: zdbNs, issns: issnNs])[0]

      // find the next 100 titles that don't have a ZDB-ID
      while (offset < count_journals_without_zdb_id) {
        def journals_without_zdb_id = JournalInstance.executeQuery("select ti.id from JournalInstance as ti where ti.status = :current and not exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace = :ns) and exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace IN :issns)",[current: status_current, ctype: idComboType, ns: zdbNs, issns: issnNs],[offset: offset, max: batchSize])
        log.debug("Processing ${count_journals_without_zdb_id} journals.")
        journals_without_zdb_id.each { ti_id ->
          def ti = TitleInstance.get(ti_id)
          log.debug("Attempting zdb augment on ${ti.id} ${ti.name}")
          titleAugmentService.augmentZdb(ti)
        }
        cleanUpGorm()
        offset += batchSize
      }
      log.info("Finished ZDB augment job, augmenting ${count_journals_without_zdb_id} Journals.")
    }
  }

  def augEzb() {
    if (grailsApplication.config.gokb.ezbAugment.enabled) {
      log.info("Starting EZB augment job.");
      def status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      def idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
      def ezbNs = IdentifierNamespace.findByValue('ezb')
      def issnNs = []
      issnNs << IdentifierNamespace.findByValue('issn')
      issnNs << IdentifierNamespace.findByValue('eissn')
      def journals
      int offset = 0
      def count_journals_without_ezb_id = JournalInstance.executeQuery("select count(ti.id) from JournalInstance as ti where ti.status = :current and not exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace = :ns) and exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace IN :issns)",[current: status_current, ctype: idComboType, ns: ezbNs, issns: issnNs])[0]

      // find the next 100 titles that don't have an EZB ID
      while (offset < count_journals_without_ezb_id) {
        def journals_without_ezb_id = JournalInstance.executeQuery("select ti.id from JournalInstance as ti where ti.status = :current and not exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace = :ns) and exists (Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace IN :issns)",[current: status_current, ctype: idComboType, ns: ezbNs, issns: issnNs],[offset: offset, max: 20])
        log.debug("Processing ${count_journals_without_ezb_id} journals.")
        journals_without_ezb_id.each { ti_id ->
          def ti = TitleInstance.get(ti_id)
          log.debug("Attempting ezb augment on ${ti.id} ${ti.name}");
          titleAugmentService.augmentEzb(ti)
          if (offset++ % 40 == 0) {
            cleanUpGorm()
          }
        }
      }
      log.info("Finished EZB augment job, augmenting ${count_journals_without_ezb_id} Journals.")
    }
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }
}
