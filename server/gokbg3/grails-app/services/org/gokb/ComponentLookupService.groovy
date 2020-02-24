package org.gokb

import grails.util.GrailsNameUtils
import groovyx.net.http.URIBuilder

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import grails.util.Holders


class ComponentLookupService {
  def grailsApplication
  def restMappingService

  public static final def ID_REGEX_TEMPLATE = ["^gokb::\\{", "\\:(\\d+)\\}\$"]


  public <T extends KBComponent> Map<String, T> lookupComponents(String... comp_name_strings) {
    Map<String, T> results = [:]
    for (String comp_name_string : comp_name_strings) {
      T comp = lookupComponent (comp_name_string)
      if (comp) {
        // Add the result.
        results["${comp_name_string}"] = comp
      }
    }

    results
  }

  public <T extends KBComponent> Map<String, T> lookupComponents(Collection<String> comp_name_strings) {
    Map<String, T> results = [:]
    for (String comp_name_string : comp_name_strings) {
      T comp = lookupComponent (comp_name_string)
      if (comp) {
        // Add the result.
        results["${comp_name_string}"] = comp
      }
    }

    results
  }

//  private Map<String, ?> vals = [:].withDefault { String key ->
//    lookupComponentDB (key)
//  }
//
//  public <T extends KBComponent> T lookupComponent(String comp_name_string) {
//
//    // Merge this object into the current session if needed.
//    T object = (T)vals.get(comp_name_string)
//    if (object != null && !object.isAttached()) {
//      object = object.merge()
//      vals.put(comp_name_string, object)
//    }
//    return object
//  }

  private <T extends KBComponent> T lookupComponent (String comp_name_string) {
    return lookupComponent(comp_name_string,false)
  }

  private <T extends KBComponent> T lookupComponent (String comp_name_string, boolean lock) {

    // The Component
    T comp = null
    if (comp_name_string) {
      def component_match

      if ((component_match = comp_name_string =~ "${ID_REGEX_TEMPLATE[0]}([^\\:]+)${ID_REGEX_TEMPLATE[1]}\$") ||
        (component_match = comp_name_string =~ "${REGEX_TEMPLATE[0]}([^\\:]+)${REGEX_TEMPLATE[1]}\$")) {

        log.debug ("Matched the component syntax \"Display Text::{ComponentType:ID}\".")

        try {

          // Partial or complete class name.
          String cls_name = component_match[0][1]
          if (!cls_name.contains('.')) {
            cls_name = "org.gokb.cred.${GrailsNameUtils.getClassNameRepresentation(cls_name)}"
          }

          log.debug("Try and lookup ${cls_name} with ID ${component_match[0][2]}")

          // We have a match.
          Class<? extends KBComponent> c = Holders.grailsApplication.getClassLoader().loadClass("${cls_name}")

          // Parse the long.
          long the_id = Long.parseLong( component_match[0][2] )

          if (the_id > 0) {

            // Try and get the component.
            if ( lock ) {
              comp = c.lock(the_id)
            }
            else {
              comp = c.get(the_id)
            }

            if (!c) log.debug ("No component with that ID. Return null.")
          } else {
            log.debug ("Attempting to create a new component.")
            comp = c.newInstance()
          }

        } catch (Throwable t) {
          // Suppress errors here. Just return null.
          log.debug("Unable to parse component string.", t)
        }
      }
    }

    comp
  }

  public def restLookup (cls, params) {
    def result = [:]
    def hqlQry = "from ${cls.simpleName} as p".toString()
    def qryParams = [:]
    boolean incomingJoin = false
    boolean outgoingJoin = false
    def max = params.max ? params.long('max') : 10
    def offset = params.offset ? params.long('offset') : 0
    def first = true
    def combos = grailsApplication.getArtefact("Domain",cls.name).newInstance().allComboPropertyNames

    combos.each { c ->
      if (params[c]) {
        boolean incoming = KBComponent.lookupComboMappingFor (cls, Combo.MAPPED_BY, c)
        log.debug("Combo prop ${c}: ${incoming ? 'incoming' : 'outgoing'}")

        if (incoming) {
          hqlQry += " join p.incomingCombos as ${c}_combo"
          hqlQry += " join ${c}_combo.fromComponent as ${c}"
        }
        else {
          hqlQry += " join p.outgoingCombos as ${c}_combo"
          hqlQry += " join ${c}_combo.toComponent as ${c}"
        }
      }
    }

    combos.each { c ->
      if (params[c]) {
        def alts = params.list(c)
        boolean incoming = KBComponent.lookupComboMappingFor (cls, Combo.MAPPED_BY, c)
        log.debug("Combo prop ${c}: ${incoming ? 'incoming' : 'outgoing'}")

        if (first) {
          hqlQry += " WHERE "
          first = false
        }
        else {
          hqlQry += " AND "
        }
        if (alts.size() > 1) {
          hqlQry += "${c} IN :${c}"
          qryParams[c] = alts.collect { KBComponent.get(Long.valueOf(it)) }
        }
        else {
          hqlQry += "${c} = :${c}"
          qryParams[c] = KBComponent.get(Long.valueOf(params[c]))
        }
      }
    }

    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(cls.name)

    pent.getPersistentProperties().each { p ->
      if (params[p.name]) {
        log.debug("Handling persistent param prop: ${p.name}")
        if (first) {
          hqlQry += " WHERE "
          first = false
        }
        else {
          hqlQry += " AND "
        }
        def alts = params.list(p.name)

        if ( p instanceof Association ) {
          qryParams[p.name] = alts.collect { Long.valueOf(it) }
          hqlQry += "p.${p.name}.id IN :${p.name}"
        }
        else if ( p.type == Long ) {
          qryParams[p.name] = alts.collect { Long.valueOf(it) }
          hqlQry += "p.${p.name} IN :${p.name}"
        }
        else if ( p.name == 'name' ){
          hqlQry += "p.${p.name} like :${p.name}"
          qryParams[p.name] = "${params[p.name]}%"
        }
        else {
          hqlQry += "p.${p.name} = :${p.name}"
          qryParams[p.name] = val
        }
      }
    }

    // if (es_result.result == 'OK') {
    //   result = restMappingService.convertEsLinks(params, es_result, "packages")
    // }

    def hqlCount = "select count(*) ${hqlQry}".toString()
    def hqlFinal = "select p ${hqlQry}".toString()

    log.debug("Final qry: ${hqlFinal}")

    def hqlTotal = cls.executeQuery(hqlCount, qryParams,[:])[0]
    def hqlResult = cls.executeQuery(hqlFinal, qryParams, [max: max, offset: offset])

    result.data = []

    hqlResult.each { r ->
      log.debug("Handling ${r} -- Total: ${hqlTotal}")
      def obj = null

      if (r instanceof Object[]) {
        obj = r[0]
      }
      else {
        obj = r
      }
      log.debug("${obj}")
      result.data << restMappingService.mapObjectToJson(obj, params)
    }

    result['_pagination'] = [
      offset: offset,
      limit: max,
      total: hqlTotal
    ]

    generateLinks(result, cls, params, max, offset, hqlTotal)

    result
  }

  private generateLinks(result, cls, params, max, offset, total) {
    def endpoint = cls.newInstance().hasProperty('restPath') ? cls.newInstance().restPath : ""
    def base = grailsApplication.config.serverURL + "/rest" + "${endpoint}"

    result['_links'] = [:]

    def selfLink = new URIBuilder(base)
    selfLink.addQueryParams(params)
    selfLink.removeQueryParam('controller')
    selfLink.removeQueryParam('action')
    selfLink.removeQueryParam('componentType')
    result['_links']['self'] = [href: selfLink.toString()]


    if (total > offset+max) {
      def link = new URIBuilder(base)
      link.addQueryParams(params)
      if(link.query.offset){
        link.removeQueryParam('offset')
      }
      link.removeQueryParam('controller')
      link.removeQueryParam('action')
      link.removeQueryParam('componentType')
      link.addQueryParam('offset', "${offset + max}")
      result['_links']['next'] = ['href': (link.toString())]
    }
    if (offset > 0) {
      def link = new URIBuilder(base)
      link.addQueryParams(params)
      if(link.query.offset){
        link.removeQueryParam('offset')
      }
      link.removeQueryParam('controller')
      link.removeQueryParam('action')
      link.removeQueryParam('componentType')
      link.addQueryParam('offset', "${(offset - max) > 0 ? offset - max : 0}")
      result['_links']['prev'] = ['href': link.toString()]
    }

  }
}
