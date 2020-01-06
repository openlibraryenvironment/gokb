package gokbg3

import grails.core.GrailsClass
import groovyx.net.http.URIBuilder

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

class RestMappingService {
  def grailsApplication
  def genericOIDService

  /**
   *  mapObject : Used to create a form which will add a new object to a named collection within the target object.
   * @param obj : The object to be mapped
   * @param embed_active : The list of object associations to be embedded
   */

  def mapObjectToJson(obj, def embed_active = []) {
    def result = [:]
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

    result['links'] = [:]

    if (KBComponent.has(obj.deproxy(),"restPath")) {
      result['links']['self'] = ['href': base + obj.restPath + "/${obj.hasProperty('uuid') ? obj.uuid : obj.id}"]
    }

    if ( embed_active.size() > 0 ) {
      result['embedded'] = [:]
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
                  result['embedded'][p.name] = [
                    'links':[
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
                  result['links'][p.name] = ['href': base + associatedObj.restPath + "/${associatedObj.hasProperty('uuid') ? associatedObj.uuid : associatedObj.id}"]
                  result['links'][p.name]['title'] = selectPreferredLabel(associatedObj)

                  if(associatedObj.hasProperty('uuid')) {
                    result['links'][p.name]['uuid'] = associatedObj.uuid
                  }
                  else {
                    result['links'][p.name]['id'] = associatedObj.id
                  }
                }
                else {
                  log.warn("No restPath defined for class ${p.type.name}!")
                }

                if (embed_active.contains(p.name)) {
                  result['embedded'][p.name] = getEmbeddedJson(associatedObj)
                }
              }
            } 
          }
          else {
            if(KBComponent.has(obj,"restPath")) {
              result['links'][p.name] = ['href': base + obj.restPath + "/${obj.hasProperty('uuid') ? obj.uuid : obj.id}/" + p.name]
            }
            if(embed_active.contains(p.name)) {
              result['embedded'][p.name] = []

              obj[p.name].each {
                result['embedded'][p.name] << getEmbeddedJson(it)
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
              result['links'][cp] = ['href': base + cval.restPath + "/" + cval.uuid, 'title': cval.name, 'uuid': cval.uuid]
            }
          }
        }
        else {
          if( embed_active.contains(cp) ) {
            result['embedded'][cp] = []
            obj[cp].take(10).each {
              result['embedded'][cp] << getEmbeddedJson(it)
            }
          }
        }
      }
    }
    result
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
    mapObject(obj)
  }
}