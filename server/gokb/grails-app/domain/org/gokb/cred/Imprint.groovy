package org.gokb.cred

import javax.persistence.Transient

class Imprint extends KBComponent {

  static hasByCombo = [
    tiplTitle               : TitleInstance
  ]

  static mappedByCombo = [
    tiplTitle               : 'tipps',
  ]

  public getPersistentId() {
    "gokb:Impront:${id}"
  }

  static mapping = {
  }

  static constraints = {
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

  @Override
  public String getNiceName() {
	return "Imprint";
  }

}
