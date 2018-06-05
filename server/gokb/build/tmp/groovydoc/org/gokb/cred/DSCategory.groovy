package org.gokb.cred

import javax.persistence.Transient

class DSCategory {

  String code
  String description
  String colour

  static mapping = {
    code column:'dscat_code'
    description column:'dscat_desc'
    colour column:'dscat_colour'
    criterion(sort:'id', order:'asc')
  }

  static constraints = {
    code(nullable:false, blank:false)
    description(nullable:true, blank:true)
    colour(nullable:true, blank:true)
  }

  static hasMany = [
    criterion:DSCriterion
  ];

  static mappedBy = [
    criterion:'owner'
  ];

  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = DSCategory.findAllByDescriptionIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.description}"])
      }
    }

    result
  }

  public String getNiceName() {
    return "Decision Support Category";
  }

}
