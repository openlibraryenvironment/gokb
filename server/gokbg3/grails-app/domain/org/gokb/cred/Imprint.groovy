package org.gokb.cred

import javax.persistence.Transient

class Imprint extends KBComponent {

  static hasByCombo = [
    org               : Org
  ]

  static manyByCombo = [
    owners      : Org
  ]

  static mappedByCombo = [
    owners      : 'ownedImprints'
  ]

  public getPersistentId() {
    "gokb:Imprint:${id}"
  }

  static constraints = {
  }

//   @Transient
//   def getPermissableCombos() {
//     [
//     ]
//   }

  public Org getCurrentOwner() {
    def result = null;
    def owner_combos = getCombosByPropertyName('owners')
    def highest_end_date = null;

    owner_combos.each { Combo pc ->
      if ( ( pc.endDate == null ) ||
           ( highest_end_date == null) ||
           ( pc.endDate > highest_end_date ) ) {

        if (isComboReverse('owners')) {
          if ( pc.fromComponent.status?.value == 'Deleted' ) {
          }
          else {
            highest_end_date = pc.endDate
            result = pc.fromComponent
          }
        } else {
          if ( pc.toComponent.status?.value == 'Deleted' ) {
          }
          else {
            highest_end_date = pc.endDate
            result = pc.toComponent
          }
        }
      }
    }
    result = result ? Org.get(result.id) : null

    result
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
