package org.gokb
import groovy.lang.Closure
import grails.orm.HibernateCriteriaBuilder
import org.gokb.cred.*
import org.codehaus.groovy.grails.commons.GrailsClassUtils

class ComboCriteria {

  private HibernateCriteriaBuilder crit
  private static ComboCriteria comboCrit

  private ComboCriteria (HibernateCriteriaBuilder crit) {
//	crit.createAlias("incomingCombos", "incomingCombos")
	this.crit = crit
  }

  public static ComboCriteria createFor(HibernateCriteriaBuilder crit) {
	if (comboCrit == null || comboCrit.crit != crit) comboCrit = new ComboCriteria (crit)
	comboCrit
  }

//  public static ComboCriteria add (String propertyName, String operator, Object... args ) {
//	// TODO: Add some error trapping for null pointer and throw a meaningful error message.
//	comboCrit.addCriteria(propertyName, operator, args)
//  }
//
//  private ComboCriteria addCriteria (String propertyName, String operator, Object... args ) {
//
//	// Property exists.
//	boolean hasProp = ((KBComponent.lookupComboMappingFor(crit.targetClass, Combo.HAS, propertyName) != null)
//		|| (KBComponent.lookupComboMappingFor(crit.targetClass, Combo.MANY,propertyName) != null))
//
//	if (hasProp) {
//	  // Combo Type.
//	  RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", KBComponent.getComboTypeValueFor(crit.targetClass, propertyName))
//
//	  // Incoming combo?
//	  boolean incoming = (KBComponent.lookupComboMappingFor(crit.targetClass, Combo.MAPPED_BY, propertyName) != null)
//
//	  // Build the criteria string ids.
//	  String combName, propName
//	  if (incoming) {
//
//		combName = "incomingCombos"
//		propName = "fromComponent"
//
//	  } else {
//		combName = "outgoingCombos"
//		propName = "toComponent"
//	  }
//
//	  // Add the type comparison.
//	  crit.eq ("${combName}.type", type)
//
//	  // Add our derived property name to the args and try and execute the method on the criteria.
//	  def methodParams = ["${combName}.${propName}".toString()]
//	  methodParams.addAll(args)
//
//	  // Try and invoke a method.
//	  crit.invokeMethod(operator, methodParams.toArray())
//	}
//
//	this
//  }
  
  def methodMissing(String name, args) {
	// Just try and invoke on the criteria.
	
	// We need to try and invoke on the HibernateCriteria.
	crit.invokeMethod(name, args)
  }
  
  public ComboCriteria add (String propertyName, String operator, args = []) {
	add(propertyName, operator, args, null)
  }

  private def add = {String propertyName, String operator, args = [].toArray(), Class the_class ->

//	def the_value = value

	// Start class as the target class of the query builder, if none supplied.
	the_class = the_class ?: crit.targetClass;

	// Get all the combo properties defined on the class.
	Map allProps = KBComponent.getAllComboPropertyDefinitionsFor(the_class)

	// Split the property and go through each property as needed.
	List props = propertyName.split("\\.")

	// If props length > 1 then we need to first add all the necessary associations.
	if (props.size() > 1) {

	  // Pop the first element from the list.
	  String prop = props.remove(0)

	  // Set the property value to the new list minus the head.
	  def newPropName = props.join(".")

	  // Get the type that the property maps to (check combo props first).
	  Class target_class = allProps[prop]

	  // Combo property?
	  if (target_class) {
		boolean incoming = KBComponent.lookupComboMappingFor (the_class, Combo.MAPPED_BY, prop)

		// Combo property... Let's add the association.
		if (incoming) {
		  // Use incoming combos.
		  "incomingCombos" {
			and {
			  eq (
				  "type",
				  RefdataCategory.lookupOrCreate (
    				  "Combo.Type",
    				  the_class.getComboTypeValueFor (the_class, prop)
				  )
			  )
			  fromComponent {
//				processContextTree(delegate, newCtxtTree, value, paramdef, target_class)
				add (newPropName, operator, args, target_class)
			  }
			}
		  }

		} else {
		  // Outgoing
		  "outgoingCombos" {
			and {
			  eq (
				  "type",
				  RefdataCategory.lookupOrCreate (
				  "Combo.Type",
				  the_class.getComboTypeValueFor (the_class, prop)
				  )
			  )
			  toComponent {
//				processContextTree(delegate, newCtxtTree, value, paramdef, target_class)
				add (newPropName, operator, args, target_class)
			  }
			}
		  }
		}
	  } else {
		// Normal groovy/grails property.
		target_class = GrailsClassUtils.getPropertyType(the_class, prop)
		crit.targetClass = the_class

		// Add the association here.
		"${prop}" {
//		  processContextTree(delegate, newCtxtTree, value, paramdef, target_class)
		  add (newPropName, operator, args, target_class)
		}
	  }

	} else {

	  // We need to do the comparison.

	  // Check if this is a combo property.
	  if (allProps[propertyName]) {

		// Add association using either incoming or outgoing properties.
		boolean incoming = KBComponent.lookupComboMappingFor (the_class, Combo.MAPPED_BY, propertyName)

		if (incoming) {
		  // Use incoming combos.
		  "incomingCombos" {
			and {
			  eq (
				  "type",
				  RefdataCategory.lookupOrCreate (
				  "Combo.Type",
				  the_class.getComboTypeValueFor (the_class, propertyName)
				  )
			  )
			  def methodProps = ["fromComponent"]
			  methodProps.addAll(args)
			  invokeMethod(operator, methodProps.toArray())
//			  fromComponent {
//				//                    processContextTree(delegate, contextTree.children, value, paramdef)
////				ilike(contextTree.prop,the_value)
////				"${operator}" (propertyName, args)
//				def methodProps = [propertyName]
//				methodProps.addAll(args)
//				invokeMethod(operator, methodProps.toArray())
//			  }
			}
		  }

		} else {
		  // Outgoing
		  "outgoingCombos" {
			and {
			  eq (
				  "type",
				  RefdataCategory.lookupOrCreate (
				  "Combo.Type",
				  the_class.getComboTypeValueFor (the_class, propertyName)
				  )
			  )
			  def methodProps = ["toComponent"]
			  methodProps.addAll(args)
			  invokeMethod(operator, methodProps.toArray())
			  
//			  toComponent {
//				//                    processContextTree(delegate, contextTree.children, value, paramdef)
////				ilike(contextTree.prop,the_value)
////				"${operator}" (propertyName, args)
//				def methodProps = [propertyName]
//				methodProps.addAll(args)
//				invokeMethod(operator, methodProps.toArray())
//			  }
			}
		  }
		}
	  } else {
		// Normal grails property.
	  	def methodProps = [propertyName]
		methodProps.addAll(args)
	  	invokeMethod(operator, methodProps.toArray())
//		crit."${operator}" (propertyName, args)
	  }
	}
  
    // return this.
    this
  }
}
