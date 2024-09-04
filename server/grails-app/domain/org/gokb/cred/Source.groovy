package org.gokb.cred

import groovy.time.TimeCategory

import javax.persistence.Transient
import java.util.regex.Matcher
import java.util.regex.Pattern

class Source extends KBComponent {

  String url
  String defaultAccessURL
  String explanationAtSource
  String contextualNotes
  Boolean automaticUpdates = false
  Boolean skipInvalid = false
  RefdataValue frequency
  String ruleset
  RefdataValue defaultSupplyMethod
  RefdataValue defaultDataFormat
  IdentifierNamespace targetNamespace
  Date lastRun
  Boolean ezbMatch = false
  Org responsibleParty
  BulkImportListConfig bulkConfig
  String type

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
    frequency(nullable:true, blank:true)
    defaultSupplyMethod(nullable:true, blank:true)
    defaultDataFormat(nullable:true, blank:true)
    responsibleParty(nullable:true, blank:true)
    ruleset(nullable:true, blank:true)
    targetNamespace(nullable:true, blank:true)
    lastRun(nullable:true, default: null)
    ezbMatch(nullable:true, default: false)
    automaticUpdates(nullable: true, default: false)
    skipInvalid(nullable: true, default: false)
    bulkConfig(nullable: true, blank: false)
    type(nullable: true, blank: true)
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


  boolean needsUpdate() {
    boolean result = false

    if (lastRun == null) {
      result = true
    }
    else if (frequency) {
      use(TimeCategory) {
        if (lastRun + intervals.get(frequency.value).days < new Date()) {
          result = true
        }
      }
    }
    result
  }

  def intervals = [
      "Daily"       : 1,
      "Weekly"      : 7,
      "Monthly"     : 30,
      "Quarterly"   : 91,
      "Yearly"      : 365,
  ]

}
