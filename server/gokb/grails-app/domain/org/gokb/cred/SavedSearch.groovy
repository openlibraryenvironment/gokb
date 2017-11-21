package org.gokb.cred

import javax.persistence.Transient
import grails.plugin.gson.converters.GSON
import com.google.gson.*

class SavedSearch {

  String name
  User owner
  String searchDescriptor

  static constraints = {
    name blank: false, nullable:false
    owner blank: false, nullable: false
    searchDescriptor blank: false, nullable:false
  }

  static mapping = {
    id column: 'ss_id'
    name column: 'ss_name'
    owner column: 'ss_owner_fk'
    searchDescriptor column: 'ss_search_descriptor', type:'text'
  }

  @Transient
  def toParams() {
    Gson gson = new Gson();
    Map jsonObject = (Map) gson.fromJson(searchDescriptor, Object.class);
    return jsonObject
  }
}
