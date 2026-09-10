package org.gokb

import grails.gorm.transactions.Transactional

import groovy.util.logging.Slf4j

import org.gokb.cred.*
import org.grails.web.json.JSONObject

@Slf4j
class PackageUpdateService {

  def componentUpdateService
  def messageService
  def restMappingService
  def titleAugmentService
  def titleLookupService
  def tippUpsertService

  @Transactional
  public Map updateLinks(Package obj, JSONObject reqBody, boolean changed, boolean remove = true, User user) {
    log.debug("Updating package links ..")
    Map errors = [:]

    if (reqBody.ids instanceof Collection || reqBody.identifiers instanceof Collection) {
      Collection id_list = reqBody.ids instanceof Collection ? reqBody.ids : reqBody.identifiers

      Map id_result = restMappingService.updateIdentifiers(obj, id_list, remove)

      changed |= id_result.changed

      if (id_result.errors.size() > 0) {
        errors.ids = id_result.errors
      }
    }

    if (reqBody.curatoryGroups instanceof Collection) {
      Map cg_result = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      changed |= cg_result.changed

      if (cg_result.errors.size() > 0) {
        errors['curatoryGroups'] = cg_result.errors
      }
    }

    if (reqBody.listStatus) {
      RefdataValue new_val = null

      if (reqBody.listStatus instanceof String) {
        new_val = RefdataCategory.lookup('Package.ListStatus', reqBody.listStatus)
      }
      else if (reqBody.listStatus instanceof Integer) {
        RefdataValue rdv = RefdataValue.get(reqBody.listStatus)

        if (rdv.owner?.id == RefdataCategory.findByLabel('Package.ListStatus').id) {
          new_val = rdv
        }
        else {
          log.error("This value belongs to another Category (${rdv.owner.label})!")
        }

        RefdataValue review_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")

        String review_qry = '''from ReviewRequest as r
                            where status = :so
                            and (
                              componentToReview.id = :pkg
                              or exists (
                                select t from TitleInstancePackagePlatform as t
                                where t.id = r.componentToReview.id
                                and t.pkg = :pkg
                              )
                            )'''

        List open_reviews = ReviewRequest.executeQuery(review_qry, [pkg: obj.id, so: review_open],[max: 1])


        if (new_val && new_val != obj.listStatus) {
          if (new_val.value == 'Checked') {
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
        else if (new_val && new_val.value == 'Checked' && reqBody.updateVerifyDate) {
          if (open_reviews?.size() > 0) {
            errors['listStatus'] = [[message: 'All connected requests for review must be closed before the package can be marked as checked.', code: 409, messageCode: 'component.package.listStatus.error.openReviews']]
          }
          else {
            obj.listVerifiedDate = new Date()
            changed = true
          }
        }
      }
    }

    if (reqBody.tipps) {
      reqBody.tipps.each { tipp_dto ->
        tipp_dto.pkg = obj.id
        List ti_errors = []

        if (tipp_dto.title && tipp_dto.title instanceof Map) {
          if (!tipp_dto.title.id) {
            try {
              TitleInstance ti = titleAugmentService.upsertDTO(titleLookupService, tipp_dto.title, user)

              if (ti) {
                tipp_dto.title = ti.id
              }
            }
            catch (grails.validation.ValidationException ve) {
              log.error("ValidationException attempting to cross reference title", ve);
              valid_ti = false
              Map validation_errors = [
                message: "Title ${tipp_dto.title?.name} failed validation!",
                baddata: tipp_dto.title,
                errors : messageService.processValidationErrors(ve.errors)
              ]

              ti_errors.add(validation_errors)
            }
            catch (org.gokb.exceptions.MultipleComponentsMatchedException mcme) {
              log.debug("Handling MultipleComponentsMatchedException")
              valid_ti = false

              ti_errors.add([
                baddata: tipp_dto.title,
                message: "Unable to uniquely match title ${tipp_dto.title?.name}, check duplicates for titles ${mcme.matched_ids}!",
                conflicts: mcme.matched_ids
              ])
            }
          }
        }

        Map tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp_dto, java.util.Locale.ENGLISH)

        if (ti_errors?.size() > 0 || !tipp_validation.valid) {
          if (!errors.tipps) {
            errors.tipps = []
          }

          if (ti_errors?.size() > 0) {
            errors.tipps << ti_errors
          }

          if (!tipp_validation.valid) {
            errors.tipps << tipp_validation.errors
          }
        }
        else {
          TitleInstancePackagePlatform upserted_tipp = tippUpsertService.upsertDTO(tipp_dto, user)

          if (upserted_tipp) {
            if (errors.size() == 0) {
              log.debug("Ensuring TIPP core data ${tipp_dto}")
              componentUpdateService.ensureCoreData(upserted_tipp, tipp_dto, true, user)

              RefdataValue tipp_status

              if (tipp_dto.status instanceof String) {
                tipp_status = RefdataCategory.lookup('KBComponent.Status', tipp_dto.status)
              }
              else if (tipp_dto.status instanceof Integer) {
                RefdataValue id_rdv = RefdataValue.get(tipp_dto.status)
                tipp_status = id_rdv.owner.label == 'KBComponent.Status' ? id_rdv : null
              }
              else if (tipp_dto.status instanceof Map) {
                RefdataValue id_rdv = RefdataValue.get(tipp_dto.status.id)
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