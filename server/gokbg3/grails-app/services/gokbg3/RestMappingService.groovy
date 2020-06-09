package gokbg3

import com.k_int.ClassUtils
import grails.core.GrailsClass

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.gokb.cred.*
import org.gokb.DomainClassExtender
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
    def sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    def embed_active = params['_embed']?.split(',') ?: []
    def include_list = params['_include']?.split(',') ?: null
    def exclude_list = params['_exclude']?.split(',') ?: null
    def base = grailsApplication.config.serverURL + "/rest"
    def jsonMap = null
    def is_curator = true

    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    jsonMap = KBComponent.has(ClassUtils.deproxy(obj), 'jsonMapping') ? obj.jsonMapping : null

    if (KBComponent.has(ClassUtils.deproxy(obj), "restPath") && !jsonMap?.ignore?.contains('_links')) {
      result['_links'] = [:]
      result['_links']['self'] = ['href': base + obj.restPath + "/${obj.id}", 'method': "GET"]

      if (obj.respondsTo('curatoryGroups') && obj.curatoryGroups?.size() > 0) {
        is_curator = user?.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)
      }

      if (obj.class.simpleName == TitleInstancePackagePlatform) {
        is_curator = obj.pkg.curatoryGroups?.size() > 0 ? user?.curatoryGroups?.id.intersect(obj.pkg.curatoryGroups?.id) : true
      }

      if (is_curator || user?.isAdmin()) {
        result._links.update = ['href': base + obj.restPath + "/${obj.id}", 'method': "PUT"]
        result._links.delete = ['href': base + obj.restPath + "/${obj.id}", 'method': "DELETE"]

        if (KBComponent.isAssignableFrom(obj.class)) {
          result._links.retire = ['href': base + obj.restPath + "/${obj.id}/retire"]
        }
      }
    }

    if (embed_active.size() > 0 || jsonMap?.defaultEmbeds?.size() > 0) {
      result['_embedded'] = [:]
    }

    if (embed_active.size() == 0 && jsonMap?.defaultEmbeds?.size() > 0) {
      embed_active = jsonMap.defaultEmbeds
    }

    result['id'] = obj.id

    pent.getPersistentProperties().each { p ->
      if (!defaultIgnore.contains(p.name) && (!jsonMap || !jsonMap.ignore.contains(p.name)) && (!include_list || include_list.contains(p.name))) {
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
              result[p.name] = obj[p.name] ? sdf.format(obj[p.name]) : null
              break;
            default:
              result[p.name] = obj[p.name]
              break;
          }
        }
      }
    }
    // Handle combo properties
    if (KBComponent.isAssignableFrom(obj.class)) {
      def combo_props = obj.allComboPropertyNames

      combo_props.each { cp ->
        if (obj.getCardinalityFor(obj.class,cp) == 'hasByCombo') {
          def cval = null

          if ( (include_list && include_list?.contains(cp)) || (!include_list && jsonMap?.defaultLinks?.contains(cp)) ) {

            cval = obj[cp]

            if (cval == null) {
              result[cp] = null
            } else {
              result[cp] = ['id': cval.id, 'name': cval.name, 'uuid': cval.uuid]
            }
          }

          if ( embed_active.contains(cp) ) {
            cval = obj[cp]
            result['_embedded'][cp] = getEmbeddedJson(cval, user)
          }
        }
        else {
          if( embed_active.contains(cp) ) {
            result['_embedded'][cp] = []
            log.debug("Mapping ManyByCombo ${cp} ${obj[cp]}")
            obj[cp].each {
              result['_embedded'][cp] << getEmbeddedJson(it, user)
            }
          }
        }
      }
    }
    result
  }

  /**
   *  updateObject : Updates an domain class object based on a provided object map.
   * @param obj : The object to be updated
   * @param reqBody : The map of properties to be updated
   */

  @Transactional
  def updateObject(obj, jsonMap, reqBody) {
    log.debug("Update object ${obj} - ${reqBody}")
    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    def toIgnore = defaultIgnore + (jsonMap?.ignore ?: [])
    def immutable = defaultImmmutable + (jsonMap?.immutable ?: [])

    log.debug("Ignore: ${toIgnore}, Immutable: ${immutable}")
    pent.getPersistentProperties().each { p -> // list of PersistentProperties
      def newVal = reqBody[p.name]
      if (!toIgnore.contains(p.name) && !immutable.contains(p.name) && reqBody[p.name]) {
        log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");

        if (p instanceof Association) {
          if (p instanceof ManyToOne || p instanceof OneToOne) {
            updateAssoc(obj, p.name, newVal)
          } else {
            // Add to collection
            log.debug("Skip generic handling of collections}");

            if (p.name == "variantNames") {
              updateVariantNames(obj, reqBody.variantNames)
            }
          }
        } else {
          log.debug("checking for type of property ${p.name} -> ${p.type}")
          switch (p.type) {
            case Long.class:
              updateLongField(obj, p.name, newVal)
              break;
            case Date.class:
              updateDateField(obj, p.name, newVal)
              break;
            default:
              log.debug("Default for type ${p.type}")
              log.debug("Set simple prop ${p.name} = ${newVal}");
              obj[p.name] = newVal
              break;
          }
        }
      }
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

    if ( val != null ) {
      if (ptype == RefdataValue) {
        def rdv = null

        if ( val == null ) {
          obj[prop] = null
        } else {
          String catName = classExaminationService.deriveCategoryForProperty(obj.class.name, prop)

          if (catName && catName != 'KBComponent.Status') {
            def cat = RefdataCategory.findByDesc(catName)

            if (val instanceof Integer) {
              rdv = RefdataValue.get(val)

              if (rdv) {
                if (rdv in cat.values) {
                  obj[prop] = rdv
                } else {
                  obj.errors.reject(
                    'rdc.values.notFound',
                    [linkObj.id, cat] as Object[],
                    '[Value {0} is not valid for category {1}!]'
                  )
                  obj.errors.rejectValue(
                    prop,
                    'rdc.values.notFound'
                  )
                }
              }
              else {
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
            else {
              rdv = RefdataCategory.lookup(catName, val)

              if (!rdv) {
                obj.errors.reject(
                  'rdc.values.notFound',
                  [val, prop] as Object[],
                  '[{0} is not a valid value for property {1}!]'
                )
                obj.errors.rejectValue(
                  prop,
                  'rdc.values.notFound'
                )
              }
              else {
                obj[prop] = rdv
              }
            }
          } else if (!catname) {
            log.error("Could not resolve category (${obj.niceName}.${p.name})!")
          } else {
            log.debug("Status updating denied in general PUT/PATCH request!")
          }
        }
      } else {
        def linkObj = ptype.get(val)

        if (linkObj) {
          obj[prop] = linkObj
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
    }
    else {
      obj[p.name] = null
    }
  }

  public def updateIdentifiers(obj, ids) {
    log.debug("updating ids ${ids}")
    def combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    def combo_type_id = RefdataCategory.lookup('Combo.Type','KBComponent.Ids')
    Set new_ids = []

    if (obj && ids instanceof Collection) {
      ids.each { i ->
        Identifier id = null

        if (i instanceof Integer) {
          id = Identifier.get(i)
        }
        else if (i instanceof Map) {
          def ns_val = i.namespace ?: i.type

          if (i.value && ns_val) {
            def ns = null

            if (ns_val instanceof String) {
              ns = ns_val
            }
            else if (ns_val) {
              ns = IdentifierNamespace.get(ns_val)?.value ?: null
            }

            try {
              if (ns) {
                id = Identifier.lookupOrCreateCanonicalIdentifier(ns, i.value)
              }
            }
            catch (grails.validation.ValidationException ve) {
              log.debug("Could not create ID ${ns}:${i.value}")
              obj.errors.reject(
                'identifier.value.IllegalIDForm',
                [i.value, ns_val] as Object[],
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
              [i.value, ns_val] as Object[],
              '[Value {0} is not valid for namespace {1}]'
            )
            obj.errors.rejectValue(
              'ids',
              'identifier.value.IllegalIDForm'
            )
          }
        }
        else {
          log.error("Could not identify ID form!")
        }

        if ( id && !obj.hasErrors() ) {
          log.debug("Adding id ${id} to current set")
          new_ids << id
        }
        else {
          log.debug("No Identifier found for ID ${i}, or errors on object ..")
        }
      }
    }
    else {
      log.error("Object ${obj} not found or illegal id format")
    }
    new_ids
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
        if (!obj.hasErrors()) {
          obj.variantNames.retainAll(remaining)
        }
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
    def pars = ['_embed': null]
    log.debug("Embedded object ${obj}")
    mapObjectToJson(obj, pars, user)
  }
}