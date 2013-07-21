package org.gokb.cred

class RefdataValue {

  String value
  String icon

  static belongsTo = [
    owner:RefdataCategory
  ]

  static mapping = {
         id column:'rdv_id'
    version column:'rdv_version'
      owner column:'rdv_owner', index:'rdv_entry_idx'
      value column:'rdv_value', index:'rdv_entry_idx'
       icon column:'rdv_icon'
  }

  static constraints = {
    icon(nullable:true)
  }
  
  @Override
  public String toString() {
	return "${value}"
  }

  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = RefdataValue.findAllByValueIlikeOrDescriptionIlike("%${params.q}%","%${params.q}%",params)

    if ( ql ) {
      ql.each { id ->
        result.add([id:"${id.class.name}:${id.id}",text:"${id.value} : ${id.description?:'No Description'}"])
      }
    }

    result
  }

//  def availableActions() {
//    [ [ code:'object::delete' , label: 'Delete' ] ]
//  }
}
