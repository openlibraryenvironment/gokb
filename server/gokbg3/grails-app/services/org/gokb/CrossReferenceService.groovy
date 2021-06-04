package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import groovy.util.logging.*
import org.gokb.cred.Package

@Slf4j
class CrossReferenceService {

  //@Async
  def xRefPkg(def rjson, boolean addOnly, boolean fullsync, def auto, def request_locale, def request_user, Job job = null) {
    Package.withNewSession {
      CrossRefPkgRun myRun = new CrossRefPkgRun(rjson, addOnly, fullsync, auto, request_locale, request_user)
      return myRun.work(job)
    }
  }

  //@Async
  def updatePackage(def rjson, boolean addOnly, boolean fullsync, def auto, def request_locale, def request_user, Job job = null) {
    Package.withNewSession {
      UpdatePkgTippsRun myRun = new UpdatePkgTippsRun(rjson, addOnly, fullsync, auto, request_locale, request_user)
      return myRun.work(job)
    }
  }
}
