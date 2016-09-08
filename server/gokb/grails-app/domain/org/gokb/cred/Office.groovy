package org.gokb.cred

import javax.persistence.Transient

class Office extends KBComponent {

  String website
  String email
  String phoneNumber
  String otherDetails
  String addressLine1
  String addressLine2
  String city
  String zipPostcode
  String region
  String state
  RefdataValue country

  static hasByCombo = [
    org : Org,
  ]

  static manyByCombo = [
	curatoryGroups : CuratoryGroup
  ]
  
  static mapping = {
        includes KBComponent.mapping
	website column:'office_website'
	email column:'office_email'
	phoneNumber column:'office_phone_number'
	otherDetails column:'office_other_details'
	addressLine1 column:'office_address_1'
	addressLine2 column:'office_address_2'
	city column:'office_city'
	zipPostcode column:'office_zip_postcode'
	region column:'office_region'
	state column:'office_state'
	country column:'office_country_fk_rv'
  }
  
  static constraints = {
	website (nullable:true, blank:true)
	email (nullable:true, blank:true)
	phoneNumber (nullable:true, blank:true)
	otherDetails (nullable:true, blank:true)
	addressLine1 (nullable:true, blank:true)
	addressLine2 (nullable:true, blank:true)
	city (nullable:true, blank:true)
	zipPostcode (nullable:true, blank:true)
	region (nullable:true, blank:true)
	state (nullable:true, blank:true)
	country (nullable:true, blank:true)
  }

  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = Office.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
      result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

}
