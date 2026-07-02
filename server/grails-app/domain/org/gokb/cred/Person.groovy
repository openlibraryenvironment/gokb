package org.gokb.cred

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
  static List refdataFind(params) {
		List result = [];
		List ql = Person.findAllByNameIlike("${params.q}%", params) ?: []

		ql.each { t ->
			result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
		}

		result
  }

  public List getComponentPeople() {
	  List result = [];
		List ql = ComponentPerson.findAllByPerson(this) ?: []

		ql.each { t ->
			KBComponent component = KBComponent.get(t.component.id)

			result.add([
				id:"${t.class.name}:${t.id}",
				bookId:"${component.id}",
				bookName:"${component.name}",
				role:"${t.role.value}"
			])
		}

	  result
  }

}
