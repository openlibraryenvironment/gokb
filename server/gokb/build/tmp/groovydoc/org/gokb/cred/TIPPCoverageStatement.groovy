package org.gokb.cred

import javax.persistence.Transient

class TIPPCoverageStatement {

  TitleInstancePackagePlatform owner

  Date startDate
  String startVolume
  String startIssue
  String embargo
  String coverageNote
  Date endDate
  String endVolume
  String endIssue

  static belongsTo = [
    owner: TitleInstancePackagePlatform
  ]

  static mapping = {
    startDate column:'tipp_start_date'
    startVolume column:'tipp_start_volume'
    startIssue column:'tipp_start_issue'
    endDate column:'tipp_end_date'
    endVolume column:'tipp_end_volume'
    endIssue column:'tipp_end_issue'
    embargo column:'tipp_embargo'
    coverageNote column:'tipp_coverage_note',type: 'text'
  }

  static constraints = {
    startDate (nullable:true, blank:true)
    startVolume (nullable:true, blank:true)
    startIssue (nullable:true, blank:true)
    endDate (nullable:true, blank:true)
    endVolume (nullable:true, blank:true)
    endIssue (nullable:true, blank:true)
    embargo (nullable:true, blank:true)
    coverageNote (nullable:true, blank:true)
  }

}
