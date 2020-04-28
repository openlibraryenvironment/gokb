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
  def componentLookupService

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
    'lastUpdated',
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

  def mapObjectToJson(obj, params, def user = null) {
    log.debug("mapObjectToJson: ${obj.class.name} -- ${params}")
    def result = [:]
    def embed_active = params['_embed']?.split(',') ?: []
    def include_list = params['_include']?.split(',') ?: null
    def exclude_list = params['_exclude']?.split(',') ?: null
    if (include_list && exclude_list) {
      exclude_list.removeAll(include_list)
    }
    def base = grailsApplication.config.serverURL + "/rest"
    def jsonMap = null
    def is_curator = true

    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    jsonMap = KBComponent.has(ClassUtils.deproxy(obj), 'jsonMapping') ? obj.jsonMapping : null

    if (KBComponent.has(ClassUtils.deproxy(obj), "restPath")) {
      result['_links'] = [:]
      result['_links']['self'] = ['href': base + obj.restPath + "/${obj.hasProperty('uuid') ? obj.uuid : obj.id}"]

      if (obj.respondsTo('curatoryGroups') && obj.curatoryGroups?.size() > 0) {
        is_curator = user?.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)
      }

      if (is_curator || user?.isAdmin()) {
        def objID = (obj.hasProperty('uuid') && obj.uuid != null) ? obj.uuid : obj.id
        result._links.update = ['href': base + obj.restPath + "/${objID}"]
        result._links.delete = ['href': base + obj.restPath + "/${objID}"]
        result._links.retire = ['href': base + obj.restPath + "/${objID}/retire"]
      }
    }

    if (embed_active.size() > 0 || jsonMap?.defaultEmbeds) {
      result['_embedded'] = [:]
    }

    if (embed_active.size() == 0 && jsonMap?.defaultEmbeds) {
      embed_active = jsonMap.defaultEmbeds
    }

    result['id'] = obj.id

    pent.getPersistentProperties().each { p ->
      if (!defaultIgnore.contains(p.name)
        && (!jsonMap || !jsonMap.ignore.contains(p.name))
        && (!include_list || include_list.contains(p.name))
        && (!exclude_list || !exclude_list.contains(p.name))) {
        if (p instanceof Association) {
          if (p instanceof ManyToOne || p instanceof OneToOne) {
            // Set ref property
            if (user?.isAdmin() || p.type != User) {
              if (obj[p.name]) {
                def label = selectPreferredLabel(obj[p.name])

                result[p.name] = [
                  'name': label,
                  'id'  : obj[p.name].id
                ]

                if (embed_active.contains(p.name)) {
                  result['_embedded'][p.name] = getEmbeddedJson(obj[p.name], user)
                }
              } else {
                result[p.name] = null
              }
            }
          } else {
            if (embed_active.contains(p.name) && (user?.isAdmin() || p.type != User)) {
              result['_embedded'][p.name] = []

              obj[p.name].each {
                result['_embedded'][p.name] << getEmbeddedJson(it, user)
              }
            }
          }
        } else {
          switch (p.type) {
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
      } else {
        log.debug("$p.name is ignored")
      }
    }
    // Handle combo properties
    if (KBComponent.isAssignableFrom(obj.class)) {
      def combo_props = obj.allComboPropertyNames

      combo_props.each { cp ->
        if (obj.getCardinalityFor(obj.class, cp) == 'hasByCombo') {
          if ((include_list && include_list?.contains(cp)) || (!include_list && jsonMap?.defaultLinks?.contains(cp))) {
            def cval = obj[cp]

            if (cval == null) {
              result[cp] = null
            } else {
              result[cp] = ['id': cval.id, 'name': cval.name, 'uuid': cval.uuid]
            }
          }
        } else {
          if (embed_active.contains(cp)) {
            result['_embedded'][cp] = []
            obj[cp].take(10).each {
              result['_embedded'][cp] << getEmbeddedJson(it, user)
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
    log.debug("Update object ${obj} - ${reqBody}")
    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    def toIgnore = defaultIgnore + (jsonMap?.ignore ?: [])
    def immutable = defaultImmmutable + (jsonMap?.immutable ?: [])

    log.debug("Ignore: ${toIgnore}, Immutable: ${immutable}")
    pent.getPersistentProperties().each { p -> // list of PersistentProperties
      if (!toIgnore.contains(p.name) && !immutable.contains(p.name) && reqBody[p.name]) {
        log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");
        if (p instanceof Association) {
          if (p instanceof ManyToOne || p instanceof OneToOne) {
            updateAssoc(obj, p.name, reqBody[p.name])
          } else {
            // Add to collection
            log.debug("Skip generic handling of collections}");

            if (p.name == "variantNames") {
              updateVariantNames(obj, reqBody.variantNames)
            }
          }
        } else {
          log.debug("checking for type of property -> ${p.type}")
          switch (p.type) {
            case Long.class:
              updateLongField(obj, p.name, reqBody[p.name])
              break;
            case Date.class:
              updateDateField(obj, p.name, reqBody[p.name])
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

    if (reqBody.ids || reqBody.identifiers) {
      def idmap = reqBody.ids ?: reqBody.identifiers
      updateIdentifiers(obj, idmap)
    }

    if (reqBody.groups || reqBody.curatoryGroups) {
      if (KBComponent.has(obj, 'curatoryGroups')) {

        def cgs = reqBody.groups ?: reqBody.curatoryGroups
        updateCuratoryGroups(obj, cgs)
      }
    }
    obj
  }

  public def updateAssoc(obj, prop, val) {
    def ptype = grailsApplication.mappingContext.getPersistentEntity(obj.class.name).getPropertyByName(prop).type
    def linkObj = ptype.get(val)

    if (linkObj) {
      if (ptype == RefdataValue) {
        String catName = classExaminationService.deriveCategoryForProperty(obj.class.name, prop)

        if (catName) {
          def cat = RefdataCategory.findByDesc(catName)

          if (linkObj in cat.values) {
            obj[prop] = linkObj
          } else {
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
        } else {
          log.error("Could not resolve category (${obj.niceName}.${p.name})!")
        }
      } else {
        obj[p.name] = linkObj
      }
    } else {
      obj.errors.reject(
        'default.not.found.message',
        [ptype, val] as Object[],
        '[{0} not found with id {1}]'
      )
      obj.errors.rejectValue(
        prop,
        'default.not.found.message'
      )
    }
  }

  public def updateIdentifiers(obj, ids) {
    Set new_ids = []

    if (obj && ids instanceof List) {
      ids.each { i ->
        Identifier id = null

        if (i instanceof Long) {
          id = Identifier.get(i)
        } else if (i instanceof Map) {
          if (i.value && i.namespace) {
            def ns = null

            if (i.namespace instanceof Long) {
              ns = IdentifierNamespace.get(i.namespace)?.value ?: null
            } else {
              ns = i.namespace
            }

            try {
              if (ns) {
                id = Identifier.lookupOrCreateCanonicalIdentifier(ns, i.value)
              }
            }
            catch (grails.validation.ValidationException ve) {
              obj.errors.reject(
                'identifier.value.IllegalIDForm',
                [i.value, ns] as Object[],
                '[Value {0} is not valid for namespace {1}]'
              )
              obj.errors.rejectValue(
                'ids',
                'identifier.value.IllegalIDForm'
              )
            }
          } else {
            obj.errors.reject(
              'identifier.value.IllegalIDForm',
              [i.value, ns] as Object[],
              '[Value {0} is not valid for namespace {1}]'
            )
            obj.errors.rejectValue(
              'ids',
              'identifier.value.IllegalIDForm'
            )
          }
        }

        if (id && !obj.errors) {
          new_ids << id
        }
      }

      if (!obj.hasErrors()) {
        new_ids.each { ni ->
          if (!obj.ids.contains(ni)) {
            obj.ids.add(ni)
          }
        }
        obj.ids.retainAll(new_ids)
      }
    }
    obj
  }

  public def updateCuratoryGroups(obj, cgs) {
    log.debug("Update curatory Groups ${cgs}")
    Set new_cgs = []

    cgs.each { cg ->
      def cg_obj = null

      if (cg instanceof String) {
        cg_obj = CuratoryGroup.findByNameIlike(cg)
      } else {
        cg_obj = CuratoryGroup.get(cg)
      }

      if (cg_obj) {
        new_cgs << cg_obj
      } else {
        obj.errors.reject(
          'component.addToList.denied.label',
          ['curatoryGroups'] as Object[],
          '[Could not process list of items for property {0}]'
        )
        obj.errors.rejectValue(
          'curatoryGroups',
          'component.addToList.denied.label'
        )
      }
    }

    if (!obj.hasErrors()) {
      new_cgs.each { c ->
        if (!obj.curatoryGroups.contains(c)) {
          log.debug("Adding new cg ${c}..")
          obj.curatoryGroups.add(c)
        } else {
          log.debug("Existing cg ${c}..")
        }
      }
      obj.curatoryGroups.retainAll(new_cgs)
    }
    log.debug("New cgs: ${obj.curatoryGroups}")
    obj
  }

  public def updateVariantNames(obj, vals) {
    def remaining = []
    def notFound = []

    try {
      vals.each {
        def newVariant = null
        if (it instanceof String) {
          if (it.trim()) {
            def nvn = GOKbTextUtils.normaliseString(it)
            def dupes = KBComponentVariantName.findByNormVariantNameAndOwner(nvn, obj)

            if (dupes) {
              log.debug("Not adding duplicate variant")
            } else {
              obj.addToVariantNames(variantName: it)
            }
          }
        } else {
          newVariant = KBComponentVariantName.get(it)

          if (newVariant && newVariant.owner == obj) {
            remaining << newVariant
          } else {
            notFound << it
          }
        }
      }

      if (notFound.size() == 0) {
        obj.variantNames.retainAll(remaining)
      } else {
        obj.errors.reject(
          'component.addToList.denied.label',
          ['variantNames'] as Object[],
          '[Could not process list of items for property {0}]'
        )
        obj.errors.rejectValue(
          'variantNames',
          'component.addToList.denied.label'
        )
      }
    }
    catch (Exception e) {
      obj.errors.reject(
        'component.addToList.denied.label',
        ['variantNames'] as Object[],
        '[Could not process list of items for property {0}]'
      )
      obj.errors.rejectValue(
        'variantNames',
        'component.addToList.denied.label'
      )
    }
    obj
  }

  public def updateLongField(obj, prop, val) {
    log.debug("Set simple prop ${prop} = ${val} (as Long)");
    try {
      obj[prop] = Long.parseLong(val);
    }
    catch (Exception e) {
      obj.errors.reject(
        'typeMismatch.java.lang.Long',
        [prop] as Object[],
        '[Invalid number value for property [{0}]]'
      )
      obj.errors.rejectValue(
        prop,
        'typeMismatch.java.lang.Long'
      )
    }
    obj
  }

  public def updateDateField(obj, prop, val) {
    if (val == null) {
      obj[prop] = null
    } else if (val.trim()) {
      LocalDateTime dateObj = GOKbTextUtils.completeDateString(val)

      if (dateObj) {
        ClassUtils.updateDateField(val, obj, prop)
      } else {
        obj.errors.reject(
          'typeMismatch.java.util.Date',
          [prop] as Object[],
          '[Invalid date value for property [{0}]]'
        )
        obj.errors.rejectValue(
          prop,
          'typeMismatch.java.util.Date'
        )
      }
      log.debug("Set simple prop ${prop} = ${val} (as date ${dateObj}))");
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
    } else if (obj.hasProperty('name')) {
      obj_label = obj.name
    } else if (obj.hasProperty('value')) {
      obj_label = obj.value
    } else if (obj.hasProperty('variantName')) {
      obj_label = obj.variantName
    }

    return obj_label
  }

  /**
   *  getEmbeddedJson : Map embedded object.
   * @param obj : The object to be mapped
   */

  public def getEmbeddedJson(obj, user) {
    def pars = [:]
    log.debug("Embedded object ${obj}")
    mapObjectToJson(obj, pars, user)
  }
}