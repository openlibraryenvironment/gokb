package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import groovy.util.logging.*
import org.gokb.cred.Package

@Slf4j
class CrossReferenceService {

  def xRefPkg(def rjson, boolean addOnly, boolean fullsync, def auto, def request_locale, def request_user, Job job = null) {
    CrossRefPkgRun myRun = new CrossRefPkgRun(rjson, addOnly, fullsync, auto, request_locale, request_user)
    return Package.withNewSession { myRun.work(job) }
  }

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
