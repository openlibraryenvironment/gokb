package org.gokb

import com.k_int.ClassUtils

import grails.gorm.transactions.Transactional

import org.gokb.cred.*
import org.hibernate.Session
import org.hibernate.SessionFactory

class OrgService {
  def platformService
  def FTUpdateService
  def restMappingService
  def componentUpdateService

  def restLookup(orgDTO, def user = null) {
    log.info("Upsert org with header ${orgDTO}");
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def result = [to_create: true]
    def normname = Org.generateNormname(orgDTO.name)

    log.debug("Checking by normname ${normname} ..")
    def name_candidates = Org.executeQuery("from Org as p where p.normname = :nn and p.status <> :sd", [nn: normname, sd: status_deleted])
    def ids_list = orgDTO.identifiers ?: orgDTO.ids
    def matches = [:]
    def created = false
    boolean changed = false;

    if (name_candidates.size() > 0) {
      name_candidates.each { nc ->
        if (!matches["${nc.id}"])
          matches["${nc.id}"] = []

        matches["${nc.id}"] << [field: 'name', value: orgDTO.name, message: "Another Organization with this name already exists!"]
      }
    }

    if (orgDTO.ids?.size() > 0) {
      ids_list.each { rid ->
        Identifier the_id = null

        if (rid instanceof Integer) {
          the_id = Identifier.get(rid)
        }
        else {
          def ns_field = rid.type ?: rid.namespace
          def ns = null

          if (ns_field) {
            if (ns_field instanceof Integer) {
              ns = IdentifierNamespace.get(ns_field)
            }
            else {
              ns = IdentifierNamespace.findByValueIlike(ns_field)
            }

            if (ns) {
              def match = Org.lookupByIO(ns.value, rid.value)

              if (match) {
                if (!matches["${ns.id}"])
                  matches["${ns.id}"] = []

                matches["${ns.id}"] << [field: 'ids', value: rid.value, message: "An existing organization was matched by a supplied identifier!"]
              }
            }
          }
        }
      }
    }

    def variant_normname = GOKbTextUtils.normaliseString(orgDTO.name)
    def variant_matches = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = :nvn and p.status <> :sd ", [nvn: variant_normname, sd: status_deleted])

    variant_matches.each { vm ->
      if (!matches["${vm.id}"])
        matches["${vm.id}"] = []

      matches["${vm.id}"] << ['field': 'name', value: orgDTO.name, message: "Provided name matched a variant of an existing organization!"]
    }

    if (orgDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      orgDTO.variantNames.each {
        def variant = null

        if (it instanceof String) {
          variant = it.trim()
        }
        else if (it instanceof Map) {
          variant = it.variantName?.trim() ?: null
        }

        if (variant) {
          def name_matches = Org.findAllByName(variant)

          name_matches.each { nm ->
            if (!matches["${nm.id}"])
              matches["${nm.id}"] = []

            matches["${nm.id}"] << [field: 'variantNames', value: variant, message: "Provided variant matched the title of an existing organization!"]
          }

          def variant_nn = GOKbTextUtils.normaliseString(variant)
          def variant_candidates = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = :nvn and p.status <> :sd ", [nvn: variant_nn, sd: status_deleted])

          variant_candidates.each { vc ->
            log.debug("Found existing Org variant name for variantName ${variant}")
            if (!matches["${vc.id}"])
              matches["${vc.id}"] = []

            matches["${vc.id}"] << ['field': 'variantNames', value: variant, message: "Provided variant matched that of an existing organization!"]
          }
        }
      }
    }

    if (matches.size() > 0) {
      result.to_create = false
      result.matches = matches
    }

    result
  }

  @Transactional
  def upsert(orgDTO, def user = null) {
    log.info("Upsert org with header ${orgDTO}")
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def org_normname = Org.generateNormname(orgDTO.name)

    log.debug("Checking by normname ${org_normname} ..")
    def name_candidates = Org.executeQuery("from Org as p where p.normname = :nn and p.status <> :sd", [nn: org_normname, sd: status_deleted])
    def full_matches = []
    def created = false
    def result = orgDTO.uuid ? Org.findByUuid(orgDTO.uuid) : null
    boolean changed = false

    if (!result && name_candidates.size() == 1) {
      log.debug("Matched org by name!")
      result = name_candidates[0]
    }
    else if (result && result.name != orgDTO.name) {
      def current_name = result.name
      changed |= ClassUtils.setStringIfDifferent(result, 'name', orgDTO.name)

      if (!result.variantNames.find { it.variantName == current_name }) {
        def new_variant = new KBComponentVariantName(owner: result, variantName: current_name).save(flush: true, failOnError: true)
      }
    }

    if (!result) {
      log.debug("Did not find a match via name, trying existing variantNames..")
      def variant_normname = GOKbTextUtils.normaliseString(orgDTO.name)
      def variant_candidates = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = :nvn and p.status <> :sd ", [nvn: variant_normname, sd: status_deleted])

      if (variant_candidates.size() == 1) {
        result = variant_candidates[0]
        log.debug("Package matched via existing variantName.")
      }
    }

    if (!result && orgDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      orgDTO.variantNames.each {

        if (it.trim().size() > 0) {
          result = Org.findByName(it)

          if (result) {
            log.debug("Found existing package name for variantName ${it}")
          }
          else {

            def variant_normname = GOKbTextUtils.normaliseString(it)
            def variant_candidates = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = :nvn and p.status <> :sd ", [nvn: variant_normname, sd: status_deleted])

            if (variant_candidates.size() == 1) {
              log.debug("Found existing Org variant name for variantName ${it}")
              result = variant_candidates[0]
            }
          }
        }
      }
    }

    if (!result) {
      log.debug("No existing Org matched. Creating new Org..")

      result = new Org(name: orgDTO.name, normname: org_normname)

      created = true

      if (orgDTO.uuid && orgDTO.uuid.trim().size() > 0) {
        result.uuid = orgDTO.uuid
      }

      result.save(flush: true, failOnError: true)
    }
    else if (user && !user.hasRole('ROLE_SUPERUSER') && result.curatoryGroups && result.curatoryGroups?.size() > 0) {
      def cur = user.curatoryGroups?.id.intersect(result.curatoryGroups?.id)

      if (!cur) {
        log.debug("No curator!")
        return result
      }
    }
    result
  }

  /*
  * Trigger updates for all incoming combo infos
  */

  @Transactional
  def updateCombos(obj, reqBody, boolean remove = true) {
    log.debug("Updating org combos ..")
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

    if (reqBody.providedPlatforms instanceof Collection) {
      def plts = reqBody.providedPlatforms

      def plts_result = updatePlatforms(obj, plts, remove)

      changed |= plts_result.changed

      if (plts_result.errors.size() > 0) {
        errors.providedPlatforms = plts_result.errors
      }
    }

    if (reqBody.curatoryGroups instanceof Collection) {
      def cg_result = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      changed |= cg_result.changed

      if (cg_result.errors.size() > 0) {
        errors['curatoryGroups'] = cg_result.errors
      }
    }

    if (reqBody.offices instanceof Collection) {
      def office_result = updateOffices(obj, reqBody.offices, remove)
      changed |= office_result.changed

      if (office_result.errors.size() > 0) {
        errors['offices'] = office_result.errors
      }
    }

    if (changed) {
      obj.lastSeen = System.currentTimeMillis()
    }
    log.debug("After update: ${obj}")
    errors
  }

  @Transactional
  def updatePlatforms(obj, plts, boolean remove = true) {
    def plt_combo_type = RefdataCategory.lookup('Combo.Type', 'Platform.Provider')
    def removed_plts = obj.providedPlatforms*.id
    def new_plts = []
    def result = [changed: false, errors: []]
    plts.each { plt ->
      Platform plt_obj = null

      if (plt instanceof String) {
        plt_obj = Platform.findByNameIlike(plt)
      }
      else if (plt instanceof Integer) {
        plt_obj = Platform.findById(plt)
      }
      else if (plt instanceof Map) {
        if (plt.id) {
          log.debug("Getting Platform by ID ${plt.id}..")
          plt_obj = Platform.findById(plt.id)
        }
        else {
          def lookup = platformService.restLookup(plt, null)
          log.debug("Result of platform lookup: ${lookup}")

          if (lookup.to_create) {
            plt_obj = platformService.upsertDTO(plt)
          }
          else if (lookup.matches.size() == 1) {
            lookup.matches?.each { mid, info ->
              log.debug("Handling platform with ID ${mid}..")
              def plt_candidate = Platform.get(mid)

              if (!plt_candidate) {
                result.errors << [message: "Unable to lookup platform!", code: 404, baddata: plt]
              }
              else if (new_plts.contains(plt_candidate)) {
                log.warn("Platform without ID is already linked to this provider!")
              }
              else if (plt_candidate.provider == null || plt_candidate.provider == obj) {
                plt_obj = plt_candidate
              }
              else {
                def provider_map = [[id: plt_candidate.provider.id, uuid: plt_candidate.provider.uuid, name: plt_candidate.provider.name]]
                result.errors << [message: "Matched Platform already has another Provider!", code: 409, baddata: plt, links: provider_map]
              }
            }
          }
          else {
            def other_providers = []

            lookup.matches?.each { mid, info ->
              log.debug("Handling platform with ID ${mid}..")
              def plt_candidate = Platform.get(mid)

              if (plt_candidate && plt_candidate.provider && plt_candidate.provider != obj && !other_providers.contains(plt_candidate.provider)) {
                other_providers << plt_candidate.provider
              }
            }

            if (other_providers.size() > 0) {
              def provider_map = other_providers.collect { [id: it.id, uuid: it.uuid, name: it.name] }
              result.errors << [message: "Matched Platforms that already have other providers!", code: 409, baddata: plt, links: provider_map]
            }
          }
        }
      }

      if (plt_obj) {
        new_plts << plt_obj

        removed_plts.removeElement(plt_obj.id)
      }
      else {
        result.errors << [message: "Unable to lookup platform!", code: 404, baddata: plt]
      }
    }

    if (!obj.hasErrors() || result.errors.size() > 0) {
      new_plts.each { c ->
        if (c.provider != obj) {
          log.debug("Adding new platform ${c}..")
          c.provider = obj
          c.save(flush: true)
          FTUpdateService.updateSingleItem(c)
          result.changed = true
        }
        else {
          log.debug("Existing platform ${c}..")
        }
      }

      if (remove) {
        removed_plts.each { pid ->
          Platform plt_obj = Platform.get(pid)
          plt_obj.provider = null
          plt_obj.save(flush: true)
          FTUpdateService.updateSingleItem(plt_obj)
          result.changed = true
        }
      }
    }
    log.debug("New platforms: ${obj.providedPlatforms}")
    result
  }

  @Transactional
  public def updateOffices(Org org, offices, boolean remove = true) {
    log.debug("Update offices ${offices}")
    RefdataValue type_office = RefdataCategory.lookup(Combo.RD_TYPE, 'Office.Org')
    RefdataValue status_active = DomainClassExtender.comboStatusActive
    def language_rdc = RefdataCategory.findByLabel(KBComponent.RD_LANGUAGE)
    def function_rdc = RefdataCategory.findByLabel(Office.RD_FUNCTION)
    def old_ids = org.offices.collect { it.id }
    def new_offices = []
    def result = [changed: false, errors: []]
    boolean created = false

    offices.each { office ->
      def office_obj = null

      if (office instanceof Integer) {
        office_obj = Office.get(office)
      }
      else if (office instanceof Map) {
        office_obj = office.id ? Office.get(office.id) : null

        if (!office_obj) {
          // create new office
          def lang = office.language

          if (lang instanceof String) {
            office.language = RefdataCategory.lookup(KBComponent.RD_LANGUAGE, lang)
          }
          else if (lang instanceof Integer) {
            def lang_rdv = RefdataValue.get(lang)

            if (lang_rdv.owner == language_rdc) {
              office.language = lang_rdv
            }
          }
          def function = office.function

          if (function instanceof String) {
            office.function = RefdataCategory.lookup(Office.RD_FUNCTION, function)
          }
          else if (function instanceof Integer) {
            def function_rdv = RefdataValue.get(function)

            if (function_rdv.owner == function_rdc) {
              office.function = function_rdv
            }
          }

          office_obj = new Office(office).save(flush: true)
          created = true
        }
      }

      if (office_obj) {
        // create combo to connect org & office

        if (!old_ids.contains(office_obj.id)) {
          org.offices.add(office_obj)
          org.save(flush: true)

          result.changed = true
        }
        new_offices << office_obj
      }
      else {
        result.errors << [message: "Unable to lookup or create office!", baddata: office]
      }
    }

    if (remove) {
      old_ids.each { old_office_id ->
        if (!new_offices*.id.contains(old_office_id)) {
          log.debug("Removing stale office entry..")
          componentUpdateService.expungeComponent(Office.get(old_office_id))
        }
      }
    }

    log.debug("New offices: ${new_offices}")

    result
  }
}
