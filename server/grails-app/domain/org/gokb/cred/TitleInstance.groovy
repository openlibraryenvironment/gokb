package org.gokb.cred

import org.grails.web.json.JSONObject

import gokbg3.DateFormatService

import java.time.LocalDateTime
import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import groovy.util.logging.*

@Slf4j
class TitleInstance extends KBComponent {

  // title is now NAME in the base component class...
  RefdataValue medium
  RefdataValue pureOA
  RefdataValue continuingSeries
  RefdataValue reasonRetired
  RefdataValue OAStatus
  Work work
  Date publishedFrom
  Date publishedTo
  String coverImage

  private static refdataDefaults = [
    "medium"  : "Journal",
    "pureOA"  : "No",
    "OAStatus": "Unknown"
  ]

  static mapping = {
    // From TitleInstance
    includes KBComponent.mapping
    medium column: 'medium_id', index: 'ti_medium_idx'
  }

  @Override
  String getLogEntityId() {
    "${this.class.name}:${id}"
  }

  public static final String restPath = "/titles"

  static jsonMapping = [
    'ignore'       : [
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
    'es'           : [
      'publisherUuid': "publisher.uuid",
      'publisherName': "publisher.name",
      'publisher'    : "publisher.id"
    ],
    'defaultEmbeds': [
      'ids',
      'variantNames',
      'publisher',
      'subjects'
    ]
  ]

  // This map is used to convey information about the title in general processing. The initial usecase is so that we can attach
  // information about how this specific title was located, for example, by class 1 identifier match, or some other method
  // title_status_properties.matched_by='Title In Title History' is used when the title was matched by a title string in the context of a title history
  @Transient
  public title_status_properties = [:]

  static hasByCombo = [
    issuer        : Org,
    translatedFrom: TitleInstance,
    absorbedBy    : TitleInstance,
    mergedWith    : TitleInstance,
    renamedTo     : TitleInstance,
    splitFrom     : TitleInstance,
    imprint       : Imprint
  ]

  static manyByCombo = [
    tipps    : TitleInstancePackagePlatform,
    publisher: Org,
    tipls    : TitleInstancePlatform
    //        ids     :  Identifier
  ]

  static constraints = {

    medium(nullable: true, blank: false)
    pureOA(nullable: true, blank: false)
    reasonRetired(nullable: true, blank: false)
    OAStatus(nullable: true, blank: false)
    publishedFrom(nullable: true, blank: false)
    publishedTo(validator: { val, obj ->
      if (obj.publishedFrom && val && (obj.hasChanged('publishedTo') || obj.hasChanged('publishedFrom')) && obj.publishedFrom > val) {
        return ['endDate.endPriorToStart']
      }
    })
    coverImage(nullable: true, blank: true)
    work(nullable: true, blank: false)
    name(validator: { val, obj ->
      if (!val && obj.hasChanged('name')) {
        return ['notNull']
      }
    })
  }

  def availableActions() {
    [[code: 'method::deleteSoft', label: 'Delete', perm: 'delete'],
     [code: 'setStatus::Current', label: 'Set Current', perm: 'admin'],
     [code: 'setStatus::Expected', label: 'Mark Expected'],
     [code: 'title::transfer', label: 'Title Transfer'],
     [code: 'title::change', label: 'Title Change'],
     [code: 'title::merge', label: 'Title Merge']
//       [code:'title::reconcile', label:'Title Reconcile']
    ]
  }

  public boolean addVariantTitle(String title, String locale = null) {

    // Check that the variant is not equal to the name of this title first.
    if (!title.equalsIgnoreCase(this.name)) {

      def normTitle = GOKbTextUtils.normaliseString(title)

      // Need to compare the existing variant names here. Rather than use the equals method,
      // we are going to compare certain attributes here.
      RefdataValue title_type = RefdataCategory.lookupOrCreate("KBComponentVariantName.VariantType", "Alternate Title")
      def locale_rd = null

      if (locale) {
        locale_rd = RefdataValue.findByOwnerAndValue(RefdataCategory.findByDesc("KBComponentVariantName.Locale"), (locale))
      }

      // Each of the variants...
      def existing = variantNames.find {
        KBComponentVariantName name = it
        return (name.getNormVariantName().equals(normTitle))
      }

      if (!existing) {
        new KBComponentVariantName([
          "variantType": (title_type),
          "owner"      : this,
          "locale"     : (locale_rd),
          "status"     : RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_CURRENT),
          "variantName": (title)
        ])
        return true
      }
      else {
        log.debug("Not adding variant title as it is the same as an existing variant.")
        return false
      }

    }
    else {
      log.debug("Not adding variant title as it is the same as the actual title.")
      return false
    }
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
      if ((pc.endDate == null) ||
        (highest_end_date == null) ||
        (pc.endDate > highest_end_date)) {

        if (isComboReverse('publisher')) {
          if (pc.fromComponent.status?.value == 'Deleted') {
          }
          else if (result && !highest_end_date) {
          }
          else {
            highest_end_date = pc.endDate
            result = pc.fromComponent
          }
        }
        else {
          if (pc.toComponent.status?.value == 'Deleted') {
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
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = [];
    def status_deleted = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    def status_filter = null

    if (params.filter1) {
      status_filter = RefdataCategory.lookup('KBComponent.Status', params.filter1)
    }

    def ql = null;
    // ql = TitleInstance.findAllByNameIlike("${params.q}%",params)
    // Return all titles where the title matches (Left anchor) OR there is an identifier for the title matching what is input
    ql = TitleInstance.executeQuery("select t from TitleInstance as t where t.status <> :sd and ( lower(t.name) like :lcqry or exists ( select c from Combo as c where c.fromComponent = t and c.toComponent in ( select id from Identifier as id where id.value like :qry ) ) )", [sd: status_deleted, lcqry: "${params.q?.toLowerCase()}%", qry: "${params.q}%"], [max: 20])

    if (ql) {
      ql.each { t ->
        if (!status_filter || t.status == status_filter) {
          result.add([id: "${t.class.name}:${t.id}", text: "${t.name}", status: "${t.status?.value}"])
        }
      }
    }

    result
  }

  @Transient
  static def oaiConfig = [
    id             : 'titles',
    textDescription: 'Title repository for GOKb',
    query          : " from TitleInstance as o ",
    statusFilter   : ['Expected'],
    pageSize       : 20,
    uriPath        : '/title'
  ]

  /**
   *  Render this title as OAI_dc
   */
  @Transient
  def toOaiDcXml(builder, attr) {
    builder.'dc'(attr) {
      'dc:title'(name)
    }
  }

  /**
   *  Render this title as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {

    try {
      def tipps = getTipps()
      Org theIssuer = getIssuer()
      def publisher_combos = getCombosByPropertyName('publisher')
      def people_combos = this.people ?: []

      def history = getTitleHistory()

      builder.'gokb'(attr) {
        builder.'title'(['id': (id), 'uuid': (uuid)]) {

          addCoreGOKbXmlFields(builder, attr)

          if (this.class.name == 'org.gokb.cred.BookInstance') {

            builder.'editionDifferentiator'(this.editionDifferentiator)
            builder.'editionStatement'(this.editionStatement)
            builder.'volumeNumber'(this.volumeNumber)
            builder.'dateFirstInPrint'(this.dateFirstInPrint ? DateFormatService.formatDate(this.dateFirstInPrint) : null)
            builder.'dateFirstOnline'(this.dateFirstOnline ? DateFormatService.formatDate(this.dateFirstOnline) : null)
            builder.'firstEditor'(this.firstEditor)
            builder.'firstAuthor'(this.firstAuthor)
          }

          builder.'imprint'(imprint?.name)
          builder.'medium'(medium?.value)
          builder.'type'(this.class.simpleName)
          builder.'OAStatus'(OAStatus?.value)
          builder.'continuingSeries'(continuingSeries?.value)
          builder.'publishedFrom'(this.publishedFrom ? DateFormatService.formatDate(this.publishedFrom) : null)
          builder.'publishedTo'(this.publishedTo ? DateFormatService.formatDate(this.publishedTo)  : null)

          builder.'publishers' {
            publisher_combos?.each { Combo pc ->
              def pub_org = null
              if (isComboReverse('publisher')) {
                pub_org = pc.fromComponent
              }
              else {
                pub_org = pc.toComponent
              }

              if (pub_org) {
                def org_ids = pub_org.activeIdInfo

                builder."publisher"(['id': pub_org.id, 'uuid': pub_org.uuid]) {
                  "name"(pub_org.name)
                  if (pc.startDate) {
                    "startDate"(pc.startDate ? DateFormatService.formatDate(pc.startDate) : null)
                  }
                  if (pc.endDate) {
                    "endDate"(pc.endDate ? DateFormatService.formatDate(pc.endDate) : null)
                  }
                  if (pc.status) {
                    "status"(pc.status.value)
                  }
                  builder."identifiers" {
                    org_ids?.each { org_id ->
                      builder.'identifier'(org_id)
                    }
                  }
                }
              }
            }
          }

          if (theIssuer) {
            builder."issuer"(['id': theIssuer.id, 'uuid': theIssuer.uuid]) {
              "name"(theIssuer.name)
            }
          }

          else {
            builder.history() {
              history.each { he ->
                builder.historyEvent(['id': he.id]) {
                  builder."date"(he.date ? DateFormatService.formatDate(he.date) : null)
                  he.from.each { hti ->
                    if (hti) {
                      builder."from" {
                        builder.'title'(hti.name)
                        builder.'uuid'(hti.uuid)
                        builder.'status'(hti.status.value)
                        builder.'internalId'(hti.id)
                        builder."identifiers" {
                          hti.activeIdInfo.each { tid ->
                            builder.'identifier'(tid)
                          }
                        }
                      }
                    }
                  }
                  he.to.each { hti ->
                    if (hti) {
                      "to" {
                        builder.'title'(hti.name)
                        builder.'uuid'(hti.uuid)
                        builder.'status'(hti.status.value)
                        builder.'internalId'(hti.id)
                        builder."identifiers" {
                          hti.activeIdInfo.each { tid ->
                            builder.'identifier'(tid)
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          builder.'TIPPs'(count: tipps?.size()) {
            tipps?.each { tipp ->
              builder.'TIPP'(['id': tipp.id, 'uuid': tipp.uuid]) {

                builder.'status'(tipp.status.value)

                def pkg = tipp.pkg
                builder.'package'(['id': pkg?.id, 'uuid': pkg?.uuid]) {
                  builder.'name'(pkg?.name)
                }

                def platform = tipp.hostPlatform
                builder.'platform'(['id': platform?.id, 'uuid': platform?.uuid]) {
                  builder.'name'(platform?.name)
                }

                builder.'accessStartDate'(tipp.accessStartDate ? DateFormatService.formatDate(tipp.accessStartDate) : null)
                builder.'accessEndDate'(tipp.accessEndDate ? DateFormatService.formatDate(tipp.accessEndDate) : null)

                builder.'subjectArea'(tipp.subjectArea?.trim())
                builder.'series'(tipp.series?.trim())

                if (tipp.prices && tipp.prices.size() > 0) {
                  builder.'prices'() {
                    tipp.prices.each { price ->
                      builder.'price' {
                        builder.'type'(price.priceType?.value)
                        builder.'amount'(price.price)
                        builder.'currency'(price.currency)
                        builder.'startDate'(price.startDate ? DateFormatService.formatDate(price.startDate) : null)
                        if (price.endDate) {
                          builder.'endDate'(price.endDate ? DateFormatService.formatDate(price.endDate) : null)
                        }
                      }
                    }
                  }
                }

                builder."identifiers" {
                  tipp.activeIdInfo.each { tid ->
                    builder.'identifier'(tid)
                  }
                }

                def cov_statements = tipp.coverageStatements
                if (cov_statements?.size() > 0) {
                  cov_statements.each { tcs ->
                    'coverage'(
                      startDate: (tcs.startDate ? DateFormatService.formatDate(tcs.startDate) : null),
                      startVolume: tcs.startVolume,
                      startIssue: tcs.startIssue,
                      endDate: (tcs.endDate ? DateFormatService.formatDate(tcs.endDate) : null),
                      endVolume: tcs.endVolume,
                      endIssue: tcs.endIssue,
                      coverageDepth: tcs.coverageDepth?.value ?: tipp.coverageDepth?.value,
                      coverageNote: tcs.coverageNote,
                      embargo: tcs.embargo
                    )
                  }
                }
                else {
                  builder.'coverage'(
                    startDate: (tipp.startDate ? DateFormatService.formatDate(tipp.startDate) : null),
                    startVolume: tipp.startVolume,
                    startIssue: tipp.startIssue,
                    endDate: (tipp.endDate ? DateFormatService.formatDate(tipp.endDate) : null),
                    endVolume: tipp.endVolume,
                    endIssue: tipp.endIssue,
                    coverageDepth: tipp.coverageDepth?.value,
                    coverageNote: tipp.coverageNote,
                    embargo: tipp.embargo)
                }
                if (tipp.url != null) {
                  'url'(tipp.url)
                }
              }
            }
          }
        }
      }
    }
    catch (Exception e) {
      log.error("problem creating record", e);
    }
  }

  @Transient
  def getTitleHistory() {
    def result = []
    def all_related_history_events = ComponentHistoryEvent.executeQuery('select eh from ComponentHistoryEvent as eh where exists ( select ehp from ComponentHistoryEventParticipant as ehp where ehp.participant = :ti and ehp.event = eh ) order by eh.eventDate', [ti: this])
    all_related_history_events.each { he ->
      def from_titles = he.participants.findAll { it.participantRole == 'in' };
      def to_titles = he.participants.findAll { it.participantRole == 'out' };

      def hint = "unknown"
      if ((from_titles?.size() == 1) && (to_titles?.size() == 1) && (from_titles[0].participant?.id != to_titles[0].participant?.id)) {
        hint = "Rename"
      }

      result.add(["id": (he.id), date: he.eventDate, from: from_titles.collect { it.participant }, to: to_titles.collect { it.participant }, hint: hint]);
    }
    return result;
  }

  def addTitlesToHistory(title, final_list, depth) {
    def result = false;

    if (title) {
      // Check to see whether this component has an id first. If not then return an empty set.
      if (title.id && title.id > 0) {
        if (final_list.contains(title)) {
          return;
        }
        else {
          // Find all history events relating to this title, and for each title related, add it to the final_list if it's not already in the list
          final_list.add(title)
          def all_related_history_events = ComponentHistoryEvent.executeQuery('select eh from ComponentHistoryEvent as eh where exists ( select ehp from ComponentHistoryEventParticipant as ehp where ehp.participant = :ti and ehp.event = eh ) order by eh.eventDate', [ti: title])
          all_related_history_events.each { the ->
            the.participants.each { p ->
              if (p.participant) {
                addTitlesToHistory(p.participant, final_list, depth + 1)
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
      addTitlesToHistory(this, il, 0)
      result.fh = ComponentHistoryEvent.executeQuery('select eh from ComponentHistoryEvent as eh where exists ( select ehp from ComponentHistoryEventParticipant as ehp where ehp.participant in (:titleList) and ehp.event = eh ) order by eh.eventDate asc', [titleList: il])
    }
    result;
  }

  def getPrecedingTitleId() {
    log.debug('getPrecedingTitleId')
    def preceeding_titles = []
    // Work through title history, see if there is a preceeding title...
    def ths = ComponentHistoryEvent.executeQuery('select eh from ComponentHistoryEvent as eh where exists ( select ehp from ComponentHistoryEventParticipant as ehp where ehp.participant = :ti and ehp.participantRole = :pr and ehp.event = eh ) order by eh.eventDate desc', [ti: this, pr: 'out'])
    if (ths.size() > 0) {
      ths[0].participants.each { p ->
        if (p.participantRole == 'in') {
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
        if (history_event_participant.participant.name == title) {
          result = history_event_participant
        }
      }
    }

    result
  }

  /**
   * titleDTO {*   title:'Title',
   *   publisher:'PubName',
   *   identifiers:[
   *{ type:'type', value:'value' },
   *{ type:'type', value:'value' },
   *   ],
   *   type:'Serial' or 'Monograph'
   *}*/
  @Transient
  static def validateDTO(JSONObject titleDTO, Locale locale) {
    def result = ['valid': true]
    def valErrors = [:]

    if (!titleDTO.name||titleDTO.name.trim()=='') {
      result.valid = false
      valErrors.put('name', [message: "missing"])
    }
    else {
      LocalDateTime startDate = GOKbTextUtils.completeDateString(titleDTO.publishedFrom)
      LocalDateTime endDate = GOKbTextUtils.completeDateString(titleDTO.publishedTo, false)

      if (titleDTO.publishedFrom && !startDate) {
        result.valid = false
        valErrors.put('publishedFrom', [message: "Unable to parse", baddata: titleDTO.remove('publishedFrom')])
      }

      if (titleDTO.publishedTo && !endDate) {
        result.valid = false
        valErrors.put('publishedTo', [message: "Unable to parse", baddata: titleDTO.remove('publishedTo')])
      }

      if (startDate && endDate && (endDate < startDate)) {
        valErrors.put('publishedTo', [message: "Publishing end date must not be prior to its start date!", baddata: titleDTO.publishedTo])
        // switch dates
        def tmp = titleDTO.publishedTo
        titleDTO.publishedTo = titleDTO.publishedFrom
        titleDTO.publishedFrom = tmp
      }

      String idJsonKey = 'ids'
      def ids_list = titleDTO[idJsonKey]

      if (!ids_list) {
        idJsonKey = 'identifiers'
        ids_list = titleDTO[idJsonKey]
      }

      def id_errors = Identifier.validateDTOs(ids_list, locale)

      if (id_errors.size() > 0) {
        valErrors.put(idJsonKey, id_errors)
        if (titleDTO[idJsonKey].size() == 0) {
          valErrors.put(idJsonKey, [message: 'no valid identifiers left'])
        }
      }
    }

    if (titleDTO.medium) {
      RefdataValue medRef = determineMediumRef(titleDTO)
      if (medRef) {
        titleDTO.medium = medRef.value
      }
      else {
        valErrors.put('medium', [message: "cannot parse", baddata: titleDTO.medium])
        titleDTO.remove(titleDTO.medium)
      }
    }

    def ti_language = titleDTO.language ? RefdataCategory.lookup('KBComponent.Language', titleDTO.language) : null
    if (ti_language){
      titleDTO.language = ti_language
    }

    if (valErrors.size() > 0) {
      if (result.errors) {
        result.errors.putAll(valErrors)
      }
      else {
        result.errors = valErrors
      }
    }
    result
  }

  static determineMediumRef(titleObj) {
    if (titleObj.medium instanceof String) {
      switch (titleObj.medium.toLowerCase()) {
        case "a & i database":
        case "abstract- & indexdatenbank":
          return RefdataCategory.lookup("TitleInstance.Medium", "A & I Database")
        case "audio":
          return RefdataCategory.lookup("TitleInstance.Medium", "Audio")
        case "database":
        case "fulltext database":
        case "Volltextdatenbank":
          return RefdataCategory.lookup("TitleInstance.Medium", "Database")
        case "dataset":
        case "datenbestand":
          return RefdataCategory.lookup("TitleInstance.Medium", "Dataset")
        case "film":
          return RefdataCategory.lookup("TitleInstance.Medium", "Film")
        case "image":
        case "bild":
          return RefdataCategory.lookup("TitleInstance.Medium", "Image")
        case "journal":
        case "zeitschrift":
          return RefdataCategory.lookup("TitleInstance.Medium", "Journal")
        case "book":
        case "buch":
          return RefdataCategory.lookup("TitleInstance.Medium", "Book")
        case "published score":
        case "musiknoten":
          return RefdataCategory.lookup("TitleInstance.Medium", "Published Score")
        case "article":
        case "artikel":
          return RefdataCategory.lookup("TitleInstance.Medium", "Article")
        case "software":
          return RefdataCategory.lookup("TitleInstance.Medium", "Software")
        case "statistics":
        case "statistiken":
          return RefdataCategory.lookup("TitleInstance.Medium", "Statistics")
        case "market data":
        case "marktdaten":
          return RefdataCategory.lookup("TitleInstance.Medium", "Market Data")
        case "standards":
        case "normen":
          return RefdataCategory.lookup("TitleInstance.Medium", "Standards")
        case "biography":
        case "biografie":
          return RefdataCategory.lookup("TitleInstance.Medium", "Biography")
        case "legal text":
        case "gesetzestext/urteil":
          return RefdataCategory.lookup("TitleInstance.Medium", "Legal Text")
        case "cartography":
        case "kartenwerk":
          return RefdataCategory.lookup("TitleInstance.Medium", "Cartography")
        case "miscellaneous":
        case "sonstiges":
          return RefdataCategory.lookup("TitleInstance.Medium", "Miscellaneous")
        case "other":
          return RefdataCategory.lookup("TitleInstance.Medium", "Other")
        default:
          return null
      }
    }
    else if (titleObj.medium instanceof Integer) {
      def rdv = RefdataValue.get(titleObj.medium)

      if (rdv && rdv.owner == RefdataCategory.findByLabel("TitleInstance.Medium")) {
        return rdv
      }
    }
    else if (titleObj.medium instanceof Map && titleObj.medium.id) {
      def rdv = RefdataValue.get(titleObj.medium.id)

      if (rdv && rdv.owner == RefdataCategory.findByLabel("TitleInstance.Medium")) {
        return rdv
      }
    }

    return null
  }

  // This is called by the titleLookupService::remapTitleInstance method but NOTE:: this is done
  // primarily so that the cpu-work and object creation of the work instance is done outside the
  // context of the primary hibernate session.
  def remapWork() {
    log.debug('remapWork');
    // BKM:TITLE + then FIRSTAUTHOR if duplicates found

    if ((normname) &&
      (normname.length() > 0) &&
      (!normname.startsWith('unknown title'))) {
      // book bucket (Work) hashes are based on the normalised name.
      def h = GOKbTextUtils.generateComponentHash([normname]);

      log.debug("Searching for bucket matches for ${h}");
      def bucketMatches = Work.executeQuery('select w from Work as w where w.bucketHash = :h', [h: h]);

      switch (bucketMatches.size()) {
        case 0:
          log.debug("No matches - create work");
          def w = new Work(name: name, bucketHash: h).save(flush: true, failOnError: true)
          this.work = w
          this.save(flush: true, failOnError: true)
          break;
        case 1:
          log.debug("Good enough unique match on bucketHash");
          this.work = bucketMatches[0]
          this.save(flush: true, failOnError: true)
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
      if (!KBComponentVariantName.findByOwnerAndNormVariantName(this, variant_normname)) {
        result = new KBComponentVariantName(owner: this, variantName: name).save(flush: true)
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
    def review_closed = RefdataCategory.lookup('ReviewRequest.Status', 'Closed')

    if (this.isDirty('status') && this.status == deleted_status) {
      // Delete all TIPP combos and TIPLs
      def tipps = getTipps()
      def tipls = getTipls()

      if (tipps?.size() > 0) {
        def tipp_ids = tipps?.collect { it.id }

        Combo.executeUpdate("delete from Combo as c where c.fromComponent = :ti and c.toComponent.id IN (:ttd)", [ti: this, ttd: tipp_ids])
      }

      if (tipls?.size() > 0) {
        def tipl_ids = tipls?.collect { it.id }
        Date now = new Date()

        TitleInstancePlatform.executeUpdate("update TitleInstancePlatform as t set t.status = :del, t.lastUpdated = :now where t.id IN (:ttd) and t.status != :del", [del: deleted_status, ttd: tipl_ids, now: now])
      }

      def events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant = :component", [component: this])

      events_to_delete.each {
        ComponentHistoryEventParticipant.executeUpdate("delete from ComponentHistoryEventParticipant as c where c.event = :event", [event: it])
        ComponentHistoryEvent.executeUpdate("delete from ComponentHistoryEvent as c where c.id = :eid", [eid: it.id])
      }

      this.reviewRequests*.status = review_closed
    }

    if (this.isDirty('name')) {
      this.shortcode = generateShortcode(this.name)
      generateNormname()
      generateComponentHash()
    }
  }

  def afterInsert() {

  }

  public static String determineTitleClass(String pubTypeName) {
    if ( pubTypeName) {
      switch ( pubTypeName) {
        case "serial":
        case "Serial":
        case "Journal":
        case "journal":
          return "org.gokb.cred.JournalInstance"
          break;
        case "monograph":
        case "Monograph":
        case "Book":
        case "book":
          return "org.gokb.cred.BookInstance"
          break;
        case "Database":
        case "database":
          return "org.gokb.cred.DatabaseInstance"
          break;
        case "Other":
        case "other":
          return "org.gokb.cred.OtherInstance"
          break;
        default:
          return null
          break;
      }
    }
    else {
      return null
    }
  }
}
