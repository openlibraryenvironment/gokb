package org.gokb.cred

class Macro extends KBComponent {
  
  String refineTransformations
  String description
  
  static hasMany = [
     tags:RefdataValue,
  ]
  
  static mapping = {
    includes KBComponent.mapping
    refineTransformations type: 'text'
    description type: 'text'
    
    tags joinTable: [name: 'macro_tags_value', key: 'mtgs_kbc_id', column: 'mtgs_rdv_id']
 }
  static constraints = {
    description nullable:true, blank:false
    refineTransformations nullable:true, blank:false
  }
}
