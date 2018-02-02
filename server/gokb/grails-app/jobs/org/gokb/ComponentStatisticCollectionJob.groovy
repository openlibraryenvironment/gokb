package org.gokb

class ComponentStatisticCollectionJob {

  def grailsApplication
  def ComponentStatisticService

  def concurrent = false

  // First of the month
  static triggers = {
    cron name: 'ComponentStatisticCollectionJobTrigger', cronExpression: "0 0/5 * * * ?", startDelay:200000
  }

  def execute() {
    log.debug("Beginning scheduled statistics update job.")
    if (grailsApplication.config.enable_statsrewrite) {
      log.debug("Also updating existing stats.")
      ComponentStatisticService.updateCompStats(12,0,true)
    }
    else{
      log.debug("Not updating existing stats.")
      ComponentStatisticService.updateCompStats()
    }
  }
}
