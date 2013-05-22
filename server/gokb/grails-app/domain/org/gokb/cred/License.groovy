package org.gokb.cred

class License extends KBComponent {
  String 		url
  String		file
  RefdataValue	type
  
  final static refdataDefaults = [
	"type" 		: "Template"
  ]
  
  static hasByCombo = [
	licensor 		: Org,
	licensee		: Org,
	'previous'		: License,
	successor		: License,
	model			: License
  ]
  
  static manyByCombo = [
	modelledLicenses	: License,
	territories			: Territory
  ]
  
  static mappedByCombo = [
	successor			: 'previous',
	modelledLicenses	: 'model'
  ]
  
  static constraints = {
	url 		nullable:true, blank:true
	file 		nullable:true, blank:true
	type 		nullable:true, blank:true
  }
  
  static mapping = {
	url 		column:'license_url'
	file	 	column:'license_document'
	type 		column:'license_type_fk_rd'
  }
}
