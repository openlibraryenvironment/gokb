package org.gokb.cred

class AdditionalPropertyDefinition {

  String propertyName

  static mapping = {
              id column:'apd_id'
    propertyName column:'apd_prop_name', index:'apd_prop_name_idx'
  }

  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = AdditionalPropertyDefinition.findAllByPropertyNameIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.propertyName}"])
      }
    }

    result
  }

}
