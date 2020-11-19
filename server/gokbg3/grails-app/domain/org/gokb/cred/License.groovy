package org.gokb.cred

import javax.persistence.Transient


class License extends KBComponent {
  String     url
  String    file

  // Known as license_type in docs
  RefdataValue  type
  String    summaryStatement
  
  private static refdataDefaults = [
    "type"     : "Template"
  ]
  
  static hasByCombo = [
    licensor     : Org,
    licensee    : Org,
    'previous'    : License,
    successor    : License,
    model      : License
  ]
  
  static manyByCombo = [
    modelledLicenses  : License,
    curatoryGroups      : CuratoryGroup
  ]
  
  static mappedByCombo = [
    successor      : 'previous',
    modelledLicenses  : 'model'
  ]
  
  static constraints = {
    url     nullable:true, blank:true
    file     nullable:true, blank:true
    type     nullable:true, blank:true
    summaryStatement nullable:true, blank:true
  }
  
  static mapping = {
    includes KBComponent.mapping
                 url column:'license_url'
                file column:'license_document'
                type column:'license_type_fk_rd'
    summaryStatement column:'summary_txt', type: 'text'
  }

  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = License.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
      result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

  static def oaiConfig = [
    id:'licenses',
    textDescription:'Office repository for GOKb',
    query:" from License as o ",
    statusFilter: ["Deleted"],
    pageSize:3
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
   *  Render this license as GoKBXML
   */
  @Transient
  def toGoKBXml(builder, attr) {
    builder.'gokb' (attr) {
      addCoreGOKbXmlFields(builder, attr)

      builder.'url' (url)
      builder.'file' (file)
      if ( type ) {
        builder.'type' (type.value)
      }
      builder.'summaryStatement' {
        builder.mkp.yieldUnescaped "<![CDATA[${summaryStatement}]]>"
      }

      if ( licensor ) {
        builder.'licensor' (licensor.name)
      }

      if ( licensee ) {
        builder.'licensee' (licensee.name)
      }

      if ( previous ) {
        builder.'previous' (previous.name)
      }

      if ( successor ) {
        builder.'successor' (successor.name)
      }

      if ( model ) {
        builder.'model' (model.name)
      }

      builder.'curatoryGroups' {
        curatoryGroups.each { cg ->
          builder.group {
            builder.name(cg.name)
          }
        }
      }
    }
  }
}
