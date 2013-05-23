package org.gokb.cred

import javax.persistence.Transient

class TitleInstance extends KBComponent {

  // title is now NAME in the base component class...
  RefdataValue	medium
  RefdataValue	pureOA
  RefdataValue	reasonRetired
  String imprint
  
  final static refdataDefaults = [
	"Medium"		: "Journal",
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
	tipps 		: TitleInstancePackagePlatform
  ]

  static mapping = {
//      tipps sort:'startDate', order: 'asc'
  }

  static constraints = {
	
	medium (nullable:true, blank:false)
	pureOA (nullable:true, blank:false)
	reasonRetired (nullable:true, blank:false)
	imprint (nullable:true, blank:false)
  }

//  @Transient
//  def getPermissableCombos() {
//    [
////      [category:'TIPP',type:'HasTitle',impl:'localSet',targetClass:TitleInstancePackagePlatform.class,setName:'tipps',direction:'in']
//    ]
//  }

  def availableActions() {
    [ [code:'object::statusDeleted', label:'Set Status: Deleted'],
      [code:'title::transfer', label:'Title Transfer'] ]
  }
}
