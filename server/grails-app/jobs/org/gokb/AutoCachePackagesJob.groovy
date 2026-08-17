package org.gokb

import org.gokb.cred.Package
import org.quartz.InterruptableJob
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.UnableToInterruptJobException

class AutoCachePackagesJob implements InterruptableJob {

  static concurrent = false

  private boolean interrupted = false

  def packageCachingService
  RuntimeVariableService runtimeVariableService

  static triggers = {
    // Cron timer.
    cron name: 'AutoCachePackageTrigger', cronExpression: "0 * * * * ? *", startDelay:30000
  }

  public void execute(JobExecutionContext context) {
    if (runtimeVariableService.getRuntimeVariable("disablePackageCaching") && runtimeVariableService.getRuntimeVariable("disablePackageCaching") == "true") {
      log.info("AutoCachePackagesJob not started because of Environment-Variable: " + runtimeVariableService.getRuntimeVariables())
    }
    else if (grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false)) {
      JobDataMap dataMap = context.mergedJobDataMap
      dataMap.put('start', new Date())
      dataMap.put('progress', '0')
      dataMap.put('errors', 0)

      log.debug("Beginning scheduled auto cache packages job.")
      packageCachingService.cachePackages()
      log.debug("Finished scheduled package caching ..")
    }
  }

  public void interrupt () throws UnableToInterruptJobException {
    log.info("Interrupting package caching job ..")
    interrupted = true
  }
}
