package org.gokb.cred

class BulkLoaderConfig {
  
  String code
  String cfg
  
  static mapping = {
    id column:'blc_id'
    code maxSize:128, column:'blc_code'
    cfg type: 'text', column:'blc_cfg'
 }

  static constraints = {
    code (nullable:false, blank:false)
    cfg (nullable:false, blank:false)
  }
}
