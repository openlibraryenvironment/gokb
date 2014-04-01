package org.gokb.cred

import javax.persistence.Transient

class Imprint extends KBComponent {

  static hasByCombo = [
    org               : Org
  ]

  static mappedByCombo = [
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
