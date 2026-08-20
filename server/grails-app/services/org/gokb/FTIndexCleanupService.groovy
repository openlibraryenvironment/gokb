package org.gokb

import com.k_int.ESSearchService
import grails.core.GrailsApplication
import org.gokb.cred.TitleInstancePackagePlatform
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

    Map syncTippsBetweenIndexAndDB (def job = null, LocalDateTime updatedSince = null, LocalDateTime updatedTill = null, boolean dryRun) {
        Map result = [result: "OK"]
        int numberUpdatedTippsInPeriod = 0
        int numberCheckedTipps = 0
        int numberNotActualTipps = 0
        int numberNotYetIndexedTipps = 0
        int numberNewIndexedTipps = 0
        List<TitleInstancePackagePlatform> tippsToReindex = new ArrayList<>()

        Date from = null
        Date till = null

        if (!updatedSince) {
            //default to 1 week before, start of day
            LocalDateTime oneWeekBefore = LocalDate.now().minusWeeks(1).atStartOfDay()
            from = Date.from(oneWeekBefore.atZone(ZoneId.systemDefault()).toInstant())
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


        TitleInstancePackagePlatform.withNewSession {
            List<TitleInstancePackagePlatform> tipps = TitleInstancePackagePlatform.executeQuery("select tipp from TitleInstancePackagePlatform as tipp where ( (tipp.lastUpdated > :us OR tipp.dateCreated > :us) AND tipp.lastUpdated <= :ut AND tipp.dateCreated <= :ut) order by tipp.lastUpdated, tipp.id", [us: from, ut: till], [readonly: true])
            numberUpdatedTippsInPeriod = tipps.size()

            log.debug("Checking " + tipps.size() + " TIPPS...")

            for (TitleInstancePackagePlatform tipp: tipps) {
                log.debug("11111: " + tipp.getUuid())
                Map esRepresentation = esSearchService.find([componentType: 'TitleInstancePackagePlatform', uuid: tipp.getUuid(), skipDomainMapping: true])
                Map esTipp = null

                if (esRepresentation.records?.size() != 1) {
                    if (esRepresentation.records?.size() == 0) {
                        numberNotYetIndexedTipps++
                        tippsToReindex.add(tipp)
                        log.info("NOT YET INDEXED: " + tipp.getName() + ": " + tipp.getUuid())
                    }
                }
                else {
                    esTipp = esRepresentation.records.get(0)
                }

                if (esTipp) {
                    numberCheckedTipps++
                    // Date esDate = Date.from(LocalDateTime.parse(esRepresentation?.data?.lastUpdated.get(0)).atStartOfDay(ZoneId.systemDefault()).toInstant())
                    ZonedDateTime zdt = ZonedDateTime.parse(esTipp.lastUpdatedDisplay)
                    Date esDate = Date.from(zdt.toInstant())

                    Date dbDate = tipp.getLastUpdated()

                    // We accept a deviation of 999 milliseconds as loss of precision due to the different Time Formats in DB and Index
                    // dbDate.getTime() should always be >= esDate.getTime
                    long epsilon = Math.abs(dbDate.getTime() - esDate.getTime())
                    if (epsilon < 1000) {
                        //ok, Index Record is up to date
                    } else {
                        numberNotActualTipps++
                        tippsToReindex.add(tipp)
                        log.info("NOT ACTUAL: " + tipp.getName() + ", " + tipp.getUuid() +  ", diff: " + (dbDate.getTime() - esDate.getTime()))
                    }
                }

            }


            log.debug("######## REINDEX " + tippsToReindex.size() + " TIPPS ######################")

            if (!dryRun) {
                // Reindex not-up-to-date Tipps
                Map updateResult = ftUpdateService.updateSpecifiedTippBulk(tippsToReindex, job)
                numberNewIndexedTipps = updateResult.indexed
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
                log.warn("FT Index Cleanup: not all found TIPPs were indexed. Expected number: " + (numberNotYetIndexedTipps + numberNotActualTipps) + ", but was: " + numberNewIndexedTipps )
            }

            if ( numberUpdatedTippsInPeriod != (numberCheckedTipps + numberNotYetIndexedTipps) ) {
                log.warn("FT Index Cleanup: found TIPP with ambiguous OS representation. Expected number: " + numberUpdatedTippsInPeriod + ", but was: " + (numberCheckedTipps + numberNotYetIndexedTipps) )
            }

        }
        return result
    }


}
