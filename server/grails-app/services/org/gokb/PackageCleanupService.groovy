package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import groovy.util.logging.Slf4j

import java.util.regex.Matcher

import org.gokb.cred.*
import org.hibernate.Session

@Slf4j
class PackageCleanupService {

  def tippService
  def autoTimestampEventListener
  def FTUpdateService
  def sessionFactory

  def reactivateReplacedTipps(pid, Job j = null) {
    def result = [result: 'OK', cases: 0, additionalDeletes: 0, total: 0]

    Package.withNewSession { tsession ->
      def obj = Package.get(pid)
      RefdataValue status_retired = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
      RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
      RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
      RefdataValue combo_title = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')
      RefdataValue combo_pkg = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
      User user = j?.ownerId ? User.get(j.ownerId) : null
      CuratoryGroup activeGroup = j?.groupId ? CuratoryGroup.get(j.groupId) : null

      if (obj) {
        boolean more = true
        int batchSize = 50
        int total = obj.currentTitleCount
        int offset = 0
        def qry_str = '''from TitleInstancePackagePlatform as t
                          where exists (
                            select 1 from Combo
                            where toComponent = t
                            and fromComponent = :ti
                            and type = :ct
                          )
                          and exists (
                            select 1 from Combo
                            where toComponent = t
                            and fromComponent = :pkg
                            and type = :cp
                          )
                          order by id'''
        log.debug("Processing ${total} Titles")

        while (more) {
          def batch = obj.getTitles(true, batchSize, offset)

          batch.each { ti ->
            result.total++
            def current_tipps = []
            def retired_tipps = []
            def ti_pkg_tipps = TitleInstancePackagePlatform.executeQuery(qry_str, [cp: combo_pkg, ct: combo_title, pkg: obj, ti: ti])

            ti_pkg_tipps.each { tipp ->
              if (tipp.status == status_current) {
                current_tipps << tipp
              }
              else if (tipp.status == status_retired) {
                retired_tipps << tipp
              }
            }

            if (current_tipps.size() == 1 && retired_tipps.size() > 0) {
              if (current_tipps[0].dateCreated > retired_tipps[0].dateCreated) {
                def duplicate = current_tipps[0]
                def to_reactivate = retired_tipps[0]
                retired_tipps.drop(1)

                if (retired_tipps.size() > 0) {
                  retired_tipps.each { ttd ->
                    ttd.status = status_deleted
                    ttd.save()
                    result.additionalDeletes++
                  }
                }

                tippService.mergeDuplicate(duplicate, to_reactivate, user, activeGroup)
                result.cases++
              }
            }
          }

          offset = offset + batch.size()

          if (offset >= total) {
            more = false
          }

          tsession.flush()
          tsession.clear()
        }
      }

      j?.endTime = new Date()
    }

    result
  }

  public Map generateYearInfoFromNames() {
    Map result = [result: 'OK', changed: 0, invalid: 0]
    Session session = sessionFactory.currentSession

    List candidate_ids = Package.executeQuery("select id from Package where status != :sd and startYear = null and endYear = null", [sd: RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)])
    int ctr = 0

    autoTimestampEventListener.withoutLastUpdated (Package) {
      for (pid in candidate_ids) {
        Package obj = Package.get(pid)
        boolean changed = false
        ctr++

        Matcher groups

        if (groups = obj.name =~ /(before\s|\<)(?<end>(1\d|2[0-2])\d{2})/) {
          // <1990 , before 1990
          obj.startYear = 1800
          obj.endYear = Integer.parseInt(groups.group("end")) - 1
          changed = true
        }
        else if (groups = obj.name =~ /\s\(?(?<start>(1\d|2[0-2])\d{2})(\s?[-\/–]\s?(?<end>(\d{4}|\d{2}))?)?\)?/) {
          // (2020-2022) , 1990/98 , 2024-1
          String start_string = groups.group("start")
          String end_string = groups.group("end")

          obj.startYear = Integer.parseInt(start_string)

          if (!end_string) {
            obj.endYear = Integer.parseInt(start_string)
          }
          else if (end_string.length() == 2) {
            obj.endYear = Integer.parseInt("${start_string.substring(0, 2)}${end_string}".toString())
          }
          else {
            obj.endYear = Integer.parseInt(end_string)
          }

          changed = true
        }
        else if (groups = obj.name =~ /\(\d{2}\/(?<start>\d{4})\s?-\s?\d{2}\/(?<end>\d{4})\)/) {
          // (04/2020 - 08/2021)
          obj.startYear = Integer.parseInt(groups.group("start"))
          obj.endYear = Integer.parseInt(groups.group("end"))
          changed = true
        }

        if (changed) {
          if (obj.validate()) {
            log.debug("Set new values for package '${obj.name} (${obj})' startYear -> ${obj.startYear}, endYear -> ${obj.endYear} ..")
            obj.save(flush: true, failOnError: true)
            FTUpdateService.updateSingleItem(obj)
            result.changed++
          }
          else {
            result.invalid++
          }
        }

        if (ctr % 50 == 0) {
          session.flush()
          session.clear()
        }
      }
    }

    result
  }
}