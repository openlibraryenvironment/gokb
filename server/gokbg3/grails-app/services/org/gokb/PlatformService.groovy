package org.gokb

import grails.gorm.transactions.Transactional

import org.gokb.cred.*
import org.hibernate.Session

class PlatformService {

  def sessionFactory
  ComponentLookupService componentLookupService

  def restLookup(platformDTO, def user = null) {

    def result = [to_create: true]
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def matches = new HashMap()
    Boolean viable_url = false

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

    def name_candidates = Platform.executeQuery("from Platform where name = ? and status != ? ", [platformDTO.name, status_deleted])

    name_candidates.each {
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
    def variant_matches = Platform.executeQuery("select distinct pl from Platform as pl join pl.variantNames as v where v.normVariantName = ? and pl.status = ? ", [variant_normname, status_current])

    variant_matches.each { vm ->
      if (!matches[vm.id])
        matches[vm.id] = []

      matches[vm.id] << ['field': 'name', value: platformDTO.name, message:"Provided name matched a variant of an existing platform!"]
    }

    if( platformDTO.variantNames?.size() > 0 ){
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
          def variant_candidates = Platform.executeQuery("select distinct p from Platform as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ",[variant_nn, status_deleted])

          variant_candidates.each { vc ->
            log.debug("Found existing Platform variant name for variantName ${variant}")
            if (!matches[vc.id])
              matches[vc.id] = []

            matches[vc.id] << [field: 'variantNames', value: variant, message:"Provided variant matched that of an existing platform!"]
          }
        }
      }
    }

    if (matches) {
      result.to_create = false
      result.matches = matches
    }

    result
  }
}