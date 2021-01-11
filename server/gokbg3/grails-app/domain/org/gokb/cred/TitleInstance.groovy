package org.gokb.cred

import java.time.LocalDateTime
import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*

@Slf4j
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
    "medium" : "Journal",
    "pureOA"		: "No",
    "OAStatus"  : "Unknown"
  ]

  static touchOnUpdate = [
    "tipps",
    "tipls"
  ]

  static mapping = {
    // From TitleInstance
    includes KBComponent.mapping
    medium column:'medium_id', index:'ti_medium_idx'
  }

  @Override
  String getLogEntityId() {
      "${this.class.name}:${id}"
  }

  public static final String restPath = "/titles"

  static jsonMapping = [
    'ignore': [
      'pureOA',
      'continuingSeries',
      'reasonRetired',
      'work',
      'coverImage',
      'issuer',
      'translatedFrom',
      'absorbedBy',
      'mergedWith',
      'renamedTo',
      'splitFrom'
    ],
    'es': [
      'publisherUuid': "publisher.uuid",
      'publisherName': "publisher.name",
      'publisher': "publisher.id"
    ],
    'defaultEmbeds': [
      'ids',
      'variantNames',
      'publisher'
    ]
  ]

  // This map is used to convey information about the title in general processing. The initial usecase is so that we can attach
  // information about how this specific title was located, for example, by class 1 identifier match, or some other method
  // title_status_properties.matched_by='Title In Title History' is used when the title was matched by a title string in the context of a title history
  @Transient
  public title_status_properties = [:]

  public boolean addVariantTitle (String title, String locale = null) {

    // Check that the variant is not equal to the name of this title first.
    if (!title.equalsIgnoreCase(this.name)) {

      def normTitle = GOKbTextUtils.normaliseString(title)

      // Need to compare the existing variant names here. Rather than use the equals method,
      // we are going to compare certain attributes here.
      RefdataValue title_type = RefdataCategory.lookupOrCreate("KBComponentVariantName.VariantType", "Alternate Title")
      def locale_rd = null

      if(locale){
        locale_rd = RefdataValue.findByOwnerAndValue(RefdataCategory.findByDesc("KBComponentVariantName.Locale"), (locale))
      }

      // Each of the variants...
      def existing = variantNames.find {
        KBComponentVariantName name = it
        return (name.getNormVariantName().equals(normTitle))
      }

      if (!existing) {
          new KBComponentVariantName([
            "variantType"	: (title_type),
            "owner"     : this,
            "locale"		: (locale_rd),
            "status"		: RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_CURRENT),
            "variantName"	: (title)
          ])
          return true
      } else {
        log.debug ("Not adding variant title as it is the same as an existing variant.")
        return false
      }

    } else {
      log.debug ("Not adding variant title as it is the same as the actual title.")
      return false
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
    tipls : TitleInstancePlatform
    //        ids     :  Identifier
  ]

  static constraints = {

    medium (nullable:true, blank:false)
    pureOA (nullable:true, blank:false)
    reasonRetired (nullable:true, blank:false)
    OAStatus (nullable:true, blank:false)
    publishedFrom (nullable:true, blank:false)
    publishedTo  (validator: { val, obj ->
      if( obj.publishedFrom && val && (obj.hasChanged('publishedTo') || obj.hasChanged('publishedFrom')) && obj.publishedFrom > val ) {
        return ['endDate.endPriorToStart']
      }
    })
    coverImage (nullable:true, blank:true)
    work (nullable:true, blank:false)
    name (validator: { val, obj ->
      if ( !val && obj.hasChanged('name') ) {
        return ['notNull']
      }
    })
  }

  def availableActions() {
    [ [code:'method::deleteSoft', label:'Delete', perm:'delete'],
      [code:'setStatus::Current', label:'Set Current', perm:'admin'],
      [code:'setStatus::Expected', label:'Mark Expected'],
      [code:'title::transfer', label:'Title Transfer'],
      [code:'title::change', label:'Title Change'],
      [code:'title::merge', label:'Title Merge']
//       [code:'title::reconcile', label:'Title Reconcile']
    ]
  }

  @Override
  public String getNiceName() {
    return "Title";
  }

  public Org getCurrentPublisher() {
    def result = null;
    def publisher_combos = getCombosByPropertyNameAndStatus('publisher', 'Active')
    def highest_end_date = null

    publisher_combos.each { Combo pc ->
      if ( ( pc.endDate == null ) ||
           ( highest_end_date == null) ||
           ( pc.endDate > highest_end_date ) ) {

        if (isComboReverse('publisher')) {
          if ( pc.fromComponent.status?.value == 'Deleted' ) {
          }
          else if (result && !highest_end_date) {
          }
          else {
            highest_end_date = pc.endDate
            result = pc.fromComponent
          }
        } else {
          if ( pc.toComponent.status?.value == 'Deleted' ) {
          }
          else if (result && !highest_end_date) {
          }
          else {
            highest_end_date = pc.endDate
            result = pc.toComponent
          }
        }
      }
    }
    result = result ? Org.get(result.id) : null

    result
  }

  /**
   * Close off any existing publisher relationships and add a new one for this publiser
   */
  def changePublisher(new_publisher, boolean null_start = false) {

    if ( new_publisher != null ) {

      def current_publisher = getCurrentPublisher()
      def combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)

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
            status  : combo_active,
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

        this.save()

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
    def status_deleted = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    def status_filter = null

    if(params.filter1) {
      status_filter = RefdataCategory.lookup('KBComponent.Status', params.filter1)
    }

    def ql = null;
    // ql = TitleInstance.findAllByNameIlike("${params.q}%",params)
    // Return all titles where the title matches (Left anchor) OR there is an identifier for the title matching what is input
    ql = TitleInstance.executeQuery("select t from TitleInstance as t where t.status <> ? and ( lower(t.name) like ? or exists ( select c from Combo as c where c.fromComponent = t and c.toComponent in ( select id from Identifier as id where id.value like ? ) ) )", [status_deleted, "${params.q?.toLowerCase()}%","${params.q}%"],[max:20]);

    if ( ql ) {
      ql.each { t ->
        if( !status_filter || t.status == status_filter ){
          result.add([id:"${t.class.name}:${t.id}",text:"${t.name}", status:"${t.status?.value}"])
        }
      }
    }

    result
  }

  @Transient
  static def oaiConfig = [
    id:'titles',
    textDescription:'Title repository for GOKb',
    query:" from TitleInstance as o ",
    statusFilter:['Expected'],
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

    try {
      def tids = this.ids ?: []
      def tipps = getTipps()
      def theIssuer = getIssuer()
      // def thePublisher = getPublisher()
      def publisher_combos = getCombosByPropertyName('publisher')
      def people_combos = this.people ?: []

      // def identifiers = Combo.executeQuery('select c.toComponent from Combo as c where c.fromComponent=:t and c.type.value :idtype and c.status.value != :d',
      //                                      [t:this,idtype:'KBComponent.ids',d:'Deleted'])

      def history = getTitleHistory()

      builder.'gokb' (attr) {
        builder.'title' (['id':(id), 'uuid':(uuid)]) {

          addCoreGOKbXmlFields(builder, attr)

          if( this.class.name == 'org.gokb.cred.BookInstance' ) {

            builder.'editionNumber' (this.editionNumber)
            builder.'editionDifferentiator' (this.editionDifferentiator)
            builder.'editionStatement' (this.editionStatement)
            builder.'volumeNumber' (this.volumeNumber)
            builder.'dateFirstInPrint' (this.dateFirstInPrint)
            builder.'dateFirstOnline' (this.dateFirstOnline)
            builder.'firstEditor' (this.firstEditor)
            builder.'firstAuthor' (this.firstAuthor)
          }

          builder.'imprint' (imprint?.name)
          builder.'medium' (medium?.value)
          builder.'type' (this.class.simpleName)
          builder.'OAStatus' (OAStatus?.value)
          builder.'continuingSeries' (continuingSeries?.value)
          builder.'publishedFrom' (publishedFrom)
          builder.'publishedTo' (publishedTo)

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
                def org_ids = pub_org.ids ?: []

                builder."publisher" (['id': pub_org.id, 'uuid': pub_org.uuid]) {
                  "name" (pub_org.name)
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
                      builder.'identifier' ('namespace':org_id?.namespace?.value, 'namespaceName':org_id?.namespace?.name, 'value':org_id?.value)
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
            builder."issuer" (['id': theIssuer.id, 'uuid': theIssuer.uuid]) {
              "name" (theIssuer.name)
            }
          }

          else {
            builder.history() {
              history.each { he ->
                builder.historyEvent(['id':he.id]) {
                  "date"(he.date)
                  he.from.each { hti ->
                    if(hti){
                      "from" {
                        title(hti.name)
                        uuid(hti.uuid)
                        status(hti.status.value)
                        internalId(hti.id)
                        "identifiers" {
                          hti.ids?.each { tid ->
                            builder.'identifier' ('namespace':tid.namespace?.value, 'namespaceName':tid.namespace?.name, 'value':tid.value, 'datatype':tid.namespace.datatype?.value)
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
                        uuid(hti.uuid)
                        status(hti.status.value)
                        internalId(hti.id)
                        "identifiers" {
                          hti.ids?.each { tid ->
                            builder.'identifier' ('namespace':tid.namespace?.value, 'namespaceName':tid.namespace?.name, 'value':tid.value, 'datatype':tid.namespace.datatype?.value)
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
          }

          builder.'TIPPs' (count:tipps?.size()) {
            tipps?.each { tipp ->
              builder.'TIPP' (['id':tipp.id, 'uuid':tipp.uuid]) {

                status(tipp.status.value)

                def pkg = tipp.pkg
                builder.'package' (['id':pkg?.id, 'uuid':pkg?.uuid]) {
                  builder.'name' (pkg?.name)
                }

                def platform = tipp.hostPlatform
                builder.'platform'(['id':platform?.id, 'uuid':platform?.uuid]) {
                  builder.'name' (platform?.name)
                }

                builder.'subjectArea'(tipp.subjectArea?.trim())
                builder.'series'(tipp.series?.trim())
                if (tipp.prices && tipp.prices.size() > 0) {
                  builder.'prices'() {
                    tipp.prices.each { price ->
                      builder.'price' {
                        builder.'type'(price.priceType.value)
                        builder.'amount'(price.price)
                        builder.'currency'(price.currency)
                        builder.'startDate'(price.startDate)
                        if (price.endDate) {
                          builder.'endDate'(price.endDate)
                        }
                      }
                    }
                  }
                }

                def cov_statements = tipp.coverageStatements
                if(cov_statements?.size() > 0){
                  cov_statements.each { tcs ->
                    'coverage'(
                      startDate:(tcs.startDate ? "${tcs.startDate.toInstant().toString()}" : null),
                      startVolume:tcs.startVolume,
                      startIssue:tcs.startIssue,
                      endDate:(tcs.endDate ? "${tcs.endDate.toInstant().toString()}" : null),
                      endVolume:tcs.endVolume,
                      endIssue:tcs.endIssue,
                      coverageDepth:tcs.coverageDepth?.value?:tipp.coverageDepth?.value,
                      coverageNote:tcs.coverageNote,
                      embargo: tcs.embargo
                    )
                  }
                }
                else{

                  builder.'coverage'(
                    startDate:(tipp.startDate ? "${tipp.startDate.toInstant().toString()}" : null),
                    startVolume:tipp.startVolume,
                    startIssue:tipp.startIssue,
                    endDate:(tipp.endDate ? "${tipp.endDate.toInstant().toString()}" : null),
                    endVolume:tipp.endVolume,
                    endIssue:tipp.endIssue,
                    coverageDepth:tipp.coverageDepth?.value,
                    coverageNote:tipp.coverageNote,
                    embargo:tipp.embargo)
                }
                if ( tipp.url != null ) { 'url'(tipp.url) }
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
        if ( history_event_participant.participant.name == title ) {
          result = history_event_participant
        }
      }
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
  public static def validateDTO(titleDTO) {
    def result = ['valid':true, 'errors':[:]]

    if (titleDTO?.name?.trim() == false) {
      result.valid = false
      result.errors.name =  [[message: "Missing title name!", baddata: titleDTO.name]]
      return result
    }

    def ids_list = titleDTO.identifiers ?: titleDTO.ids

    LocalDateTime startDate = GOKbTextUtils.completeDateString(titleDTO.publishedFrom)
    LocalDateTime endDate = GOKbTextUtils.completeDateString(titleDTO.publishedTo, false)

    if ( titleDTO.publishedFrom && !startDate ) {
      result.valid = false
      result.errors.publishedFrom = [[message:"Unable to parse publishing start date ${titleDTO.publishedFrom}!", baddata: titleDTO.publishedFrom]]
    }

    if ( titleDTO.publishedTo && !endDate ) {
      result.valid = false
      result.errors.publishedTo = [[message:"Unable to parse publishing end date ${titleDTO.publishedTo}!", baddata: titleDTO.publishedTo]]
    }

    if (startDate && endDate && (endDate < startDate)) {
      result.valid = false
      result.errors.publishedTo = [[message:"Publishing end date must not be prior to its start date!", baddata: titleDTO.publishedTo]]
    }

    def id_errors = []

    ids_list?.each { idobj ->
      def id_def = [:]
      def ns_obj = null

      if (idobj instanceof Map) {
        def id_ns = idobj.type ?: (idobj.namespace ?: null)

        id_def.value = idobj.value

        if (id_ns instanceof String) {
          log.debug("Default namespace handling for ${id_ns}..")
          ns_obj = IdentifierNamespace.findByValueIlike(id_ns)
        }
        else if (id_ns) {
          log.debug("Handling namespace def ${id_ns}")
          ns_obj = IdentifierNamespace.get(id_ns)
        }

        if (!ns_obj) {
          id_errors.add([message: "Unable to lookup identifier namespace ${id_ns}!", baddata: id_ns, code: 404])
        }
        else {
          id_def.type = ns_obj.value
        }
      }
      else if (idobj instanceof Integer){
        Identifier the_id = Identifier.get(id_inc)

        if (!the_id) {
          id_errors.add([message:"Unable to lookup identifier object by ID!", baddata: idobj])
          result.valid = false
        }
      }
      else {
        log.warn("Missing information in id object ${idobj}")
        id_errors.add([message:"Missing information for identifier object!", baddata: idobj])
        result.valid = false
      }

      if (ns_obj && id_def.size() > 0) {
        if (!Identifier.findByNamespaceAndNormname(ns_obj, Identifier.normalizeIdentifier(id_def.value))) {
          if ( ns_obj.pattern && !(id_def.value ==~ ns_obj.pattern) ) {
            log.warn("Validation for ${id_def.type}:${id_def.value} failed!")
            id_errors.add([message:"Validation for identifier ${id_def.type}:${id_def.value} failed!", baddata: idobj])
            result.valid = false
          }
          else {
            log.debug("New identifier ..")
          }
        }
        else {
          log.debug("Found existing identifier ..")
        }
      }
    }

    if (id_errors.size() > 0) {
      result.errors.ids = id_errors
    }
    result
  }

  @Transient
  public static TitleInstance upsertDTO(titleLookupService,titleDTO,user=null,fullsync=false) {
    def result = null;
    def type = null

    if (titleDTO.type) {
      switch (titleDTO.type) {
        case "serial":
        case "Serial":
        case "Journal":
        case "journal":
          type = "org.gokb.cred.JournalInstance"
          break;
        case "monograph":
        case "Monograph":
        case "Book":
        case "book":
          type = "org.gokb.cred.BookInstance"
          break;
        case "Database":
        case "database":
          type = "org.gokb.cred.DatabaseInstance"
          break;
        case "Other":
        case "other":
          type = "org.gokb.cred.OtherInstance"
          break;
        default:
          log.warn("Missing type for title!")
          break;
      }
    }

    if (type) {
      result = titleLookupService.findOrCreate(titleDTO.name,
                                      titleDTO.publisher,
                                      titleDTO.identifiers,
                                      user,
                                      null,
                                      type,
                                      titleDTO.uuid,
                                      fullsync
                                  )
      log.debug("Result of upsertDTO: ${result}");
    }
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

  @Override
  @Transient
  def ensureVariantName(String name) {
    def result = null
    if (name.trim().size() != 0) {

      // Variant names use different normalisation method.
      def variant_normname = GOKbTextUtils.normaliseString(name)

      // not already a name
      // Make sure not already a variant name
      if ( !KBComponentVariantName.findByOwnerAndNormVariantName(this, variant_normname) ) {
        result = new KBComponentVariantName( owner:this, variantName:name ).save(flush:true)
      }
      else {
        log.debug("Unable to add ${name} as an alternate name to ${id} - it's already an alternate name....");
      }
    }
    else {
      log.error("No viable variant name supplied!")
    }
    result
  }

  def beforeUpdate() {
    def deleted_status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    if (this.isDirty('status') && this.status == deleted_status) {
      // Delete the tipps too as a TIPP should not exist without the associated
      // title.
      def tipps = getTipps()
      def tipls = getTipls()

      if ( tipps?.size() > 0 ) {
        def tipp_ids = tipps?.collect { it.id }
        Date now = new Date()

        TitleInstancePackagePlatform.executeUpdate("update TitleInstancePackagePlatform as t set t.status = :del, t.lastUpdated = :now where t.id IN (:ttd) and t.status != :del",[del: deleted_status, ttd:tipp_ids, now: now])
      }

      if ( tipps?.size() > 0 ) {
        def tipl_ids = tipls?.collect { it.id }
        Date now = new Date()

        TitleInstancePlatform.executeUpdate("update TitleInstancePlatform as t set t.status = :del, t.lastUpdated = :now where t.id IN (:ttd) and t.status != :del",[del: deleted_status, ttd:tipl_ids, now: now])
      }

      def events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant = :component",[component:this])

      events_to_delete.each {
        ComponentHistoryEventParticipant.executeUpdate("delete from ComponentHistoryEventParticipant as c where c.event = ?",[it])
        ComponentHistoryEvent.executeUpdate("delete from ComponentHistoryEvent as c where c.id = ?", [it.id])
      }
    }
  }

  def afterInsert() {

  }
}
