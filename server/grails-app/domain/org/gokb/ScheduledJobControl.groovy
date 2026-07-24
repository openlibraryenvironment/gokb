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
    jobType (nullable: false, blank: false, unique: true)
  }

}
