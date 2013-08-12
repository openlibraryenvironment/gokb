package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils

class TitleInstance extends KBComponent {

  // title is now NAME in the base component class...
  RefdataValue	medium
  RefdataValue	pureOA
  RefdataValue	reasonRetired
  String imprint
  
  private static refdataDefaults = [
	"medium"		: "Journal",
	"pureOA"		: "No"
  ]
  
  public void addVariantTitle (String title, String locale = "EN-us") {
	addToVariantNames(
	  new KBComponentVariantName([
		"variantType"	: RefdataCategory.lookupOrCreate("KBComponentVariantName.variantType", "Alternate Title"),
		"locale"		: RefdataCategory.lookupOrCreate("KBComponentVariantName.locale", (locale)),
		"status"		: RefdataCategory.lookupOrCreate('KBComponentVariantName.status', KBComponent.STATUS_CURRENT),
		"variantName"	: (title)
	  ])
	)
  }

  static hasByCombo = [
	issuer			: Org,
	translatedFrom	: TitleInstance,
	absorbedBy		: TitleInstance,
	mergedWith		: TitleInstance,
	renamedTo		: TitleInstance,
	splitFrom		: TitleInstance
  ]
  
  static manyByCombo = [
	tipps : TitleInstancePackagePlatform,
	publisher : Org,
//        ids     :  Identifier
  ]

  static constraints = {
	
	medium (nullable:true, blank:false)
	pureOA (nullable:true, blank:false)
	reasonRetired (nullable:true, blank:false)
	imprint (nullable:true, blank:false)
  }

  def availableActions() {
    [ [code:'object::statusDeleted', label:'Set Status: Deleted'],
      [code:'title::transfer', label:'Title Transfer'] ]
  }
}
