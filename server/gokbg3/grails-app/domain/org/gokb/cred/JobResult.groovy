package org.gokb.cred

import grails.converters.JSON

class JobResult {

  String uuid
  String description
  String statusText
  String resultObject
  RefdataValue type
  Long ownerId
  Long groupId
  Date startTime
  Date endTime
  Long linkedItemId

  static mapping = {
    uuid column: 'jr_uuid'
    description column: 'jr_description', type: 'text'
    statusText column: 'jr_status_text'
    resultObject column: 'jr_result_json', type: 'text'
    type column: 'jr_type_rv_fk'
    ownerId column: 'jr_owner_fk'
    groupId column: 'jr_group_fk'
    startTime column: 'jr_start_time'
    endTime column: 'jr_end_time'
    linkedItemId column: 'jr_linked_item_fk'
  }

  def afterInsert() {
    if (!uuid) {
      uuid = UUID.randomUUID().toString()
    }
  }

  def getResultJson() {
    def result = null
    if (resultObject && resultObject.length() > 0 ) {
      result = JSON.parse(resultObject);
    }
    result;
  }
}
