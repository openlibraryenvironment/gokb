package org.gokb

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.commons.lang.RandomStringUtils
import org.springframework.web.servlet.support.RequestContextUtils
import org.gokb.cred.*
import au.com.bytecode.opencsv.CSVReader
import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CancellationException

import groovy.util.logging.*

@Slf4j
class IntegrationController {

  def springSecurityService
  def concurrencyManagerService
  def classExaminationService
  def componentUpdateService
  def componentLookupService
  def titleLookupService
  def applicationEventService
  def reviewRequestService
  def sessionFactory
  def packageService
  def messageService
  def titleHistoryService

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def index() {
  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def assertJsonldPlatform() {
    def result = [result: 'OK']
    def name = request.JSON.'skos:prefLabel'
    def normname = GOKbTextUtils.norm2(name)
    def located_entries = KBComponent.findAllByNormname(normname)
    log.debug("assertJsonldPlatform ${name}/${normname}");
    if (located_entries.size() == 0) {
      log.debug("No platform with normname ${normname} - create");
      def new_platform = new org.gokb.cred.Platform(name: name, normname: normname).save()
      result.message = "Added new platform"
    }
    else {
      result.message = "Entity with that name already exists.."
    }
    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def assertGroup() {
    def result = [result: 'OK']
    def name = request.JSON.name
    def normname = CuratoryGroup.generateNormname(name)
    def user = springSecurityService.currentUser
    def uuid = request.JSON.uuid
    def group = uuid ? CuratoryGroup.findByUuid(uuid) : null
    def status = request.JSON.status

    if (!group) {
      group = CuratoryGroup.findByNormname(normname)
    }
    else {
      result.message = "Group ${group.name} matched by uuid!"
    }

    if (!group) {
      group = new CuratoryGroup(name: name, uuid: uuid)

      if (group.validate()) {
        group.save(flush: true)
        result.message = "Created new group ${name}!"
      }
      else {
        result.message = "Could not reference group ${name}"
        result.errors = group.errors
        result.result = 'ERROR'

        render result as JSON
      }
    }
    else {
      result.message = "Group ${group.name} matched by name!"
    }

    // Defaults first.
    componentUpdateService.ensureCoreData(group, request.JSON, false, user)

    // Find by username but do not create missing entries.
    def owner = request.JSON.owner
    if (owner) {
      group.owner = User.findByUsername(owner)
    }

    // Need to add all users to the group.
    def memberNames = request.JSON.users
    if (memberNames) {
      def members = User.createCriteria().list {
        'in'('username', memberNames)
      }

      members.each {
        group.addToUsers(it)
      }
    }

    if (group) {
      group.save(flush: true)
      result.groupId = group.id
    }
    else {
      result.message = "Could not reference group ${name}"
      result.result = 'ERROR'
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def assertJsonldOrg() {
    // log.debug("assertOrg, request.json = ${request.JSON}");
    def result = [:]
    def user = springSecurityService.currentUser
    result.status = true;

    try {

      def name = request.JSON.'skos:prefLabel'

      if ((name != null) && (name.trim().length() > 0)) {

        log.debug("Trying to locate component with ID ${request.JSON.'@id'} name is \"${name}\"");

        // Try and match on primary ID
        def located_entries = KBComponent.lookupByIdentifierValue([request.JSON.'@id'.toString()] as String[]);

        if (located_entries?.size() == 1) {
          log.debug("Identified record..");
          enrichJsonLDOrg(located_entries[0], request.JSON)
        }
        else if (located_entries?.size() == 0) {

          log.debug("Not identified - try sameAs relations");

          if (request.JSON.'owl:sameAs' != null) {
            log.debug("Attempt lookup by sameAs : ${request.JSON.'owl:sameAs' as String[]} ");
            located_entries = KBComponent.lookupByIdentifierValue((request.JSON.'owl:sameAs') as String[])
          }
          else {
            log.debug("No owl:sameAs entries found");
          }

          if (located_entries?.size() == 0) {
            log.debug("Failed to match on same-as. Attempting primary name match");
            def normname = GOKbTextUtils.norm2(name)
            located_entries = KBComponent.findAllByNormname(normname)
            if (located_entries?.size() == 0) {
              log.debug("No match on normalised name ${normname}.. Trying variant names");
              def variant_normname = GOKbTextUtils.normaliseString(name)
              def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
              located_entries = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status <> ?", [variant_normname, status_deleted]);

              if (located_entries?.size() == 0) {

                createJsonLDOrg(request.JSON);
              }
              else if (located_entries?.size() == 1) {
                log.debug("Exact match on normalised variantname ${variant_normname} - good enough");
                enrichJsonLDOrg(located_entries[0], request.JSON)
              }
              else {
                log.error("Multiple matches on normalised variant name... abandon all hope");
              }
            }
            else if (located_entries?.size() == 1) {
              log.debug("Exact match on normalised name ${normname} - good enough");
              enrichJsonLDOrg(located_entries[0], request.JSON)
            }
            else {
              log.error("Multiple matches on normalised name... abandon all hope");
            }

          }
          else if (located_entries?.size() == 1) {
            log.debug("Located identifier");
          }
          else {
            log.error("set of SameAs identifiers locate more that one component");
          }
        }
        else {
          log.error("Unique identifier finds multiple components.");
        }

        result.status = 'OK'
      }
      else {
        log.error("skipping org [ ${request.JSON.'@id'}] due to null name");
      }
    }
    catch (Exception e) {
      log.error("Problem", e)
      result.status = 'ERROR'
    }
    finally {
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def createJsonLDOrg(ldjsonorg) {
    log.debug("createJsonLDOrg");
    //             "@id": "http://www.lib.ncsu.edu/ld/onld/00000134" ,
    //         "skos:prefLabel": "A.B. Lundequistska Bokhandeln" ,
    //         "owl:sameAs": [
    //             "http://viaf.org/viaf/152447102" ,
    //             "http://isni-url.oclc.nl/isni/0000000102215732" ,
    //             "http://id.loc.gov/authorities/names/n80148304"
    //         ] ,
    def name = request.JSON.'skos:prefLabel'
    def id = request.JSON.'@id'
    def new_org = new Org(name: name)

    def primary_identifier = componentLookupService.lookupOrCreateCanonicalIdentifier('global', id)
    new_org.ids.add(primary_identifier)

    request.JSON.'owl:sameAs'?.each { said ->

      // Double check that this identifier is NOT already used
      def existing_usage = KBComponent.lookupByIO('global', said)
      if (existing_usage == null) {
        def identifier = componentLookupService.lookupOrCreateCanonicalIdentifier('global', said)
        new_org.ids.add(identifier)
      }
      else {
        log.error("Not adding identifer to a second item...");
      }
    }

    new_org.save();

    request.JSON.'skos:altLabel'?.each { al ->
      println("checking alt label ${al}");
      new_org.ensureVariantName(al);
    }

    new_org.save();

    if (request.JSON.'foaf:homepage' != null) {
      new_org.homepage = request.JSON.'foaf:homepage'
    }

    if (new_org.save(flush: true, failOnError: true)) {
      log.debug("Saved ok");
    }
    else {
      log.error("Problem saving new org. ${new_org.errors}");
    }
  }

  @Secured(value = ["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def enrichJsonLDOrg(org, jsonld) {
    log.debug("Enrich existing..");
    request.JSON.'skos:altLabel'?.each { al ->
      println("checking alt label ${al}");
      org.ensureVariantName(al);
    }

  }

  /**
   *  assertOrg()
   *  allow an authorized external component to send in a JSON structure following this template:
   *      [
   *         name:National Association of Corrosion Engineers,
   *         description:National Association of Corrosion Engineers,
   *         parent:
   *         customIdentifiers:[[identifierType:"idtype", identifierValue:"value"]],
   *         combos:[[linkTo:[identifierType:"ncsu-internal", identifierValue:"ncsu:61929"], linkType:"HasParent"]],
   *         flags:[[flagType:"Org Role", flagValue:"Content Provider"],
   *                [flagType:"Org Role", flagValue:"Publisher"],
   *                [flagType:"Authorized", flagValue:"N"]]
   *      ]
   *
   */
  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def assertOrg() {
    log.debug("assertOrg, request.json = ${request.JSON}");
    def result = [result: 'OK']
    def user = springSecurityService.currentUser
    def assert_errors = false;
    def jsonOrg = request.JSON

    try {
      def located_or_new_org = resolveOrgUsingPrivateIdentifiers(jsonOrg.identifiers)

      if (located_or_new_org == null) {
        if (jsonOrg.name) {
          String orgName = jsonOrg.name
          String orgNormName = Org.generateNormname(orgName)

          // No match. One more attempt to match on norm_name only.
          def org_by_name = Org.findAllByNormname(orgNormName)
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

          if (org_by_name.size() == 1) {
            located_or_new_org = org_by_name[0]
          }

          if (located_or_new_org == null && org_by_name.size() == 0) {

            def variant_normname = GOKbTextUtils.normaliseString(orgName)
            def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status <> ?", [variant_normname, status_deleted]);

            if (candidate_orgs.size() == 1) {
              located_or_new_org = candidate_orgs[0]

              log.debug("Matched Org on variant name!");
            }
            else if (candidate_orgs.size() == 0) {

              log.debug("Create new org name will be \"${jsonOrg.name}\" (${jsonOrg.name?.length()})");

              located_or_new_org = new Org(name: jsonOrg.name, normname: orgNormName, uuid: jsonOrg.uuid?.trim()?.size() > 0 ? jsonOrg.uuid : null)

              log.debug("Attempt to save - validate: ${located_or_new_org}");

              if (located_or_new_org.save(flush: true, failOnError: true)) {
                log.debug("Saved ok");
              }
              else {
                assert_errors = true;
              }
            }
            else {
              log.debug("Multiple matches via variant name, skipping Org!");
              assert_errors = true;
            }
          }
          else if (org_by_name.size == 1) {
            log.debug("Matched Org by normname!")
          }
          else {
            log.debug("Multiple matches for org via normname!")
            assert_errors = true;
          }
        }
        else {
          log.warn("Provided Org has no name!");
          assert_errors = true;
        }
      }
      else {
        log.debug("Located existing record.. Still update...");
      }

      if (assert_errors) {
        log.debug("Save failed ${located_or_new_org}");
        result.errors = []
        located_or_new_org.errors.each { e ->
          log.error("Problem saving new org record", e);
          result.errors.add("${e}".toString());
        }
        result.result = 'ERROR'
        response.setStatus(400)
        result.message = "There was a problem saving the new Org!"
        result.baddata = jsonOrg
        return
      }

      componentUpdateService.setAllRefdata([
        'software', 'service'
      ], jsonOrg, located_or_new_org)

      if (jsonOrg.mission) {
        log.debug("Mission ${jsonOrg.mission}");
        located_or_new_org.mission = RefdataCategory.lookup('Org.Mission', jsonOrg.mission);
      }

      if (jsonOrg.homepage) {
        located_or_new_org.homepage = jsonOrg.homepage
      }

      // Add parent.
      if (jsonOrg.parent) {
        def parentDef = jsonOrg.parent;
        log.debug("Adding parent using ${parentDef.identifierType}:${parentDef.identifierValue}");
        def located_component = KBComponent.lookupByIO(parentDef.identifierType, parentDef.identifierValue)
        if (located_component) {
          located_or_new_org.parent = located_component
        }
      }

      log.debug("Combo processing: ${jsonOrg.combos}")

      // combos
      jsonOrg.combos.each { c ->
        log.debug("lookup to item using ${c.linkTo.identifierType}:${c.linkTo.identifierValue}");
        def located_component = KBComponent.lookupByIO(c.linkTo.identifierType, c.linkTo.identifierValue)

        // Located a component.
        if ((located_component != null)) {
          def combo = new Combo(
            type: RefdataCategory.lookup('Combo.Type', c.linkType),
            fromComponent: located_or_new_org,
            toComponent: located_component,
            startDate: new Date()).save(flush: true, failOnError: true);
        }
        else {
          log.error("Problem resolving from(${located_or_new_org}) or to(${located_component}) org for combo");
        }
      }

      // roles
      log.debug("Role Processing: ${jsonOrg.roles}");
      jsonOrg.roles.each { r ->
        log.debug("Adding role ${r}");
        def role = RefdataCategory.lookup("Org.Role", r)

        if (role) {
          located_or_new_org.addToRoles(role)
        }
      }

      // Core data...
      componentUpdateService.ensureCoreData(located_or_new_org, jsonOrg, false, user)

      log.debug("Attempt to save - validate: ${located_or_new_org}");

      if (located_or_new_org.save(flush: true, failOnError: true)) {
        log.debug("Saved ok");
        result.message = "Added/Updated org: ${located_or_new_org.id} ${located_or_new_org.name}";
        result.orgId = located_or_new_org.id
      }
      else {
        log.debug("Save failed ${located_or_new_org}");
        result.errors = []
        located_or_new_org.errors.each { e ->
          log.error("Problem saving new org record", e);
          result.errors.add("${e}".toString());
        }
        response.setStatus(400)
        result.baddata = jsonOrg
        result.result = 'ERROR'
        result.message = "There was a problem saving the Org!"
      }

    }
    catch (Exception e) {
      log.error("Unexpected error importing org", e)
      result.result = 'ERROR'
      result.message = "Unexpected error importing the Org!"
      result.baddata = jsonOrg
      response.setStatus(500)
    }
    render result as JSON
  }

  /**
   *
   *  assertSource()
   *  allow an authorized external component to send in a JSON structure following this template:
   *      [
   *         name:'',
   *         shortcode:'',
   *         editStatus:'',
   *         url:'',
   *         defaultAccessURL:'',
   *         explanationAtSource:'',
   *         contextualNotes:'',
   *         frequency:'',
   *         ruleset:'',
   *         defaultSupplyMethod:'',
   *         defaultDataFormat:''
   *      ]
   */
  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def assertSource() {
    createOrUpdateSource(request.JSON)
  }

  private static def createOrUpdateSource(data) {
    log.debug("assertSource, data = ${data}");
    def result = [:]
    def source_data = data;
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

          componentUpdateService.setAllRefdata([
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
    }
    result
  }


  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  @Transactional(readOnly = true)
  private resolveOrgUsingPrivateIdentifiers(idlist) {
    def located_or_new_org = null;

    if (idlist?.size() ?: 0 > 0) {
      // Rewritten to perform this as a singel query.
      def crit = Org.createCriteria()
      def matched_orgs = crit.list {

        createAlias('outgoingCombos', 'ogc')
        createAlias('ogc.type', 'ogcType')
        createAlias('ogcType.owner', 'ogcOwner')

        createAlias('ogc.toComponent', 'tc')
        createAlias('tc.namespace', 'tcNamespace')

        and {
          and {
            eq 'ogcOwner.desc', 'Combo.Type'
            eq 'ogcType.value', 'KBComponent.Ids'
          }
          or {
            for (def ci : idlist) {
              and {
                eq 'tc.value', ci.identifierValue
                eq 'tcNamespace.value', ci.identifierType
              }
            }
          }
        }

        projections {
          distinct 'id'
        }
      }

      switch (matched_orgs.size()) {
        case 0:
          log.debug("No match for ${idlist}.")
          break
        case 1:
          log.debug("Found single component ID: ${matched_orgs}")
          // Matched one only! This is correct.
          located_or_new_org = Org.read(matched_orgs[0])
          break
        case { it > 1 }:
          log.error("**CONFLICT**")
          log.error("Identifiers ${idlist} matched multiple component IDs ${matched_orgs}!")
          break

      }
    }

    // See if we can locate the item using any of the custom identifiers

    located_or_new_org
  }

//  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
//  private resolveOrgUsingPrivateIdentifiers(idlist) {
//    def located_or_new_org = null;
//
//    // See if we can locate the item using any of the custom identifiers
//    idlist.each { ci ->
//
//      log.debug("Attempt lookup of ${ci.identifierType}:${ci.identifierValue}");
//      if ( located_or_new_org ) {
//        // We've already located an org for this identifier, the new identifier should be new (And therefore added to this org) or
//        // resolve to this org. If it resolves to some other org, then there is a conflict and we fail!
//        def located_component = KBComponent.lookupByIO(ci.identifierType,ci.identifierValue)
//        if ( located_component ) {
//          log.debug("Matched something...");
//          if ( !located_or_new_org ) {
//            located_or_new_org = located_component
//          }
//          else {
//            if ( located_component.id == located_or_new_org.id ) {
//              log.debug("Matched an identifier");
//            }
//            else {
//              log.error("**CONFLICT**");
//            }
//          }
//        }
//        else {
//          // No match.. candidate identifier
//          log.debug("No match for ${ci.identifierType}:${ci.identifierValue}");
//        }
//      }
//      else {
//        located_or_new_org = KBComponent.lookupByIO(ci.identifierType,ci.identifierValue)
//      }
//    }
//    located_or_new_org
//  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def registerVariantName() {
    log.debug("registerVariantName ${params} ${request.JSON}")

    // See if we can locate the variant name as a first class component

    Org variant_org = null;
    if (request.JSON.variantidns != null && request.JSON.variantidvalue != null) {
      variant_org = Org.lookupByIO(request.JSON.variantidns, request.JSON.variantidvalue)
      log.debug("Existing variant org[${request.JSON.variantidns}:${request.JSON.variantidvalue}]: ${variant_org}")
    }

    Org org_to_update = Org.lookupByIO(request.JSON.idns, request.JSON.idvalue)
    log.debug("Org to update[${request.JSON.idns}:${request.JSON.idvalue}]: ${org_to_update}")

    // Update any combos that point to the variant so that they now point to the authorized entry

    // Delete any remaining variant org combox
    // Delete the variant org

    render addVariantNameToComponent(org_to_update, request.JSON.name)
  }

  private static def ensureCoreData(KBComponent component, data, boolean sync = false, user) {

    // Set the name.
    def hasChanged = false
    component.lock()
    component.refresh()

    if (!component.name && data.name) {
      component.name = data.name
      hasChanged = true
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
      String testKey = "${ci.type}|${ci.value}".toString()

      if (ci.type && ci.value && ci.type.toLowerCase() != "originediturl") {

        if (!ids.contains(testKey)) {
          def canonical_identifier = componentLookupService.lookupOrCreateCanonicalIdentifier(ci.type, ci.value)

          log.debug("Checking identifiers of component ${component.id}")

          def duplicate = Combo.executeQuery("from Combo as c where c.toComponent = ? and c.fromComponent = ?", [canonical_identifier, component])

          if (duplicate.size() == 0) {
            log.debug("adding identifier(${ci.type},${ci.value})(${canonical_identifier.id})")
            def new_id = new Combo(fromComponent: component, toComponent: canonical_identifier, status: combo_active, type: combo_type_id).save(flush: true, failOnError: true)
            hasChanged = true
          }
          else if (duplicate.size() == 1 && duplicate[0].status == combo_deleted) {

            def additionalInfo = [:]

            additionalInfo.vars = [canonical_identifier, component.name]

            log.debug("Found a deleted identifier combo for ${canonical_identifier.value} -> ${component}")
            reviewRequestService.raise(
              component,
              "Review ID status.",
              "Identifier ${canonical_identifier} was previously connected to '${component}', but has since been manually removed.",
              user,
              null,
              (additionalInfo as JSON).toString(),
              RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Removed Identifier')
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
    if (!component.source && data.source && data.source?.size() > 0) {
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
      def groups = component.curatoryGroups.collect { [id: it.id, name: it.name] }

      data.curatoryGroups?.each { String name ->
        if (!groups.find { it.name.toLowerCase() == name.toLowerCase() }) {

          def group = CuratoryGroup.findByNormname(CuratoryGroup.generateNormname(name))
          // Only add if we have the group already in the system.
          if (group) {
            component.addToCuratoryGroups(group)
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
            def pType = AdditionalPropertyDefinition.findByPropertyName(it.name)
            if (!pType) {
              pType = new AdditionalPropertyDefinition()
              pType.propertyName = it.name
              pType.save(failOnError: true)
            }

            component.refresh()
            def prop = new KBComponentAdditionalProperty()
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
        if (name?.trim().size() > 0 && !variants.find { it.variantName == name }) {
          // Add the variant name.
          log.debug("Adding variantName ${name} to ${component} ..")

          def new_variant_name = component.ensureVariantName(name)

          // Add to collection.
          if (new_variant_name) {
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
    component.save(flush: true)
  }


  private static addVariantNameToComponent(KBComponent component, variant_name) {

    component.ensureVariantName(variant_name)
  }

  def crossReferencePackage() {
    def result = ['result': 'OK']
    def async = params.async ? params.boolean('async') : false
    def update = params.addOnly ? params.boolean('addOnly') : false
    def request_locale = RequestContextUtils.getLocale(request)
    def rjson = request.JSON
    def cancelled = false
    UpdateToken updateToken = null
    User request_user = null
    def fullsync = false

    log.debug("crossReferencePackage (${request_locale})")

    if (springSecurityService.isLoggedIn()) {
      request_user = springSecurityService.currentUser
    }
    else if (params.updateToken?.trim() || rjson.updateToken?.trim()) {
      def token = params.updateToken ?: rjson.updateToken
      updateToken = UpdateToken.findByValue(token)

      if (updateToken) {
        request_user = updateToken.updateUser

        if (rjson.packageHeader) {
          rjson.packageHeader.uuid = updateToken.pkg.uuid
        }
      }
      else {
        log.error("Unable to reference update token!")
        result.message = "Unable to reference update token!"
        response.setStatus(400)
        result.result = "ERROR"
      }
    }
    else {
      response.setStatus(401)
      response.setHeader('WWW-Authenticate', 'Basic realm="gokb"')
    }

    if (params.fullsync == "true" && request_user?.adminStatus) {
      fullsync = true
    }

    if (rjson?.packageHeader?.name && request_user?.apiUserStatus) {
      def pkg_validation = packageService.validateDTO(rjson.packageHeader, request_locale)

      if (pkg_validation.valid) {

        def upserted_pkg = packageService.upsertDTO(rjson.packageHeader, request_user)

        if (upserted_pkg) {
          Job background_job = concurrencyManagerService.createJob { Job job ->
            def json = rjson
            def invalidTipps = []
            def job_result = [result:'OK']
            def ctr = 0
            def errors = [global: [], tipps: []]

            Package.withNewSession {
              job.startTime = new Date()

              def user = User.get(request_user.id)
              def locale = request_locale
              springSecurityService.reauthenticate(request_user.username)

              final def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
              final def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
              final def status_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')
              final def status_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')

              job.ownerId = user.id

              try {
                def the_pkg = Package.findById(upserted_pkg.id)
                def existing_tipps = []
                def allTippsValid = true
                Boolean curated_pkg = false;
                def is_curator = null;

                if (the_pkg) {
                  job_result.pkgId = the_pkg.id
                  job_result.uuid = the_pkg.uuid
                  job_result.name = the_pkg.name

                  if (the_pkg.curatoryGroups && the_pkg.curatoryGroups?.size() > 0) {
                    is_curator = user.curatoryGroups?.id.intersect(the_pkg.curatoryGroups?.id)

                    if (is_curator?.size() == 1) {
                      job.groupId = is_curator[0]
                    }
                    else if (is_curator?.size() > 1) {
                      log.debug("Got more than one cg candidate!")
                      job.groupId = is_curator[0]
                    }

                    curated_pkg = true;
                  }

                  if (is_curator || !curated_pkg || user.authorities.contains(Role.findByAuthority('ROLE_SUPERUSER'))) {
                    componentUpdateService.ensureCoreData(the_pkg, json.packageHeader, fullsync, user)

                    if (!pkg_validation.match && json.packageHeader.generateToken) {
                      String charset = (('a'..'z') + ('0'..'9')).join()
                      def tokenValue = RandomStringUtils.random(255, charset.toCharArray())

                      if (the_pkg.updateToken) {
                        def currentToken = the_pkg.updateToken
                        the_pkg.updateToken = null
                        currentToken.delete(flush: true)
                      }

                      def update_token = new UpdateToken(pkg: the_pkg, updateUser: user, value: tokenValue).save(flush: true)
                      job_result.updateToken = update_token.value
                    }

                    if (the_pkg.tipps?.size() > 0) {
                      existing_tipps = the_pkg.tipps*.id
                      log.debug("Matched package has ${the_pkg.tipps.size()} TIPPs")
                    }

                    Map platform_cache = [:]
                    log.debug("\n\n\nPackage ID: ${the_pkg.id} / ${json.packageHeader}");
                    def idx = 0

                    // Validate and upsert titles and platforms
                    for (json_tipp in json.tipps) {

                      def title_validation = TitleInstance.validateDTO(json_tipp.title);
                      def tipp_plt_dto = json_tipp.hostPlatform ?: json_tipp.platform
                      allTippsValid &= title_validation.valid

                      if (title_validation && !title_validation.valid) {
                        invalidTipps << json_tipp
                        log.warn("Not allTippsValid after title validation ${json_tipp.title}");
                        def preval_error = [
                          code   : 400,
                          message: messageService.resolveCode('crossRef.package.tipps.error.title.preValidation', [json_tipp.title.name, title_validation.errors], locale),
                          baddata: json_tipp.title,
                          idx    : idx,
                          errors : title_validation.errors
                        ]
                        errors.tipps.add(preval_error)
                      }
                      else {
                        def tippValid = true

                        TitleInstance.withNewSession {
                          def ti = null
                          def titleObj = json_tipp.title.name ? json_tipp.title : json_tipp
                          def title_changed = false
                          def title_class_name = determineTitleClass(titleObj)

                          try {
                            ti = titleLookupService.findOrCreate(
                              titleObj.name,
                              titleObj.publisher,
                              titleObj.identifiers,
                              user,
                              null,
                              title_class_name,
                              titleObj.uuid
                            )

                            if (ti?.id && !ti.hasErrors()) {
                              if (titleObj.imprint) {
                                if (title.imprint?.name == titleObj.imprint) {
                                  // Imprint already set
                                }
                                else {
                                  def imprint = Imprint.findByName(titleObj.imprint) ?: new Imprint(name: titleObj.imprint).save(flush: true, failOnError: true);
                                  title.imprint = imprint;
                                  title_changed = true
                                }
                              }

                              // Add the core data.
                              componentUpdateService.ensureCoreData(ti, titleObj, fullsync, user)

                              title_changed |= componentUpdateService.setAllRefdata([
                                'OAStatus', 'medium',
                                'pureOA', 'continuingSeries',
                                'reasonRetired'
                              ], titleObj, ti)

                              def pubFrom = GOKbTextUtils.completeDateString(titleObj.publishedFrom)
                              def pubTo = GOKbTextUtils.completeDateString(titleObj.publishedTo, false)

                              log.debug("Completed date publishedFrom ${titleObj.publishedFrom} -> ${pubFrom}")

                              title_changed |= ClassUtils.setDateIfPresent(pubFrom, ti, 'publishedFrom')
                              title_changed |= ClassUtils.setDateIfPresent(pubTo, ti, 'publishedTo')

                              if (titleObj.historyEvents?.size() > 0) {
                                def he_result = titleHistoryService.processHistoryEvents(ti, titleObj, title_class_name, user, fullsync, locale)

                                if (he_result.errors) {
                                  result.errors = he_result.errors
                                }
                              }

                              if (title_class_name == 'org.gokb.cred.BookInstance') {

                                log.debug("Adding Monograph fields for ${ti.class.name}: ${ti}")
                                def mg_change = addMonographFields(ti, titleObj)

                                // TODO: Here we will have to add authors and editors, like addPerson() in TSVIngestionService
                                if (mg_change) {
                                  title_changed = true
                                }
                              }

                              titleLookupService.addPublisherHistory(ti, titleObj.publisher_history)

                              ti = ti.merge(flush: true)

                              json_tipp.title.internalId = ti.id
                            }
                            else {
                              def errorObj = ['code': 400, 'message': messageService.resolveCode('crossRef.package.tipps.error.title', json_tipp.title.name, locale), 'baddata': json_tipp.title]
                              if (ti != null) {
                                errorObj.errors = messageService.processValidationErrors(ti.errors)
                                errors.tipps.add(errorObj)
                                ti.discard()
                              }
                              tippValid = false
                              allTippsValid = false
                            }
                          }
                          catch (org.gokb.exceptions.MultipleComponentsMatchedException mcme) {
                            log.debug("Handling MultipleComponentsMatchedException")
                            result.result = "ERROR"
                            result.message = messageService.resolveCode('crossRef.title.error.multipleMatches', [json_tipp?.title?.name, mcme.matched_ids], locale)
                          }
                          catch (grails.validation.ValidationException ve) {
                            log.error("ValidationException attempting to cross reference title", ve);
                            tippValid = false
                            allTippsValid = false
                            def validation_errors = [
                              code   : 400,
                              message: messageService.resolveCode('crossRef.package.tipps.error.title.validation', [json_tipp?.title?.name], locale),
                              baddata: json_tipp,
                              idx    : idx,
                              errors : messageService.processValidationErrors(ve.errors)
                            ]
                            errors.tipps.add(validation_errors)
                          }
                        }

                        if (tippValid && json_tipp.title.internalId == null) {
                          log.error("Failed to locate a title for ${json_tipp?.title} when attempting to create TIPP");
                          allTippsValid = false
                          errors.tipps.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.title', [json_tipp?.title?.name], locale)])
                        }
                      }

                      if (!Platform.validateDTO(tipp_plt_dto).valid) {
                        log.warn("Not valid after platform validation ${tipp_plt_dto}");
                        invalidTipps << json_tipp
                        def plt_errors = [
                          code   : 400,
                          idx    : idx,
                          message: messageService.resolveCode('crossRef.package.tipps.error.platform.preValidation', [tipp_plt_dto?.name], locale),
                          baddata: tipp_plt_dto,
                          errors : valid_plt.errors
                        ]
                        errors.tipps.add(plt_errors)
                      }
                      else {
                        def pl = null
                        def pl_id
                        if (platform_cache.containsKey(tipp_plt_dto.name) && (pl_id = platform_cache[tipp_plt_dto.name]) != null) {
                          pl = Platform.get(pl_id)
                        }
                        else {
                          // Not in cache.
                          try {
                            pl = Platform.upsertDTO(tipp_plt_dto, user);

                            if (pl) {
                              platform_cache[tipp_plt_dto.name] = pl.id

                              componentUpdateService.ensureCoreData(pl, tipp_plt_dto, fullsync)
                            }
                            else {
                              log.error("Could not find/create ${tipp_plt_dto}")
                              errors.tipps.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.platform', [tipp_plt_dto.name], locale)])
                              allTippsValid = false
                            }
                          }
                          catch (grails.validation.ValidationException ve) {
                            log.error("ValidationException attempting to cross reference title", ve);
                            valid_plt = false
                            allTippsValid = false
                            invalidTipps << json_tipp
                            def plt_errors = [
                              code   : 400,
                              message: messageService.resolveCode('crossRef.package.tipps.error.platform.validation', [tipp_plt_dto], locale),
                              baddata: tipp_plt_dto,
                              idx    : idx,
                              errors : messageService.processValidationErrors(ve.errors)
                            ]
                            errors.tipps.add(plt_errors)
                          }
                        }

                        if (pl && (tipp_plt_dto.internalId == null)) {
                          tipp_plt_dto.internalId = pl.id;
                        }
                        else {
                          log.warn("No platform arising from ${tipp_plt_dto}");
                        }
                      }

                      if ((json_tipp.package == null) && (the_pkg.id)) {
                        json_tipp.package = [internalId: the_pkg.id]
                      }
                      else {
                        log.warn("No package");
                        errors.tipps.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.pkgId', [json_tipp.title.name], locale)])
                        allTippsValid = false
                        invalidTipps << json_tipp
                      }

                      if (idx % 50 == 0) {
                        cleanUpGorm()
                      }

                      if (Thread.currentThread().isInterrupted()) {
                        log.debug("Job cancelling ..")
                        cancelled = true
                        job.endTime = new Date()
                        job_result.result = "CANCELLED"
                        job_result.messageService = "Import was cancelled before completion!"
                        break;
                      }

                      idx++
                      job.setProgress(idx, json.tipps.size() * 2)
                    }
                    json.tipps.removeAll(invalidTipps)
                  }
                  else {
                    allTippsValid = false
                    log.warn("Package update for user ${user.username} denied!")
                    job_result.result = 'ERROR'
                    job_result.message = messageService.resolveCode('crossRef.package.error.denied', [the_pkg.name], locale)
                    return job_result
                  }

                  int tippctr = 0;
                  if (!cancelled) {
                    def vidx = 0
                    // validate tipps
                    log.debug("Validating tipps [${tippctr++}]");
                    for (json_tipp in json.tipps) {
                      def validation_result = TitleInstancePackagePlatform.validateDTO(json_tipp)

                      if (validation_result && !validation_result.valid) {
                        invalidTipps.add(json_tipp)
                        log.debug("TIPP Validation failed on ${json_tipp}")
                        allTippsValid = false
                        def tipp_error = [
                          'code' : 400,
                          idx    : vidx,
                          message: messageService.resolveCode('crossRef.package.tipps.error.preValidation', [json_tipp.title.name, validation_result.errors], locale),
                          baddata: json_tipp,
                          errors : validation_result.errors
                        ]
                        errors.tipps.add(tipp_error)

                      }

                      if (vidx % 50 == 0) {
                        cleanUpGorm()
                      }

                      if (Thread.currentThread().isInterrupted()) {
                        log.debug("Job cancelling ..")
                        cancelled = true
                        job.endTime = new Date()
                        job_result.result = "CANCELLED"
                        job_result.message = "Import was cancelled before completion!"
                        break;
                      }

                      vidx++
                    }
                  }

                  if (!cancelled) {
                    log.debug("\n\nupsert tipp data\n\n")
                    tippctr = 0

                    def tipps_to_delete = existing_tipps.clone()
                    def num_removed_tipps = 0

                    def tipp_upsert_start_time = System.currentTimeMillis()
                    def tipp_fails = 0
                    def tidx = 0

                    if (json.tipps?.size() > 0) {
                      Package.withNewSession {
                        def pkg_new = Package.get(the_pkg.id)
                        def status_ip = RefdataCategory.lookup('Package.ListStatus', 'In Progress')

                        if (pkg_new.status == status_current && pkg_new?.listStatus != status_ip) {
                          pkg_new.listStatus = status_ip
                          pkg_new.save(flush: true)
                        }
                      }
                    }
                    json.tipps.removeAll(invalidTipps)
                    // upsert valid tipps
                    for (json_tipp in json.tipps) {
                      tippctr++

                      log.debug("Upsert tipp [${tippctr}] ${json_tipp}")
                      def upserted_tipp = null

                      try {
                        upserted_tipp = TitleInstancePackagePlatform.upsertDTO(json_tipp, user)
                        log.debug("Upserted TIPP ${upserted_tipp} with URL ${upserted_tipp?.url}")
                        upserted_tipp = upserted_tipp?.merge(flush: true)

                        componentUpdateService.ensureCoreData(upserted_tipp, json_tipp, fullsync)
                      }
                      catch (grails.validation.ValidationException ve) {
                        log.error("ValidationException attempting to cross reference TIPP", ve);
                        allTippsValid = false
                        tipp_fails++
                        def tipp_errors = [
                          code   : 400,
                          idx    : tidx,
                          message: messageService.resolveCode('crossRef.package.tipps.error.validation', [json_tipp.title.name], locale),
                          baddata: json_tipp,
                          errors : messageService.processValidationErrors(ve.errors)
                        ]
                        errors.tipps.add(tipp_errors)
                        if (upserted_tipp)
                          upserted_tipp.discard()
                      }
                      catch (Exception ge) {
                        log.error("Exception attempting to cross reference TIPP:", ge)
                        allTippsValid = false
                        tipp_fails++
                        def tipp_errors = [
                          code   : 500,
                          idx    : tidx,
                          message: messageService.resolveCode('crossRef.package.tipps.error', [json_tipp.title.name], locale),
                          baddata: json_tipp
                        ]
                        errors.tipps.add(tipp_errors)

                        if (upserted_tipp)
                          upserted_tipp.discard()
                      }

                      if (upserted_tipp) {
                        if (existing_tipps.size() > 0 && existing_tipps.contains(upserted_tipp.id)) {
                          log.debug("Existing TIPP matched!")
                          tipps_to_delete.removeElement(upserted_tipp.id)
                        }

                        if (upserted_tipp?.status != status_deleted && json_tipp.status == "Deleted") {
                          upserted_tipp.deleteSoft()
                          num_removed_tipps++;
                        }
                        else if (upserted_tipp && upserted_tipp?.status != status_retired && json_tipp.status == "Retired") {
                          upserted_tipp.retire()
                          num_removed_tipps++;
                        }
                        else if (upserted_tipp && upserted_tipp.status != status_current && (!json_tipp.status || json_tipp.status == "Current")) {
                          if (upserted_tipp.isDeleted() && !fullsync) {
                            reviewRequestService.raise(
                              upserted_tipp,
                              "Matched TIPP was marked as Deleted.",
                              "Check TIPP Status.",
                              user,
                              null,
                              null,
                              RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Status Deleted')
                            )
                          }
                          upserted_tipp.setActive()
                        }

                        upserted_tipp.save()

                        if (upserted_tipp.isCurrent() && upserted_tipp.hostPlatform?.status != status_current) {
                          def additionalInfo = [:]

                          additionalInfo.vars = [upserted_tipp.hostPlatform.name, upserted_tipp.hostPlatform.status?.value]

                          reviewRequestService.raise(
                            upserted_tipp,
                            "The existing platform matched for this TIPP (${upserted_tipp.hostPlatform}) is marked as ${upserted_tipp.hostPlatform.status?.value}! Please review the URL/Platform for validity.",
                            "Platform not marked as current.",
                            user,
                            null,
                            (additionalInfo as JSON).toString(),
                            RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Platform Noncurrent')
                          )
                        }
                      }
                      else {
                        log.debug("Could not reference TIPP")
                        allTippsValid = false
                        tipp_fails++
                        def tipp_errors = [
                          code   : 500,
                          idx    : tidx,
                          message: messageService.resolveCode('crossRef.package.tipps.error', [json_tipp.title.name], locale),
                          baddata: json_tipp
                        ]
                        errors.tipps.add(tipp_errors)
                      }

                      if (tidx % 50 == 0) {
                        cleanUpGorm()
                      }

                      if (Thread.currentThread().isInterrupted()) {
                        log.debug("Job cancelling ..")
                        cancelled = true
                        job.endTime = new Date()
                        job_result.result = "CANCELLED"
                        job_result.message = "Import was cancelled before completion!"
                        break;
                      }

                      tidx++
                      job.setProgress(tidx + json.tipps.size(), json.tipps.size() * 2)
                    }

                    if (!allTippsValid) {
                      job_result.result = 'WARNING'
                      job_result.message = "Package was created, but ${invalidTipps.size()} TIPPs have been ignored!"
                    }
                    if (!cancelled) {
                      if (!update && existing_tipps.size() > 0) {
                        tipps_to_delete.eachWithIndex { ttd, idx ->

                          def to_retire = TitleInstancePackagePlatform.get(ttd)

                          if (to_retire?.isCurrent()) {
                            if (fullsync) {
                              to_retire.deleteSoft()
                            }
                            else {
                              to_retire.retire()
                            }
                            to_retire.save(failOnError: true)

                            num_removed_tipps++;
                          }

                          if (idx % 50 == 0) {
                            cleanUpGorm()
                          }
                        }
                        if (num_removed_tipps > 0) {
                          def additionalInfo = [:]

                          additionalInfo.vars = [the_pkg.id, num_removed_tipps]

                          reviewRequestService.raise(
                            the_pkg,
                            "TIPPs retired.",
                            "An update to package ${the_pkg.id} did not contain ${num_removed_tipps} previously existing TIPPs.",
                            user,
                            null,
                            (additionalInfo as JSON).toString(),
                            RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'TIPPs Retired')
                          )
                        }
                      }
                      log.debug("Found ${num_removed_tipps} TIPPS to delete/retire from the matched package!")
                      job_result.result = 'OK'
                      job_result.message = messageService.resolveCode('crossRef.package.success', [json.packageHeader.name, tippctr, existing_tipps.size(), num_removed_tipps], locale)

                      Package.withNewSession {
                        def pkg_obj = Package.get(the_pkg.id)
                        if (pkg_obj.status.value != 'Deleted') {
                          pkg_obj.lastUpdateComment = job_result.message
                          pkg_obj.save(flush: true)
                        }
                      }
                      log.debug("Elapsed tipp processing time: ${System.currentTimeMillis() - tipp_upsert_start_time} for ${tippctr} records")
                    }
                    else {
                      job_result.result = 'ERROR'
                      job_result.message = messageService.resolveCode('crossRef.package.error.cancelled', [json.packageHeader.name], locale)
                    }
                  }
                  else {
                    job_result.result = 'ERROR'
                    job_result.message = messageService.resolveCode('crossRef.package.error.cancelled', [json.packageHeader.name], locale)
                  }

                  if (invalidTipps?.size() > 0) {
                    def additionalInfo = [:]

                    if (errors.global.size() > 0 || errors.tipps.size() > 0) {
                      additionalInfo.errorObjects = errors
                    }

                    additionalInfo.vars = [job.id]
                    additionalInfo.skippedItems = invalidTipps

                    reviewRequestService.raise(
                      the_pkg,
                      "Invalid TIPPs.",
                      "An update for this package was incomplete due to ${invalidTipps.size()} TIPPs containing invalid information (JOB ${job.id}).",
                      user,
                      null,
                      (additionalInfo as JSON).toString(),
                      RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Invalid TIPPs')
                    )
                  }
                } else {
                  job_result.result = 'ERROR'
                  errors.global.add(['code': 400, 'message': message.resolveCode('crossRef.package.error', null, locale)])
                }
              }
              catch (Exception e) {
                log.error("Package Crossref failed with Exception", e)
                job_result.result = "ERROR"
                job_result.message = "Package referencing failed with exception!"
                job_result.code = 500
                errors.global.add([code: 500, message: messageService.resolveCode('crossRef.package.error.unknown', null, locale), data: json.packageHeader])
              }
              cleanUpGorm()
            }

            job.endTime = new Date()

            if (!cancelled) {
              job.message(job_result.message.toString())
              job.setProgress(100)
            }

            if (errors.global.size() > 0 || errors.tipps.size() > 0) {
              job_result.errors = errors
            }

            JobResult.withNewSession {
              def result_object = JobResult.findByUuid(job.uuid)

              if (!result_object) {
                def job_map = [
                  uuid: (job.uuid),
                  description: (job.description),
                  resultObject: (job_result as JSON).toString(),
                  type: (job.type),
                  statusText: (job_result?.result),
                  ownerId: (job.ownerId),
                  groupId: (job.groupId),
                  startTime: (job.startTime),
                  endTime: (job.endTime),
                  linkedItemId: (job.linkedItem?.id)
                ]

                result_object = new JobResult(job_map).save(flush: true, failOnError: true)
              }
            }

            log.debug("xRefPackage job result: $job_result")
            return job_result
          }

          log.debug("Starting job ${background_job}..")
          background_job.description = "Package CrossRef (${rjson.packageHeader.name})"
          background_job.type = RefdataCategory.lookupOrCreate('Job.Type', 'PackageCrossRef')
          background_job.linkedItem = [name: upserted_pkg.name,
                                        type: "Package",
                                        id  : upserted_pkg.id,
                                        uuid: upserted_pkg.uuid]
          background_job.message("Starting upsert for Package ${upserted_pkg.name} (uuid: ${upserted_pkg.uuid})".toString())
          background_job.startOrQueue()

          if (async == false) {
            def job_final = null

            try {
              job_final = background_job.get()
            }
            catch (CancellationException ce) {
              result.result = 'ERROR'
              result.job_id = background_job.uuid
              result.message = "The import job was cancelled before completion."
            }

            if (job_final) {
              result = job_final
            }
          } else {
            result.job_id = background_job.uuid
          }
        } else {
          result.result = 'ERROR'
          result.errors = ['code': 400, 'message': message.resolveCode('crossRef.package.error', null, request_locale)]
        }
      } else {
        log.debug("Package validation failed!")
        result.result = 'ERROR'
        response.setStatus(400)
        result.errors << pkg_validation.errors
        result.message = messageService.resolveCode('crossRef.package.error.validation.global', null, request_locale)
      }
    } else if (request_user) {
      if (request_user.apiUserStatus) {
        log.debug("Not ingesting package without name!")
        result.result = "ERROR"
        result.message = messageService.resolveCode('crossRef.package.error.name', [], request_locale)
        result.errors << [name: [[message: messageService.resolveCode('crossRef.package.error.name', null, request_locale), baddata: null]]]
        response.setStatus(400)
      }
      else {
        log.debug("User lacks API access!")
        result.result = "ERROR"
        result.message = messageService.resolveCode('crossRef.package.error.apiRole', [], request_locale)
        result.errors << [name: [
          [message: messageService.resolveCode('crossRef.package.error.apiRole',
            null,
            request_locale),
           baddata: null]
        ]
        ]
        response.setStatus(403)
      }
    } else {
      log.debug("Unable to reference user!")
    }
    // cleanUpGorm()

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  @Transactional
  def crossReferencePlatform() {
    def result = ['result': 'OK']
    def created = false
    def platformJson = request.JSON
    def fullsync = false
    User user = springSecurityService.currentUser

    if (params.fullsync == "true" && user.adminStatus) {
      fullsync = true
    }

    log.debug("crossReferencePlatform - ${platformJson}")
    if (platformJson?.platformUrl?.trim() && (platformJson?.name?.trim() || platformJson?.platformName?.trim())) {
      if (!platformJson.name?.trim() && platformJson.platformName) {
        platformJson.name = platformJson.platformName
      }

      try {
        def p = Platform.upsertDTO(platformJson)

        if (p) {
          log.debug("created or looked up platform ${p}!")

          componentUpdateService.setAllRefdata([
            'software', 'service'
          ], platformJson, p)
          ClassUtils.setRefdataIfPresent(platformJson.authentication, p, 'authentication', 'Platform.AuthMethod')

          if (platformJson.provider) {
            def prov = null

            if (platformJson.provider instanceof String) {
              prov = Org.findByNormname(Org.generateNormname(platformJson.provider))
            }
            else {
              if (platformJson.provider.uuid) {
                prov = Org.findByUuid(platformJson.provider.uuid)
              }

              if (!prov && platformJson.provider.name) {
                prov = Org.findByNormname(Org.generateNormname(platformJson.provider.name))
              }
            }

            if (prov) {
              log.debug("Adding Provider ${prov} to platform ${p}!")
              p.provider = prov
            }
            else {
              log.debug("No provider found for ${platformJson.provider}!")
            }
          }
          p.save(flush: true)

          // Add the core data.
          componentUpdateService.ensureCoreData(p, platformJson, fullsync, user)

          //      if ( changed ) {
          //        p.save(flush:true, failOnError:true);
          //      }
          result.message = "Created/Updated platform ${p}"

          result.platformId = p.id;
        }
        else {
          log.debug("No platform matched for ${platformJson}")
          result.message = "Could not crossreference platform ${platformJson}"
          response.setStatus(500)
          result.result = 'ERROR'
        }
      }
      catch (Exception e) {
        log.debug("Exception while looking up platform!", e)
        result.message = "There was an error looking up platform ${platformJson}"
        response.setStatus(500)
        result.result = "ERROR"
      }
    }
    else {
      log.debug("Missing Platform info for ${platformJson}")
      result.message = "Platform ${platformJson} is missing required information ('name' or 'platformUrl')!"
      response.setStatus(400)
      result.result = "ERROR"
    }
    render result as JSON
  }

  private static boolean setAllRefdata(propNames, data, target, boolean createNew = false) {
    boolean changed = false
    propNames.each { String prop ->
      changed |= ClassUtils.setRefdataIfPresent(data[prop], target, prop, createNew)
    }
    changed
  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def crossReferenceLicense() {
    def result = ['result': 'OK']
    def user = springSecurityService.currentUser

    // Add the license.
    def data = request.JSON
    if (data && data.name) {
      // Use the name to either match or create a Licence.
      License l = License.findOrCreateByName(License.generateNormname(data.name)) ?: new License(name: data.name)

      // Update the properties on the license.
      l.with {
        url = data.url
        file = data.file
        summaryStatement = data.summaryStatement
      }

      componentUpdateService.setAllRefdata([
        'type'
      ], data, l)


      // Add the core data.
      componentUpdateService.ensureCoreData(l, data, false, user)

//      l.save(flush:true, failOnError:true)
    }

    render result as JSON
  }

  /**
   *  Cross reference an incoming title with the database. See an example of calling this controller method
   *  in GOKB_PROJECT slash scripts slash sync_gokb_titles.groovy
   *
   *  Cross reference record::
   *
   *{*    'title':'the_title',
   *    'publisher':'the_publisher',
   *    'identifiers':[
   *{type:'namespace',value:'value'},
   *{type:'isbn', value:'1234-5678'}*    ]
   *    'type':'Serial'|'Monograph',
   *    'variantNames':[
   *      'Array Of Strings - one for each variant name'
   *    ],
   *    'imprint':'the_publisher',
   *    'publishedFrom':'yyyy-MM-dd' 'HH:mm:ss.SSS',
   *    'publishedTo':'yyyy-MM-dd' 'HH:mm:ss.SSS',
   *    'editStatus':'edit_status_value',
   *    'status':'status_value',
   *    'historyEvents':[
   *    ],
   *    'series':'series_name',
   *    'subjectArea':'subject_area_name',
   *    'prices':[
   *{*       'type':'list',
   *       'currency':'EUR',
   *       'amount':12.89
   *}*    ]
   *}*/
  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def crossReferenceTitle() {
    User user = springSecurityService.currentUser
    def rjson = request.JSON
    def cancelled = false
    def async = params.async ? params.boolean('async') : false
    def request_locale = RequestContextUtils.getLocale(request)
    def fullsync = false
    def result

    if (params.fullsync == "true" && user.adminStatus) {
      fullsync = true
    }

    if (org.grails.web.json.JSONArray != rjson.getClass()) {

      result = crossReferenceSingleTitle(rjson, user.id, fullsync, request_locale)

      cleanUpGorm()
    }
    else {
      log.debug("Starting crossReferenceTitle Job")
      Job background_job = concurrencyManagerService.createJob { Job job ->
        def json = rjson
        def locale = request_locale
        def job_result = [:]
        def ctr = 0

        job_result.results = []

        for (e in json) {

          if (Thread.currentThread().isInterrupted()) {
            log.debug("Job cancelling ..")
            cancelled = true
            job.endTime = new Date()
            job_result.status = "cancelled"
            break;
          }

          job_result.results << crossReferenceSingleTitle(e, user.id, fullsync, locale)

          ctr++
          job.setProgress(ctr, json.size())
        }

        job.endTime = new Date()
        job.setProgress(100)
        job.message("Finished processing ${job_result?.results?.size()} titles.".toString())

        JobResult.withNewSession {
          def result_object = JobResult.findByUuid(job.uuid)

          if (!result_object) {
            def job_map = [
              uuid: (job.uuid),
              description: (job.description),
              resultObject: (job_result as JSON).toString(),
              type: (job.type),
              statusText: (job_result?.result),
              ownerId: (job.ownerId),
              groupId: (job.groupId),
              startTime: (job.startTime),
              endTime: (job.endTime)
            ]

            result_object = new JobResult(job_map).save(flush: true, failOnError: true)
          }
        }

        return job_result
      }
      log.debug("Starting job ${background_job}..")

      background_job.startOrQueue()
      background_job.description = "Title CrossRef"
      background_job.type = RefdataCategory.lookupOrCreate('Job.Type', 'TitleCrossRef')
      background_job.startTime = new Date()

      if (async == false) {
        result = background_job.get()
      }
      else {
        result = [job_id: background_job.uuid]
      }
    }

    render result as JSON
  }

  private crossReferenceSingleTitle(Object titleObj, userid, fullsync, locale) {

    def result = ['result': 'OK']

    log.debug("crossReferenceTitle(${titleObj.type},${titleObj.name},${titleObj.identifiers}},...)");

    TitleInstance.withNewSession {
      User user = User.get(userid)

      def title_validation = TitleInstance.validateDTO(titleObj)

      if (title_validation && !title_validation.valid) {
        log.warn("Not valid after title validation ${titleObj}")
        result.result = 'ERROR'
        result.message = messageService.resolveCode('crossRef.title.error.preValidation', [titleObj.name], locale)
        result.baddata = titleObj
        result.errors = title_validation.errors
      }
      else {
        def title_class_name = determineTitleClass(titleObj)

        if (!title_class_name) {
          log.error("Missing or unknown publication type: ${titleObj.type}")
          result.result = "ERROR"
          result.message = messageService.resolveCode('crossRef.title.error.type', [titleObj.name], locale)
          result.errors = [type: [message: messageService.resolveCode('crossRef.title.error.type.local', null, locale), baddata: titleObj.type]]

          return result
        }

        try {
          def title = titleLookupService.findOrCreate(
            titleObj.name,
            titleObj.publisher,
            titleObj.identifiers,
            user,
            null,
            title_class_name,
            titleObj.uuid
          )

          if (title && !title.hasErrors()) {
            def title_changed = false;

            if (titleObj.imprint) {
              if (title.imprint?.name == titleObj.imprint) {
                // Imprint already set
              }
              else {
                def imprint = Imprint.findByName(titleObj.imprint) ?: new Imprint(name: titleObj.imprint).save(flush: true, failOnError: true);
                title.imprint = imprint;
                title_changed = true
              }
            }

            // Add the core data.
            componentUpdateService.ensureCoreData(title, titleObj, fullsync, user)

            title_changed |= componentUpdateService.setAllRefdata([
              'OAStatus', 'medium',
              'pureOA', 'continuingSeries',
              'reasonRetired'
            ], titleObj, title)

            def pubFrom = GOKbTextUtils.completeDateString(titleObj.publishedFrom)
            def pubTo = GOKbTextUtils.completeDateString(titleObj.publishedTo, false)

            log.debug("Completed date publishedFrom ${titleObj.publishedFrom} -> ${pubFrom}")

            title_changed |= ClassUtils.setDateIfPresent(pubFrom, title, 'publishedFrom')
            title_changed |= ClassUtils.setDateIfPresent(pubTo, title, 'publishedTo')

            if (titleObj.historyEvents?.size() > 0) {
              def he_result = titleHistoryService.processHistoryEvents(title, titleObj, title_class_name, user, fullsync, locale)

              if (he_result.errors) {
                result.errors = he_result.errors
              }
            }

            if (title_class_name == 'org.gokb.cred.BookInstance') {

              log.debug("Adding Monograph fields for ${title.class.name}: ${title}")
              def mg_change = addMonographFields(title, titleObj)

              // TODO: Here we will have to add authors and editors, like addPerson() in TSVIngestionService
              if (mg_change) {
                title_changed = true
              }
            }

            titleLookupService.addPublisherHistory(title, titleObj.publisher_history)

            title.save()

            if (!result.message) {
              result.message = messageService.resolveCode('crossRef.title.success', null, locale)
            }
            result.cls = title.class.name
            result.titleId = title.id
            result.uuid = title.uuid
          }
          else if (title) {
            result.result = "ERROR"
            result.baddata = titleObj
            log.error("Cross Reference Title failed: ${titleObj}");
            result.errors = messageService.processValidationErrors(title.errors)

            if (title?.id) {
              result.titleId = title.id
              result.uuid = title.uuid
              result.message = messageService.resolveCode('crossRef.title.error.existing', [title.name, title.id], locale)
              log.error("CrossReference Matched existing title (${title.id}) with errors: ${title.errors}")
            }
            else {
              result.message = "Cross Reference of title ${titleObj.name} failed";
            }
          }
          else {
            result.result = "ERROR"
            result.baddata = titleObj.identifiers
            result.message = messageService.resolveCode('crossRef.title.error.dupes', [title.name], locale)
          }
        }
        catch (org.gokb.exceptions.MultipleComponentsMatchedException mcme) {
          log.debug("Handling MultipleComponentsMatchedException")
          result.result = "ERROR"
          result.message = messageService.resolveCode('crossRef.title.error.multipleMatches', [titleObj.name, mcme.matched_ids], locale)
        }
        catch (grails.validation.ValidationException ve) {
          log.debug("ValidationException attempting to cross reference title", ve);
          result.result = "ERROR"
          result.message = messageService.resolveCode('crossRef.title.error.validation', [titleObj.name], locale)
          result.errors = messageService.processValidationErrors(ve.errors)
          result.baddata = titleObj
        }
        catch (Exception e) {
          log.error("Exception attempting to cross reference title", e);

          if (result.result != 'ERROR') {
            result.result = "ERROR"
            result.message = messageService.resolveCode('crossRef.title.error.unknown', [titleObj.name], locale)
            result.baddata = titleObj
            log.error("Source message causing error (ADD_TO_TEST_CASES): ${titleObj}");
          }
        }
        finally {
          log.debug("Result of cross ref title: ${result}");
        }
      }
    }
    result
  }

  private static determineTitleClass(titleObj) {
    if (titleObj.type) {
      switch (titleObj.type) {
        case "serial":
        case "Serial":
        case "Journal":
        case "journal":
          return "org.gokb.cred.JournalInstance"
          break;
        case "monograph":
        case "Monograph":
        case "Book":
        case "book":
          return "org.gokb.cred.BookInstance"
          break;
        case "Database":
        case "database":
          return "org.gokb.cred.DatabaseInstance"
          break;
        case "Other":
        case "other":
          return "org.gokb.cred.OtherInstance"
          break;
        default:
          return null
          break;
      }
    }
    else {
      return null
    }
  }

  private static addPublisherHistory(TitleInstance ti, publishers) {
    if (publishers && ti) {
      log.debug("Handling publisher history ..")

      def publisher_combos = []
      publisher_combos.addAll(ti.getCombosByPropertyName('publisher'))
      String propName = ti.isComboReverse('publisher') ? 'fromComponent' : 'toComponent'
      String tiPropName = ti.isComboReverse('publisher') ? 'toComponent' : 'fromComponent'

      // Go through each Org.
      for (def pub_to_add : publishers) {

        Org publisher = null
        // Lookup the publisher.
        if (pub_to_add.uuid) {
          publisher = Org.findByUuid(pub_to_add.uuid)
        }

        def norm_pub_name = KBComponent.generateNormname(pub_to_add.name)
        def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

        if (!publisher) {
          publisher = Org.findByNormname(norm_pub_name)
        }

        if (!publisher || publisher.status == status_deleted) {
          def variant_normname = GOKbTextUtils.normaliseString(pub_to_add.name)
          def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status <> ?", [variant_normname, status_deleted]);

          if (candidate_orgs.size() == 1) {
            publisher = candidate_orgs[0]
          }
        }

        if (publisher) {

          LocalDateTime parsedStart = GOKbTextUtils.completeDateString(pub_to_add.startDate)
          LocalDateTime parsedEnd = GOKbTextUtils.completeDateString(pub_to_add.endDate, false)
          Date pub_add_sd = parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null
          Date pub_add_ed = parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null

          boolean found = false
          for (int i = 0; !found && i < publisher_combos.size(); i++) {
            Combo pc = publisher_combos[i]
            def idMatch = pc."${propName}".id == publisher.id

            if (idMatch) {
              if (pub_add_sd && pc.startDate && pub_add_sd != pc.startDate) {
              }
              else if (pub_add_ed && pc.endDate && pub_add_ed != pc.endDate) {
              }
              else {
                found = true
              }
            }


          }

          // Only add if we havn't found anything.
          if (!found) {

            log.debug("Adding new combo for publisher ${publisher} (${propName}) to title ${ti} (${tiPropName})")

            RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, ti.getComboTypeValue('publisher'))

            def combo = null

            if (propName == "toComponent") {
              combo = new Combo(
                type: (type),
                status: pub_to_add.status ? RefdataCategory.lookupOrCreate(Combo.RD_STATUS, pub_to_add.status) : DomainClassExtender.getComboStatusActive(),
                startDate: pub_add_sd,
                endDate: pub_add_ed,
                toComponent: publisher,
                fromComponent: ti
              )
            }
            else {
              combo = new Combo(
                type: (type),
                status: pub_to_add.status ? RefdataCategory.lookupOrCreate(Combo.RD_STATUS, pub_to_add.status) : DomainClassExtender.getComboStatusActive(),
                startDate: pub_add_sd,
                endDate: pub_add_ed,
                fromComponent: publisher,
                toComponent: ti
              )
            }

            // Depending on where the combo is defined we need to add a combo.
//            if (ti.isComboReverse('publisher')) {
//              ti.addToIncomingCombos(combo)
//            } else {
//              ti.addToOutgoingCombos(combo)
//            }
//            publisher.save()

            if (combo) {
              combo.save(flush: true, failOnError: true)

              // Add the combo to our list to avoid adding duplicates.
              publisher_combos.add(combo)

              log.debug "Added publisher ${publisher.name} for '${ti.name}'" +
                (combo.startDate ? ' from ' + combo.startDate : '') +
                (combo.endDate ? ' to ' + combo.endDate : '')
            }
            else {
              log.error("Could not create publisher Combo..")
            }

          }
          else {
            log.debug "Publisher ${publisher.name} already set against '${ti.name}'"
          }

        }
        else {
          log.debug "Could not find org name: ${pub_to_add.name}, with normname: ${norm_pub_name}"
        }
      }
    }
  }

  private static addMonographFields(BookInstance bi, titleObj) {

    def book_changed = false

    def bookStringAttrs = ["editionNumber", "editionDifferentiator",
                           "editionStatement", "volumeNumber",
                           "summaryOfContent", "firstAuthor", "firstEditor"]

    bookStringAttrs.each {
      if (titleObj[it] && titleObj[it].toString().trim().length() > 0) {
        book_changed |= ClassUtils.setStringIfDifferent(bi, it, titleObj[it])
      }
    }
    def dfip = GOKbTextUtils.completeDateString(titleObj.dateFirstInPrint)
    def dfo = GOKbTextUtils.completeDateString(titleObj.dateFirstOnline, false)

    book_changed |= ClassUtils.setDateIfPresent(dfip, bi, 'dateFirstInPrint')
    book_changed |= ClassUtils.setDateIfPresent(dfo, bi, 'dateFirstOnline')

    if (book_changed) {
      bi.save(flush: true, failOnError: true)
    }

    book_changed
  }

  def getJobInfo() {
    def result = ['result': 'OK', 'params': params]
    User user = null
    String uuid = params.id

    if (uuid == null) {
      result.result = "ERROR"
      response.setStatus(400)
      result.message = "Request is missing an id parameter."
    }
    else {
      if (springSecurityService.isLoggedIn()) {
        user = springSecurityService.currentUser
      }
      else if (params.updateToken?.trim()) {
        def updateToken = UpdateToken.findByValue(params.updateToken)

        if (updateToken) {
          user = updateToken.updateUser
        }
        else {
          log.error("Unable to reference update token!")
          result.message = "Unable to reference update token!"
          response.setStatus(400)
          result.result = "ERROR"
        }
      }
      else {
        response.setStatus(401)
        response.setHeader('WWW-Authenticate', 'Basic realm="gokb"')
      }

      if (user) {
        Job job = concurrencyManagerService.getJob(uuid)

        if (job) {
          log.debug("${job}")

          if (user.superUserStatus || (job.ownerId && job.ownerId == user.id)) {
            result.description = job.description
            result.type = job.type ? [value: job.type.value, id: job.type.id] : null
            result.linkedItem = job.linkedItem
            result.startTime = job.startTime

            if (job.endTime || job.isCancelled()) {
              result.finished = true
              result.endTime = job.endTime
              try {
                result.job_result = job.get()
              }
              catch (CancellationException ce) {
                result.cancelled = true
              }
            }
            else {
              result.finished = false
              result.progress = job.progress
            }
          }
          else {
            result.result = "ERROR"
            response.setStatus(403)
            result.message = "No permission to view job with ID ${id}."
          }
        } else {
          def persistedResult = JobResult.findByUuid(uuid)

          if (persistedResult) {
            def linkedItemMap = null

            if (persistedResult.linkedItemId) {
              def linkedItem = KBComponent.get(persistedResult.linkedItemId)

              if (linkedItem) {
                linkedItemMap = [id: linkedItem.id, name: linkedItem.name, uuid: linkedItem.uuid, type: linkedItem.niceName]
              }
            }

            result.description = persistedResult.description
            result.type = persistedResult.type ? [value: persistedResult.type.value, id: persistedResult.type.id] : null
            result.linkedItem = linkedItemMap
            result.startTime = persistedResult.startTime
            result.endTime = persistedResult.endTime
            result.job_result = new JsonSlurper().parseText(persistedResult.resultObject)
            result.finished = true

            if (result.job_result?.result == 'CANCELLED') {
              result.cancelled = true
            }
          } else {
            result.result = "ERROR"
            response.setStatus(404)
            result.message = "Could not find job with ID ${id}."
          }
        }
      }
    }
    render result as JSON
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def loadMacroList() {

    def cleanData = { String data ->
      String d = data.trim()
      d != '' && d != '\\N' ? d : null
    }

    def title_file = request.getFile("macros")?.inputStream
    char del = '\t'
    char quote = '"'
    def r = new CSVReader(new InputStreamReader(title_file, java.nio.charset.Charset.forName('UTF-8')), del, quote)

    def col_positions = ['id': 0, 'name': 1, 'desc': 2, 'transformations': 3]
    String[] nl = r.readNext()

    int rowctr = 0
    def ret = [:]
    while (nl != null) {
      rowctr++
      try {
        if (nl.length >= col_positions.size() && cleanData(nl[col_positions.'name'])) {

          String name = cleanData(nl[col_positions.'name'])
          Macro m = Macro.findByNormname(Macro.generateNormname(name)) ?: new Macro()

          // Update
          m.name = name
          m.description = cleanData(nl[col_positions.'desc'])
          m.refineTransformations = cleanData(nl[col_positions.'transformations'])

          // Save to DB
          m.save(flush: true, failOnError: true)
          log.info "Created/Updated macro with id ${m.id}"
          ret["Row ${rowctr}"] = "Created Macro with ID ${m.id}"
        }
        else {
          log.error("Unable to parse row ${rowctr}..")
          ret["Row ${rowctr}"] = "Failed to parse"
        }
      }
      catch (Exception e) {
        log.error("Unable to process row ${rowctr}..", e)
        ret["Row ${rowctr}"] = "Exception thrown ${e}"
      }
      nl = r.readNext()
    }
    render ret as JSON
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def loadTitleList() {
    def title_file = request.getFile("titleFile")?.inputStream
    char tab = '\t'
    char quote = '"'
    def r = new CSVReader(new InputStreamReader(title_file, java.nio.charset.Charset.forName('UTF-8')), tab, quote)

    def col_positions = ['identifier.pissn': -1, 'identifier.eissn': -1, 'title': -1]

    String[] header = r.readNext()
    int ctr = 0
    header.each {
      col_positions[it.toLowerCase()] = ctr++
    }

    if ((col_positions.'title' != -1) &&
      ((col_positions.'identifier.pissn' != -1) ||
        (col_positions.'identifier.eissn' != -1))) {

      // So long as we have at least one identifier...
      String[] nl = r.readNext()

      int rowctr = 0;

      while (nl != null) {
        try {
          KBComponent.withNewTransaction() {

            def candidate_identifiers = []

            if ((col_positions.'identifier.pissn' != -1) &&
              (nl[col_positions.'identifier.pissn']?.length() > 0) &&
              (nl[col_positions.'identifier.pissn'].toLowerCase() != 'null')) {
              candidate_identifiers.add([type: 'issn', value: nl[col_positions.'identifier.pissn']]);
            }

            if ((col_positions.'identifier.eissn' != -1) &&
              (nl[col_positions.'identifier.eissn']?.length() > 0) &&
              (nl[col_positions.'identifier.eissn'].toLowerCase() != 'null')) {
              candidate_identifiers.add([type: 'eissn', value: nl[col_positions.'identifier.eissn']]);
            }

            if (candidate_identifiers.size() > 0) {
              log.debug("Looking up ${candidate_identifiers} - ${nl[col_positions.'title']}");
              // def existing_component = titleLookupService.find (nl[col_positions.'title'], null, candidate_identifiers)
            }
            else {
              log.debug("No candidate identifiers: ${nl}");
            }
          }
        }
        catch (Exception e) {
          log.error("Unable to process..", e);
        }

        if (rowctr++ > 100) {
          log.debug("CleanUpGorm..");
          rowctr = 0;
          cleanUpGorm()
        }
        nl = r.readNext()
        log.debug("Next row: ${nl}");
      }
    }
    log.debug("Done");
    redirect(action: 'index');
  }

  private def cleanUpGorm() {
    log.debug("Clean up GORM");

    // Get the current session.
    def session = sessionFactory.currentSession

    // flush and clear the session.
    session.flush()
    session.clear()
  }
}
