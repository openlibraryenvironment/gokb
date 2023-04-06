package org.gokb.cred

class BulkImportListConfig {

  String code
  String cfg
  String url
  Boolean automatedUpdate = false
  RefdataValue frequency
  Date lastRun

  static mapping = {
    id column:'bilc_id'
    code maxSize:128, column:'bilc_code'
    cfg type: 'text', column:'bilc_cfg'
    url type: 'text', column:'bilc_url'
    automatedUpdate column:'bilc_automated_update'
    frequency column:'bilc_frequency'
    lastRun column:'bilc_last_run'
 }

  static constraints = {
    code (nullable:false, blank:false)
    cfg (nullable:true, blank:false)
    url (nullable:true, blank:false)
    automatedUpdate (nullable:true, default:false)
    frequency (nullable:true, blank:false)
    lastRun (nullable:true, blank:false)
  }
}
