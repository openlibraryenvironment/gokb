package org.gokb.cred

import javax.persistence.Transient

class Subject extends KBComponent {

  RefdataValue scheme
  String heading

  static manyByCombo = [
	]
  
  static hasByCombo = [

  ]

  static mappedByCombo = [

  ]

  static mapping = {

	scheme column:'subj_scheme'
	heading column:'subj_heading'
  }

  static constraints = {

	scheme(nullable:true, blank:false)
	heading(nullable:true, blank:false)
  }
  
  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
	def result = [];
	def ql = null;
	ql = Subject.findAllByNameIlike("${params.q}%",params)

	if ( ql ) {
	  ql.each { t ->
	  result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
	  }
	}

	result
  }


}
