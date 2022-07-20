package org.gokb.cred

class ComponentStatistic {

  String componentType
  int year
  int month
  Long numTotal
  Long numNew

  static constraints = {
    componentType(nullable:false, blank:false)
    year(nullable:false, blank:false)
    month(nullable:false, blank:false)
    numTotal(nullable:false, blank:true)
    numNew(nullable:false, blank:true)
  }
}
