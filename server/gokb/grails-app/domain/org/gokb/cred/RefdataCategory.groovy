package org.gokb.cred

class RefdataCategory {

  String desc
  Set values

  static mapping = {
         id column:'rdc_id'
    version column:'rdc_version'
       desc column:'rdc_description', index:'rdc_description_idx'
     values sort:'value', order:'asc'

  }

  static hasMany = [
    values:RefdataValue
  ]

  static mappedBy = [
    values:'owner'
  ]

  static constraints = {
  }

  static def lookupOrCreate(category_name, value) {
    def cat = RefdataCategory.findByDesc(category_name);
    if ( !cat ) {
      cat = new RefdataCategory(desc:category_name).save();
    }

    def result = RefdataValue.findByOwnerAndValue(cat, value)

    if ( !result ) {
      new RefdataValue(owner:cat, value:value).save(flush:true);
      result = RefdataValue.findByOwnerAndValue(cat, value);
    }

    result
  }

  def availableActions() {
    [ [ code:'object::delete' , label: 'Delete' ] ]
  }

}
