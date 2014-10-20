package org.gokb.cred

class Territory extends KBComponent {
  
//  Set<User> users
//  
//  static hasMany = [
//    users:User,
//  ]
  
//  static belongsTo = [ User ]
  
  static manyByCombo = [
  	licenses 	: License,
  	packages  	: Package,
  	platforms  	: Platform,
  	offices  	: Office,
  ]
  
  static mappedByCombo = [
  	licenses 	: 'territories',
  	packages  	: 'territories',
  	platforms  	: 'territories',
  	offices  	: 'territories',
  ]
  
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = Territory.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }
}

