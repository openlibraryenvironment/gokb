package org.gokb

import org.gokb.FTUpdateService
import org.gokb.cred.TitleInstance

class ESTitleUpdateJob {

  // Allow only one run at a time.
  static concurrent = false

  def FTUpdateService

  static triggers = {
    // Cron timer.
    cron name: 'ESTitleUpdateTrigger', cronExpression: "0 * * * * ?", startDelay:120000
  }

  def execute() {
    if ( grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false) ) {
      log.debug ("Beginning scheduled title es update job.")
      FTUpdateService.triggerUpdateForClass(TitleInstance.class)
      log.debug ("ESTitleUpdateJob completed.")
    }
    else {
      log.debug("FTUpdate is not enabled - set config.ftupdate_enabled = true in config to enable")
    }
  }
}
