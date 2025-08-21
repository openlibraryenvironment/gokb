package org.gokb.cred

class BulkImportListConfig {

  String code
  String cfg
  String url
  Boolean automatedUpdate = false
  Boolean updateOnly = false
  Boolean prependProviderName = false
  RefdataValue frequency
  RefdataValue curatorPolicy
  User owner
  Date lastRun

  static mapping = {
    id column:'bilc_id'
    code maxSize:128, column:'bilc_code'
    cfg type: 'text', column:'bilc_cfg'
    url type: 'text', column:'bilc_url'
    automatedUpdate column:'bilc_automated_update'
    frequency column:'bilc_frequency'
    curatorPolicy column:'bilc_curator_policy'
    lastRun column:'bilc_last_run'
    owner column:'bilc_owner_fk'
    updateOnly column: 'bilc_update_only'
    prependProviderName column: 'bilc_prepend_provider_name'
 }

  static constraints = {
    code (validator: { val, obj ->
      if (obj.hasChanged('code')) {
        if (val && val.trim()) {
          def dupes = BulkImportListConfig.findAllByCodeIlike(val)

          if (dupes?.size() > 0 && dupes.any { it != obj }) {
            return ['notUnique']
          }
        }
        else {
          return ['notNull']
        }
      }
    })
    cfg (nullable:true, blank:false)
    url (nullable:true, blank:false)
    automatedUpdate (nullable:true)
    frequency (nullable:true, blank:false)
    lastRun (nullable:true, blank:false)
    owner (nullable:true, blank:false)
    updateOnly (nullable:true)
    prependProviderName (nullable:true)
  }

  static def refdataFind(params) {
    def result = []
    def ql = BulkImportListConfig.findAllByCodeIlike(params.q)

    if ( ql ) {
      ql.each { id ->
        result.add([id:"${id.class.name}:${id.id}", text: id.code])
      }
    }

    result
  }
}
