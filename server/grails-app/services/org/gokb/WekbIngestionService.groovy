package org.gokb

import com.k_int.ConcurrencyManagerService
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.Source
import org.gokb.cred.TIPPCoverageStatement
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.hibernate.Session
import org.hibernate.SessionFactory

import java.time.LocalDate

@Transactional
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
    def rdv_current
    def rdv_deleted
    def rdv_expected
    def rdv_retired
    SessionFactory sessionFactory
    ConcurrencyManagerService concurrencyManagerService

    def startTitleImport (pkgInfo, Source pkg_source, Platform pkg_plt, Org pkg_prov, Long title_ns, org.gokb.cred.Package pkg) {
        def result = [status: 'ok']
        long startTime = System.currentTimeMillis()
        ingest_systime = startTime
        def ingestDate = LocalDate.now().toString()
        int batchSize = 100

        rdv_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
        rdv_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
        rdv_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')
        rdv_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')

        String sourceUrl = pkg_source?.url
        String wekbUUID = extractUUIDFromUrlString(sourceUrl)

        // pkgInfo = [name: p.name, type: "Package", id: p.id, uuid: p.uuid]

        def packageInfo = wekbAPIService.getPackageByUuid(wekbUUID)
        int titleCount = packageInfo[0]?.titleCount

        isUpdate = false
        int old_tipp_count = TitleInstancePackagePlatform.executeQuery('select count(*) ' +
                'from TitleInstancePackagePlatform as tipp, Combo as c ' +
                'where c.fromComponent.id=:pkg and c.toComponent=tipp and tipp.status = :sc',
                [pkg: pkg.id, sc: RefdataCategory.lookup('KBComponent.Status', 'Current')])[0]

        result.report = [numRows: titleCount, skipped: 0, matched: 0, partial: 0, created: 0, retired: 0, reviews: 0, invalid: 0, previous: old_tipp_count]

        if (old_tipp_count > 0) {
            log.debug("OLD TIPPCOUNT: " + old_tipp_count + "  ----> IS UPDATE... ")
            isUpdate = true
        }

        def targetNamespaceTitleId =  pkg_source.getTargetNamespace()?.getValue()

        int tippNum = 0
        def tippBatches = []

        for(int offset = 0; offset < titleCount; offset += batchSize) {
            def tipps = wekbAPIService.getTIPPSOfPackage(wekbUUID, batchSize, offset)
            log.debug("#### " + tipps)
            tippBatches.add(tipps)

            def expungeResult = deleteDeletedTippsIfNeeded(tipps, isUpdate)
            log.debug("Deleted " + expungeResult.expunged + " old TIPPS")

            for (tipp in tipps) {

                tippNum++
                log.debug('TIPP ' + tippNum + ": " + tipp)

                if (tipp.status == 'Deleted') {
                    //in der WEKB gelöschte Titel werden nicht importiert
                    log.debug("Title is deleted --> SKIP")
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


                def identifiers = []
                if (tipp.identifiers && tipp.identifiers.size() > 0) {
                    boolean titleIdIsToSet = false
                    if(targetNamespaceTitleId){
                        titleIdIsToSet = true
                        log.debug("+++++++++++++ TITLE ID TARGET: TRUE ++++++++++++++++++")
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
                                    identifierType = "isbn"
                                    break;
                                case "isbn":
                                    identifierType = "pisbn"
                                    break;
                                case "title_id":
                                    if (titleIdIsToSet) {
                                        identifierType = targetNamespaceTitleId
                                    }
                                    break;
                                default:
                                    identifierType = identifier.namespace
                            }
                        }
                        if (identifierType) {
                            identifiers << [type: identifierType, value: identifier.value]
                        }
                    }
                }

               log.debug("ALL IDENTIFIERS: " + identifiers)


                def tipp_map = [
                        uuid                       : tipp.uuid?.trim(),
                        url                        : tipp.url?.trim(),
                        coverageStatements         : [
                                tipp.coverage ?: [
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


                // log.debug("TIPP-MAP : " + tipp_map)

                //TODO: zu Testzwecken auskommentiert
                def line_result = saveTippToDB(tipp_map, pkg_plt, pkg, ingestDate)

                // TODO: result
                //result.report[line_result.status]++

                log.debug('----------------------------------------------------------------------')

                if (tippNum % 50 == 0) {
                    def session = sessionFactory.getCurrentSession()
                    session.flush()
                    session.clear()
                }

            }
            def session = sessionFactory.getCurrentSession()
            session.flush()
            session.clear()

        }// end 1. Durchgang Save

        def session = sessionFactory.getCurrentSession()
        session.flush()
        session.clear()


        // handle Tipp-Status
        def status_map = ['Current': rdv_current, 'Deleted': rdv_deleted, 'Expected': rdv_expected, 'Retired': rdv_retired]
        log.debug("+++ set Title Status +++++++++++++")
        tippNum = 0
        for(int i = 0; i < tippBatches.size(); i++) {
            def tipps = tippBatches.get(i)
            for (tipp in tipps) {
                tippNum++
                def importedTipp = TitleInstancePackagePlatform.findByUuid(tipp.uuid)
                if (importedTipp == null) {
                    log.debug("Title was not imported --> Skip")
                    continue
                }
                log.debug("importedTipp.status: " + importedTipp.getStatus() + ", newTipp.status: " + tipp.status)
                def actualTippStatus = RefdataCategory.lookup('KBComponent.Status', tipp.status)
                importedTipp.setStatus(actualTippStatus)
                //importedTipp.setStatus(status_map.get(tipp.status))

                log.debug("----------------------------------- ")
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
        log.debug(" " + currentSession.toString())
        currentSession.flush()
        currentSession.clear()


        def matching_job = concurrencyManagerService.createJob { mjob ->
            tippService.matchPackage(pkgInfo.id, mjob)
        }

        currentSession.clear()

        Package.withNewSession {
            Package p = Package.get(pkg.getId())
            matching_job.description = "Package Title Matching".toString()
            matching_job.type = RefdataCategory.lookup('Job.Type', 'PackageTitleMatch')
            matching_job.linkedItem = pkgInfo
            matching_job.message("Starting title match for Package ${p.name}".toString())
            matching_job.startOrQueue()
            matching_job.startTime = new Date()
        }

        /* if (!async) {
            result.matchingJob = matching_job.get()
        }
        else {
            result.matchingJob = matching_job.uuid
        } */


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
                result = upsertTipp(tipp,
                        platform,
                        pkg,
                        ingestDate
                )
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

        log.debug("upsertTipp " + tipp)

        if ( isUpdate ) {

            // check if Title already exists
            tipp = TitleInstancePackagePlatform.findByUuid(tipp_map.uuid)

            log.debug("isUpdate...")

            //Tipp exists - update if it is not deleted
            if ( tipp ) {

                // result.status = 'matched'
                if ( tipp.getStatus() != rdv_deleted ) {
                    tipp.refresh()
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

        }

        tipp = tippService.updateTippFields(tipp, tipp_map, null, new_coverage)
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

                // falls oldTipp gelöscht und newTipp.status != deleted
                if ( isUpdate ) {
                    // TODO: ist das überhaupt nötig oder kann das existierende TIPP schlicht geupdatet werden?
                    // TODO: was ist mit neuen TIPPS, deren Status zuvor nicht "Current" war (retired z.B.)
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

}
