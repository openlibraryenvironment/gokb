package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender

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
	
	// Need to compare the existing variant names here. Rather than use the equals method,
	// we are going to compare certain attributes here.
	RefdataValue title_type = RefdataCategory.lookupOrCreate("KBComponentVariantName.VariantType", "Alternate Title")
	RefdataValue locale_rd = RefdataCategory.lookupOrCreate("KBComponentVariantName.Locale", (locale))
	
	// Each of the variants...
	def exisiting = variantNames.find {
	  KBComponentVariantName name = it
	  return (name.locale == locale_rd && name.variantType == title_type
		&& name.getVariantName().equalsIgnoreCase(title))
	}
	
	if (!exisiting) {
	  addToVariantNames(
		new KBComponentVariantName([
		  "variantType"	: (title_type),
		  "locale"		: (locale_rd),
		  "status"		: RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_CURRENT),
		  "variantName"	: (title)
		])
	  )
	}
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

  @Override
  public String getNiceName() {
	return "Title";
  }

  public Org getCurrentPublisher() {
    def result = null;
    def publisher_combos = getCombosByPropertyName('publisher')
    publisher_combos.each { Combo pc ->
      if ( pc.endDate == null ) {
		if (isComboReverse('publisher')) {
		  result = pc.fromComponent
		} else {
		  result = pc.toComponent
		}
      }
    }
    result
  }

  /**
   * Close off any existing publisher relationships and add a new one for this publiser
   */
  def changePublisher(new_publisher, boolean null_start = false) {

    if ( new_publisher != null ) {

      def current_publisher = getCurrentPublisher()

      if ( ( current_publisher != null ) && ( current_publisher.id==new_publisher.id ) ) {
        // no change... leave it be
		return false
      }
      else {
        def publisher_combos = getCombosByPropertyName('publisher')
        publisher_combos.each { pc ->
          if ( pc.endDate == null ) {
            pc.endDate = new Date();
          }
        }
		
		// Now create a new Combo
		RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, getComboTypeValue('publisher'))
		Combo combo = new Combo(
		  type    : (type),
		  status  : DomainClassExtender.getComboStatusActive(),
		  startDate : (null_start ? null : new Date())
		)
		
		// Depending on where the combo is defined we need to add a combo.
		if (isComboReverse('publisher')) {
		  combo.fromComponent = new_publisher
		  addToIncomingCombos(combo)
		} else {
		  combo.toComponent = new_publisher
		  addToOutgoingCombos(combo)
		}
		
		return true
//        publisher.add(new_publisher)
      }
    }
	
	// Returning false if we get here implies the publisher has not been changed.
	return false
  }
}
