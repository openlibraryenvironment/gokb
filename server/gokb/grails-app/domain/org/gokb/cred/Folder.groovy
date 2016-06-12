package org.gokb.cred

class Folder extends FolderEntry {

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

}
