package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import org.gokb.GOKbTextUtils
import org.gokb.cred.*
import org.grails.web.json.JSONObject
import org.hibernate.Session

class PlatformService {

  def sessionFactory
  def componentLookupService
  def reviewRequestService
  def restMappingService

  public Map restLookup(JSONObject platformDTO, def user = null) {
    Map result = [to_create: true]
    RefdataValue status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
    RefdataValue status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    Map matches = [:]
    Boolean viable_url = false

    if (platformDTO.name?.startsWith("http")) {
      try {
        log.debug("checking if platform name is an URL..")

        URL url_as_name = new URL(platformDTO.name)

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

    List name_candidates = Platform.findAllByNameIlikeAndStatusNotEqual(platformDTO.name?.trim(), status_deleted)

    name_candidates?.each {
      if (!matches[it.id]) {
        matches[it.id] = []
      }

      matches[it.id] << ['field': 'name', value: platformDTO.name, message:"The provided name matched an existing platform!"]
    }

    if (platformDTO.primaryUrl && platformDTO.primaryUrl.trim().size() > 0) {
      try {
        URL inc_url = new URL(platformDTO.primaryUrl)

        if (inc_url) {
          viable_url = true
          String urlHost = inc_url.getHost()

          if (urlHost.startsWith("www.")) {
            urlHost = urlHost.substring(4)
          }

          def platform_crit = Platform.createCriteria()

          List url_candidates = platform_crit.list {
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


    String variant_normname = GOKbTextUtils.normaliseString(platformDTO.name)
    List variant_matches = Platform.executeQuery("select distinct pl from Platform as pl join pl.variantNames as v where v.normVariantName = :nvn and pl.status = :sc ", [nvn: variant_normname, sc: status_current])

    variant_matches.each { vm ->
      if (!matches[vm.id])
        matches[vm.id] = []

      matches[vm.id] << ['field': 'name', value: platformDTO.name, message:"Provided name matched a variant of an existing platform!"]
    }

    if (platformDTO.variantNames?.size() > 0)  {
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      platformDTO.variantNames.each {
        String variant = null

        if (it instanceof String) {
          variant = it.trim()
        }
        else if (it instanceof Map) {
          variant = it.variantName?.trim() ?: null
        }

        if (variant){
          List name_matches = Platform.findAllByName(variant)

          name_matches.each { nm ->
            if (!matches[nm.id])
              matches[nm.id] = []

            matches[nm.id] << [field: 'variantNames', value: variant, message:"Provided variant matched the title of an existing platform!"]
          }

          String variant_nn = GOKbTextUtils.normaliseString(variant)
          List variant_candidates = Platform.executeQuery("select distinct p from Platform as p join p.variantNames as v where v.normVariantName = :nvn and p.status <> :sd ",[nvn: variant_nn, sd: status_deleted])

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

  public Platform upsertDTO(JSONObject platformDTO, def user = null) {
    Platform result
    boolean skip = false
    RefdataValue status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
    RefdataValue status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    List name_candidates = []
    List url_candidates = []
    boolean changed = false
    boolean viable_url = false

    if (platformDTO.uuid) {
      result = Platform.findByUuid(platformDTO.uuid)
    }
    if (result) {
      changed |= ClassUtils.setStringIfDifferent(result, 'name', platformDTO.name)
    } else {
      if (platformDTO.name.startsWith("http")) {
        try {
          log.debug("checking if platform name is an URL..")

          URL url_as_name = new URL(platformDTO.name)

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
          URL inc_url = new URL(platformDTO.primaryUrl)

          if (inc_url) {
            viable_url = true;
            String urlHost = inc_url.getHost()

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

        String variant_normname = GOKbTextUtils.normaliseString(platformDTO.name)

        List varname_candidates = Platform.executeQuery("select distinct pl from Platform as pl join pl.variantNames as v where v.normVariantName = :nvn and pl.status = :sc ", [nvn: variant_normname, sc: status_current])

        if (varname_candidates.size() == 1) {
          log.debug("Platform matched by variant name!")
          result = varname_candidates[0]
        }

      } else if (name_candidates.size() == 1 && name_candidates[0].status == status_current) {
        log.debug("Platform ${platformDTO.name} matched by name!")
        result = name_candidates[0]
      } else {
        log.warn("Could not match a specific current platform for ${platformDTO.name}!")
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

          List current_platforms = url_candidates.findAll { it.status == status_current } ?: []

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
        Map platform_map = [
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
          null,
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

  public Map merge(old_platform_id, new_platform_id, Job j = null) {
    Map result = [:]
    boolean new_session = false

    try {
      Session session = sessionFactory.currentSession
    }
    catch (Exception e) {
      new_session = true
    }

    if (new_session) {
      Platform.withNewSession {
        result = processMerge(old_platform_id, new_platform_id, j)
      }
    }
    else {
      result = processMerge(old_platform_id, new_platform_id, j)
    }

    result
  }

  private Map processMerge(old_platform_id, new_platform_id, Job j = null) {
    Map result = [result: 'OK', tipps: 0, tipls: 0, pkgs: 0]
    Session psession = sessionFactory.currentSession

    Platform old_platform = Platform.findById(old_platform_id)
    Platform new_platform = Platform.findById(new_platform_id)

    if (!old_platform || !new_platform) {
      log.error("merge :: Missing value - Old: ${old_platform}, New: ${new_platform}")
      result.result = 'ERROR'
      result.message = "Unable to lookup platform(s): ${old_platform_id} -> ${old_platform} -- ${new_platform_id} -> ${new_platform}!"
      return result
    }

    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
    boolean cancelled = false

    try {
      List affected_pkgs_ids = Package.executeQuery('''select p.id from Package as p
                                                        where p.nominalPlatform = :op
                                                        or exists (
                                                          select 1 from TitleInstancePackagePlatform
                                                          where pkg = p
                                                          and status != :sd
                                                          and hostPlatform = :op
                                                        )
                                                        and status != :sd''',
                                                        [
                                                          op: old_platform,
                                                          sd: status_deleted
                                                        ])

      j?.message("Processing ${affected_pkgs_ids.size()} affected pkgs ..")

      for (pid in affected_pkgs_ids) {
        if (cancelled) {
          break
        }

        result.pkgs++
        boolean more_tipps = true
        j?.message("Processing TIPPs for package ${pid} ..")

        while (more_tipps) {
          if (Thread.currentThread().isInterrupted() || j?.isCancelled()){
            log.debug("Job is cancelled ..")
            result.result = 'CANCELLED'
            cancelled = true
            break
          }

          j?.setProgress(Math.floor(50 * (result.pkgs/(affected_pkgs_ids.size() + 1))).toInteger())

          List affected_tipps_batch = TitleInstancePackagePlatform.executeQuery('''from TitleInstancePackagePlatform as t
                                                            where pkg.id = :pid
                                                            and status != :sd
                                                            and hostPlatform = :op''',
                                                            [
                                                              op: old_platform,
                                                              pid: pid,
                                                              sd: status_deleted
                                                            ],
                                                            [max: 50]
                                                          )
          result.tipps += affected_tipps_batch.size()

          affected_tipps_batch.each { ctp ->
            def dupes = TitleInstancePackagePlatform.executeQuery('''select count(tipp.id) from TitleInstancePackagePlatform as tipp
                                                                      where pkg.id = :pid
                                                                      and title = :ti
                                                                      and hostPlatform = :np
                                                                      and status != :sd''',
                                                                    [
                                                                      ti: ctp.title,
                                                                      pid: pid,
                                                                      np: new_platform,
                                                                      sd: status_deleted
                                                                    ]
                                                                  )[0]
            if (dupes == 0) {
              ctp.hostPlatform = new_platform
              ctp.lastUpdateComment = "Platform cleanup"
              ctp.save(flush: true, failOnError: true)
            }
            else {
              log.debug("Not creating duplicate TIPP!")
              ctp.status = status_deleted
              ctp.save(flush: true, failOnError: true)
            }
          }

          if (affected_tipps_batch.size() < 50) {
            more_tipps = false
          }

          psession.flush()
          psession.clear()
        }

        if (!cancelled) {
          Package pkg = Package.findById(pid)

          if (pkg.nominalPlatform == old_platform) {
            pkg.nominalPlatform = new_platform
          }

          pkg.lastUpdateComment = "Platform cleanup"
          pkg.save(flush: true, failOnError: true)
        }
      }

      if (cancelled) {
        return result
      }

      boolean more_tipls = true

      j?.message("Processing TIPLs ..")


      int count_tipls = TitleInstancePlatform.executeQuery("select count(*) from TitleInstancePlatform where hostPlatform = :op", [op: old_platform])[0]

      while (more_tipls) {
        if (Thread.currentThread().isInterrupted() || j?.isCancelled()){
          log.debug("Job is cancelled ..")
          result.result = 'CANCELLED'
          cancelled = true
          break
        }

        if (count_tipls > 0) {
          j?.setProgress(50 + Math.floor(50 * (result.tipls/count_tipls)).toInteger())
        }

        List affected_tipls_batch = TitleInstancePlatform.executeQuery("from TitleInstancePlatform where hostPlatform = :op",[op: old_platform], [max: 50])

        result.tipls += affected_tipls_batch.size()

        affected_tipls_batch.each { ctp ->
          int dupes = TitleInstancePlatform.executeQuery('''select count(*) from TitleInstancePlatform
                                                            where title = :ti
                                                            and status != :sd
                                                            and hostPlatform = :np)''',
                                                            [
                                                              ti: ctp.title,
                                                              sd: status_deleted,
                                                              np: new_platform
                                                            ])[0]

          if (dupes == 0) {
            ctp.hostPlatform = new_platform
            ctp.save(flush: true, failOnError: true)

            connected_tipl.lastUpdateComment = "Platform cleanup"
            connected_tipl.save(flush: true, failOnError: true)
          }
          else {
            log.debug("Not creating duplicate TIPL for ${connected_tipl.title}")
            connected_tipl.status = status_deleted
            connected_tipl.save(flush: true, failOnError: true)
          }
        }

        if (affected_tipls_batch.size() < 50) {
          more_tipls = false
        }

        psession.flush()
        psession.clear()
      }

      if (cancelled) {
        return result
      }

      old_platform.refresh()
      old_platform.status = status_deleted
      old_platform.save(flush: true, failOnError: true)
    }
    catch (Exception e){
      log.error("Problem merging platforms", e)
      result.result = 'ERROR'
    }

    j?.setProgress(100)
    j?.endTime = new Date()

    result
  }

  private Map updateLinks(Platform obj, JSONObject reqBody, changed, boolean remove = true) {
    Map errors = [:]
    log.debug("Updating platform hasMany links ..")

    if (reqBody.ids || reqBody.identifiers) {
      List idmap = (reqBody.ids ?: reqBody.identifiers) as List
      changed |= restMappingService.updateIdentifiers(obj, idmap, remove)
    }

    if (reqBody.curatoryGroups) {
      Map cg_result = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      changed |= cg_result.changed

      if (cg_result.errors.size() > 0) {
        errors['curatoryGroups'] = cg_errors
      }
    }

    errors
  }
}