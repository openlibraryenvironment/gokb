package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import org.gokb.cred.*
import org.hibernate.Session
import org.hibernate.SessionFactory

class OrgService {
  def platformService
  def FTUpdateService
  def restMappingService
  def componentUpdateService
  def titleAugmentService
  def sessionFactory
  def validationService

  def restLookup(orgDTO, def user = null) {
    log.info("Upsert org with header ${orgDTO}")
    Map result = [to_create: true]
    RefdataValue status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    String normname = Org.generateNormname(orgDTO.name)
    String variant_normname = GOKbTextUtils.normaliseString(orgDTO.name)

    log.debug("Checking by normname ${normname} ..")
    List name_candidates = Org.executeQuery("from Org as p where p.normname = :nn and p.status <> :sd", [nn: normname, sd: status_deleted])
    List ids_list = orgDTO.identifiers ?: orgDTO.ids
    Map matches = validationService.matchForName(Org, orgDTO.name)

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

    if (orgDTO.variantNames?.size() > 0) {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")

      orgDTO.variantNames.each {
        String variant_string

        if (it instanceof String) {
          variant_string = it.trim()
        }
        else if (it instanceof Map) {
          variant_string = it.variantName?.trim() ?: null
        }

        if (variant_string) {
          matches << validationService.matchForName(Org, variant_string, true)
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
  def updateCombos(obj, reqBody, changed, boolean remove = true) {
    log.debug("Updating org combos ..")
    def errors = [:]

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

    if (reqBody.roles instanceof Collection) {
      def roles_result = updateRoles(obj, reqBody.roles, remove)
      changed |= roles_result.changed

      if (roles_result.errors.size() > 0) {
        errors['roles'] = roles_result.errors
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

  @Transactional
  public def updateRoles(Org org, roles, boolean remove = true) {
    RefdataCategory category = RefdataCategory.findByLabel('Org.Role')
    def result = [changed: false, errors: []]
    def old_roles = org.roles?.toArray() ?: []
    List new_roles = []

    roles.each { nr ->
      RefdataValue role_obj = null

      if (nr instanceof Integer) {
        role_obj = RefdataValue.get(nr)
      }
      else if (nr instanceof String) {
        role_obj = RefdataCategory.lookup('Org.Role', nr)
      }
      else if (nr instanceof Map) {
        role_obj = RefdataValue.get(nr.id)
      }

      if (role_obj && role_obj.owner == category) {
        new_roles << role_obj
      }
      else {
        result.errors << [message: "Unable to reference org role!", baddata: nr]
      }
    }

    new_roles.each { nr ->
      if (!old_roles.contains(nr)) {
        org.addToRoles(nr)
        result.changed = true
      }
    }

    if (remove) {
      old_roles.each { old_role ->
        if (!new_roles.contains(old_role)) {
          org.removeFromRoles(old_role)
          org.save(flush: true)
          result.changed = true
        }
      }
    }

    if (result.changed) {
      org.save(flush: true, failOnError: true)
    }

    result
  }

  @Transactional
  def transferPackages(old_provider, new_provider, boolean createNewCombos = true) {
    def result = [result: 'OK', transferred: 0]
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    RefdataValue combo_type_pkg_provider = RefdataCategory.lookup('Combo.Type', 'Package.Provider')
    def session = sessionFactory.currentSession

    if (!old_provider || !new_provider) {
      log.error("transferPackages :: Missing value - Old:${old_provider}, New:${new_provider}")
      result.result = 'ERROR'
      return result
    }

    if (createNewCombos) {
      def affected_pkgs = Package.executeQuery('''select p.id from Package as p
                                                  where exists (
                                                    select 1 from Combo as c
                                                    where fromComponent = p
                                                    and fromComponent.status != :sd
                                                    and toComponent = :op
                                                  )''',
                                                  [
                                                    sd: status_deleted,
                                                    op: old_provider
                                                  ])

      affected_pkgs.each { pid ->
        new_provider.refresh()
        Package pobj = Package.findById(pid)

        if (pobj.provider == old_provider) {
          pobj.provider = new_provider
        }

        if (pobj.broker == old_provider) {
          pobj.broker = new_provider
        }

        if (pobj.licensor == old_provider) {
          pobj.licensor = new_provider
        }

        if (pobj.vendor == old_provider) {
          pobj.vendor = new_provider
        }

        result.transferred++

        pobj.save(flush: true, failOnError: true)
      }
    }
    else {
      def affected_pkg_combos = Combo.executeQuery('''select id from Combo as c
                                                      where exists (
                                                        select 1 from Package as p
                                                        where p.id = c.fromComponent.id
                                                        and p.status != :sd
                                                      )
                                                      and toComponent = :op''',
                                                      [
                                                        sd: status_deleted,
                                                        op: old_provider
                                                      ])


      affected_pkg_combos.each { cttid ->
        Combo cobj = Combo.get(cttid)
        def pkg_obj = cobj.fromComponent

        cobj.toComponent = new_provider
        cobj.save(flush: true, failOnError: true)

        pkg_obj.lastUpdateComment = "Link Transfer for '${cobj.type.value}'"
        pkg_obj.save(flush: true, failOnError: true)

        result.transferred++
      }
    }

    result
  }

  @Transactional
  def mergeDuplicate(old_org_id, new_org_id, Job j = null) {
    def result = null
    boolean new_session = false
    Session session

    try {
      session = sessionFactory.currentSession
    }
    catch (Exception e) {
      new_session = true
    }

    if (new_session) {
      Platform.withNewSession { nsession ->
        result = processMergeDuplicate(old_org_id, new_org_id, nsession, j)
      }
    }
    else {
      result = processMergeDuplicate(old_org_id, new_org_id, session, j)
    }
  }

  private def processMergeDuplicate(old_org_id, new_org_id, session, Job j = null) {
    def result = [result: 'OK', ti: 0, pkgs: 0, plts: 0]

    Org old_org = Org.findById(old_org_id)
    Org new_org = Org.findById(new_org_id)
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    RefdataValue combo_type_ti_org = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Publisher')
    RefdataValue combo_type_plt_org = RefdataCategory.lookup('Combo.Type', 'Platform.Provider')
    boolean cancelled = false

    if (!old_org || !new_org) {
      log.error("mergeDuplicate :: Missing value - Old:${old_org}, New:${new_org}")
      result.result = 'ERROR'
      result.message = "Unable to lookup org(s): ${old_org_id} -> ${old_org} -- ${new_org_id} -> ${new_org}!"
      return result
    }

    try {
      // transfer publishers & update TIPPs + Packages
      def affected_ti_ids = TitleInstance.executeQuery('''select ti.id from TitleInstance as ti
                                                          where exists (
                                                            select 1 from Combo
                                                            where fromComponent = ti
                                                            and toComponent = :op
                                                            and type = :cttp
                                                          )
                                                          and status != :sd''',
                                                          [
                                                            op: old_org,
                                                            cttp: combo_type_ti_org,
                                                            sd: status_deleted
                                                          ])

      j?.message("Processing ${affected_ti_ids.size()} published titles ..")

      for (tid in affected_ti_ids) {
        def ti_obj = TitleInstance.get(tid)

        def dupes = Combo.executeQuery("select count(*) from Combo where fromComponent = :ti and toComponent = :np", [ti: ti_obj, np: new_org])[0]

        if (dupes == 0) {
          def combos_to_update = Combo.findAllByFromComponentAndToComponentAndType(ti_obj, old_org, combo_type_ti_org)

          combos_to_update.each { ctu ->
            ctu.toComponent = new_org
            ctu.save(flush: true, failOnError: true)
          }
        }
        else {
          log.debug("Found dupes, deleting old combos ..")

          def combos_deleted = Combo.executeUpdate('''delete from Combo
                                                      where fromComponent = :ti
                                                      and toComponent = :op
                                                      and type = :cttp
                                                    ''',
                                                    [
                                                      ti: ti_obj,
                                                      op: old_org,
                                                      cttp: combo_type_ti_org
                                                    ])
        }

        result.ti++
        j?.setProgress(result.ti, affected_ti_ids.size() + 50)

        ti_obj.lastUpdateComment = "Org cleanup"
        ti_obj.save(flush: true, failOnError: true)

        titleAugmentService.touchTitleTipps(ti_obj, false)

        if (result.ti % 50 == 0) {
          session.flush()
          session.clear()
        }

        if (Thread.currentThread().isInterrupted() || j?.isCancelled()){
          log.debug("Job is cancelled ..")
          cancelled = true
          break
        }
      }

      if (cancelled) {
        result.result = 'CANCELLED'
        return result
      }

      result.pkgs = transferPackages(old_org, new_org).transferred

      // Transfer Platforms

      def affected_platform_ids = Platform.executeQuery('''select p.id from Platform as p
                                                            where exists (
                                                              select 1 from Combo
                                                              where fromComponent = p
                                                              and toComponent = :op
                                                              and type = :ctpp
                                                            )
                                                            and status != :sd''',
                                                            [
                                                              op: old_org,
                                                              ctpp: combo_type_plt_org,
                                                              sd: status_deleted
                                                            ])

      affected_platform_ids.each { plid ->
        def plt = Platform.get(plid)
        plt.provider = new_org
        plt.save(flush: true, failOnError: true)

        result.plts++
      }

      // Moving variantNames

      def old_variants = []

      old_org.refresh()

      KBComponentVariantName.findAllByOwner(old_org).each { vn ->
        old_variants << [
          id: vn.id,
          variantName: vn.variantName,
          locale: vn.locale,
          type: vn.variantType,
          status: vn.status
        ]
      }

      log.debug("Transferring ${old_variants.size()} variants ..")

      // Moving Ids

      if (old_org.ids.size() > 0 && new_org.ids?.size() == 0) {
        def ids_to_add = old_org.activeIdInfo

        componentUpdateService.updateIdentifiers(new_org, ids_to_add)
      }

      old_org.status = status_deleted
      old_org.save(flush: true, failOnError: true)

      old_variants.each { variant ->
        new_org.ensureVariantName(variant.variantName, variant.type, variant.locale)
        new_org = new_org.merge(flush: true, failOnError: true)
      }

      new_org.ensureVariantName(old_org.name)
      new_org.lastUpdateComment = "Org ${old_org.id} merged"
      new_org.save(flush: true, failOnError: true)

      if (j) {
        j.setProgress(100)
        j.endTime = new Date()
      }
    }
    catch (Exception e) {
      result.result = 'ERROR'
      log.error("Error merging orgs", e)
    }

    result
  }

  @Transactional
  public def adjustOrgRolesToExistingCombos(org) {
    def result = [result: 'OK', changed: false]
    RefdataValue role_provider = RefdataCategory.lookup('Org.Role', 'Platform Provider')
    RefdataValue role_publisher = RefdataCategory.lookup('Org.Role', 'Publisher')
    RefdataValue combo_type_publisher = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Publisher')
    def existing_roles = org.roles.toArray()
    def new_roles = []

    Boolean is_provider = org.providedPlatforms?.size() > 0
    Boolean is_publisher = Combo.executeQuery('select count(*) from Combo where type = :rpb and toComponent = :org')[0] > 0

    if (is_provider) {
      new_roles << role_provider
    }

    if (is_publisher) {
      new_roles << role_publisher
    }

    result.changed |= org.roles.addAll(new_roles)

    if (changed) {
      org.save()
    }

    boolean removed = org.roles.retainAll(new_roles)

    if (removed) {
      org.save()
      result.changed = true
    }

    result
  }
}
