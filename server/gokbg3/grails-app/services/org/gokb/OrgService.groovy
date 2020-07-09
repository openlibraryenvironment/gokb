package org.gokb

import grails.gorm.transactions.Transactional

import org.gokb.cred.*
import org.hibernate.Session

class OrgService {

  def sessionFactory
  ComponentLookupService componentLookupService

  def restLookup(orgDTO, def user = null) {
    log.info("Upsert org with header ${orgDTO}");
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status','Deleted')
    def result = [to_create:true]
    def normname = Org.generateNormname(orgDTO.name)

    log.debug("Checking by normname ${org_normname} ..")
    def name_candidates = Org.executeQuery("from Org as p where p.normname = ? and p.status <> ?",[org_normname, status_deleted])
    def ids_list = orgDTO.identifiers ?: orgDTO.ids
    def matches = [:]
    def created = false
    boolean changed = false;

    if(name_candidates.size() > 0) {
      name_candidates.each { nc ->
        if (!matches["${nc.id}"])
          matches["${nc.id}"] = []

        matches["${nc.id}"] << [field: 'name', value: orgDTO.name, message: "Another Organization with this name already exists!"]
      }
    }

    if(orgDTO.ids?.size() > 0) {
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
                if (!matches["${nc.id}"])
                  matches["${nc.id}"] = []
                
                matches["${nc.id}"] << [field:'ids', value: rid.value, message: "An existing organization was matched by a supplied identifier!"]
              }
            }
          }
        }
      }
    }

    def variant_normname = GOKbTextUtils.normaliseString(orgDTO.name)
    def variant_matches = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ",[variant_normname, status_deleted]);

    variant_matches.each { vm ->
      if (!matches["${vm.id}"])
        matches["${vm.id}"] = []

      matches["${vm.id}"] << ['field': 'name', value: orgDTO.name, message:"Provided name matched a variant of an existing organization!"]
    }

    if( orgDTO.variantNames?.size() > 0 ){
      log.debug("Did not find a match via existing variantNames, trying supplied variantNames..")
      orgDTO.variantNames.each {
        def variant = null

        if (it instanceof String) {
          variant = it.trim()
        }
        else if (it instanceof Map) {
          variant = it.variantName?.trim() ?: null
        }

        if(variant){
          def name_matches = Org.findAllByName(variant)

          name_matches.each { nm ->
            if (!matches["${nm.id}"])
              matches["${nm.id}"] = []

            matches["${nm.id}"] << [field: 'variantNames', value: variant, message:"Provided variant matched the title of an existing organization!"]
          }

          def variant_nn = GOKbTextUtils.normaliseString(variant)
          def variant_candidates = Org.executeQuery("select distinct p from Org as p join p.variantNames as v where v.normVariantName = ? and p.status <> ? ",[variant_nn, status_deleted]);

          variant_candidates.each { vc ->
            log.debug("Found existing Org variant name for variantName ${variant}")
            if (!matches["${vc.id}"])
              matches["${vc.id}"] = []

            matches["${vc.id}"] << ['field': 'variantNames', value: variant, message:"Provided variant matched that of an existing organization!"]
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
}