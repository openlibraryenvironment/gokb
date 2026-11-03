package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import groovy.util.logging.Slf4j

import org.gokb.cred.*
import org.hibernate.Session
import org.grails.web.json.JSONObject

@Slf4j
class PackageService {
  def sessionFactory
  def genericOIDService
  def restMappingService
  def componentLookupService
  def platformService
  def validationService

  public Map generatePackageTypes(Job j = null, def pkg_id = null) {
    log.debug("Generating missing package content types.")
    List result = [book: 0, db: 0, journal: 0, mixed: 0, errors: 0]
    List pkg_list = []

    if (!pkg_id) {
      pkg_list = Package.executeQuery("select id from Package where contentType is null")
    }
    else {
      pkg_list << pkg_id
    }

    List msg_list = []
    int ctr = 0

    for (pkg in pkg_list) {
      Package.withNewTransaction {
        Package pkg_obj = Package.get(pkg)
        boolean has_db = TitleInstance.executeQuery('select id from TitleInstancePackagePlatform as tipp where pkg = :pkg and exists (select 1 from DatabaseInstance where id = tipp.title.id)', [pkg: pkg_obj],[max: 1]).size() > 0
        boolean has_journal = TitleInstance.executeQuery('select id from TitleInstancePackagePlatform as tipp where pkg = :pkg and exists (select 1 from JournalInstance where id = tipp.title.id)', [pkg: pkg_obj], [max: 1]).size() > 0
        boolean has_book = TitleInstance.executeQuery('select id from TitleInstancePackagePlatform as tipp where pkg = :pkg and exists (select 1 from BookInstance where id = tipp.title.id)', [pkg: pkg_obj], [max: 1]).size() > 0

        if (has_db && !has_journal && !has_book) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Database')
          result.db++
        }
        else if (has_journal && !has_db && !has_book) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Journal')
          result.journal++
        }
        else if (has_book && !has_db && !has_journal) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Book')
          result.book++
        }
        else if (has_book && has_journal) {
          pkg_obj.contentType = RefdataCategory.lookup('Package.ContentType', 'Mixed')
          result.mixed++
        }
        else if (!has_book && !has_journal && !has_db) {
          log.debug("No content to categorize for ${pkg_obj.name}")
        }
        else {
          def msg = "Found illegal package composition for ${pkg_obj.name} (journals: ${has_journal}, db: ${has_db}, book: ${has_book})!"
          log.warn(msg)
          result.errors++
          msg_list.add(msg)
        }

        ctr++

        if (j) {
          j.setProgress(ctr, pkg_list.size())
        }
      }
    }

    if (j) {
      j.message("Finished creating ${ctr} new types (${result.errors} errors).".toString())

      msg_list.each {
        j.message(it.toString())
      }

      j.endTime = new Date()
    }
  }

  public Map compareLists(listOne, listTwo, def full = true, Date date = null, Job j = null) {
    Map result = [:]

    RefdataValue status_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')
    RefdataValue status_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    List tipp_status = [status_current]
    Date checkDate = date ?: new Date()
    Map tipp_params = [:]
    Map totals = [
      one: [
        tipps: 0,
        titles: 0
      ],
      two: [
        tipps: 0,
        titles: 0
      ]
    ]
    Map titlesOne = [:]
    Map titlesTwo = [:]
    Integer currentPkgNum = 0
    int batchSize = 50
    boolean cancelled = false

    if (date) {
      if (date.before(new Date())) {
        tipp_status << status_retired
      }
    }

    if (full) {
      result = [
        'new': [],
        'both':[],
        'missing':[]
      ]
    }
    else {
      result = [
        'new': 0,
        'both': 0,
        'missing': 0
      ]
    }

    log.debug("Building titles map 1 ..")

    for (p1 in listOne) {
      Package pkg = Package.get(genericOIDService.oidToId(p1))
      currentPkgNum++

      if (pkg && !cancelled) {
        int total = TitleInstancePackagePlatform.executeQuery('''select count(*) from TitleInstancePackagePlatform as tipp
                                                                  where tipp.status in (:tippStatus)
                                                                  and tipp.pkg = :pkg''',
                                                                  [tippStatus: tipp_status, pkg: pkg])[0]
        int currentOffset = 0

        while (currentOffset < total) {
          List tipps = TitleInstancePackagePlatform.executeQuery('''from TitleInstancePackagePlatform as tipp
                                                                  where tipp.status in (:tippStatus)
                                                                  and tipp.pkg = :pkg
                                                                  and (!tipp.accessEndDate or tipp.accessEndDate > :date)
                                                                  and (!tipp.accessStartDate or tipp.accessStartDate < :date)
                                                                  and (tipp.status != :sr or tipp.accessEndDate > :date)
                                                                  order by tipp.id''',
                                                                [tippStatus: tipp_status, pkg: pkg, date: checkDate, sr: status_retired],
                                                                [max: batchSize, offset: currentOffset, readOnly: true])

          tipps.each { tipp ->
            TitleInstance ti = ClassUtils.deproxy(tipp.title)

            if (!titlesOne[ti.id]){
              titlesOne[ti.id] = [id: ti.id, name: ti.name, tipps: []]
              totals.one.titles++
            }

            totals.one.tipps++
            titlesOne[ti.id]['tipps'] << restMappingService.mapObjectToJson(tipp, tipp_params)

            currentOffset++
          }

          cleanUpGorm()

          if (Thread.currentThread().isInterrupted() || j?.isCancelled()) {
            log.debug("cancelling Job #${j?.uuid}")
            cancelled = true
            break
          }
        }

        if (j) {
          j.setProgress(currentPkgNum, (listOne.size() + listTwo.size()))
        }
      }
      else if (!pkg) {
        log.debug("Unable to resolve Package with id ${p1}")
      }
    }

    log.debug("Added ${totals.one.titles} titles with ${totals.one.tipps} TIPPs!")

    log.debug("Building titles map 2 ..")

    for (p2 in listTwo) {
      Package pkg = Package.get(genericOIDService.oidToId(p2))
      currentPkgNum++

      if (pkg && !cancelled) {
        int total = TitleInstancePackagePlatform.executeQuery('''select count(*) from TitleInstancePackagePlatform as tipp
                                                                  where tipp.status in (:tippStatus)
                                                                  and tipp.pkg = :pkg''',
                                                                  [tippStatus: tipp_status, pkg: pkg])[0]
        int currentOffset = 0

        while (currentOffset < total) {
          List tipps = TitleInstancePackagePlatform.executeQuery('''from TitleInstancePackagePlatform as tipp
                                                                    where tipp.status in (:tippStatus)
                                                                    and tipp.pkg = :pkg
                                                                    and (!tipp.accessEndDate or tipp.accessEndDate > :date)
                                                                    and (!tipp.accessStartDate or tipp.accessStartDate < :date)
                                                                    and (tipp.status != :sr or tipp.accessEndDate > :date)
                                                                    order by tipp.id''',
                                                                    [tippStatus: tipp_status, pkg: pkg, date: checkDate, sr: status_retired],
                                                                    [max: 50, offset: currentOffset, readOnly: true])

          tipps.each { tipp ->
            TitleInstance ti = ClassUtils.deproxy(tipp.title)

            if (!titlesTwo[ti.id]){
              titlesTwo[ti.id] = [id: ti.id, name: ti.name, tipps: []]
              totals.two.titles++
            }

            totals.two.tipps++
            titlesTwo[ti.id]['tipps'] << restMappingService.mapObjectToJson(tipp, tipp_params)

            if (!titlesOne[ti.id]) {
              if (full) {
                result['new'] << restMappingService.mapObjectToJson(tipp, tipp_params)
              }
              else {
                result['new']++
              }
            }

            currentOffset++
          }


          cleanUpGorm()

          if (Thread.currentThread().isInterrupted() || j?.isCancelled()) {
            log.debug("cancelling Job #${j?.uuid}")
            cancelled = true
            break
          }
        }

        if (j) {
          j.setProgress(currentPkgNum, (listOne.size() + listTwo.size()))
        }
      }
    }

    log.debug("Finished collecting TIPPs. Starting comparison")

    if (!cancelled) {
      titlesOne.each { id, val ->
        if (!titlesTwo[id]) {
          if (full) {
            result['missing'] << val
          }
          else {
            result['missing']++
          }
        }
        else {
          if (full) {
            result['both'] << ['id': val.id, 'name': val.name, 'old': val.tipps, 'new': titlesTwo[id].tipps]
          }
          else {
            result['both']++
          }
        }
      }

      titlesTwo.each { id, val ->
        if (!titlesOne[id]) {
          if (full) {
            result['new'] << val
          }
          else {
            result['new']++
          }
        }
      }
    }

    if (j) {
      j.endTime = new Date()
    }

    log.debug("Added ${totals.two.titles} titles with ${totals.two.tipps} TIPPs!")
    result
  }

  public Map restLookup(packageHeaderDTO, User user = null) {
    log.info("Upsert package with header ${packageHeaderDTO}")
    Map result = [to_create: true]
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    Map matches = validationService.matchForName(Package, packageHeaderDTO.name)

    // List ids_list = packageHeaderDTO.identifiers ?: packageHeaderDTO.ids

    // if (packageHeaderDTO.ids?.size() > 0) {
    //   ids_list.each { rid ->
    //     Identifier the_id = null

    //     if (rid instanceof Integer) {
    //       the_id = Identifier.get(rid)
    //     }
    //     else {
    //       def ns_field = rid.type ?: rid.namespace
    //       def ns = null

    //       if (ns_field) {
    //         if (ns_field instanceof Integer) {
    //           ns = IdentifierNamespace.get(ns_field)
    //         }
    //         else {
    //           ns = IdentifierNamespace.findByValueIlike(ns_field)
    //         }

    //         if (ns) {
    //           def match = Package.lookupByIO(ns.value, rid.value)

    //           if (match && match.status != status_deleted) {
    //             if (!matches["${ns.id}"])
    //               matches["${ns.id}"] = []

    //             matches["${ns.id}"] << [field: 'ids', value: rid.value, message: "An existing package was matched by a supplied identifier!"]
    //           }
    //         }
    //       }
    //     }
    //   }
    // }

    if (packageHeaderDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")

      packageHeaderDTO.variantNames.each {
        String variant_string

        if (it instanceof String) {
          variant_string = it.trim()
        }
        else if (it instanceof Map) {
          variant_string = it.variantName?.trim() ?: null
        }

        if (variant_string) {
          matches << validationService.matchForName(Package, variant_string, true)
        }
      }
    }

    if (matches.size() > 0) {
      result.to_create = false
      result.matches = matches
    }

    result
  }

  /**
   * REST package header validation
   */
  public Map restValidate(JSONObject packageHeaderDTO, Locale locale, boolean remove) {
    Map result = [valid: true, errors: [:]]

    if (!packageHeaderDTO.name || !packageHeaderDTO.name.trim()) {
      result.valid = false
      result.errors.name = [
        [
          messageCode: 'validation.missingName',
          message: 'Package name is missing!',
          baddata: packageHeaderDTO.name
        ]
      ]
    }

    String idJsonKey = 'ids'
    List ids_list = packageHeaderDTO[idJsonKey]

    if (!ids_list) {
      idJsonKey = 'identifiers'
      ids_list = packageHeaderDTO[idJsonKey]
    }

    if (ids_list) {
      Map id_errors = Identifier.validateDTOs(ids_list, locale)

      if (id_errors) {
        result.errors.put(idJsonKey, id_errors)
      }
    }

    validateLinkedInfo(result, packageHeaderDTO, 'provider', Org, remove)
    validateLinkedInfo(result, packageHeaderDTO, 'nominalPlatform', Platform, remove)

    result
  }

  /**
   * REST package header validation for provided package object
   */
  public Map restValidate(Package obj, JSONObject packageHeaderDTO, Locale locale, boolean remove) {
    Map result = restValidate(packageHeaderDTO, locale, remove)

    Map lookup_result = restLookup(packageHeaderDTO)

    lookup_result.matches?.each { id, errors ->
      if (id != "${obj.id}") {
        errors.each { er ->
          if (!result.errors[er.field]) {
            result.errors[er.field] = [er]
          }
          else {
            result.errors[er.field] << er
          }
        }

        result.valid = false
      }
    }

    result
  }

  private void validateLinkedInfo (result, packageHeaderDTO, linkType, cls, remove) {
    Object obj

    if (packageHeaderDTO[linkType]) {
      if (packageHeaderDTO[linkType] instanceof Integer || packageHeaderDTO[linkType] instanceof Long) {
        obj = cls.get(packageHeaderDTO[linkType])
      }
      else if (packageHeaderDTO[linkType] instanceof String) {
        obj = cls.findByUuid(packageHeaderDTO[linkType])
      }
      else if (packageHeaderDTO[linkType] instanceof Map) {
        if (packageHeaderDTO[linkType].uuid) {
          obj = cls.findByUuid(packageHeaderDTO[linkType].uuid)
        }

        if (!obj && packageHeaderDTO[linkType].id) {
          plt = cls.get(packageHeaderDTO[linkType].id)
        }
      }

      if (!obj) {
        result.valid = false

        result.errors[linkType] = [
          [
            message: 'Unable to reference mandatory linked component!',
            code: 404,
            baddata: packageHeaderDTO[linkType]
          ]
        ]
      }
    }
    else if (packageHeaderDTO[linkType] == null && remove) {
      result.valid = false

      result.errors[linkType] = [
        [
          message: 'Mandatory link must not be empty!',
          code: 400,
          baddata: null
        ]
      ]
    }
  }
}
