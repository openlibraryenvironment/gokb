package org.gokb.cred

class BatchControl {

  String domainClassName
  String activity
  Long lastTimestamp

  static constraints = {
  }

  static getLastTimestamp(domainclass, activity) {
    def result = 0;
    def r = BatchControl.findByDomainClassNameAndActivity(domainclass,activity);
    if ( r ) {
      result = r.lastTimestamp;
    }
    result
  }

  static updateLastTimestamp(domainclass, activity, ts) {
    def r = BatchControl.findByDomainClassNameAndActivity(domainclass,activity);
    if ( r ) {
      r.lastTimestamp = ts;
    }
    else {
      r=new BatchControl(domainClassName:domainclass,activity:activity, lastTimestamp:ts)
    }

    r.save(flush:true, failOnError:true);
  }

}
