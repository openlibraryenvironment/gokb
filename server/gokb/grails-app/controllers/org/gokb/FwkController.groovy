package org.gokb

import org.gokb.cred.*;
import grails.converters.JSON

class FwkController {

  def springSecurityService

  def history() { 
    log.debug("FwkController::history...");
    def result = [:]
    result.user = User.get(springSecurityService.principal.id)

    def obj = resolveOID2(params.id)
    
    if(obj) {
      def qry_params = [obj.getClass().getSimpleName(),Objects.toString(obj.id, null)];
      log.debug("Params: ${qry_params}")

      result.max = params.max ?: 20;
      result.offset = params.offset ?: 0;
      log.debug("${AuditLogEvent.class.name}")

      result.historyLines = AuditLogEvent.executeQuery("select e from org.gokb.cred.AuditLogEvent as e where e.className=? and e.persistedObjectId=? order by id desc", 
                                                                              qry_params, 
                                                                              [max:result.max, offset:result.offset]);

      result.historyLinesTotal = AuditLogEvent.executeQuery("select count(e.id) from org.gokb.cred.AuditLogEvent as e where e.className=? and e.persistedObjectId=?",
                                                                              qry_params)[0];
    }else{
      log.error("resolve OID failed to identify a domain class. Input was ${params.id}")
    }
    
    result
  }

  def notes() { 
    log.debug("FwkController::notes...");
    def result = [:]
    result.user = User.get(springSecurityService.principal.id)
    // result.owner = 
    def oid_components = params.id.split(':');
    def qry_params = [oid_components[0],Long.parseLong(oid_components[1])];
    result.ownerClass = oid_components[0]
    result.ownerId = oid_components[1]

    result.max = params.max ?: 20;
    result.offset = params.offset ?: 0;

    result.noteLines = Note.executeQuery("select n from Note as n where ownerClass=? and ownerId=? order by id desc", qry_params, [max:result.max, offset:result.offset]);
    result.noteLinesTotal = AuditLogEvent.executeQuery("select count(n.id) from Note as n where ownerClass=? and ownerId=?",qry_params)[0];

    result
  }

  def attachments() { 
    log.debug("FwkController::attachments...");
  }

  def resolveOID2(oid) {
    def oid_components = oid.split(':');
    def result = null;
    def domain_class=null;
    domain_class = grailsApplication.getArtefact('Domain',oid_components[0])
    if ( domain_class ) {
      if ( oid_components[1]=='__new__' ) {
        result = domain_class.getClazz().refdataCreate(oid_components)
        log.debug("Result of create ${oid} is ${result}");
      }
      else {
        result = domain_class.getClazz().get(oid_components[1])
      }
    }
    else {
      log.error("resolve OID failed to identify a domain class. Input was ${oid_components}");
    }
    result
  }

  def toggleWatch() {
    log.debug("FwkController::toggleWatch(${params})");
    def result = [change:0]

    def displayobj = resolveOID2(params.oid)
    def user = User.get(springSecurityService.principal.id)

    if ( displayobj && user ) {
      def watch = KBComponent.executeQuery("select n from ComponentWatch as n where n.component=? and n.user=?",[displayobj, user])
      if ( watch.size() == 1 ) {
        // watch exists
        watch[0].delete();
        result.change = -1
      }
      else {
        // Create new watch
        def new_watch = new ComponentWatch(component:displayobj, user:user).save(flush:true, failOnError:true);
        result.change = 1
      }
    }
    render result as JSON
  }

}
