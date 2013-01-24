package org.gokb.cred

import javax.persistence.Transient

class TitleInstance extends KBComponent {

  // title is now NAME in the base component class... String title
  String impId
  RefdataValue status
  RefdataValue type
  Date dateCreated
  Date lastUpdated

  static mappedBy = [tipps: 'title']
  static hasMany = [tipps: TitleInstancePackagePlatform]


  static mapping = {
         id column:'ti_id'
    version column:'ti_version'
      impId column:'ti_imp_id', index:'ti_imp_id_idx'
     status column:'ti_status_rv_fk'
       type column:'ti_type_rv_fk'
      tipps sort:'startDate', order: 'asc'
  }

  static constraints = {
    status(nullable:true, blank:false);
    type(nullable:true, blank:false);
  }

  @Transient
  Org getPublisher() {
    def result = null;
    orgs.each { o ->
      if ( o.roleType.value == 'Publisher' ) {
        result = o.org
      }
    }
    result
  }
}
