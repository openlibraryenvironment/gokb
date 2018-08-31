package org.gokb

import org.gokb.cred.*;
import grails.converters.JSON

class FwkController {

  def springSecurityService

  def history() { 
    log.debug("FwkController::history...");
    def result = [:]

    def obj = resolveOID2(params.id)
    
    if(obj && obj.isReadable()) {

      result.max = params.max ?: 20;
      result.offset = params.offset ?: 0;
      result.timestamp = new Date()

      def qry_params = [ocn: obj.getClass().getSimpleName(), oid: params.id];
      def related_combos = null
      def oid_string = "[id:${params.id}]%"

      if( params.withCombos ) {
        related_combos = AuditLogEvent.executeQuery("select distinct ale.persistedObjectId from AuditLogEvent as ale where ale.className = 'Combo' AND (ale.newValue LIKE :oidt OR ale.oldValue LIKE :oidt)", [oidt: oid_string])
      }

      if (related_combos?.size() > 0 && params.withCombos) {
        qry_params.put('oidt', oid_string)
        qry_params.put('comboids', related_combos)

        def query_start_time = System.currentTimeMillis();

        result.events = AuditLogEvent.executeQuery("select e from org.gokb.cred.AuditLogEvent as e where (e.className= :ocn and e.persistedObjectId= :oid) OR (e.persistedObjectId IN (:comboids) AND (e.propertyName = 'fromComponent' OR e.propertyName = 'toComponent') AND (e.newValue NOT LIKE :oidt OR e.newValue IS NULL) AND (e.oldValue NOT LIKE :oidt OR e.oldValue IS NULL)) order by id desc",qry_params,[max:result.max,offset:result.offset]);

        log.debug("Fetch completed after ${System.currentTimeMillis() - query_start_time}");
//         def count_start_time = System.currentTimeMillis();
//
//         result.historyLinesTotal = AuditLogEvent.executeQuery("select count(e.id) from org.gokb.cred.AuditLogEvent as e where (e.className= :ocn and e.persistedObjectId= :oid) OR (e.persistedObjectId IN (:comboids) AND (e.propertyName = 'fromComponent' OR e.propertyName = 'toComponent') AND (e.newValue NOT LIKE :oidt OR e.newValue IS NULL) AND (e.oldValue NOT LIKE :oidt OR e.oldValue IS NULL))", qry_params)[0];
//
//         log.debug("Count completed (${result.historyLinesTotal}) after ${System.currentTimeMillis() - count_start_time}");
        result.historyLines = []

        result.events.eachWithIndex { evt, idx ->
          def event = [:]
          def allOidEvents = AuditLogEvent.findAllByPersistedObjectId(evt.persistedObjectId)
          def skip = false

          if ( evt.oldValue && evt.oldValue.startsWith("[id:org.gokb.cred") ) {
            event.oldValue = getComboValueMaps(evt.oldValue)
          }else{
            event.oldValue = evt.oldValue
          }

          if ( evt.newValue && evt.newValue.startsWith("[id:org.gokb.cred") ) {
            event.newValue = getComboValueMaps(evt.newValue)
          }else{
            event.newValue = evt.newValue
          }

          if ( evt.className == 'Combo' ) {
            def hasOwnerRef = allOidEvents.collect {( it.newValue?.contains(params.id) || it.oldValue?.contains(params.id)) && it.propertyName == evt.propertyName}.any { it == true }
            def deletedComboVal = null

            if(!hasOwnerRef) {
              allOidEvents.each { aoe ->
                if (aoe.eventName == 'INSERT' && aoe.propertyName == 'type') {
                  event.propertyName = aoe.newValue.split(']')[1]
                }
                else if (!evt.newValue && !evt.oldValue && aoe.propertyName == evt.propertyName && aoe.newValue) {
                  event.oldValue = getComboValueMaps(aoe.newValue)
                }
              }
            }
            else {
              skip = true
            }
          }else {
            event.propertyName = evt.propertyName
          }

          event.id = evt.id
          event.actor = evt.actor
          event.dateCreated = evt.dateCreated
          event.eventName = evt.eventName

          if (!skip) {
            result.historyLines.add(event)
          }
        }
      }
      else {
        def query_start_time = System.currentTimeMillis();

        result.historyLines = AuditLogEvent.executeQuery("select e from org.gokb.cred.AuditLogEvent as e where e.className= :ocn and e.persistedObjectId= :oid order by id desc", qry_params,[max:result.max,offset:result.offset]);

        log.debug("Fetch completed after ${System.currentTimeMillis() - query_start_time}");
//         def count_start_time = System.currentTimeMillis();
//
//         result.historyLinesTotal = AuditLogEvent.executeQuery("select count(e.id) from org.gokb.cred.AuditLogEvent as e where e.className= :ocn and e.persistedObjectId= :oid", qry_params)[0];
//
//         log.debug("Count completed (${result.historyLinesTotal}) after ${System.currentTimeMillis() - count_start_time}");
      }

    }else{
      log.error("resolve OID failed to identify a domain class. Input was ${params.id}")
    }
    withFormat {
      html { result }
      json {
        result.remove('events')
        render result as JSON
      }
    }
  }

  private List getComboValueMaps(String valueString) {
    def allOids = valueString.substring(1).split(/,\[/)

    def valueMaps = []

    allOids.each { ao ->
      def aot = ao.trim()

      if( aot.startsWith("id:org.gokb.cred") ) {
        valueMaps.add([val: aot.split(']')[1], oid: aot.split(']')[0].substring(3)])
      }
    }
    valueMaps
  }

  def notes() { 
    log.debug("FwkController::notes...");
    def result = [:]
    result.user = User.get(springSecurityService.principal.id)
    // result.owner = 
    def obj = resolveOID2(params.id)

    if ( obj.isReadable() ) {

      def oid_components = params.id.split(':');
      def qry_params = [oid_components[0],Long.parseLong(oid_components[1])];
      result.ownerClass = oid_components[0]
      result.ownerId = oid_components[1]

      result.max = params.max ?: 20;
      result.offset = params.offset ?: 0;

      result.noteLines = Note.executeQuery("select n from Note as n where ownerClass=? and ownerId=? order by id desc", qry_params, [max:result.max, offset:result.offset]);
      result.noteLinesTotal = AuditLogEvent.executeQuery("select count(n.id) from Note as n where ownerClass=? and ownerId=?",qry_params)[0];
    }

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
