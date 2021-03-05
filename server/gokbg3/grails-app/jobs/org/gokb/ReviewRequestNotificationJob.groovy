package org.gokb

import org.gokb.cred.*

class ReviewRequestNotificationJob {

  // Every five minutes
  static triggers = {
    cron name: 'ReviewRequestNotificationJobTrigger', cronExpression: "0 0/5 * * * ?", startDelay:500000
  }

  def execute() {
    sendEmails();
  }

  def sendEmails() {
    def pendingRequests = ReviewRequest.findAllByNeedsNotify(Boolean.TRUE)

    def usermap = [:]

    pendingRequests.each { pr ->
      if ( usermap[pr.allocatedTo.id] == null ) {
        usermap[pr.allocatedTo.id] = []
      }

      usermap[pr.allocatedTo.id].add(pr);
    }

    usermap.each { k,v ->
      v.each { pr ->
        println("Email user ${k} about ${pr}");
        pr.needsNotify=Boolean.FALSE
        pr.save()
      }
    }
  }
}
