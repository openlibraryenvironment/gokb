package gokb

import org.gokb.cred.*

class AugmentJob {

  def titleAugmentService

  // Every five minutes
  static triggers = {
    cron name: 'CollectJobTrigger', cronExpression: "0 0/5 * * * ?", startDelay:60000
  }

  def execute() {
  }

  def aug() {
    log.debug("Attempting to augment titles");

    // find the next 100 titles that don't have a suncat ID
    def titles_without_suncat_id = TitleInstance.executeQuery("select ti from TitleInstance as ti where not exists ( Select ii from ti.ids as ii where ii.identifier.ns.ns = 'SUNCAT' )",[],[max:100])

    log.debug("Processing ${titles_without_suncat_id.size()}");

    titles_without_suncat_id.each { ti ->
      log.debug("Attempting augment on ${ti.id} ${ti.name}");
      titleAugmentService.augment(ti)
    }
  }
}
