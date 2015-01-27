package org.gokb.cred

class CuratoryGroup extends KBComponent {
  
  
  static belongsTo = User
  static hasMany = [
    users: User,
  ]
  
  static mappedBy = [users: "curatoryGroups"]
  
  static manyByCombo = [
  	licenses 	: License,
  	packages  : Package,
  	platforms : Platform,
  	offices  	: Office,
  ]
  
  static mappedByCombo = [
  	licenses 	: 'curatoryGroups',
  	packages  : 'curatoryGroups',
  	platforms : 'curatoryGroups',
  	offices  	: 'curatoryGroups',
  ]
  
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = CuratoryGroup.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }
}

