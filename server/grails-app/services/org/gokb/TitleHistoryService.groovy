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
import org.grails.web.json.JSONObject

@Transactional
class TitleHistoryService {

  def sessionFactory
  def titleLookupService
  def componentUpdateService
  def messageService
  def restMappingService
  def dateFormatService

  public List getDirectHistory(TitleInstance obj, boolean embed_titles, User user = null) {
    List result = []

    if (obj) {
      List history = obj.titleHistory

      if (history) {
        history.each { he ->
          Map mapped_event = [id: he.id, date: he.date ? dateFormatService.formatDate(he.date) : null, from: [], to: []]

          he.from.each { f ->
            if (embed_titles) {
              mapped_event.from << restMappingService.mapObjectToJson(f, [:], user)
            }
            else {
              mapped_event.from << [name: f.name, id: f.id, uuid: f.uuid]
            }
          }

          he.to.each { t ->
            if (embed_titles) {
              mapped_event.to << restMappingService.mapObjectToJson(t, [:], user)
            }
            else {
              mapped_event.to << [name: t.name, id: t.id, uuid: t.uuid]
            }
          }

          result << mapped_event
        }
      }
    }
    result
  }

  public Map restUpdate(ti, JSONObject reqBody) {
    Map result = [result: 'OK']
    List current_history = ti.titleHistory
    List events = []

    log.debug("Current history: ${current_history}")

    if (reqBody instanceof List) {
      log.debug("Got list of events")

      reqBody.each { event ->
        log.debug("Event ${event}")
        Map parts = [from: [], to: []]

        if (event.id) {
          Map matched_event = current_history.find { it.id == event.id }

          if (event.date && matched_event && event.date != dateFormatService.formatDate(matched_event.date)) {
            ComponentHistoryEvent he_obj = ComponentHistoryEvent.get(matched_event.id)

            if (he_obj) {
              Date parsed_date = null

              try {
                parsed_date = dateFormatService.parseDate(event.date)
              }
              catch (Exception e){
                log.debug("Illegal date value ${event.date}!")

                if (!errors.date)
                  errors.date = []

                errors.date << [message: "Unable to parse event date!", baddate: event]
              }

              if (errors.size() == 0 && parsed_date) {
                he_obj.eventDate = parsed_date
                log.debug("Updated date of existing event!")
              }

              events.add(he_obj.id)
            }
            else {
              log.debug("Unable to lookup event by id!")
              if (!errors.id)
                errors.id = []

              errors.id << [message: "Unable to lookup event for ID ${event.id}", baddata: event]
            }
          }
          else if (!matched_event) {
            log.debug("Matched event is not connected to this title!")
            if (!errors.id)
              errors.id = []

            errors.id << [message: "Existing event with ID ${event.id} is not connected to this title!", baddata: event]
          }
          else {
            events.add(matched_event.id)
          }
        }
        else {
          List lookedUpIds = []

          if (event.from instanceof List) {
            event.from.each { entry ->
              TitleInstance cti = null

              if (entry instanceof Integer) {
                if (entry == ti.id) {
                  cti = ti
                }
                else {
                  cti = TitleInstance.get(entry)
                }
              }
              else if (entry instanceof Map) {
                if (entry.id == ti.id) {
                  cti = ti
                }
                else {
                  cti = TitleInstance.get(entry.id)
                }
              }

              if (cti) {
                if (!lookedUpIds.contains(cti.id)) {
                  lookedUpIds.add(cti.id)
                }
                else {
                  if (!errors.from)
                    errors.from = []

                  errors.from << [message: "Multiple instances of title ${cti.id} in event participants!", baddata: entry, code: 404]
                }

                if (cti.id != ti.id) {
                  Map addResult = ensureSingleParticipant(ti, 'from', cti, event.date)

                  if (addResult.errors) {
                    errors << addResult.errors
                  }
                  else {
                    log.debug("New event ${addResult}")
                    events.add(addResult.id)
                  }
                }
              }
              else {
                if (!errors.from)
                  errors.from = []

                errors.from << [message: "Unable to lookup title for ${entry}", baddata: entry, code: 404]
              }
            }
          }

          if (event.to instanceof List) {
            event.to.each { entry ->
              TitleInstance cti = null

              if (entry instanceof Integer) {
                if (entry == ti.id) {
                  cti = ti
                }
                else {
                  cti = TitleInstance.get(entry)
                }
              }
              else if (entry instanceof Map) {
                if (entry.id == ti.id) {
                  cti = ti
                }
                else {
                  cti = TitleInstance.get(entry.id)
                }
              }

              if (cti) {
                if (!lookedUpIds.contains(cti.id)) {
                  lookedUpIds.add(cti.id)
                }
                else {
                  if (!errors.to)
                    errors.to = []

                  errors.to << [message: "Multiple instances of title ${cti.id} in event participants!", baddata: entry, code: 404]
                }

                if (cti.id != ti.id) {
                  Map addResult = ensureSingleParticipant(ti, 'to', cti, event.date)

                  if (addResult.errors) {
                    errors << addResult.errors
                  }
                  else {
                    log.debug("New event ${addResult}")
                    events.add(addResult.id)
                  }
                }
              }
              else {
                if (!errors.to)
                  errors.to = []

                errors.to << [message: "Unable to lookup title for ${entry}", baddata: entry, code: 404]
              }
            }
          }

          if (event.from instanceof Integer && event.from != ti.id) {
            TitleInstance cti = TitleInstance.get(event.from)

            if (cti) {
              if (cti.id != ti.id) {
                Map addResult = ensureSingleParticipant(ti, 'from', cti, event.date)

                if (addResult.errors) {
                  errors << addResult.errors
                }
                else {
                  log.debug("New event ${addResult}")
                  events.add(addResult.id)
                }
              }
            }
            else {
              if (!errors.id)
                errors.from = []

              errors.id << [message: "Unable to lookup title for ID ${from_entry.id}", baddata: entry, code: 404]
            }
          } else if (event.to instanceof Integer && event.to != ti.id) {
            TitleInstance cti = TitleInstance.get(event.from)

            if (cti) {
              if (cti.id != ti.id) {
                Map addResult = ensureSingleParticipant(ti, 'from', cti, event.date)

                if (addResult.errors) {
                  errors << addResult.errors
                }
                else {
                  log.debug("New event ${addResult}")
                  events.add(addResult.id)
                }
              }
            }
            else {
              if (!errors.id)
                errors.from = []

              errors.id << [message: "Unable to lookup title for ID ${from_entry.id}", baddata: entry, code: 404]
            }
          }
        }
      }

      if (errors.size() > 0) {
        result.result = 'ERROR'
        result.message = "There were errors updating the title history!"
        result.errors = errors
      }
      else if (remove) {
        current_history.each { ce ->
          if (!events.find { it == ce.id }) {
            def event = ComponentHistoryEvent.get(ce.id)
            event.delete(flush:true, failOnError:true)
          }
        }
      }
    }
    else {
      log.debug("Found illegal payload format!")
      result.result = 'ERROR'
      result.message = "Unexpected payload format, expected array of events!"
    }

    result
  }

  public Map ensureSingleParticipant(ti, type, participant, date) {
    Map result = [:]
    String dupe_hql = '''select che from ComponentHistoryEvent as che
                          where exists (
                            select pf.id from ComponentHistoryEventParticipant as pf
                            where pf.participant = :from
                            and pf.participantRole = 'in'
                            and pf.event = che
                          )
                          AND exists (
                            select pt.id from ComponentHistoryEventParticipant as pt
                            where pt.participant = :to
                            and pt.participantRole = 'out'
                            and pt.event = che
                          )'''

    Map pars = [:]

    if (type == 'from') {
      pars = [from: participant, to: ti]
    } else {
      pars = [from: ti, to: participant]
    }

    List dupe = ComponentHistoryEvent.executeQuery(dupe_hql, pars)

    if (!dupe) {
      Map req = [date: date]

      if (type == 'from') {
        req.from = [participant.id]
      } else {
        req.to = [participant.id]
      }

      Map add_result = addNewEvent(ti, req)

      if (add_result.errors) {
        result.errors = add_result.errors
      }
      else {
        result.id = add_result.new_events[0]
      }
    }
    else {
      if (dupe.size() == 1) {
        ComponentHistoryEvent existingEvent = dupe[0]
        result.id = existingEvent.id

        if (date && (!existingEvent.eventDate || dateFormatService.formatDate(existingEvent.eventDate) != date)) {
          existingEvent.eventDate = dateFormatService.parseDate(date)
        }
      }
      else {
        log.error("Got multiple history events between two titles ${dupe}!")
      }
    }

    result
  }

  public Map addNewEvent(TitleInstance ti, reqBody) {
    Map result = [result:'OK', new_events: []]
    Set inlist = []
    Set outlist = []
    Map errors = [:]
    LocalDateTime date = GOKbTextUtils.completeDateString(reqBody.date)

    if (!date) {
      errors.date << [message:"Unable to parse event date ${reqBody.date}", code: 404, baddata: reqBody.date]
      result.result = 'ERROR'
    }

    if ( reqBody.from instanceof Collection ) {
      reqBody.from.each { hep ->
        if ( hep != ti.id ) {
          TitleInstance hep_ti = TitleInstance.get(hep)

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
          TitleInstance hep_ti = TitleInstance.get(hep)

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
          ComponentHistoryEvent he = new ComponentHistoryEvent()

          ClassUtils.setDateIfPresent(date, he, 'eventDate')

          he.save(flush: true, failOnError: true)

          new ComponentHistoryEventParticipant(event: he, participant: it, participantRole: 'in').save(flush: true, failOnError: true)
          new ComponentHistoryEventParticipant(event: he, participant: ti, participantRole: 'out').save(flush: true, failOnError: true)

          result.new_events.add(he.id)
        }
      }
      else if ( outlist?.size() > 0 && inlist.size() == 0 ) {
        outlist.each {
          ComponentHistoryEvent he = new ComponentHistoryEvent()

          ClassUtils.setDateIfPresent(date, he, 'eventDate')

          he.save(flush: true, failOnError: true)

          new ComponentHistoryEventParticipant(event: he, participant: it, participantRole: 'out').save(flush: true, failOnError: true)
          new ComponentHistoryEventParticipant(event: he, participant: ti, participantRole: 'in').save(flush: true, failOnError: true)

          result.new_events.add(he.id)
        }
      }
      else {
        errors.general = [
          [
            message: "Request contains multiple events, but must only contain one (from OR to)!",
            code:400,
            baddata: reqBody
          ]
        ]
      }
    }

    if (errors.size() > 0) {
      result.errors = errors
      result.result = 'ERROR'
    }
    result
  }

  public void addDirectEvent(from, to, date) {
    String che_query = '''from ComponentHistoryEvent as che where
                          exists (
                            select chep from ComponentHistoryEventParticipant as chep
                            where chep.event = che
                            and chep.participant = :source
                            and participantRole = :pri
                          )
                          and exists (
                            select chep from ComponentHistoryEventParticipant as chep
                            where chep.event = che
                            and chep.participant = :target
                            and participantRole = :pro
                          )'''

    List dupes = ComponentHistoryEvent.executeQuery(che_query, [source: from, target: to, pri: 'in', pro: 'out'])

    if (!dupes) {
      log.debug("Adding new history event ${from.name} -> ${to.name}")
      ComponentHistoryEvent new_event = new ComponentHistoryEvent(eventDate: date).save(flush:true, failOnError:true)

      new ComponentHistoryEventParticipant(event: new_event, participant: from, participantRole: 'in').save(flush:true, failOnError:true)
      new ComponentHistoryEventParticipant(event: new_event, participant: to, participantRole: 'out').save(flush:true, failOnError:true)
    }
    else {
      log.debug("Not adding duplicate event between ${from} -> ${to}!")
    }
  }

  public void transferEvents(old_ti, new_ti) {
    List ti_history = old_ti.getTitleHistory()

    ti_history.each { ohe ->
      List new_from = []
      List new_to = []
      boolean dupe = false

      if (ohe.to.contains(old_ti)){
        ohe.to.removeIf { it == old_ti }
        ohe.to.add(new_ti)
        new_to = ohe.to

        ohe.from.each { hep ->
          List he_match = ComponentHistoryEvent.executeQuery('''select che from ComponentHistoryEvent as che
                                                                where exists (
                                                                  select chep from ComponentHistoryEventParticipant as chep
                                                                  where chep.event = che
                                                                  and chep.participant = :fromPart
                                                                )
                                                                AND exists (
                                                                  select chep from ComponentHistoryEventParticipant as chep
                                                                  where chep.event = che
                                                                  and chep.participant = :toPart
                                                                )''',
                                                                [fromPart: hep, toPart: new_ti])

          if (he_match){
            dupe = true
          }
        }

        new_from = ohe.from
      }
      else if (ohe.from.contains(old_ti)){
        ohe.from.removeIf { it == old_ti }
        ohe.from.add(new_ti)
        new_from = ohe.from

        ohe.from.each { hep ->
          List he_match = ComponentHistoryEvent.executeQuery('''select che from ComponentHistoryEvent as che
                                                                where exists (
                                                                  select chep from ComponentHistoryEventParticipant as chep
                                                                  where chep.event = che
                                                                  and chep.participant = :fromPart
                                                                )
                                                                AND exists (
                                                                  select chep from ComponentHistoryEventParticipant as chep
                                                                  where chep.event = che and chep.participant = :toPart
                                                                )''',
                                                                [fromPart: new_ti, toPart: hep])

          if (he_match){
            dupe = true
          }
        }

        new_to = ohe.to
      }
      if (!dupe){
        ComponentHistoryEvent he = new ComponentHistoryEvent()

        if (ohe.date){
          he.eventDate = ohe.date
        }

        he.save(flush: true, failOnError: true)

        new_from.each{
          new ComponentHistoryEventParticipant(event: he, participant: it, participantRole: 'in').save(flush: true, failOnError: true)
        }
        new_to.each{
          new ComponentHistoryEventParticipant(event: he, participant: it, participantRole: 'out').save(flush: true, failOnError: true)
        }
      }
    }
  }
}
