package org.gokb.cred

import javax.persistence.Transient

class Person extends KBComponent {

//  String label;
//  // do we want other information, like address etc?

//  static mapping = {
//    label column:'name'
//  }

//  static constraints = {
//    label(nullable:false, blank:false)
//  }

  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
	def result = [];
	def ql = null;
	ql = Person.findAllByNameIlike("${params.q}%",params)

	if ( ql ) {
	  ql.each { t ->
	  result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
	  }
	}

	result
  }
  
  @Transient
  def getComponentPeople() {
	  def result = [];
	  def ql = null;
	  ql = ComponentPerson.findAllByPerson(this)
  
	  if ( ql ) {
		ql.each { t ->
		def component = KBComponent.findAllById(t.component.id);	
		result.add([id:"${t.class.name}:${t.id}",bookId:"${t.component.id}", bookName:"${t.component.name}", role:"${t.role.value}"])
		}
	  }
  
	  result
  }
  
}
