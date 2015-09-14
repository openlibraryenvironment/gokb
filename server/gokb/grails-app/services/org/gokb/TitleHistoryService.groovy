package org.gokb


import grails.transaction.Transactional
import org.gokb.FTControl
import org.hibernate.ScrollMode
import java.nio.charset.Charset
import java.util.GregorianCalendar


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
}
