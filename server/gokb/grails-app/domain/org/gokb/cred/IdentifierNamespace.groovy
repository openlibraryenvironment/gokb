package org.gokb.cred

class IdentifierNamespace {

  String ns

  static mapping = {
    id column:'idns_id'
    ns column:'idns_ns'
  }

  static constraints = {
  }
}
