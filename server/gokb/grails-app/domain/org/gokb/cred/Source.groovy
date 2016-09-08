package org.gokb.cred

import javax.persistence.Transient

class Source extends KBComponent {

  String url
  String defaultAccessURL
  String explanationAtSource
  String contextualNotes
  // Org combo -- What organisation - aggregator -- responsibleParty
  String frequency
  String ruleset
  // Default method refdata - email web ftp other
  // Default data Format KBART,Prop
  RefdataValue defaultSupplyMethod
  RefdataValue defaultDataFormat
  Org responsibleParty


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
  }

  public String getNiceName() {
    return "Source";
  }

  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = Source.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

}
