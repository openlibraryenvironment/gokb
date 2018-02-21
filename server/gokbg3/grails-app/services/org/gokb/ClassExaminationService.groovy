package org.gokb

import java.beans.PropertyDescriptor
import java.lang.reflect.Field
import org.grails.datastore.mapping.model.PersistentEntity
import grails.util.GrailsNameUtils
import org.gokb.cred.RefdataValue
import org.springframework.beans.BeanUtils

class ClassExaminationService {
  def grailsApplication

  def getRefdataPropertyNames (String className) {

	// Map keyed by natural name and property name.
	LinkedHashMap refdata_props = [:]
	
	// Let's append a list of refdata properties.
	PersistentEntity the_class = grailsApplication.getArtefact('Domain', className)
	// log.debug("Examining ${className} for refdata properties.")
	
	// The surent class pointer.
	Class currentClass = the_class.getClazz()
	
	// Each field.
	while (!currentClass.equals(Object)) {
	
	  currentClass.getDeclaredFields().each { Field prop ->
		if (prop.getType().equals(RefdataValue)) {
		  // Add the property here.

		  // Need to find the actual declaring class...
		  String title = GrailsNameUtils.getNaturalName(prop.getName())
		  String key = "${prop.getDeclaringClass().getSimpleName()}.${GrailsNameUtils.getClassName(prop.getName())}"
		  refdata_props[key] = [title:(title), name: prop.getName()]
		  // log.debug ("\tFound ${key} = ${refdata_props[key]}.")
		}
	  }
	  
	  currentClass = currentClass.getSuperclass()
	}
	refdata_props
  }
}
