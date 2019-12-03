package gokbg3

import grails.core.GrailsClass
import groovyx.net.http.URIBuilder

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

class RestMappingService {
  def grailsApplication
  def genericOIDService

  def createHalMapping(obj, List embeds = [], boolean embedded = false) {
    def result = [:]
    def base = grailsApplication.config.serverURL
    def jsonMap = null
    def embed_active = []

    GrailsClass obj_cls = grailsApplication.getArtefact('Domain', obj.class.name)

    if ( obj?.hasProperty('restPath') ) {
      PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj_cls.fullName)
      jsonMap = obj.hasProperty('jsonMapping') ? obj.jsonMapping : null

      result['links'] = [
        'self': ['href': base + obj.restPath + obj.id]
      ]

      if ( embed_active.size() > 0 ) {
        result['embedded'] = [:]
      }

      pent.getPersistentProperties().each { p ->
        if (!jsonMap || !jsonMap.ignore.contains(p.name)) {
          if ( p instanceof Association ) {
            if ( p instanceof ManyToOne || p instanceof OneToOne ) {
              // Set ref property
              log.debug("set assoc ${p.name} to lookup of OID ${obj[p.name]?.id}");
              if (obj[p.name]) {
                if(p.type.name == 'org.gokb.cred.RefdataValue'){
                  result['links'][p.name] = ['href': base + "/refdata/values/" + obj[p.name].id, 'title': obj[p.name].value]
                  if (embed_active.contains(p.name)) {
                    result['embedded'][p.name] = [
                      'links':[
                        'self':['href': base + "/refdata/values/" + obj[p.name].id, 'title': obj[p.name].value],
                        'owner':['href': base + "/refdata/categories/" + obj[p.name].owner.id]
                      ],
                      'value': obj[p.name].value,
                      'id': obj[p.name].id
                    ]
                  }
                }
                else {
                  if (obj[p.name].restPath) {
                    result['links'][p.name] = ['href': base + obj[p.name].restPath + "/${obj[p.name].id}"]

                    if (p.type.respondsTo('name')) {
                      result['links'][p.name]['title'] = obj[p.name].name
                    } 
                    else if (p.type.respondsTo('value')) {
                      result['links'][p.name]['title'] = obj[p.name].value
                    }
                  }
                  else {
                    log.error("No restPath defined for class ${p.type.name}!")
                  }
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
          log.debug("Combo prop ${cp} is ${obj.getCardinality(cp)}")
          if (obj.getCardinality(cp) == 'hasByCombo') {
            if ( obj[cp] != null ) {
              def cval = obj[cp]
              result['links'][cp] = ['href': base + cval.restPath + "/" + cval.id, 'title': cval.name]
            }
          }
          else {
            if( (!embedded && embed_active.contains(cp) || (embedded && jsonMap && jsonMap.combos_default)) ) {
              result['embedded'][cp] = []
              obj[cp].each {
                result['embedded'][cp] << getEmbeddedJson(it)
              }
            }
          }
        }
      }
    }
    else {
      log.error("No restPath defined for class ${obj.class.name}!")
    }
    result
  }

  public def getEmbeddedJson(obj) {
    createHalMapping(obj.class.name, obj.id, [], true)
  }
}