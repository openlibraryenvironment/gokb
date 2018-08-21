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

      result.max = params.max ?: 20;
      result.offset = params.offset ?: 0;

      def qry_params = [ocn: obj.getClass().getSimpleName(), oid: params.id];
      def related_combos = null

      if( params.withCombos ) {
        related_combos = Combo.executeQuery("select c.id from Combo as c where c.fromComponent = :obj OR c.toComponent = :obj", [obj: obj]).collect { "org.gokb.cred.Combo:" + Objects.toString(it, null) }
      }

      if ( related_combos?.size() == 0 || !params.withCombos ) {
        result.historyLines = AuditLogEvent.executeQuery("select e from org.gokb.cred.AuditLogEvent as e where e.className= :ocn and e.persistedObjectId= :oid order by id desc",
                                                                        qry_params,
                                                                        [max:result.max,offset:result.offset]);
        result.historyLinesTotal = AuditLogEvent.executeQuery("select count(e.id) from org.gokb.cred.AuditLogEvent as e where e.className= :ocn and e.persistedObjectId= :oid",
                                                                                qry_params)[0];
      }
      else{
        qry_params.put('oidt', "%" + params.id + "]%")
        qry_params.put('comboids', related_combos)

        result.historyLines = AuditLogEvent.executeQuery("select e from org.gokb.cred.AuditLogEvent as e where e.className= :ocn and e.persistedObjectId= :oid OR (e.className = 'Combo' and e.persistedObjectId IN (:comboids) and (e.propertyName = 'fromComponent' OR e.propertyName = 'toComponent' OR e.eventName = 'UPDATE') and e.newValue NOT LIKE :oidt ) order by id desc",
                                                                        qry_params,
                                                                        [max:result.max,offset:result.offset]);

        result.historyLinesTotal = AuditLogEvent.executeQuery("select count(e.id) from org.gokb.cred.AuditLogEvent as e where e.className= :ocn and e.persistedObjectId= :oid OR (e.className = 'Combo' and e.persistedObjectId IN (:comboids) and (e.propertyName = 'fromComponent' OR e.propertyName = 'toComponent' OR e.eventName = 'UPDATE') and e.newValue NOT LIKE :oidt)",
                                                                                qry_params)[0];
        }



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
