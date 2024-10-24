package org.gokb

import com.k_int.ClassUtils

import grails.gorm.transactions.Transactional

import groovy.transform.Synchronized

import org.gokb.cred.*
import org.opensearch.action.delete.DeleteRequest
import org.opensearch.client.RequestOptions

@Transactional
class ComponentUpdateService {
  def componentLookupService
  def reviewRequestService
  def dateFormatService
  def restMappingService
  def sessionFactory
  def ESWrapperService
  def grailsApplication

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

      data.curatoryGroups?.each{ String name ->
        if (!groups.find{ it.name.toLowerCase() == name.toLowerCase() }){
          def group = CuratoryGroup.findByNormname(CuratoryGroup.generateNormname(name))
          // Only add if we have the group already in the system.
          if (group){
            log.debug("Adding group ${name}..")
            new Combo(fromComponent: component, toComponent: group, type: combo_type_cg).save(flush: true, failOnError: true)
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
            Combo.executeUpdate("delete from Combo as c where c.fromComponent = :comp and c.toComponent.id = :cg", [comp: component, cg: cg.id])
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
    def existing_ids = []

    component.ids.each {
      Identifier ido = Identifier.get(it.id)
      existing_ids << "${ido.namespace?.value}|${Identifier.normalizeIdentifier(ido.value)}".toString()
    }

    RefdataValue combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    RefdataValue combo_type_id = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')

    new_ids.each { ci ->
      def namespace_val = ci.namespace ?: ci.type
      String testKey = "${namespace_val}|${ci.value}".toString()

      if (namespace_val && ci.value && namespace_val.toLowerCase() != "originediturl") {
        if (!existing_ids.contains(testKey)) {
          def canonical_identifier = componentLookupService.lookupOrCreateCanonicalIdentifier(namespace_val, ci.value)

          if (canonical_identifier) {
            def duplicate = Combo.executeQuery("from Combo as c where c.toComponent = :ci and c.fromComponent = :comp", [ci: canonical_identifier, comp: component])

            if (duplicate.size() == 0) {
              log.debug("adding identifier(${namespace_val},${ci.value})(${canonical_identifier.id})")
              new Combo(fromComponent: component, toComponent: canonical_identifier, type: combo_type_id).save(flush: true, failOnError: true)
              hasChanged = true
            } else if (duplicate.size() == 1 && duplicate[0].status == combo_deleted) {
              log.debug("Found a deleted identifier combo for ${canonical_identifier.value} -> ${component}")

              // def additionalInfo = [:]

              // additionalInfo.vars = [testKey, component.name]
              // reviewRequestService.raise(
              //   component,
              //   "Review ID status.",
              //   "Identifier ${canonical_identifier} was previously connected to '${component}', but has since been manually removed.",
              //   user,
              //   null,
              //   (additionalInfo as JSON).toString(),
              //   RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Removed Identifier'),
              //   group ?: componentLookupService.findCuratoryGroupOfInterest(component, user)
              // )
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
        Identifier ido = Identifier.get(ci.id)
        String ido_testkey = "${ido.namespace?.value}|${Identifier.normalizeIdentifier(ido.value)}".toString()
        def new_id_short = new_ids.collect { "${it.namespace ? it.namespace.toLowerCase() : it.type.toLowerCase()}|${Identifier.normalizeIdentifier(it.value)}".toString() }

        if (!new_id_short.contains(ido_testkey)) {
          def ctr = Combo.executeQuery("select id from Combo as c where c.toComponent = :ci and c.fromComponent = :comp", [ci: ido, comp: component])

          if (ctr.size() == 1) {
            log.debug("Removing stale ID ${ido} from ${component}")
            Combo.get(ctr[0]).delete()
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
    def curated_component = KBComponent.has(obj, 'curatoryGroups') ? obj : (obj?.class == TitleInstancePackagePlatform ? obj.pkg : null)

    if (curated_component) {
      if (curated_component.curatoryGroups.size() == 0 || curated_component.curatoryGroups*.id.intersect(user.curatoryGroups*.id)) {
        curator = true
      }
    }
    else if (obj?.class == ReviewRequest) {
      if (obj.allocatedTo == user) {
        curator = true
      }
      else if (obj.allocatedGroups*.group.id.intersect(user.curatoryGroups*.id)) {
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

  @Transactional
  def expungeComponent(KBComponent obj) {
    log.debug("Component expunge");
    def result = [success: true, deleteType: obj.class.name, deleteId: obj.id, esDelete: false]
    def class_simple_name = obj.class.simpleName
    def oid = "${obj.class.name}:${obj.id}"

    obj.class.withTransaction {
      Combo.executeUpdate("delete from Combo as c where c.fromComponent=:component or c.toComponent=:component", [component: obj])
      ComponentWatch.executeUpdate("delete from ComponentWatch as cw where cw.component=:component", [component: obj])
      KBComponentVariantName.executeUpdate("delete from KBComponentVariantName as c where c.owner=:component", [component: obj])

      def events_to_delete = ComponentHistoryEventParticipant.executeQuery("select c.event from ComponentHistoryEventParticipant as c where c.participant = :component", [component: obj])

      events_to_delete.each {
        ComponentHistoryEventParticipant.executeUpdate("delete from ComponentHistoryEventParticipant as c where c.event = :event", [event: it])
        ComponentHistoryEvent.executeUpdate("delete from ComponentHistoryEvent as c where c.id = :event", [event: it.id])
      }

      if (obj.class == CuratoryGroup) {
        AllocatedReviewGroup.removeAll(obj)

        obj.users*.id.each { user_id ->
          User.get(user_id).removeFromCuratoryGroups(obj).save()
        }
      }
      else {
        ReviewRequestAllocationLog.executeUpdate("delete from ReviewRequestAllocationLog as c where c.rr in ( select r from ReviewRequest as r where r.componentToReview=:component)", [component: obj])

        ReviewRequest.executeQuery("select id from ReviewRequest where componentToReview=:component", [component: obj]).each {
          reviewRequestService.expungeReview(ReviewRequest.findById(it))
        }
      }

      ComponentPerson.executeUpdate("delete from ComponentPerson as c where c.component=:component", [component: obj])
      ComponentSubject.executeUpdate("delete from ComponentSubject as c where c.component=:component", [component: obj])
      ComponentIngestionSource.executeUpdate("delete from ComponentIngestionSource as c where c.component=:component", [component: obj])
      KBComponent.executeUpdate("update KBComponent set duplicateOf = NULL where duplicateOf=:component", [component: obj])
      KBComponent.executeUpdate("delete from ComponentPrice where owner=:component", [component: obj])
      result.result = obj.delete(failOnError: true)

      if (ESWrapperService.indicesPerType[class_simple_name]){
        def esclient = ESWrapperService.getClient()
        DeleteRequest req = new DeleteRequest(grailsApplication.config.getProperty('gokb.es.indices.' + ESWrapperService.indicesPerType[class_simple_name]), oid)
        def es_response = esclient.delete(req, RequestOptions.DEFAULT)
        log.debug("${es_response}")
        result.esDelete = true
      }
    }
    result
  }

  void closeConnectedReviews(obj) {
    if (KBComponent.isAssignableFrom(ClassUtils.deproxy(obj).class)) {
      obj.reviewRequests.each {
        ReviewRequest rr = ReviewRequest.get(it.id)

        if (rr.status.value != 'Closed') {
          rr.status = RefdataCategory.lookup("ReviewRequest.Status", 'Closed')
          rr.save(flush: true)
        }
      }
    }
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
  }
}
