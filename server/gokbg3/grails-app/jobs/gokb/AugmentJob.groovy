package gokb

import org.gokb.cred.*

class AugmentJob {

  def titleAugmentService

  // Every five minutes
  static triggers = {
    cron name: 'CollectJobTrigger', cronExpression: "0 0/5 * * * ?", startDelay:600000
  }

  def execute() {
  }

  def aug() {
    log.debug("Attempting to augment titles");
    def status_current = RefdataCategory.lookup("KBComponent.Status", "Current")

    // find the next 100 titles that don't have a suncat ID
    def journals_without_zdb_id = JournalInstance.executeQuery("select ti from JournalInstance as ti where ti.status = :current and not exists ( Select ii from ti.ids as ii where ii.identifier.ns.ns = 'zdb' )",[current: status_current],[max:100])

    log.debug("Processing ${journals_without_zdb_id.size()}");

    journals_without_zdb_id.each { ti ->
      log.debug("Attempting augment on ${ti.id} ${ti.name}");
      titleAugmentService.augment(ti)
    }
  }
}
