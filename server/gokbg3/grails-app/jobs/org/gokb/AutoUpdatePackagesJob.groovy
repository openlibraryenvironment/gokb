package org.gokb

import groovyx.net.http.RESTClient
import org.gokb.cred.Package
import org.gokb.cred.Source

import java.util.regex.Matcher
import java.util.regex.Pattern

import static groovyx.net.http.Method.GET

class AutoUpdatePackagesJob {

  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Cron timer.            
    cron name: 'AutoUpdatePackageTrigger', cronExpression: "0 0 6 * * ? *" // daily at 6:00 am
  }

  def execute() {
    if (!concurrent) {
      concurrent = true;
      if (grailsApplication.config.gokb.packageUpdate.enabled && grailsApplication.config.gokb.ygorUrl) {
        log.debug("Beginning scheduled auto update packages job.")
        def ygorBaseUrl = grailsApplication.config.gokb.ygorUrl
        def udateTrigger
        def respData
        // find all updateable packages
        def updPacks = Package.executeQuery("from Package p where p.updateToken is not null and p.source != null and p.source.automaticUpdates = true and (p.source.lastRun = null or p.source.lastRun < current_date)")
        updPacks.each { Package p ->
          if (needsUpdate(p)) {
            def error = false
            def path = "/enrichment/processGokbPackage?pkgId=${p.id}&updateToken=${p.updateToken.value}"
            udateTrigger = new RESTClient(ygorBaseUrl + path)
            try {
              udateTrigger.request(GET) { request ->
                response.success = { resp, data ->
                  respData = data
                  // wait for ygor to finish the enrichment
                  def running = true
                  def statusService = new RESTClient(ygorBaseUrl + "/enrichment/getStatus?jobId=${respData.jobId}")
                  while (running) {
                    log.debug("checking Ygor update Process ${respData.jobId}")
                    statusService.request(GET) { req ->
                      response.success = { statusResp, statusData ->
                        running = statusData.uploadStatus in ['PREPARATION', 'STARTED']
                        log.debug("status of job ${respData.gokbJobId}: ${statusData.uploadStatus}")
                        sleep(300000) // 5 min
                      }
                      response.failure { statusResp ->
                        log.error("autoUpdateStatus Error - ${statusResp}")
                        running = false
                        error = true
                      }
                    }
                  }
                }
                response.failure = { resp ->
                  log.error("autoUpdatePackage Error - ${resp}");
                  error = true
                }
              }
            }
            catch (Exception e) {
              e.printStackTrace();
            }
            if (!error) {
              p.source.lastRun = new Date()
            }
          }
        }
        log.info("auto update packages job completed.")
      } else {
        log.debug("automatic package update is not enabled - set config.gokb.packageUpdate_enabled = true and config.gokb.ygorUrl in config to enable");
      }
    }
    concurrent = false
  }

  private boolean needsUpdate(Package pack) {
    Source src = pack.source
    if (src.lastRun == null) {
      return true;
    }
    if (src.frequency != null) {
      Pattern pattern = Pattern.compile("\\s*(\\d*)\\s*([a-zA-Z]*)\\s*");
      Matcher matcher = pattern.matcher(src.frequency);
      int days
      if (matcher.find()) {
        def length = [
          D: 1,
          T: 1,
          W: 7,
          M: 30,
          Q: 91,
          H: 182,
          J: 365,
          Y: 365]
        def interval = matcher.group(2)
        def number = matcher.group(1)
        days = Integer.parseInt(number) * length[interval.toUpperCase()]

        Date today = new Date()
        Date due = src.lastRun.plus(days)
        if (due.before(today)) {
          return true
        }
      }
    }
    return false
  }
}
