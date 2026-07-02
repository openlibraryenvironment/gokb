package org.gokb.cred

class TitlePublisher {
  static final String RD_STATUS = 'TitlePublisher.Status'
  static final String STATUS_ACTIVE = "Active"
  static final String STATUS_SUPERSEDED = "Superseded"
  static final String STATUS_DELETED = "Deleted"
  static final String STATUS_EXPIRED = "Expired"


  Org publisher
  TitleInstance title

  RefdataValue status

  Date startDate
  Date endDate

  Date dateCreated
  Date lastUpdated

  static mapping = {
    publisher column:'tp_publisher_fk', index: 'tp_pub_idx,tp_full_idx'
    title column:'tp_title_fk', index: 'tp_ttl_idx,tp_full_idx'
    status column:'tp_status_rv_fk', index: 'tp_pub_idx,tp_ttl_idx,tp_full_idx'
    startDate column: 'tp_start_date'
    endDate column: 'tp_end_date'
    dateCreated column:'tp_date_created', index: 'tp_created_idx'
    lastUpdated column:'tp_last_updated'
  }

  static constraints = {
    publisher(nullable:false, blank:false)
    title(nullable:false, blank:false)
    startDate(nullable: true)
    endDate(nullable: true)
    status(nullable:true, blank:false)
  }

  def beforeInsert() {
    RefdataValue status_active = RefdataCategory.lookup(TitlePublisher.RD_STATUS, TitlePublisher.STATUS_ACTIVE)

    if (this.status == null) {
      this.status = status_active
    }
  }
}
