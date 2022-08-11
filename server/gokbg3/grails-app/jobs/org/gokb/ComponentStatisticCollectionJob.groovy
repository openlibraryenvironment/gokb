package org.gokb

class ComponentStatisticCollectionJob {

  static concurrent = false

  ComponentStatisticService componentStatisticService

  static triggers = {
    cron name: 'ComponentStatisticCollectionJobTrigger', cronExpression: "0 0/5 * * * ?", startDelay:700000
  }

  def execute() {
    log.debug("Beginning scheduled statistics update job.")
    if (grailsApplication.config.enable_statsrewrite) {
      log.debug("Also updating existing stats.")
      componentStatisticService.updateCompStats(12,0,true)
    }
    else{
      log.debug("Not updating existing stats.")
      componentStatisticService.updateCompStats()
    }
  }
}
