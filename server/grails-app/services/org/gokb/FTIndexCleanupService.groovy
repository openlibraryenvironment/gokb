package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ESSearchService
import grails.core.GrailsApplication
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.TitleInstancePackagePlatform
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class FTIndexCleanupService {

    GrailsApplication grailsApplication
    @Autowired
    ESSearchService esSearchService
    @Autowired
    FTUpdateService ftUpdateService
    SessionFactory sessionFactory

    Map syncTippsBetweenIndexAndDB (ConcurrencyManagerService.Job job = null, LocalDateTime updatedSince = null, LocalDateTime updatedTill = null, boolean dryRun, boolean isStartedFromQuartz) {
        Map result = [result: "OK"]
        int numberUpdatedTippsInPeriod = 0
        int numberCheckedTipps = 0
        int numberNotActualTipps = 0
        int numberNotYetIndexedTipps = 0
        int numberNewIndexedTipps = 0
        List<TitleInstancePackagePlatform> tippsToReindex
        List<Integer> tippIDsToReindex = new ArrayList<>()

        Date from = null
        Date till = null


        TitleInstancePackagePlatform.withNewSession {
            RefdataValue cleanupJobType = RefdataCategory.lookup("Job.Type", "FTIndexCleanupJob")
            RefdataValue tippIndexJobType = RefdataCategory.lookup("Job.Type", "ESTippUpdateJob")

            ScheduledJobControl cleanupJobControl = ScheduledJobControl.findByJobType(cleanupJobType)
            ScheduledJobControl tippIndexJobControl = ScheduledJobControl.findByJobType(tippIndexJobType)
            boolean completed = true

            if (cleanupJobControl) {
                cleanupJobControl.lastEnd = null
            } else {
                cleanupJobControl = new ScheduledJobControl()
                cleanupJobControl.jobType = cleanupJobType
            }

            cleanupJobControl.lastStart = LocalDateTime.now()
            cleanupJobControl.save(flush: true, failOnError: true)

            if (tippIndexJobControl) {
                // try waiting for completion if other indexing-job is running in parallel, max 10 minutes
                long startWaitingTime = new Date().getTime()
                while (tippIndexJobControl.lastStart && tippIndexJobControl.lastEnd == null) {
                    sleep(20 * 1000)
                    if (new Date().getTime() - startWaitingTime > 10 * 60 * 1000) {
                        result.result = "WARNING"
                        result.message = "FT Index Cleanup Job could not start."
                        log.warn("FT Index Cleanup Job could not start because of other concurrent job running.")
                        cleanupJobControl.lastEnd = LocalDateTime.now()
                        cleanupJobControl.save(flush: true, failOnError: true)
                        return result
                    }
                }
            }

            FTUpdateService.tippsRunning = true

            if (!updatedSince) {
                //default is last successful starttime of job, fallback minus 1 week start of day
                if (cleanupJobControl && cleanupJobControl.lastStartComplete) {
                    from = Date.from(cleanupJobControl.lastStartComplete.atZone(ZoneId.systemDefault()).toInstant())
                }
                else {
                    LocalDateTime oneWeekBefore = LocalDate.now().minusWeeks(1).atStartOfDay()
                    from = Date.from(oneWeekBefore.atZone(ZoneId.systemDefault()).toInstant())
                }
            }
            else {
                from = Date.from(updatedSince.atZone(ZoneId.systemDefault()).toInstant())
            }

            if (!updatedTill) {
                till = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant())
            }
            else {
                till = Date.from(updatedTill.atZone(ZoneId.systemDefault()).toInstant())
            }

            log.info("Start Syncing Tipps that were updated between: ... " + from + " - " + till)

            List tippIDs = TitleInstancePackagePlatform.executeQuery("select tipp.id, tipp.uuid, tipp.lastUpdated from TitleInstancePackagePlatform as tipp where ( (tipp.lastUpdated > :us OR tipp.dateCreated > :us) AND tipp.lastUpdated <= :ut AND tipp.dateCreated <= :ut) order by tipp.lastUpdated, tipp.id", [us: from, ut: till], [readonly: true])
            numberUpdatedTippsInPeriod = tippIDs.size()

            log.debug("Checking " + numberUpdatedTippsInPeriod + " TIPPS...")

            int count = 0

            for (List tippStub: tippIDs) {
                count++
                // id: tippStub[0], uuid: tippStub[1], lastUpdated: tippStub[2]
                Map esRepresentation = esSearchService.find([componentType: 'TitleInstancePackagePlatform', uuid: tippStub[1], skipDomainMapping: true])
                Map esTipp = null

                if (esRepresentation.records?.size() != 1) {
                    if (esRepresentation.records?.size() == 0) {
                        numberNotYetIndexedTipps++
                        tippIDsToReindex.add(tippStub[0])
                        log.info("NOT YET INDEXED: " + tippStub[1] )
                    }
                    else {
                        log.info("AMBIGUOUS Tipps in Index: " + esRepresentation.records)
                    }
                }
                else {
                    esTipp = esRepresentation.records.get(0)
                }

                if (esTipp) {
                    numberCheckedTipps++
                    ZonedDateTime zdt = ZonedDateTime.parse(esTipp.lastUpdatedDisplay)
                    Date esDate = Date.from(zdt.toInstant())

                    Date dbDate = tippStub[2]

                    // We accept a deviation of 999 milliseconds as loss of precision due to the different Time Formats in DB and Index
                    // dbDate.getTime() should always be >= esDate.getTime, however we use Math.abs for safety
                    long epsilon = Math.abs(dbDate.getTime() - esDate.getTime())
                    if (epsilon < 1000) {
                        //ok, Index Record is up to date
                    } else {
                        numberNotActualTipps++
                        tippIDsToReindex.add(tippStub[0])
                        // log.info("NOT ACTUAL: " + tipp.getName() + ", " + tipp.getUuid() +  ", diff: " + (dbDate.getTime() - esDate.getTime()))
                    }
                }


                if ( !tippIDsToReindex.isEmpty() && (tippIDsToReindex.size() % 100 == 0 || count == numberUpdatedTippsInPeriod) ) {
                    if (!dryRun) {
                        tippsToReindex = TitleInstancePackagePlatform.executeQuery("select tipp from TitleInstancePackagePlatform as tipp where tipp.id IN :ids", [ids: tippIDsToReindex], [readonly: true])
                        // Reindex not-up-to-date Tipps
                        Map updateResult = ftUpdateService.updateSpecifiedTippBulk(tippsToReindex, job)
                        if (updateResult.result != "OK") {
                            completed = false
                        }
                        numberNewIndexedTipps += updateResult.indexed
                        tippIDsToReindex = new ArrayList<>()

                        sessionFactory.getCurrentSession().clear()
                    }
                }

            }

            result.report = [
                    periodStart: from.toString(),
                    periodEnd: till.toString(),
                    numberUpdatedTippsInPeriod: numberUpdatedTippsInPeriod,
                    numberCheckedTipps: numberCheckedTipps,
                    numberNotActualTipps: numberNotActualTipps,
                    numberNotYetIndexedTipps: numberNotYetIndexedTipps,
                    numberNewIndexedTipps: numberNewIndexedTipps
            ]

            log.info("FT Index Cleanup Result: " + result)

            if ( (numberNotYetIndexedTipps + numberNotActualTipps) != numberNewIndexedTipps ) {
                completed = false
                log.warn("FT Index Cleanup: not all found TIPPs were indexed. Expected number: " + (numberNotYetIndexedTipps + numberNotActualTipps) + ", but was: " + numberNewIndexedTipps )
            }

            if ( numberUpdatedTippsInPeriod != (numberCheckedTipps + numberNotYetIndexedTipps) ) {
                log.warn("FT Index Cleanup: found TIPPs with ambiguous OS representation. Expected number: " + numberUpdatedTippsInPeriod + ", but was: " + (numberCheckedTipps + numberNotYetIndexedTipps) )
            }

            FTUpdateService.tippsRunning = false

            cleanupJobControl.lastEnd = LocalDateTime.now()
            if (completed) {
                cleanupJobControl.lastStartComplete = cleanupJobControl.lastStart
                cleanupJobControl.lastEndComplete = cleanupJobControl.lastEnd
            }
            cleanupJobControl.save(flush: true, failOnError: true)

        }
        return result
    }


}
