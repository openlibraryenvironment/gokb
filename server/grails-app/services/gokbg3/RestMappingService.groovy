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

  static final List<String> defaultIgnore = [
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
      'componentDiscriminator'
  ]

  static final List<String> defaultEmbed = [
      'linkedIds',
      'variantNames',
      'additionalProperties',
      'reviewRequests'
  ]

  static final List<String> defaultImmmutable = [
      'id',
      'uuid',
      'lastUpdated',
      'dateCreated',
      'lastUpdatedBy',
      'value',
      'version'
  ]

  static final Map<String,String> mappedProps = [
      'linkedIds': 'ids',
      'publisherLinks': 'publisher'
  ]

  /**
   *  mapObjectToJson : Maps an domain class object to JSON based on its jsonMapping config.
   * @param proxy : The object to be mapped
   * @param params : The map of request parameters
   */

  public Map mapObjectToJson(proxy, params, def user = null) {
    log.debug("mapObjectToJson: ${proxy.class.name} -- ${params}")
    Object obj = ClassUtils.deproxy(proxy).refresh()
    Map result = [:]
    List<String> embed_active = params['_embed']?.split(',') ?: []
    List<String> include_list = params['_include']?.split(',') ?: null
    List<String> exclude_list = params['_exclude']?.split(',') ?: null
    Map jsonMap = KBComponent.has(obj, 'jsonMapping') ? obj.jsonMapping : null
    String base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
    boolean nested = params['nested'] ? true : false
    boolean curatedClass = obj.respondsTo('curatoryGroups')
    boolean process_deleted_links = params.boolean('_showdeleted') ?: false
    boolean is_curator = user ? componentUpdateService.isUserCurator(obj, user) : false

    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj.class.name)

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

    if (embed_active.size() == 0) {
      if (KBComponent.isAssignableFrom(obj.class)) {
        if (!nested) {
          embed_active = defaultEmbed
        }
        else if (jsonMap?.defaultEmbeds?.contains('ids')) {
          embed_active.add('ids')
        }
      }

      if (!nested && jsonMap?.defaultEmbeds?.size() > 0) {
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
            if ((embed_active.contains(p.name) && (user?.isAdmin() || p.type != User)) || (!nested && ['reviewRequests', 'comments'].contains(p.name) && user?.editorStatus)) {
              String mapped_field = mappedProps[p.name] ?: p.name

              log.debug("Handling embeds for ${p.name}: ${obj[mapped_field]}")


              result['_embedded'][mapped_field] = []

              obj[p.name].each { ao ->
                Object assoc_obj = ClassUtils.deproxy(ao)
                boolean addToList = false
                Map mapped_item = [:]

                if (assoc_obj instanceof ComponentSubject) {
                  mapped_item = getEmbeddedJson(assoc_obj.subject, user)
                  log.debug("Using subject ${assoc_obj} for embed mapping ..")
                }
                else if (assoc_obj instanceof ComponentIdentifier) {
                  if (process_deleted_links || assoc_obj.status?.value == 'Active') {
                    mapped_item = getEmbeddedJson(assoc_obj.identifier, user)

                    mapped_item.['_linkStatus'] = assoc_obj.status.value
                  }
                }
                else if (assoc_obj instanceof TitlePublisher) {
                  if (process_deleted_links || assoc_obj.status?.value == 'Active') {
                    mapped_item = getEmbeddedJson(assoc_obj.publisher, user)

                    mapped_item.['_linkStatus'] = assoc_obj.status.value
                  }
                }
                else if (!assoc_obj.hasProperty('status') || process_deleted_links || assoc_obj.status?.value != 'Deleted') {
                  mapped_item = getEmbeddedJson(assoc_obj, user)
                }

                if (mapped_item) {
                  result['_embedded'][mapped_field] << mapped_item
                }

                log.debug("${result['_embedded'][mapped_field]}")
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
              result[p.name] = obj[p.name] ? obj[p.name].toString() : null
              break;

            case Date.class:
              if (p.name == 'lastUpdated' || p.name == 'dateCreated' || p.name == 'lastRun') {
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

    if (obj.class == ReviewRequest && embed_active.contains('allocatedGroups')) {
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
   * @param jsonMap : The map of update restrictions for the object class
   * @param reqBody : The map of properties to be updated
   */

  @Transactional
  public Boolean updateObject(obj, jsonMap, reqBody) {
    log.debug("Update object ${obj} - ${reqBody}")
    Boolean changed = false
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
            changed |= updateAssoc(obj, p.name, newVal)
          }
          else {
            // Add to collection
            log.debug("Skip generic handling of collections ..")
          }
        }
        else {
          log.debug("checking for type of property ${p.name} -> ${p.type}")
          if (newVal == null || newVal == "") {
            obj[p.name] = null
          }

          switch (p.type) {
            case Integer.class:
              updateIntField(obj, p.name, newVal)
              break;
            case Long.class:
              updateLongField(obj, p.name, newVal)
              break;
            case Date.class:
              updateDateField(obj, p.name, newVal)
              break;
            case LocalDate.class:
              updateLocalDateField(obj, p.name, newVal)
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
    if (obj.isDirty()) {
      changed = true
    }

    if (obj.validate()) {
      obj.save()
    }

    return changed
  }

  def lookupRefdataValueForCategory(value, RefdataCategory category) {
    def result = [obj: null]
    RefdataValue rdv = null

    if (value instanceof Integer) {
      rdv = RefdataValue.get(value)
    }
    else if (value instanceof String) {
      rdv = RefdataValue.findByOwnerAndValue(category, value)
    }
    else if (value instanceof Map) {
      if (value.id) {
        rdv = RefdataValue.get(value.id)
      }
      else if (value.value) {
        rdv = RefdataValue.findByOwnerAndValue(category, value.value)
      }
      else if (value.name) {
        rdv = RefdataValue.findByOwnerAndValue(category, value.name)
      }
    }
    else {
      result.error = [
        message: 'Unable to process refdata info!',
        baddata: value,
        code: 400
      ]
    }

    if (rdv) {
      if (rdv.owner == category) {
        result.obj = rdv
      }
      else {
        result.error = [
          message: 'Provided value does not belong to the right category!',
          baddata: value,
          field: 'owner',
          code: 400
        ]
      }
    }
    else if (!result.error) {
      result.error = [
        message: 'Unable to find provided refdata!',
        baddata: value,
        code: 404
      ]
    }

    result
  }

  @Transactional
  public Boolean updateAssoc(obj, prop, val, def cat = null) {
    log.debug("Update association $obj - $prop: $val")
    Boolean changed = false
    def ptype = grailsApplication.mappingContext.getPersistentEntity(obj.class.name).getPropertyByName(prop).type

    if (val != null) {
      if (ptype == RefdataValue) {
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
          def rdv_result = lookupRefdataValueForCategory(val, cat)

          if (rdv_result.obj) {
            if (catName == 'KBComponent.Status') {
              updateStatus(obj, rdv_result.obj.value)
            }
            else {
              obj[prop] = rdv_result.obj
            }
          }
          else {
            if (rdv_result.error?.field == 'owner') {
              obj.errors.reject(
                  'rdc.values.notFound',
                  [rdv_result.obj, cat] as Object[],
                  '[Value {0} is not valid for category {1}!]'
              )
              obj.errors.rejectValue(
                  prop,
                  'rdc.values.notFound'
              )
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
          log.error("Could not resolve category (${obj.niceName}.${prop})!")
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
          if (linkObj != obj[prop]) {
            obj[prop] = linkObj
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
    }
    else if (obj[prop] != null) {
      log.debug("Set value to null")
      changed = true
      obj[prop] = null
    }

    changed
  }

  @Transactional
  public def updateIdentifiers(obj, ids, boolean remove = true) {
    log.debug("updating ids ${ids}")
    List id_links = obj.linkedIds
    RefdataValue status_active = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_ACTIVE)
    RefdataValue ci_status_deleted = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_DELETED)
    def result = [changed: false, errors: []]
    Set new_ids = []

    if (obj && ids instanceof Collection) {
      ids?.each { i ->
        Identifier id = null
        boolean valid = true

        if (i instanceof Integer) {
          id = Identifier.get(i)
        }
        else if (i instanceof Map && (!i['_linkStatus'] || i['_linkStatus'] == 'Active')) {
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
          List dupes = ComponentIdentifier.executeQuery("from ComponentIdentifier where component = :fc and identifier = :tc", [fc: obj, tc: i])

          if (dupes.size() == 0) {
            new ComponentIdentifier(component: obj, identifier: i).save(flush: true, failOnError: true)
            result.changed = true
          }
          else if (dupes.size() == 1) {
            if (dupes[0].status == ci_status_deleted) {
              log.debug("Matched active ID link was marked as deleted!")
              dupes[0].delete(flush: true)
              new ComponentIdentifier(component: obj, identifier: i).save(flush: true, failOnError: true)
              result.changed = true
            }
            else {
              log.debug("Not adding duplicate ..")
            }
          }
          else {
            result.errors << [message: "There seem to be duplicate links for an identifier against this title!", baddata: i]
            log.error("Multiple ID links for ${obj} -- ${i}!")
          }
        }

        if (remove && result.errors.size() == 0) {
          Iterator items = id_links.iterator()
          Object element
          while (items.hasNext()) {
            element = items.next()

            if (!new_ids.contains(element.identifier)) {
              // Remove.
              log.debug("Removing newly missing ID ${element.identifier}")
              element.status = ci_status_deleted
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

  public boolean updateStatus(obj, val) {
    boolean changed = false

    if (val == 'Deleted') {
      if (obj.status.value != 'Deleted') {
        changed = true
        obj.deleteSoft()

        componentUpdateService.closeConnectedReviews(obj)
      }
    }
    else if (val == 'Retired') {
      if (obj.status.value != 'Retired' ) {
        changed = true
        obj.retire()
      }
    }
    else if (val == 'Current') {
      if (obj.status.value != 'Current') {
        changed = true
        obj.setActive()
      }
    }
    else if (val == 'Expected') {
      if (obj.status.value != 'Expected') {
        changed = true
        obj.setExpected()
      }
    }
    else {
      obj.errors.reject(
          'rdc.values.notFound',
          [val] as Object[],
          '[{0} is not a valid status value!]'
      )
      obj.errors.rejectValue(
          'status',
          'rdc.values.notFound'
      )
    }

    changed
  }


  @Transactional
  public Map updateCuratoryGroups(obj, cgs, boolean remove = true) {
    log.debug("Update curatory Groups ${cgs}")
    Map result = [changed: false, errors: []]
    Set new_cgs = []

    CuratoryGroup.withTransaction {
      List current_cgs = obj.curatoryGroups

      cgs?.each { cg ->
        CuratoryGroup cg_obj

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
            obj.addToCuratoryGroups(c)
            result.changed = true
          }
          else {
            log.debug("Existing cg ${c}..")
          }
        }

        if (remove) {
          current_cgs.each { ncg ->
            if (!new_cgs.contains(ncg)) {
              // Remove.
              obj.removeFromCuratoryGroups(ncg)
              result.changed = true
            }
          }
        }
      }
    }

    log.debug("New cgs: ${new_cgs}")
    result
  }

  @Transactional
  public Map updateVariantNames(obj, vals, boolean remove = true) {
    log.debug("Update Variants ${vals} ..")
    Map result = [changed: false, errors: []]
    List remaining = []
    List notFound = []
    List toRemove = []

    try {
      KBComponentVariantName.withTransaction {
        vals?.each {
          KBComponentVariantName newVariant = null

          if (it instanceof String) {
            if (it.trim()) {
              String nvn = GOKbTextUtils.normaliseString(it)
              List dupes = KBComponentVariantName.findByNormVariantNameAndOwner(nvn, obj) ?: []

              if (dupes) {
                log.debug("Not adding duplicate variant")
              }
              else {
                newVariant = obj.ensureVariantName(it)

                if (newVariant) {
                  log.debug("Added variant ${newVariant}")
                  result.changed = true
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
              String nvn = GOKbTextUtils.normaliseString(it.variantName)
              List dupes = KBComponentVariantName.findByNormVariantNameAndOwner(nvn, obj) ?: []

              if (dupes) {
                log.debug("Not adding duplicate variant")

                if (!remaining.contains(dupes))
                  remaining << dupes
              }
              else {
                newVariant = obj.ensureVariantName(it.variantName)

                log.debug("Ensured variant: ${newVariant}")

                if (newVariant) {
                  result.changed = true

                  if (it.locale) {
                    result.changed |= updateAssoc(newVariant, 'locale', it.locale, RefdataCategory.findByDesc(KBComponent.RD_LANGUAGE))
                  }
                  else {
                    newVariant.locale = null
                  }

                  if (it.variantType) {
                    result.changed |= updateAssoc(newVariant, 'variantType', it.variantType)
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
                result.changed = true
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

        if (result.changed) {
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
  public Map updateComments(obj, comments, boolean remove = true) {
    Map result = [changed: false, errors: []]
    RefdataCategory cat_lang = RefdataCategory.findByDesc(KBComponent.RD_LANGUAGE)
    Boolean changed = false
    List remaining = []
    List notFound = []
    List toRemove = []

    KBComponentComment.withTransaction {
      comments.each { co ->
        KBComponentComment cobj = null
        boolean created = false

        if (co instanceof Map) {
          if (co.id) {
            cobj = KBComponentComment.findById(co.id)
            if (!cobj || cobj.owner != obj) {
              result.errors << [
                message: 'Unable to reference existing comment item!',
                baddata: co
              ]
            }
          }
          else if (co.language && co.value?.trim()) {

            def rd_result = lookupRefdataValueForCategory(co.language, cat_lang)
            if (rd_result.obj) {
              def existing = KBComponentComment.findByOwnerAndLanguage(obj, rd_result.obj)
              if (existing) {
                result.errors << [
                  message: 'Matched incoming comment without id to existing comment with the same language!',
                  baddata: co
                ]
              }
              else {
                created = true
                cobj = new KBComponentComment(owner: obj, value: co.value, language: rd_result.obj).save(flush: true)
                result.changed = true
              }
            }
            else {
              result.errors << rd_result.error
            }
          }
          else {
            result.errors << [
              message: 'Missing language info or value for comment item!',
              baddata: co
            ]
          }
        }
        else if (co instanceof Integer) {
          cobj = KBComponentComment.get(co)
        }
        else {
          result.errors << [
            message: 'Unable to process comment item!',
            baddata: co
          ]
        }

        if (cobj) {
          if (!created && co.value != cobj.value) {
            cobj.value = co.value
            cobj.save(flush: true)
            result.changed = true
          }

          remaining << cobj
        }
      }

      if (remove) {
        obj.comments.each { old_comment ->
          if (!remaining.contains(old_comment)) {
            toRemove << old_comment.id
          }
        }

        toRemove.each { trid ->
          def tr_obj = KBComponentComment.get(trid)

          obj.removeFromComments(tr_obj)
          obj.save(flush: true)
          result.changed = true
        }
      }
    }

    result
  }

  @Transactional
  public Map updatePrices(obj, prices, boolean remove = true) {
    Map result = [changed: false, errors: []]
    List existing_prices_ids = obj.prices?.collect { it.id } ?: []
    List new_prices = []

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
              result.changed = true
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
  public Map updateSubjects(obj, subjects, boolean remove = true) {
    log.debug("Update subjects ${subjects}")
    Map result = [changed: false, errors: []]
    List existing_subjects_ids = obj.subjects?.collect { it.id } ?: []
    List new_subjects = []

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
   * @param new_vals : A list of Orgs
   * @param remove : Flag for removal of existing Combos not present in the new list
   */

  @Transactional
  public Map updatePublisherList(TitleInstance obj, List new_vals, boolean remove = true) {
    Map result = [changed: false, errors: []]
    List existing_links = TitlePublisher.executeQuery("select id from TitlePublisher where title = :ti ", [ti: obj])
    List new_links = []

    new_vals.each { pub ->
      Org pub_obj = null

      if (pub instanceof Map) {
        pub_obj = Org.get(pub.id)
      }
      else {
        pub_obj = Org.get(pub)
      }

      if (!pub_obj) {
        result.errors << [message: "Unable to reference publisher with info ${pub}!", baddata: pub]
      }
      else {
        TitlePublisher tp = TitlePublisher.findByTitleAndPublisher(obj, pub_obj)

        if (!tp) {
          tp = new TitlePublisher(title: obj, publisher: pub_obj).save(flush:true, failOnError: true)
          result.changed = true
        }

        new_links << tp

        if (pub instanceof Map) {
          if (pub.containsKey('_linkStart')) {
            updateLocalDateField(tp, 'startDate', pub['_linkStart'])
          }

          if (pub.containsKey('_linkEnd')) {
            updateLocalDateField(tp, 'endDate', pub['_linkEnd'])
          }

          if (pub.containsKey('_linkedStatus')) {
            RefdataValue link_status = pub['_linkedStatus'] ? RefdataCategory.lookup(TitlePublisher.RD_STATUS, pub['_linkedStatus']) : null

            if (!link_status || (pub['_linkedStatus'] && link_status)) {
              tp.status = link_status
            }
            else {
              result.errors << [message: "Unable to save status for publisher link with info ${pub}!", baddata: pub['_linkedStatus']]
            }
          }
        }

        if (tp.validate()) {
          tp.save(flush: true, failOnError: true)
        }
        else {
          result.errors << [message: "Unable to save dates for publisher link with info ${pub}!", baddata: pub]
        }
      }
      else {
        log.warn("Duplicate for incoming publisher ${pub}!")
      }
    }
    log.debug("New list of pubs: ${new_pubs}")

    if (remove && !result.errors) {
      existing_links.each { elid ->
        if (!new_links*.id.contains(elid)) {
          TitlePublisher.findById(elid).delete(flush: true, failOnError: true)
          result.changed = true
        }
      }
    }

    result
  }

  public void updateLongField(obj, prop, val) {
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
  }

  public def updateIntField(obj, prop, val) {
    log.debug("Set simple prop ${prop} = ${val} (as Long)")

    try {
      obj[prop] = Integer.parseInt(val)
    }
    catch (Exception e) {
      obj.errors.reject(
          'typeMismatch.java.lang.Integer',
          [prop] as Object[],
          '[Invalid number value for property [{0}]]'
      )
      obj.errors.rejectValue(
          prop,
          'typeMismatch.java.lang.Integer'
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
  }


  public def updateLocalDateField(obj, prop, val) {
    if (val == null || !val.trim()) {
      obj[prop] = null
    }
    else if (val.trim()) {
      LocalDate dateObj = GOKbTextUtils.completeDateString(val)?.toLocalDate()

      if (dateObj) {
        obj[prop] = dateObj
      }
      else {
        obj.errors.reject(
            'typeMismatch.java.util.LocalDate',
            [prop] as Object[],
            '[Invalid date value for property [{0}]]'
        )
        obj.errors.rejectValue(
            prop,
            'typeMismatch.java.util.LocalDate'
        )
      }
      log.debug("Set simple prop ${prop} = ${val} (as date ${dateObj}))")
    }
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
