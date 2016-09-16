package org.gokb.cred

import javax.persistence.Transient

class Imprint extends KBComponent {

  static hasByCombo = [
    org               : Org
  ]

  static mappedByCombo = [
  ]

  public getPersistentId() {
    "gokb:Imprint:${id}"
  }

  static constraints = {
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

  public String getNiceName() {
    return "Imprint";
  }

  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = Imprint.findAllByNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
      }
    }

    result
  }

}
