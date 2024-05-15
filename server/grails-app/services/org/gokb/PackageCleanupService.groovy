package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import groovy.util.logging.Slf4j

import org.gokb.cred.*

@Slf4j
class PackageCleanupService {

  def tippService

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
                          )'''
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
}