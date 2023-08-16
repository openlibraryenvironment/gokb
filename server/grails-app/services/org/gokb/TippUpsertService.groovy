package org.gokb

import com.k_int.ClassUtils

import grails.converters.JSON
import grails.gorm.transactions.*

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

import org.gokb.GOKbTextUtils
import org.gokb.cred.*

class TippUpsertService {

  def dateFormatService
  def tippService

  /**
   * Create a new TIPP being mindful of the need to create TIPLs
   */

  public TitleInstancePackagePlatform tiplAwareCreate(tipp_fields = [:]) {
    def tipp_status = tipp_fields.status ? RefdataCategory.lookup('KBComponent.Status', tipp_fields.status) : null
    def tipp_editstatus = tipp_fields.editStatus ? RefdataCategory.lookup('KBComponent.EditStatus', tipp_fields.editStatus) : null
    def tipp_language = tipp_fields.language ? RefdataCategory.lookup('KBComponent.Language', tipp_fields.language) : null
    def result = new TitleInstancePackagePlatform(uuid: tipp_fields.uuid,
                                                  status: tipp_status,
                                                  editStatus: tipp_editstatus,
                                                  name: tipp_fields.name,
                                                  language: tipp_language,
                                                  url: tipp_fields.url).save(failOnError: true, flush:true)

    if (result) {

      result.hostPlatform = tipp_fields.hostPlatform
      result.pkg = tipp_fields.pkg

      if (tipp_fields.title) {
        result.title = tipp_fields.title

        TitleInstancePlatform.ensure(tipp_fields.title, tipp_fields.hostPlatform, tipp_fields.url)
      }
    }
    else {
      log.error("TIPP creation failed!")
    }

    result
  }

  @Transactional
  public TitleInstancePackagePlatform upsertDTO(tipp_dto, def user = null) {
    def result = null
    log.debug("upsertDTO(${tipp_dto})")
    def pkg = null
    def plt = null
    def ti = null

    if (tipp_dto.pkg || tipp_dto.package) {
      def pkg_info = tipp_dto.package ?: tipp_dto.pkg

      if (pkg_info instanceof Map) {
        pkg = Package.get(pkg_info.id ?: pkg_info.internalId)
      }
      else {
        pkg = Package.get(pkg_info)
      }

      log.debug("Package lookup: ${pkg}")
    }

    if (tipp_dto.hostPlatform || tipp_dto.platform) {
      def plt_info = tipp_dto.hostPlatform ?: tipp_dto.platform

      if (plt_info instanceof Map) {
        plt = Platform.get(plt_info.id ?: plt_info.internalId)
      }
      else {
        plt = Platform.get(plt_info)
      }

      log.debug("Platform lookup: ${plt}")
    }

    if (tipp_dto.title) {
      def title_info = tipp_dto.title

      if (title_info instanceof Map) {
        ti = TitleInstance.get(title_info.id ?: title_info.internalId)
      }
      else {
        ti = TitleInstance.get(title_info)
      }

      log.debug("Title lookup: ${ti}")
    }

    def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    def status_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    def trimmed_url = tipp_dto.url ? tipp_dto.url.trim() : null
    def curator = pkg?.curatoryGroups?.size() > 0 ? (user.adminStatus || user.curatoryGroups*.id.intersect(pkg?.curatoryGroups*.id)) : true
    def tipp
    if (pkg && plt && curator) {
      log.debug("See if we already have a tipp")

      def uuid_tipp = tipp_dto.uuid ? TitleInstancePackagePlatform.findByUuid(tipp_dto.uuid) : null
      tipp = null

      log.debug("UUID result: ${uuid_tipp} for ${tipp_dto.uuid}")

      if (uuid_tipp) {
        if (uuid_tipp.pkg == pkg && uuid_tipp.hostPlatform == plt && (!ti || uuid_tipp.title == ti)) {
          tipp = uuid_tipp
        }
        else {
          log.warn("TIPP matched by ID has different links! (incoming: ${pkg}, ${plt} - match: ${uuid_tipp.pkg}, ${uuid_tipp.hostPlatform})")
        }
      }

      def tipps = []

      if (tipps.size() == 0 && ti) {
        tipps = TitleInstancePackagePlatform.executeQuery('select tipp from TitleInstancePackagePlatform as tipp, Combo as pkg_combo, Combo as title_combo, Combo as platform_combo  ' +
          'where pkg_combo.toComponent=tipp and pkg_combo.fromComponent = :pkg ' +
          'and platform_combo.toComponent=tipp and platform_combo.fromComponent = :plt ' +
          'and title_combo.toComponent=tipp and title_combo.fromComponent = :ti ' +
          'and tipp.status != :sd',
          [pkg: pkg, plt: plt, ti: ti, sd: status_deleted])
      }

      if (!tipp) {
        switch (tipps.size()) {
          case 1:
            log.debug("found")

            if (trimmed_url && trimmed_url.size() > 0) {
              if (!tipps[0].url || tipps[0].url == trimmed_url) {
                tipp = tipps[0]
              }
              else {
                log.debug("matched tipp has a different url..")
              }
            }
            else {
              tipp = tipps[0]
            }
            break;
          case 0:
            log.debug("not found");

            break;
          default:
            if (trimmed_url && trimmed_url.size() > 0) {
              tipps = tipps.findAll { !it.url || it.url == trimmed_url }
              log.debug("found ${tipps.size()} tipps for URL ${trimmed_url}")
            }

            def cur_tipps = tipps.findAll { it.status == status_current }
            def ret_tipps = tipps.findAll { it.status == status_retired }

            if (cur_tipps.size() > 0) {
              tipp = cur_tipps[0]

              if (cur_tipps.size() > 1) {
                log.debug("found ${cur_tipps.size()} current TIPPs!")
              }
            }
            else if (ret_tipps.size() > 0) {
              tipp = ret_tipps[0]

              if (ret_tipps.size() > 1) {
                log.debug("found ${ret_tipps.size()} retired TIPPs!")
              }
            }
            else {
              log.debug("None of the matched TIPPs are 'Current' or 'Retired'!")
            }
            break;
        }
      }

      if (!tipp) {
        log.debug("Creating new TIPP..")
        def tmap = [
            'pkg'         : pkg,
            'title'       : ti,
            'hostPlatform': plt,
            'url'         : trimmed_url,
            'uuid'        : (tipp_dto.uuid ?: null),
            'status'      : (tipp_dto.status ?: null),
            'name'        : (tipp_dto.name ?: null),
            'editStatus'  : (tipp_dto.editStatus ?: null),
            'language'    : (tipp_dto.language ?: null),
            'importId'    : (tipp_dto.titleId ? (tipp_dto.importId ?: null) : null)
        ]

        tipp = tiplAwareCreate(tmap)
        // Hibernate problem

        if (!tipp) {
          log.error("TIPP creation failed!")
        }
      }
      else if (ti) {
        TitleInstancePlatform.ensure(ti, plt, trimmed_url)
      }
    }

    if (tipp) {
      def changed = false

      if (tipp.isRetired() && tipp_dto.status == "Current") {
        if (tipp.accessEndDate) {
          tipp.accessEndDate = null
        }

        changed = true
      }

      if (tipp_dto.paymentType) {
        if (tipp_dto.paymentType instanceof String) {
          def payment_statement

          if (tipp_dto.paymentType == 'P') {
            payment_statement = 'Paid'
          }
          else if (tipp_dto.paymentType == 'F') {
            payment_statement = 'OA'
          }
          else {
            payment_statement = tipp_dto.paymentType
          }

          def payment_ref = RefdataCategory.lookup("TitleInstancePackagePlatform.PaymentType", payment_statement)

          if (payment_ref) tipp.paymentType = payment_ref
        }
        else if (tipp_dto.paymentType instanceof Integer) {
          def int_rdv = RefdataValue.get(tipp_dto.paymentType)

          if (int_rdv?.owner.label == 'TitleInstancePackagePlatform.PaymentType') {
            tipp.paymentType = int_rdv
          }
        }
      }

      changed |= ClassUtils.setStringIfDifferent(tipp, 'url', trimmed_url)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'name', tipp_dto.name)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'firstAuthor', tipp_dto.firstAuthor)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'firstEditor', tipp_dto.firstEditor)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'publisherName', tipp_dto.publisherName)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'volumeNumber', tipp_dto.volumeNumber)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'series', tipp_dto.series)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'subjectArea', tipp_dto.subjectArea)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'editionStatement', tipp_dto.editionStatement)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'parentPublicationTitleId', tipp_dto.parentPublicationTitleId)
      changed |= ClassUtils.setStringIfDifferent(tipp, 'precedingPublicationTitleId', tipp_dto.precedingPublicationTitleId)
      changed |= ClassUtils.updateDateField(tipp_dto.accessStartDate, tipp, 'accessStartDate')
      changed |= ClassUtils.updateDateField(tipp_dto.accessEndDate, tipp, 'accessEndDate')
      changed |= ClassUtils.updateDateField(tipp_dto.dateFirstInPrint, tipp, 'dateFirstInPrint')
      changed |= ClassUtils.updateDateField(tipp_dto.dateFirstOnline, tipp, 'dateFirstOnline')
      changed |= ClassUtils.updateDateField(tipp_dto.lastChangedExternal, tipp, 'lastChangedExternal')
      changed |= ClassUtils.setRefdataIfPresent(tipp_dto.medium, tipp, 'medium', TitleInstancePackagePlatform.RD_MEDIUM)
      changed |= ClassUtils.setRefdataIfPresent(tipp_dto.publicationType, tipp, 'publicationType', 'TitleInstancePackagePlatform.PublicationType')
      changed |= ClassUtils.setRefdataIfPresent(tipp_dto.language, tipp, 'language')
      changed |= ClassUtils.setRefdataIfPresent(tipp_dto.status, tipp, 'status')

      if (tipp_dto.coverageStatements && !tipp_dto.coverage) {
        tipp_dto.coverage = tipp_dto.coverageStatements
      }

      if (!tipp.importId && (tipp_dto.importId || tipp_dto.titleId)) {
        tipp.importId = tipp_dto.importId ?: tipp_dto.titleId
      }

      def stale_coverage_ids = tipp.coverageStatements.collect { it.id }

      tipp_dto.coverage.each { c ->
        def parsedStart = GOKbTextUtils.completeDateString(c.startDate)
        def parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)
        def cs_match = false
        def conflict = false
        def startAsDate = (parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
        def endAsDate = (parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
        def conflicting_statements = []

        tipp.coverageStatements?.each { tcs ->
          if (c.id && tcs.id == c.id) {
            changed |= ClassUtils.setStringIfDifferent(tcs, 'startIssue', c.startIssue)
            changed |= ClassUtils.setStringIfDifferent(tcs, 'startVolume', c.startVolume)
            changed |= ClassUtils.setStringIfDifferent(tcs, 'endVolume', c.endVolume)
            changed |= ClassUtils.setStringIfDifferent(tcs, 'endIssue', c.endIssue)
            changed |= ClassUtils.setStringIfDifferent(tcs, 'embargo', c.embargo)
            changed |= ClassUtils.setStringIfDifferent(tcs, 'coverageNote', c.coverageNote)
            changed |= ClassUtils.updateDateField(parsedStart, tcs, 'startDate')
            changed |= ClassUtils.updateDateField(parsedEnd, tcs, 'endDate')
            changed |= ClassUtils.setRefdataIfPresent(c.coverageDepth, tipp, 'coverageDepth', 'TIPPCoverageStatement.CoverageDepth')

            cs_match = true
            stale_coverage_ids.removeAll { it == tcs.id }
          }
          else if (!cs_match) {
            if (!tcs.endDate && !endAsDate) {
              conflict = true
            }
            else if (tcs.startVolume && tcs.startVolume == c.startVolume) {
              log.debug("Matched CoverageStatement by startVolume")
              cs_match = true
            }
            else if (tcs.startDate && tcs.startDate == startAsDate) {
              log.debug("Matched CoverageStatement by startDate")
              cs_match = true
            }
            else if (!tcs.startVolume && !tcs.startDate && !tcs.endVolume && !tcs.endDate) {
              log.debug("Matched CoverageStatement with unspecified values")
              cs_match = true
            }
            else if (tcs.startDate && tcs.endDate) {
              if (startAsDate && startAsDate > tcs.startDate && startAsDate < tcs.endDate) {
                conflict = true
              }
              else if (endAsDate && endAsDate > tcs.startDate && endAsDate < tcs.endDate) {
                conflict = true
              }
            }

            if (conflict) {
              conflicting_statements.add(tcs.id)
            }
            else if (cs_match) {
              changed |= ClassUtils.setStringIfDifferent(tcs, 'startIssue', c.startIssue)
              changed |= ClassUtils.setStringIfDifferent(tcs, 'startVolume', c.startVolume)
              changed |= ClassUtils.setStringIfDifferent(tcs, 'endVolume', c.endVolume)
              changed |= ClassUtils.setStringIfDifferent(tcs, 'endIssue', c.endIssue)
              changed |= ClassUtils.setStringIfDifferent(tcs, 'embargo', c.embargo)
              changed |= ClassUtils.setStringIfDifferent(tcs, 'coverageNote', c.coverageNote)
              changed |= ClassUtils.updateDateField(parsedStart, tcs, 'startDate')
              changed |= ClassUtils.updateDateField(parsedEnd, tcs, 'endDate')
              changed |= ClassUtils.setRefdataIfPresent(c.coverageDepth, tcs, 'coverageDepth', 'TIPPCoverageStatement.CoverageDepth')

              stale_coverage_ids.removeAll { it == tcs.id }
            }
          }
          else {
            log.debug("Matched new coverage ${c} on multiple existing coverages!")
          }
        }

        for (def cst : conflicting_statements) {
          tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(cst))
        }

        if (!cs_match) {

          def cov_depth = null

          if (c.coverageDepth instanceof String) {
            cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', c.coverageDepth) ?: RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', "Fulltext")
          }
          else if (c.coverageDepth instanceof Integer) {
            cov_depth = RefdataValue.get(c.coverageDepth)
          }
          else if (c.coverageDepth instanceof Map) {
            if (c.coverageDepth.id) {
              cov_depth = RefdataValue.get(c.coverageDepth.id)
            }
            else {
              cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', (c.coverageDepth.name ?: c.coverageDepth.value))
            }
          }

          def cst_obj = [
            'startVolume': c.startVolume,
            'startIssue': c.startIssue,
            'endVolume': c.endVolume,
            'endIssue': c.endIssue,
            'embargo': c.embargo,
            'coverageDepth': cov_depth,
            'coverageNote': c.coverageNote,
            'startDate': startAsDate,
            'endDate': endAsDate
          ]

          tipp.addToCoverageStatements(cst_obj)
        }
      }

      // remove missing coverage statements from TIPP
      stale_coverage_ids.each {
        tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(it))
      }

      // prices
      if (tipp_dto.prices && tipp_dto.prices.size() > 0) {
        tipp_dto.prices.each { price ->
          log.debug("Setting price ${price}")
          if (!price.id && (price.price || price.amount) )
            tipp.setPrice(price.type instanceof String ? price.type : price.type?.name,
                "${price.amount ?: price.price} ${price.currency instanceof String ? price.currency : price.currency?.name}",
                price.startDate ? DateFormatService.parseDate(price.startDate) : null,
                price.endDate ? DateFormatService.parseDate(price.endDate) : null)
        }
      }

      tipp.save(flush: true, failOnError: true)

      tippService.touchPackage(tipp)

      result = tipp
    }
    else {
      log.debug("Not able to reference TIPP: ${tipp_dto}")
    }
    result
  }
}