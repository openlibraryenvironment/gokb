package org.gokb.cred

class IdentifierNamespace {

  String value

  static mapping = {
    value column:'idns_value'
  }

  static constraints = {
	value (nullable:true, blank:false)
  }
}
