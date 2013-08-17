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
  RefdataValue country
  
  static manyByCombo = [
	territories : Territory
  ]
  
  static mapping = {
	website column:'office_website'
	email column:'office_email'
	phoneNumber column:'office_phone_number'
	otherDetails column:'office_other_details'
	addressLine1 column:'office_address_1'
	addressLine2 column:'office_address_2'
	city column:'office_city'
	zipPostcode column:'office_zip_postcode'
	region column:'office_region'
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
	country (nullable:true, blank:true)
  }

}
