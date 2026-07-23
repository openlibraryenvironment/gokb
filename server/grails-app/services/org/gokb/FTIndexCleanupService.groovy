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

    def  syncTippsBetweenIndexAndDB (def job = null, LocalDateTime updatedSince = null) {
        Map result = [result: "OK"]
        int numberUpdatedTippsInPeriod = 0
        int numberCheckedTipps = 0
        int numberNotActualTipps = 0
        int numberNewIndexedTipps = 0
        List<TitleInstancePackagePlatform> tippsToReindex = new ArrayList<>()

        Date from = null

        if (!updatedSince) {
            //default to 1 week before, start of day
            LocalDateTime oneWeekBefore = LocalDate.now().minusWeeks(1).atStartOfDay()
            from = Date.from(oneWeekBefore.atZone(ZoneOffset.UTC).toInstant())
        }
        else {
            from = Date.from(updatedSince.atZone(ZoneOffset.UTC).toInstant())
        }
        log.debug("Start Syncing Tipps that were updated since: ... " + updatedSince)


        TitleInstancePackagePlatform.withNewSession {
            List<TitleInstancePackagePlatform> tipps = TitleInstancePackagePlatform.executeQuery("select tipp from TitleInstancePackagePlatform as tipp where (tipp.lastUpdated > :us OR tipp.dateCreated > :us) order by tipp.lastUpdated, tipp.id", [us: from], [readonly: true])
            numberUpdatedTippsInPeriod = tipps.size()

            log.debug("#### " + tipps)

            for (TitleInstancePackagePlatform tipp: tipps) {
                //log.debug("TIPP: " + tipp)

                Map esRepresentation = esSearchService.find([componentType: 'TitleInstancePackagePlatform', uuid: tipp.getUuid(), skipDomainMapping: true])
                Map esTipp = null
                if (esRepresentation.records?.size() != 1) {

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

                    //Due to the fact that in OS we have no milliseconds, we accept a deviation of 999 milliseconds
                    // dbDate.getTime() should always be >= esDate.getTime
                    long epsilon = Math.abs(dbDate.getTime() - esDate.getTime())
                    if (epsilon < 1000) {
                        //ok, Index Record is up to date
                    } else {
                        numberNotActualTipps++
                        tippsToReindex.add(tipp)
                        log.debug("NOT ACTUAL: " + tipp.getName() + ": " + (dbDate.getTime() - esDate.getTime()))
                        log.debug("11111: " + tipp)
                        log.debug("22222: " + esTipp)
                    }
                }

            }


            log.debug("######## REINDEX " + tippsToReindex.size() + " TIPPS ######################")

            // Reindex not-up-to-date Tipps
            ftUpdateService.updateSpecifiedTippBulk(tippsToReindex, job)

            result.report = [
                    periodStart: "",
                    periodEnd: "",
                    numberUpdatedTippsInPeriod: numberUpdatedTippsInPeriod,
                    numberCheckedTipps: numberCheckedTipps,
                    numberNotActualTipps: numberNotActualTipps,
                    numberNewIndexedTipps: numberNewIndexedTipps
            ]
            log.debug("Result: " + result)

        }
        return result
    }


}
