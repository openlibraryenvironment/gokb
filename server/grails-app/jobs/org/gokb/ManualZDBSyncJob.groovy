package org.gokb

import grails.gorm.transactions.Transactional
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.RefdataCategory

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class ManualZDBSyncJob {


    static final String query = '''from JournalInstance as ti
                              where ti.status = :current
                              and not exists (
                                    Select ci from Combo as ci
                                    where ci.type = :ctype
                                    and ci.status = :cstatus
                                    and ci.fromComponent = ti
                                    and ci.toComponent.namespace = :ns
                                  )
                                  and exists (
                                    Select ci from Combo as ci
                                    where ci.type = :ctype
                                    and ci.status = :cstatus
                                    and ci.fromComponent = ti
                                    and ci.toComponent.namespace IN (:issns)
                                  )'''

    // ti.dateCreated > :lastRun
    //                                or (

    @Transactional
    def execute() {

        def status_current = RefdataCategory.lookup("KBComponent.Status", "Current")
        def idComboType = RefdataCategory.lookup("Combo.Type", "KBComponent.Ids")
        def combo_active = RefdataCategory.lookup("Combo.Status", "Active")
        def zdbNs = IdentifierNamespace.findByValue('zdb')
        def issnNs = []
        issnNs << IdentifierNamespace.findByValue('issn')
        issnNs << IdentifierNamespace.findByValue('eissn')
        int offset = 0
        Instant start = Instant.now()
        ZonedDateTime zdt = ZonedDateTime.ofInstant(start, ZoneId.systemDefault()).minus(1, ChronoUnit.HOURS)
        Date startDate = Date.from(zdt.toInstant())

        def qry_params = [
                current: status_current,
                ctype: idComboType,
                cstatus: combo_active,
                ns: zdbNs,
                issns: issnNs
        ]

        def count_journals_without_zdb_id = JournalInstance.executeQuery("select count(ti.id) ${query}".toString(), qry_params)[0]
        def journals_without_zdb_id = JournalInstance.executeQuery("select ti.id ${query}".toString(), qry_params)

        log.debug("111: " + count_journals_without_zdb_id)
        log.debug("222: " + journals_without_zdb_id)



    }


}
