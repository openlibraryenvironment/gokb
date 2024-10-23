package gokbg3

import com.k_int.ClassUtils

import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId

import org.gokb.cred.*
import org.gokb.GOKbTextUtils
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

import grails.gorm.transactions.Transactional

import io.micronaut.http.uri.UriBuilder

class RestMappingService {
  def grailsApplication
  def classExaminationService
  def componentLookupService
  def componentUpdateService
  def messageService
  def dateFormatService
  def validationService

  def defaultIgnore = [
      'bucketHash',
      'shortcode',
      'normname',
      'people',
      'lastSeen',
      'provenance',
      'reference',
      'updateBenchmark',
      'systemComponent',
      'insertBenchmark',
      'componentHash',
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
   * @param proxy : The object to be mapped
   * @param params : The map of request parameters
   */

  def mapObjectToJson(proxy, params, def user = null) {
    log.debug("mapObjectToJson: ${proxy.class.name} -- ${params}")
    def obj = ClassUtils.deproxy(proxy).refresh()
    def result = [:]
    def embed_active = params['_embed']?.split(',') ?: []
    def include_list = params['_include']?.split(',') ?: null
    def exclude_list = params['_exclude']?.split(',') ?: null
    def nested = params['nested'] ? true : false
    def base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
    def curatedClass = obj.respondsTo('curatoryGroups')
    def jsonMap = null
    def is_curator = user ? componentUpdateService.isUserCurator(obj, user) : false

    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

    jsonMap = KBComponent.has(obj, 'jsonMapping') ? obj.jsonMapping : null

    if (KBComponent.has(obj, "restPath") && !jsonMap?.ignore?.contains('_links')) {
      result['_links'] = [:]
      result['_links']['self'] = ['href': base + obj.restPath + "/${obj.id}"]

      result.type = obj.niceName

      def href = ((obj.isEditable() && is_curator) || user?.isAdmin()) ? base + obj.restPath + "/${obj.id}" : null
      result._links.update = ['href': href]
      result._links.delete = ['href': href]

      if (KBComponent.isAssignableFrom(obj.class)) {
        result._links.retire = ['href': href ? href + "/retire" : null]
      }
    }

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
    if (embed_active.size() > 0) {
      result['_embedded'] = [:]
      log.debug("Embeds: ${embed_active}")
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
                def assoc_obj = ClassUtils.deproxy(obj[p.name])

                result[p.name] = [
                    'name': label,
                    'type': assoc_obj.niceName,
                    'id'  : assoc_obj.id
                ]

                if (p.type == IdentifierNamespace) {
                  result[p.name]['value'] = assoc_obj.value
                }

                if (embed_active.contains(p.name)) {
                  result['_embedded'][p.name] = getEmbeddedJson(assoc_obj, user)
                }
              }
              else {
                result[p.name] = null
              }
            }
          }
          else {
            if ((embed_active.contains(p.name) && (user?.isAdmin() || p.type != User)) || (!nested && p.name == 'reviewRequests' && user?.editorStatus)) {
              log.debug("Handling embeds for ${p.name}: ${obj[p.name]}")
              result['_embedded'][p.name] = []

              obj[p.name].each { ao ->
                def assoc_obj = ClassUtils.deproxy(ao)

                if (assoc_obj instanceof ComponentSubject) {
                  assoc_obj = assoc_obj.subject
                  log.debug("Using subject ${assoc_obj} for embed mapping ..")
                }

                result['_embedded'][p.name] << getEmbeddedJson(assoc_obj, user)

                log.debug("${result['_embedded'][p.name]}")
              }
            }
          }
        }
        else {
          switch (p.type) {
            case Float.class:
              if (p.name == 'price') {
                String pstring = obj[p.name] ? "${obj[p.name].round(2)}" : null

                if (pstring) {
                  if (!pstring.contains('.')) {
                    pstring += ".00"
                  }
                  else if (pstring.indexOf('.') == pstring.length() - 2) {
                    pstring += "0"
                  }
                }

                result[p.name] = pstring
                break
              }
            case Long.class:
              result[p.name] = obj[p.name] ? "${obj[p.name]}" : null
              break;

            case LocalDate.class:
              new_obj[p.name] = LocalDate.parse(params[p.name])
              break;

            case Date.class:
              if (p.name == 'lastUpdated' || p.name == 'dateCreated') {
                result[p.name] = obj[p.name] ? dateFormatService.formatIsoTimestamp(obj[p.name]) : null
              }
              else {
                result[p.name] = obj[p.name] ? dateFormatService.formatDate(obj[p.name]) : null
              }
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
        if (obj.getCardinalityFor(obj.class, cp) == 'hasByCombo') {
          def cval = null

          if ((include_list && include_list?.contains(cp)) || (!include_list && jsonMap?.defaultLinks?.contains(cp))) {
            RefdataValue combo_type = RefdataCategory.lookup('Combo.Type', obj.getComboTypeValue(cp))
            def chql = null
            def reverse = obj.isComboReverse(cp)

            if (reverse) {
              chql = "from Combo as c where c.toComponent = :o and c.type = :ct"
            }
            else {
              chql = "from Combo as c where c.fromComponent = :o and c.type = :ct"
            }
            def combo = Combo.executeQuery(chql, [o: obj, ct: combo_type])

            if (combo.size() == 0) {
              result[cp] = null
            }
            else {
              cval = reverse ? ClassUtils.deproxy(combo[0].fromComponent) : ClassUtils.deproxy(combo[0].toComponent)
              result[cp] = ['id': cval.id, 'name': cval.name, 'type': cval.niceName, 'uuid': cval.uuid]
            }
          }

          if (embed_active.contains(cp)) {
            cval = obj[cp]
            result['_embedded'][cp] = cval ? getEmbeddedJson(cval, user) : null
          }
        }
        else {
          if (embed_active.contains(cp)) {
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
      def inProgress = RefdataCategory.lookup('AllocatedReviewGroup.Status', 'In Progress')

      obj.allocatedGroups?.each {
        if (it.status == inProgress){
          result.allocatedGroups << [name: it.group.name, id: it.group.id]
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
      if (!toIgnore.contains(p.name) && !immutable.contains(p.name) && reqBody.keySet().contains(p.name)) {
        log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");

        if (p instanceof Association) {
          if (p instanceof ManyToOne || p instanceof OneToOne) {
            updateAssoc(obj, p.name, newVal)
          }
          else {
            // Add to collection
            log.debug("Skip generic handling of collections ..")
          }
        }
        else {
          log.debug("checking for type of property ${p.name} -> ${p.type}")
          switch (p.type) {
            case Long.class:
              updateLongField(obj, p.name, newVal)
              break;
            case Date.class:
              updateDateField(obj, p.name, newVal)
              break;
            case String.class:
              obj[p.name] = newVal ? newVal.trim() : null
              break;
            default:
              log.debug("Default for type ${p.type}")
              log.debug("Set simple prop ${p.name} = ${newVal}")
              obj[p.name] = newVal
              break;
          }
        }
      }
    }
    if(obj.validate()) {
      obj.save()
    } else {
      obj
    }
  }

  @Transactional
  def updateAssoc(obj, prop, val, def cat = null) {
    log.debug("Update association $obj - $prop: $val")
    def ptype = grailsApplication.mappingContext.getPersistentEntity(obj.class.name).getPropertyByName(prop).type

    if (val != null) {
      if (ptype == RefdataValue) {
        def rdv = null
        String catName = cat ? cat.desc : classExaminationService.deriveCategoryForProperty(obj.class.name, prop)

        if (!cat) {
          if (catName) {
            cat = RefdataCategory.findByDesc(catName)
          }

          if (!cat) {
            def catParts = catName.split('.')

            if (catParts.size() == 2) {
              cat = RefdataCategory.findByDesc(catParts[1])
            }
          }
        }

        if (cat) {
          if (val instanceof Integer) {
            rdv = RefdataValue.get(val)

            if (rdv) {
              if (rdv in cat.values) {
                if (catName == 'KBComponent.Status') {
                  updateStatus(obj, rdv.value)
                }
                else {
                  obj[prop] = rdv
                }
              }
              else {
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
            if (val.id && val.id != null) {
              log.debug("Assign by id")
              rdv = RefdataValue.get(val.id)

              if (rdv) {
                if (rdv in cat.values) {
                  if (catName == 'KBComponent.Status') {
                    updateStatus(obj, rdv.value)
                  }
                  else {
                    obj[prop] = rdv
                  }
                }
                else {
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
                log.debug("Unable to fetch rdv by ID")
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
              log.debug("Assign by value")
              rdv = RefdataCategory.lookup(catName, val.name)

              if (!rdv) {
                log.debug("Unable to fetch rdv by value")
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
                  updateStatus(obj, rdv.value)
                }
                else {
                  obj[prop] = rdv
                }
              }
            }
            else {
              log.error("Unable to handle value map ${val}")
            }
          }
          else {
            rdv = RefdataCategory.lookup(catName, val)

            if (!rdv) {
              log.debug("Unable to lookup rdv for ${val}")
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
              if (catName == 'KBComponent.Status') {
                updateStatus(obj, rdv.value)
              }
              else {
                obj[prop] = rdv
              }
            }
          }
        }
        else {
          log.error("Could not resolve category (${obj.niceName}.${p.name})!")
        }
      }
      else {
        log.debug("Handling non-refdata association")
        def linkObj = null

        if (val instanceof Integer) {
          linkObj = ptype.get(val)
        }
        else if (val instanceof Map) {
          linkObj = val.id ? ptype.get(val.id) : null
        }

        if (linkObj) {
          obj[prop] = linkObj
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
    }
    else {
      log.debug("Set value to null")
      obj[prop] = null
    }

    obj
  }

  @Transactional
  public def updateIdentifiers(obj, ids, boolean remove = true) {
    log.debug("updating ids ${ids}")
    def combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    def combo_id_type = RefdataCategory.lookup(Combo.RD_TYPE, "KBComponent.Ids")
    def id_combos = obj.getCombosByPropertyNameAndStatus('ids', 'Active')
    def result = [changed: false, errors: []]
    Set new_ids = []

    if (obj && ids instanceof Collection) {
      ids?.each { i ->
        Identifier id = null
        def valid = true

        if (i instanceof Integer) {
          id = Identifier.get(i)
        }
        else if (i instanceof Map) {
          if (i.id instanceof Integer) {
            id = Identifier.get(i.id)
          }
          else {
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

                  if (!id) {
                    result.errors << [message: "Identifier ${ns_val}:${i.value} is invalid!", baddata: i.value, messageCode: 'identifier.validation.generic']
                    valid = false
                  }
                }
                else {
                  log.warn("Unable to determine namespace ${ns_val}!")

                  if (!id) {
                    result.errors << [message: "Unable to reference namespace ${ns_val}!", baddata: i.value, messageCode: 'identifier.validation.namespace']
                    valid = false
                  }
                }
              }
              catch (grails.validation.ValidationException ve) {
                log.debug("Could not create ID ${ns}:${i.value}")

                result.errors << messageService.processValidationErrors(ve.errors)
              }
            }
            else {
              result.errors << [message: messageService.resolveCode('identifier.value.IllegalIDForm', null, null), baddata: i]
              valid = false
            }
          }
        }
        else {
          result.errors << [message: "Could not identify ID form!", baddata: i]
          valid = false
          log.error("Could not identify ID form!")
        }

        if (id && valid) {
          log.debug("Adding id ${id} to current set")
          new_ids << id
        }
        else if (!id) {
          log.debug("No Identifier found for ID ${i}, or errors on object ..")
        }
      }

      if (result.errors.size() == 0) {
        new_ids.each { i ->

          def dupe = Combo.executeQuery("from Combo where type = :ct and fromComponent = :fc and toComponent = :tc", [ct: combo_id_type, fc: obj, tc: i])

          if (dupe.size() == 0) {
            new Combo(fromComponent: obj, toComponent: i, type: combo_id_type).save(flush: true, failOnError: true)
            result.changed = true
          }
          else if (dupe.size() == 1) {
            if (dupe[0].status == combo_deleted) {
              log.debug("Matched ID combo was marked as deleted!")
              dupe[0].delete(flush: true)
              new Combo(fromComponent: obj, toComponent: i, type: combo_id_type).save(flush: true, failOnError: true)
              result.changed = true
            }
            else {
              log.debug("Not adding duplicate ..")
            }
          }
          else {
            result.errors << [message: "There seem to be duplicate links for an identifier against this title!", baddata: i]
            log.error("Multiple ID combos for ${obj} -- ${i}!")
          }
        }

        if (remove && result.errors.size() == 0) {
          Iterator items = id_combos.iterator()
          List removedIds = []
          Object element
          while (items.hasNext()) {
            element = items.next()
            if (!new_ids.contains(element.toComponent)) {
              // Remove.
              log.debug("Removing newly missing ID ${element.toComponent}")
              element.status = combo_deleted
              removedIds.add(element.toComponent)
              result.changed = true
            }
          }
        }
      }
    }
    else {
      log.error("Object ${obj} not found or illegal id format")
      result.errors << [message: "Expected an Array to process!", baddata: ids]
    }

    result
  }

  @Transactional
  public def updateStatus(obj, val) {
    if (val == 'Deleted') {
      obj.deleteSoft()

      componentUpdateService.closeConnectedReviews(obj)
    }
    else if (val == 'Retired') {
      obj.retire()
    }
    else if (val == 'Current') {
      obj.setActive()
    }
    else if (val == 'Expected') {
      obj.setExpected()
    }
    else {
      obj.errors.reject(
          'rdc.values.notFound',
          [val] as Object[],
          '[{0} is not a valid status value!]'
      )
      obj.errors.rejectValue(
          prop,
          'rdc.values.notFound'
      )
    }

    obj
  }


  @Transactional
  public def updateCuratoryGroups(obj, cgs, boolean remove = true) {
    log.debug("Update curatory Groups ${cgs}")
    Set new_cgs = []
    def result = [changed: false, errors: []]

    CuratoryGroup.withTransaction {
      def current_cgs = obj.getCombosByPropertyName('curatoryGroups')
      RefdataValue combo_type = RefdataCategory.lookup('Combo.Type', obj.getComboTypeValue('curatoryGroups'))

      cgs?.each { cg ->
        def cg_obj = null

        if (cg instanceof String) {
          cg_obj = CuratoryGroup.findByNameIlike(cg)
        }
        else if (cg instanceof Integer) {
          cg_obj = CuratoryGroup.get(cg)
        }
        else if (cg instanceof Map) {
          cg_obj = CuratoryGroup.get(cg.id)
        }

        if (cg_obj) {
          new_cgs << cg_obj
        }
        else {
          result.errors << [message: "Unable to lookup curatory group!", baddata: cg]
        }
      }

      if (result.errors.size() == 0) {
        new_cgs.each { c ->
          if (!obj.curatoryGroups.contains(c)) {
            new Combo(fromComponent: obj, toComponent: c, type: combo_type).save(flush: true, failOnError: true)
            result.changed = true
          }
          else {
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
              result.changed = true
            }
          }
        }
      }
    }
    log.debug("New cgs: ${obj.curatoryGroups}")
    result
  }

  @Transactional
  public def updateVariantNames(obj, vals, boolean remove = true) {
    log.debug("Update Variants ${vals} ..")
    def result = [changed: false, errors: []]
    def changed = false
    def remaining = []
    def notFound = []
    def toRemove = []

    try {
      KBComponentVariantName.withTransaction {
        vals?.each {
          def newVariant = null

          if (it instanceof String) {
            if (it.trim()) {
              def nvn = GOKbTextUtils.normaliseString(it)
              def dupes = KBComponentVariantName.findByNormVariantNameAndOwner(nvn, obj)

              if (dupes) {
                log.debug("Not adding duplicate variant")
              }
              else {
                newVariant = obj.ensureVariantName(it)

                if (newVariant) {
                  log.debug("Added variant ${newVariant}")
                  changed = true
                  remaining << newVariant
                }
                else {
                  log.debug("Could not add variant ${it}!")
                  result.errors << [message: "Could not add variant ${it} since it is already a variant for another component.", code: 'inUse', baddata: it]
                }
              }
            }
            else {
              log.debug("Ignoring empty variant")
            }
          }
          else if (it instanceof Integer) {
            newVariant = KBComponentVariantName.get(it)

            if (newVariant && newVariant.owner == obj) {
              remaining << newVariant
            }
            else {
              notFound << it
            }
          }
          else if (it instanceof Map) {
            if (it.id && it.id instanceof Integer) {
              newVariant = KBComponentVariantName.get(it.id)

              if (newVariant && newVariant.owner == obj) {
                remaining << newVariant
              }
              else {
                notFound << it
              }
            }
            else if (it.variantName) {
              def nvn = GOKbTextUtils.normaliseString(it.variantName)
              def dupes = KBComponentVariantName.findByNormVariantNameAndOwner(nvn, obj)

              if (dupes) {
                log.debug("Not adding duplicate variant")

                if (!remaining.contains(dupes))
                  remaining << dupes
              }
              else {
                newVariant = obj.ensureVariantName(it.variantName)

                log.debug("Ensured variant: ${newVariant}")

                if (newVariant) {
                  changed = true

                  if (it.locale) {
                    newVariant = updateAssoc(newVariant, 'locale', it.locale, RefdataCategory.findByDesc(KBComponent.RD_LANGUAGE))
                  }
                  else {
                    newVariant.locale = null
                  }

                  if (it.variantType) {
                    newVariant = updateAssoc(newVariant, 'variantType', it.variantType)
                  }
                  else {
                    newVariant.variantType = null
                  }

                  if (!newVariant.hasErrors()) {
                    newVariant.save(flush:true)
                  }
                  else {
                    log.error("Unable to set details for variant: ${newVariant.errors}")
                  }

                  log.debug("${newVariant.variantName} (${newVariant.locale})")

                  remaining << newVariant
                }
                else {
                  log.debug("Could not add variant ${it}!")
                  result.errors << [message: "Could not add variant ${it.variantName} since it is already a variant for another component.", code: 'inUse', baddata: it]
                }
              }
            }
            else {
              log.debug("Unable to process map ${it}!")
            }
          }
        }

        if (notFound.size() == 0) {
          if (!result.errors && remove) {
            obj.variantNames.each { vn ->
              if (!remaining.contains(vn)) {
                toRemove.add(vn.id)
                changed = true
              }
            }

            toRemove.each {
              obj.removeFromVariantNames(KBComponentVariantName.get(it))
            }
          }
          else {
            log.debug("Not removing: (remove: ${remove} - errors: ${obj.errors})")
          }
        }
        else {
          log.debug("Unable to look up variants ..")
          notFound.each {
            result.errors << [message: "Could not add variant ${it} since it is already a variant for another component.", code: 'inUse', baddata: it]
          }
        }

        if (changed) {
          obj.lastSeen = System.currentTimeMillis()
        }
      }
    }
    catch (Exception e) {
      log.debug("Unable to process variants:", e)
      result.errors << [message: "Unable to process variants!", code: 500, baddata: vals]
    }
    result
  }

  @Transactional
  def updatePrices(obj, prices, boolean remove = true) {
    def result = [changed: false, errors: []]
    def existing_prices_ids = obj.prices?.collect { it.id }
    def new_prices = []

    try {
      ComponentPrice.withTransaction {
        prices?.each { price ->
          if (price.price || price.amount) {
            boolean valid = true
            LocalDateTime parsedStart = GOKbTextUtils.completeDateString(price.startDate)
            LocalDateTime parsedEnd = GOKbTextUtils.completeDateString(price.endDate, false)
            Date startAsDate = (parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
            Date endAsDate = (parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
            def type_val = price.type ?: price.priceType

            if (type_val instanceof Map) {
              type_val = type_val.name ?: type.value
            }

            if (parsedStart && parsedEnd && parsedEnd < parsedStart) {
              valid = false
              result.errors << [message: "Price end date must be after its start date!", code: 500, baddata: price]
            }

            if (valid) {
              def item = obj.setPrice(type_val,
                  "${price.amount ?: price.price} ${String.isInstance(price.currency) ? price.currency : price.currency.name}",
                  startAsDate,
                  endAsDate)

              if (item) {
                new_prices << item
                result.changed = true
              }
              else if (price.id) {
                new_prices << ComponentPrice.get(price.id)
              }
            }
          }
          else {
            result.errors << [message: "Skipping invalid price!", code: 500, baddata: price]
          }
        }

        if (remove == true) {
          existing_prices_ids.each { ep ->
            if (!new_prices.findAll { it.id == ep }) {
              ComponentPrice cp_to_delete = ComponentPrice.get(ep)
              obj.removeFromPrices(cp_to_delete)
              cp_to_delete.delete()
            }
          }
        }
      }
    }
    catch (Exception e) {
      log.error("Unable to process prices:", e)
      result.errors << [message: "Unable to process prices!", code: 500, baddata: prices]
    }

    result
  }

  @Transactional
  def updateSubjects(obj, subjects, boolean remove = true) {
    log.debug("Update subjects ${subjects}")
    def result = [changed: false, errors: []]
    def existing_subjects_ids = obj.subjects?.collect { it.id }
    def new_subjects = []

    try {
      ComponentSubject.withTransaction {
        subjects?.each { subject ->
          Subject sub_obj

          if (subject instanceof Integer) {
            sub_obj = Subject.get(subject)
          }
          else if (subject instanceof Map) {
            if (subject.id) {
              sub_obj = Subject.get(subject.id)
            }
            else if (subject.heading && subject.scheme) {
              RefdataValue scheme

              if (subject.scheme instanceof Map) {
                if (subject.scheme.id) {
                  scheme = RefdataValue.get(subject.scheme.id)
                }
                else if (subject.scheme.value) {
                  scheme = RefdataCategory.lookup("Subject.Scheme", subject.scheme.value)
                }
              }
              else if (subject.scheme instanceof String) {
                scheme = RefdataCategory.lookup("Subject.Scheme", subject.scheme)
              }
              else if (subject.scheme instanceof Integer) {
                scheme = RefdataValue.get(subject.scheme)
              }

              if (scheme) {
                def validation_result = validationService.checkSubject(scheme, subject.heading)

                if (validation_result.result == 'ERROR') {
                  result.errors = result.errors + validation_result.errors
                }
                else {
                  sub_obj = Subject.findBySchemeAndHeading(scheme, subject.heading) ?: new Subject(scheme: scheme, heading: subject.heading).save(flush: true)
                }
              }
              else {
                result.errors << [message: "Unable to reference scheme of subject!", code: 404, baddata: subject]
              }
            }
          }

          if (sub_obj) {
            ComponentSubject subject_link = ComponentSubject.findByComponentAndSubject(obj, sub_obj)

            if (subject_link) {
              log.debug("Matched existing component subject link ..")
            }
            else {
              log.debug("Creating new component subject link ..")
              subject_link = new ComponentSubject(component: obj, subject: sub_obj).save(flush: true)
              result.changed = true
            }

            new_subjects << subject_link.id
          }
          else {
            result.errors << [message: "Unable to reference subject!", code: 404, baddata: subject]
          }
        }

        if (remove == true) {
          existing_subjects_ids.each { ep ->
            if (!new_subjects.findAll { it == ep }) {
              ComponentSubject cp_to_delete = ComponentSubject.get(ep)
              log.debug("Removing stale subject link ${cp_to_delete}")
              obj.removeFromSubjects(cp_to_delete)
              cp_to_delete.delete()
              result.changed = true
            }
          }
        }
      }
    }
    catch (Exception e) {
      log.error("Unable to process subjects:", e)
      result.errors << [message: "Unable to process subjects!", code: 500, baddata: subjects]
    }

    result
  }


  /**
   *  updatePublisher : Updates the list of publishers linked to a TitleInstance.
   * @param obj : The TitleInstance object to be updated
   * @param new_pubs : A list of Org IDs
   * @param remove : Flag for removal of existing Combos not present in the new list
   */

  @Transactional
  public def updatePublisherList(obj, new_pubs, boolean remove = true) {
    def result = [changed: false, errors: []]

    TitleInstance.withTransaction {
      def publisher_combos = obj.getCombosByPropertyName('publisher')
      def combo_type = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Publisher')

      String propName = obj.isComboReverse('publisher') ? 'fromComponent' : 'toComponent'
      String tiPropName = obj.isComboReverse('publisher') ? 'toComponent' : 'fromComponent'
      def pubs_to_add = []

      new_pubs.each { pub ->
        if (!pubs_to_add.findAll { it.id == pub }) {
          def pub_obj = Org.get(pub)

          if (pub_obj) {
            pubs_to_add << Org.get(pub)
          }
          else {
            result.errors << [message: "Unable to reference publisher with ID ${new_pubs}!", baddata: pub]
          }
        }
        else {
          log.warn("Duplicate for incoming publisher ${pub}!")
        }
      }

      if (!result.errors) {
        pubs_to_add.each { publisher ->
          boolean found = false
          for (int i = 0; !found && i < publisher_combos.size(); i++) {
            Combo pc = publisher_combos[i]
            def idMatch = pc."${propName}".id == publisher.id

            if (idMatch) {
              found = true
            }
          }

          if (!found) {
            obj.publisher << publisher
            obj.save(flush: true)
            result.changed = true
          }
          else {
            log.debug "Publisher ${publisher.name} already set against '${obj.name}'"
          }
        }
      }

      if (remove && !result.errors) {
        Iterator items = publisher_combos.iterator()
        Object element

        while (items.hasNext()) {
          element = items.next()

          if (!pubs_to_add.contains(element.toComponent) && !pubs_to_add.contains(element.fromComponent)) {
            // Remove.
            element.delete()
            result.changed = true
          }
        }
      }
    }

    result
  }

  public def updateLongField(obj, prop, val) {
    log.debug("Set simple prop ${prop} = ${val} (as Long)")

    try {
      obj[prop] = Long.parseLong(val)
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
    if (val == null || !val.trim()) {
      obj[prop] = null
    }
    else if (val.trim()) {
      LocalDateTime dateObj = GOKbTextUtils.completeDateString(val)

      if (dateObj) {
        ClassUtils.updateDateField(val, obj, prop)
      }
      else {
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
      log.debug("Set simple prop ${prop} = ${val} (as date ${dateObj}))")
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
    }
    else if (obj.hasProperty('value')) {
      obj_label = obj.value
    }
    else if (obj.hasProperty('name')) {
      obj_label = obj.name
    }
    else if (obj.hasProperty('variantName')) {
      obj_label = obj.variantName
    }
    else if (obj.hasProperty('propertyName')) {
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

  /**
   *  buildUrlString : Build an URL string for paginating index requests
   * @param context : REST path for the requested component type
   * @param type : Pagination type ('next', 'prev', null)
   * @param offset : Offset of the initial request
   * @param max : Number of results to return
   * @param params : Initial request parameters
   */

  String buildUrlString(context, type, offset , max, params) {
    URL serverUrl = grailsApplication.config.getProperty('grails.serverURL') ? new URL(grailsApplication.config.getProperty('grails.serverURL')) : null
    String path = "/rest" + "${context}"

    UriBuilder selfLink = UriBuilder.of(serverUrl.toURI())
        .path(path)

    params.each { p, vals ->
      log.debug("handling param ${p}: ${vals}")
      if (vals instanceof String[]) {
        vals.each { val ->
          if (val?.trim()) {
            log.debug("Val: ${val} -- ${val.class.name}")
            selfLink.queryParam(p, val)
          }
        }
        log.debug("${selfLink.toString()}")
      }
      else if (vals instanceof String && !['id','controller', 'action', 'componentType', 'offset'].contains(p)) {
        selfLink.queryParam(p, vals)
      }
    }

    if (type == 'prev') {
      selfLink.queryParam('offset', "${(offset - max) > 0 ? offset - max : 0}")
    }

    if (type == 'next') {
      selfLink.queryParam('offset', "${offset + max}")
    }

    selfLink.build()

    return selfLink.toString()
  }
}
