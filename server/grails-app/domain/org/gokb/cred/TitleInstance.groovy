package org.gokb.cred

import org.grails.web.json.JSONObject

import gokbg3.DateFormatService

import java.time.LocalDateTime
import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import groovy.util.logging.*

@Slf4j
class TitleInstance extends KBComponent {

  RefdataValue medium
  RefdataValue pureOA
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

  static hasMany = [
    publisherLinks: TitlePublisher,
    tipps: TitleInstancePackagePlatform,
    tipls: TitleInstancePlatform
  ]

  static mappedBy = [
    publisherLinks: 'title'
    tipps: 'title',
    tipls: 'title',
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

  @Override
  String getLogEntityId() {
    "${this.class.name}:${id}"
  }

  public static final String restPath = "/titles"

  static Map jsonMapping = [
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

  public Map availableActions() {
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
    boolean result = false

    // Check that the variant is not equal to the name of this title first.
    if (!title.equalsIgnoreCase(this.name)) {

      String normTitle = GOKbTextUtils.normaliseString(title)

      // Need to compare the existing variant names here. Rather than use the equals method,
      // we are going to compare certain attributes here.
      RefdataValue title_type = RefdataCategory.lookupOrCreate("KBComponentVariantName.VariantType", "Alternate Title")
      RefdataValue locale_rd = null

      if (locale) {
        locale_rd = RefdataValue.findByOwnerAndValue(RefdataCategory.findByDesc("KBComponentVariantName.Locale"), (locale))
      }

      // Each of the variants...
      List existing = variantNames.find { kv ->
        kv.normname == normTitle
      }

      if (!existing) {
        new KBComponentVariantName([
          "variantType": (title_type),
          "owner"      : this,
          "locale"     : (locale_rd),
          "status"     : RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_CURRENT),
          "variantName": (title)
        ])
        result = true
      }
      else {
        log.debug("Not adding variant title as it is the same as an existing variant.")
      }

    }
    else {
      log.debug("Not adding variant title as it is the same as the actual title.")
    }

    result
  }

  @Override
  public String getNiceName() {
    return "Title";
  }

  public Org getCurrentPublisher() {
    Org result = null
    Date highest_end_date

    publisherLinks.each { TitlePublisher pc ->
      if ((pc.endDate == null) ||
        (highest_end_date == null) ||
        (pc.endDate > highest_end_date)) {

        if (result && !highest_end_date) {
        }
        else {
          highest_end_date = pc.endDate
          result = pc.publisher
        }
      }
    }
    result = result ? Org.get(result.id) : null

    result
  }

  public List getPublisher() {
    List result = []

    publisherLinks.each {
      result << it.publisher
    }

    return result
  }


  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static List refdataFind(params) {
    List result = [];
    RefdataValue status_deleted = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    RefdataValue status_filter = null

    if (params.filter1) {
      status_filter = RefdataCategory.lookup('KBComponent.Status', params.filter1)
    }

    // ql = TitleInstance.findAllByNameIlike("${params.q}%",params)
    // Return all titles where the title matches (Left anchor) OR there is an identifier for the title matching what is input
    List ql = TitleInstance.executeQuery('''select t from TitleInstance as t
                                            where t.status <> :sd
                                            and (
                                              lower(t.name) like :lcqry
                                              or exists (
                                                select c from ComponentIdentifier as c
                                                where c.component = t
                                                and c.identifier in (
                                                  select id from Identifier as id
                                                  where id.value like :qry
                                                )
                                              )
                                            )''',
                                          [sd: status_deleted, lcqry: "${params.q?.toLowerCase()}%", qry: "${params.q}%"], [max: 20])

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
  static Map oaiConfig = [
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
  public void toOaiDcXml(builder, attr) {
    builder.'dc'(attr) {
      'dc:title'(name)
    }
  }

  /**
   *  Render this title as GoKBXML
   */
  @Transient
  public void toGoKBXml(builder, attr) {

    try {
      List publisher_links = getPublisherLinks()
      List history = getTitleHistory()
      List tipps = getTipps()

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
            publisher_links?.each { TitlePublisher pc ->
              List pub_info = Org.executeQuery("select id, uuid, name from Org where id = :fc", [fc: pc.publisher.id])

              if (pub_info) {
                builder."publisher"(['id': pub_info[0], 'uuid': pub_info[1]]) {
                  "name"(pub_info[2])

                  if (pc.startDate) {
                    "startDate"(pc.startDate ? DateFormatService.formatDate(pc.startDate) : null)
                  }

                  if (pc.endDate) {
                    "endDate"(pc.endDate ? DateFormatService.formatDate(pc.endDate) : null)
                  }

                  if (pc.status) {
                    "status"(pc.status.value)
                  }
                }
              }
            }
          }

          if (this.class.name == 'org.gokb.cred.JournalInstance') {
            builder.'history' {
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
                builder.'name'(tipp.name)
                builder.'status'(tipp.status.value)

                List pkg_info = Package.executeQuery("select id, uuid, name from Package where id = :pid", [pid: tipp.pkg.id])

                builder.'package'(['id': pkg_info[0], 'uuid': pkg_info[1]]) {
                  builder.'name'(pkg_info[2])
                }

                List plt_info = Platform.executeQuery("select id, uuid, name from Platform where id = :pid", [pid: tipp.hostPlatform.id])

                builder.'platform'(['id': plt_info[0], 'uuid': plt_info[1]]) {
                  builder.'name'(plt_info[2])
                }

                builder.'url'(tipp.url)

                builder.'accessStartDate'(tipp.accessStartDate ? DateFormatService.formatDate(tipp.accessStartDate) : null)
                builder.'accessEndDate'(tipp.accessEndDate ? DateFormatService.formatDate(tipp.accessEndDate) : null)

                builder."identifiers" {
                  List tipp_id_list = tipp.activeIdInfo

                  tipp_id_list.each { tid ->
                    builder.'identifier'(tid)
                  }
                }

                List cov_statements = tipp.coverageStatements

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
                  builder.'coverage'()
                }
              }
            }
          }
        }
      }
    }
    catch (Exception e) {
      log.error("problem creating record", e)
    }
  }

  public List getTitleHistory() {
    List result = []
    List all_related_history_events = ComponentHistoryEvent.executeQuery('''select eh from ComponentHistoryEvent as eh
                                                                            where exists (
                                                                              select ehp from ComponentHistoryEventParticipant as ehp
                                                                              where ehp.participant = :ti
                                                                              and ehp.event = eh
                                                                            )
                                                                            order by eh.eventDate''',
                                                                          [ti: this])

    all_related_history_events.each { he ->
      List from_titles = he.participants.findAll { it.participantRole == 'in' }
      List to_titles = he.participants.findAll { it.participantRole == 'out' }

      String hint = "unknown"

      if ((from_titles?.size() == 1) && (to_titles?.size() == 1) && (from_titles[0].participant?.id != to_titles[0].participant?.id)) {
        hint = "Rename"
      }

      result.add([
        "id": (he.id),
        date: he.eventDate,
        from: from_titles.collect { it.participant },
        to: to_titles.collect { it.participant },
        hint: hint
      ])
    }

    return result
  }

  public void addTitlesToHistory(title, final_list, depth) {
    Boolean result = false

    if (title) {
      // Check to see whether this component has an id first. If not then return an empty set.
      if (title.id && title.id > 0) {
        if (final_list.contains(title)) {
          // already in list
        }
        else {
          // Find all history events relating to this title, and for each title related, add it to the final_list if it's not already in the list
          final_list.add(title)

          List all_related_history_events = ComponentHistoryEvent.executeQuery('''select eh from ComponentHistoryEvent as eh
                                                                                  where exists (
                                                                                    select ehp from ComponentHistoryEventParticipant as ehp
                                                                                    where ehp.participant = :ti
                                                                                    and ehp.event = eh
                                                                                  )
                                                                                  order by eh.eventDate''',
                                                                                [ti: title])

          all_related_history_events.each { the ->
            the.participants.each { p ->
              if (p.participant) {
                addTitlesToHistory(p.participant, final_list, depth + 1)
              }
              else {
                log.error("Title history participant was null - HistoryEvent==${the}")
              }
            }
          }
        }
      }
    }
    else {
      log.error("Attempt to addTitlesToHistory for a null title")
    }
  }

  public Map getFullTitleHistory() {
    Map result = [:]

    // Check to see whether this component has an id first. If not then return an empty set.
    if (id && id > 0) {
      List il = []
      addTitlesToHistory(this, il, 0)
      result.fh = ComponentHistoryEvent.executeQuery('''select eh from ComponentHistoryEvent as eh
                                                        where exists (
                                                          select ehp from ComponentHistoryEventParticipant as ehp
                                                          where ehp.participant in (:titleList)
                                                          and ehp.event = eh )
                                                          order by eh.eventDate asc''',
                                                    [titleList: il])
    }
    result;
  }

  public List getPrecedingTitleId() {
    log.debug('getPrecedingTitleId')
    List preceeding_titles = []
    // Work through title history, see if there is a preceeding title...
    List ths = ComponentHistoryEvent.executeQuery('''select eh from ComponentHistoryEvent as eh
                                                    where exists (
                                                      select ehp from ComponentHistoryEventParticipant as ehp
                                                      where ehp.participant = :ti
                                                      and ehp.participantRole = :pr
                                                      and ehp.event = eh
                                                    )
                                                    order by eh.eventDate desc''',
                                                [ti: this, pr: 'out'])

    if (ths.size() > 0) {
      ths[0].participants.each { p ->
        if (p.participantRole == 'in') {
          preceeding_titles.add(p.participant.id)
        }
      }
    }

    return preceeding_titles.join(', ')
  }

  public ComponentHistoryEventParticipant findInTitleHistory(title) {
    ComponentHistoryEventParticipant result

    Map full_th = getFullTitleHistory()

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
  public static Map validateDTO(JSONObject titleDTO, Locale locale) {
    Map result = ['valid': true]
    Map valErrors = [:]

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
        result.valid = false
      }

      String idJsonKey = 'ids'
      List ids_list = titleDTO[idJsonKey] ?: []

      if (!ids_list) {
        idJsonKey = 'identifiers'
        ids_list = titleDTO[idJsonKey]
      }

      List id_errors = Identifier.validateDTOs(ids_list, locale)

      if (id_errors.size() > 0) {
        valErrors.put(idJsonKey, id_errors)

        if (titleDTO[idJsonKey].size() == 0) {
          valErrors.put(idJsonKey, [message: 'no valid identifiers left'])
        }
      }
    }

    if (titleDTO.medium) {
      RefdataValue medRef = determineMediumRef(titleDTO)

      if (!medRef) {
        valErrors.put('medium', [message: "cannot parse", baddata: titleDTO.medium])
      }
    }

    if (titleDTO.language){
      RefdataValue ti_language = titleDTO.language ? RefdataCategory.lookup('KBComponent.Language', titleDTO.language) : null

      if (!ti_language) {
        valErrors.put('language', [message: "cannot parse", baddata: titleDTO.language])
      }
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

  public static RefdataValue determineMediumRef(titleObj) {
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
      RefdataValue rdv = RefdataValue.get(titleObj.medium)

      if (rdv && rdv.owner == RefdataCategory.findByLabel("TitleInstance.Medium")) {
        return rdv
      }
    }
    else if (titleObj.medium instanceof Map && titleObj.medium.id) {
      RefdataValue rdv = RefdataValue.get(titleObj.medium.id)

      if (rdv && rdv.owner == RefdataCategory.findByLabel("TitleInstance.Medium")) {
        return rdv
      }
    }

    return null
  }

  // This is called by the titleLookupService::remapTitleInstance method but NOTE:: this is done
  // primarily so that the cpu-work and object creation of the work instance is done outside the
  // context of the primary hibernate session.
  public void remapWork() {
    log.debug('remapWork');
    // BKM:TITLE + then FIRSTAUTHOR if duplicates found

    if ((normname) &&
      (normname.length() > 0) &&
      (!normname.startsWith('unknown title'))) {
      // book bucket (Work) hashes are based on the normalised name.
      String h = GOKbTextUtils.generateComponentHash([normname])

      log.debug("Searching for bucket matches for ${h}");
      List bucketMatches = Work.executeQuery('select w from Work as w where w.bucketHash = :h', [h: h])

      switch (bucketMatches.size()) {
        case 0:
          log.debug("No matches - create work")
          Work w = new Work(name: name, bucketHash: h).save(flush: true, failOnError: true)
          this.work = w
          this.save(flush: true, failOnError: true)
          break;
        case 1:
          log.debug("Good enough unique match on bucketHash")
          this.work = bucketMatches[0]
          this.save(flush: true, failOnError: true)
          break;
        default:
          log.debug("Mached multiple works - use discriminator properties")
          break;
      }
    }
  }

  @Override
  public KBComponentVariantName ensureVariantName(String name) {
    KBComponentVariantName result

    if (name.trim().size() != 0) {

      // Variant names use different normalisation method.
      String variant_normname = GOKbTextUtils.normaliseString(name)

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
    RefdataValue deleted_status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    RefdataValue review_closed = RefdataCategory.lookup('ReviewRequest.Status', 'Closed')

    if (this.isDirty('status') && this.status == deleted_status) {
      List tipps = getTipps()
      List tipls = getTipls()

      if (tipps?.size() > 0) {
        tipps.each {
          it.title = null
        }
      }

      if (tipls?.size() > 0) {
        List tipl_ids = tipls?.collect { it.id }
        Date now = new Date()

        TitleInstancePlatform.executeUpdate("update TitleInstancePlatform as t set t.status = :del, t.lastUpdated = :now where t.id IN (:ttd) and t.status != :del", [del: deleted_status, ttd: tipl_ids, now: now])
      }

      List events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant = :component", [component: this])

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
