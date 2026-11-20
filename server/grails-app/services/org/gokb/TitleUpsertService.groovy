package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.gorm.transactions.Transactional

import java.time.LocalDateTime

import org.gokb.cred.*
import org.gokb.GOKbTextUtils
import org.grails.web.json.JSONObject
import org.grails.web.json.JSONArray

class TitleUpsertService {

  def messageService
  def restMappingService
  def reviewRequestService
  def titleAugmentService
  def titleHistoryService
  def titleLookupService
  def orgService

  public Map upsertList(JSONArray rjson, User user, Locale locale, Job job, boolean fullsync = false) {
    Map job_result = [result: 'OK']

    TitleInstance.withNewSession { session ->
      JSONArray json = rjson
      User job_user = User.findById(user.id)
      int ctr = 0

      job_result.results = []

      for (e in json) {
        if (Thread.currentThread().isInterrupted()) {
          log.debug("Job cancelling ..")
          cancelled = true
          job.endTime = new Date()
          job_result.status = "cancelled"
          break;
        }

        job_result.results << upsert(e, job_user, locale, fullsync)

        ctr++
        job.setProgress(ctr, json.size())

        if (ctr % 50 == 0) {
          session.flush()
          session.clear()
        }
      }

      job_result.message = "Upserted ${ctr} titles."

      job.endTime = new Date()
      job.setProgress(100)
      job.message("Finished processing ${job_result?.results?.size()} titles.".toString())

      JobResult.withTransaction {
        JobResult result_object = JobResult.findByUuid(job.uuid)

        if (!result_object) {
          Map job_map = [
              uuid        : (job.uuid),
              description : (job.description),
              resultObject: (job_result as JSON).toString(),
              type        : (job.type),
              statusText  : (job_result?.result),
              ownerId     : (job.ownerId),
              groupId     : (job.groupId),
              startTime   : (job.startTime),
              endTime     : (job.endTime)
          ]

          result_object = new JobResult(job_map).save(flush: true, failOnError: true)
        }
      }
    }

    return job_result
  }

  @Transactional
  public Map upsert(JSONObject reqBody, User user, Locale locale, boolean fullsync = false) {
    Map result = [result: 'OK', changed: false]
    Map errors = [:]
    List ids = reqBody.ids ?: reqBody.identifiers ?: []
    String publisher_name = null
    TitleInstance obj
    Class type = setType(reqBody.publicationType ?: reqBody.type)

    if (!type) {
      result.result = 'ERROR'
      result.message = "Unrecognized title type!"

      return result
    }
    else if (type == TitleInstance) {
      result.result = 'ERROR'
      result.message = "Specific title type required!"

      return result
    }

    if (reqBody.uuid) {
      TitleInstance uuid_match = TitleInstance.findByUuid(reqBody.uuid)

      if (uuid_match.class != type) {
        result.result = 'ERROR'
        result.message = 'UUID lookup returned a different title class!'
        return result
      }
      else {
        obj = uuid_match
      }
    }

    if (!obj) {
      if (reqBody?.publisher) {
        if (reqBody.publisher instanceof Collection) {
          log.debug("Skipping publisher list")
        }
        else if (reqBody.publisher instanceof String) {
          publisher_name = reqBody.publisher
        }
        else {
          publisher_name = Org.get(reqBody.publisher)?.name
        }
      }

      if ( reqBody?.name?.trim() && type && type != TitleInstance ) {
        reqBody.name = GOKbTextUtils.removeControlChars(reqBody.name.trim())

        Map title_lookup = titleLookupService.find(
          reqBody.name,
          publisher_name,
          ids,
          type.name
        )

        if (title_lookup.to_create) {
          obj = type.newInstance()
          obj.name = reqBody.name
          obj.uuid = reqBody.uuid
          obj.save(flush: true, failOnError: true)

          if (title_lookup.matches.size() > 0 && !fullsync) {
            Map additionalInfo = [:]
            List linked_ids = [obj.id]
            RefdataValue rr_type = RefdataCategory.lookup("ReviewRequest.StdDesc", "Duplicate Title Info")

            additionalInfo.otherComponents = []

            title_lookup.matches.each { tlm ->
              additionalInfo.otherComponents.add([
                oid:"${tlm.object.class.name}:${tlm.object.id}",
                name:"${tlm.object.name}",
                id: tlm.object.id,
                uuid: tlm.object.uuid
              ])
              linked_ids.add(tlm.object.id)
            }

            additionalInfo.cstring = linked_ids.sort().join('_')

            reviewRequestService.raise(
              obj,
              "New TI created.",
              "There have been possible conflicts with other existing titles.",
              null,
              (additionalInfo as JSON).toString(),
              rr_type,
              componentLookupService.findCuratoryGroupOfInterest(obj, user)
            )

            result.message = "Created new title ${obj} with id conflicts!"
          }
          else {
            result.message = "Created new title ${obj}: ${obj.name}."
          }
        }
        else if (title_lookup.matches.size() == 1) {
          obj = title_lookup.matches[0].object

          result.message = "Matched existing title ${obj}."
        }
        else {
          log.debug("Got ${title_lookup.matches.size()} matches for ${reqBody.name} ... upsert skipped!")

          result.message = "Matched ${title_lookup.matches.size()} titles, skipped upsert!"
        }
      }

      if (obj) {
        result.titleId = obj.id

        if (reqBody.publishedTo) {
          LocalDateTime parsed_end = GOKbTextUtils.completeDateString(reqBody.publishedTo, false)

          if (parsed_end) {
            reqBody.publishedTo = parsed_end.toLocalDate().toString()
          }
          else {
            errors.publishedTo = [[message: "Unable to parse value ${reqBody.publishedTo}", baddata: reqBody.publishedTo]]
          }
        }

        result.changed |= restMappingService.updateObject(obj, obj.jsonMapping, reqBody)

        if (!errors && obj.validate()) {
          obj.save(flush:true, failOnError: true)

          log.debug("Updated dates: ${obj.publishedFrom} -- ${obj.publishedTo}")

          Map variant_result = restMappingService.updateVariantNames(obj, reqBody.variantNames, fullsync)

          if (variant_result.errors.size() > 0) {
            errors.variantNames = variant_result.errors
          }

          result.changed |= variant_result.changed

          Map subject_result = restMappingService.updateSubjects(obj, reqBody.subjects, fullsync)

          if (subject_result.errors.size() > 0) {
            errors.subjects = subject_result.errors
          }

          result.changed |= subject_result.changed

          List id_list = (reqBody.ids instanceof Collection ? reqBody.ids : (reqBody.identifiers instanceof Collection ? reqBody.identifiers : [])) as List

          if (id_list) {
            Map id_result = restMappingService.updateIdentifiers(obj, id_list, fullsync)

            result.changed |= id_result.changed

            if (id_result.errors.size() > 0) {
              errors.ids = id_result.errors
            }
          }

          // Lookup publishers
          if (reqBody.publisher instanceof Collection) {
            Map publisher_result = restMappingService.updatePublisherList(obj, reqBody.publisher as List, fullsync, true)

            result.changed |= publisher_result.changed

            if (publisher_result.errors.size() > 0) {
              errors.publisher = publisher_result.errors
            }
          }

          if (reqBody.historyEvents instanceof Collection) {
            Map history_result = titleHistoryService.restUpdate(obj, reqBody.historyEvents, true)

            result.changed |= history_result.changed

            if (history_result.errors) {
              errors.historyEvents = history_result.errors
            }
          }
        }
        else {
          result.result = 'ERROR'
          errors << messageService.processValidationErrors(obj.errors, request.locale)
        }
      }
      else {
        result.result = 'ERROR'
        result.message = 'Unable to reference an unique title!'
      }
    }
    else {
      errors.name = [
        [
          baddata: reqBody?.name,
          message:"Request is missing a title name!",
          messageCode: "validiation.missingName"
        ]
      ]
    }

    if (errors.size() > 0) {
      log.debug("Errors: ${errors}")
      result.result = 'ERROR'
      result.error = errors
    }

    result
  }

  private Class setType(typeVal) {
    Class type = TitleInstance

    if (typeVal) {
      if (typeVal.toLowerCase() == 'journal' || typeVal.toLowerCase() == 'serial' ) {
        type = JournalInstance
      }
      else if (typeVal.toLowerCase() == 'book' || typeVal.toLowerCase() == 'monograph') {
        type = BookInstance
      }
      else if (typeVal.toLowerCase() == 'database') {
        type = DatabaseInstance
      }
      else if (typeVal.toLowerCase() == 'other') {
        type = OtherInstance
      }
      else {
        type = null
      }
    }
    return type
  }
}