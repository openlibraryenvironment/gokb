package org.gokb

import org.gokb.FTUpdateService

class ESUpdateJob {

  // Allow only one run at a time.
  static concurrent = false

  def FTUpdateService

  static triggers = {
    // Cron timer.
    cron name: 'ESUpdateTrigger', cronExpression: "0 0/5 * * * ?", startDelay:500000
  }

  def execute() {
    if ( grailsApplication.config.gokb.ftupdate_enabled ) {
      log.debug ("Beginning scheduled es update job.")
      FTUpdateService.updateFTIndexes();
      log.debug ("ESUpdateJob completed.")
    }
    else {
      log.debug("FTUpdate is not enabled - set config.ftupdate_enabled = true in config to enable");
    }
  }
}
