package org.gokb.cred

import grails.util.GrailsNameUtils

import javax.persistence.Transient

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty

abstract class KBComponent {

  transient textNormalisationService

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
  String name
  String normname
  String shortcode

  RefdataValue status
  RefdataValue editStatus

  Set tags = []
  Set additionalProperties = []
  Set outgoingCombos = []
  Set incomingCombos = []

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
  ]

  static hasMany = [
    tags:RefdataValue,
    outgoingCombos:Combo,
    incomingCombos:Combo,
    additionalProperties:KBComponentAdditionalProperty,
    variantNames:KBComponentVariantName
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

  static def generateShortcode(name) {
	def candidate = name.trim().replaceAll(" ","_")

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
    normname = textNormalisationService.normalise(name);
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
	  normname = name.toLowerCase().trim();
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
}
