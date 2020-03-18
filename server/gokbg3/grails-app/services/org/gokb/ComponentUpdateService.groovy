package org.gokb

import grails.util.GrailsNameUtils
import groovyx.net.http.URIBuilder

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import com.k_int.ClassUtils
import grails.util.Holders

import groovy.util.logging.*

@Slf4j
class ComponentUpdateService {
  def grailsApplication
  def restMappingService

  public boolean ensureCoreData ( KBComponent component, data, boolean sync = false ) {

    // Set the name.
    def hasChanged = false

    if(!component.name && data.name) {
      component.name = data.name
      hasChanged = true
    }

    // Core refdata.
    hasChanged |= setAllRefdata ([
      'status', 'editStatus',
    ], data, component)

    // Identifiers
    log.debug("Identifier processing ${data.identifiers}")
    Set<String> ids = component.ids.collect { "${it.namespace?.value}|${it.value}".toString() }
    RefdataValue combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    RefdataValue combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    RefdataValue combo_type_id = RefdataCategory.lookup('Combo.Type','KBComponent.Ids')

    data.identifiers.each { ci ->
      String testKey = "${ci.type}|${ci.value}".toString()

      if ( ci.type && ci.value && ci.type.toLowerCase() != "originediturl") {

        if (!ids.contains(testKey)) {
          def canonical_identifier = Identifier.lookupOrCreateCanonicalIdentifier(ci.type,ci.value)

          log.debug("Checking identifiers of component ${component.id}")

          def duplicate = Combo.executeQuery("from Combo as c where c.toComponent = ? and c.fromComponent = ?",[canonical_identifier,component])

          if(duplicate.size() == 0){
            log.debug("adding identifier(${ci.type},${ci.value})(${canonical_identifier.id})")
            def new_id = new Combo(fromComponent: component, toComponent: canonical_identifier, status: combo_active, type: combo_type_id).save(flush:true, failOnError:true)
            hasChanged = true
          }
          else if (duplicate.size() == 1 && duplicate[0].status == combo_deleted) {

            log.debug("Found a deleted identifier combo for ${canonical_identifier.value} -> ${component}")
            ReviewRequest.raise(
              component,
              "Review ID status.",
              "Identifier ${canonical_identifier} was previously connected to '${component}', but has since been manually removed.",
              user
            )
          }
          else {
            log.debug("Identifier combo is already present, probably via titleLookupService.")
          }

          // Add the value for comparison.
          ids << testKey
        }
      }
    }

    if (sync) {
      log.debug("Cleaning up deprecated IDs ..")
      component.ids.each { cid ->
        if (!data.identifiers.collect {"${it.type.toLowerCase()}|${Identifier.normalizeIdentifier(it.value)}".toString()}.contains("${cid.namespace?.value}|${Identifier.normalizeIdentifier(cid.value)}".toString())) {
          def ctr = Combo.executeQuery("from Combo as c where c.toComponent = ? and c.fromComponent = ?", [cid,component])

          if(ctr.size() == 1) {
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
    if (!component.source && data.source && data.source?.size() > 0) {
      component.source = createOrUpdateSource (data.source)?.get('component')
    }

    // Add each file upload too!
    data.fileAttachments.each { fa ->

      if (fa?.md5) {

        DataFile file = DataFile.findByMd5(fa.md5) ?: new DataFile( guid: fa.guid, md5: fa.md5 )

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
    if ( KBComponent.has(component, 'curatoryGroups') ) {
      def groups = component.curatoryGroups.collect { [id: it.id, name: it.name] }

      data.curatoryGroups?.each { String name ->
        if (!groups.find {it.name.toLowerCase() == name.toLowerCase()}) {

          def group = CuratoryGroup.findByNormname(CuratoryGroup.generateNormname(name))
          // Only add if we have the group already in the system.
          if (group) {
            component.addToCuratoryGroups ( group )
            hasChanged = true
            groups << [id: it.id, name: it.name]
          }
        }
      }

      if (sync) {
        groups.each { cg ->
          if (!data.curatoryGroups || !data.curatoryGroups.contains(cg.name)) {
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
            def pType = AdditionalPropertyDefinition.findByPropertyName (it.name)
            if (!pType) {
              pType = new AdditionalPropertyDefinition ()
              pType.propertyName = it.name
              pType.save(failOnError: true)
            }

            component.refresh()
            def prop = new KBComponentAdditionalProperty ()
            prop.propertyDefn = pType
            prop.apValue = it.value
            component.addToAdditionalProperties(prop)
            component.save(failOnError: true)
            props << testKey
          }
        }
      }
    }
    def variants = component.variantNames.collect { [id: it.id, variantName: it.variantName] }

    // Variant names.
    if (data.variantNames) {
      for (String name : data.variantNames) {
        if (name?.trim().size() > 0 && !variants.find {it.variantName == name}) {
          // Add the variant name.
          log.debug("Adding variantName ${name} to ${component} ..")

          def new_variant_name = component.ensureVariantName(name)

          // Add to collection.
          if(new_variant_name) {
            variants << [id: new_variant_name.id, variantName: new_variant_name.variantName]
          }
        }
      }
    }

    if (sync) {
      variants.each { vn ->
        if (!data.variantNames || !data.variantNames.contains(vn.variantName)) {
          def vobj = KBComponentVariantName.get(vn.id)
          vobj.delete()
          component.refresh()
          hasChanged = true
        }
      }
    }

    if (hasChanged) {
      component.lastSeen = new Date().getTime()
    }
    component.save(flush:true)

    hasChanged
  }

  public boolean setAllRefdata (propNames, data, target, boolean createNew = false) {
    boolean changed = false
    propNames.each { String prop ->
      changed |= ClassUtils.setRefdataIfPresent(data[prop], target, prop, createNew)
    }
    changed
  }
}