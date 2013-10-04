package org.gokb.cred

class License extends KBComponent {
  String 		url
  String		file
  RefdataValue	type
  
  private static refdataDefaults = [
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

  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = License.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
      result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

}
