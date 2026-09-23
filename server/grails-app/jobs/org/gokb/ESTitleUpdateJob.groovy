package org.gokb

import org.gokb.FTUpdateService
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.TitleInstance
import org.quartz.InterruptableJob
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.UnableToInterruptJobException
import org.springframework.beans.factory.annotation.Autowired

class ESTitleUpdateJob implements InterruptableJob{

  // Allow only one run at a time.
  static concurrent = false

  @Autowired
  FTUpdateService ftUpdateService

  static triggers = {
    // Cron timer.
    cron name: 'ESTitleUpdateTrigger', cronExpression: "0 * * * * ?", startDelay:120000
  }

  public void execute(JobExecutionContext context) {
    if ( grailsApplication.config.getProperty('gokb.ftupdate_enabled', Boolean, false) ) {
      log.debug ("Beginning scheduled title es update job.")

      RefdataValue indexJobType = RefdataCategory.lookup("Job.Type", "ESTitleUpdateJob")
      JobDataMap dataMap = context.mergedJobDataMap
      ftUpdateService.executeUpdateJobForIndex(indexJobType, null, dataMap)
      log.debug ("ESTitleUpdateJob completed.")
    }
    else {
      log.debug("FTUpdate is not enabled - set config.ftupdate_enabled = true in config to enable")
    }
  }

  public void interrupt () throws UnableToInterruptJobException {
    log.info("Interrupting ES Tipp Update job ..")
  }

}
