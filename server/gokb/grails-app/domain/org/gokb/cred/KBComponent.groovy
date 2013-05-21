package org.gokb.cred

import javax.persistence.Transient
import grails.util.GrailsNameUtils
abstract class KBComponent {

  static final String RD_STATUS = "KBComponent.Status"
  static final String STATUS_ACTIVE = "Active"
  static final String STATUS_DELETED = "Deleted"
  
  static auditable = true

  String impId
  // Canonical name field - title for a title instance, name for an org, etc, etc, etc
  String name
  String normname
  String shortcode
  
  RefdataValue status
  
  Set tags = []
  List additionalProperties = []
  List outgoingCombos = []
  List incomingCombos = []
  List ids = []
  
  static mappedBy = [
    ids: 'component',
    outgoingCombos: 'fromComponent',
    incomingCombos:'toComponent',
//    orgs: 'linkedComponent',
    additionalProperties: 'fromComponent']
  
  static hasMany = [
    ids: IdentifierOccurrence,
    tags:RefdataValue,
    outgoingCombos:Combo,
    incomingCombos:Combo,
    additionalProperties:KBComponentAdditionalProperty
  ]

  static mapping = {
    id column:'kbc_id'
    version column:'kbc_version'
    impId column:'kbc_imp_id', index:'kbc_imp_id_idx'
    name column:'kbc_name'
    normname column:'kbc_normname'
	status column:'kbc_status_rv_fk'
    shortcode column:'kbc_shortcode', index:'kbc_shortcode_idx'
    tags joinTable: [name: 'kb_component_refdata_value', key: 'kbcrdv_kbc_id', column: 'kbcrdv_rdv_id']
  }

  static constraints = {
    impId(nullable:true, blank:false)
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
        identifier {
          eq('value',idvalue)
          ns {
            eq('ns',idtype)
          }
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
	
	// Check the status
	if (status == null) {
	  // Lookup or create the refdata in a separate session.
	  RefdataCategory.withNewSession { session ->
		status = RefdataCategory.lookupOrCreate(RD_STATUS, STATUS_ACTIVE)
	  }
	}
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
	org.hibernate.Hibernate.getClass(this).getSimpleName()
  }

  @Transient
  abstract getPermissableCombos()
}
