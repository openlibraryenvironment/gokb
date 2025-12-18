package org.gokb

import org.gokb.cred.*

class ReviewRequestNotificationJob {

  def curatoryGroupAlertingService
  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Set from Bootstrap
  }

  def execute() {
    if (grailsApplication.config.getProperty('gokb.reviewRequestNotification.enabled', Boolean, false)) {
      curatoryGroupAlertingService.triggerDailyReviewsAlerts()
    } else {
      log.debug("daily reviews notification is not enabled - set gokb.reviewRequestNotification.enabled = true in app config to enable")
    }
  }
}
