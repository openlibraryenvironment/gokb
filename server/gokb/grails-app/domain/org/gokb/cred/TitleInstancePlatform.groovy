package org.gokb.cred

import javax.persistence.Transient

class TitleInstancePlatform extends KBComponent {

  String url

  static hasByCombo = [
    tiplHostPlatform        : Platform,
    tiplTitle               : TitleInstance
  ]

  static mappedByCombo = [
    tiplHostPlatform        : 'hostedTipps',
    tiplTitle               : 'tipps',
  ]

  public getPersistentId() {
    "gokb:TIPL:${title?.id}:${hostPlatform?.id}"
  }

  static mapping = {
  }

  static constraints = {
    url (nullable:true, blank:true)
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

  @Override
  public String getNiceName() {
	return "TIPL";
  }
}
