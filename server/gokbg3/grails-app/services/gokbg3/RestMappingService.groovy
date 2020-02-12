package gokbg3

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

    GrailsClass obj_cls = grailsApplication.getArtefact('Domain', obj.class.name)
    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj_cls.fullName)

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
              def associatedObj = obj[p.name]

              if(p.type.name == 'org.gokb.cred.RefdataValue'){
                if (embed_active.contains(p.name)) {
                  result['_embedded'][p.name] = [
                    '_links':[
                      'self':['href': base + "/refdata/values/" + associatedObj.id, 'title': associatedObj.value],
                      'owner':['href': base + "/refdata/categories/" + associatedObj.owner.id]
                    ],
                    'value': associatedObj.value,
                    'id': associatedObj.id
                  ]
                }
                result[p.name] = associatedObj.value
              }
              else {

                if (KBComponent.has(associatedObj, "restPath")) {
                  result['_links'][p.name] = ['href': base + associatedObj.restPath + "/${associatedObj.hasProperty('uuid') ? associatedObj.uuid : associatedObj.id}"]
                  result['_links'][p.name]['title'] = selectPreferredLabel(associatedObj)

                  if(associatedObj.hasProperty('uuid')) {
                    result['_links'][p.name]['uuid'] = associatedObj.uuid
                  }
                  else {
                    result['_links'][p.name]['oid'] = associatedObj.logEntityId
                  }
                }
                else {
                  log.warn("No restPath defined for class ${p.type.name}!")
                }

                if (embed_active.contains(p.name)) {
                  result['_embedded'][p.name] = getEmbeddedJson(associatedObj)
                }
              }
            } 
          }
          else {
            if(KBComponent.has(obj,"restPath")) {
              result['_links'][p.name] = ['href': base + obj.restPath + "/${obj.hasProperty('uuid') ? obj.uuid : obj.id}/" + p.name]
            }
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
    if ( KBComponent.isAssignableFrom(obj_cls.clazz) ) {
      def combo_props = obj.allComboPropertyNames

      combo_props.each { cp ->
        log.debug("Combo prop ${cp} is ${obj.getCardinalityFor(obj_cls.clazz,cp)}")
        if (obj.getCardinalityFor(obj_cls.clazz,cp) == 'hasByCombo') {
          if ( obj[cp] != null) {
            def cval = obj[cp]

            if(KBComponent.has(cval.deproxy(),"restPath")) {
              result['_links'][cp] = ['href': base + cval.restPath + "/" + cval.uuid, 'title': cval.name, 'uuid': cval.uuid]
            }
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

  def updateObject(obj, jonMap) {
    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    pent.getPersistentProperties().each { p -> // list of PersistentProperties
      log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");
      if ( (!jsonMap.ignore || !jsonMap.ignore.contains(p.name)) && (!jsonMap.immutable || !jsonMap.immutable.contains(p.name)) && reqBody[p.name] ) {
        if ( p instanceof Association ) {
          if ( p instanceof ManyToOne || p instanceof OneToOne ) {
            // Set ref property
            if ( p.type.name == 'org.gokb.cred.RefdataValue' ) {
              pkg[p.name] = RefdataValue.get(reqBody[p.name].id)
            }
            else {
              log.debug("set assoc ${p.name} to lookup of OID ${reqBody[p.name].oid}");
              if ( reqBody[p.name].uuid ) {
                pkg[p.name] = genericOIDService.resolveOID(reqBody.provider.uuid)
              }
              else {
                pkg[p.name] = genericOIDService.resolveOID(reqBody[p.name].oid)
              }
            }
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
              pkg[p.name] = Long.parseLong(reqBody[p.name]);
              break;

            case Date.class:
              LocalDateTime dateObj = reqBody[p.name] ? LocalDate.parse(reqBody[p.name], formatter) : null
              pkg[p.name] = dateObj ? java.sql.Timestamp.valueOf(dateObj) : null
              log.debug("Set simple prop ${p.name} = ${reqBody[p.name]} (as date ${dateObj}))");
              break;
            default:
              log.debug("Default for type ${p.type}")
              log.debug("Set simple prop ${p.name} = ${reqBody[p.name]}");
              pkg[p.name] = reqBody[p.name]
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
    mapObjectToJson(obj)
  }
}