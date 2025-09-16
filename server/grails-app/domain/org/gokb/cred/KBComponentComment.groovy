package org.gokb.cred

class KBComponentComment {

  KBComponent owner
  RefdataValue language

  Date dateCreated
  Date lastUpdated

  String value

  static mapping = {
    id column:'cc_id'
    version column:'cc_version'
    owner column:'cc_kbc_fk'
    value column:'cc_value', type: 'text'
    language column:'cc_language_rv_fk'
  }

  static constraints = {
    value (nullable:false, blank:false)
    language (nullable:true, blank:false)
  }

  String getLogEntityId() {
    "${this.class.name}:${id}"
  }

  static belongsTo = [
    owner: KBComponent
  ]

  static jsonMapping = [
    'ignore': [
      'owner'
    ]
  ]
}
