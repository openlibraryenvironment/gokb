package org.gokb.cred

class Subject extends KBComponent {

  RefdataValue scheme
  String heading

  static mapping = {
    scheme column:'subj_scheme'
    heading column:'subj_heading'
  }

  static constraints = {
    scheme(nullable:true, blank:false)
    heading(nullable:true, blank:false)
  }

  static jsonMapping = [
    'ignore': [
      'lastUpdatedBy',
      'dateCreated',
      'editStatus',
      'name',
      'status',
      'lastUpdated',
      'description',
      'language',
      'source',
      '_links'
    ]
  ]

  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static List refdataFind(params) {
    List result = []
    List ql = Subject.findAllByNameIlike("${params.q}%", params)

    if ( ql ) {
      ql.each { t ->
        result.add([id: "${t.class.name}:${t.id}", text: "${t.name}"])
      }
    }

    result
  }


}
