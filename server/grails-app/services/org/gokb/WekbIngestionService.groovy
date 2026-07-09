package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JobResult
import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.Source
import org.gokb.cred.Combo
import org.gokb.cred.TIPPCoverageStatement
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.User
import org.hibernate.Session
import org.hibernate.SessionFactory

import java.time.LocalDate

//@Transactional
class WekbIngestionService {

  WekbAPIService wekbAPIService
  TippService tippService
  /* def titleMatchResult = [
        matches: [
                partial: 0,
                full: 0
        ],
        created: 0,
        conflicts: 0,
        noid: 0
  ]
  def titleMatchConflicts = []
  */
  boolean isUpdate
  Long ingest_systime
  RefdataValue rdv_current
  RefdataValue rdv_deleted
  RefdataValue rdv_expected
  RefdataValue rdv_retired
  SessionFactory sessionFactory
  ConcurrencyManagerService concurrencyManagerService
  Map identifierTargetTypes = [:]
  final int SIZE_LIMIT = 30000
  def rdv_liststatus_checked
  def rdv_liststatus_progress

  def startTitleImport (pkgInfo, Source pkg_source, Platform pkg_plt, Org pkg_prov, Package pkg, Job job, Boolean async, Boolean restrictSize) {
    def result = [result: 'OK', dryRun: false]
    result.messages = []
    long startTime = System.currentTimeMillis()
    ingest_systime = startTime
    def ingestDate = LocalDate.now().toString()
    int batchSize = 100
    def missedBatches = []

    String sourceUrl = pkg_source?.url
    String wekbUUID = extractUUIDFromUrlString(sourceUrl)

    def packageInfo = wekbAPIService.getPackageByUuid(wekbUUID)
    int titleCount = packageInfo[0]?.titleCount
    List<String> validTippStatusList = Arrays.asList("Deleted", "Retired", "Current", "Expected")

    if ( restrictSize && titleCount > SIZE_LIMIT ) {
      result.result = 'ERROR'
      //result.messageCode = 'kbart.errors.url.fileSize'
      result.message = "The package you want to import is too big! Packages with more than 30.000 titles have to be authorized manually by an administrator."
    } else {
      identifierTargetTypes = loadIdentifierTargetTypes()
      Map validIdentifierForPubType = [:]
      validIdentifierForPubType.put("Monograph", getValidIdentifiersForPublicationType("Monograph"))
      validIdentifierForPubType.put("Serial", getValidIdentifiersForPublicationType("Serial"))
      validIdentifierForPubType.put("Database", getValidIdentifiersForPublicationType("Database"))
      validIdentifierForPubType.put("Other", getValidIdentifiersForPublicationType("Other"))

      rdv_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
      rdv_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
      rdv_expected = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_EXPECTED)
      rdv_retired = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)

      RefdataValue combo_pkg = RefdataCategory.lookup(Combo.RD_TYPE, 'Package.Tipps')

      isUpdate = false
      int old_tipp_count = TitleInstancePackagePlatform.executeQuery('''select count(*)
              from TitleInstancePackagePlatform as tipp,
              Combo as c
              where c.fromComponent.id = :pkg
              and c.toComponent = tipp
              and c.type = :ct
              and tipp.status != :sd''',
              [pkg: pkg.id, ct: combo_pkg, sd: rdv_deleted])[0]

      result.report = [
        numRows : titleCount,
        skipped : 0,
        matched : 0,
        partial : 0,
        created : 0,
        retired : 0,
        reviews : 0,
        invalid : 0,
        previous: old_tipp_count
      ]

      if (old_tipp_count > 0) {
        log.debug("OLD TIPPCOUNT: " + old_tipp_count + "  ----> IS UPDATE... ")
        isUpdate = true
      }

      def targetNamespaceTitleIdSerial = pkg_source.getTitleIdSerial()?.getValue()
      def targetNamespaceTitleIdMonograph = pkg_source.getTitleIdMonograph()?.getValue()


      int tippNum = 0
      def tippBatches = []
      boolean cancelled = false

      try {
        for (int offset = 0; offset < titleCount; offset += batchSize) {
          def tipps = null

          //maximum 5 trials to reach WEKB endpoint
          int trials = 1
          do {
            tipps = wekbAPIService.getTIPPSOfPackage(wekbUUID, batchSize, offset)
            trials++
            if (!tipps) {
              log.debug("TIPPS nicht vorhanden --> sleep... Request-Versuch: " + trials )
              sleep(1500)
            }
          } while (!tipps && trials < 6)

          if (tipps) {
            tippBatches.add(tipps)
          }
          else {
            result.result = 'ERROR'
            missedBatches.add(offset)
          }

          def expungeResult = deleteDeletedTippsIfNeeded(tipps, isUpdate)
          log.debug("Deleted " + expungeResult.expunged + " old TIPPS")

          for (tipp in tipps) {
            def targetNamespaceTitleId = null

            if (tippNum == 0) {
              log.debug("Checking first TIPP for duplicates ..")

              def dupes = TitleInstancePackagePlatform.executeQuery('''select uuid
                      from TitleInstancePackagePlatform as tipp
                      where uuid = :uuid
                      and exists (
                        select 1
                        from Combo
                        where type = :ct
                        and toComponent = tipp
                        and fromComponent.id != :pkg
                      )
                      ''', [pkg: pkg.id, ct: combo_pkg, uuid: tipp.uuid])

              if (dupes) {
                cancelled = true
                result.result = 'ERROR'
                result.message = "The first title of this import (${tipp.name}) already exists in another package. Multiple instances of the same external package are not allowed!"
                result.baddata = tipp.uuid
                break
              }
            }


            tippNum++
            log.debug('TIPP ' + tippNum + ": " + tipp)

            if (tipp.status == 'Deleted' || !validTippStatusList.contains(tipp.status)) {
              //in der WEKB gelöschte Titel werden nicht importiert
              log.debug("Title is deleted or status not valid --> SKIP")
              result.report["skipped"]++
              continue
            }

            Map ids = tipp.identifiers?.find { it.namespace == 'title_id' }
            def title_id = null

            if (ids) {
              title_id = ids.value
              log.debug('ids: ' + ids + ", title_id: " + title_id)
            }

            def lang = null

            if (tipp.languages && tipp.languages.size() > 0) {
              lang = tipp.languages.get(0)?.value
            }

            def pubtype = tipp.publicationType

            if (pubtype == 'Serial') {
              targetNamespaceTitleId = targetNamespaceTitleIdSerial
            } else {
              targetNamespaceTitleId = targetNamespaceTitleIdMonograph
            }

            def identifiers = []
            //def validIdentifiers = getValidIdentifiersForPublicationType(tipp.publicationType)

            if (tipp.identifiers && tipp.identifiers.size() > 0) {
              boolean titleIdIsToSet = false

              if (targetNamespaceTitleId) {
                titleIdIsToSet = true

                for (identifier in tipp.identifiers) {
                  if (identifier.namespace && identifier.namespace.equalsIgnoreCase(targetNamespaceTitleId)) {
                    titleIdIsToSet = false
                  }
                }
              }

              for (identifier in tipp.identifiers) {
                String identifierType = null

                if (identifier.namespace) {
                  switch (identifier.namespace) {
                    case "eisbn":
                      if (pubtype == "Monograph") {
                          identifierType = "isbn"
                      }
                      break;
                    case "isbn":
                      if (pubtype == "Monograph") {
                          identifierType = "pisbn"
                      }
                      break;
                    case "title_id":
                      if (titleIdIsToSet) {
                        identifierType = targetNamespaceTitleId
                      }
                      break;
                    default:
                      if (validIdentifierForPubType.get(pubtype).contains(identifier.namespace)) {
                        identifierType = identifier.namespace
                      }
                  }
                }

                if (identifierType) {
                  identifiers << [type: identifierType, value: identifier.value]
                }
              }
            }


            def tipp_map = [
              uuid                       : tipp.uuid?.trim(),
              url                        : tipp.url?.trim(),
              coverageStatements         : tipp.coverage ?: [
                [
                  embargo      : null,
                  coverageDepth: 'Fulltext',
                  coverageNote : null,
                  startDate    : null,
                  startVolume  : null,
                  startIssue   : null,
                  endDate      : null,
                  endVolume    : null,
                  endIssue     : null
                ]
              ],
              importId                   : title_id?.trim(),
              name                       : tipp.name?.trim(),
              publicationType            : tipp.publicationType?.trim(),
              parentPublicationTitleId   : tipp.parentPublicationTitleId?.trim(),
              precedingPublicationTitleId: tipp.precedingPublicationTitleId?.trim(),
              firstAuthor                : tipp.firstAuthor?.trim(),
              publisherName              : tipp.publisherName?.trim(),
              volumeNumber               : tipp.volumeNumber?.trim(),
              editionStatement           : tipp.editionStatement?.trim(),
              dateFirstInPrint           : tipp.dateFirstInPrint?.trim(),
              dateFirstOnline            : tipp.dateFirstOnline?.trim(),
              firstEditor                : tipp.firstEditor?.trim(),
              subjectArea                : tipp.subjectArea?.trim(),
              series                     : tipp.series?.trim(),
              language                   : lang,
              medium                     : tipp.medium?.trim(),
              accessStartDate            : tipp.accessStartDate?.trim(),
              accessEndDate              : tipp.accessEndDate?.trim(),
              lastSeen                   : ingest_systime,
              identifiers                : identifiers,
              pkg                        : [id: pkg.id, uuid: pkg.uuid, name: pkg.name],
              hostPlatform               : [id: pkg_plt.id, uuid: pkg_plt.uuid, name: pkg_plt.name],
              paymentType                : tipp.accessType == "Free" ? "F" : "P"
            ]

            def line_result = saveTippToDB(tipp_map, pkg_plt, pkg, ingestDate)

            result.report[line_result.status]++

            if (tippNum % 50 == 0) {
              def session = sessionFactory.getCurrentSession()
              session.flush()
              session.clear()
            }

          }

          if (cancelled) {
             break
          }

          int progress = (int) ((offset / titleCount) * 100)
          log.debug("++++++++++ progress: " + progress)
          job?.setProgress(progress)

          def session = sessionFactory.getCurrentSession()
          session.flush()
          session.clear()

        }

        def session = sessionFactory.getCurrentSession()
        session.flush()
        session.clear()

        if (!cancelled) {
          // handle Tipp-Status
          //def status_map = ['Current': rdv_current, 'Deleted': rdv_deleted, 'Expected': rdv_expected, 'Retired': rdv_retired]
          log.debug("set TIPP status...")
          tippNum = 0

          for (int i = 0; i < tippBatches.size(); i++) {
            def tipps = tippBatches.get(i)

            for (tipp in tipps) {
              tippNum++
              def importedTipp = TitleInstancePackagePlatform.findByUuid(tipp.uuid)

              if (importedTipp == null) {
                log.debug("Title was not imported --> Skip")
                continue
              }

              def actualTippStatus = RefdataCategory.lookup('KBComponent.Status', tipp.status)

              if (actualTippStatus) {
                importedTipp.setStatus(actualTippStatus)
              }
              else {
                log.error("Unable to process wekb TIPP status value ${tipp.status} for TIPP ${tipp.uuid}!")
              }

              if (tippNum % 50 == 0) {
                session = sessionFactory.getCurrentSession()
                session.flush()
                session.clear()
              }
            }

            session = sessionFactory.getCurrentSession()
            session.flush()
            session.clear()
          }

          log.debug("start Title Matching... ")

          def currentSession = sessionFactory.getCurrentSession()
          currentSession.flush()
          currentSession.clear()

          Job matching_job

          Package.withNewSession {
            matching_job = concurrencyManagerService.createJob { mjob ->
              tippService.matchPackage(pkgInfo.id, mjob)
            }

            Package p = Package.get(pkg.getId())
            matching_job.description = "Package Title Matching".toString()
            matching_job.type = RefdataCategory.lookup('Job.Type', 'PackageTitleMatch')
            matching_job.linkedItem = pkgInfo
            matching_job.message("Starting title match for Package ${p.name}".toString())
            matching_job.startOrQueue()
            matching_job.startTime = new Date()
          }

          if (!async) {
            result.matchingJob = matching_job.get()
          } else {
            result.matchingJob = matching_job.uuid
          }
        }
      }
      catch (grails.validation.ValidationException ve) {
        log.error('Validation error druing wekb package update', e)
        List field_errors = ve.errors.fieldErrors
        result.result = 'ERROR'
        result.exception = e.message

        if (field_errors) {
          field_errors.each { er ->
            if (er.field == 'uuid' && er.code == 'unique') {
              result.errors << [
                message: 'At least one title with the same UUID has already been imported in another package!',
                baddata: er.rejectValue
              ]
            }
            else {
              result.errors << [
                message: 'At least one title failed validation while saving!',
                field: er.field,
                baddata: er.rejectValue
              ]
            }
          }
        }
      }
      catch (Exception e) {
        log.error("Error in updating wekb package ${pkgInfo}", e)
        result.result = 'ERROR'
        result.exception = e.message
      }

      if (job) {
        job.setProgress(100)
        job.endTime = new Date()

        JobResult.withNewTransaction {
          def result_object = JobResult.findByUuid(job.uuid)

          /*if (result.titleMatch) {
              result.titleMatch.rowConflicts = titleMatchConflicts
          } */

          if (!result_object) {
            def job_map = [
              uuid        : (job.uuid),
              description : "External Source Import".toString(),
              resultObject: (result as JSON).toString(),
              type        : (job.type),
              statusText  : (result.result),
              ownerId     : (job.ownerId),
              groupId     : (job.groupId),
              startTime   : (job.startTime),
              endTime     : (job.endTime),
              linkedItemId: (job.linkedItem?.id)
            ]

            def jr = new JobResult(job_map).save(flush: true, failOnError: true)
          }
        }
      }
    }

    return result
  }

  String extractUUIDFromUrlString (String url) {
    String[] tokens = url.split("\\?")[1].split("&")
    Map<String, String> params = new HashMap<String, String>()

    for (String token: tokens) {
      params.put(token.split("=")[0], token.split("=")[1])
    }

    return params.get("uuid")
  }

  def saveTippToDB (tipp, platform, pkg, ingestDate) {
    def result = [status: null, reviewCreated: false]

    if (platform != null) {
      def titleClass = TitleInstance.determineTitleClass(tipp.publicationType)

      if (titleClass) {
        result = upsertTipp(tipp, platform, pkg, ingestDate)
      }
      else {
        log.error("Unable to reference title class!")
      }
    } else {
      log.warn("couldn't resolve platform - title not added.")
      result.status = 'invalid'
    }

    result
  }


  def upsertTipp (tipp_map, platform, pkg, ingestDate) {
    def result = [status: null, reviewCreated: false]
    TitleInstancePackagePlatform tipp = null
    boolean new_coverage = true
    boolean hasChanged = false

    log.debug("upsertTipp " + tipp)

    if ( isUpdate ) {
      // check if Title already exists
      tipp = TitleInstancePackagePlatform.findByUuid(tipp_map.uuid)

      log.debug("isUpdate...")

      //Tipp exists - update if it is not deleted
      if ( tipp ) {
        if ( tipp.getStatus() != rdv_deleted ) {
          tipp.refresh()
          result.status = 'matched'
          log.debug("Tipp wird refresht")
        }
      } else {
        log.debug("Tipp wird neu angelegt...")
        // create new Tipp
        def tipp_fields = [
          uuid: tipp_map.uuid,
          pkg: pkg,
          hostPlatform: platform,
          url: tipp_map.url?.trim(),
          name: tipp_map.publicationTitle?.trim(),
          importId: tipp_map.title_id?.trim()
        ]

        tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_fields)
        result.status = 'created'
        log.debug("Created TIPP ${tipp} with URL ${tipp?.url}")
      }
    } else {
      log.debug("Initialer Import: Tipp wird neu angelegt...")
      def tipp_fields = [
        uuid: tipp_map.uuid,
        pkg: pkg,
        hostPlatform: platform,
        url: tipp_map.url?.trim(),
        name: tipp_map.publicationTitle?.trim(),
        importId: tipp_map.title_id?.trim()
      ]

      tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_fields)
      result.status = 'created'
    }

    if (!tipp.coverageStatements) {
      // log.debug("Create new statement")
    }
    else if (tipp.coverageStatements.size() != tipp_map.coverageStatements.size()) {
      tippService.deleteExistingCoverage(tipp)
    } else {
      boolean mismatched_coverage = false

      tipp_map.coverageStatements.each { ntcs ->
        if (!tippService.existsCoverage(tipp, ntcs)) {
          mismatched_coverage = true
        }
      }

      if (mismatched_coverage) {
        tippService.deleteExistingCoverage(tipp)
      }
      else {
        new_coverage = false
      }
    }

    hasChanged |= tippService.updateTippFields(tipp, tipp_map, null, new_coverage)
    tipp.refresh()


    log.debug("manualUpsertTIPP returning")
    log.debug("TIPP ${tipp.id} info check: ${tipp.name}, ${tipp.url}")

    if (tipp.validate()) {
      if (ingest_systime) {
        log.debug("Update last seen on tipp ${tipp.id} - set to ${ingestDate} (${tipp.lastSeen} -> ${ingest_systime})")
        tippService.updateLastSeen(tipp, ingest_systime)
      }
      else {
        log.debug("Skipping unchanged")
      }

      tipp.save(flush: true)
    }
    else {
      log.debug("Error bei der Tipp-Validierung ...")
      log.error("Validation failed!")
      tipp.errors.allErrors.each {
        log.error("${it}")
      }
    }

    result
  }


  def deleteDeletedTippsIfNeeded ( newTipps, isUpdate ) {
    def result = [status: null, expunged: 0]
    int expunged = 0

    for (newTipp in newTipps) {
      def oldTipp = TitleInstancePackagePlatform.findByUuid(newTipp.uuid)

      if (oldTipp && oldTipp.getStatus() == rdv_deleted) {
        log.debug(" +++ TIPP mit UUID " + newTipp.uuid + " existiert bereits und ist gelöscht ")

        if ( isUpdate ) {
          if (newTipp.status != 'Deleted') {
            log.debug("---> expunged ... ")
            oldTipp.expunge()
            expunged++
          }
        } else {
          // initialer Import und Tipp existiert bereits --> löschen
          log.debug("---> expunged ... ")
          oldTipp.expunge()
          expunged++
        }
      }
    }
    result.expunged = expunged
    def session = sessionFactory.getCurrentSession()
    session.flush()
    session.clear()

    return result
  }


  Map loadIdentifierTargetTypes () {
    Map targetTypes = [:]
    RefdataValue.findAllByOwner(RefdataCategory.findByLabel('IdentifierNamespace.TargetType'))
      .each { refVal ->
        targetTypes.put((refVal.value), refVal)
      }

    return targetTypes
  }

  List getValidIdentifiersForPublicationType (String publicationType) {
    List validIdentifiers = []
    Map mapping = ["Monograph":"Book", "Serial":"Journal", "Database":"Database", "Other":"Other"]
    IdentifierNamespace.findAllByTargetTypeInList([identifierTargetTypes["Title"], identifierTargetTypes[mapping.get(publicationType)]])
      .each {
        ns -> validIdentifiers << ns.value
      }

    return validIdentifiers
  }
}
