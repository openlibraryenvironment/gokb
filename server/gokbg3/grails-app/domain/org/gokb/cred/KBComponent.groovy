package org.gokb.cred

import grails.util.GrailsNameUtils
import groovy.util.logging.*

import javax.persistence.Transient

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import grails.plugins.orm.auditable.Auditable
import grails.plugins.orm.auditable.AuditEventType
import org.gokb.GOKbTextUtils

/**
 * Abstract base class for GoKB Components.
 */

@Slf4j
@grails.gorm.dirty.checking.DirtyCheck
abstract class KBComponent implements Auditable {

  static final String RD_STATUS         = "KBComponent.Status"
  static final String STATUS_CURRENT       = "Current"
  static final String STATUS_DELETED       = "Deleted"
  static final String STATUS_EXPECTED       = "Expected"
  static final String STATUS_RETIRED       = "Retired"

  static final String RD_EDIT_STATUS      = "KBComponent.EditStatus"
  static final String EDIT_STATUS_APPROVED    = "Approved"
  static final String EDIT_STATUS_IN_PROGRESS  = "In Progress"
  static final String EDIT_STATUS_REJECTED    = "Rejected"

  static final String CURRENT_PRICE_HQL = '''
select cp 
from ComponentPrice as cp 
where cp.owner = :c 
  and cp.priceType.value = :t
  and ( ( startDate is null OR startDate <= :d ) and ( endDate is null OR endDate > :d ) )
'''

  private static refdataDefaults = [
    "status"     : STATUS_CURRENT,
    "editStatus"  : EDIT_STATUS_IN_PROGRESS
  ]

  private static final Map fullDefaultsForClass = [:]

  @Override
  Collection<AuditEventType> getLogIgnoreEvents() {
      [AuditEventType.INSERT, AuditEventType.DELETE]
  }

  @Override
  Collection<String> getLogExcluded() {
      [
        'version',
        'lastUpdated',
        'lastUpdatedBy',
        'bucketHash',
        'componentHash',
        'componentDiscriminator',
        'normname',
        'shortcode',
        'systemComponent',
        'insertBenchmark',
        'componentHash',
        'incomingCombos',
        'outgoingCombos'
      ]
  }

  String getLogEntityId() {
      "${this.class.name}:${id}"
  }

  @Transient
  private def springSecurityService

  @Transient
  protected grails.core.GrailsApplication grailsApplication

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

    try {

      // Metaclass
      ExpandoMetaClass metaclass_of_this_component = this.metaClass

      // First get or build up the full static map of defaults
      final Class rootClass = metaclass_of_this_component.getTheClass()
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
            classMap = metaclass_of_this_component.getProperty(
                rootClass,
                theClass,
                "refdataDefaults",
                false,
                true
                )
          } catch (MissingPropertyException e) {
            // Catch the error and just set to null.
            // log.error("MissingPropertyExceptiono - clearing out classMap",e);
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
        PersistentEntity dClass = grailsApplication.mappingContext.getPersistentEntity(thisComponent.class.name)

        defaultsForThis.each { String className, defaults ->

          // Add each property and value to the properties in the defaults.
          defaults.each { String property, values ->
            def lone_property = property
            def rdc = null

            if(lone_property.contains('.')) {
              def split_prop = lone_property.split("\\.")

              rdc = split_prop[0]
              lone_property = split_prop[1]
            }

            if ( lone_property?.length() > 0 && thisComponent."${lone_property}" == null ) {

              // Get the type defined against the class.
              PersistentProperty propertyDef = dClass.getPropertyByName(lone_property)
              String propType = propertyDef?.getType()?.getName()
  
              if (propType) {
  
                switch (propType) {
                  case RefdataValue.class.getName() :
  
                        final String ucProp = GrailsNameUtils.getClassName(lone_property);
                        final String key = "${rdc ?: className}.${ucProp}"
    
                        if (values instanceof Collection) {
                          values.each { val ->
                            def v= RefdataCategory.lookupOrCreate(key, val)
                            // log.debug("lookupOrCreate-1(${key},${val}) - ${v.id}");
                            thisComponent."addTo${ucProp}" ( v )
                          }
                        } else {
                          // Set the default.
                          def v = RefdataCategory.lookupOrCreate(key, values)
                          // log.debug("lookupOrCreate-2(${key},${values}) - ${v.id}");
                          thisComponent."${lone_property}" = v
                        }
                      break
                  default :
                    // Just treat as a normal prop
                    thisComponent."${lone_property}" = values
                    break
                }
              }
              else {
                log.debug("Could not find property ${lone_property} for class ${dClass.getName()}")
              }
            }
          }
        }
      }
    }
    catch ( Exception e ) {
      log.error("Problem initializing defaults",e);
    }
  }

  //  String impId
  // Canonical name field - title for a title instance, name for an org, etc, etc, etc

  /**
   * UUID
   */
  String uuid

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
   * A description (DC.description)
   */
  String description

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

  String lastUpdateComment

  // Set tags = []
  List additionalProperties = []
  Set outgoingCombos = []
  Set incomingCombos = []
  Set reviewRequests = []
  Set variantNames = []

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

  Long lastSeen
  Long insertBenchmark
  Long updateBenchmark

  // Read only flag should be honoured in the UI
  boolean systemComponent = false

  // used in data tidy routines
  KBComponent duplicateOf

  // MD5 Hash of the comparable title for the component. This hash is used to group
  // candidate duplicate works together. It is the means by which we group possible duplicates
  // for more meaningful comparisons. As such, it needs to be coarse and as widely encompassing
  // as possible.
  String bucketHash

  // MD5 Hash specific to class of component that is used for deduplication - EG Title + Edition == Instance level hashing
  String componentHash

  // A discriminator which can be added to the hash above to explicitly
  // discriminate items who's hash would otherwise be (Correctly) the same.
  String componentDiscriminator

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
    reviewRequests:'componentToReview',
    people:'component',
    subjects:'component',
    prices: 'owner'
  ]

  static hasMany = [
    // tags:RefdataValue,
    outgoingCombos:Combo,
    incomingCombos:Combo,
    additionalProperties:KBComponentAdditionalProperty,
    variantNames:KBComponentVariantName,
    reviewRequests:ReviewRequest,
    people:ComponentPerson,
    subjects:ComponentSubject,
    prices: ComponentPrice
  ]




  static mapping = {
    tablePerHierarchy false
    id column:'kbc_id'
    uuid column:'kbc_uuid', type:'text'
    version column:'kbc_version'
    name column:'kbc_name', type:'text', index:'kbc_name_idx'
    // Removed auto creation of norm_id_value_idx from here and identifier - MANUALLY CREATE
    // create index norm_id_value_idx on kbcomponent(kbc_normname(64),id_namespace_fk);
    normname column:'kbc_normname', type:'text', index:'kbc_normname_idx'
    description column:'kbc_description', type:'text'
    source column:'kbc_source_fk'
    status column:'kbc_status_rv_fk', index:'kbc_status_idx'
    shortcode column:'kbc_shortcode', index:'kbc_shortcode_idx'
    // tags joinTable: [name: 'kb_component_tags_value', key: 'kbctgs_kbc_id', column: 'kbctgs_rdv_id']
    dateCreated column:'kbc_date_created', index:'kbc_date_created_idx'
    lastUpdated column:'kbc_last_updated', index:'kbc_last_updated_idx'
    duplicateOf column:'kbc_duplicate_of'
    reviewRequests sort: 'id', order: 'asc'
    lastSeen column:'kbc_last_seen'
    insertBenchmark column:'kbc_insert_benchmark'
    updateBenchmark column:'kbc_update_benchmark'
    lastUpdateComment column:'kbc_last_update_comment'
    componentHash column:'kbc_component_hash', index:'kbc_component_hash_idx'
    bucketHash column:'kbc_bucket_hash', index:'kbc_bucket_hash_idx'
    componentDiscriminator column:'kbc_component_descriminator'
    incomingCombos batchSize: 10
    outgoingCombos batchSize: 10
    //dateCreatedYearMonth formula: "DATE_FORMAT(kbc_date_created, '%Y-%m')"
    //lastUpdatedYearMonth formula: "DATE_FORMAT(kbc_last_updated, '%Y-%m')"

  }

  static constraints = {
    uuid    (nullable:true, blank:false, maxSize:2048)
    name    (nullable:true, blank:false, maxSize:2048)
    shortcode  (nullable:true, blank:false, maxSize:128)
    description  (nullable:true, blank:false)
    duplicateOf  (nullable:true, blank:false)
    normname  (nullable:true, blank:false, maxSize:2048)
    status    (nullable:true, blank:false)
    editStatus  (nullable:true, blank:false)
    source (nullable:true, blank:false)
    lastSeen (nullable:true, blank:false)
    lastUpdateComment (nullable:true, blank:false)
    insertBenchmark (nullable:true, blank:false)
    updateBenchmark (nullable:true, blank:false)
    bucketHash (nullable:true, blank:false)
    componentDiscriminator (nullable:true, blank:false)
    componentHash (nullable:true, blank:false)
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

  protected def generateUuid () {
    if (!uuid) {
      uuid = UUID.randomUUID().toString()
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
    def l = KBComponent.executeQuery('select id from KBComponent where shortcode = :n',[n:name]);
    // if ( KBComponent.findWhere([shortcode : (name)]) ) {
    if ( l.size() > 0 ) {
      // There is already a shortcode for that identfier
      int i = 2;
      // while ( KBComponent.findWhere([shortcode : "${name}_${i}"]) ) {
      while ( KBComponent.executeQuery('select id from KBComponent where shortcode = :n',[n:"${name}_${i}"]).size() > 0 ) {
        i++
      }
      result = "${name}_${i}"
    }

    result;
  }
  
  @Transient
  static <T extends KBComponent> T lookupByIO(String idtype, String idvalue) {
    // println("lookupByIO(${idtype},${idvalue})");
    // Component(ids) -> (fromComponent) Combo (toComponent) -> (identifiedComponents) Identifier
    def result = null
    def crit = T.createCriteria()
    def db_results = crit.list {
        
      createAlias('outgoingCombos', 'ogc')
      createAlias('ogc.type', 'ogcType')
      createAlias('ogcType.owner', 'ogcOwner')
      
      createAlias('ogc.toComponent', 'tc')
      createAlias('tc.namespace', 'tcNamespace')
      
      and {
        eq 'ogcOwner.desc', 'Combo.Type'
        eq 'ogcType.value', 'KBComponent.Ids'
        
        eq 'tc.value', idvalue
        eq 'tcNamespace.value', idtype
      }
      
      projections {
        distinct 'id'
      }
    }
    
    switch (db_results.size()) {
      case 1 :
        result = T.get(db_results[0])
        break
//      case {it > 1} : 
//        // Error. Should only match 1...
//        break 
    }
    
    result
  }
  
//  @Transient
//  static def lookupByIO(String idtype, String idvalue) {
//    // println("lookupByIO(${idtype},${idvalue})");
//    // Component(ids) -> (fromComponent) Combo (toComponent) -> (identifiedComponents) Identifier
//    def result = null
//
//    // Look up the namespace.. If we can't find it, there can't possibly be a match
//    def ns = IdentifierNamespace.findByValue(idtype)
//    if ( ns != null ) {
//
//      // Got a namespace, see if we can find the supplied idvalue in that namespace, if not, we won't be able to find
//      // any components with that identifier
//      def identifier = Identifier.findByNamespaceAndValue(ns, idvalue)
//
//      if ( identifier != null ) {
//        // Found an identifier.. Get all components where that identifier is linked via
//        // the ids combo map.
//        def crit = KBComponent.createCriteria()
//        def combotype = RefdataCategory.lookupOrCreate('Combo.Type','KBComponent.Ids');
//
//        def lr = crit.list {
//          outgoingCombos {
//            and {
//              eq ( 'toComponent', identifier)
//              eq ( 'type', combotype)
//            }
//          }
//        }
//
//        if ( lr ) {
//          if ( lr.size() == 0 ) {
//            // println("Not found");
//          }
//          else if ( lr.size() == 1 ) {
//            result=lr.get(0);
//          }
//          else {
//            // println("Too many");
//          }
//        }
//      }
//      else {
//        // println("No Identifier");
//      }
//    }
//    else {
//      // println("No Namespace");
//    }
//    result
//  }

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
    def status_deleted = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    def ql = null;
    ql = Class.forName(params.baseClass).findAllByNameIlikeAndStatusNotEqual("${params.q}%", status_deleted, params)
//    ql = KBComponent.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        if( !params.filter1 || t.status?.value == params.filter1 ) {
          result.add([id:"${t.class.name}:${t.id}",text:"${t.name}", status:"${t.status?.value}"])
        }
      }
    }

    result
  }


  /** Added here so that everyone who wants a normalised component name can
      call this function, then we have a single place to call or change to pivot the norm rules */
  public static def generateNormname (str_to_norm) {
    def r = GOKbTextUtils.norm2(str_to_norm);

    if ( r.length() == 0 )
      r = null;

    return r
  }

  protected def generateNormname () {
    log.debug("checking for normname")
    this.normname = generateNormname(name);
  }

  protected def generateComponentHash() {
    // Default component hash generation -- Override in subclasses

    // To try and find instances
    this.componentHash = GOKbTextUtils.generateComponentHash([normname, componentDiscriminator]);

    // To find works
    this.bucketHash = GOKbTextUtils.generateComponentHash([normname]);
  }

  def beforeInsert() {
    // Generate any necessary values.
    generateShortcode()
    generateNormname()
    generateComponentHash()
    generateUuid()

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
    log.debug("beforeUpdate for ${this}")
    if ( name ) {
      if ( !shortcode ) {
        this.shortcode = generateShortcode(name);
      }
      generateNormname();
      generateComponentHash()
    }

    if (!uuid) {
      generateUuid()
    }

    def user = springSecurityService?.currentUser
    if ( user != null ) {
      this.lastUpdatedBy = user
    }
  }

  @Transient
  String getIdentifierValue(idtype) {

    // As ids are combo controlled it should be enough just to call find here.
    // This will return only the first match and stop looking afterwards.
    // Null returned if no match.
    ids?.find { it.namespace.value.toLowerCase() == idtype.toLowerCase() }?.value
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
    save(flush:true, failOnError:true)
  }

  public void retire (def context = null) {
    log.debug("KBComponent::retire");
    // Set the status to retired.
    setStatus(RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_RETIRED))
    save(flush:true, failOnError:true)
  }

  public void setActive (context) {
    setStatus(RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_CURRENT))
    save(flush:true, failOnError:true)
  }
  
  public void setExpected (context) {
    setStatus(RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_EXPECTED))
    save(flush:true, failOnError:true)
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
  
  @Transient
  public boolean isExpected () {
    return (getStatus() == RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_EXPECTED))
  }

  /**
   *  Return the combos pertaining to a specific property (Rather than the components linked).
   *  Needed for editing start/end dates. Initially on publisher, but probably on other things too later on.
   */
  @Transient
   public List<Combo> getCombosByPropertyName(propertyName) {

    return getCombosByPropertyNameAndStatus(propertyName,null)
  }

  @Transient
  public List<Combo> getCombosByPropertyNameAndStatus(propertyName,status) {
//     log.debug("KBComponent::getCombosByPropertyNameAndStatus::${propertyName}|${status}")

    def combos
    def status_ref
    def hql_query
    def hql_params = []

    if ( this.getId() != null ) {
      // Unsaved components can't have combo relations
      RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, getComboTypeValue(propertyName))
      
      if(status && status!="null") status_ref = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, status);

      hql_query = "from Combo where type=? "
      hql_params += type
      if (isComboReverse(propertyName)) {
        hql_query += " and toComponent=?"
        hql_params += this
      } else {
        hql_query += " and fromComponent=?"
        hql_params += this
      }
      if(status_ref){
        hql_query += " and status=?"
        hql_params += status_ref
      }


      combos = Combo.executeQuery(hql_query,hql_params)

//       log.debug("Qry: ${hql_query}, Params:${hql_params} : result.size=${combos?.size()}");
    }
    else {
      log.debug("This.id == null");
    }

    return combos
  }

  @Transient
  public String getDerivedName() {
    return name;
  }

  @Override
  public boolean equals(Object obj) {
    Object o = KBComponent.deproxy(obj)
    if ( o != null ) {
      // Deproxy the object first to ensure it isn't a hibernate proxy.
      return (this.getClassName() == o.getClass().name) && (this.getId() == o.getId())
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
    "${name?:''} (${getNiceName()} ${this.id})".toString()
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

    // SO: Think the issue was actually that deproxy was being called on the list of items when iterating [each (el in val)]
    // should have been called on el not val.
    def ignore_list = [
      'id',
      'outgoingCombos',
      'incomingCombos',
//      'reviewRequests',
      'tags',
      'systemOnly',
      'additionalProperties',
//      'skippedTitles',
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
      def val = this."${prop}"

      switch (val) {

        case {it instanceof Collection} :
          def newVals = []
          for (el in val) {

            // Deproxy the item in the list and then add.
            def newVal = KBComponent.deproxy(el)
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
          props["${prop}"] = KBComponent.deproxy(val)
      }
    }

    props
  }

  /**
   * Get the list of all properties and ids.
   */
  @Transient
  public Map getAllPropertiesWithLinks(boolean addCombos = true) {

    def ignore_list = [
      'id',
      'outgoingCombos',
      'incomingCombos',
      'reviewRequests',
      'tags',
      'systemOnly',
      'additionalProperties',
//      'skippedTitles',
//      'variantNames',
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

    if (addCombos) {
      localProps += allComboPropertyNames
    }

    localProps.each { prop ->

      // Ignore the ones in the list.
      if (prop in ignore_list) {
        return
      }

      // println("Deproxy ${prop}");
      def val = this."${prop}"

      switch (val) {

        case {it instanceof Collection} :
          def newVals = []
          for (el in val) {

            // Deproxy the item in the list and then add.
            def newVal = processPropVal(el)

            newVals << newVal
          }

          props["${prop}"] = newVals
          break
        default :
          props["${prop}"] = processPropVal(val)
      }
    }

    props
  }

  private def processPropVal(el) {
    def newVal = KBComponent.deproxy(el)
    def result = null

    if (newVal && grailsApplication.isDomainClass(newVal."class")) {
      def obj_label = null

      if (newVal.hasProperty('username')) {
        obj_label = newVal.username
      }
      else if (newVal.hasProperty('name')) {
        obj_label = newVal.name
      }
      else if (newVal.hasProperty('value')) {
        obj_label = newVal.value
      }
      else if (newVal.hasProperty('variantName')) {
        obj_label = newVal.variantName
      }

      result = ['oid': "${newVal.class.name}:${newVal.id}", 'label': obj_label]
    }
    else {
      result = newVal
    }
    result
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
  def ensureVariantName(String name) {

    if (name.trim().size() != 0) {
      def normname = generateNormname(name)

      // Check that name is not already a name or a variant, if so, add it.
      def existing_component = KBComponent.findByNormname( normname )

      if ( existing_component == null ) {

        // Variant names use different normalisation method.
        normname = GOKbTextUtils.normaliseString(name)

        // not already a name
        // Make sure not already a variant name
        def existing_variants = KBComponentVariantName.findAllByNormVariantName(normname)
        if ( existing_variants.size() == 0 ) {
          KBComponentVariantName kvn = new KBComponentVariantName( owner:this, variantName:name ).save()
        }
        else {
          log.error("Unable to add ${name} as an alternate name to ${id} - it's already an alternate name....");
        }

      }
      else {
        log.error("Unable to add ${name} as an alternate name to ${id} - it's already name for ${existing_component.id}");
      }
    }
    else {
      log.error("No viable variant name supplied!")
    }

  }

  /**
   *  Accept a map of namespace:x,identifier:y pairs. Every identifier which does match something must match the same component
   *  Non-matches are OK
   */
  @Transient
  static def secureIdentifierLookup(candidate_identifiers) {

    def result = null;

    def base_query = "select distinct c.fromComponent from Combo as c where c.toComponent in ( :l )"
    def identifier_list = []

    candidate_identifiers.each { id ->
      identifier_list.add(Identifier.lookupOrCreateCanonicalIdentifier(id.namespace, id.value))
    }

    def qresult = Combo.executeQuery(base_query,[l:identifier_list],[readOnly:true]);

    if ( qresult.size() == 1 ) {
      result = qresult[0]
    }
    else if ( qresult.size() == 0 ) {
    }
    else {
      def matching_identifiers = qresult.collect{it.id}
      throw new Exception("secureIdentifierLookup found multiple (${qresult.size()}) matching components (${matching_identifiers}) for a supposedly unique set of identifiers: ${candidate_identifiers}");
    }

    result
  }

  @Transient
  public String getDisplayName() {
    return name
  }

  @Transient
  def getNotes() {
    return Note.findAllByOwnerClassAndOwnerId(this.class.name, this.getId())
  }

  @Transient
  def getDecisionSupportLines(filter=null) {

    // Return an array consisting of DS Categories, in each category the Criterion and then null or the currently selected value
    def result = [:]
    def criterion = null;

    // N.B. for steve.. saying "if id != null" always fails - id is hibernate injected - should investigate this
    if (getId() != null) {
          // N.B. Long standing bug in hibernate means that dsac.appliedTo = ? throws a 'can only ref props in the driving table' exception
          // Workaround is to use the id directly
          log.debug("Package being processed (KB COMPONENT): ${getId()}")
          criterion = DSCriterion.executeQuery('select c, dsac from DSCriterion as c left outer join c.appliedCriterion as dsac with dsac.appliedTo.id = ?', getId());
          def currentUser = springSecurityService.currentUser

          def criterionMap = [:] //Convert results to group many DSAppliedCriterion's (Val) to a DSCriterion (Key)
          criterion.each{ c ->
              if (!criterionMap.containsKey(c[0]))
                  criterionMap.put(c[0], []);
              if (c[1])
                  criterionMap[c[0]].add(c[1])
          }

          Closure dates = { a, b -> a.lastUpdated <= b.lastUpdated? 1 : -1 }
          criterionMap.each { c, acrit ->

              def cat_code = c.owner.code //e.g. Fromat,Access - Read Online, etc.

              if (result[cat_code] == null)
                  result[cat_code] = [description: c.owner.description, 
                                      id:c.owner.id, 
                                      criterion: [:],
                                      comment_count:0,
                                      vote_count:0,
                                      vote_y_count:0,
                                      vote_n_count:0,
                                      vote_o_count:0 ] //criterion now a map

              // Add criteria title, current value if present, a string of componentId:CriteriaId (For setter/getter)
              if (!result[cat_code].criterion[c.id]) {
                  // Set all params.
                  //use criterion key instead for id
                  result[cat_code].criterion[c.id] = [
                          "title"        : c.title,         //Downloadable PDF, Embedded PDF, etc.
                          "description"  : c.description,   //Downloadable PDF, Embedded PDF, etc.
                          "explanation"  : c.explanation,   //Downloadable PDF, Embedded PDF, etc.
                          "title"        : c.title,         //Downloadable PDF, Embedded PDF, etc.
                          "appliedTo"    : getId(),         //Package extends KBComponent
                          "yourVote"     : [],              //logged in users vote
                          "otherVotes"   : [],              //Every else minus logged in & master vote
                          "voteCounter"  : [0,0,0,0],       //Red,Amber,Green,Unknown
                          "notes"        : [],              //Comments organised
                          "deletedNotes" : []               //Comments organised
                  ]
              }

              //ORDERING
              def liveOrg = [] //Live comments by logged in user domain, in date order (last updated)
              def deleted = [] //Remaining deleted comments by last updated
              acrit.each { ac ->

                  //Your votes placeholder
                  if (currentUser == ac?.user) {
                      // Current users vote.
                      result[cat_code].criterion[c.id]['yourVote'] = [
                              ac?.value?.value, //colour
                              ac,               //dsac
                              ac.user           //user
                      ]
                  }
                  else {
                      //Has there been any other vote
                      result[cat_code].criterion[c.id]['otherVotes'] << [
                              ac?.value?.value,
                              ac,
                              ac?.user
                      ]
                  }

                  //DSAppliedCriterion level, not possible to check if deleted unless loop through each individual note
                  //colour value is per vote, additional checks will need to be made
                  switch (ac?.value?.value) {
                      case 'Red':
                          result[cat_code].criterion[c.id]['voteCounter'][0]++;
                          result[cat_code].vote_n_count++;
                          result[cat_code].vote_count++;
                          break
                      case 'Amber':
                          result[cat_code].criterion[c.id]['voteCounter'][1]++;
                          result[cat_code].vote_o_count++;
                          result[cat_code].vote_count++;
                          break
                      case 'Green':
                          result[cat_code].criterion[c.id]['voteCounter'][2]++;
                          result[cat_code].vote_y_count++;
                          result[cat_code].vote_count++;
                          break
                      default:
                          result[cat_code].criterion[c.id]['voteCounter'][3]++;
                          break
                  }

                  //Notes processing, for ordering and separation of deleted notes
                  ac?.notes?.each { note ->
                    result[cat_code].comment_count++;

                    def comment_user_is_curator = false
                    if ( filter == 'curator' ) {
                    }

                    // Control comment inclusion based on filter
                    if ( 
                           ( filter == null ) || ( filter=='all' ) || ( filter == '' ) ||                                // NO filter == everything
                         ( ( filter == 'mylib' )    && (  note.criterion.user.org == currentUser.org ) ) ||              // User only wants comments from their own org
                         ( ( filter == 'otherlib' ) && ( note.criterion.user.org != currentUser.org ) ) ||               // HEIs other than the users
                         ( ( filter == 'vendor' )   && ( note.criterion.user.org?.mission?.value == 'Commercial' ) ) ||  // Filter to vendor comments
                         ( ( filter == 'curator' )  && comment_user_is_curator )                                         // User is a curator
                       ) { 
                      if (!note.isDeleted )
                          liveOrg.add(note)
                      else if (note.isDeleted)
                          deleted.add(note)
                      else
                          liveOrg.add(note)
                    }
                  }

              }
              //End of DSAppliedCriterion processing for current criterion. Now to sort the notes...

              liveOrg.sort(true,dates)
              result[cat_code].criterion[c.id]['notes'].addAll(liveOrg)

              deleted.sort(true,dates)
              result[cat_code].criterion[c.id]['deletedNotes'].addAll(deleted)
          }

          return result
    }
  }

  def expunge() {
    log.debug("Component expunge");
    def result = [deleteType:this.class.name, deleteId:this.id]
    log.debug("Removing all components");
    Combo.executeUpdate("delete from Combo as c where c.fromComponent=:component or c.toComponent=:component",[component:this])
    ComponentWatch.executeUpdate("delete from ComponentWatch as cw where cw.component=:component",[component:this])
    KBComponentAdditionalProperty.executeUpdate("delete from KBComponentAdditionalProperty as c where c.fromComponent=:component",[component:this]);
    KBComponentVariantName.executeUpdate("delete from KBComponentVariantName as c where c.owner=:component",[component:this]);

    ReviewRequestAllocationLog.executeUpdate("delete from ReviewRequestAllocationLog as c where c.rr in ( select r from ReviewRequest as r where r.componentToReview=:component)",[component:this]);
    def events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant = :component",[component:this])

    events_to_delete.each {
      ComponentHistoryEventParticipant.executeUpdate("delete from ComponentHistoryEventParticipant as c where c.event = ?",[it])
      ComponentHistoryEvent.executeUpdate("delete from ComponentHistoryEvent as c where c.id = ?", [it.id])
    }
//     ComponentHistoryEventParticipant.executeUpdate("delete from ComponentHistoryEventParticipant as c where c.participant = :component",[component:this]);

    ReviewRequest.executeUpdate("delete from ReviewRequest as c where c.componentToReview=:component",[component:this]);
    ComponentPerson.executeUpdate("delete from ComponentPerson as c where c.component=:component",[component:this]);
    ComponentSubject.executeUpdate("delete from ComponentSubject as c where c.component=:component",[component:this]);
    ComponentIngestionSource.executeUpdate("delete from ComponentIngestionSource as c where c.component=:component",[component:this]);
    this.delete(flush:true, failOnError:true)
    result;
  }

  @Transient
  def addCoreGOKbXmlFields(builder, attr) {
    def cids = this.ids ?: []
    String cName = this.class.name
    
    // Singel props.
    builder.'name' (name)
    builder.'status' (status?.value)
    builder.'editStatus' (editStatus?.value)
    builder.'shortcode' (shortcode)
    
    // Identifiers
    builder.'identifiers' {
      cids?.each { tid ->
        builder.'identifier' ('namespace':tid?.namespace?.value, 'value':tid?.value)
      }
      if ( grailsApplication.config.serverUrl || grailsApplication.config.baseUrl ) {
        builder.'identifier' ('namespace':'originEditUrl', 'value':"${grailsApplication.config.serverUrl ?: grailsApplication.config.baseUrl}/resource/show/${cName}:${id}")
      }
    }
    
    // Variant Names
    if ( variantNames ) {
      builder.'variantNames' {
        variantNames.each { vn ->
          builder.'variantName' ( vn.variantName )
        }
      }
    }
    
    // Tags
//     if ( tags ) {
//       builder.'tags' {
//         tags.each { tag ->
//           builder.'tag' (tag.value)
//         }
//       }
//     }
    
    if (additionalProperties) {
      builder.'additionalProperties' {
        additionalProperties.each { prop ->
          String pName = prop.propertyDefn?.propertyName
          if (pName && prop.apValue) {
            builder.'additionalProperty' ('name':pName, 'value':prop.apValue)
          }
        }
      }
    }
    if (fileAttachments) {
      builder.'fileAttachments' {
        fileAttachments.each { fa ->
          builder.'fileAttachment' {
            builder.'guid' (fa.guid)
            builder.'md5' (fa.md5)
            builder.'uploadName' (fa.uploadName)
            builder.'uploadMimeType' (fa.uploadMimeType)
            builder.'filesize' (fa.filesize)
            builder.'doctype' (fa.doctype)
            builder.'content' {
              builder.'mkp'.yieldUnescaped "<![CDATA[${fa.fileData.encodeBase64().toString()}]]>"
            }
          }
        }
      }
    }
    
    if (source) {
      builder.'source' {
        source.with {

          addCoreGOKbXmlFields(builder, attr)

          builder.'url' (url)
          builder.'defaultAccessURL' (defaultAccessURL)
          builder.'explanationAtSource' (explanationAtSource)
          builder.'contextualNotes' (contextualNotes)
          builder.'frequency' (frequency)
          builder.'ruleset' (ruleset)
          if ( defaultSupplyMethod ) {
            builder.'defaultSupplyMethod' ( defaultSupplyMethod.value )
          }
          if ( defaultDataFormat ) {
            builder.'defaultDataFormat' ( defaultDataFormat.value )
          }
          if ( responsibleParty ) {
            builder.'responsibleParty' {
              builder.'name' (responsibleParty.name)
            }
          }
        }
      }
    }
  }

  // Given the type return a string such as "1.23 GBP" which represents the CURRENT
  // price for the type variant. Default type to "list" if null is passed in.
  public String getPrice(String type) {
    String result = null;
    String price_type = type ?: 'list'
    Date now = new Date()
    def cpresult = ComponentPrice.executeQuery(CURRENT_PRICE_HQL,[t:price_type, c:this, d:now]);
    if ( cpresult.size() == 1 ) {
      result = String.format('%.2f',cpresult.get(0).price);
      if ( cpresult.get(0).currency != null ) {
        result += " ${cpresult.get(0).currency.value}"
      }
    }
    else if ( cpresult.size() == 0 ) {
      // No matches - return null
    }
    else {
      throw new RuntimeException("Multiple prices match for component ${this.id} price type ${price_type}");
    }

    return result;
  }

  /**
   * Set a price formatted as "nnnn.nn" or "nnnn.nn CUR"
   */
  public void setPrice(String type, String price) {
    Float f = null;
    RefdataValue rdv_type = null;
    RefdataValue rdv_currency = null;

    if ( price ) {
      Date now = new Date();

      String[] price_components = price.trim().split(' ');
      f = Float.parseFloat(price_components[0])
      rdv_type = RefdataCategory.lookupOrCreate('Price.type',type?:'list').save(flush:true, failOnError:true)

      if ( price_components.length == 2 ) {
        rdv_currency = RefdataCategory.lookupOrCreate('Currency',price_components[1].trim()).save(flush:true, failOnError:true)
      }

      // Close out any existing component prices
      ComponentPrice.executeUpdate('update ComponentPrice set endDate=:now where owner=:t and endDate is null and priceType=:pt',[t:this, now:now, pt:rdv_type]);

      // Create the new component price
      ComponentPrice cp = new ComponentPrice(
                                             owner:this, 
                                             priceType:rdv_type, 
                                             currency:rdv_currency ,
                                             startDate:now,
                                             endDate:null, 
                                             price:f).save(flush:true, failOnError:true);
    }
  }
  @Transient
  public userAvailableActions() {
    def user = springSecurityService.currentUser
    def allActions = []
    def result = []

    if (this.respondsTo('availableActions')) {
      allActions = this.availableActions()

      allActions.each { ao ->
        if (ao.perm == "delete" && !this.isDeletable()) {
        }
        else if (ao.perm == "admin" && !this.isAdministerable()) {
        }
        else if (ao.perm == "su" && !user.hasRole('ROLE_SUPERUSER')) {
        }
        else {
          result.add(ao)
        }
      }
    }
    result
  }
}
