package org.gokb

import com.k_int.ClassUtils
import grails.gorm.transactions.Transactional
import org.gokb.FTControl
import org.hibernate.ScrollMode
import java.nio.charset.Charset
import java.util.GregorianCalendar
import org.gokb.cred.ComponentHistoryEventParticipant
import org.gokb.cred.ComponentHistoryEvent
import org.gokb.cred.TitleInstance


@Transactional
class TitleHistoryService {

  def executorService
  def sessionFactory

  def updateTitleHistories() {
    log.debug("updateTitleHistories");
    def future = executorService.submit({
      doTitleHistoryUpdate()
    } as java.util.concurrent.Callable)
    log.debug("updateTitleHistories returning");
  }

  def doTitleHistoryUpdate() {
    log.debug("doTitleHistoryUpdate");
    def max_timestamp = BatchControl.getLastTimestamp('org.gokb.cred.ComponentHistoryEvent','TitleHistoryUpdate');
    log.debug("looking for all component history events > ${max_timestamp}");
    def events = ComponentHistoryEvent.findAllByDateCreatedGreaterThan(max_timestamp);
    events.each {
      log.debug("Process ${it}");
      // Step 1 - Find all titles in this revised title history
      def th_graph = findGraphOfEvents(it);
      // Step 2 - Remove any title histories involved
      def histories = getHistories(th_graph);
      // Step 3 - create a new one.

    }
  }

  def findParticipatingTitles(thevent) {
    def title_history_events_to_expand = [thevent]
    def th_graph=[]

    while ( title_history_events_to_expand.size() > 0 ) {
      followTitleHistory(title_history_events_to_expand, th_graph)
    }
  }

  def followTitleHistory(title_history_events_to_expand, th_graph) {
    title_history_events_to_expand.each { the ->
      if ( th_graph.contains( the ) ) {
        // Identified event is already in the graph - may be a cycle - don't add it again
      }
      else {
        th_graph.add(the);
      }
    }
  }

  def getHistories(th_graph) {   
  }


  def addNewEvent(ti, reqBody) {
    Set inlist = []
    Set outlist = []
    def errors = [:]
    def result = [result:'OK', new_events: []]
    def date = GOKbTextUtils.completeDateString(reqBody.date)

    if (!date) {
      errors.date << [message:"Unable to parse event date ${reqBody.date}", code: 404, baddata: reqBody.date]
      result.result = 'ERROR'
    }

    if ( reqBody.from instanceof Collection ) {
      reqBody.from.each { hep ->
        if ( hep != ti.id ) {
          def hep_ti = TitleInstance.get(hep)

          if ( hep_ti ) {
            inlist.add(hep_ti)
          }
          else {
            if ( !errors.from ) {
              errors.from = []
            }

            errors.from << [message:"Unable to lookup history participant", code: 404, baddata: hep]
            result.result = 'ERROR'
          }
        }
      }
    }

    if ( reqBody.to instanceof Collection ) {
      reqBody.to.each { hep ->
        if ( hep != ti.id ) {
          def hep_ti = TitleInstance.get(hep)

          if ( hep_ti && !inlist.contains(hep_ti)) {
            outlist.add(hep_ti)
          }
          else if (!hep_ti) {
            if ( !errors.to ) {
              errors.to = []
            }

            errors.to << [message:"Unable to lookup history participant", code: 404, baddata: hep]
            result.result = 'ERROR'
          }
          else {
            if ( !errors.to ) {
              errors.to = []
            }

            errors.to << [message:"Participants must not be defined on both ends!", code: 400, baddata: hep]
          }
        }
      }
    }

    if ( errors.size() == 0 ) {
      if ( inlist?.size() > 0 && outlist.size() == 0 ) {
        inlist.each {
          def he = new ComponentHistoryEvent()

          ClassUtils.setDateIfPresent(date, he, 'eventDate');

          he.save(flush:true, failOnError:true);

          def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'in');
          hep.save(flush:true, failOnError:true);

          def tip = new ComponentHistoryEventParticipant(event:he, participant:ti, participantRole:'out');
          tip.save(flush:true, failOnError:true);

          result.new_events.add(he.id)
        }
      } 
      else if ( outlist?.size() > 0 && inlist.size() == 0 ) {
        outlist.each {
          def he = new ComponentHistoryEvent()

          ClassUtils.setDateIfPresent(date, he, 'eventDate');

          he.save(flush:true, failOnError:true);

          def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'out');
          hep.save(flush:true, failOnError:true);

          def tip = new ComponentHistoryEventParticipant(event:he, participant:ti, participantRole:'in');
          tip.save(flush:true, failOnError:true);

          result.new_events.add(he.id)
        }
      }
      else {
        errors.general = [[message: "Request contains multiple events, but must only contain one (from OR to)!", code:400, baddata: reqBody]]
      }
    }

    if (errors.size() > 0) {
      result.errors = errors
      result.result = 'ERROR'
    }
    result
  }

  def deleteEvent(event) {
    def deleted_parts = ComponentHistoryEventParticipant.executeUpdate("delete from ComponentHistoryEventParticipant where event = :event", [event:event])
    event.delete(flush:true, failOnError:true)
  }
}
