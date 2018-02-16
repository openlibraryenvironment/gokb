package org.gokb.cred

class Folder extends FolderEntry {

  transient static String AVAILABILITY_QUERY = """
select count(t) 
from TitleInstance as t, 
     KBComponentFolderEntry as fe 
where fe.folder = :folder 
  and t.id = fe.linkedComponent.id
  and exists ( select c from Combo as c where c.fromComponent = t and c.type.value = 'TitleInstance.Tipps' )
"""

  String name
  Party owner

  static hasMany = [
    contents:FolderEntry
  ]

  static mappedBy = [
    contents:'folder'
  ]

  static constraints = {
    name blank: false, nullable: true
  }


  static mapping = {
          id column:'fldr_id'
        user column:'fldr_owner_id'
        name column:'fldr_name'
    contents cascade: 'all-delete-orphan'
  }

  transient getAvailability() {
    def result = [:]
 
    result.total = contents.size();

    result.available = TitleInstance.executeQuery(AVAILABILITY_QUERY,[folder:this])
 
    return result   
  }
}
