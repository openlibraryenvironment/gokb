package org.gokb.cred

class ComponentIdentifier {
  static final String RD_STATUS = 'ComponentIdentifier.Status'
  static final String STATUS_ACTIVE = "Active"
  static final String STATUS_SUPERSEDED = "Superseded"
  static final String STATUS_DELETED = "Deleted"
  static final String STATUS_EXPIRED = "Expired"


  KBComponent component
  Identifier identifier

  RefdataValue status

  Date startDate
  Date endDate

  Date dateCreated
  Date lastUpdated

  static mapping = {
    component column:'ci_comp_fk', index: 'ci_cmp_idx,ci_full_idx'
    identifier column:'ci_ident_fk', index: 'ci_ident_idx,ci_full_idx'
    status column:'ci_status_rv_fk', index: 'ci_ident_idx,ci_cmp_idx,ci_full_idx'
    dateCreated column:'ci_date_created', index: 'ci_created_idx'
    lastUpdated column:'ci_last_updated'
  }

  static constraints = {
    component(nullable:false, blank:false)
    identifier(nullable:false, blank:false)
    status(nullable:true, blank:false)
  }

  def beforeInsert() {
    RefdataValue status_active = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_ACTIVE)

    if (this.status == null) {
      this.status = status_active
    }
  }
}
