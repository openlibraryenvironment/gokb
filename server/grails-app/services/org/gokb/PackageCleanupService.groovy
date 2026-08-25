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
  def FTUpdateService
  def sessionFactory

  public Map reactivateReplacedTipps(pid, Job j = null) {
    Map result = [result: 'OK', cases: 0, additionalDeletes: 0, total: 0]

    Package.withNewSession { tsession ->
      Package obj = Package.get(pid)
      RefdataValue status_retired = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED)
      RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
      RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
      User user = j?.ownerId ? User.get(j.ownerId) : null
      CuratoryGroup activeGroup = j?.groupId ? CuratoryGroup.get(j.groupId) : null

      if (obj) {
        boolean more = true
        int batchSize = 50
        int total = obj.currentTitleCount
        int offset = 0
        String qry_str = '''from TitleInstancePackagePlatform as t
                          where t.title = :ti
                          and t.pkg = :pkg
                          order by id'''

        log.debug("Processing ${total} Titles")

        while (more) {
          List batch = obj.getTitles(true, batchSize, offset)

          batch.each { ti ->
            result.total++
            List current_tipps = []
            List retired_tipps = []
            List ti_pkg_tipps = TitleInstancePackagePlatform.executeQuery(qry_str, [pkg: obj, ti: ti])

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
                TitleInstancePackagePlatform duplicate = current_tipps[0]
                TitleInstancePackagePlatform to_reactivate = retired_tipps[0]
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
    RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)
    RefdataValue content_type_book = RefdataCategory.lookup("Package.ContentType", "Book")

    List candidate_ids = Package.executeQuery("select id from Package where status != :sd and contentType = :ctb and startYear = null and endYear = null", [sd: status_deleted, ctb: content_type_book])
    int ctr = 0

    for (pid in candidate_ids) {
      Package obj = Package.get(pid)
      boolean changed = false
      Integer new_start
      Integer new_end
      ctr++

      Matcher groups

      if (groups = obj.name =~ /(before\s|\<)(?<end>(1\d|2[0-2])\d{2})/) {
        // <1990 , before 1990
        new_start = 1800
        new_end = Integer.parseInt(groups.group("end")) - 1

        changed = true
      }
      else if (groups = obj.name =~ /\s\(?(?<start>(1\d|2[0-2])\d{2})(\s?[-\/–]\s?(?<end>(\d{4}|\d{2}))?)?\)?/) {
        // (2020-2022) , 1990/98 , 2024-1
        String start_string = groups.group("start")
        String end_string = groups.group("end")

        new_start = Integer.parseInt(start_string)

        if (!end_string) {
          new_end = Integer.parseInt(start_string)
        }
        else if (end_string.length() == 2) {
          new_end = Integer.parseInt("${start_string.substring(0, 2)}${end_string}".toString())
        }
        else {
          new_end = Integer.parseInt(end_string)
        }

        changed = true
      }
      else if (groups = obj.name =~ /\(\d{2}\/(?<start>\d{4})\s?-\s?\d{2}\/(?<end>\d{4})\)/) {
        // (04/2020 - 08/2021)
        new_start = Integer.parseInt(groups.group("start"))
        new_end = Integer.parseInt(groups.group("end"))
        changed = true
      }

      if (changed) {
        obj.startYear = new_start
        obj.endYear = new_end

        if (!obj.validate()) {
          obj.discard()

          result.invalid++
        }
        else {
          obj.discard()

          log.debug("Set new values for package '${obj}' startYear -> ${new_start}, endYear -> ${new_end} ..")
          Package.executeUpdate("update Package set startYear = :start, endYear = :end where id = :oid", [start: new_start, end: new_end, oid: obj.id])

          // FTUpdateService.updateSingleItem(obj)
          result.changed++
        }
      }

      if (ctr % 50 == 0) {
        session.flush()
        session.clear()
      }
    }

    result
  }
}
