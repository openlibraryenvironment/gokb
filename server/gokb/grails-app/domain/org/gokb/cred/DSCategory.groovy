package org.gokb.cred

import javax.persistence.Transient

class DSCategory {

  String code
  String description

  static mapping = {
    code column:'dscat_code'
    description column:'dscat_desc'
  }

  static constraints = {
    code(nullable:false, blank:false)
    description(nullable:false, blank:false)
  }

 static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = DSCategory.findAllByDescriptionIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

}
