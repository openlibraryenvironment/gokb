package org.gokb

import org.gokb.FTUpdateService
import org.gokb.cred.Org
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.quartz.InterruptableJob
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.UnableToInterruptJobException
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDateTime

class ESOrgUpdateJob implements InterruptableJob {

  // Allow only one run at a time.
  static concurrent = false

  @Autowired
  FTUpdateService ftUpdateService

  static triggers = {
    // Cron timer.
    cron name: 'ESOrgUpdateTrigger', cronExpression: "0 * * * * ?", startDelay:120000
  }

  public void execute(JobExecutionContext context) {
    if ( grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false) ) {
      log.debug ("Beginning scheduled org es update job.")
      RefdataValue orgIndexJobType = RefdataCategory.lookup("Job.Type", "ESOrgUpdateJob")
      JobDataMap dataMap = context.mergedJobDataMap
      ftUpdateService.executeUpdateJobForIndex( orgIndexJobType, null, dataMap)
    }
    else {
      log.debug("FTUpdate is not enabled - set config.ftupdate_enabled = true in config to enable")
    }
  }

  public void interrupt () throws UnableToInterruptJobException {
    log.info("Interrupting ES Org Update job ..")

  }
}
