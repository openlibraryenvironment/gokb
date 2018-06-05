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
    name (validator: { val, obj ->
      if (obj.id) {
        CuratoryGroup.findAllByNameIlike(val)?.each { cg ->
          if (!cg.equals(obj)){
            return 'validation.curatoryGroup.name.unique';
          }
        }
        true
      }
      else if(CuratoryGroup.findAllByNameIlike(val)) {
        return 'validation.curatoryGroup.name.unique';
      }else {
        true
      }
    })
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
  }

  def beforeUpdate() {
  }

}

