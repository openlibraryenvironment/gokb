package org.gokb

import org.gokb.cred.*;
import org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent

class FwkController {

  def springSecurityService

  def history() { 
    log.debug("FwkController::history...");
    def result = [:]
    result.user = User.get(springSecurityService.principal.id)
    def oid_components = params.id.split(':');
    def qry_params = [oid_components[0],oid_components[1]];

    result.max = params.max ?: 20;
    result.offset = params.offset ?: 0;

    result.historyLines = AuditLogEvent.executeQuery("select e from AuditLogEvent as e where className=? and persistedObjectId=? order by id desc", qry_params, [max:result.max, offset:result.offset]);
    result.historyLinesTotal = AuditLogEvent.executeQuery("select count(e.id) from AuditLogEvent as e where className=? and persistedObjectId=?",qry_params)[0];

    result
  }

  def notes() { 
    log.debug("FwkController::notes...");
  }

  def attachments() { 
    log.debug("FwkController::attachments...");
  }
}
