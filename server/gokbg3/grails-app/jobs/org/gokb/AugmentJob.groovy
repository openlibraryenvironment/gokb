package org.gokb

import org.gokb.cred.*
import org.gokb.TitleAugmentService

class AugmentJob {

  static concurrent = false

  def titleAugmentService

  // Every five minutes
  static triggers = {
    cron name: 'TitleAugmentJobTrigger', cronExpression: "0 0 * * * ?", startDelay:600000
  }

  def execute() {
    aug()
  }

  def aug() {
    if (grailsApplication.config.gokb.zdbAugment.enabled) {
      log.debug("Attempting to augment titles");
      def status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
      def idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
      def zdbNs = IdentifierNamespace.findByValue('zdb')

      // find the next 100 titles that don't have a suncat ID
      def journals_without_zdb_id = JournalInstance.executeQuery("select ti from JournalInstance as ti where ti.status = :current and not exists ( Select ci from Combo as ci where ci.type = :ctype and ci.fromComponent = ti and ci.toComponent.namespace = :ns )",[current: status_current, ctype: idComboType, ns: zdbNs])

      log.debug("Processing ${journals_without_zdb_id.size()}");

      journals_without_zdb_id.each { ti ->
        log.debug("Attempting augment on ${ti.id} ${ti.name}");
        titleAugmentService.augment(ti)
      }
    }
  }
}
