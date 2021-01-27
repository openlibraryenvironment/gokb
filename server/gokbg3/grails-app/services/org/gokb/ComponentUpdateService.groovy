package org.gokb

import grails.util.GrailsNameUtils
import groovyx.net.http.URIBuilder

import org.gokb.cred.*
import org.gokb.rest.RefdataController
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import groovy.transform.Synchronized
import com.k_int.ClassUtils
import grails.util.Holders

import groovy.util.logging.*

@Slf4j
class ComponentUpdateService {
  def componentLookupService
  def reviewRequestService
  def dateFormatService

  private final Object findLock = new Object()

  public boolean ensureCoreData(KBComponent component, data, boolean sync = false, user) {
    return ensureSync(component, data, sync, user)
  }

  @Synchronized("findLock")
  private boolean ensureSync(KBComponent component, data, boolean sync = false, user) {

    // Set the name.
    def hasChanged = false

    component.refresh()

    if (data.name?.trim() && (!component.name || (sync && component.name != data.name))) {
      component.name = data.name
      hasChanged = true
    }

    if (sync) {
      component.lastSeen = new Date().getTime()
    }

    // Core refdata.
    hasChanged |= setAllRefdata([
      'status', 'editStatus',
    ], data, component)

    // Identifiers
    log.debug("Identifier processing ${data.identifiers}")
    Set<String> ids = component.ids.collect { "${it.namespace?.value}|${it.value}".toString() }
    RefdataValue combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    RefdataValue combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    RefdataValue combo_type_id = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')

    data.identifiers.each { ci ->
      def namespace_val = ci.type ?: ci.namespace
      String testKey = "${namespace_val}|${ci.value}".toString()

      if (namespace_val && ci.value && namespace_val.toLowerCase() != "originediturl") {

        if (!ids.contains(testKey)) {
          def canonical_identifier = null

          if (!KBComponent.has(component, 'publisher')) {
            canonical_identifier = componentLookupService.lookupOrCreateCanonicalIdentifier(namespace_val, ci.value)
          }
          else {
            def norm_id = Identifier.normalizeIdentifier(ci.value)
            def ns = IdentifierNamespace.findByValueIlike(namespace_val)
            canonical_identifier = Identifier.findByNamespaceAndNormnameIlike(ns, norm_id)
          }

          log.debug("Checking identifiers of component ${component.id}")
          if (canonical_identifier) {
            def duplicate = Combo.executeQuery("from Combo as c where c.toComponent = ? and c.fromComponent = ?", [canonical_identifier, component])

            if (duplicate.size() == 0) {
              log.debug("adding identifier(${namespace_val},${ci.value})(${canonical_identifier.id})")
              def new_id = new Combo(fromComponent: component, toComponent: canonical_identifier, status: combo_active, type: combo_type_id).save(flush: true, failOnError: true)
              hasChanged = true
            }
            else if (duplicate.size() == 1 && duplicate[0].status == combo_deleted) {

              log.debug("Found a deleted identifier combo for ${canonical_identifier.value} -> ${component}")
              reviewRequestService.raise(
                component,
                "Review ID status.",
                "Identifier ${canonical_identifier} was previously connected to '${component}', but has since been manually removed.",
                user,
                null,
                null,
                RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Removed Identifier')
              )
            }
            else {
              log.debug("Identifier combo is already present, probably via titleLookupService.")
            }

            // Add the value for comparison.
            ids << testKey
          }
          else {
            log.debug("Could not find or create Identifier!")
          }
        }
      }
    }

    if (sync) {
      log.debug("Cleaning up deprecated IDs ..")
      component.ids.each { cid ->
        if (!data.identifiers.collect { "${it.type.toLowerCase()}|${Identifier.normalizeIdentifier(it.value)}".toString() }.contains("${cid.namespace?.value}|${Identifier.normalizeIdentifier(cid.value)}".toString())) {
          def ctr = Combo.executeQuery("from Combo as c where c.toComponent = ? and c.fromComponent = ?", [cid, component])

          if (ctr.size() == 1) {
            ctr[0].delete()
            hasChanged = true
          }
        }
      }
    }

    // Flags
    log.debug("Tag Processing: ${data.tags}");

    data.tags?.each { t ->
      log.debug("Adding tag ${t.type},${t.value}")

      component.addToTags(
        RefdataCategory.lookupOrCreate(t.type, t.value)
      )
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

    // If this is a component that supports curatoryGroups we should check for them.
    if (KBComponent.has(component, 'curatoryGroups')) {
      log.debug("Handling Curatory Groups ..")
      def groups = component.curatoryGroups.collect { [id: it.id, name: it.name] }

      data.curatoryGroups?.each { String name ->
        if (!groups.find { it.name.toLowerCase() == name.toLowerCase() }) {

          def group = CuratoryGroup.findByNormname(CuratoryGroup.generateNormname(name))
          def combo_type_cg = RefdataCategory.lookup('Combo.Type', component.getComboTypeValue('curatoryGroups'))
          // Only add if we have the group already in the system.
          if (group) {
            log.debug("Adding group ${name}..")
            def new_combo = new Combo(fromComponent: component, toComponent: group, type: combo_type_cg, status: combo_active).save(flush: true, failOnError: true)
            hasChanged = true
            groups << [id: group.id, name: group.name]
          }
          else {
            log.debug("Could not find linked group ${name}!")
          }
        }
      }

      if (sync) {
        groups.each { cg ->
          if (!data.curatoryGroups || !data.curatoryGroups.find { it.toLowerCase() == cg.name.toLowerCase() }) {
            log.debug("Removing deprecated CG ${cg.name}")
            Combo.executeUpdate("delete from Combo as c where c.fromComponent = ? and c.toComponent.id = ?", [component, cg.id])
            component.refresh()
            hasChanged = true
          }
        }
      }
    }
    else {
      log.debug("Skipping CG handling ..")
    }

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
        if (name?.trim().size() > 0 && !variants.find { it.variantName == name }) {
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
        if (priceData.amount != null && priceData.currency) {
          component.setPrice(priceData.type, "${priceData.amount} ${priceData.currency}", priceData.startDate ? dateFormatService.parseDate(priceData.startDate) : null, priceData.endDate ? dateFormatService.parseDate(priceData.endDate) : null)
          hasChanged = true
        }
      }
    }

    if (hasChanged) {
      component.lastSeen = new Date().getTime()
    }
    component.merge(flush: true)

    hasChanged
  }

  public boolean setAllRefdata(propNames, data, target, boolean createNew = false) {
    boolean changed = false
    propNames.each { String prop ->
      changed |= ClassUtils.setRefdataIfPresent(data[prop], target, prop, createNew)
    }
    changed
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

          changed |= setAllRefdata([
            'software', 'service'
          ], source_data, located_or_new_source)

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
}
