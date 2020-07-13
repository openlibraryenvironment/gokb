package org.gokb

import org.gokb.FTUpdateService
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import grails.gorm.transactions.Transactional

class ESUpdateJob {
  
  // Allow only one run at a time.
  static concurrent = false

  def FTUpdateService
  def concurrencyManagerService
  
  static triggers = {
    // Cron timer.            
    cron name: 'ESUpdateTrigger', cronExpression: "0 0/5 * * * ?", startDelay:500000
  }

  @Transactional
  def execute() {
    if ( grailsApplication.config.gokb.ftupdate_enabled ) {
      log.debug ("Beginning scheduled es update job.")
      Job j = concurrencyManagerService.createJob { Job j ->
        FTUpdateService.updateFTIndexes(j);
      }.startOrQueue()

      j.description = "Update Free Text Indexes (Scheduled)"
      j.startTime = new Date()
    }
    else {
      log.debug("FTUpdate is not enabled - set config.ftupdate_enabled = true in config to enable");
    }
  }
}
