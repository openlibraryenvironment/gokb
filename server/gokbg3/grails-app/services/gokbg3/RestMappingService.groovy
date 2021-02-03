package gokbg3

import com.k_int.ClassUtils

import java.time.LocalDateTime

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
  def messageSource
  def messageService
  def dateFormatService

  def defaultIgnore = [
    'bucketHash',
    'shortcode',
    'normname',
    'people',
    'lastSeen',
    'updateBenchmark',
    'systemComponent',
    'insertBenchmark',
    'componentHash',
    'subjects',
    'lastUpdateComment',
    'duplicateOf',
    'componentDiscriminator',
    'incomingCombos',
    'outgoingCombos'
  ]

  def defaultEmbed = [
    'ids',
    'variantNames',
    'additionalProperties',
    'reviewRequests'
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
    def nested = params['nested'] ? true : false
    def base = grailsApplication.config.serverURL + "/rest"
    def curatedClass = obj.respondsTo('curatoryGroups')
    def jsonMap = null
    def is_curator = true

    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    jsonMap = KBComponent.has(ClassUtils.deproxy(obj), 'jsonMapping') ? obj.jsonMapping : null

    if (KBComponent.has(ClassUtils.deproxy(obj), "restPath") && !jsonMap?.ignore?.contains('_links')) {
      result['_links'] = [:]
      result['_links']['self'] = ['href': base + obj.restPath + "/${obj.id}"]

      if (curatedClass && obj.curatoryGroups?.size() > 0) {
        is_curator = user?.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)
      }

      if (obj.class.simpleName == TitleInstancePackagePlatform) {
        is_curator = obj.pkg.curatoryGroups?.size() > 0 ? user?.curatoryGroups?.id.intersect(obj.pkg.curatoryGroups?.id) : true
      }

      result.type = obj.niceName

      def href = (obj.isEditable() && is_curator) || user?.isAdmin() ? base + obj.restPath + "/${obj.id}" : null
      result._links.update = ['href': href]
      result._links.delete = ['href': href]

      if (KBComponent.isAssignableFrom(obj.class)) {
        result._links.retire = ['href': href ? href + "/retire" : null]
      }
    }

    result['_embedded'] = [:]

    if (embed_active.size() == 0 && !nested) {
      if (KBComponent.isAssignableFrom(obj.class)) {
        embed_active = defaultEmbed
      }
      if (jsonMap?.defaultEmbeds?.size() > 0) {
        jsonMap.defaultEmbeds.each {
          if (!embed_active.contains(it)) {
            embed_active.add(it)
          }
        }
      }
    }

    result['id'] = obj.id

    pent.getPersistentProperties().each { p ->
      if (!defaultIgnore.contains(p.name) && (!jsonMap || !jsonMap.ignore.contains(p.name)) && (!include_list || include_list.contains(p.name))) {
        if (p instanceof Association) {
          if (p instanceof ManyToOne || p instanceof OneToOne) {
            // Set ref property
            if (user?.isAdmin() || p.type != User) {
              if (obj[p.name]) {
                def label = selectJsonLabel(obj[p.name])

                result[p.name] = [
                  'name': label,
                  'type': obj[p.name].niceName,
                  'id'  : obj[p.name].id
                ]

                if (p.name == 'namespace') {
                  result['namespace']['value'] = obj['namespace'].value
                }

                if (embed_active.contains(p.name)) {
                  result['_embedded'][p.name] = getEmbeddedJson(obj[p.name], user)
                }
              } else {
                result[p.name] = null
              }
            }
          } else {
            if ( (embed_active.contains(p.name) && (user?.isAdmin() || p.type != User))  || (!nested && p.name == 'reviewRequests' && user?.editorStatus) ) {
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
              result[p.name] = obj[p.name] ? dateFormatService.formatIsoTimestamp(obj[p.name]) : null
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
            RefdataValue combo_type = RefdataCategory.lookup('Combo.Type', obj.getComboTypeValue(cp))
            def chql = null
            def reverse = obj.isComboReverse(cp)

            if (reverse) {
              chql = "from Combo as c where c.toComponent = ? and c.type = ?"
            }
            else {
              chql = "from Combo as c where c.fromComponent = ? and c.type = ?"
            }
            def combo = Combo.executeQuery(chql, [obj, combo_type])

            if (combo.size() == 0) {
              result[cp] = null
            } else {
              cval = reverse ? combo[0].fromComponent : combo[0].toComponent
              result[cp] = ['id': cval.id, 'name': cval.name, 'type': cval.niceName, 'uuid': cval.uuid]
            }
          }

          if ( embed_active.contains(cp) ) {
            cval = obj[cp]
            result['_embedded'][cp] = cval ? getEmbeddedJson(cval, user) : null
          }
        }
        else {
          if( embed_active.contains(cp) ) {
            result['_embedded'][cp] = []

            def combos = obj.getCombosByPropertyName(cp)
            boolean reverse = obj.isComboReverse(cp)

            combos.each { c ->
              def linked_obj = getEmbeddedJson(reverse ? c.fromComponent : c.toComponent, user)

              if (c.status?.value == 'Active') {
                result['_embedded'][cp] << linked_obj
              }
              else {
                log.debug("Skipping ${c.status.value} combo..")
              }
            }
          }

          if (include_list?.contains('publisher') && TitleInstance.isAssignableFrom(obj.class)) {
            def pub = obj.currentPublisher

            result.publisher = pub ? getEmbeddedJson(pub, user) : null
          }
        }
      }
    }
    else if (obj.class == ReviewRequest && embed_active.contains('allocatedGroups')) {
      result.allocatedGroups = []

      obj.allocatedGroups?.each {
        result.allocatedGroups << [name: it.group.name, id: it.group.id]
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
      if (!toIgnore.contains(p.name) && !immutable.contains(p.name) && reqBody.keySet().contains(p.name)) {
        log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");

        if (p instanceof Association) {
          if (p instanceof ManyToOne || p instanceof OneToOne) {
            updateAssoc(obj, p.name, newVal)
          } else {
            // Add to collection
            log.debug("Skip generic handling of collections}");
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
    obj
  }

  public def updateAssoc(obj, prop, val) {
    log.debug("Update association $obj - $prop: $val")
    def ptype = grailsApplication.mappingContext.getPersistentEntity(obj.class.name).getPropertyByName(prop).type

    if ( val != null ) {
      if (ptype == RefdataValue) {
        def rdv = null

        if ( val == null ) {
          obj[prop] = null
        } else {
          String catName = classExaminationService.deriveCategoryForProperty(obj.class.name, prop)

          if (catName) {
            def cat = RefdataCategory.findByDesc(catName)

            if (val instanceof Integer) {
              rdv = RefdataValue.get(val)

              if (rdv) {
                if (rdv in cat.values) {
                  if (catName == 'KBComponent.Status') {
                    if (rdv.value == 'Deleted') {
                      obj.deleteSoft()
                    }
                    else if (rdv.value == 'Retired') {
                      obj.retire()
                    }
                    else if (rdv.value == 'Current') {
                      obj.setActive()
                    }
                    else if (rdv.value == 'Expected') {
                      obj.setExpected()
                    }
                  }
                  else {
                    obj[prop] = rdv
                  }
                } else {
                  obj.errors.reject(
                    'rdc.values.notFound',
                    [rdv, cat] as Object[],
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
            else if (val instanceof Map) {
              if (val.id) {
                rdv = RefdataValue.get(val.id)

                if (rdv) {
                  if (rdv in cat.values) {
                    if (catName == 'KBComponent.Status') {
                      if (rdv.value == 'Deleted') {
                        obj.deleteSoft()
                      }
                      else if (rdv.value == 'Retired') {
                        obj.retire()
                      }
                      else if (rdv.value == 'Current') {
                        obj.setActive()
                      }
                      else if (rdv.value == 'Expected') {
                        obj.setExpected()
                      }
                    }
                    else {
                      obj[prop] = rdv
                    }
                  } else {
                    obj.errors.reject(
                      'rdc.values.notFound',
                      [rdv, cat] as Object[],
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
                    [ptype, val.id] as Object[],
                    '[{0} not found with id {1}]'
                  )
                  obj.errors.rejectValue(
                    prop,
                    'default.not.found.message'
                  )
                }
              }
              else if (val.name) {
                rdv = RefdataCategory.lookup(catName, val.name)

                if (!rdv) {
                  obj.errors.reject(
                    'rdc.values.notFound',
                    [val.name, prop] as Object[],
                    '[{0} is not a valid value for property {1}!]'
                  )
                  obj.errors.rejectValue(
                    prop,
                    'rdc.values.notFound'
                  )
                }
                else {
                  if (catName == 'KBComponent.Status') {
                    if (rdv.value == 'Deleted') {
                      obj.deleteSoft()
                    }
                    else if (rdv.value == 'Retired') {
                      obj.retire()
                    }
                    else if (rdv.value == 'Current') {
                      obj.setActive()
                    }
                    else if (rdv.value == 'Expected') {
                      obj.setExpected()
                    }
                  }
                  else {
                    obj[prop] = rdv
                  }
                }
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
          } else {
            log.error("Could not resolve category (${obj.niceName}.${p.name})!")
          }
        }
      } else {
        def linkObj = null

        if (val instanceof Integer) {
          linkObj = ptype.get(val)
        }
        else if (val instanceof Map) {
          linkObj = val.id ? ptype.get(val.id) : null
        }

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
      obj[prop] = null
    }
  }

  @Transactional
  public def updateIdentifiers(obj, ids, boolean remove = true) {
    log.debug("updating ids ${ids}")
    def combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    def combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    def combo_id_type = RefdataCategory.lookup(Combo.RD_TYPE, "KBComponent.Ids")
    def id_combos = obj.getCombosByPropertyName('ids')
    def errors = []
    Set new_ids = []

    if (obj && ids instanceof Collection) {
      ids.each { i ->
        Identifier id = null
        def valid = true

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
                id = componentLookupService.lookupOrCreateCanonicalIdentifier(ns, i.value)
              }
            }
            catch (grails.validation.ValidationException ve) {
              log.debug("Could not create ID ${ns}:${i.value}")

              errors << messageService.processValidationErrors(ve.errors)
            }
          } else {
            errors << [message: messageService.resolveCode('identifier.value.IllegalIDForm', null, null), baddata: i]
            valid = false
          }
        }
        else {
          errors << [message: "Could not identify ID form!", baddata: i]
          valid = false
          log.error("Could not identify ID form!")
        }

        if (id && !obj.hasErrors() && valid) {
          log.debug("Adding id ${id} to current set")
          new_ids << id
        }
        else if (!id) {
          log.debug("No Identifier found for ID ${i}, or errors on object ..")
        }
      }

      if (errors.size() == 0) {
        new_ids.each { i ->

          def dupe = Combo.executeQuery("from Combo where type = ? and fromComponent = ? and toComponent = ?",[combo_id_type, obj, i])

          if (dupe.size() == 0) {
            def new_combo = new Combo(fromComponent: obj, toComponent: i, type: combo_id_type).save(flush:true)
          }
          else if (dupe.size() == 1 ) {
            if (dupe[0].status == combo_deleted) {
              log.debug("Matched ID combo was marked as deleted!")
              dupe[0].delete(flush:true)
              def new_combo = new Combo(fromComponent: obj, toComponent: i, type: combo_id_type).save(flush:true)
            }
            else {
              log.debug("Not adding duplicate ..")
            }
          }
          else {
            if (!errors.ids) {
              errors.ids = []
            }

            errors.ids << [message: "There seem to be duplicate links for an identifier against this title!", baddata: i]
            log.error("Multiple ID combos for ${obj} -- ${i}!")
          }
        }

        if (remove) {
          Iterator items = id_combos.iterator();
          List removedIds = []
          Object element;
          while (items.hasNext()) {
            element = items.next();
            if (!new_ids.contains(element.toComponent)) {
              // Remove.
              log.debug("Removing newly missing ID ${element.toComponent}")
              element.status = combo_deleted
              removedIds.add(element.toComponent)
            }
          }

          if (removedIds) {
            def idString = ""

            removedIds.each {
              idString += "${it.namespace.value}:${it.value} "
            }

            obj.lastUpdateComment = "Removed Ids: ${idString}"
          }
        }
      }
    }
    else {
      log.error("Object ${obj} not found or illegal id format")
      errors << [message: "Expected an Array to process!", baddata: ids]
    }

    errors
  }

  @Transactional
  public def updateCuratoryGroups(obj, cgs, boolean remove = true) {
    log.debug("Update curatory Groups ${cgs}")
    Set new_cgs = []
    def errors = []
    def current_cgs = obj.getCombosByPropertyName('curatoryGroups')
    RefdataValue combo_type = RefdataCategory.lookup('Combo.Type', obj.getComboTypeValue('curatoryGroups'))

    cgs.each { cg ->
      def cg_obj = null

      if (cg instanceof String) {
        cg_obj = CuratoryGroup.findByNameIlike(cg)
      } else if (cg instanceof Integer){
        cg_obj = CuratoryGroup.get(cg)
      }
      else if (cg instanceof Map) {
        cg_obj = CuratoryGroup.get(cg.id)
      }

      if (cg_obj) {
        new_cgs << cg_obj
      } else {
        errors << [message: "Unable to lookup curatory group!", baddata: cg]
      }
    }

    if (errors.size() == 0) {
      new_cgs.each { c ->
        if (!obj.curatoryGroups.contains(c)) {
          def new_combo = new Combo(fromComponent: obj, toComponent: c, type: combo_type).save(flush:true)
        } else {
          log.debug("Existing cg ${c}..")
        }
      }

      if (remove) {
        Iterator items = current_cgs.iterator();
        Object element;
        while (items.hasNext()) {
          element = items.next();
          if (!new_cgs.contains(element.toComponent)) {
            // Remove.
            element.delete()
          }
        }
      }
    }
    log.debug("New cgs: ${obj.curatoryGroups}")
    errors
  }

  public def updateVariantNames(obj, vals, boolean remove = true) {
    log.debug("Update Variants ${vals} ..")
    def remaining = []
    def notFound = []
    def toRemove = []

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
              newVariant = obj.ensureVariantName(it)

              if (newVariant) {
                log.debug("Added variant ${newVariant}")
                remaining << newVariant
              }
              else {
                log.debug("Could not add variant ${it}!")
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
          } else {
            log.debug("Ignoring empty variant")
          }
        } else if (it instanceof Integer) {
          newVariant = KBComponentVariantName.get(it)

          if (newVariant && newVariant.owner == obj) {
            remaining << newVariant
          } else {
            notFound << it
          }
        } else if (it instanceof Map) {
          if (it.id && it.id instanceof Integer) {
            newVariant = KBComponentVariantName.get(it.id)

            if (newVariant && newVariant.owner == obj) {
              remaining << newVariant
            } else {
              notFound << it
            }
          }
          else if (it.variantName) {
            def nvn = GOKbTextUtils.normaliseString(it.variantName)
            def dupes = KBComponentVariantName.findByNormVariantNameAndOwner(nvn, obj)

            if (dupes) {
              log.debug("Not adding duplicate variant")
              remaining << dupes
            } else {
              newVariant = obj.ensureVariantName(it.variantName)

              if (newVariant) {
                log.debug("Added variant ${newVariant}")
                if (it.locale) {
                  newVariant = updateAssoc(newVariant, 'locale', it.locale)
                } else {
                  newVariant.locale = null
                }

                if (it.variantType) {
                  newVariant = updateAssoc(newVariant, 'variantType', it.variantType)
                } else {
                  newVal.variantType = null
                }

                remaining << newVariant
              } else {
                log.debug("Could not add variant ${it}!")
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
          }
          else {
            log.debug("Unable to process map ${it}!")
          }
        }
      }

      if (notFound.size() == 0) {
        if (!obj.hasErrors() && remove) {
          obj.variantNames.each { vn ->
            if (!remaining.contains(vn)) {
              toRemove.add(vn.id)
            }
          }

          toRemove.each {
            obj.removeFromVariantNames(KBComponentVariantName.get(it))
          }

          log.debug("New List has ${obj.variantNames.size()} items!")
        }
        else {
          log.debug("Not removing: (remove: ${remove} - errors: ${obj.errors})")
        }
      } else {
        log.debug("Unable to look up variants ..")
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
      log.debug("Unable to process variants:", e)
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

  @Transactional
  public def updatePublisher(obj, new_pubs, boolean remove = true) {
    def errors = []
    def publisher_combos = obj.getCombosByPropertyName('publisher')
    def combo_type = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Publisher')

    String propName = obj.isComboReverse('publisher') ? 'fromComponent' : 'toComponent'
    String tiPropName = obj.isComboReverse('publisher') ? 'toComponent' : 'fromComponent'
    def pubs_to_add = []

    if (new_pubs instanceof Collection) {
      new_pubs.each { pub ->
        if (!pubs_to_add.collect { it.id == pub}) {
          pubs_to_add << Org.get(pub)
        }
        else {
          log.warn("Duplicate for incoming publisher ${pub}!")
        }
      }
    }
    else {
      if (!pubs_to_add.collect { it.id == new_pubs}) {
        pubs_to_add << Org.get(new_pubs)
      }
      else {
        log.warn("Duplicate for incoming publisher ${new_pubs}!")
      }
    }

    pubs_to_add.each { publisher ->
      boolean found = false
      for ( int i=0; !found && i<publisher_combos.size(); i++) {
        Combo pc = publisher_combos[i]
        def idMatch = pc."${propName}".id == publisher.id

        if (idMatch) {
          found = true
        }
      }

      if (!found) {
        def new_combo = new Combo(fromComponent: obj, toComponent: publisher, type: combo_type).save(flush: true)
      } else {
        log.debug "Publisher ${publisher.name} already set against '${obj.name}'"
      }
    }

    if (remove) {
      Iterator items = publisher_combos.iterator();
      Object element;
      while (items.hasNext()) {
        element = items.next();
        if (!pubs_to_add.contains(element.toComponent) && !pubs_to_add.contains(element.fromComponent)) {
          // Remove.
          element.delete()
        }
      }
    }
    errors
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

  private String selectJsonLabel(obj) {
    def obj_label = null

    if (obj.hasProperty('jsonLabel')) {
      obj_label = obj[obj.jsonLabel]
    } else if (obj.hasProperty('value')) {
      obj_label = obj.value
    } else if (obj.hasProperty('name')) {
      obj_label = obj.name
    } else if (obj.hasProperty('variantName')) {
      obj_label = obj.variantName
    } else if (obj.hasProperty('propertyName')) {
      obj_label = obj.propertyName
    }

    return obj_label
  }

  /**
   *  getEmbeddedJson : Map embedded object.
   * @param obj : The object to be mapped
   */

  public def getEmbeddedJson(obj, user) {
    def pars = ['nested': true]
    log.debug("Embedded object ${obj}")
    mapObjectToJson(obj, pars, user)
  }
}