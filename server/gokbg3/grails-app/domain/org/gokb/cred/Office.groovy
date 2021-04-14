package org.gokb.cred

import javax.annotation.PostConstruct
import javax.persistence.Transient

class Office extends KBComponent {

  static final String RD_FUNCTION = "Office.Function"

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
  RefdataValue function

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
  function column:'office_function_fk_rv' // , defaultValue: RefdataCategory.lookup(RD_FUNCTION, "Technical Support")
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
  function (nullable: true, blank: false)
  }

  private static refdataDefaults = [
      "function"		: "Technical Support"
  ]

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

  @Transient
  static def oaiConfig = [
    id:'offices',
    textDescription:'Office repository for GOKb',
    query:" from Office as o ",
    statusFilter: ["Deleted"],
    pageSize:20
  ]

  /**
   *  Render this package as OAI_dc
   */
  @Transient
  def toOaiDcXml(builder, attr) {
    builder.'dc'(attr) {
      'dc:title' (name)
    }
  }

  /**
   *  Render this package as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {
    builder.'gokb' (attr) {

      addCoreGOKbXmlFields(builder, attr)

      builder.'website' (website)
      builder.'phoneNumber' (phoneNumber)
      builder.'otherDetails' (otherDetails)
      builder.'addressLine1' (addressLine1)
      builder.'addressLine2' (addressLine2)
      builder.'city' (city)
      builder.'zipPostcode' (zipPostcode)
      builder.'region' (region)
      builder.'state' (state)

      if ( country ) {
        builder.'country' ( country.value )
      }

      if ( org ) {
        builder.'org' {
          builder.'name' ( org.name )
        }
      }

      builder.curatoryGroups {
        curatoryGroups.each { cg ->
          builder.group {
            builder.owner(cg.owner.username)
            builder.name(cg.name)
          }
        }
      }
    }
  }
}
