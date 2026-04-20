package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import groovy.util.logging.Slf4j

import java.time.*

import org.gokb.cred.*

@Slf4j
class PackageCleanupService {

  def tippService
  def titleAugmentService
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

  public Map revertTitleIds(Long pid, LocalDate date, Job j = null) {
    Map result = [:]

    try {
      def session = sessionFactory.currentSession
      result = processTitleIdCleanup(session, pid, date, j)
    }
    catch (Exception e) {
      log.debug("Failed session lookup", e)

      Package.withNewSession { session ->
        log.debug("revertTitleIds :: creating new session ..")
        result = processTitleIdCleanup(session, pid, date, j)
      }
    }

    result
  }

  private Map processTitleIdCleanup(session, Long pid, LocalDate date, Job job = null) {
    Map result = [result: 'OK', cleanups: 0]
    RefdataValue type_ids = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')
    RefdataValue type_ti_tipps = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')
    RefdataValue type_pkg_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
    log.debug("revertTitleIds :: Processing id links for Package ${pid} between ${java.sql.Date.valueOf(date)} and ${java.sql.Date.valueOf(date.plusDays(1))} ..")

    def deletion_candidates_qry = '''select id, fromComponent.id from Combo as cid
                                      where type = :cti
                                      and dateCreated between :dateStart and :dateEnd
                                      and exists (
                                        select 1 from Combo as ct
                                        where type = :ctt
                                        and fromComponent = cid.fromComponent
                                        and exists (
                                          select 1 from Combo as cp
                                          where type = :ctp
                                          and toComponent = ct.toComponent
                                          and fromComponent.id = :pid
                                        )
                                      )
                                      order by fromComponent.id'''

    Map pars = [
      pid: pid,
      cti: type_ids,
      ctt: type_ti_tipps,
      ctp: type_pkg_tipps,
      dateStart: java.sql.Date.valueOf(date),
      dateEnd: java.sql.Date.valueOf(date.plusDays(1))
    ]

    List delete_candidates = Combo.executeQuery(deletion_candidates_qry, pars)

    def total_ids_qry = '''select id, fromComponent.id from Combo as cid
                            where type = :cti
                            and exists (
                              select 1 from Combo as ct
                              where type = :ctt
                              and fromComponent = cid.fromComponent
                              and exists (
                                select 1 from Combo as cp
                                where type = :ctp
                                and toComponent = ct.toComponent
                                and fromComponent.id = :pid
                              )
                            )
                            order by fromComponent.id'''

    Map total_pars = [
      pid: pid,
      cti: type_ids,
      ctt: type_ti_tipps,
      ctp: type_pkg_tipps
    ]

    List total_ids = Combo.executeQuery(total_ids_qry, total_pars)

    log.debug("revertTitleIds :: Found ${delete_candidates.size()} of ${total_ids.size()} ids to remove ..")

    for (c in delete_candidates) {
      Combo ctd = Combo.get(c[0]).delete(flush: true)
      result.cleanups++
      TitleInstance ti = TitleInstance.get(c[0])

      titleAugmentService.touchTitleTipps(ti, false)

      if (result.cleanups % 50 == 0) {
        session.flush()
        session.clear()
      }
    }

    job?.endTime = new Date()

    result
  }
}