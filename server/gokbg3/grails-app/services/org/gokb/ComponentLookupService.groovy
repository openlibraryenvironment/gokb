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

  /**
   *  restLookup : Look up components via HQL query.
   * @param cls : The Class of objects to be searched for
   * @param params : The map of request parameters
   * @param context : Possible override of the self link path
   */

  public def restLookup (user, cls, params, def context = null) {
    def result = [:]
    def hqlQry = "from ${cls.simpleName} as p".toString()
    def qryParams = [:]
    def max = params.max ? params.long('max') : 10
    def offset = params.offset ? params.long('offset') : 0
    def first = true
    def comboProps = grailsApplication.getArtefact("Domain",cls.name).newInstance().allComboPropertyNames
    def sort = null
    def sortField = null
    def order = params['order']?.toLowerCase() == 'desc' ? 'desc' : 'asc'

    def comboJoinStr = ""
    def comboFilterStr = ""

    // Check params for known combo properties

    comboProps.each { c ->

      if (params[c] || params['sort'] == c) {
        boolean incoming = KBComponent.lookupComboMappingFor (cls, Combo.MAPPED_BY, c)
        log.debug("Combo prop ${c}: ${incoming ? 'incoming' : 'outgoing'}")

        if (incoming) {
          comboJoinStr += " join p.incomingCombos as ${c}_combo"
          comboJoinStr += " join ${c}_combo.fromComponent as ${c}"
        }
        else {
          comboJoinStr += " join p.outgoingCombos as ${c}_combo"
          comboJoinStr += " join ${c}_combo.toComponent as ${c}"
        }

        if (first) {
          comboFilterStr += " WHERE "
          first = false
        }
        else {
          comboFilterStr += " AND "
        }

        comboFilterStr += "${c}_combo.type = :${c}type AND "
        qryParams["${c}type"] = RefdataCategory.lookupOrCreate ( "Combo.Type", cls.getComboTypeValueFor(cls, c))
        comboFilterStr += "${c}_combo.status = :${c}status "
        qryParams["${c}status"] = RefdataCategory.lookup("Combo.Status", "Active")

        def validLong = []
        def validStr = []
        def paramStr = ""

        if (params[c]) {
          params.list(c)?.each { a ->
            def addedLong = false

            try {
              validLong.add(Long.valueOf(a))
              addedLong = true
            }
            catch (java.lang.NumberFormatException nfe) {
            }

            if (!addedLong && a instanceof String && a?.trim() ) {
              validStr.add(a)
            }
          }

          if (validStr.size() > 0 || validLong.size() > 0) {
            paramStr += " AND ("

            if (validLong.size() > 0) {
              paramStr += "${c}.id IN :${c}"
              qryParams["${c}"] = validLong
            }
            if (validStr.size() > 0) {
              if (validLong.size() > 0) {
                paramStr += " OR "
              }
              paramStr += "${c}.uuid IN :${c}_str OR "
              paramStr += "${c}.${c == 'ids' ? 'value' : 'name'} IN :${c}_str"
              qryParams["${c}_str"] = validStr
            }
            paramStr += ")"
            comboFilterStr += paramStr
          }
        }
        else {
          sortField = "${c}.name"
          sort = " order by ${c}.name ${order ?: ''}"
        }
      }
    }

    hqlQry += comboJoinStr + comboFilterStr

    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(cls.name)

    // Check params for persistent properties

    pent.getPersistentProperties().each { p ->
      if (params[p.name]) {
        log.debug("Handling persistent param prop: ${p.name}")
        def paramStr = ""
        def addParam = true

        if (first) {
          paramStr += " WHERE "
          first = false
        }
        else {
          paramStr += " AND "
        }
        def alts = params.list(p.name)

        if ( p instanceof Association ) {
          def validLong = []
          def validStr = []

          alts.each { a ->
            def addedLong = false

            try {
              validLong.add(Long.valueOf(a))
              addedLong = true
            }
            catch (java.lang.NumberFormatException nfe) {
            }

            if (!addedLong && a instanceof String && a?.trim() ) {
              validStr.add(a)
            }
          }

          if ( validLong.size() > 0 || validStr.size() > 0 ) {
            paramStr += "("
            if (validLong.size() > 0) {
              paramStr += "p.${p.name}.id IN :${p.name}"
              qryParams[p.name] = validLong
            }
            if (validStr.size() > 0) {
              if (validLong.size() > 0) {
                paramStr += " OR "
              }

              paramStr += "p.${p.name}.${selectPreferredLabelProp(p.type)} IN :${p.name}_str"

              if (p.type.hasProperty('uuid')) {
                paramStr += " OR p.${p.name}.uuid IN :${p.name}_str"
              }
              qryParams["${p.name}_str"] = validStr
            }
            paramStr += ")"
          }
          else {
            addParam = false
          }
        }
        else if ( p.type == Long ) {
          qryParams[p.name] = alts.collect { Long.valueOf(it) }
          paramStr += "p.${p.name} IN :${p.name}"
        }
        else if ( p.name == 'name' ){
          paramStr += "lower(p.${p.name}) like :${p.name}"
          qryParams[p.name] = "${params[p.name].toLowerCase()}%"
        }
        else {
          paramStr += "p.${p.name} = :${p.name}"
          qryParams[p.name] = params[p.name]
        }

        if (addParam) {
          hqlQry += paramStr
        }
      }

      if (params['sort'] == p.name) {
        sortField = "p.${p.name}"
        sort = " order by ${p.name} ${order ?: ''}"
      }
    }

    // Filter out deleted records by default.

    if (!params['status']) {
      if (first) {
        hqlQry += " WHERE "
        first = false
      }
      else {
        hqlQry += " AND "
      }
      hqlQry += "p.status != :status"
      qryParams['status'] = RefdataCategory.lookup("KBComponent.Status", "Deleted")
    }

    def hqlCount = "select count(p.id) ${hqlQry}".toString()
    def hqlFinal = "select p ${sort ? ', ' + sortField : ''} ${hqlQry} ${sort ?: ''}".toString()

    log.debug("Final qry: ${hqlFinal}")

    def hqlTotal = cls.executeQuery(hqlCount, qryParams,[:])[0]
    def hqlResult = cls.executeQuery(hqlFinal, qryParams, [max: max, offset: offset])

    result.data = []

    hqlResult.each { r ->
      log.debug("Handling ${r} (${r.class.name}) -- Total: ${hqlTotal}")
      def obj = null

      if (r instanceof Object[]) {
        obj = r[0]
      }
      else {
        obj = r
      }

      log.debug("${obj}")
      result.data << restMappingService.mapObjectToJson(obj, params, user)
    }

    result['_pagination'] = [
      offset: offset,
      limit: max,
      total: hqlTotal
    ]

    generateLinks(result, cls, context, params, max, offset, hqlTotal)

    result
  }

  /**
   *  selectPreferredLabelProp : Determines the correct label property for a specific class.
   * @param cls : The class to be examined
   */

  private String selectPreferredLabelProp(cls) {
    def obj_label = null
    def cls_inst = cls.newInstance()

    if (cls_inst.hasProperty('username')) {
      obj_label = "username"
    }
    else if (cls_inst.hasProperty('value')) {
      obj_label = "value"
    }
    else if (cls_inst.hasProperty('name')) {
      obj_label = "name"
    }
    else if (cls_inst.hasProperty('variantName')) {
      obj_label = "variantName"
    }

    return obj_label
  }

  /**
   *  generateLinks : Generates pagination links for the JSON response.
   * @param result : The result object
   * @param cls : The class of returned objects
   * @param context : Possible override of the generated link path
   * @param max : Maximum items per page
   * @param offset : Result offset
   * @param total : Number of total results
   */

  private generateLinks(result, cls, context, params, max, offset, total) {
    def endpoint = cls.newInstance().hasProperty('restPath') ? cls.newInstance().restPath : ""
    def base = grailsApplication.config.serverURL + "/rest" + "${context ?: endpoint}"

    result['_links'] = [:]

    def selfLink = new URIBuilder(base)
    selfLink.addQueryParams(params)
    params.each { p, vals ->
      log.debug("handling param ${p}: ${vals}")
      if (vals instanceof String[]) {
        selfLink.removeQueryParam(p)
        vals.each { val ->
          if (val.trim()) {
            log.debug("Val: ${val} -- ${val.class.name}")
            selfLink.addQueryParam(p, val)
          }
        }
        log.debug("${selfLink.toString()}")
      }
      else if (!p.trim()) {
        selfLink.removeQueryParam(p)
      }
    }
    if(params.controller) {
      selfLink.removeQueryParam('controller')
    }
    if (params.action) {
      selfLink.removeQueryParam('action')
    }
    if (params.componentType) {
      selfLink.removeQueryParam('componentType')
    }
    result['_links']['self'] = [href: selfLink.toString()]


    if (total > offset+max) {
      def nextLink = selfLink
      if(nextLink.query.offset){
        nextLink.removeQueryParam('offset')
      }
      nextLink.addQueryParam('offset', "${offset + max}")
      result['_links']['next'] = ['href': (nextLink.toString())]
    }
    if (offset > 0) {
      def prevLink = selfLink

      if(prevLink.query.offset){
        prevLink.removeQueryParam('offset')
      }
      prevLink.addQueryParam('offset', "${(offset - max) > 0 ? offset - max : 0}")
      result['_links']['prev'] = ['href': prevLink.toString()]
    }
  }
}
