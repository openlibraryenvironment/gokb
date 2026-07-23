package org.gokb

import org.gokb.cred.RefdataValue
import java.time.LocalDateTime

class ScheduledJobControl {

  RefdataValue jobTyoe
  LocalDateTime lastStart
  LocalDateTime lastStartComplete
  LocalDateTime lastEnd
  LocalDateTime lastEndComplete

  static constraints = {
    jobTyoe (nullable: false, blank: false)
  }

}
