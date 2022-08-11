package org.gokb;

class FTControl {

  String domainClassName
  String activity
  Long lastTimestamp
  Long lastId

  static constraints = {
    lastId (nullable:true, blank:false)
  }
}

