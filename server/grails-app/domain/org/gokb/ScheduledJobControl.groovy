package org.gokb

import org.gokb.cred.RefdataValue
import java.time.LocalDateTime

class ScheduledJobControl {

  RefdataValue jobType
  LocalDateTime lastStart
  LocalDateTime lastStartComplete
  LocalDateTime lastEnd
  LocalDateTime lastEndComplete

  static constraints = {
    jobType (validator: { val, obj ->
      if (val) {
        if (val.owner?.label != "Job.Type") {
          return ['wrongRefdataCategory']
        }
        List<ScheduledJobControl> dupes = ScheduledJobControl.findAllByJobType(val)

        if (dupes?.size() > 0 && dupes.any { it != obj }) {
          return ['notUnique']
        }
      }
      else {
        return ['notNull']
      }
    })
  }

}
