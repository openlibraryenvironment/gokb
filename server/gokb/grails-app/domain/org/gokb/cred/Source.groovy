package org.gokb.cred

import javax.persistence.Transient

class Source extends KBComponent {

  String url

  static mapping = {
    url column:'source_url'
  }

  static constraints = {
    url(nullable:true, blank:true)
  }

  @Override
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
