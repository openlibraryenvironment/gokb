package org.gokb

/**
 * The following class adds the functionality that allows properties maintained by the Combo
 * mechanism instead of the normal Grails gorm mapping.
 * 
 * The following shows how specially mapped Combo properties can be declared on a Domain class:
 * 
 * static hasByCombo   = ['myProp'     : MyClass]
 * static manyByCombo  = ['myCollection'   : MyClass]
 * static mappedByCombo  = ['myProp'      : propColl]
 * 
 * The hasByCombo mapping would create a virtual property 'myProp',
 * along with the getter and setter (getMyProp(), setMyProp (MyClass val)), 
 * on the current domain class with the type MyClass.
 * 
 * The has manyByCombo mapping would create a virtual property 'myCollection',
 * along with the getter and setter (getMyCollection(), setMyCollection( Collection<MyClass> val )),
 * on the current domain class with the type List<MyClass>.
 * 
 * The mappedByCombo mapping declares that the property declared as 'myProp' should be treated as an incoming
 * relationship from the class 'MyClass' from the property 'propColl'. This will ensure that relationships 
 * are only stored once in the database but allows us to easily derive the relationship identifier to lookup.
 * 
 */

import grails.util.GrailsNameUtils
import groovy.util.logging.*

import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.gokb.cred.*

@Log4j
class DomainClassExtender {

  private static RefdataValue getComboStatusActive = null;
  public static RefdataValue getComboStatusActive () {
	if (getComboStatusActive == null) {
	  getComboStatusActive = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
	}

	getComboStatusActive
  }

  private static addComboPropertyCache = { DefaultGrailsDomainClass domainClass ->

	log.debug("Adding comboPropertyCache to ${delegate}")
	// Get the metaclass.
	domainClass.getMetaClass().comboPropertyCache = {
	  log.debug ("Creating cache for per-instance metaclass.")

	  // Called the first time we should create a map and store on the per-instance metaclass.
	  Map cache = [:]

	  // Get the delegates Metaclass
	  delegate.getMetaClass().comboPropertyCache = {
		// override here.
		log.debug ("Returning cache from per-instance metaclass.")
		cache
	  }

	  // Return the cache object.
	  cache
	}
  }

  private static addGetAllComboPropertyNames = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	domainClass.getMetaClass().getAllComboPropertyNames = { ->
	  getAllComboPropertyNamesFor (domainClass.getClazz())
	}
  }

  private static addIsComboPropertyFor = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	final ExpandoMetaClass mc = domainClass.getMetaClass()
	mc.static.isComboPropertyFor = { Class forClass, String propertyName ->
	  log.debug ("isComboPropertyFor invoked using params ${[forClass, propertyName]}")

	  // Split at the dot.
	  String[] properties = propertyName.split("\\.");

	  // The class that owns the end-point of the property string.
	  Class parentClass = forClass;


	  Set cProps = getAllComboPropertyNamesFor (forClass)
	  cProps.contains(propertyName)
	}
  }

  private static addIsComboProperty = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	domainClass.getMetaClass().isComboProperty = { String propertyName ->
	  addIsComboPropertyFor (domainClass.getClazz(), propertyName)
	}
  }

  private static Map comboTypeValueCache = [:]

  private static addGetAllComboPropertyNamesFor = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	final ExpandoMetaClass mc = domainClass.getMetaClass()
	mc.static.getAllComboPropertyNamesFor = { Class forClass ->
	  log.debug("getAllComboPropertyNamesFor called with args ${[forClass]}")

	  getAllComboPropertyDefinitionsFor(forClass).keySet()
	}
  }

  private static addGetAllComboPropertyDefinitionsFor = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	final ExpandoMetaClass mc = domainClass.getMetaClass()
	mc.static.getAllComboPropertyDefinitionsFor = { Class forClass ->
	  log.debug("getAllDefinedComboPropertyFor called with args ${[forClass]}")
	  String cacheKey = "${GrailsNameUtils.getShortName(forClass)}:all"

	  // Check cache.
	  log.debug("Checking comboMappingCache for ${cacheKey}...")
	  Map cProps = DomainClassExtender.comboMappingCache[cacheKey]

	  if (cProps == null) {
		log.debug("\t...not found doing lookup.")

		// No cached value.
		cProps = [:]
		cProps.putAll( getComboMapFor (forClass, Combo.HAS) )
		cProps.putAll( getComboMapFor (forClass, Combo.MANY) )

		// Cache it.
		DomainClassExtender.comboMappingCache[cacheKey] = cProps
	  } else {
		log.debug("\t...found and returning ${cProps}.")
	  }

	  cProps
	}
  }

  private static addGetAllComboTypeValues = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	domainClass.getMetaClass().getAllComboTypeValues = { ->
	  getAllComboTypeValuesFor (domainClass.getClazz())
	}
  }

  private static addGetAllComboTypeValuesFor = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	final ExpandoMetaClass mc = domainClass.getMetaClass()
	mc.static.getAllComboTypeValuesFor = { Class forClass ->
	  log.debug("getAllComboTypeValuesFor called with args ${[forClass]}")
	  String cacheKey = "${GrailsNameUtils.getShortName(forClass)}:all"

	  // Check cache.
	  log.debug("Checking cache...")
	  Set types = DomainClassExtender.comboTypeValueCache[cacheKey]

	  if (types == null) {
		// No cached value.
		log.debug("\t...not found doing lookup.")

		Set cProps = getAllComboPropertyNamesFor(forClass)

		// Generate each type in turn.
		types = []
		for (String propertyName in cProps) {
		  types << getComboTypeValueFor (forClass, propertyName)
		}

		// Cache it.
		DomainClassExtender.comboTypeValueCache[cacheKey] = types
	  } else {
		log.debug("\t...found and returning ${types}.")
	  }

	  types
	}
  }

  private static addGetComboMap = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	domainClass.getMetaClass().getComboMap = { String mappingName ->
	  log.debug("getComboMap called on ${delegate} with args ${[mappingName]}")
	  getComboMapFor (domainClass.getClazz(), mappingName)
	}
  }
  
  private static addCombineInheritedMap = { DefaultGrailsDomainClass domainClass ->
	
	// Get the metaclass.
	domainClass.getMetaClass().combineInheritedMap = { String mapName ->
	  combineInheritedMapFor (domainClass.getClazz(), mapName)
	}
  }
  
  private static addCombineInheritedMapFor = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	domainClass.getMetaClass().static.combineInheritedMapFor = {Class forClass, String mapName ->
	  log.debug("combineInheritedValuesFor called on ${delegate} with args ${[forClass, mapName]}")
	  
	  // Start with this class.
	  Class theClass = forClass
	  
	  // Start with an empty map.
	  Map values = [:]
	  while (theClass) {
		Map value = [:]
		try {
		  // Read the classMap
		  value.putAll(theClass."${mapName}")
		} catch (MissingPropertyException e) {
		  // Catch the error and just set to null.
		  value = null
		}

		// If we have values then add.
		if (value) {

		  // Current values should override the collected.
		  value.putAll (values)
		  
		  values.clear()
          values.putAll(value)
		}

		// Get the superclass.
		theClass = theClass.getSuperclass()
	  }
	  
	  // Return the combined map.
	  values
	}
  }

  private static addGetComboMapFor = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	final ExpandoMetaClass mc = domainClass.getMetaClass()
	mc.static.getComboMapFor = { Class forClass, String mapName ->
	  log.debug("getComboMapFor called on ${delegate} with args ${[forClass,mapName]}")

	  // Return from cache if present.
	  String cacheKey = "${GrailsNameUtils.getShortName(forClass)}:${mapName}"
	  log.debug("Checking cache for ${cacheKey}...")
	  def value = DomainClassExtender.comboMappingCache[cacheKey]
	  if (value != null) {
		log.debug("\t...found, returning ${value}")
		return value
	  }

	  log.debug("\t...Not found, looking up.")
	  try {

		// Lookup the value combining values in the superclass too!
//		value = mc.getProperty(delegate.getClass(), forClass, mapName, true, true)
		value = combineInheritedMapFor (forClass, mapName)
	  } catch (Exception e) { value = [:] }

	  if (value == null) value = [:]

	  // Cache it.
	  DomainClassExtender.comboMappingCache[cacheKey] = value

	  // Return the value.
	  log.debug("${value} found.")
	  value
	}
  }

  private static addGetComboProperty = { DefaultGrailsDomainClass domainClass ->
	final MetaClass mc = domainClass.getMetaClass()
	mc.getComboProperty {String propertyName ->
	  log.debug("getComboProperty called on ${delegate} with args ${[propertyName]}")

	  // Test this way to allow us to cache null values.
	  log.debug("Checking cache...")
	  String cacheKey = "${propertyName}".toString()
	  if (comboPropertyCache().containsKey(cacheKey)) {
		log.debug ("\t...found")
		return comboPropertyCache().get(cacheKey);
	  }
	  log.debug ("\t...not found, looking for value,")

	  // Check the type.
	  Class typeClass = lookupComboMapping(Combo.MANY, propertyName)

	  // Generate the type.
	  RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", getComboTypeValue(propertyName))

	  if (typeClass) {

		// The result.
		def result

		// The delegate.
		final KBComponent thisComponent = delegate

		if (isComboReverse(propertyName)) {

		  // Reverse.
		  //          def combos = incomingCombos.findAll {
		  //            it.type == (type)
		  //          }
		  def combos = Combo.createCriteria().list {
			and {
			  eq ("type", (type))
			  eq ("toComponent", (thisComponent))
			}
			projections {
			  property("fromComponent")
			}
		  }

		  // Create our new list.
		  result = new ComboPersistedList (
			  (thisComponent),
			  DomainClassExtender.getComboStatusActive(),
			  type,
			  combos,
			  true
          )

		  //          if (combos) {
		  //            for (combo in combos) {
		  //              result.add(combo.fromComponent)
		  //            }
		  //          }

		} else {
		  //          def combos = outgoingCombos.findAll {
		  //            it.type == (type)
		  //          }
		  def combos = Combo.createCriteria().list {
			and {
			  eq ("type", (type))
			  eq ("fromComponent", (thisComponent))
			}
			projections {
			  property("toComponent")
			}
		  }

		  // Create our new list.
		  result = new ComboPersistedList (
			  (thisComponent),
			  DomainClassExtender.getComboStatusActive(),
			  type,
			  combos,
			  false
			  )

		  //          if (combos) {
		  //            for (combo in combos) {
		  //              result.add(combo.toComponent)
		  //            }
		  //          }
		}

		// Add the result to the cache.
		comboPropertyCache().put(cacheKey, result)

		log.debug("${result} found.")
		return result

	  } else {

		// Try singular.
		typeClass = lookupComboMapping(Combo.HAS, propertyName)

		if (typeClass) {
		  def result = null
		  if (isComboReverse(propertyName)) {

			// Just return the component.
			//            Combo combo = incomingCombos.find {
			//              it.type == (type)
			//            }
			final KBComponent thisComponent = delegate
			result = Combo.createCriteria().get {
			  and {
				eq ("type", (type))
				eq ("toComponent", (thisComponent))
			  }
			  projections {
				property("fromComponent")
			  }
			}

			//            if (combo) result = combo.fromComponent
		  } else {
			//            Combo combo = outgoingCombos.find {
			//              it.type == (type)
			//            }
			final KBComponent thisComponent = delegate
			result = Combo.createCriteria().get {
			  and {
				eq ("type", (type))
				eq ("fromComponent", (thisComponent))
			  }
			  projections {
				property("toComponent")
			  }
			}

			//            if (combo) result = combo.toComponent
		  }

		  // Add the result to the cache.
		  comboPropertyCache().put(cacheKey, result)

		  log.debug("${result} found.")
		  return result
		}

		log.debug("No Property found, throw Exception.")
		// If we get here then throw an exception.
		throw new MissingPropertyException(propertyName, this.class)
	  }

	}
  }

  private static addGetComboTypeValue = {DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	domainClass.getMetaClass().getComboTypeValue = {String propertyName  ->
	  log.debug("getComboTypeValue called on ${delegate} with args ${[propertyName]}")
	  getComboTypeValueFor(domainClass.getClazz(), propertyName)
	}
  }

  private static addGetComboTypeValueFor = {DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	final MetaClass mc = domainClass.getMetaClass()
	mc.static.getComboTypeValueFor = {Class forClass, String propertyName  ->
	  log.debug("getComboTypeValueFor called on ${delegate} with args ${[forClass,propertyName]}")

	  String cacheKey = "${forClass.getName()}.${propertyName}"
	  log.debug ("Checking cache for ${cacheKey}...")
	  String key = DomainClassExtender.comboTypeValueCache[cacheKey]

	  if (key != null) {
		log.debug("\t...found")
	  } else {

		String capProp
		Class mappedByClass
		def mappedByProp = lookupComboMappingFor(forClass, Combo.MAPPED_BY, propertyName)
		if (mappedByProp) {
		  // We need to look up the relationship the other way round.
		  // First find the class type mapped to.
		  mappedByClass = lookupComboMappingFor(forClass, Combo.MANY, propertyName)
		  mappedByClass = mappedByClass ?: lookupComboMappingFor(forClass, Combo.HAS, propertyName)

		  if (mappedByClass) {
			// Found the class, we can now use this information to build up our string.
			if (mappedByProp.length() > 1) {
			  capProp = mappedByProp[0].toUpperCase() + mappedByProp[1..-1]
			} else {
			  capProp = mappedByProp.toUpperCase()
			}
		  }
		} else {
		  if (propertyName.length() > 1) {
			capProp = propertyName[0].toUpperCase() + propertyName[1..-1]
		  } else {
			capProp = propertyName.toUpperCase();
		  }

		  // Set the class also.
		  mappedByClass = forClass
		}
		
		// We have a mapped by class, but we need to check that it isn't declared on one of the super classes.
        def propNameToUse = GrailsNameUtils.getPropertyName(capProp)
		Class theClass = mappedByClass
		while (theClass) {
		  try {
			Set<String> props = getAllComboPropertyNamesFor (theClass)
			if (props.contains(propNameToUse)) {
			  // Set the key.
			  key = "${GrailsNameUtils.getShortName(theClass)}.${capProp}"
			}
			
		  } catch (Throwable t) {
		  	// Do nothing.
		  }
		  
		  theClass = theClass.getSuperclass();
		}

		// Cache the constructed key.
		DomainClassExtender.comboTypeValueCache[cacheKey] = key
		log.debug("\t... not found, generated and added to cache.")
	  }

	  log.debug("Using type value ${key}")
	  key
	}
  }

  private static addIsComboReverse = { DefaultGrailsDomainClass domainClass ->
	domainClass.getMetaClass().isComboReverse = {String propertyName ->
	  log.debug("isComboReverse called on ${delegate} with args ${[propertyName]}")
	  (lookupComboMapping (Combo.MAPPED_BY, propertyName) != null)
	}
  }

  private static addLookupComboMapping = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	domainClass.getMetaClass().lookupComboMapping = {String mappingName, String propertyName ->

	  log.debug("lookupComboMapping called on ${delegate} with args ${[mappingName,propertyName]}")
	  lookupComboMappingFor (domainClass.getClazz(), mappingName, propertyName)
	}
  }

  private static addLookupComboMappingFor = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	final MetaClass mc = domainClass.getMetaClass()
	mc.static.lookupComboMappingFor = {Class forClass, String mappingName, String propertyName ->
	  log.debug("lookupComboMappingFor called on ${delegate} with args ${[forClass,mappingName,propertyName]}")
	  // Get the map.
	  def map = getComboMapFor (forClass, mappingName)

	  // Return the property.
	  def prop = map[propertyName]

	  log.debug("${delegate}.${propertyName} maps to type ${prop}.")
	  prop
	}
  }

  private static addPropertyMissing = {DefaultGrailsDomainClass domainClass ->
	// Get the metaclass.
	final MetaClass mc = domainClass.getMetaClass()

	// Save the old version of methodMissing so it can be used if needed
	MetaMethod oldPropertyMissing = mc.methods.find { it.name == 'propertyMissing' }

	mc.propertyMissing = {String name, value = null ->

	  log.debug("propertyMissing called on ${delegate} with args ${[name, value]}")
	  def result
	  switch (name) {
		case Combo.HAS :
		case Combo.MAPPED_BY :
		case Combo.MANY :
		  return null
		  break

		default :

		// Execute the existing propertyMissing.
		  if (value == null) {
			if (oldPropertyMissing != null) {
			  log.debug("calling oldPropertyMissing on ${delegate} with args ${name}")
			  result = oldPropertyMissing.doMethodInvoke(delegate, [name].toArray())
			}

			result = result ?: getComboProperty(name)
		  } else {
			if (oldPropertyMissing != null) {

			  log.debug("calling oldPropertyMissing on ${delegate} with args ${[name, value]}")
			  result = oldPropertyMissing.doMethodInvoke(delegate, [name, value].toArray())
			}
			result = result ?: setComboProperty(name, value)
		  }
	  }
	  result
	}
  }
  private static addRemoveComboPropertyVals = { DefaultGrailsDomainClass domainClass ->
	final MetaClass mc = domainClass.getMetaClass()
	mc.removeComboPropertyVals {String propertyName, boolean preserveCurrent = false ->
	  log.debug("removeComboPropertyVals called on ${delegate} with args ${propertyName}")

	  // End dateused when expiring.
	  Date endDate = (preserveCurrent ? new Date() : null)

	  // Generate the type.
	  RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", getComboTypeValue(propertyName))

	  // Get all..
	  List<Combo> combos
	  // Query DB for current combos (endDate is NULL)
	  if (isComboReverse(propertyName)) {
		// Reverse.
		final KBComponent thisComponent = delegate
		combos = Combo.createCriteria().list {
		  and {
			eq ("type", (type))
			eq ("toComponent", (thisComponent))
			isNull ("endDate")
			eq ("status", DomainClassExtender.getComboStatusActive())
		  }
		}
	  } else {
		final KBComponent thisComponent = delegate

		combos = Combo.createCriteria().list {
		  and {
			eq ("type", (type))
			eq ("fromComponent", (thisComponent))
			isNull ("endDate")
			eq ("status", DomainClassExtender.getComboStatusActive())
		  }
		}
	  }

	  // Delete each combo in turn.
	  for (Combo combo in combos) {

		// Need to make sure we remove from both sides of the,
		// association before attempting to remove the combo.

		// Clear caches first as removing from set will set components to null.
		if (combo.fromComponent) {
		  KBComponent comp = combo.fromComponent
		  comp.comboPropertyCache().clear()

		  if (!preserveCurrent) {
			comp.removeFromOutgoingCombos(combo)
		  }
		  //          comp.save()
		}
		if (combo.toComponent) {
		  KBComponent comp = combo.toComponent
		  comp.comboPropertyCache().clear()

		  if (!preserveCurrent) {
			comp.removeFromIncomingCombos(combo)
		  }
		  //          comp.save()
		}

		// Remove or expire the combo.
		if (preserveCurrent) {
		  // We need to "expire" the current value(s), not remove it.
		  log.debug ("Expiring combo with ID ${combo.id}")

		  // Expire the combo with a custom date.
		  endDate = combo.expire(endDate)

		  // Save the combo.
		  combo.save()

		} else {
		  log.debug ("Deleting combo with ID ${combo.id}")
		  // Remove the combo.
		  combo.delete()
		}
	  }

	  // Clear the cached value too if present.
	  comboPropertyCache().remove("${propertyName}".toString())

	  // Return the date added as the endDate
	  endDate
	}
  }

  private static addSetComboProperty = {DefaultGrailsDomainClass domainClass ->
	final MetaClass mc = domainClass.getMetaClass()
	mc.setComboProperty = {String propertyName, def value, boolean preserveCurrent = false ->
	  log.debug("setComboProperty called on ${delegate} with args ${[propertyName, value]}")
	  Class typeClass
	  switch (value) {
		case Collection :
		// Check the many relationships
		  typeClass = lookupComboMapping(Combo.MANY, propertyName)

		  if (typeClass == null) throw new IllegalArgumentException(
			"Supplied value for setComboProperty was a collection, but could not find mapping for ${propertyName} in ${Combo.MANY} for class ${domainClass.getClazz()}"
			)
		  break
		default:
		// Check single properties.
		  typeClass = lookupComboMapping(Combo.HAS, propertyName)

		  if (typeClass == null) throw new IllegalArgumentException(
			"Supplied value for setComboProperty was a singular none collection, but could not find mapping for ${propertyName} in ${Combo.HAS} for class ${domainClass.getClazz()}"
			)
	  }

	  if (typeClass) {

		// Remove the property values.
		Date new_start_date = removeComboPropertyVals(propertyName, preserveCurrent) ?: new Date()

		if (value) {

		  // Generate the type.
		  RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, getComboTypeValue(propertyName))

		  // Go through each item and generate a value.
		  switch (value) {
			case Collection :

			  if (isComboReverse(propertyName)) {

				// Reverse
				for (val in value) {
				  if (typeClass.isInstance(val)) {

					// Create an active combo
					Combo combo = new Combo(
						type    : (type),
						status  : DomainClassExtender.getComboStatusActive(),
						startDate : new_start_date
						) //.save()

					// Add to the collections.
					log.debug("adding incoming Combo of type ${type} to ${delegate} to ${val}.")
					delegate.addToIncomingCombos(combo)

					log.debug("adding outgoing Combo of type ${type} from ${val} to ${delegate}.")
					val.addToOutgoingCombos(combo)

				  } else {
					throw new IllegalArgumentException(
					"All values in collection for property ${delegate}.${propertyName} should be of defined type: ${typeClass.getName()}"
					)
				  }
				}
			  } else {
				for (val in value) {
				  if (typeClass.isInstance(val)) {
					Combo combo = new Combo(
						type    : (type),
						status  : DomainClassExtender.getComboStatusActive(),
						startDate  : new_start_date
						)

					// Add to the collections.
					log.debug("adding outgoing Combo of type ${type} from ${delegate} to ${val}.")
					delegate.addToOutgoingCombos(combo)

					log.debug("adding incoming Combo of type ${type} to ${val} from ${delegate}.")
					val.addToIncomingCombos(combo)

				  } else {
					throw new IllegalArgumentException(
					"All values in collection for property ${delegate}.${propertyName} should be of defined type: ${typeClass.getName()}"
					)
				  }
				}
			  }
			  break
			default:
			// Check single properties.
			  typeClass = lookupComboMapping(Combo.HAS, propertyName)
			  if (typeClass.isInstance(value)) {

				if (isComboReverse(propertyName)) {
				  Combo combo = new Combo(
					  type    : (type),
					  status  : DomainClassExtender.getComboStatusActive(),
					  startDate : new_start_date
					  )

				  // Add to the incoming collection
				  log.debug("adding incoming Combo of type ${type} to ${delegate} from ${value}.")

				  delegate.addToIncomingCombos(combo)
				  log.debug("adding outgoing Combo of type ${type} from ${value} to ${delegate}.")
				  value.addToOutgoingCombos(combo)

				} else {
				  Combo combo = new Combo(
					  type     : (type),
					  status    : DomainClassExtender.getComboStatusActive(),
					  startDate  : new_start_date
					  )//.save()

				  // Add to the collections.
				  log.debug("adding outgoing Combo of type ${type} from ${delegate} to ${value}.")
				  delegate.addToOutgoingCombos(combo)

				  log.debug("adding incoming Combo of type ${type} to ${value} to ${delegate}.")
				  value.addToIncomingCombos(combo)

				}
			  } else {
				throw new IllegalArgumentException(
				"Value for property ${delegate}.${propertyName} should be of defined type: ${typeClass.getName()}"
				)
			  }
		  }

		  // Add to the cache.
		  comboPropertyCache().put("${propertyName}".toString(), value)

		  // We should also completely clear the target cache too.
		  //TODO Maybe we could be more selective about what we remove from the cache,
		  // although it's probably negligible between the processing needed to lookup,
		  // and derive the value to clear than to rebuild if/when needed.
		  value?.comboPropertyCache().clear()
		}
	  } else {
		log.debug("Thrown missing property exception for ${propertyName} on ${delegate}.")
		throw new MissingPropertyException(propertyName, domainClass.getClazz())
	  }
	}
  }

  private static Map comboMappingCache = [:]

  public static extend = { DefaultGrailsDomainClass domainClass ->

	// Get the actual class that is represented by this domain class object.
	Class actualClass = domainClass.getClazz()

	if (!KBComponent.class.is(actualClass)) {
	  if (KBComponent.class.isAssignableFrom(actualClass)) {

		// Extends KBCombonent. Add Static methods.
		DomainClassExtender.addGetComboMapFor (domainClass)
		DomainClassExtender.addLookupComboMappingFor (domainClass)
		DomainClassExtender.addGetComboTypeValueFor (domainClass)
		DomainClassExtender.addGetAllComboPropertyDefinitionsFor (domainClass)
		DomainClassExtender.addGetAllComboPropertyNamesFor (domainClass)
		DomainClassExtender.addGetAllComboTypeValuesFor (domainClass)
		DomainClassExtender.addIsComboPropertyFor (domainClass)
		DomainClassExtender.addCombineInheritedMapFor (domainClass)
		DomainClassExtender.addComboPropertyGettersAndSetters(domainClass)

		// Extend to handle ComboMapped Properties.
		DomainClassExtender.extendMapConstructor(domainClass)
		DomainClassExtender.addGetComboMap (domainClass)
		DomainClassExtender.addLookupComboMapping (domainClass)
		DomainClassExtender.addGetComboTypeValue (domainClass)
		DomainClassExtender.addIsComboReverse (domainClass)
		DomainClassExtender.addComboPropertyCache(domainClass)
		DomainClassExtender.addSetComboProperty(domainClass)
		DomainClassExtender.addGetComboProperty(domainClass)
		DomainClassExtender.addRemoveComboPropertyVals(domainClass)
		DomainClassExtender.addPropertyMissing(domainClass)
		DomainClassExtender.addGetAllComboPropertyNames (domainClass)
		DomainClassExtender.addGetAllComboTypeValues (domainClass)
		DomainClassExtender.addIsComboProperty (domainClass)
		DomainClassExtender.addCombineInheritedMap (domainClass)
	  }
	} else {

	  // Is KBCombonent class. Just add the static helper methods.
	  DomainClassExtender.extendMapConstructor(domainClass)
	  DomainClassExtender.addGetComboMapFor (domainClass)
	  DomainClassExtender.addLookupComboMappingFor (domainClass)
	  DomainClassExtender.addGetComboTypeValueFor (domainClass)
	  DomainClassExtender.addGetAllComboPropertyDefinitionsFor (domainClass)
	  DomainClassExtender.addGetAllComboPropertyNamesFor (domainClass)
	  DomainClassExtender.addGetAllComboTypeValuesFor (domainClass)
	  DomainClassExtender.addIsComboPropertyFor (domainClass)
	  DomainClassExtender.addCombineInheritedMapFor (domainClass)
	}
  }

  private static extendMapConstructor = { DefaultGrailsDomainClass domainClass ->

	// Get the metaclass.
	final ExpandoMetaClass mc = domainClass.getMetaClass()

	// Get the original contructor.
	def oldConstructor = mc.retrieveConstructor(Map)
	mc.constructor = { Map args ->
	  log.debug("MapConstructor called for new ${delegate} with args ${args}")

	  log.debug ("Calling original contructor for new ${delegate} with args ${args}.")

	  // Instantiate the object and save...
	  // We really need to save here so we can reference this object within the combos.
	  def instance = oldConstructor.newInstance(args)
	  if (instance.save(failOnError:true)) {

		// Now that we have created our instance using the original constructor we can,
		// now set the combo props that were missed.
		Set cProps = getAllComboPropertyNamesFor (instance.getClass())
		for (prop in args.keySet()) {
		  if (cProps.contains(prop)) {

			// Set the combo property directly.
			instance.setComboProperty(prop, args[prop])
		  }
		}
	  }
	  instance
	}
  }

  private static addComboPropertyGettersAndSetters = { DefaultGrailsDomainClass domainClass ->
	// Get the metaclass.
	final MetaClass mc = domainClass.getMetaClass()

	// Get the list of all combo properties for this class and add the getters and setters.
	// Should be much less resource intensive than using method missing.
	Set<String> comboProperties = domainClass.getClazz().getAllComboPropertyNamesFor (domainClass.getClazz())

	for (String property in comboProperties) {
	  // Uppercase the first character.
	  String propName = "${property[0].toUpperCase()}${property[1..-1]}"

	  // Create a copy of the property.
	  final String prop = property.toString()

	  log.debug ("Adding methods get${propName} and set${propName} to ${domainClass.getClazz()} metaclass.")

	  // Add the getter.
	  mc."get${propName}" = { ->
		log.debug("get${propName} called for ${delegate}")
		delegate.getComboProperty(prop)
	  }

	  // Add the setter
	  mc."set${propName}" = { Object value ->
		log.debug("set${propName} called for ${delegate} using args ${[value]}")
		delegate.setComboProperty(prop, value)
	  }
	}
  }
}
