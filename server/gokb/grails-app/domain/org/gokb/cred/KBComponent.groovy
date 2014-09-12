package org.gokb.cred

import grails.util.GrailsNameUtils
import groovy.util.logging.*

import javax.persistence.Transient

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.gokb.GOKbTextUtils
import org.hibernate.proxy.HibernateProxy

/**
 * Abstract base class for GoKB Components.
 */

@Log4j
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
  private def springSecurityService

  @Transient
  private def grailsApplication

  @Transient
  public setSpringSecurityService(sss) {
    this.springSecurityService = sss
  }

  @Transient
  public setGrailsApplication(ga) {
    this.grailsApplication = ga
  }

  @Transient
  protected void touchAllDependants () {
    
    //TODO: SO - This really needs to be reviewed. There must be an easy way to do this without hibernate freaking out. Commenting out for now.

    // The update closure.
//    def doUpdate = { obj, Date stamp ->
//
//      try {
//
//        def saveParams = [failOnError:true, "system_save" : (systemComponent)]
//
//        obj.lastUpdated = stamp
//        obj.save(saveParams)
//
//      } catch (Throwable t) {
//
//        // Suppress but log.
//        log.error(t)
//      }
//    }
//
//    if (hasProperty("touchOnUpdate")) {
//
//      // We should also update the object(s).
//      this.touchOnUpdate.each { dep_name ->
//
//        // Get the dependant.
//        def deps = this."${dep_name}"
//
//        if (deps) {
//          if (deps instanceof Map) {
//
//            deps.each { k,obj ->
//              doUpdate(obj, lastUpdated)
//            }
//
//          } else if (deps instanceof Iterable) {
//
//            deps.each { obj ->
//              doUpdate(obj, lastUpdated)
//            }
//
//          } else if (grailsApplication.isDomainClass(deps.class)) {
//            doUpdate(deps, lastUpdated)
//          }
//        }
//      }
//    }
  }

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
   * Generic name for the component. For packages, package name, for journals the journal title. Try to follow DC-Title style naming
   * conventions when trying to decide what to map to this property in a subclass. The name should be a string that reasonably identifies this
   * object when placed in a list of other components.
   */ 
  String name

  /**
   * The normalised name of this component. Lower-case, strip diacritics
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

  /**
   *  Provenance
   */
  String provenance

  /**
   * Reference
   */
  String reference

  /**
   * Last updated by
   */
  User lastUpdatedBy

  /**
   * The source for the record (Whatever it is)
   */
  Source source


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

  // Read only flag should be honoured in the UI
  boolean systemComponent = false

  // ids moved to combos.
  static manyByCombo = [
    ids : Identifier,
    fileAttachments : DataFile,
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
    source column:'kbc_source_fk'
    status column:'kbc_status_rv_fk'
    shortcode column:'kbc_shortcode', index:'kbc_shortcode_idx'
    tags joinTable: [name: 'kb_component_tags_value', key: 'kbctgs_kbc_id', column: 'kbctgs_rdv_id']
    dateCreated column:'kbc_date_created'
    lastUpdated column:'kbc_last_updated'

    //dateCreatedYearMonth formula: "DATE_FORMAT(kbc_date_created, '%Y-%m')"
    //lastUpdatedYearMonth formula: "DATE_FORMAT(kbc_last_updated, '%Y-%m')"

  }

  static constraints = {
    name    (nullable:true, blank:false, maxSize:2048)
    shortcode  (nullable:true, blank:false, maxSize:128)
    normname  (nullable:true, blank:false, maxSize:2048)
    status    (nullable:true, blank:false)
    editStatus  (nullable:true, blank:false)
    source (nullable:true, blank:false)
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
    // println("lookupByIO(${idtype},${idvalue})");
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
        def combotype = RefdataCategory.lookupOrCreate('Combo.Type','KBComponent.Ids');

        def lr = crit.list {
          outgoingCombos {
            and {
              eq ( 'toComponent', identifier)
              eq ( 'type', combotype)
            }
          }
        }

        if ( lr ) {
          if ( lr.size() == 0 ) {
            // println("Not found");
          }
          else if ( lr.size() == 1 ) {
            result=lr.get(0);
          }
          else {
            // println("Too many");
          }
        }
      }
      else {
        // println("No Identifier");
      }
    }
    else {
      // println("No Namespace");
    }
    result
  }

  /*
   *  ignore any namespace or type - see if we can find a componenet where a linked identifier has the specified value
   *  @return LIST of all components with this identifier as a value
   */
  static def lookupByIdentifierValue(String[] idvalue) {

    def result = []

    if ( idvalue != null ) {
      def crit = Identifier.createCriteria()
      // def combotype = RefdataCategory.lookupOrCreate('Combo.Type','KBComponent.Ids');

      def lr = crit.list {
        or {
          idvalue.each {
            if ( ( it != null ) && ( it.trim().length() > 0 ) ) {
              eq('value', it)
            }
          }
        }
      }

      lr?.each { id ->
        id.identifiedComponents.each { component ->
          result.add ( component )
        }
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

  def afterInsert() {

    // Alter the timestamps of any dependants.
    touchAllDependants()
  }

  def afterUpdate() {

    // Alter the timestamps of any dependants.
    touchAllDependants()
  }

  def afterDelete() {

    // Alter the timestamps of any dependants.
    touchAllDependants()
  }

  def beforeUpdate() {
    if ( name ) {
      if ( !shortcode ) {
        shortcode = generateShortcode(name);
      }
      generateNormname();
    }
    def user = springSecurityService?.currentUser
    if ( user != null ) {
      this.lastUpdatedBy = user
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

  public void deleteSoft (context) {
    // Set the status to deleted.
    setStatus(RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_DELETED))
    save(failOnError:true)
  }

  public void retire () {
    // Set the status to deleted.
    setStatus(RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_RETIRED))
    save(failOnError:true)
  }

  @Transient
  public boolean isRetired () {
    return (getStatus() == RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_RETIRED))
  }

  @Transient
  public boolean isDeleted () {
    return (getStatus() == RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_DELETED))
  }

  @Transient
  public boolean isCurrent () {
    return (getStatus() == RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_CURRENT))
  }

  /**
   *  Return the combos pertaining to a specific property (Rather than the components linked).
   *  Needed for editing start/end dates. Initially on publisher, but probably on other things too later on.
   */
  @Transient getCombosByPropertyName(propertyName) {
    def combos
    if ( this.id != null ) {
      // Unsaved components can't have combo relations
      RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, getComboTypeValue(propertyName))

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

  @Override
  public boolean equals(Object obj) {

    if ( obj != null ) {
    // Deproxy the object first to ensure it isn't a hibernate proxy.

      if (obj instanceof KBComponent) {
        return (this.getClassName() == obj.getClassName()) && (this.getId() == obj.getId())
      }
      else if ( obj instanceof org.hibernate.proxy.HibernateProxy ) {
        def the_obj = KBComponent.deproxy(obj)
        return (this.getClassName() == the_obj.getClassName()) && (this.getId() == the_obj.getId())
      }
    }

    // Return false if we get here.
    false
  }

  public void appendToAdditionalProperty (String prop_name, String val) {

    // Only need to add if a name and value has been supplied.
    if (prop_name && val) {

      // Find the KBComponentAdditionalProperty
      KBComponentAdditionalProperty prop = additionalProperties.find { KBComponentAdditionalProperty prop ->
        prop.getPropertyDefn().getPropertyName() == prop_name &&
            prop.getApValue()?.equalsIgnoreCase(val)
      }

      // Only add a prop if we haven't already got one matching the value and definition.
      if (!prop) {

        // Add a new property.
        def prop_defn = AdditionalPropertyDefinition.findByPropertyName(prop_name) ?: new AdditionalPropertyDefinition(propertyName:prop_name).save(failOnError:true)

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

  public String toString() {
    "${name?:''} (${getNiceName()} ${id})".toString()
  }

  /**
   * Get the list of all properties and there values.
   */
  @Transient
  public Map getAllPropertiesAndVals() {

    // The list of property names that we are to ignore.
    // II: ToDo Steve - Please review this.
    // explanation :: I'm not fully sure what side effects this might have, but in local_props below deproxy is called
    // for each property. skippedTitles is a list of strings and was causing hell on earth in the A_Api which was unable to tell the
    // difference between f(x) and f([x]) closure called with a list containing one argument. Not been able to fully 
    // wrap my head around A_Api yet, so added skippedTitles here as a stop-gap. Substantial changes already made to A_Api and don't
    // want to change any more yet.
    // added variantNames, ids
    def ignore_list = [
      'id',
      'outgoingCombos',
      'incomingCombos',
      'reviewRequests',
      'tags',
      'systemOnly',
      'additionalProperties',
      'skippedTitles',
      'variantNames',
      'ids',
      'fileAttachments'
    ]

    // Get the domain class.
    def domainClass = grailsApplication.getDomainClass(this."class".name)

    // The map of current property names and values to be passed to the
    // new constructor.
    def props = [:]

    // Add combo and persisted properties to the list.
    def localProps = (domainClass?.persistentProperties?.collect { it.name }) ?: []
    localProps += allComboPropertyNames

    localProps.each { prop ->
      
      // Ignore the ones in the list.
      if (prop in ignore_list) {
        return
      }

      // println("Deproxy ${prop}");
      def val = deproxy(this."${prop}")

      switch (val) {

        case {it instanceof Collection} :
          def newVals = []
          for (el in val) {

            // Deproxy the item in the list and then add.
            def newVal = deproxy(val)
            if (grailsApplication.isDomainClass(newVal."class")) {
              // Domain class.
              newVal = newVal.merge()
            }

            // Add to the list.
            newVals << newVal
          }

          props["${prop}"] = newVals
          break
        default :
          props["${prop}"] = val
      }
    }

    props
  }

  /**
   * Creates a new component of the type of this class
   * and populates all the properties with the values of this one
   * with the exception of the id as this will be set on save.
   */
  @Transient
  public <T extends KBComponent> T clone () {

    // Now we have a map of all properties and values we should create our new instance.
    T comp = this."class".newInstance()
    sync (comp)
  }

  /**
   * This method copies the values from this component to the supplied.
   */
  @Transient
  public <T extends KBComponent> T sync (T to) {
    if (to) {
      
      T me = this
      
      // Update Master tipp.
      Map propVals = allPropertiesAndVals
      log.debug("Found ${propVals.size()} properties to synchronize.")
      int count = 1
      propVals.each { p, v ->
        log.debug("(${count}) Attempting to copy '${p}' from component ${me.id} to ${to.id}...")
        if (v != null) {
          def toHas = has(to, "${p}")
          
          if (toHas) {
            log.debug ("\t...sending value ${v.toString()}")
            to."${p}" = v
          } else {
            log.debug ("\t...target doesn't support '${p}'")
          }
          
          
        } else {
          log.debug("\t...value is null")
        }
        count ++
      }
    }

    // Return the supplied element.
    to
  }

  /**
   * Similar to the respondsTo method but checks for methods properties and combos.
   */
  @Transient
  public static boolean has (Object ob, String op) {

    // The flag value.
    boolean hasOp = false

    if (ob) {
      // Check properties.
      hasOp = ob.hasProperty(op) ||
          (ob.respondsTo(op)?.size() > 0) ||
          (ob instanceof KBComponent && ob.allComboPropertyNames.contains(op))
    }

    hasOp
  }

  @Transient
  def ensureVariantName(name) {

    def normname = GOKbTextUtils.normaliseString(name)

    // Check that name is not already a name or a variant, if so, add it.
    def existing_component = KBComponent.findByNormname( normname )

    if ( existing_component == null ) {
      // not already a name
      // Make sure not already a variant name
      def existing_variants = KBComponentVariantName.findAllByNormVariantName(normname)
      if ( existing_variants.size() == 0 ) {
        KBComponentVariantName kvn = new KBComponentVariantName(owner:this, normVariantName:name).save()
      }
      else {
        log.error("Unable to add ${name} as an alternate name to ${id} - it's already an alternate name....");
      }

    }
    else {
      log.error("Unable to add ${name} as an alternate name to ${id} - it's already name for ${existing_component.id}");
    }

  }
}
