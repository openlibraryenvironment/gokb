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
}
