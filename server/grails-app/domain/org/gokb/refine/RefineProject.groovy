package org.gokb.refine

import javax.persistence.Transient

import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.RefdataValue
import org.gokb.cred.User

class RefineProject extends KBComponent {

  public enum Status {
    CHECKED_IN      ("Checked In"),
    CHECKED_OUT      ("Checked Out"),
    INGESTING      ("Ingesting"),
    INGESTED      ("Ingested"),
    INGEST_FAILED    ("Ingest Failed"),
    PARTIALLY_INGESTED  ("Partialy Ingested")

    private name

    private Status (String name) {
      this.name = name
    }

    public String getName () {
      name
    }
  }

  String description
  Date modified
  String file
  User lastCheckedOutBy
  User modifiedBy
  User createdBy
  Long localProjectID
  String hash
  Org provider
  String lastValidationResult
  Long progress
  String possibleRulesString
  String notes
  RefdataValue defaultSupplyMethod
  RefdataValue defaultDataFormat
  String accessUrl
  String dataUrl
  byte[] sourceFile

  // The rows skipped in the ingest process.
  Set<String> skippedTitles = []

  static hasMany = [
    skippedTitles: String,
  ]

  Status projectStatus = Status.CHECKED_OUT

  @Transient
  def lastValidationResultAsMap = null
  @Transient
  def possibleRulesResultAsList = null

  static mapping = {
    description column: 'rp_desc'
    modified column: 'rp_modified'
    file column: 'rp_file'
    //               checkedIn column: 'rp_checked_in'
    lastCheckedOutBy column: 'rp_last_checked_out_by'
    createdBy column: 'rp_created_by'
    modifiedBy column: 'rp_modified_by'
    localProjectID column: 'rp_local_project_id'
    hash column: 'rp_hash'
    provider column: 'rp_prov_fk'
    lastValidationResult column: 'rp_last_validation_result', type: 'text'
    progress column: 'rp_progress'
    possibleRulesString column: 'rp_matching_rules', type: 'text'
    notes column: 'rp_notes'
    projectStatus column: 'rp_project_status'
    // sourceFile column: 'rp_source_file', sqlType:'longblob', lazy: true - Changed -- longblob not supported by pgsql
    sourceFile column: 'rp_source_file', lazy:true
  }


  static constraints = {
    hash nullable: true
    lastCheckedOutBy nullable: true
    description nullable: true
    localProjectID nullable: true
    lastValidationResult nullable: true
    provider nullable: true
    progress nullable: true
    possibleRulesString nullable: true
    notes nullable: true
    projectStatus nullable: false
    defaultSupplyMethod(nullable:true, blank:true)
    defaultDataFormat(nullable:true, blank:true)
    accessUrl(nullable:true, blank:true)
    dataUrl(nullable:true, blank:true)
    sourceFile(nullable:true,blank:false,maxSize: 1024 * 1024 * 1024)
  }

  @Transient
  lastValidationAsMap() {
    if ( lastValidationResult && ( lastValidationResultAsMap == null ) ) {
      lastValidationResultAsMap = grails.converters.JSON.parse(lastValidationResult)
    }
    lastValidationResultAsMap
  }

  @Transient
  possibleRulesAsList() {
    if ( possibleRulesString && ( possibleRulesResultAsList == null ) ) {
      possibleRulesResultAsList = grails.converters.JSON.parse(possibleRulesString)
    }
    possibleRulesResultAsList
  }

}
