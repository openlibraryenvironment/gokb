package org.gokb

import grails.gorm.transactions.Transactional

import groovy.util.logging.Slf4j

import org.gokb.cred.*

@Slf4j
class PackageUpdateService {

  def componentUpdateService
  def messageService
  def restMappingService
  def titleAugmentService
  def titleLookupService
  def tippUpsertService

  @Transactional
  def updateCombos(obj, reqBody, boolean remove = true, user) {
    log.debug("Updating package combos ..")
    def errors = [:]
    def changed = false

    if (reqBody.ids instanceof Collection || reqBody.identifiers instanceof Collection) {
      def id_list = reqBody.ids instanceof Collection ? reqBody.ids : reqBody.identifiers

      def id_result = restMappingService.updateIdentifiers(obj, id_list, remove)

      changed |= id_result.changed

      if (id_result.errors.size() > 0) {
        errors.ids = id_result.errors
      }
    }

    if (reqBody.curatoryGroups instanceof Collection) {
      def cg_result = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      changed |= cg_result.changed

      if (cg_result.errors.size() > 0) {
        errors['curatoryGroups'] = cg_result.errors
      }
    }

    if (reqBody.listStatus) {
      def new_val = null

      if (reqBody.listStatus instanceof String) {
        new_val = RefdataCategory.lookup('Package.ListStatus', reqBody.listStatus)
      }
      else if (reqBody.listStatus instanceof Integer) {
        def rdv = RefdataValue.get(reqBody.listStatus)

        if (rdv.owner?.id == RefdataCategory.findByLabel('Package.ListStatus').id) {
          new_val = rdv
        }
        else {
          log.error("This value belongs to another Category (${rdv.owner.label})!")
        }

        if (new_val && new_val != obj.listStatus) {
          if (new_val.value == 'Checked') {
            RefdataValue review_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
            RefdataValue combo_tipps = RefdataCategory.lookup("Combo.Type", "Package.Tipps")
            def open_reviews = ReviewRequest.executeQuery("from ReviewRequest where componentToReview in (select k from KBComponent as k where exists (select 1 from Combo where fromComponent.id = :pkg and type = :ct and toComponent = k) or k.id = :pkg) and status = :so", [pkg: obj.id, so: review_open, ct: combo_tipps],[max: 1])

            log.debug("Open Reviews: ${open_reviews}")

            if (open_reviews.size() == 0) {
              obj.listStatus = new_val
              obj.listVerifiedDate = new Date()
              changed = true
            }
            else {
              errors['listStatus'] = [[message: 'All connected requests for review must be closed before the package can be marked as checked.', code: 409, messageCode: 'component.package.listStatus.error.openReviews']]
            }
          }
          else {
            obj.listStatus = new_val
            changed = true
          }
        }

        if (new_val && new_val.value == 'Checked' && !errors.listStatus) {
          obj.listVerifiedDate = new Date()
        }
      }
    }

    if (reqBody.provider instanceof Integer) {
      def prov = null

      try {
        prov = Org.get(reqBody.provider)
      }
      catch (Exception e) {
      }

      if (prov && prov != obj.provider) {
        obj.provider = prov
        changed = true
      }
      else if (!prov) {
        errors.provider = [[message: "Could not find provider Org with id ${reqBody.provider}!", baddata: reqBody.provider]]
      }
    }
    else if (reqBody.provider == null) {
      obj.provider = null
      changed = true
    }

    if (reqBody.nominalPlatform != null || reqBody.platform != null) {
      def plt_id = reqBody.nominalPlatform ?: reqBody.platform
      def plt = null

      try {
        plt = Platform.get(plt_id)
      }
      catch (Exception e) {
      }

      if (plt && plt != obj.nominalPlatform) {
        obj.nominalPlatform = plt
        changed = true
      }
      else if (!plt) {
        errors.nominalPlatform = [[message: "Could not find platform with id ${reqBody.nominalPlatform}!", baddata: plt_id]]
      }
    }
    else if (reqBody.nominalPlatform == null || reqBody.platform == null) {
      obj.nominalPlatform = null
      changed = true
    }

    if (reqBody.tipps) {
      reqBody.tipps.each { tipp_dto ->
        tipp_dto.pkg = obj.id
        def ti_errors = []

        if (tipp_dto.title && tipp_dto.title instanceof Map) {
          if (!tipp_dto.title.id) {
            try {
              def ti = titleAugmentService.upsertDTO(titleLookupService, tipp_dto.title, user)

              if (ti) {
                tipp_dto.title = ti.id
              }
            }
            catch (grails.validation.ValidationException ve) {
              log.error("ValidationException attempting to cross reference title", ve);
              valid_ti = false
              def validation_errors = [
                  message: "Title ${tipp_dto.title?.name} failed validation!",
                  baddata: tipp_dto.title,
                  errors : messageService.processValidationErrors(ve.errors)
              ]
              ti_errors.add(validation_errors)
            }
            catch (org.gokb.exceptions.MultipleComponentsMatchedException mcme) {
              log.debug("Handling MultipleComponentsMatchedException")
              valid_ti = false
              ti_errors.add([baddata: tipp_dto.title, 'message': "Unable to uniquely match title ${tipp_dto.title?.name}, check duplicates for titles ${mcme.matched_ids}!", conflicts: mcme.matched_ids])
            }
          }
        }

        def tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp_dto, java.util.Locale.ENGLISH)

        if (ti_errors?.size > 0 || !tipp_validation.valid) {
          if (!errors.tipps) {
            errors.tipps = []
          }

          if (ti_errors?.size > 0) {
            errors.tipps << ti_errors
          }

          if (!tipp_validation.valid) {
            errors.tipps << tipp_validation.errors
          }
        }
        else {
          def upserted_tipp = tippUpsertService.upsertDTO(tipp_dto, user)

          if (upserted_tipp) {
            if (errors.size() == 0) {
              log.debug("Ensuring TIPP core data ${tipp_dto}")
              componentUpdateService.ensureCoreData(upserted_tipp, tipp_dto, true, user)

              def tipp_status = null

              if (tipp_dto.status instanceof String) {
                tipp_status = RefdataCategory.lookup('KBComponent.Status', tipp_dto.status)
              }
              else if (tipp_dto.status instanceof Integer) {
                def id_rdv = RefdataValue.get(tipp_dto.status)
                tipp_status = id_rdv.owner.label == 'KBComponent.Status' ? id_rdv : null
              }
              else if (tipp_dto.status instanceof Map) {
                def id_rdv = RefdataValue.get(tipp_dto.status.id)
                tipp_status = id_rdv.owner.label == 'KBComponent.Status' ? id_rdv : null
              }

              if (tipp_status && upserted_tipp.status != tipp_status) {
                upserted_tipp.status = tipp_status
              }

              upserted_tipp = upserted_tipp?.save(flush: true)
              changed = true
            }
          }
          else {
            if (!errors.tipps) {
              errors.tipps = []
            }

            error.tipps << [[message: "Unable to reference TIPP!", baddata: tipp_dto]]
          }
        }
      }
    }

    if (changed) {
      obj.lastSeen = System.currentTimeMillis()
      obj.save(flush: true)
    }
    errors
  }
}