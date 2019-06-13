package org.gokb

import org.gokb.cred.*;
import grails.converters.JSON
import groovy.time.TimeCategory

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
      result.objectclass = obj.getClass().getSimpleName()
      result.label = obj.name ?: obj.id

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

        def events = getCombinedEvents(obj, qry_params, result.max, result.offset )

        log.debug("Fetch with combos completed after ${System.currentTimeMillis() - query_start_time}");

        def processed_events = processEvents(events)
        def skippedLastCall = processed_events.skipped
        def new_offset = result.max

        result.historyLines = processed_events.historyLines

        while ( skippedLastCall > 0 ) {
          log.debug("Last call skipped ${skippedLastCall} - new offset: ${new_offset}")

          def added_events = getCombinedEvents(obj, qry_params, skippedLastCall, new_offset)

          new_offset += skippedLastCall
          processed_events = processEvents(added_events)
          skippedLastCall = processed_events.skipped

          result.historyLines.addAll(processed_events.historyLines)
        }

      }
      else {
        def query_start_time = System.currentTimeMillis();

        def events = AuditLogEvent.executeQuery("select e from org.gokb.cred.AuditLogEvent as e where e.className= :ocn and e.persistedObjectId= :oid order by id desc", qry_params,[max:result.max,offset:result.offset]);

        def processed_events = processEvents(events)

        result.historyLines = processed_events.historyLines

        log.debug("Fetch without combos completed after ${System.currentTimeMillis() - query_start_time}");
      }

    }else{
      log.error("resolve OID failed to identify a domain class. Input was ${params.id}")
    }
    withFormat {
      html { result }
      json { render result as JSON }
    }
  }

  private List getCombinedEvents(def obj, LinkedHashMap qry_params, int max, int offset) {
    def events = []
    if(obj.class.simpleName == 'TitleInstancePackagePlatform') {
      events = AuditLogEvent.executeQuery("select e from org.gokb.cred.AuditLogEvent as e where (e.className= :ocn and e.persistedObjectId= :oid) OR (e.persistedObjectId IN (:comboids) AND (e.propertyName = 'fromComponent' OR e.propertyName = 'toComponent') AND (e.newValue NOT LIKE :oidt OR e.newValue IS NULL) AND (e.oldValue NOT LIKE :oidt OR e.oldValue IS NULL)) order by id desc",qry_params,['max':max,'offset':offset]);
    }
    else {
      def combo_rdc = RefdataCategory.findByLabel('Combo.Type')
      def criteria = AuditLogEvent.createCriteria()

      events = criteria.list ('max':max, 'offset':offset) {
        order 'id', 'desc'
        and {
          or {
            not {
              isNull('oldValue')
            }
            not {
              isNull('newValue')
            }
          }
          or {
            and {
              eq('className', qry_params.ocn)
              eq('persistedObjectId', qry_params.oid)
              ne('propertyName', 'lastSeen')
            }
            and {
              'in'('persistedObjectId', qry_params.comboids)
              or {
                eq('propertyName', 'fromComponent')
                eq('propertyName', 'toComponent')
              }
              or {
                not {
                  like('newValue', qry_params.oidt)
                }
                isNull('newValue')
              }
              or {
                not {
                  like('oldValue', qry_params.oidt)
                }
                isNull('oldValue')
              }
              or {
                not {
                  like('newValue', "[id:org.gokb.cred.TitleInstanceP%")
                }
                isNull('newValue')
              }
              or {
                not {
                  like('oldValue', "[id:org.gokb.cred.TitleInstanceP%")
                }
                isNull('oldValue')
              }
              if ( obj.class.simpleName == 'Platform' || obj.class.simpleName == 'Org') {
                or {
                  not {
                    like('newValue', "[id:org.gokb.cred.Package%")
                  }
                  isNull('newValue')
                }
                or {
                  not {
                    like('oldValue', "[id:org.gokb.cred.Package%")
                  }
                  isNull('oldValue')
                }
                or {
                  not {
                    like('newValue', "[id:org.gokb.cred.BookInstance%")
                  }
                  isNull('newValue')
                }
                or {
                  not {
                    like('newValue', "[id:org.gokb.cred.JournalInstance%")
                  }
                  isNull('newValue')
                }
              }
            }
          }
        }
      }
    }

    events
  }

  private LinkedHashMap processEvents(List events) {

    log.debug("Process ${events.size()} events ..")

    def stagingHistoryLines = []
    def finalHistoryLines = []
    def skippedLines = 0

    events.eachWithIndex { evt, idx ->
      def event = [:]
      def allOidEvents = AuditLogEvent.findAllByPersistedObjectId(evt.persistedObjectId)
      def skip = false

      event.oldValue = getComboValueMaps(evt.oldValue)

      event.newValue = getComboValueMaps(evt.newValue)

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
          event.className = 'Combo'
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
        stagingHistoryLines.add(event)
      }else {
        skippedLines++
      }
    }

    stagingHistoryLines.eachWithIndex { hl, idx ->
      if(hl.className == 'Combo') {
        log.debug("Combo line: ${hl}")
      }

      use( TimeCategory ) {
        if ( idx < stagingHistoryLines.size() - 1
          && hl.className == 'Combo'
          && stagingHistoryLines[idx+1].className == 'Combo'
          && hl.dateCreated <= stagingHistoryLines[idx+1].dateCreated + 1.second
          && hl.propertyName == stagingHistoryLines[idx+1].propertyName
          && stagingHistoryLines[idx+1].newValue[0].val
          && hl.eventName == 'DELETE'
          && stagingHistoryLines[idx+1].eventName == 'INSERT'
        ) {
          hl.eventName = 'UPDATE'
          hl.newValue = stagingHistoryLines[idx+1].newValue
          finalHistoryLines.add(hl)
          log.debug("Combo delete line: ${hl}")
        }
        else if ( idx > 0
          && hl.className == 'Combo'
          && hl.dateCreated >= stagingHistoryLines[idx-1].dateCreated - 1.second
          && hl.propertyName == stagingHistoryLines[idx-1].propertyName
          && hl.eventName == 'INSERT'
          && stagingHistoryLines[idx-1].eventName == 'UPDATE'
        ) {
          skippedLines++
          log.debug("Skipped line: ${hl}")
        }
        else {
          finalHistoryLines.add(hl)
        }
      }
    }

    return [historyLines: finalHistoryLines, skipped: skippedLines]
  }

  private List getComboValueMaps(valueString) {
    def valueMaps = []

    if (valueString) {
      def allOids = null

      if ( valueString.startsWith('[id:') ) {
        allOids = valueString.substring(1).split(/,\s\[/)
      }
      else {
        valueMaps.add([val: valueString, oid: null])
      }

      if(valueMaps.size() == 0 && allOids) {
        allOids.each { ao ->
          def aot = ao.trim()
          def oid_split = aot.split(']')

          valueMaps.add([val: oid_split[1], oid: (oid_split[0].startsWith('id:org.gokb.cred') ? oid_split[0].substring(3) : null) ])
        }
      }
    }
    else {
      valueMaps.add([val: "", oid: null])
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
    def user = springSecurityService.currentUser

    if ( displayobj && user ) {
      def watch = ComponentWatch.findByComponentAndUser(displayobj, user)
      if (watch) {
        // watch exists
        watch.delete(flush:true);
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
