package org.gokb

import com.k_int.ConcurrencyManagerService.Job
import groovy.util.logging.*

@Slf4j
class CrossReferenceService {

  //@Async
  def xRefPkg(def rjson, boolean addOnly, boolean fullsync, def request_locale, def request_user, Job job = null) {
    CrossRefPkgRun myRun = new CrossRefPkgRun(rjson, addOnly, fullsync, request_locale, request_user)
    return myRun.work(job)
  }
}
