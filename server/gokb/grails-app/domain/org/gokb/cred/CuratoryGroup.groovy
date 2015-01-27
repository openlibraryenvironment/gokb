package org.gokb.cred

class CuratoryGroup extends KBComponent {
  
  Set<User> users
  
  static hasMany = [
    users: User,
  ]
  
  static belongsTo = [ User ]
  
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

