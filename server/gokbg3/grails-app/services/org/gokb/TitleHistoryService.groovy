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
  def titleLookupService
  def componentUpdateService
  def messageService

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

  def processHistoryEvents(TitleInstance ti, titleObj, title_class_name, user, fullsync, locale) {
    def result = [result:'OK']
    def errors = [:]

    titleObj.historyEvents.each { jhe ->
        // 1971-01-01 00:00:00.0
      log.debug("Handling title history");
      try {
        def inlist = []
        def outlist = []
        def cont = true

        jhe.from.each { fhe ->
          def p = null
          def setCore = true

          if ( titleLookupService.compareIdentifierMaps(fhe.identifiers, titleObj.identifiers) && fhe.title == titleObj.name ) {
            log.debug("Setting main title ${ti} as participant")
            setCore = false
            p = ti
          }
          else {
            log.debug("Looking up connected title ${fhe} as participant")
            p = titleLookupService.findOrCreate(
              fhe.title,
              null,
              fhe.identifiers,
              user,
              null,
              title_class_name,
              fhe.uuid
            );
          }

          if ( p && !p.hasErrors() ) {
            if ( setCore ) {
              componentUpdateService.ensureCoreData(p, fhe, fullsync, user)
            }
            inlist.add(p);
          }
          else {
            cont = false;
          }
        }

        jhe.to.each { fhe ->

          def p = null
          def setCore = true

          if ( titleLookupService.compareIdentifierMaps(fhe.identifiers, titleObj.identifiers) && fhe.title == titleObj.name ) {
            log.debug("Setting main title ${ti} as participant")
            setCore = false
            p = ti
          }
          else {
            log.debug("Looking up connected title ${fhe} as participant")
            p = titleLookupService.findOrCreate(
              fhe.title,
              null,
              fhe.identifiers,
              user,
              null,
              title_class_name,
              fhe.uuid
            );
          }

          if ( p && !p.hasErrors() && !inlist.contains(p) ) {
            if ( setCore ) {
              componentUpdateService.ensureCoreData(p, fhe, fullsync, user)
            }
            outlist.add(p);
          }
          else {
            cont = false;
          }
        }

        def first = true;
        // See if we can locate an existing ComponentHistoryEvent involving all the titles specified in this event
        def che_check_qry_sw  = new StringWriter();
        def qparams = []

        che_check_qry_sw.write('select che from ComponentHistoryEvent as che where ')

        inlist.each { fhe ->
          if ( first ) { first = false; } else { che_check_qry_sw.write(' AND ') }

          che_check_qry_sw.write(' exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = ?) ')
          qparams.add(fhe)
        }

        outlist.each { fhe ->
          if ( first ) { first = false; } else { che_check_qry_sw.write(' AND ') }

          che_check_qry_sw.write(' exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = ?) ')
          qparams.add(fhe)
        }

        def che_check_qry = che_check_qry_sw.toString()

        log.debug("Search for existing history event:: ${che_check_qry} ${qparams}");

        def qr = []
        if (qparams.size() > 0) {
          qr = ComponentHistoryEvent.executeQuery(che_check_qry, qparams)
        }

        if ( qr.size() > 0 || inlist.size() == 0 || outlist.size() == 0 )
          cont = false;

        if ( cont ) {

          def he = new ComponentHistoryEvent()

          if ( jhe.date ) {
            ClassUtils.setDateIfPresent(jhe.date, he, 'eventDate');
          }

          he.save(flush:true, failOnError:true);

          inlist.each {
            def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'in');
            hep.save(flush:true, failOnError:true);
          }

          outlist.each {
            def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'out');
            hep.save(flush:true, failOnError:true);
          }
        }
        else {
          // Matched an existing TH event, not creating a duplicate
        }
      }
      catch ( grails.validation.ValidationException veh ) {
        if (!errors.historyEvents) {
          errors.historyEvents = []
        }

        log.error("Problem processing title history",veh);
        result.result="ERROR"
        errors.historyEvents << messageService.processValidationErrors(veh.errors)
      }
      catch ( Exception eh ) {
        log.error("Problem processing title history",eh);
        if (!errors.historyEvents) {
          errors.historyEvents = []
        }
        result.result="ERROR"
        errors.historyEvents << [message: messageService.resolveCode('crossRef.title.error.historyEvent', null, locale), baddata: jhe]
      }
    }

    if (errors.size() > 0) {
      result.errors = errors
    }

    result
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
}
