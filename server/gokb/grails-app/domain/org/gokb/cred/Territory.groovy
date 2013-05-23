package org.gokb.cred

class Territory extends KBComponent {
  String name
  static constraints = {
	name (nullable:true, blank:false)
  }
  
  static manyByCombo = [
	licenses 	: License,
	packages  	: Package,
	platforms  	: Platform,
	offices  	: Office,
	users		: User,
  ]
  
  static mappedByCombo = [
	licenses 	: 'territories',
	packages  	: 'territories',
	platforms  	: 'territories',
	offices  	: 'territories',
	users		: 'territories',
  ]
  
  static mapping = {
	name column:'territory_name'
  }
}
