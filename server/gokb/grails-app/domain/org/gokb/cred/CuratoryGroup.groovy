package org.gokb.cred

class CuratoryGroup extends KBComponent {
  
  static belongsTo = User

  User owner;

  static hasMany = [
    users: User,
  ]
  
  static mappedBy = [users: "curatoryGroups", ]
  
  static manyByCombo = [
  	licenses: License,
  	packages: Package,
  	platforms: Platform,
  	offices: Office,
  ]
  
  static mappedByCombo = [
  	licenses: 'curatoryGroups',
  	packages: 'curatoryGroups',
  	platforms: 'curatoryGroups',
  	offices: 'curatoryGroups',
  ]

  static constraints = {
    owner (nullable:true, blank:false)
  }
  
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = CuratoryGroup.findAllByNameIlike("${params.q}%",params)

    ql.each { t ->
      if( !params.filter1 || t.status.value == params.filter1 ){
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

  def beforeInsert() {
    def user = springSecurityService?.currentUser
    this.owner = user
    println log.name
    log.debug("Checking for duplicate CuratoryGroup: ${this.name}")
    
    if (CuratoryGroup.findByNameIlike(this.name)){
      this.errors.reject ("Name is not unique","A group with this name alread exists" )
      this.errors.rejectValue ( "name", "default.notunique" ,"A group with this name alread exists" )
      log.debug("CuratoryGroup: ${this.errors}")
      return false
    }
  }

  def beforeUpdate() {
    log.debug("Checking for duplicate CuratoryGroup: ${this.name}")
    println log.name
    if (CuratoryGroup.findByNameIlike(this.name)){
      this.errors.reject ("Name is not unique", "A group with this name alread exists" )
      this.errors.rejectValue ( "name", "default.notunique" ,"A group with this name alread exists" )
      log.debug("CuratoryGroup: ${this.errors}")
      return false
    }
  }

}

