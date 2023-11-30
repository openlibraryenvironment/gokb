package org.gokb

import org.gokb.cred.RefdataCategory
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDateTime

class TippAccessStatusUpdateJob {

  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // See Bootstrap.groovy
  }

  def tippService

  def execute() {
    tippService.statusUpdate()
  }
}
