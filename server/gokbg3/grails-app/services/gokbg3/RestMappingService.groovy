package gokbg3

import com.k_int.ClassUtils
import grails.core.GrailsClass
import groovyx.net.http.URIBuilder

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

class RestMappingService {
  def grailsApplication
  def genericOIDService

  def convertEsLinks(params, es_result, component_endpoint) {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"

    result['_links'] = [:]
    result['data'] = es_result.records
    result['_pagination'] = [
      offset: es_result.offset,
      limit: es_result.max,
      total: es_result.count
    ]

    def selfLink = new URIBuilder(base + "/${component_endpoint}")
    selfLink.addQueryParams(params)
    selfLink.removeQueryParam('controller')
    selfLink.removeQueryParam('action')
    selfLink.removeQueryParam('componentType')
    result['_links']['self'] = [href: selfLink.toString()]


    if (es_result.count > es_result.offset+es_result.max) {
      def link = new URIBuilder(base + "/${component_endpoint}")
      link.addQueryParams(params)
      if(link.query.offset){
        link.removeQueryParam('offset')
      }
      link.removeQueryParam('controller')
      link.removeQueryParam('action')
      link.removeQueryParam('componentType')
      link.addQueryParam('offset', "${es_result.offset + es_result.max}")
      result['_links']['next'] = ['href': (link.toString())]
    }
    if (es_result.offset > 0) {
      def link = new URIBuilder(base + "/${component_endpoint}")
      link.addQueryParams(params)
      if(link.query.offset){
        link.removeQueryParam('offset')
      }
      link.removeQueryParam('controller')
      link.removeQueryParam('action')
      link.removeQueryParam('componentType')
      link.addQueryParam('offset', "${(es_result.offset - es_result.max) > 0 ? es_result.offset - es_result.max : 0}")
      result['_links']['prev'] = ['href': link.toString()]
    }

    result
  }

  /**
   *  mapObjectToJson : Maps an domain class object to JSON based on its jsonMapping config.
   * @param obj : The object to be mapped
   * @param params : The map of request parameters
   */

  def mapObjectToJson(obj, params) {
    log.debug("mapObjectToJson: ${obj.class.name} -- ${params}")
    def result = [:]
    def embed_active = params['_embed']?.split(',') ?: []
    def base = grailsApplication.config.serverURL + "/rest"
    def jsonMap = null
    def defaultIgnore = [
      'lastProject',
      'bucketHash',
      'shortcode',
      'normname',
      'people',
      'lastSeen',
      'additionalProperties',
      'updateBenchmark',
      'systemComponent',
      'provenance',
      'insertBenchmark',
      'componentHash',
      'prices',
      'subjects',
      'lastUpdateComment',
      'reference',
      'duplicateOf',
      'componentDiscriminator',
      'incomingCombos',
      'outgoingCombos'
    ]

    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    jsonMap = KBComponent.has(obj.deproxy(),'jsonMapping') ? obj.jsonMapping : null

    result['_links'] = [:]

    if (KBComponent.has(obj.deproxy(),"restPath")) {
      result['_links']['self'] = ['href': base + obj.restPath + "/${obj.hasProperty('uuid') ? obj.uuid : obj.id}"]
    }

    if ( embed_active.size() > 0 || jsonMap?.defaultEmbeds ) {
      result['_embedded'] = [:]
    }

    if ( embed_active.size() == 0 && jsonMap?.defaultEmbeds ) {
      embed_active = jsonMap.defaultEmbeds
    }

    pent.getPersistentProperties().each { p ->
      if (!defaultIgnore.contains(p.name) && (!jsonMap || !jsonMap.ignore.contains(p.name)) ) {
        if ( p instanceof Association ) {
          if ( p instanceof ManyToOne || p instanceof OneToOne ) {
            // Set ref property
            log.debug("set assoc ${p.name} to lookup of OID ${obj[p.name]?.id}");
            if (obj[p.name]) {
              def label = selectPreferredLabel(obj[p.name])

              result[p.name] = [
                'name': label,
                'id': obj[p.name].id
              ]

              if (embed_active.contains(p.name)) {
                result['_embedded'][p.name] = getEmbeddedJson(obj[p.name])
              }
            }
          }
          else {
            if(embed_active.contains(p.name)) {
              result['_embedded'][p.name] = []

              obj[p.name].each {
                result['_embedded'][p.name] << getEmbeddedJson(it)
              }
            }
          }
        }
        else {
          switch ( p.type ) {
            case Long.class:
              result[p.name] = "${obj[p.name]}";
              break;

            case Date.class:
              result[p.name] = obj[p.name] ? "${obj[p.name]}" : null
              break;
            default:
              result[p.name] = obj[p.name]
              break;
          }
        }
      }
    }
    // Handle combo properties
    if ( KBComponent.isAssignableFrom(obj.class) ) {
      def combo_props = obj.allComboPropertyNames

      combo_props.each { cp ->
        log.debug("Combo prop ${cp} is ${obj.getCardinalityFor(obj.class,cp)}")
        if (obj.getCardinalityFor(obj.class,cp) == 'hasByCombo') {
          if ( obj[cp] != null) {
            def cval = obj[cp]
            result[cp] = ['id': cval.id, 'name': cval.name, 'uuid': cval.uuid]
          }
        }
        else {
          if( embed_active.contains(cp) ) {
            result['_embedded'][cp] = []
            obj[cp].take(10).each {
              result['_embedded'][cp] << getEmbeddedJson(it)
            }
          }
        }
      }
    }
    result
  }

  def updateObject(obj, jonMap, reqBody) {
    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    pent.getPersistentProperties().each { p -> // list of PersistentProperties
      log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");
      if ( (!jsonMap.ignore || !jsonMap.ignore.contains(p.name)) && (!jsonMap.immutable || !jsonMap.immutable.contains(p.name)) && reqBody[p.name] ) {
        if ( p instanceof Association ) {
          if ( p instanceof ManyToOne || p instanceof OneToOne ) {
            // Set ref property
            Class objClass = p.type
            obj[p.name] = objClass.get(reqBody[p.name].id)
          }
          else {
            // Add to collection
            log.debug("Skip handling collections}");
          }
        }
        else {
          log.debug("checking for type of property -> ${p.type}")
          switch ( p.type ) {
            case Long.class:
              log.debug("Set simple prop ${p.name} = ${reqBody[p.name]} (as long=${Long.parseLong(reqBody[p.name])})");
              obj[p.name] = Long.parseLong(reqBody[p.name]);
              break;

            case Date.class:
              if (reqBody[p.name] == null) {
                obj[p.name] = null
              }
              else if (reqBody[p.name].trim()) {
                LocalDateTime dateObj = GOKbTextUtils.completeDateString(reqBody[p.name])
                if (dateObj) {
                  obj[p.name] = Date.from(dateObj.atZone(ZoneId.systemDefault()).toInstant())
                }
                else {
                  obj.errors.reject(
                    'typeMismatch.java.util.Date',
                    [p.name] as Object[],
                    '[Invalid date value for property [{0}]]'
                  )
                  obj.errors.rejectValue(
                    p.name,
                    'typeMismatch.java.util.Date'
                  )
                }
                log.debug("Set simple prop ${p.name} = ${reqBody[p.name]} (as date ${dateObj}))");
              }
              break;
            default:
              log.debug("Default for type ${p.type}")
              log.debug("Set simple prop ${p.name} = ${reqBody[p.name]}");
              obj[p.name] = reqBody[p.name]
              break;
          }
        }
      }
    }
    obj
  }

  private String selectPreferredLabel(obj) {
    def obj_label = null

    if (obj.hasProperty('username')) {
      obj_label = obj.username
    }
    else if (obj.hasProperty('name')) {
      obj_label = obj.name
    }
    else if (obj.hasProperty('value')) {
      obj_label = obj.value
    }
    else if (obj.hasProperty('variantName')) {
      obj_label = obj.variantName
    }

    return obj_label
  }

  public def getEmbeddedJson(obj) {
    def pars = [:]
    log.debug("Embedded object ${obj}")
    mapObjectToJson(obj, pars)
  }
}