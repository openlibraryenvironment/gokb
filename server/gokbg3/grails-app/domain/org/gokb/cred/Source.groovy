package org.gokb.cred

import javax.persistence.Transient
import java.util.regex.Matcher
import java.util.regex.Pattern

class Source extends KBComponent {

  String url
  String defaultAccessURL
  String explanationAtSource
  String contextualNotes
  // Org combo -- What organisation - aggregator -- responsibleParty
  Boolean automaticUpdates = false
  String frequency
  String ruleset
  // Default method refdata - email web ftp other
  // Default data Format KBART,Prop
  RefdataValue defaultSupplyMethod
  RefdataValue defaultDataFormat
  IdentifierNamespace targetNamespace
  Date lastRun
  Boolean zdbMatch = false
  Boolean ezbMatch = false
  Org responsibleParty

  static manyByCombo = [
    curatoryGroups: CuratoryGroup
  ]

  static mapping = {
    includes KBComponent.mapping
    url column:'source_url'
    ruleset column:'source_ruleset', type:'text'
  }

  static constraints = {
    url(nullable:true, blank:true)
    defaultAccessURL(nullable:true, blank:true)
    explanationAtSource(nullable:true, blank:true)
    contextualNotes(nullable:true, blank:true)
    frequency(nullable:true, blank:true, default: "1D")
    defaultSupplyMethod(nullable:true, blank:true)
    defaultDataFormat(nullable:true, blank:true)
    responsibleParty(nullable:true, blank:true)
    ruleset(nullable:true, blank:true)
    targetNamespace(nullable:true, blank:true)
    lastRun(nullable:true,default: null)
    ezbMatch(nullable:true, default: false)
    zdbMatch(nullable:true,default: false)
    automaticUpdates(nullable: true,default: false)
    name(validator: { val, obj ->
      if (obj.hasChanged('name')) {
        if (val && val.trim()) {
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def dupes = Source.findAllByNameIlikeAndStatusNotEqual(val, status_deleted);

          if (dupes.size() > 0 && dupes.any {it != obj}) {
            return ['notUnique']
          }
        }
        else {
          return ['notNull']
        }
      }
    })
  }

  public static final String restPath = "/sources"

  static def refdataFind(params) {
    def result = [];
    def status_deleted = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    def status_filter = null

    if(params.filter1) {
      status_filter = RefdataCategory.lookup('KBComponent.Status', params.filter1)
    }

    def ql = null;
    ql = Source.findAllByNameIlikeAndStatusNotEqual("${params.q}%", status_deleted, params)

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
    id:'sources',
    textDescription:'Source repository for GOKb',
    query:" from Source as o where o.status.value != 'Deleted'",
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
    builder.'gokb' (attr) {

      addCoreGOKbXmlFields(builder, attr)

      builder.'url' (url)
      builder.'defaultAccessURL' (defaultAccessURL)
      builder.'explanationAtSource' (explanationAtSource)
      builder.'contextualNotes' (contextualNotes)
      builder.'frequency' (frequency)
      builder.'ruleset' (ruleset)
      builder.'automaticUpdates' (automaticUpdates)
      builder.'ezbMatch' (ezbMatch)
      builder.'zdbMatch' (zdbMatch)
      builder.'lastRun' (lastRun)
      if ( targetNamespace ) {
        builder.'targetNamespace'('namespaceName': targetNamespace.name, 'value': targetNamespace.value, 'id': targetNamespace.id)
      }
      if ( defaultSupplyMethod ) {
        builder.'defaultSupplyMethod' ( defaultSupplyMethod.value )
      }
      if ( defaultDataFormat ) {
        builder.'defaultDataFormat' ( defaultDataFormat.value )
      }
      if ( responsibleParty ) {
        builder.'responsibleParty' {
          builder.name(responsibleParty.name)
        }
      }
    }
  }

  public boolean needsUpdate() {
    Source src = this
    if (src.lastRun == null) {
      return true;
    }
    if (src.frequency != null) {
      Pattern pattern = Pattern.compile("\\s*(\\d*)\\s*([a-zA-Z]*)\\s*");
      Matcher matcher = pattern.matcher(src.frequency);
      int days
      if (matcher.find()) {
        def length = [
          D: 1,
          T: 1,
          W: 7,
          M: 30,
          Q: 91,
          H: 182,
          J: 365,
          Y: 365]
        def interval = matcher.group(2)
        def number = matcher.group(1)
        days = Integer.parseInt(number) * length[interval.toUpperCase()]

        Date today = new Date()
        Date due = src.lastRun.plus(days)
        if (due.before(today)) {
          return true
        }
      }
    }
    return false
  }
}
