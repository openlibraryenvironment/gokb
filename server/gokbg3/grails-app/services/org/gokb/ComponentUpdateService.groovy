package org.gokb

import gokbg3.DateFormatService
import grails.util.GrailsNameUtils
import groovyx.net.http.URIBuilder

import org.gokb.cred.*
import org.gokb.rest.RefdataController
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import groovy.transform.Synchronized
import com.k_int.ClassUtils
import grails.converters.JSON
import grails.util.Holders

import groovy.util.logging.*
import org.grails.web.json.JSONObject

@Slf4j
class ComponentUpdateService {
  def componentLookupService
  def reviewRequestService
  def dateFormatService
  def restMappingService
  def classExaminationService
  def sessionFactory

  private final Object findLock = new Object()

  public boolean ensureCoreData(KBComponent component, data, boolean sync = false, user, CuratoryGroup group = null) {
    return ensureSync(component, data, sync, user, group)
  }

  @Synchronized("findLock")
  private boolean ensureSync(KBComponent component, data, boolean sync = false, user, CuratoryGroup group = null) {

    // Set the name.
    def hasChanged = false

    if (data.name?.trim() && (!component.name || (sync && component.name != data.name))) {
      component.name = data.name
      hasChanged = true
    }

    // Core refdata.
    hasChanged |= setAllRefdata([
      'status', 'editStatus',
    ], data, component)

    // Identifiers
    def data_identifiers = data.identifiers ?: data.ids

    if (data_identifiers) {
      hasChanged |= updateIdentifiers(component, data_identifiers, user, group, sync)
    }

    // Flags
    if (data.hasProperty('tags')) {
      log.debug("Tag Processing: ${data.tags}")

      data.tags.each { t ->
        log.debug("Adding tag ${t.type},${t.value}")
        component.addToTags(
          RefdataCategory.lookupOrCreate(t.type, t.value)
        )
      }
    }

    // handle the source.
    if (!component.source && data.source) {
      component.source = createOrUpdateSource(data.source)?.get('component')
    }

    // Add each file upload too!
    data.fileAttachments.each { fa ->
      if (fa?.md5) {
        DataFile file = DataFile.findByMd5(fa.md5) ?: new DataFile(guid: fa.guid, md5: fa.md5)

        // Single properties.
        file.with {
          (name, uploadName, uploadMimeType, filesize, doctype) = [
            fa.uploadName, fa.uploadName, fa.uploadMimeType, fa.filesize, fa.doctype
          ]

          // The contents of the file.
          if (fa.content) {
            fileData = fa.content.decodeBase64()
          }

          // Update.
          save()
        }

        // Grab the attachments.
        def attachments = component.getFileAttachments()
        if (!attachments.contains(file)) {
          // Add to the attached files.
          attachments.add(file)
        }
      }
    }

    hasChanged |= checkCuratoryGroups(component, data, sync)

    if (data.additionalProperties) {
      Set<String> props = component.additionalProperties.collect { "${it.propertyDefn?.propertyName}|${it.apValue}".toString() }
      for (Map it : data.additionalProperties) {

        if (it.name && it.value) {
          String testKey = "${it.name}|${it.value}".toString()

          if (!props.contains(testKey)) {
            def pType = AdditionalPropertyDefinition.findByPropertyName(it.name)
            if (!pType) {
              pType = new AdditionalPropertyDefinition()
              pType.propertyName = it.name
              pType.save(failOnError: true)
            }

            component.refresh()
            hasChanged = true
            def prop = new KBComponentAdditionalProperty()
            prop.propertyDefn = pType
            prop.apValue = it.value
            component.addToAdditionalProperties(prop)
            props << testKey
          }
        }
      }
    }
    def variants = component.variantNames.collect { [id: it.id, variantName: it.variantName] }

    // Variant names.
    if (data.variantNames) {
      for (String name : data.variantNames) {
        if (name?.trim() && !variants.find { it.variantName == name }) {
          // Add the variant name.
          log.debug("Adding variantName ${name} to ${component} ..")

          def new_variant_name = component.ensureVariantName(name)

          // Add to collection.
          if (new_variant_name) {
            variants << [id: new_variant_name.id, variantName: new_variant_name.variantName]
            hasChanged = true
          }
        }
      }
    }

    if (sync) {
      variants.each { vn ->
        if (!data.variantNames || !data.variantNames.contains(vn.variantName)) {
          def vobj = KBComponentVariantName.get(vn.id)
          component.removeFromVariantNames(vobj)
          hasChanged = true
        }
      }
    }

    // Prices.
    if (data.prices) {
      for (def priceData : data.prices) {
        def val = priceData.price ?: priceData.amount ?: null
        def typ = priceData.priceType ? priceData.priceType.value : priceData.type ?: null
        def startDate = priceData.startDate ? (priceData.startDate instanceof Date ? priceData.startDate : dateFormatService.parseDate(priceData.startDate.toString())) : null
        def endDate = priceData.endDate ? (priceData.endDate instanceof Date ? priceData.endDate : dateFormatService.parseDate(priceData.endDate.toString())) : null

        if (val != null && priceData.currency && typ) {
          component.setPrice(typ,
            "${val} ${priceData.currency}",
            startDate,
            endDate)
          hasChanged = true
        }
      }
    }

    if (hasChanged) {
      component.lastSeen = new Date().getTime()
    }

    component.save(flush: true)

    hasChanged
  }

  private def checkCuratoryGroups(KBComponent component, data, boolean sync) {
    // If this is a component that supports curatoryGroups we should check for them.
    boolean hasChanged = false

    if (KBComponent.has(component, 'curatoryGroups')){
      log.debug("Handling Curatory Groups ..")
      def groups = component.curatoryGroups.collect{ [id: it.id, name: it.name] }

      RefdataValue combo_type_cg = RefdataCategory.lookup('Combo.Type', component.getComboTypeValue('curatoryGroups'))
      RefdataValue combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)

      data.curatoryGroups?.each{ String name ->
        if (!groups.find{ it.name.toLowerCase() == name.toLowerCase() }){
          def group = CuratoryGroup.findByNormname(CuratoryGroup.generateNormname(name))
          // Only add if we have the group already in the system.
          if (group){
            log.debug("Adding group ${name}..")
            new Combo(fromComponent: component, toComponent: group, type: combo_type_cg, status: combo_active)
                .save(flush: true, failOnError: true)
            hasChanged = true
            groups << [id: group.id, name: group.name]
          }
          else{
            log.debug("Could not find linked group ${name}!")
          }
        }
      }

      if (sync){
        groups.each{ cg ->
          if (!data.curatoryGroups || !data.curatoryGroups.find{ it.toLowerCase() == cg.name.toLowerCase() }){
            log.debug("Removing deprecated CG ${cg.name}")
            Combo.executeUpdate("delete from Combo as c where c.fromComponent = ? and c.toComponent.id = ?", [component, cg.id])
            component.refresh()
            hasChanged = true
          }
        }
      }
    }
    else{
      log.debug("Skipping CuratoryGroup handling for component ${component.id} ..")
    }

    hasChanged
  }

  def updateIdentifiers(component, new_ids, User user = null, CuratoryGroup group = null, boolean remove = false) {
    boolean hasChanged = false
    Set<String> existing_ids = component.ids.collect { "${it.namespace?.value}|${Identifier.normalizeIdentifier(it.value)}".toString() }
    RefdataValue combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    RefdataValue combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    RefdataValue combo_type_id = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')

    new_ids.each { ci ->
      def namespace_val = ci.type ?: ci.namespace
      String testKey = "${namespace_val}|${ci.value}".toString()

      if (namespace_val && ci.value && namespace_val.toLowerCase() != "originediturl") {
        if (!existing_ids.contains(testKey)) {
          def canonical_identifier = componentLookupService.lookupOrCreateCanonicalIdentifier(namespace_val, ci.value)

          if (canonical_identifier) {
            def duplicate = Combo.executeQuery("from Combo as c where c.toComponent = ? and c.fromComponent = ?", [canonical_identifier, component])

            if (duplicate.size() == 0) {
              log.debug("adding identifier(${namespace_val},${ci.value})(${canonical_identifier.id})")
              def new_id = new Combo(fromComponent: component, toComponent: canonical_identifier, status: combo_active, type: combo_type_id).save(flush: true, failOnError: true)
              hasChanged = true
            } else if (duplicate.size() == 1 && duplicate[0].status == combo_deleted) {

              def additionalInfo = [:]

              additionalInfo.vars = [testKey, the_title.name]

              log.debug("Found a deleted identifier combo for ${canonical_identifier.value} -> ${component}")
              reviewRequestService.raise(
                component,
                "Review ID status.",
                "Identifier ${canonical_identifier} was previously connected to '${component}', but has since been manually removed.",
                user,
                null,
                (additionalInfo as JSON).toString(),
                RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Removed Identifier'),
                group ?: componentLookupService.findCuratoryGroupOfInterest(component, user)
              )
            } else {
              log.debug("Identifier combo is already present.")
            }

            // Add the value for comparison.
            existing_ids << testKey
          } else {
            log.debug("Could not find or create Identifier!")
          }
        }
      }
    }

    if (remove) {
      log.debug("Cleaning up deprecated IDs ..")
      component.ids.each { ci ->
        if (!new_ids.collect { "${it.type.toLowerCase()}|${Identifier.normalizeIdentifier(it.value)}".toString() }.contains("${ci.namespace?.value}|${Identifier.normalizeIdentifier(ci.value)}".toString())) {
          def ctr = Combo.executeQuery("from Combo as c where c.toComponent = ? and c.fromComponent = ?", [ci, component])

          if (ctr.size() == 1) {
            log.debug("Removing stale ID ${ci} from ${component}")
            ctr[0].delete()
            hasChanged = true
          }
        }
      }
    }
    hasChanged
  }

  public boolean setAllRefdata(propNames, data, target, boolean createNew = false) {
    boolean changed = false
    propNames.each { String prop ->
      changed |= ClassUtils.setRefdataIfPresent(data[prop], target, prop, createNew)
    }
    changed
  }

  def bulkUpdateField(User user, cls, params) {
    log.info("Bulk update for ${cls.name}: ${params}")
    def result = [total: 0, errors: 0]
    def field = params['_field']
    int offset = 0
    int max = 50
    def value = null

    result.total = componentLookupService.restLookup(user, cls, params, null, true)._pagination.total

    while (offset < result.total) {
      params.limit = max

      def items = componentLookupService.restLookup(user, cls, params, null, true).data

      if (cls == TitleInstancePackagePlatform && params.pkg?.trim() && field == 'status') {
        def pkg = Package.get(params.int('pkg'))
        def status_rdv = params.int('_value') ? RefdataValue.get(params.int('_value')) : RefdataCategory.lookup('KBComponent.Status', params['_value'])

        if (pkg && isUserCurator(pkg, user) && status_rdv?.owner?.label == 'KBComponent.Status') {
          TitleInstancePackagePlatform.executeUpdate("update TitleInstancePackagePlatform set status = :status, lastUpdated = :date where id IN (:ids)", [status: status_rdv, ids: items, date: new Date()])
          offset += max
        }
        else {
          offset = result.total
          result.errors = result.total
        }
      }
      else {
        items.each {
          def obj = cls.get(it)
          def reqBody = [:]

          reqBody[field] = params['_value']

          if (isUserCurator(obj, user)) {
            obj = restMappingService.updateObject(obj, null, reqBody)

            if (obj.hasErrors()) {
              result.errors++
            } else {
              obj.save(flush:true)
            }
          }
          else {
            result.errors++
          }
          offset++
        }
      }

      log.debug("Finished ${offset}/${result.total}")
      cleanUpGorm()
    }
    result
  }

  public boolean isUserCurator(obj, user) {
    boolean curator = user.adminStatus
    def curated_component = KBComponent.has(obj, 'curatoryGroups') ? obj : (obj.class == TitleInstancePackagePlatform ? obj.pkg : null)

    if (curated_component) {
      if (curated_component.curatoryGroups.size() == 0 || curated_component.curatoryGroups.id.intersect(user.curatoryGroups?.id)) {
        curator = true
      }
    }
    else if (obj.class == ReviewRequest) {
      if (obj.allocatedTo == user) {
        curator = true
      }
      else if (obj.allocatedGroups?.group.id.intersect(user.curatoryGroups?.id)) {
        curator = true
      }
      else if (!obj.allocatedGroups && user.contributorStatus) {
        curator = true
      }
    }
    else {
      curator = true
    }

    curator
  }

  private def createOrUpdateSource(data) {
    log.debug("assertSource, data = ${data}");
    def result = [:]
    def source_data = data;
    def changed = false
    result.status = true;

    try {
      if (data.name) {

        Source.withNewSession {
          def located_or_new_source = Source.findByNormname(Source.generateNormname(data.name)) ?: new Source(name: data.name).save(flush: true, failOnError: true)

          ClassUtils.setStringIfDifferent(located_or_new_source, 'url', data.url)
          ClassUtils.setStringIfDifferent(located_or_new_source, 'defaultAccessURL', data.defaultAccessURL)
          ClassUtils.setStringIfDifferent(located_or_new_source, 'explanationAtSource', data.explanationAtSource)
          ClassUtils.setStringIfDifferent(located_or_new_source, 'contextualNotes', data.contextualNotes)
          ClassUtils.setStringIfDifferent(located_or_new_source, 'frequency', data.frequency)
          ClassUtils.setStringIfDifferent(located_or_new_source, 'ruleset', data.ruleset)

          ClassUtils.setRefdataIfPresent(data.defaultSupplyMethod, located_or_new_source, 'defaultSupplyMethod', 'Source.DataSupplyMethod')
          ClassUtils.setRefdataIfPresent(data.defaultDataFormat, located_or_new_source, 'defaultDataFormat', 'Source.DataFormat')

          log.debug("Variant names processing: ${data.variantNames}")

          // variants
          data.variantNames.each { vn ->
            addVariantNameToComponent(located_or_new_source, vn)
          }

          result['component'] = located_or_new_source
        }
      }
    }
    catch (Exception e) {
      e.printStackTrace()
      result.error = e
    }
    result
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }
}
