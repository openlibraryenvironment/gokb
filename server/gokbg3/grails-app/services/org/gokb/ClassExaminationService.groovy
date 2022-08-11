package org.gokb

import java.beans.PropertyDescriptor
import java.lang.reflect.Field
import grails.core.GrailsClass
import org.gokb.cred.*
import grails.util.GrailsNameUtils
import org.springframework.beans.BeanUtils
import org.grails.datastore.mapping.model.*

class ClassExaminationService {
  def grailsApplication

  def getRefdataPropertyNames (String className) {

	// Map keyed by natural name and property name.
	LinkedHashMap refdata_props = [:]
	
	// Let's append a list of refdata properties.
	GrailsClass the_class = grailsApplication.getArtefact('Domain', className)
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

  def deriveCategoryForProperty ( String obj, String propName ) {
    String propertyDef

    // Default to this class.

    def moreTests = grailsApplication.mappingContext.getPersistentEntity(obj)

//     def moreTests = obj.domainClass.clazz
    while (moreTests) {
      log.debug("deriveCategoryForProperty testing ${obj} for ${propName}")
      // Read the property...
      if (moreTests.getPropertyByName(propName)) {

        log.debug("found ${propName} for ${moreTests.getJavaClass().simpleName}")

        propertyDef = "${moreTests.getJavaClass().simpleName}"

        // Get the superclass.
        moreTests = moreTests.getParentEntity()
      } else {

        log.debug("${propName} not found ..")

        moreTests = false
      }
    }

    propertyDef ? "${propertyDef}.${GrailsNameUtils.getClassName(propName)}" : null

  }
}
