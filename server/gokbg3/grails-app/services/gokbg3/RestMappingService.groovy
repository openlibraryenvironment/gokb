package gokbg3

import com.k_int.ClassUtils
import grails.core.GrailsClass

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.gokb.cred.*
import org.gokb.GOKbTextUtils
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

import grails.gorm.transactions.Transactional

class RestMappingService {
  def grailsApplication
  def genericOIDService
  def classExaminationService

  def defaultIgnore = [
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

  def defaultImmmutable = [
    'id',
    'uuid',
    'lastUpdate',
    'dateCreated',
    'lastUpdatedBy',
    'value',
    'version'
  ]

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

  /**
   *  updateObject : Maps an domain class object to JSON based on its jsonMapping config.
   * @param obj : The object to be mapped
   * @param params : The map of request parameters
   */

  @Transactional
  def updateObject(obj, jsonMap, reqBody) {
    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    def toIgnore = defaultIgnore + (jsonMap?.ignore ?: [])
    def immutable = defaultImmmutable + (jsonMap?.immutable ?: [])

    pent.getPersistentProperties().each { p -> // list of PersistentProperties
      log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");
      if ( !toIgnore.contains(p.name) && !immutable.contains(p.name) && reqBody[p.name] ) {
        if ( p instanceof Association ) {
          if ( p instanceof ManyToOne || p instanceof OneToOne ) {
            // Set ref property
            def linkObj = p.type.get(reqBody[p.name])

            if (linkObj) {
              if (p.type == RefdataValue) {
                String catName = classExaminationService.deriveCategoryForProperty(obj.class.name, p.name)

                if (catName) {
                  def cat = RefdataCategory.findByDesc(catName)

                  if (linkObj in cat.values) {
                    obj[p.name] = linkObj
                  }
                  else {
                    obj.errors.reject(
                      'rdc.values.notFound',
                      [linkObj.id, catName] as Object[],
                      '[Value with ID {0} does not belong to category {1}!]'
                    )
                    obj.errors.rejectValue(
                      p.name,
                      'rdc.values.notFound'
                    )
                  }

                }
                else {
                  log.error("Could not resolve category (${obj.niceName}.${p.name})!")
                }
              }
              else {
                obj[p.name] = linkObj
              }
            }
            else {
              obj.errors.reject(
                'default.not.found.message',
                [p.type, reqBody[p.name]] as Object[],
                '[{0} not found with id {1}]'
              )
              obj.errors.rejectValue(
                p.name,
                'default.not.found.message'
              )
            }
          }
          else {
            // Add to collection
            log.debug("Skip handling collections}");

            if (p.name == "variantNames") {
              def remaining = []
              def notFound = []

              try {
                reqBody[p.name].each {
                  def nobj = null
                  if (it instanceof String) {
                    if (it.trim()) {
                      def nvn = GOKbTextUtils.normaliseString(variantName)
                      def dupes = KBComponentVariantName.findByNormVariantNameAndOwner(nvn, obj)

                      if (dupes) {
                        log.debug("Not adding duplicate variant")
                      }
                      else {
                        obj.addToVariantNames(variantName: it)
                      }
                    }
                  }
                  else {
                    nobj = p.type.get(it)

                    if (nobj && nobj.owner == obj) {
                      remaining << nobj
                    }
                    else {
                      notFound << it
                    }
                  }
                }

                if (notFound.size() == 0) {
                  obj[p.name].retainAll(remaining)
                }
                else {
                  obj.errors.reject(
                    'component.addToList.denied.label',
                    [p.name] as Object[],
                    '[Could not process list of items for property {0}]'
                  )
                  obj.errors.rejectValue(
                    p.name,
                    'component.addToList.denied.label'
                  )
                }
              }
              catch (Exception e) {
                obj.errors.reject(
                  'component.addToList.denied.label',
                  [p.name] as Object[],
                  '[Could not process list of items for property {0}]'
                )
                obj.errors.rejectValue(
                  p.name,
                  'component.addToList.denied.label'
                )
              }
            }
          }
        }
        else {
          log.debug("checking for type of property -> ${p.type}")
          switch ( p.type ) {
            case Long.class:
              log.debug("Set simple prop ${p.name} = ${reqBody[p.name]} (as long=${Long.parseLong(reqBody[p.name])})");
              try {
                obj[p.name] = Long.parseLong(reqBody[p.name]);
              }
              catch (Exception e) {
                obj.errors.reject(
                  'typeMismatch.java.lang.Long',
                  [p.name] as Object[],
                  '[Invalid number value for property [{0}]]'
                )
                obj.errors.rejectValue(
                  p.name,
                  'typeMismatch.java.lang.Long'
                )
              }
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

  /**
   *  selectPreferredLabel : Determines the correct label property for a specific object.
   * @param obj : The object to be examined
   */

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

  /**
   *  getEmbeddedJson : Map embedded object.
   * @param obj : The object to be mapped
   */

  public def getEmbeddedJson(obj) {
    def pars = [:]
    log.debug("Embedded object ${obj}")
    mapObjectToJson(obj, pars)
  }
}