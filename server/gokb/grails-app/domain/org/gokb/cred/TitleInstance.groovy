package org.gokb.cred

import javax.persistence.Transient

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

  static hasByCombo = [
	publisher		: Org,
	issuer			: Org,
	translatedFrom	: TitleInstance,
	absorbedBy		: TitleInstance,
	mergedWith		: TitleInstance,
	renamedTo		: TitleInstance,
	splitFrom		: TitleInstance
  ]
  
  static manyByCombo = [
	tipps 	: TitleInstancePackagePlatform,
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
