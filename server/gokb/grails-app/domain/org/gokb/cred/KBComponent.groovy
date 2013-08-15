package org.gokb.cred

import grails.util.GrailsNameUtils

import javax.persistence.Transient

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.gokb.GOKbTextUtils
import org.hibernate.proxy.HibernateProxy

/**
 * Abstract base class for GoKB Components.
 */
abstract class KBComponent {

  static final String RD_STATUS         = "KBComponent.Status"
  static final String STATUS_CURRENT       = "Current"
  static final String STATUS_DELETED       = "Deleted"
  static final String STATUS_EXPECTED       = "Expected"
  static final String STATUS_RETIRED       = "Retired"

  static final String RD_EDIT_STATUS      = "KBComponent.EditStatus"
  static final String EDIT_STATUS_APPROVED    = "Approved"
  static final String EDIT_STATUS_IN_PROGRESS  = "In Progress"
  static final String EDIT_STATUS_REJECTED    = "Rejected"

  static auditable = true

  private static refdataDefaults = [
	"status"     : STATUS_CURRENT,
	"editStatus"  : EDIT_STATUS_IN_PROGRESS
  ]

  private static final Map fullDefaultsForClass = [:]

  @Transient
  private ensureDefaults () {

	// Metaclass
	ExpandoMetaClass mc = getMetaClass()

	// First get or build up the full static map of defaults
	final Class rootClass = mc.getTheClass()
	Map defaultsForThis = fullDefaultsForClass.get(rootClass.getName())
	if (defaultsForThis == null) {

	  defaultsForThis = [:]

	  // Default to the root.
	  Class theClass = rootClass

	  // Try and get the map.
	  Map classMap
	  while (theClass) {

		try {
		  // Read the classMap
		  classMap = mc.getProperty(
			  rootClass,
			  theClass,
			  "refdataDefaults",
			  false,
			  true
			  )
		} catch (MissingPropertyException e) {
		  // Catch the error and just set to null.
		  classMap = null
		}

		// If we have values then add.
		if (classMap) {

		  // Add using the class simple name.
		  defaultsForThis[theClass.getSimpleName()] = classMap
		}

		// Get the superclass.
		theClass = theClass.getSuperclass()
	  }

	  // Once we have added each map to our map add to the global map.
	  fullDefaultsForClass[rootClass.getName()] = defaultsForThis
	}

	// Check we have some defaults.
	if (defaultsForThis) {

	  // Create a pointer to this so that we can access within the closures below.
	  KBComponent thisComponent = this

	  // DomainClassArtefactHandler for this class
	  GrailsDomainClass dClass = thisComponent.domainClass

	  defaultsForThis.each { String className, defaults ->

		// Add each property and value to the properties in the defaults.
		defaults.each {String property, values ->

		  if (thisComponent."${property}" == null) {

			// Get the type defined against the class.
			GrailsDomainClassProperty propertyDef = dClass.getPropertyByName(property)
			String propType = propertyDef?.getReferencedPropertyType()?.getName()

			if (propType) {

			  switch (propType) {
				case RefdataValue.class.getName() :

				// Expecting refdata value. Do the lookup in a new session.
				  KBComponent.withNewSession { session ->
					final String ucProp = GrailsNameUtils.getClassName(property);
					final String key = "${className}.${ucProp}"

					if (values instanceof Collection) {
					  values.each { val ->
						thisComponent."addTo${ucProp}" ( RefdataCategory.lookupOrCreate(key, val) )
					  }

					} else {
					  // Set the default.
					  thisComponent."${property}" = RefdataCategory.lookupOrCreate(key, values)
					}
				  }
				  break
				default :
				// Just treat as a normal prop
				  thisComponent."${property}" = values
				  break
			  }
			}
		  }
		}
	  }
	}
  }

  //  String impId
  // Canonical name field - title for a title instance, name for an org, etc, etc, etc

  /**
   * Generic name for the compoent. For packages, package name, for journals the journal title. Try to follow DC-Title style naming
   * conventions when trying to decide what to map to this property in a subclass. The name should be a string that reasonably identifies this
   * object when placed in a list of other components.
   */ 
  String name

  /**
   * The normalised name of this component. Lowecase, strip diacritics
   */
  String normname

  /**
   * A URI style shortcode for the component referenced. Used to create unique but human readable URIs for this item.
   */
  String shortcode

  /**
   * Component Status. Linked to refdata table.
   */
  RefdataValue status

  /**
   * Edit Status. Linked to refdata table.
   */
  RefdataValue editStatus

  Set tags = []
  List additionalProperties = []
  Set outgoingCombos = []
  Set incomingCombos = []
  Set reviewRequests = []

  // Org provOrg
  // String provUpdateFrequency
  // String provSource
  // String provDownloadUrl
  // String provNote
  // RefdataValue provFormat

//  Set ids = []

  // Timestamps
  Date dateCreated
  Date lastUpdated

  // ids moved to combos.
  static manyByCombo = [
    ids : Identifier,
  ]

  static mappedBy = [
    outgoingCombos: 'fromComponent',
    incomingCombos:'toComponent',
    additionalProperties: 'fromComponent',
    variantNames: 'owner',
	reviewRequests:'componentToReview'
  ]

  static hasMany = [
    tags:RefdataValue,
    outgoingCombos:Combo,
    incomingCombos:Combo,
    additionalProperties:KBComponentAdditionalProperty,
    variantNames:KBComponentVariantName,
	reviewRequests:ReviewRequest
  ]

  static mapping = {
	id column:'kbc_id'
	version column:'kbc_version'
	name column:'kbc_name'
	normname column:'kbc_normname'
	status column:'kbc_status_rv_fk'
	shortcode column:'kbc_shortcode', index:'kbc_shortcode_idx'
	tags joinTable: [name: 'kb_component_tags_value', key: 'kbctgs_kbc_id', column: 'kbctgs_rdv_id']
	dateCreated column:'kbc_date_created'
	lastUpdated column:'kbc_last_updated'
  }

  static constraints = {
	name    (nullable:true, blank:false, maxSize:2048)
	shortcode  (nullable:true, blank:false, maxSize:128)
	normname  (nullable:true, blank:false, maxSize:2048)
	status    (nullable:true, blank:false)
	editStatus  (nullable:true, blank:false)
  }

  /**
   * Defined parameter-less method to allow for overrides in classes, wishing to define
   * their own way of generating a shortcode.
   * @return
   */
  protected def generateShortcode () {
	if (!shortcode && name) {
	  // Generate the short code.
	  shortcode = generateShortcode(name)
	}
  }

  static def generateShortcode(String text) {
	def candidate = text.trim().replaceAll(" ","_")

	if ( candidate.length() > 100 )
	  candidate = candidate.substring(0,100)

	return incUntilUnique(candidate);
  }

  static def incUntilUnique(name) {
	def result = name;
	if ( KBComponent.findWhere([shortcode : (name)]) ) {
	  // There is already a shortcode for that identfier
	  int i = 2;
	  while ( KBComponent.findWhere([shortcode : "${name}_${i}"]) ) {
		i++
	  }
	  result = "${name}_${i}"
	}

	result;
  }

  @Transient
  static def lookupByIO(String idtype, String idvalue) {
	// println("lookupByIdentifier(${idtype},${idvalue})");
	// Component(ids) -> (fromComponent) Combo (toComponent) -> (identifiedComponents) Identifier
	def result = null

	// Look up the namespace.. If we can't find it, there can't possibly be a match
	def ns = IdentifierNamespace.findByValue(idtype)
	if ( ns != null ) {

	  // Got a namespace, see if we can find the supplied idvalue in that namespace, if not, we won't be able to find
	  // any components with that identifier
	  def identifier = Identifier.findByNamespaceAndValue(ns, idvalue)

	  if ( identifier != null ) {
		// Found an identifier.. Get all components where that identifier is linked via
		// the ids combo map.
		def crit = KBComponent.createCriteria()

		def lr = crit.list {
		  or {
			outgoingCombos {
			  eq ( 'toComponent', identifier)
			}
			incomingCombos {
			  eq ( 'fromComponent', identifier)
			}
		  }
		}

		if ( lr && lr.size() == 1 )
		  result=lr.get(0);
	  }
	}
	result
  }


  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
	def result = [];
	def ql = null;
	ql = KBComponent.findAllByNameIlike("${params.q}%",params)

	if ( ql ) {
	  ql.each { t ->
		result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
	  }
	}

	result
  }

  protected def generateNormname () {
    
	// Get the norm_name
	def nname = GOKbTextUtils.normaliseString(name);
	
	// Set to null if blank.
	normname = nname == "" ? null : nname 
  }

  def beforeInsert() {

	// Generate the any necessary values.
	generateShortcode()
	generateNormname()

	// Ensure any defaults defined get set.
	ensureDefaults()

  }

  def beforeUpdate() {
	if ( name ) {
	  if ( !shortcode ) {
		shortcode = generateShortcode(name);
	  }
	  generateNormname();
	}
  }

  @Transient
  String getIdentifierValue(idtype) {
	def result=null
	ids?.each { id ->
	  if ( id.toComponent instanceof Identifier ) {
		if ( id.toComponent.ns?.ns == idtype )
		  result = id.toComponent?.value
	  }
	}
	result
  }

  @Transient
  public List getOtherIncomingCombos () {

    def combs = null
    // Only run this query id this is not a transient object. This must have an ID for this method to work
    if ( this.id ) {
	Set comboPropTypes = getAllComboTypeValuesFor(this.getClass());

	combs = Combo.createCriteria().list {
	  and {
		eq ("toComponent", this)
		type {
		  and {
			owner {
			  eq ("desc", 'Combo.Type')
			}
			not { 'in' ("value", comboPropTypes) }
		  }

		}
	  }
	}
    }
    else {
      combs = []
    }

    combs
  }

  @Transient
  public List getOtherOutgoingCombos () {


    def combs = null

    if ( this.id != null ) {
	Set comboPropTypes = getAllComboTypeValuesFor(this.getClass());

	combs = Combo.createCriteria().list {
	  and {
		eq ("fromComponent", this)
		type {
		  and {
			owner {
			  eq ("desc", 'Combo.Type')
			}
			not { 'in' ("value", comboPropTypes) }
		  }
		}
	  }
	}
    }
    else {
      combs = null;
    }

    combs
  }

  public Date deleteSoft (Date endDate = new Date()) {
	// Set the status to deleted.
	setStatus(RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_DELETED))
  }

  @Transient
  public String getClassName () {
	getMetaClass().getTheClass().getName()
  }

  //  @Transient
  //  abstract getPermissableCombos()


  /**
   *  Return the combos pertaining to a specific property (Rather than the components linked).
   *  Needed for editing start/end dates. Initially on publisher, but probably on other things too later on.
   */
  @Transient getCombosByPropertyName(propertyName) {
    def combos
    if ( this.id != null ) {
      // Unsaved components can't have combo relations
      RefdataValue type = RefdataCategory.lookupOrCreate("Combo.Type", getComboTypeValue(propertyName))

      if (isComboReverse(propertyName)) {
       combos = Combo.createCriteria().list {
         and {
           eq ("type", (type))
           eq ("toComponent", (this))
         }
       }
      } else {
       combos = Combo.createCriteria().list {
         and {
           eq ("type", (type))
           eq ("fromComponent", (this))
         }
       }
      }
    }

    return combos
  }
  
  public boolean isInstanceOf (Class testCase) {
	boolean val = getMetaClass().getTheClass().isAssignableFrom(testCase)
	val
  }
  
  public static <T> T deproxy(def element) {
  	if (element instanceof HibernateProxy) {
  		return (T) ((HibernateProxy) element).getHibernateLazyInitializer().getImplementation();
  	}
	return (T) element;
  }
//	return (getMetaClass().getTheClass() instanceof testCase.class)

  @Override
  public boolean equals(Object obj) {
	
	// Deproxy the object first to ensure it isn't a hibernate proxy.
	def the_obj = KBComponent.deproxy(obj)
	
	if (the_obj instanceof KBComponent) {
	  return (this.getClassName() == the_obj.getClassName()) &&
	  	(this.getId() == the_obj.getId())
	}
	
	// Return false if we get here.
	false
  }
  
  public void appendToAdditionalProperty (String prop_name, String val) {
	
	// Only need to  add if a value has been supplied.
	if (val) {

	  // Find the KBComponentAdditionalProperty
	  KBComponentAdditionalProperty prop = additionalProperties.find { KBComponentAdditionalProperty prop ->
		prop.getPropertyDefn().getPropertyName() == prop_name
		prop.getApValue()?.equalsIgnoreCase(val)
	  }

	  // Only add a prop if we haven't already got one matching the value and definition.
	  if (!prop) {

		// Add a new property.
		def prop_defn = AdditionalPropertyDefinition.findByPropertyName(prop_name) ?: new AdditionalPropertyDefinition(propertyName:prop_name)

		// Add to the additional properties.
		addToAdditionalProperties(
			new KBComponentAdditionalProperty (
			propertyDefn : prop_defn,
			apValue : val
			)
		)
	  }
	}
  }
  
  public String getNiceName () {
	GrailsNameUtils.getNaturalName(getClassName())
  }
}
