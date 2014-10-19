package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*

@Log4j
class TitleInstance extends KBComponent {


  // title is now NAME in the base component class...
  RefdataValue	medium
  RefdataValue	pureOA
  RefdataValue	continuingSeries
  RefdataValue	reasonRetired
  RefdataValue	oa
  Date publishedFrom
  Date publishedTo
//  String imprint

  private static refdataDefaults = [
    "medium"		: "Journal",
    "pureOA"		: "No"
  ]

  public void addVariantTitle (String title, String locale = "EN-us") {
    
    // Check that the variant is not equal to the name of this title first.
    if (!title.equalsIgnoreCase(this.name)) {

      // Need to compare the existing variant names here. Rather than use the equals method,
      // we are going to compare certain attributes here.
      RefdataValue title_type = RefdataCategory.lookupOrCreate("KBComponentVariantName.VariantType", "Alternate Title")
      RefdataValue locale_rd = RefdataCategory.lookupOrCreate("KBComponentVariantName.Locale", (locale))
      
      // Each of the variants...
      def existing = variantNames.find {
        KBComponentVariantName name = it
        return (name.locale == locale_rd && name.variantType == title_type
        && name.getVariantName().equalsIgnoreCase(title))
      }
  
      if (!existing) {
        addToVariantNames(
            new KBComponentVariantName([
              "variantType"	: (title_type),
              "locale"		: (locale_rd),
              "status"		: RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_CURRENT),
              "variantName"	: (title)
            ])
            )
      } else {
        log.debug ("Not adding variant title as it is the same as an existing variant.")
      }
      
    } else {
      log.debug ("Not adding variant title as it is the same as the actual title.")
    }
  }

  static hasByCombo = [
    issuer		: Org,
    translatedFrom	: TitleInstance,
    absorbedBy		: TitleInstance,
    mergedWith		: TitleInstance,
    renamedTo		: TitleInstance,
    splitFrom		: TitleInstance,
    imprint		: Imprint
  ]

  static manyByCombo = [
    tipps : TitleInstancePackagePlatform,
    publisher : Org,
    //        ids     :  Identifier
  ]

  static constraints = {

    medium (nullable:true, blank:false)
    pureOA (nullable:true, blank:false)
    reasonRetired (nullable:true, blank:false)
    oa (nullable:true, blank:false)
//    imprint (nullable:true, blank:false)
    publishedFrom (nullable:true, blank:false)
    publishedTo (nullable:true, blank:false)
  }

  def availableActions() {
    [ [code:'method::deleteSoft', label:'Delete'],
      [code:'title::transfer', label:'Title Transfer'],
      [code:'title::change', label:'Title Change'],
      // [code:'title::reconcile', label:'Title Reconcile'] 
    ]
  }

  public String getNiceName() {
    return "Title";
  }

  public Org getCurrentPublisher() {
    def result = null;
    def publisher_combos = getCombosByPropertyName('publisher')
    publisher_combos.each { Combo pc ->
      if ( pc.endDate == null ) {
        if (isComboReverse('publisher')) {
          result = pc.fromComponent
        } else {
          result = pc.toComponent
        }
      }
    }
    result
  }

  /**
   * Close off any existing publisher relationships and add a new one for this publiser
   */
  def changePublisher(new_publisher, boolean null_start = false) {

    if ( new_publisher != null ) {

      def current_publisher = getCurrentPublisher()

      if ( ( current_publisher != null ) && ( current_publisher.id==new_publisher.id ) ) {
        // no change... leave it be
        return false
      }
      else {
        def publisher_combos = getCombosByPropertyName('publisher')
        publisher_combos.each { pc ->
          if ( pc.endDate == null ) {
            pc.endDate = new Date();
          }
        }

        // Now create a new Combo
        RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, getComboTypeValue('publisher'))
        Combo combo = new Combo(
            type    : (type),
            status  : DomainClassExtender.getComboStatusActive(),
            startDate : (null_start ? null : new Date())
            )

        // Depending on where the combo is defined we need to add a combo.
        if (isComboReverse('publisher')) {
          combo.fromComponent = new_publisher
          addToIncomingCombos(combo)
        } else {
          combo.toComponent = new_publisher
          addToOutgoingCombos(combo)
        }
        
        new_publisher.save()
        save()

        return true
        //        publisher.add(new_publisher)
      }
    }

    // Returning false if we get here implies the publisher has not been changed.
    return false
  }


  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    // ql = TitleInstance.findAllByNameIlike("${params.q}%",params)
    // Return all titles where the title matches (Left anchor) OR there is an identifier for the title matching what is input
    ql = TitleInstance.executeQuery("select t.id, t.name from TitleInstance as t where lower(t.name) like ? or exists ( select c from Combo as c where c.fromComponent = t and c.toComponent in ( select id from Identifier as id where id.value like ? ) )", ["${params.q}%","${params.q}%"],[max:20]);

    if ( ql ) {
      ql.each { t ->
        result.add([id:"org.gokb.cred.TitleInstance:${t[0]}",text:"${t[1]} "])
      }
    }

    result
  }
  
  @Transient
  static def oaiConfig = [
    id:'titles',
    textDescription:'Title repository for GOKb',
    query:" from TitleInstance as o where o.status.value != 'Deleted'",
    pageSize:20
  ]

  /**
   *  Render this package as OAI_dc
   */
  @Transient
  def toOaiDcXml(builder, attr) {
    builder.'dc'(attr) {
      'dc:title' (name)
    }
  }

  /**
   *  Render this package as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def tipps = getTipps()
    def tids = getIds() ?: []
    def theIssuer = getIssuer()
    def thePublisher = getPublisher()

    def history = getTitleHistory()
    
    builder.'gokb' (attr) {
      builder.'title' (['id':(id)]) {
        builder.'name' (name)
        builder.'identifiers' {
          tids?.each { tid ->
            builder.'identifier' ('namespace':tid.namespace?.value, 'value':tid.value)
          }
          if ( grailsApplication.config.serverUrl != null ) {
            builder.'identifier' ('namespace':'originEditUrl', 'value':"${grailsApplication.config.serverUrl}/resource/show/org.gokb.cred.TitleInstance:${id}")
          }
        }
        
        if (thePublisher) {
          builder."publisher" (['id': thePublisher.id]) {
            "name" (thePublisher.name)
          }
        }
        
        if (theIssuer) {
          builder."issuer" (['id': theIssuer.id]) {
            "name" (theIssuer.name)
          }
        }
        
        builder.history() {
          history.each { he ->
            builder.historyEvent(['id':he.id]) {
              "date"(he.date)
              he.from.each { hti ->
                "from" {
                  title(hti.name)
                  internalId(hti.id)
                  "identifiers" {
                    hti.getIds()?.each { tid ->
                      builder.'identifier' ('namespace':tid.namespace?.value, 'value':tid.value)
                    }
                  
                  }
                }
              }
              he.to.each { hti ->
                "to" {
                  title(hti.name)
                  internalId(hti.id)
                  "identifiers" {
                    hti.getIds()?.each { tid ->
                      builder.'identifier' ('namespace':tid.namespace?.value, 'value':tid.value)
                    }
                  }
                }
              }
            }
          }
        }

        builder.'TIPPs' (count:tipps?.size()) {
          tipps?.each { tipp ->
            builder.'TIPP' (['id':tipp.id]) {
              
              def pkg = tipp.pkg
              builder.'package' (['id':pkg.id]) {
                builder.'name' (pkg.name)
              }
              
              def platform = tipp.hostPlatform
              builder.'platform'(['id':platform.id]) {
                builder.'name' (platform.name)
              }
              
              builder.'coverage'(
                startDate:(tipp.startDate ?sdf.format(tipp.startDate):null),
                startVolume:tipp.startVolume,
                startIssue:tipp.startIssue,
                endDate:(tipp.endDate?sdf.format(tipp.endDate):null),
                endVolume:tipp.endVolume,
                endIssue:tipp.endIssue,
                coverageDepth:tipp.coverageDepth?.value,
                coverageNote:tipp.coverageNote)
              if ( tipp.url != null ) { 'url'(tipp.url) }
            }
          }
        }
      }
    }
  }

  @Transient
  def getTitleHistory() {
    def result = []
    def all_related_history_events = ComponentHistoryEvent.executeQuery('select eh from ComponentHistoryEvent as eh where exists ( select ehp from ComponentHistoryEventParticipant as ehp where ehp.participant = ? and ehp.event = eh ) order by eh.eventDate',this)
    all_related_history_events.each { he ->
      def from_titles = he.participants.findAll { it.participantRole == 'in' };
      def to_titles = he.participants.findAll { it.participantRole == 'out' };

      def hint = "unknown"
      if ( ( from_titles?.size() == 1 ) && ( to_titles?.size() == 1 ) && ( from_titles[0].participant.id != to_titles[0].participant.id ) ) {
        hint="Rename"
      }

      result.add( [ "id":(he.id), date:he.eventDate, from:from_titles.collect{it.participant}, to:to_titles.collect{it.participant}, hint:hint ] );
    }
    return result;
  }


}
