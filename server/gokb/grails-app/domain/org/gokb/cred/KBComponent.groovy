package org.gokb.cred

import javax.persistence.Transient
import grails.util.GrailsNameUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.hibernate.Hibernate
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler

abstract class KBComponent {

  static final String RD_STATUS 				= "KBComponent.Status"
  static final String STATUS_CURRENT 			= "Current"
  static final String STATUS_DELETED 			= "Deleted"
  static final String STATUS_EXPECTED 			= "Expected"
  static final String STATUS_RETIRED 			= "Deleted"

  static final String RD_EDIT_STATUS			= "KBComponent.EditStatus"
  static final String EDIT_STATUS_APPROVED		= "Approved"
  static final String EDIT_STATUS_IN_PROGRESS	= "In progress"
  static final String EDIT_STATUS_REJECTED		= "Rejected"

  static auditable = true

  static refdataDefaults = [
	"status" 		: STATUS_CURRENT,
	"editStatus"	: EDIT_STATUS_IN_PROGRESS
  ]

  @Transient
  private ensureDefaults () {

	// Metaclass
	ExpandoMetaClass mc = getMetaClass()

	// Get the property values for the current class implementation.
	Map defaults = mc.getProperty(getClass(), getClass(), "refdataDefaults", true, true)

	// Check we have some defaults.
	if (defaults) {

	  // The className
	  String className = Hibernate.getClass(getClass()).getSimpleName()

	  // DomainClassArtefactHandler for this class
	  def domainDescriptor = grailsApplication.getArtefact(DomainClassArtefactHandler.TYPE, className)
	  GrailsDomainClass dClass = this.domainClass

	  // Create a pointer to this so that we can access within the each closure below.
	  KBComponent thisComponent = this

	  // Add each property and value to the properties in the defaults.
	  defaults.each {String property, values ->

		if (thisComponent."${property}" == null) {

		  // Get the type defined against the class.
		  String propType = dClass.getPropertyByName(property)?.getType()?.getName()

		  if (propType) {

			switch (propType) {
			  case RefdataValue.class.getName() :

			    // Expecting refdata value. Do the lookup in a new session.
			    def vals
				
				KBComponent.withNewSession { session ->
				  String key = "${className}.${GrailsNameUtils.getClassName(property)}"

				  if (values instanceof Collection) {
					vals = []
					values.each { val ->
					  vals << RefdataCategory.lookupOrCreate(key, val)
					}
    				  
				  } else {
				  
					vals = RefdataCategory.lookupOrCreate(key, values)
				  }
				}
				  
				// Set the default.
				thisComponent."${property}" = vals
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

  //  String impId
  // Canonical name field - title for a title instance, name for an org, etc, etc, etc
  String name
  String normname
  String shortcode

  RefdataValue status
  RefdataValue editStatus

  Set tags = []
  List additionalProperties = []
  List outgoingCombos = []
  List incomingCombos = []
  List ids = []

  // Timestamps
  Date dateCreated
  Date lastUpdated

//  static manyByCombo = [
//	ids      :  Identifier,
//  ]

//  static mappedByCombo = [
//	ids      :  'component',
//  ]

  static mappedBy = [
	outgoingCombos: 'fromComponent',
	incomingCombos:'toComponent',
	additionalProperties: 'fromComponent',
	ids: 'component'
  ]

  static hasMany = [
	ids:Identifier,
	tags:RefdataValue,
	outgoingCombos:Combo,
	incomingCombos:Combo,
	additionalProperties:KBComponentAdditionalProperty
  ]

  static mapping = {
	id column:'kbc_id'
	version column:'kbc_version'
	//    impId column:'kbc_imp_id', index:'kbc_imp_id_idx'
	name column:'kbc_name'
	normname column:'kbc_normname'
	status column:'kbc_status_rv_fk'
	shortcode column:'kbc_shortcode', index:'kbc_shortcode_idx'
	tags joinTable: [name: 'kb_component_refdata_value', key: 'kbcrdv_kbc_id', column: 'kbcrdv_rdv_id']
	dateCreated column:'kbc_date_created'
	lastUpdated column:'kbc_last_updated'
	ids column:'kbc_identifier_fk', index:'kbc_identifier_fk_idx'
  }

  static constraints = {
	//    impId(nullable:true, blank:false)
	name(nullable:true, blank:false, maxSize:2048)
	shortcode(nullable:true, blank:false, maxSize:128)
	normname(nullable:true, blank:false, maxSize:2048)
	status(nullable:true, blank:false)
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
	def result = null
	def crit = KBComponent.createCriteria()
	def lr = crit.list {
	  ids {
		eq('value',idvalue)
		namespace {
		  eq('value',idtype)
		}
	  }
	}

	// println("res: ${lr}");

	if ( lr && lr.size() == 1 )
	  result=lr.get(0);

	// println("result: ${result}");
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

  def beforeInsert() {
	if ( name ) {
	  if ( !shortcode ) {
		shortcode = generateShortcode(name);
	  }
	  normname = name.toLowerCase().trim();
	}

	//	// Check the status
	//	if (status == null) {
	//	  // Lookup or create the refdata in a separate session.
	//	  RefdataCategory.withNewSession { session ->
	//		status = RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_CURRENT)
	//	  }
	//	}

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
	  if ( id.identifier?.ns?.ns == idtype )
		result = id.identifier?.value
	}
	result
  }

  @Transient
  public List getOtherIncomingCombos () {

	Set comboPropTypes = getAllComboTypeValuesFor(this.getClass());

	List combs = Combo.createCriteria().list {
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

	combs
  }

  @Transient
  public List getOtherOutgoingCombos () {

	Set comboPropTypes = getAllComboTypeValuesFor(this.getClass());

	List combs = Combo.createCriteria().list {
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

	combs
  }

  public Date deleteSoft (Date endDate = new Date()) {

	// Set the status to deleted.
	setStatus(RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_DELETED))
  }

  @Transient
  public String getClassName () {
	Hibernate.getClass(this).getName()
  }

  //  @Transient
  //  abstract getPermissableCombos()
}
