package org.gokb.cred

class Territory extends KBComponent {
  String name
  static constraints = {
	name (nullable:true, blank:false)
  }
  
  static mapping = {
	name column:'territory_name'
  }
}
