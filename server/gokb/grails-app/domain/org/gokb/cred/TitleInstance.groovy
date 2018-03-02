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
  RefdataValue	OAStatus
  Work work
  Date publishedFrom
  Date publishedTo
  String coverImage

  private static refdataDefaults = [
    "medium"		: "Journal",
    "pureOA"		: "No",
    "OAStatus"  : "Unknown"
  ]

  static mapping = {
    // From TitleInstance
    includes KBComponent.mapping
  }

  // This map is used to convey information about the title in general processing. The initial usecase is so that we can attach
  // information about how this specific title was located, for example, by class 1 identifier match, or some other method
  // title_status_properties.matched_by='Title In Title History' is used when the title was matched by a title string in the context of a title history
  @Transient
  public title_status_properties = [:]

  public void addVariantTitle (String title, String locale = null) {

    // Check that the variant is not equal to the name of this title first.
    if (!title.equalsIgnoreCase(this.name)) {
    
      def normTitle = GOKbTextUtils.normaliseString(title)

      // Need to compare the existing variant names here. Rather than use the equals method,
      // we are going to compare certain attributes here.
      RefdataValue title_type = RefdataCategory.lookupOrCreate("KBComponentVariantName.VariantType", "Alternate Title")
      
      if(locale){
        RefdataValue locale_rd = RefdataValue.findByOwnerAndValue(RefdataCategory.findByDesc("KBComponentVariantName.Locale"), (locale))
      }

      // Each of the variants...
      def existing = variantNames.find {
        KBComponentVariantName name = it
        return (name.locale == locale_rd && name.variantType == title_type
        && name.getNormVariantName().equals(normTitle))
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
    OAStatus (nullable:true, blank:false)
    publishedFrom (nullable:true, blank:false)
    publishedTo (nullable:true, blank:false)
    coverImage (nullable:true, blank:true)
    work (nullable:true, blank:false)
  }

  def availableActions() {
    [ [code:'method::deleteSoft', label:'Delete'],
      [code:'title::transfer', label:'Title Transfer'],
      [code:'title::change', label:'Title Change'],
      [code:'title::merge', label:'Title Merge']
//       [code:'title::reconcile', label:'Title Reconcile']
    ]
  }

  public String getNiceName() {
    return "Title";
  }

  public Org getCurrentPublisher() {
    def result = null;
    def publisher_combos = getCombosByPropertyName('publisher')
    def highest_end_date = null;

    publisher_combos.each { Combo pc ->
      if ( ( pc.endDate == null ) ||
           ( highest_end_date == null) ||
           ( pc.endDate > highest_end_date ) ) {

        if (isComboReverse('publisher')) {
          if ( pc.fromComponent.status?.value == 'Deleted' ) {
          }
          else {
            highest_end_date = pc.endDate
            result = pc.fromComponent
          }
        } else {
          if ( pc.toComponent.status?.value == 'Deleted' ) {
          }
          else {
            highest_end_date = pc.endDate
            result = pc.toComponent
          }
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
    if (params.filter1) {
      ql = TitleInstance.executeQuery("select t.id, t.name from TitleInstance as t where ( lower(t.name) like ? or exists ( select c from Combo as c where c.fromComponent = t and c.toComponent in ( select id from Identifier as id where id.value like ? ) ) AND t.status.value = ? )", ["${params.q?.toLowerCase()}%","${params.q}%", params.filter1],[max:20]);
    }else{
      ql = TitleInstance.executeQuery("select t.id, t.name from TitleInstance as t where lower(t.name) like ? or exists ( select c from Combo as c where c.fromComponent = t and c.toComponent in ( select id from Identifier as id where id.value like ? ) )", ["${params.q?.toLowerCase()}%","${params.q}%"],[max:20]);
    }

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
    query:" from TitleInstance as o ",
    statusFilter:"where o.status.value != 'Expected'",
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

    try {
      def tids = getIds() ?: []
      def tipps = getTipps()
      def theIssuer = getIssuer()
      // def thePublisher = getPublisher()
      def publisher_combos = getCombosByPropertyName('publisher')

      // def identifiers = Combo.executeQuery('select c.toComponent from Combo as c where c.fromComponent=:t and c.type.value :idtype and c.status.value != :d',
      //                                      [t:this,idtype:'KBComponent.ids',d:'Deleted'])

      def history = getTitleHistory()

      builder.'gokb' (attr) {
        builder.'title' (['id':(id)]) {

          addCoreGOKbXmlFields(builder, attr)
          
          builder.'imprint' (imprint?.name)
          builder.'medium' (medium?.value)
          builder.'OAStatus' (OAStatus?.value)
          builder.'continuingSeries' (continuingSeries?.value)
          builder.'publishedFrom' (publishedFrom)
          builder.'publishedTo' (publishedTo)
          builder.'issuer' (issuer?.name)

          builder.'publishers' {
            publisher_combos?.each { Combo pc ->
              def pub_org = null
              if (isComboReverse('publisher')) {
                pub_org = pc.fromComponent
              }
              else {
                pub_org = pc.toComponent
              }

              if ( pub_org ) {
                def org_ids = pub_org.getIds()

                builder."publisher" (['id': pub_org?.id]) {
                  "name" (pub_org?.name)
                  if ( pc.startDate ) {
                    "startDate" (pc.startDate)
                  }
                  if ( pc.endDate ) {
                    "endDate" (pc.endDate)
                  }
                  if (pc.status) {
                    "status" (pc.status)
                  }
                  builder."identifiers" {
                    org_ids?.each { org_id ->
                      builder.'identifier' ('namespace':org_id?.namespace?.value, 'value':org_id?.value)
                    }
                    if ( grailsApplication.config.serverUrl ) {
                      builder.'identifier' ('namespace':'originEditUrl', 'value':"${grailsApplication.config.serverUrl}/resource/show/org.gokb.cred.Org:${pub_org?.id}")
                    }
                  }
                }
              }
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
                  if(hti){
                    "from" {
                      title(hti.name)
                      internalId(hti.id)
                      "identifiers" {
                        hti.getIds()?.each { tid ->
                          builder.'identifier' ('namespace':tid.namespace?.value, 'value':tid.value, 'datatype':tid.namespace.datatype?.value)
                        }
                        if ( grailsApplication.config.serverUrl ) {
                          builder.'identifier' ('namespace':'originEditUrl', 'value':"${grailsApplication.config.serverUrl}/resource/show/${hti.class.name}:${hti.id}")
                        }
                      }
                    }
                  }
                }
                he.to.each { hti ->
                  if(hti){
                    "to" {
                      title(hti.name)
                      internalId(hti.id)
                      "identifiers" {
                        hti.getIds()?.each { tid ->
                          builder.'identifier' ('namespace':tid.namespace?.value, 'value':tid.value)
                        }
                        if ( grailsApplication.config.serverUrl ) {
                          builder.'identifier' ('namespace':'originEditUrl', 'value':"${grailsApplication.config.serverUrl}/resource/show/${hti.class.name}:${hti.id}")
                        }
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
                builder.'package' (['id':pkg?.id]) {
                  builder.'name' (pkg?.name)
                }

                def platform = tipp.hostPlatform
                builder.'platform'(['id':platform?.id]) {
                  builder.'name' (platform?.name)
                }

                'access'(start:tipp.accessStartDate?sdf.format(tipp.accessStartDate):null,end:tipp.accessEndDate?sdf.format(tipp.accessEndDate):null)

                builder.'coverage'(
                  startDate:(tipp.startDate ? sdf.format(tipp.startDate):null),
                  startVolume:tipp.startVolume,
                  startIssue:tipp.startIssue,
                  endDate:(tipp.endDate ? sdf.format(tipp.endDate):null),
                  endVolume:tipp.endVolume,
                  endIssue:tipp.endIssue,
                  coverageDepth:tipp.coverageDepth?.value,
                  coverageNote:tipp.coverageNote,
                  embargo: tipp.embargo
                )

                builder.'url'(tipp.url)
              }
            }
          }
        }
      }
    }
    catch ( Exception e ) {
      log.error("problem creating record",e);
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
      if ( ( from_titles?.size() == 1 ) && ( to_titles?.size() == 1 ) && ( from_titles[0].participant?.id != to_titles[0].participant?.id ) ) {
        hint="Rename"
      }

      result.add( [ "id":(he.id), date:he.eventDate, from:from_titles.collect{it.participant}, to:to_titles.collect{it.participant}, hint:hint ] );
    }
    return result;
  }

  def addTitlesToHistory(title, final_list, depth) {
    def result = false;
    
    if ( title ) {
      // Check to see whether this component has an id first. If not then return an empty set.
      if (title.id && title.id > 0) {
        if ( final_list.contains(title) ) {
          return;
        }
        else {
          // Find all history events relating to this title, and for each title related, add it to the final_list if it's not already in the list
          final_list.add(title)
          def all_related_history_events = ComponentHistoryEvent.executeQuery('select eh from ComponentHistoryEvent as eh where exists ( select ehp from ComponentHistoryEventParticipant as ehp where ehp.participant = ? and ehp.event = eh ) order by eh.eventDate',title)
          all_related_history_events.each { the ->
            the.participants.each { p ->
              if ( p.participant ) {
                addTitlesToHistory(p.participant, final_list, depth+1)
              }
              else {
                log.error("Title history participant was null - HistoryEvent==${the}");
              }
            }
          }
        }
      }
    }
    else {
      log.error("Attempt to addTitlesToHistory for a null title");
    }

    result;
  }

  @Transient
  def getFullTitleHistory() {
    def result = [:]
    
    // Check to see whether this component has an id first. If not then return an empty set.
    if (id && id > 0) {
      def il = []
      addTitlesToHistory(this,il,0)
      result.fh = ComponentHistoryEvent.executeQuery('select eh from ComponentHistoryEvent as eh where exists ( select ehp from ComponentHistoryEventParticipant as ehp where ehp.participant in (:titleList) and ehp.event = eh ) order by eh.eventDate asc',[titleList:il])
    }
    result;
  }

  def getPrecedingTitleId() {
    log.debug('getPrecedingTitleId')
    def preceeding_titles = []
    // Work through title history, see if there is a preceeding title...
    def ths = ComponentHistoryEvent.executeQuery('select eh from ComponentHistoryEvent as eh where exists ( select ehp from ComponentHistoryEventParticipant as ehp where ehp.participant = ? and ehp.participantRole=? and ehp.event = eh ) order by eh.eventDate desc',[this, 'out'])
    if ( ths.size() > 0 ) {
      ths[0].participants.each { p ->
        if ( p.participantRole == 'in') {
          preceeding_titles.add(p.participant.id)
        }
      }
    }
    return preceeding_titles.join(', ')
  }

  def findInTitleHistory(title) {
    def result = null;

    def full_th = getFullTitleHistory()
    full_th.fh.each { history_event ->
      history_event.participants.each { history_event_participant ->
        if ( history_event_participant.name == title ) {
          result = history_event_participant
        }
      }
    }

    if ( result ) {  
      result.title_status_properties.matched_by='Title In Title History'
    }
    result
  }

  /**
   * titleDTO {
   *   title:'Title',
   *   publisher:'PubName',
   *   identifiers:[
   *      { type:'type', value:'value' },
   *      { type:'type', value:'value' },
   *   ],
   *   type:'Serial' or 'Monograph'
   * }
   */
  @Transient
  public static boolean validateDTO(titleDTO) {
    def result = true;
    result &= titleDTO != null
    result &= titleDTO.name != null
    result &= titleDTO.identifiers != null

    if ( !result ) {
      log.warn("Title Failed Validation ${titleDTO}");
    }

    result;
  }

  @Transient
  public static TitleInstance upsertDTO(titleLookupService,titleDTO,user=null) {
    def result = null;
    result = titleLookupService.find(titleDTO.name,
                                     titleDTO.publisher,
                                     titleDTO.identifiers,
                                     user,
                                     null,
                                     titleDTO.type=='Serial' ? 'org.gokb.cred.JournalInstance' : 'org.gokb.cred.BookInstance' )

    log.debug("Result of upsertDTO: ${result}");
    result;
  }

  // This is called by the titleLookupService::remapTitleInstance method but NOTE:: this is done
  // primarily so that the cpu-work and object creation of the work instance is done outside the
  // context of the primary hibernate session.
  def remapWork() {
    log.debug('remapWork');
    // BKM:TITLE + then FIRSTAUTHOR if duplicates found

      if ( ( normname ) && 
           ( normname.length() > 0 ) &&
           ( ! normname.startsWith('unknown title')) ) {
        // book bucket (Work) hashes are based on the normalised name.
        def h = GOKbTextUtils.generateComponentHash([normname]);

        log.debug("Searching for bucket matches for ${h}");
        def bucketMatches = Work.executeQuery('select w from Work as w where w.bucketHash = :h',[h:h]);

        switch( bucketMatches.size() ) {
          case 0:
            log.debug("No matches - create work");
            def w = new Work(name: name, bucketHash:h).save(flush:true, failOnError:true)
            this.work = w
            this.save(flush:true, failOnError:true)
            break;
          case 1:
            log.debug("Good enough unique match on bucketHash");
            this.work = bucketMatches[0]
            this.save(flush:true, failOnError:true)
            break;
          default:
            log.debug("Mached multiple works - use discriminator properties");
            break;
        }
      }
  }

}
