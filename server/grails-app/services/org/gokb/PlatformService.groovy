package org.gokb

import com.k_int.ClassUtils

import grails.gorm.transactions.Transactional

import org.gokb.GOKbTextUtils
import org.gokb.cred.*
import org.hibernate.Session

class PlatformService {

  def sessionFactory
  ComponentLookupService componentLookupService
  def reviewRequestService

  def restLookup(platformDTO, def user = null) {

    def result = [to_create: true]
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def matches = new HashMap()
    Boolean viable_url = false

    if (platformDTO.name?.startsWith("http")) {
      try {
        log.debug("checking if platform name is an URL..")

        def url_as_name = new URL(platformDTO.name)

        if (url_as_name.getProtocol()) {
          if (!platformDTO.primaryUrl || !platformDTO.primaryUrl.trim()) {
            log.debug("identified URL as platform name")
            platformDTO.primaryUrl = platformDTO.name
          }
          platformDTO.name = url_as_name.getHost()

          if (platformDTO.name.startsWith("www.")) {
            platformDTO.name = platformDTO.name.substring(4)
          }

          log.debug("New platform name is ${platformDTO.name}.")
        }
      } catch (MalformedURLException) {
        log.debug("Platform name is no valid URL")
      }
    }

    def name_candidates = Platform.findAllByNameIlikeAndStatusNotEqual(platformDTO.name?.trim(), status_deleted)

    name_candidates?.each {
      if (!matches[it.id]) {
        matches[it.id] = []
      }

      matches[it.id] << ['field': 'name', value: platformDTO.name, message:"The provided name matched an existing platform!"]
    }

    if (platformDTO.primaryUrl && platformDTO.primaryUrl.trim().size() > 0) {
      try {
        def inc_url = new URL(platformDTO.primaryUrl);
        def other_candidates = []

        if (inc_url) {
          viable_url = true;
          String urlHost = inc_url.getHost();

          if (urlHost.startsWith("www.")) {
            urlHost = urlHost.substring(4)
          }

          def platform_crit = Platform.createCriteria()

          def url_candidates = platform_crit.list {
            or {
              like("name", "${urlHost}")
              like("primaryUrl", "%${urlHost}%")
            }
            ne("status", status_deleted)
          }

          url_candidates.each { um ->
            if (!matches[um.id])
              matches[um.id] = []

            matches[um.id] << ['field': 'primaryUrl', value: platformDTO.primaryUrl, message:"The provided URL matched an existing platform!"]
          }
        }
      } catch (MalformedURLException ex) {
        log.error("URL of ingest Platform ${platformDTO} is broken!")
      }
    }


    def variant_normname = GOKbTextUtils.normaliseString(platformDTO.name)
    def variant_matches = Platform.executeQuery("select distinct pl from Platform as pl join pl.variantNames as v where v.normVariantName = :nvn and pl.status = :sc ", [nvn: variant_normname, sc: status_current])

    variant_matches.each { vm ->
      if (!matches[vm.id])
        matches[vm.id] = []

      matches[vm.id] << ['field': 'name', value: platformDTO.name, message:"Provided name matched a variant of an existing platform!"]
    }

    if (platformDTO.variantNames?.size() > 0)  {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      platformDTO.variantNames.each {
        def variant = null

        if (it instanceof String) {
          variant = it.trim()
        }
        else if (it instanceof Map) {
          variant = it.variantName?.trim() ?: null
        }

        if(variant){
          def name_matches = Platform.findAllByName(variant)

          name_matches.each { nm ->
            if (!matches[nm.id])
              matches[nm.id] = []

            matches[nm.id] << [field: 'variantNames', value: variant, message:"Provided variant matched the title of an existing platform!"]
          }

          def variant_nn = GOKbTextUtils.normaliseString(variant)
          def variant_candidates = Platform.executeQuery("select distinct p from Platform as p join p.variantNames as v where v.normVariantName = :nvn and p.status <> :sd ",[nvn: variant_nn, sd: status_deleted])

          variant_candidates.each { vc ->
            log.debug("Found existing Platform variant name for variantName ${variant}")
            if (!matches[vc.id])
              matches[vc.id] = []

            matches[vc.id] << [field: 'variantNames', value: variant, message:"Provided variant matched that of an existing platform!"]
          }
        }
      }
    }

    if (matches?.size() > 0) {
      result.to_create = false
      result.matches = matches
    }

    result
  }

  public Platform upsertDTO(platformDTO, def user = null, def project = null) {
    // Ideally this should be done on platformUrl, but we fall back to name here

    def result = false;
    Boolean skip = false;
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def name_candidates = []
    def url_candidates = [];
    def changed = false
    Boolean viable_url = false;

    if (platformDTO.uuid) {
      result = Platform.findByUuid(platformDTO.uuid)
    }
    if (result) {
      changed |= ClassUtils.setStringIfDifferent(result, 'name', platformDTO.name)
    } else {
      if (platformDTO.name.startsWith("http")) {
        try {
          log.debug("checking if platform name is an URL..")

          def url_as_name = new URL(platformDTO.name)

          if (url_as_name.getProtocol()) {
            if (!platformDTO.primaryUrl || !platformDTO.primaryUrl.trim()) {
              log.debug("identified URL as platform name")
              platformDTO.primaryUrl = platformDTO.name
            }
            platformDTO.name = url_as_name.getHost()

            if (platformDTO.name.startsWith("www.")) {
              platformDTO.name = platformDTO.name.substring(4)
            }

            log.debug("New platform name is ${platformDTO.name}.")
          }
        } catch (MalformedURLException) {
          log.debug("Platform name is no valid URL")
        }
      }

      name_candidates = Platform.executeQuery("from Platform where name = :name and status != :sd ", [name: platformDTO.name, sd: status_deleted])

      if (platformDTO.primaryUrl && platformDTO.primaryUrl.trim().size() > 0) {
        try {
          def inc_url = new URL(platformDTO.primaryUrl);
          def other_candidates = []

          if (inc_url) {
            viable_url = true;
            String urlHost = inc_url.getHost();

            if (urlHost.startsWith("www.")) {
              urlHost = urlHost.substring(4)
            }

            def platform_crit = Platform.createCriteria()

            url_candidates = platform_crit.list {
              or {
                like("name", "${urlHost}")
                like("primaryUrl", "%${urlHost}%")
              }
            }
          }
        } catch (MalformedURLException ex) {
          log.error("URL of ingest Platform ${platformDTO} is broken!")
        }
      }

      if (name_candidates.size() == 0) {
        log.debug("No platforms matched by name!")

        def variant_normname = GOKbTextUtils.normaliseString(platformDTO.name)

        def varname_candidates = Platform.executeQuery("select distinct pl from Platform as pl join pl.variantNames as v where v.normVariantName = :nvn and pl.status = :sc ", [nvn: variant_normname, sc: status_current])

        if (varname_candidates.size() == 1) {
          log.debug("Platform matched by variant name!")
          result = varname_candidates[0]
        }

      } else if (name_candidates.size() == 1 && name_candidates[0].status == status_current) {
        log.debug("Platform ${platformDTO.name} matched by name!")
        result = name_candidates[0];
      } else {
        log.warn("Could not match a specific current platform for ${platformDTO.name}!");
      }

      if (!result && viable_url) {
        log.debug("Trying to match platform by primary URL..")

        if (url_candidates.size() == 0) {
          log.debug("Could not match an existing platform!")
        } else if (url_candidates.size() == 1) {
          log.debug("Matched existing platform by URL!")
          result = url_candidates[0];
        } else if (url_candidates.size() > 1) {
          log.warn("Matched multiple platforms by URL!")

          def current_platforms = url_candidates.findAll { it.status == status_current }

          if (current_platforms.size() == 1) {
            result = current_platforms[0]

            if (!result.primaryUrl) {
              result.primaryUrl = platformDTO.primaryUrl
              result.save(flush: true, failOnError: true)
            }
          } else if (current_platforms.size() == 0) {
            log.error("Matched only non-current platforms by URL!")
            result = url_candidates[0]
          } else {

            // Picking randomly from multiple results is bad, but right now a result is always expected. Maybe this should be skipped...
            // skip = true

            log.error("Multiple matched current platforms: ${current_platforms}")
            result = current_platforms[0]
          }
        }
      }

      if (!result && !skip) {
        log.debug("Creating new platform for: ${platformDTO}")
        def platform_map = [
          name: platformDTO.name,
          normname: KBComponent.generateNormname(platformDTO.name),
          primaryUrl: (viable_url ? platformDTO.primaryUrl : null),
          uuid: platformDTO.uuid ?: null
        ]

        result = new Platform(platform_map).save(flush: true, failOnError: true)

        reviewRequestService.raise(
          result,
          "The platform ${result} did not exist and was newly created.",
          "New platform created",
          user,
          project,
          null,
          RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'New Platform')
        )
      }
    }

    if (result) {
      log.debug("Updating platform url ${result.primaryUrl} -> ${platformDTO.primaryUrl} ..")
      result.primaryUrl = platformDTO.primaryUrl
      result.save(flush:true, failOnError:true)
    }

    result
  }

  @Transactional
  def merge(old_platform, new_platform) {
    def result = [result: 'OK', tipps: 0, tipls: 0, pkgs: 0]
    RefdataValue deleted_status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    RefdataValue current_status = RefdataCategory.lookup('KBComponent.Status', 'Current')
    RefdataValue combo_type_plt_pkg = RefdataCategory.lookup('Combo.Type', 'Package.NominalPlatform')
    RefdataValue combo_type_pkg_tipp = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
    RefdataValue combo_type_plt_tipp = RefdataCategory.lookup('Combo.Type', 'Platform.HostedTipps')
    RefdataValue combo_type_plt_tipl = RefdataCategory.lookup('Combo.Type', 'Platform.HostedTitles')
    RefdataValue combo_type_ti_tipl = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipls')
    RefdataValue combo_type_ti_tipp = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')
    def psession = sessionFactory.currentSession

    try {
      def affected_pkgs_ids = Package.executeQuery('''select p.id from Package as p
                                                  where exists (
                                                    select 1 from Combo as pkg_to_plt
                                                    where fromComponent = p
                                                    and toComponent = :op
                                                    and type = :ctpplt
                                                  )
                                                  or exists (
                                                    select 1 from Combo as pkg_to_tipp
                                                    where fromComponent = p
                                                    and type = :ctpkg
                                                    and toComponent.status != :sd
                                                    and exists (
                                                      select 1 from Combo as plt_to_tipp
                                                      where fromComponent = :op
                                                      and plt_to_tipp.toComponent = pkg_to_tipp.toComponent
                                                      and type = :cttplt
                                                    )
                                                  )
                                                  and status != :sd''',
                                                  [
                                                    op: old_platform,
                                                    ctpplt: combo_type_plt_pkg,
                                                    ctpkg: combo_type_pkg_tipp,
                                                    cttplt: combo_type_plt_tipp,
                                                    sd: deleted_status
                                                  ])

      for (pid in affected_pkgs_ids) {
        result.pkgs++
        boolean more_tipps = true

        while (more_tipps) {
          def affected_tipp_combos_batch = Combo.executeQuery('''from Combo as c
                                            where c.fromComponent.id = :pid
                                            and c.type = :ctt
                                            and c.toComponent.status != :sd
                                            and exists (
                                              select 1 from Combo
                                              where fromComponent = :op
                                              and type = :ctp
                                              and toComponent = c.toComponent
                                            )''',
                                          [
                                            op: old_platform,
                                            ctt: combo_type_pkg_tipp,
                                            ctp: combo_type_plt_tipp,
                                            pid: pid,
                                            sd: deleted_status
                                          ],
                                          [max: 50]
                                        )
          result.tipps += affected_tipp_combos_batch.size()

          affected_tipp_combos_batch.each { ctp ->
            def connected_item = ClassUtils.deproxy(ctp.toComponent)
            def dupes = TitleInstancePackagePlatform.executeQuery('''select count(tipp.id) from TitleInstancePackagePlatform as tipp
                                                                      where exists (
                                                                        select 1 from Combo
                                                                        where toComponent = tipp
                                                                        and fromComponent.id = :pid
                                                                        and type = :ctpkg
                                                                      )
                                                                      and exists (
                                                                        select 1 from Combo
                                                                        where toComponent = tipp
                                                                        and fromComponent = :ti
                                                                        and type = :ctti
                                                                      )
                                                                      and exists (
                                                                        select 1 from Combo
                                                                        where toComponent = tipp
                                                                        and fromComponent = :np
                                                                        and type = :ctplt
                                                                      )
                                                                      and status != :sd''',
                                                                    [
                                                                      ctpkg: combo_type_pkg_tipp,
                                                                      ctti: combo_type_ti_tipp,
                                                                      ctplt: combo_type_plt_tipp,
                                                                      ti: connected_item.title,
                                                                      pid: pid,
                                                                      np: new_platform,
                                                                      sd: deleted_status
                                                                    ]
                                                                  )[0]
            if (dupes == 0) {
              connected_item.hostPlatform = new_platform
              connected_item.lastUpdateComment = "Platform cleanup"
              connected_item.save(flush: true)
            }
            else {
              log.debug("Not creating duplicate TIPP!")
              connected_item.status = deleted_status
              connected_item.save(flush: true)
            }
          }

          if (affected_tipp_combos_batch.size() < 50) {
            more_tipps = false
          }

          psession.flush()
          psession.clear()
        }

        def pkg = Package.findById(pid)

        if (pkg.nominalPlatform == old_platform) {
          Combo.findByFromComponentAndToComponentAndType(pkg, old_platform, combo_type_plt_pkg).delete()
          new Combo(type: combo_type_plt_pkg, fromComponent: pkg, toComponent: new_platform).save(flush: true, failOnError: true)
        }

        pkg.lastUpdateComment = "Platform cleanup"
        pkg.save(flush: true)
      }

      boolean more_tipls = true

      while (more_tipls) {
        def affected_tipl_combos_batch = Combo.executeQuery('''from Combo as c
                                          where c.fromComponent = :op
                                          and c.type = :ctp''',
                                        [
                                          op: old_platform,
                                          ctp: combo_type_plt_tipl
                                        ],
                                        [max: 50]
                                      )
        result.tipls += affected_tipl_combos_batch.size()

        affected_tipl_combos_batch.each { ctp ->
          def connected_tipl = ClassUtils.deproxy(ctp.toComponent)
          def dupes = TitleInstancePlatform.executeQuery('''select count(*) from TitleInstancePlatform as tipl,
                                                            Combo as cti
                                                            where cti.type = :ctti
                                                            and cti.fromComponent = :ti
                                                            and cti.toComponent = tipl
                                                            and exists (
                                                              select 1 from Combo
                                                              where toComponent = tipl
                                                              and fromComponent = :np
                                                            )''',
                                                          [
                                                            ctti: combo_type_ti_tipl,
                                                            ti: connected_tipl.tiplTitle,
                                                            np: new_platform
                                                          ]
                                                        )[0]

          if (dupes == 0) {
            ctp.fromComponent = new_platform
            ctp.save(flush:true)

            connected_tipl.lastUpdateComment = "Platform cleanup"
            connected_tipl.save(flush: true)
          }
          else {
            log.debug("Not creating duplicate TIPL for ${connected_tipl.tiplTitle}")
            connected_tipl.status = deleted_status
            connected_tipl.save(flush: true)
          }
        }

        if (affected_tipl_combos_batch.size() < 50) {
          more_tipls = false
        }

        psession.flush()
        psession.clear()
      }

      old_platform.refresh()
      old_platform.status = deleted_status
      old_platform.save(flush: true)
    }
    catch (Exception e){
      log.error("Problem merging platforms", e)
      result.result = 'ERROR'
    }

    result
  }
}