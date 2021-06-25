package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import groovy.util.logging.*
import org.gokb.cred.Package
import org.springframework.scheduling.annotation.Async

@Slf4j
class CrossReferenceService {

  def xRefPkg(def rjson, boolean addOnly, boolean fullsync, def auto, def request_locale, def request_user, Job job = null) {
    Package.withSession {
      CrossRefPkgRun myRun = new CrossRefPkgRun(rjson, addOnly, fullsync, auto, request_locale, request_user)
      return myRun.work(job)
    }
  }

  @Async
  def updatePackage(def rjson, boolean addOnly, boolean fullsync, def auto, def request_locale, def request_user, Job job = null) {
    if (!job) {
      Package.withSession {
        UpdatePkgTippsRun myRun = new UpdatePkgTippsRun(rjson, addOnly, fullsync, auto, request_locale, request_user)
        return myRun.work(job)
      }
    }
    Package.withNewSession {
      UpdatePkgTippsRun myRun = new UpdatePkgTippsRun(rjson, addOnly, fullsync, auto, request_locale, request_user)
      return myRun.work(job)
    }
  }
}
