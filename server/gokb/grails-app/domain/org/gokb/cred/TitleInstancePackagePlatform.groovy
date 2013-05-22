package org.gokb.cred

import javax.persistence.Transient

class TitleInstancePackagePlatform extends KBComponent {

  Date startDate
  String startVolume
  String startIssue
  String embargo
  String coverageDepth
  String coverageNote
  RefdataValue format
  RefdataValue delayedOA
  String delayedOAEmbargo
  RefdataValue hybridOA
  String hybridOAUrl
  RefdataValue primary
  RefdataValue paymentType
  Date endDate
  String endVolume
  String endIssue
//  RefdataValue option
  
  final static refdataDefaults = [
	"format" 		: "Electronic",
	"delayedOA"		: "Unknown",
	"hybridOA"		: "Unknown",
	"primary"		: "No",
	"payment"		: "Paid"
  ]
  
  static hasByCombo = [
    pkg 					: Package,
    hostPlatform			: Platform,
    title					: TitleInstance,
	derivedFrom				: TitleInstancePackagePlatform,
  ]
  
  static mappedByCombo = [
    pkg 					: 'tipps',
    hostPlatform 			: 'hostedTipps',
    additionalPlatforms		: 'linkedTipps',
    title 					: 'tipps',
    derivatives				: 'derivedFrom',
  ]
  
  static manyByCombo = [
	derivatives				: TitleInstancePackagePlatform,
	additionalPlatforms		: Platform,
  ]

  static mapping = {
        startDate column:'tipp_start_date'
      startVolume column:'tipp_start_volume'
       startIssue column:'tipp_start_issue'
          endDate column:'tipp_end_date'
        endVolume column:'tipp_end_volume'
         endIssue column:'tipp_end_issue'
          embargo column:'tipp_embargo'
    coverageDepth column:'tipp_coverage_depth'
     coverageNote column:'tipp_coverage_note',type: 'text'
		   format column:'tipp_format_rv_fk'
	    delayedOA column:'tipp_delayed_oa'
 delayedOAEmbargo column:'tipp_delayed_oa_embargo'
 		 hybridOA column:'tipp_hybrid_oa'
	  hybridOAUrl column:'tipp_hybrid_oa_url'
	  	  primary column:'tipp_primary'
	  paymentType column:'tipp_payment_type'
  }

  static constraints = {
	startDate (nullable:true, blank:true)
	startVolume (nullable:true, blank:true)
	 startIssue (nullable:true, blank:true)
		endDate (nullable:true, blank:true)
	  endVolume (nullable:true, blank:true)
	   endIssue (nullable:true, blank:true)
		embargo (nullable:true, blank:true)
  coverageDepth (nullable:true, blank:true)
   coverageNote (nullable:true, blank:true)
		 format (nullable:true, blank:true)
	  delayedOA (nullable:true, blank:true)
delayedOAEmbargo (nullable:true, blank:true)
		hybridOA (nullable:true, blank:true)
	hybridOAUrl (nullable:true, blank:true)
		  primary (nullable:true, blank:true)
	paymentType (nullable:true, blank:true)
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }
}
