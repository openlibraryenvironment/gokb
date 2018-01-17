package org.gokb

import grails.converters.JSON
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured;
import org.gokb.cred.*
import au.com.bytecode.opencsv.CSVReader
import com.k_int.ClassUtils

import groovy.util.logging.*


@Log4j
class IntegrationController {

  def grailsApplication
  def springSecurityService
  def titleLookupService
  def applicationEventService
  def sessionFactory
  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP


  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def index() {
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def assertJsonldPlatform() {
    def result = [result:'OK']
    def name = request.JSON.'skos:prefLabel'
    def normname = GOKbTextUtils.norm2(name)
    def located_entries = KBComponent.findAllByNormname(normname)
    log.debug("assertJsonldPlatform ${name}/${normname}");
    if ( located_entries.size() == 0 ) {
      log.debug("No platform with normname ${normname} - create");
      def new_platform = new org.gokb.cred.Platform(name:name, normname:normname).save()
      result.message="Added new platform"
    }
    else {
      result.message="Entity with that name already exists.."
    }
    render result as JSON
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def assertGroup() {
    def result = [result:'OK']
    def name = request.JSON.name
    def normname = CuratoryGroup.generateNormname(name)
    def group = CuratoryGroup.findByNormname(normname) ?: new CuratoryGroup (name: name)

    // Defaults first.
    ensureCoreData(group, request.JSON)

    // Find by username but do not create missing entries.
    def owner = request.JSON.owner
    if (owner) {
      group.owner = User.findByUsername(owner)
    }

    // Need to add all users to the group.
    def memberNames = request.JSON.users
    if (memberNames) {
      def members = User.createCriteria().list {
        'in' ('username', memberNames)
      }

      members.each {
        group.addToUsers(it)
      }
    }

    group.save(flush: true, failOnError:true)

    render result as JSON
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def assertJsonldOrg() {
    // log.debug("assertOrg, request.json = ${request.JSON}");
    def result=[:]
    result.status = true;

    try {

      def name = request.JSON.'skos:prefLabel'

      if ( ( name != null ) && ( name.trim().length() > 0 ) ) {

        log.debug("Trying to locate component with ID ${request.JSON.'@id'} name is \"${name}\"");

        // Try and match on primary ID
        def located_entries = KBComponent.lookupByIdentifierValue([request.JSON.'@id'.toString()] as String[]);

        if ( located_entries?.size() == 1 ) {
          log.debug("Identified record..");
          enrichJsonLDOrg(located_entries[0], request.JSON)
        }
        else if ( located_entries?.size() == 0 ) {

          log.debug("Not identified - try sameAs relations");

          if ( request.JSON.'owl:sameAs' != null ) {
            log.debug("Attempt lookup by sameAs : ${request.JSON.'owl:sameAs' as String[]} ");
            located_entries = KBComponent.lookupByIdentifierValue((request.JSON.'owl:sameAs') as String[])
          }
          else {
            log.debug("No owl:sameAs entries found");
          }

          if ( located_entries?.size() == 0 ) {
            log.debug("Failed to match on same-as. Attempting primary name match");
            def normname = GOKbTextUtils.norm2(name)
            located_entries = KBComponent.findAllByNormname(normname)
            if ( located_entries?.size() == 0 ) {
              log.debug("No match on normalised name ${normname}.. Trying variant names");
              def variant_normname = GOKbTextUtils.normaliseString( name )
              located_entries = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status.value <> 'Deleted'",[variant_normname]);

              if ( located_entries?.size() == 0 ) {

                createJsonLDOrg(request.JSON);
              }
              else if( located_entries?.size() == 1 ){
                log.debug("Exact match on normalised variantname ${variant_normname} - good enough");
                enrichJsonLDOrg(located_entries[0], request.JSON)
              }
              else{
                log.error("Multiple matches on normalised variant name... abandon all hope");
              }
            }
            else if ( located_entries?.size() == 1 ) {
               log.debug("Exact match on normalised name ${normname} - good enough");
               enrichJsonLDOrg(located_entries[0], request.JSON)
            }
            else {
              log.error("Multiple matches on normalised name... abandon all hope");
            }

          }
          else if ( located_entries?.size() == 1 ) {
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
    catch ( Exception e ) {
      log.error("Problem",e)
      result.status = 'ERROR'
    }
    finally {
    }

    render result as JSON
  }

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
    def new_org = new Org(name:name)

    def primary_identifier = Identifier.lookupOrCreateCanonicalIdentifier('global',id)
    new_org.ids.add(primary_identifier)

    request.JSON.'owl:sameAs'?.each {  said ->

      // Double check that this identifier is NOT already used
      def existing_usage = KBComponent.lookupByIO('global',said)
      if ( existing_usage == null ) {
        def identifier = Identifier.lookupOrCreateCanonicalIdentifier('global',said)
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

    if ( request.JSON.'foaf:homepage' != null ) {
      new_org.homepage = request.JSON.'foaf:homepage'
    }

    if ( new_org.save(flush:true, failOnError : true) ) {
      log.debug("Saved ok");
    }
    else {
      log.error("Problem saving new org. ${new_org.errors}");
    }
  }

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
  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def assertOrg() {
    log.debug("assertOrg, request.json = ${request.JSON}");
    def result=[:]
    result.status = true;
    def assert_errors = false;

    try {
      def located_or_new_org = resolveOrgUsingPrivateIdentifiers(request.JSON.identifiers)

      if ( located_or_new_org == null ) {
        if ( request.JSON.name ) {
          String orgName = request.JSON.name
          String orgNormName = Org.generateNormname (orgName)

          // No match. One more attempt to match on norm_name only.
          def org_by_name = Org.findAllByNormname( orgNormName )

          if ( org_by_name.size() == 1 ) {
            located_or_new_org = org_by_name[0]
          }

          if ( located_or_new_org == null && org_by_name.size() == 0 ) {

            def variant_normname = GOKbTextUtils.normaliseString( orgName )
            def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status.value <> 'Deleted'",[variant_normname]);

            if(candidate_orgs.size() == 1){
              located_or_new_org = candidate_orgs[0]

              log.debug("Matched Org on variant name!");
            }
            else if( candidate_orgs.size() == 0 ){

              log.debug("Create new org name will be \"${request.JSON.name}\" (${request.JSON.name?.length()})");

              located_or_new_org = new Org(name:request.JSON.name, normname:orgNormName)

              log.debug("Attempt to save - validate: ${located_or_new_org}");

              if ( located_or_new_org.save(flush:true, failOnError : true) ) {
                log.debug("Saved ok");
              } else {
                assert_errors = true;
              }
            }

            else {
              log.debug("Multiple matches via variant name, skipping Org!");
              assert_errors = true;
            }
          }

          else if ( org_by_name.size == 1 ) {
            log.debug("Matched Org by normname!")
          }

          else {
            log.debug("Multiple matches for org via normname!")
            assert_errors = true;
          }
        } else {
          log.warn("Provided Org has no name!");
          assert_errors = true;
        }
      } else {
        log.debug("Located existing record.. Still update...");
      }

      if(assert_errors){
        log.debug("Save failed ${located_or_new_org}");
        result.errors = []
        located_or_new_org.errors.each { e ->
          log.error("Problem saving new org record",e);
          result.errors.add("${e}".toString());
        }
        result.status = false;
        return
      }

      setAllRefdata ([
        'software', 'service'
      ], request.JSON, located_or_new_org)

      if ( request.JSON.mission ) {
        log.debug("Mission ${request.JSON.mission}");
        located_or_new_org.mission = RefdataCategory.lookupOrCreate('Org.Mission',request.JSON.mission);
      }

      if ( request.JSON.homepage ) {
        located_or_new_org.homepage = request.JSON.homepage
      }

      // Add parent.
      if (request.JSON.parent) {
        def parentDef = request.JSON.parent;
        log.debug("Adding parent using ${parentDef.identifierType}:${parentDef.identifierValue}");
        def located_component = KBComponent.lookupByIO(parentDef.identifierType,parentDef.identifierValue)
        if (located_component) {
          located_or_new_org.parent = located_component
        }
      }

      log.debug("Combo processing: ${request.JSON.combos}")

      // combos
      request.JSON.combos.each { c ->
        log.debug("lookup to item using ${c.linkTo.identifierType}:${c.linkTo.identifierValue}");
        def located_component = KBComponent.lookupByIO(c.linkTo.identifierType,c.linkTo.identifierValue)

        // Located a component.
        if ( ( located_component != null ) ) {
          def combo = new Combo(
            type:RefdataCategory.lookupOrCreate('Combo.Type',c.linkType),
            fromComponent:located_or_new_org,
            toComponent:located_component,
            startDate:new Date()).save(flush:true,failOnError:true);
        }
        else {
          log.error("Problem resolving from(${located_or_new_org}) or to(${located_component}) org for combo");
        }
      }

      // roles
      log.debug("Role Processing: ${request.JSON.roles}");
      request.JSON.roles.each { r ->
        log.debug("Adding role ${r}");
        def role = RefdataCategory.lookupOrCreate("Org.Role", r)
        located_or_new_org.addToRoles(role)
      }

      // Core data...
      ensureCoreData(located_or_new_org, request.JSON)

      log.debug("Attempt to save - validate: ${located_or_new_org}");

      if ( located_or_new_org.save(flush:true, failOnError : true) ) {
        log.debug("Saved ok");
      }
      else {
        log.debug("Save failed ${located_or_new_org}");
        result.errors = []
        located_or_new_org.errors.each { e ->
          log.error("Problem saving new org record",e);
          result.errors.add("${e}".toString());
        }
        result.status = false;
        return
      }

      result.msg="Added/Updated org: ${located_or_new_org.id} ${located_or_new_org.name}";

    }
    catch ( Exception e ) {
      log.error("Unexpected error importing org",e)
      result.msg="ERROR: ${e}";
      result.status=false
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
  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def assertSource() {
    createOrUpdateSource ( request.JSON )
  }

  private static def createOrUpdateSource( data ) {
    log.debug("assertSource, data = ${data}");
    def result=[:]
    result.status = true;

    try {
      if ( data.name ) {
        def located_or_new_source = Source.findByNormname( Source.generateNormname(data.name) ) ?: new Source(name:data.name)

        ClassUtils.setStringIfDifferent(located_or_new_source,'url',data.url)
        ClassUtils.setStringIfDifferent(located_or_new_source,'defaultAccessURL',data.defaultAccessURL)
        ClassUtils.setStringIfDifferent(located_or_new_source,'explanationAtSource',data.explanationAtSource)
        ClassUtils.setStringIfDifferent(located_or_new_source,'contextualNotes',data.contextualNotes)
        ClassUtils.setStringIfDifferent(located_or_new_source,'frequency',data.frequency)
        ClassUtils.setStringIfDifferent(located_or_new_source,'ruleset',data.ruleset)

        setAllRefdata ([
          'software', 'service'
        ], data, located_or_new_source)

        ClassUtils.setRefdataIfPresent(data.defaultSupplyMethod, located_or_new_source, 'defaultSupplyMethod', 'Source.DataSupplyMethod')
        ClassUtils.setRefdataIfPresent(data.defaultDataFormat, located_or_new_source, 'defaultDataFormat', 'Source.DataFormat')

        ensureCoreData(located_or_new_source, data)

        log.debug("Variant names processing: ${data.variantNames}")

        // variants
        data.variantNames.each { vn ->
          addVariantNameToComponent(located_or_new_source, vn)
        }

        result['component'] = located_or_new_source
      }
    }
    catch ( Exception e ) {
      e.printStackTrace()
    }
    result
  }


  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  @Transactional(readOnly=true)
  private resolveOrgUsingPrivateIdentifiers(idlist) {
    def located_or_new_org = null;

    if (idlist?.size() ?:0 > 0) {
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
        case {it > 1} :
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

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def registerVariantName() {
    log.debug("registerVariantName ${params} ${request.JSON}")

    // See if we can locate the variant name as a first class component

    Org variant_org = null;
    if ( request.JSON.variantidns != null && request.JSON.variantidvalue != null ) {
      variant_org = Org.lookupByIO(request.JSON.variantidns,request.JSON.variantidvalue)
      log.debug("Existing variant org[${request.JSON.variantidns}:${request.JSON.variantidvalue}]: ${variant_org}")
    }

    Org org_to_update = Org.lookupByIO(request.JSON.idns,request.JSON.idvalue)
    log.debug("Org to update[${request.JSON.idns}:${request.JSON.idvalue}]: ${org_to_update}")

    // Update any combos that point to the variant so that they now point to the authorized entry

    // Delete any remaining variant org combox
    // Delete the variant org

    render addVariantNameToComponent (org_to_update, request.JSON.name)
  }

  private static def ensureCoreData ( KBComponent component, data ) {

    // Set the name.
    if(!component.name && data.name) {
      component.name = data.name
    }

    // Core refdata.
    if (!component.status) {
      setAllRefdata ([
        'status', 'editStatus',
      ], data, component)
    }

    // Identifiers
    log.debug("Identifier processing ${data.identifiers}")
    Set<String> ids = component.ids.collect { "${it.namespace?.value}|${it.value}".toString() }
    data.identifiers.each { ci ->
      String testKey = "${ci.type}|${ci.value}".toString()
      if (!ids.contains(testKey)) {
        def canonical_identifier = Identifier.lookupOrCreateCanonicalIdentifier(ci.type,ci.value)
        log.debug("Checking identifiers of component ${component.id}")
        def duplicate = Combo.executeQuery("Select c.id from Combo as c where c.toComponent.id = ? and c.fromComponent.id = ?",[canonical_identifier.id,component.id])
        if(duplicate.size() == 0){
          log.debug("adding identifier(${ci.type},${ci.value})(${canonical_identifier.id})")
          component.ids.add(canonical_identifier)
        }else{
          log.debug("Identifier combo is already present, probably via titleLookupService.")
        }

        // Add the value for comparison.
        ids << testKey
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
    if (component.respondsTo('addToCuratoryGroups')) {
      Set<String> groups = component.curatoryGroups.collect { "${it.name}".toString() }
      data.curatoryGroups?.each { String name ->
        if (!groups.contains(name)) {

          def group = CuratoryGroup.findByNormname(CuratoryGroup.generateNormname(name))
          // Only add if we have the group already in the system.
          if (group) {
            component.addToCuratoryGroups ( group )
            groups << name
          }
        }
      }
    }

    // Save the component so we have something to set the names against.
    component.save(failOnError: true, flush:true)

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

    // Variant names.
    if (data.variantNames) {
      Set<String> variants = component.variantNames.collect { "${it.variantName}".toString() }
      for (String name : data.variantNames) {
        if (!variants.contains(name)) {
          // Add the variant name.
          def new_variant_name = new KBComponentVariantName(variantName: name, owner: component)
          new_variant_name.save(failOnError: true)

          // Add to collection.
          variants << name
        }
      }
    }
  }


  private static addVariantNameToComponent (KBComponent component, variant_name) {

    component.ensureVariantName(variant_name)
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def crossReferencePackage() {
    def result = [ 'result' : 'OK' ]
    User user = springSecurityService.currentUser
    if ( request.JSON.packageHeader.name ) {
      def valid = Package.validateDTO(request.JSON.packageHeader)
      if ( valid ) {
        def the_pkg = Package.upsertDTO(request.JSON.packageHeader, user)
        def existing_tipps = []
        Boolean curated_pkg = false;
        def is_curator = null;
        
        if ( the_pkg.curatoryGroups && the_pkg.curatoryGroups?.size() > 0 ) {
          is_curator = user.curatoryGroups?.id.intersect(the_pkg.curatoryGroups?.id)
          curated_pkg = true;
        }
        
        if ( is_curator || !curated_pkg ) {
          if ( the_pkg.tipps?.size() > 0 ) {
            existing_tipps = the_pkg.tipps.collect { it.id }
            log.debug("Matched package has ${the_pkg.tipps.size()} TIPPs")
          }

          Map platform_cache = [:]
          log.debug("\n\n\nPackage ID: ${the_pkg.id} / ${request.JSON.packageHeader}");

          // Validate and upsert titles and platforms
          request.JSON.tipps.each { tipp ->

            TitleInstance.withNewSession {

              valid &= TitleInstance.validateDTO(tipp.title);

              if ( !valid )
                log.warn("Not valid after title validation ${tipp.title}");

              def ti = TitleInstance.upsertDTO(titleLookupService, tipp.title, user);
              
              if ( ti && ( tipp.title.internalId == null ) ) {
                tipp.title.internalId = ti.id;
              }

              if ( tipp.title.internalId == null ) {
                log.error("Failed to locate or a title for ${tipp.title} when attempting to create TIPP");
              }

              valid &= Platform.validateDTO(tipp.platform);
              if ( !valid )
                log.warn("Not valid after platform validation ${tipp.platform}");

              if ( valid ) {

                def pl = null
                def pl_id
                if (platform_cache.containsKey(tipp.platform.name) && (pl_id = platform_cache[tipp.platform.name]) != null) {
                  pl = Platform.get(pl_id)
                } else {
                  // Not in cache.
                  pl = Platform.upsertDTO(tipp.platform, user);

                  if(pl){
                    platform_cache[tipp.platform.name] = pl.id
                  }else{
                    log.error("Could not find/create ${tipp.platform}")
                    result['errors'].add(['code': 500, 'message': "TIPP platform ${tipp.platform.name} could not be matched/created! Please check for duplicates in GOKb!"])
                    valid = false
                  }
                }

                if ( pl && ( tipp.platform.internalId == null ) ) {
                  tipp.platform.internalId = pl.id;
                }
                else {
                  log.warn("No platform arising from ${tipp.platform}");
                }
              }
              else {
                log.warn("Skip platform upsert ${tipp.platform} - Not valid after platform check");
              }
  //
  //            def pkg = the_pkg.id != null ? Package.get(the_pkg.id) : null
              if ( ( tipp.package == null ) && ( the_pkg.id ) ) {
                tipp.package = [ internalId: the_pkg.id ]
              }
              else {
                log.warn("No package");
                valid = false
              }
            }
          }
        }
        else{
          valid = false
          log.warn("Package update denied!")
          response.status = 403
          result['result'] = 'ERROR'
          result['errors'] = []
          result['errors'].add(['code': 403, 'message': "Insufficient permissions to edit matched Package ${the_pkg}. You have to belong to a connected CuratoryGroup to edit Packages."])
        }

//        cleanUpGorm()

        int tippctr=0;
        if ( valid ) {
          // If valid so far, validate tipps
          log.debug("Validating tipps [${tippctr++}]");
          request.JSON.tipps.each { tipp ->
            def validation_result = TitleInstancePackagePlatform.validateDTO(tipp)
            if ( !validation_result) {
              log.error("TIPP Validation failed on ${tipp}")
            }
          }
        }
        else {
          log.warn("Not validating tipps - failed pre validation")
        }


        log.debug("\n\nupsert tipp data\n\n")
        tippctr=0

        def tipps_to_delete = existing_tipps
        def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')

        if ( valid ) {
          def tipp_upsert_start_time = System.currentTimeMillis()
          // If valid, upsert tipps
          request.JSON.tipps.each { tipp ->
            TitleInstancePackagePlatform.withNewSession {
              log.debug("Upsert tipp [${tippctr++}] ${tipp}")

              def upserted_tipp = TitleInstancePackagePlatform.upsertDTO(tipp)

              if ( existing_tipps.size() > 0 && upserted_tipp && existing_tipps.contains(upserted_tipp.id) ) {
                log.debug("Existing TIPP matched!")
                tipps_to_delete.remove(upserted_tipp.id)
              }
            }
          }
          def num_deleted_tipps = 0;
          
          if ( existing_tipps.size() > 0 ) {


            tipps_to_delete.each { ttd ->
              
              def to_retire = TitleInstancePackagePlatform.get(ttd)
              
              if ( to_retire.isCurrent() ) {
                
                to_retire.retire()
                to_retire.save(failOnError: true)

//                 ReviewRequest.raise(
//                     to_retire,
//                     "TIPP retired.",
//                     "An update to this package did not contain this TIPP.",
//                     user
//                 )
                num_deleted_tipps++;
              }
            }
            if( num_deleted_tipps > 0 ) {
              ReviewRequest.raise(
                  the_pkg,
                  "TIPPs retired.",
                  "An update to package ${the_pkg.id} did not contain ${num_deleted_tipps} previously existing TIPPs.",
                  user
              )
            }
          }
          log.debug("Found ${num_deleted_tipps} TIPPS to retire from the matched package!")

          log.debug("Elapsed tipp processing time: ${System.currentTimeMillis()-tipp_upsert_start_time} for ${tippctr} records")
        }
        else {
          log.warn("Not loading tipps - failed validation")
        }
      }

    }
    render result as JSON
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def crossReferencePlatform() {
    def result = [ 'result' : 'OK' ]
    User user = springSecurityService.currentUser
    if ( ( request.JSON.platformUrl ) &&
         ( request.JSON.platformUrl.trim().length() > 0 ) &&
         ( request.JSON.platformName ) &&
         ( request.JSON.platformName.trim().length() > 0 ) ) {
      def p = Platform.findByPrimaryUrl(request.JSON.platformUrl)
      if ( p == null ) {

        // Attempt normname lookup.
        p = Platform.findByNormname( Platform.generateNormname (request.JSON.name) ) ?: new Platform(primaryUrl:request.JSON.platformUrl, name:request.JSON.name)
      }

      setAllRefdata ([
        'software', 'service'
      ], request.JSON, p)
      ClassUtils.setRefdataIfPresent(request.JSON.authentication, p, 'authentication', 'Platform.AuthMethod')

      if (request?.JSON?.provider) {
        def prov = Org.findByNormname( Org.generateNormname (request.JSON.provider) )
        if (prov) {
          p.provider = prov
        }
      }

      // Add the core data.
      ensureCoreData(p, request.JSON)

//      if ( changed ) {
//        p.save(flush:true, failOnError:true);
//      }

      result.platform_id = p.id;
    }
    render result as JSON
  }

  private static boolean setAllRefdata (Collection<String> propNames, data, target) {
    boolean changed = false
    propNames.each { String prop ->
      changed |= ClassUtils.setRefdataIfPresent(data[prop], target, prop)
    }
    changed
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def crossReferenceLicense() {
    def result = [ 'result' : 'OK' ]

    // Add the license.
    def data = request.JSON
    if (data && data.name) {
      // Use the name to either match or create a Licence.
      License l = License.findOrCreateByName( License.generateNormname (data.name) ) ?: new License (name: data.name)

      // Update the properties on the license.
      l.with {
        url = data.url
        file = data.file
        summaryStatement = data.summaryStatement
      }

      setAllRefdata ([
        'type'
      ], data, l)


      // Add the core data.
      ensureCoreData(l, data)

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
   *  {
   *    'title':'the_title',
   *    'publisher':'the_publisher',
   *    'identifiers':[
   *      {type:'namespace',value:'value'},
   *      {type:'isbn', value:'1234-5678'}
   *    ]
   *    'type':'Serial'|'Monograph',
   *    'variantNames':[
   *      'Array Of Strings - one for each variant name'
   *    ],
   *    'imprint':'the_publisher',
   *    'publishedFrom':'yyyy-MM-dd' 'HH:mm:ss.SSS',
   *    'publishedTo':'yyyy-MM-dd' 'HH:mm:ss.SSS',
   *    'editStatus':'the_publisher',
   *    'status':'the_publisher',
   *    'historyEvents':[
   *    ]
   *  }
   */
  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def crossReferenceTitle() {
    def result
    def json = request.JSON

    if(org.codehaus.groovy.grails.web.json.JSONArray != json.getClass()){

      result = crossReferenceSingleTitle(json)
    }
    else {
      result = []

      json.eachWithIndex{ e, i ->
        result <<  crossReferenceSingleTitle(e)
      }
    }
    render result as JSON
  }

  private crossReferenceSingleTitle(Object titleObj) {

    def result = [ 'result' : 'OK' ]

    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");

    log.debug("crossReferenceTitle(${titleObj.type},${titleObj.title},${titleObj.identifiers}},...)");

    try {
      User user = springSecurityService.currentUser
      def title = titleLookupService.find(
        titleObj.name,
        titleObj.publisher,
        titleObj.identifiers,
        user,
        null,
        titleObj.type=='Serial' ? 'org.gokb.cred.JournalInstance' : 'org.gokb.cred.BookInstance'
      );  // project

      if ( title ) {

//        if ( titleObj.variantNames?.size() > 0 ) {
//          titleObj.variantNames.each { vn ->
//            log.debug("Ensure variant name ${vn}");
//            title.addVariantTitle(vn);
//          }
//        }

        def title_changed = false;

        if ( titleObj.imprint ) {
          if ( title.imprint?.name == titleObj.imprint ) {
            // Imprint already set
          }
          else {
            def imprint = Imprint.findByName(titleObj.imprint) ?: new Imprint(name:titleObj.imprint).save(flush:true, failOnError:true);
            title.imprint = imprint;
            title_changed = true
          }
        }

        title_changed |= setAllRefdata ([
              'software', 'service',
              'OAStatus', 'medium',
              'pureOA', 'continuingSeries',
              'reasonRetired'
        ], titleObj, title)

        if (titleObj.type == 'Serial') {
          title_changed |= ClassUtils.setDateIfPresent(titleObj.publishedFrom, title, 'publishedFrom', sdf)
          title_changed |= ClassUtils.setDateIfPresent(titleObj.publishedTo, title, 'publishedTo', sdf)
        }

        // Add the core data.
        ensureCoreData(title, titleObj)

//        title.save(flush:true, failOnError:true)

        if ( titleObj.historyEvents?.size() > 0 ) {

          titleObj.historyEvents.each { jhe ->
                // 1971-01-01 00:00:00.0
            log.debug("Handling title history");
            try {
              def inlist = []
              def outlist = []
              def cont = true

              jhe.from.each { fhe ->

                def p = titleLookupService.find(
                  fhe.title,
                  null,
                  fhe.identifiers,
                  user,
                  null,
                  titleObj.type=='Serial' ? 'org.gokb.cred.JournalInstance' : 'org.gokb.cred.BookInstance'
                );

                if ( p ) { inlist.add(p); } else { cont = false; }
              }

              jhe.to.each { fhe ->

                def p =  titleLookupService.find(
                  fhe.title,
                  null,
                  fhe.identifiers,
                  user,
                  null,
                  titleObj.type=='Serial' ? 'org.gokb.cred.JournalInstance' : 'org.gokb.cred.BookInstance'
                );

                if ( p && !inlist.contains(p) ) { outlist.add(p); } else { cont = false; }
              }

              def first = true;
              // See if we can locate an existing ComponentHistoryEvent involving all the titles specified in this event
              def che_check_qry_sw  = new StringWriter();
              def qparams = []

              che_check_qry_sw.write('select che from ComponentHistoryEvent as che where ')

              inlist.each { fhe ->
                if ( first ) { first = false; } else { che_check_qry_sw.write(' AND ') }

                che_check_qry_sw.write(' exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = ?) ')
                qparams.add(fhe)
              }

              outlist.each { fhe ->
                if ( first ) { first = false; } else { che_check_qry_sw.write(' AND ') }

                che_check_qry_sw.write(' exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = ?) ')
                qparams.add(fhe)
              }

              def che_check_qry = che_check_qry_sw.toString()

              log.debug("Search for existing history event:: ${che_check_qry} ${qparams}");

              def qr = ComponentHistoryEvent.executeQuery(che_check_qry, qparams);

              if ( qr.size() > 0 || inlist.size() == 0 || outlist.size() == 0 )
                cont = false;

              if ( cont ) {

                def he = new ComponentHistoryEvent()

                if ( jhe.date ) {
                  he.eventDate = sdf.parse(jhe.date);
                }

                he.save(flush:true, failOnError:true);

                inlist.each {
                  def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'in');
                  hep.save(flush:true, failOnError:true);
                }

                outlist.each {
                  def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'out');
                  hep.save(flush:true, failOnError:true);
                }
              }
              else {
                // Matched an existing TH event, not creating a duplicate
              }
            }
            catch ( Exception e ) {
                  log.error("Problem processing title history",e);
            }
          }
        }
        if( title.class.name == "org.gokb.cred.BookInstance" && (titleObj.type == 'Book' || titleObj.type == 'Monograph') ){
          
          log.debug("Adding Monograph fields for ${title.class.name}: ${title}")
          def mg_change = addMonographFields(title, titleObj, sdf)

          // TODO: Here we will have to add authors and editors, like addPerson() in TSVIngestionService
          if(mg_change){
            title_changed = true
          }
        }

        addPublisherHistory(title, titleObj.publisher_history, sdf)

        result.message = "Created/looked up title ${title.id}"
        result.cls = title.class.name
        result.titleId = title.id
      }
      else {
        result.message = "No title for ${titleObj}";
        log.error("Cross Reference Title failed :${titleObj}");
        // applicationEventService.publishApplicationEvent('CriticalSystemMessages', 'ERROR', [description:"Cross Reference Title failed :${titleObj}"])
        event ( topic:'IntegrationDataError', data:[description:"Cross Reference Title failed :${titleObj}"], params:[:]) {
              // Event callback closure
        }
      }
    }
    catch ( Exception e ) {
      log.error("Exception attempting to cross reference title",e);
      result.result="ERROR"
      result.message=e.toString()
      result.baddata=titleObj
      log.error("Source message causing error (ADD_TO_TEST_CASES): ${titleObj}");
    }
    finally {
      log.debug("Result of cross ref title: ${result}");
    }

    result
  }

  private addPublisherHistory ( TitleInstance ti, publishers, sdf) {

    if (publishers) {

      def publisher_combos = []
      publisher_combos.addAll( ti.getCombosByPropertyName('publisher') )
      String propName = ti.isComboReverse('publisher') ? 'fromComponent' : 'toComponent'
      String tiPropName = ti.isComboReverse('publisher') ? 'toComponent' : 'fromComponent'

      // Go through each Org.
      for (def pub_to_add : publishers) {

        // Lookup the publisher.
        def norm_pub_name = KBComponent.generateNormname(pub_to_add.name)
        Org publisher = Org.findByNormname(norm_pub_name)


        if(!publisher || publisher.status.value == 'Deleted'){
          def variant_normname = GOKbTextUtils.normaliseString(pub_to_add.name)
          def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status.value <> 'Deleted'",[variant_normname]);

          if(candidate_orgs.size() == 1){
            publisher = candidate_orgs[0]
          }
        }

        if (publisher) {

          Date pub_add_ed = pub_to_add?.endDate && "${pub_to_add.endDate}" != "" ? sdf.parse(pub_to_add.endDate) : null

          boolean found = false
          for ( int i=0; !found && i<publisher_combos.size(); i++) {
            Combo pc = publisher_combos[i]
            found = pc."${propName}".id == publisher.id
            found = found && pc.endDate == pub_add_ed
          }

          // Only add if we havn't found anything.
          if (!found) {
            RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, ti.getComboTypeValue('publisher'))
            Combo combo = new Combo(
              type            : (type),
              status          : pub_to_add.status ? RefdataCategory.lookupOrCreate(Combo.RD_STATUS,pub_to_add.status) : DomainClassExtender.getComboStatusActive(),
              startDate       : pub_to_add?.startDate && "${pub_to_add.startDate}" != "" ? sdf.parse(pub_to_add.startDate) : null,
              endDate         : pub_add_ed,
              "${propName}"   : publisher,
              "${tiPropName}" : ti
            )

            // Depending on where the combo is defined we need to add a combo.
//            if (ti.isComboReverse('publisher')) {
//              ti.addToIncomingCombos(combo)
//            } else {
//              ti.addToOutgoingCombos(combo)
//            }
//            publisher.save()

            combo.save(flush:true, failOnError:true)

            // Add the combo to our list to avoid adding duplicates.
            publisher_combos.add ( combo )

            log.debug "Added publisher ${publisher.name} for '${ti.name}'" +
              (combo.startDate ? ' from ' + combo.startDate : '') +
              (combo.endDate ? ' to ' + combo.endDate : '')

          } else {
            log.debug "Publisher ${publisher.name} already set against '${ti.name}'"
          }

        } else {
          log.debug "Could not find org name: ${pub_to_add.name}, with normname: ${norm_pub_name}"
        }
      }
    }
  }

  private addMonographFields ( BookInstance bi, titleObj, sdf ) {

    def book_changed = false

    def bookStringAttrs = ["editionNumber","editionDifferentiator",
                            "editionStatement","volumeNumber",
                            "summaryOfContent"]

    bookStringAttrs.each {
      if(titleObj[it] && titleObj[it].toString().trim().length() > 0){
        bi[it] = titleObj[it].toString().trim()
        book_changed = true
      }
    }

    book_changed |= ClassUtils.setDateIfPresent(titleObj.dateFirstInPrint, bi, 'dateFirstInPrint', sdf)
    book_changed |= ClassUtils.setDateIfPresent(titleObj.dateFirstOnline, bi, 'dateFirstOnline', sdf)

    if ( book_changed ) {
      bi.save(flush: true, failOnError:true)
    }

    book_changed
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
    def r = new CSVReader( new InputStreamReader(title_file, java.nio.charset.Charset.forName('UTF-8') ), del, quote )

    def col_positions = [ 'id':0, 'name':1, 'desc':2, 'transformations':3 ]
    String [] nl = r.readNext()

    int rowctr = 0
    def ret = [:]
    while ( nl != null) {
      rowctr ++
      try {
        if (nl.length >= col_positions.size() && cleanData (nl[col_positions.'name']) ) {

          String name = cleanData(nl[col_positions.'name'])
          Macro m = Macro.findByNormname( Macro.generateNormname (name) ) ?: new Macro()

          // Update
          m.name = name
          m.description = cleanData (nl[col_positions.'desc'])
          m.refineTransformations = cleanData (nl[col_positions.'transformations'])

          // Save to DB
          m.save(flush: true, failOnError: true)
          log.info "Created/Updated macro with id ${m.id}"
          ret["Row ${rowctr}"] = "Created Macro with ID ${m.id}"
        } else {
          log.error("Unable to parse row ${rowctr}..")
          ret["Row ${rowctr}"] = "Failed to parse"
        }
      } catch ( Exception e ) {
        log.error("Unable to process row ${rowctr}..",e)
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
    def r = new CSVReader( new InputStreamReader(title_file, java.nio.charset.Charset.forName('UTF-8') ), tab,quote )

    def col_positions = [ 'identifier.pissn':-1, 'identifier.eissn':-1, 'title':-1 ]

    String [] header = r.readNext()
    int ctr = 0
    header.each {
      col_positions [ it.toLowerCase() ] = ctr++
    }

    if ( ( col_positions.'title' != -1 ) &&
         ( ( col_positions.'identifier.pissn' != -1 ) ||
           ( col_positions.'identifier.eissn' != -1 ) ) ) {

      // So long as we have at least one identifier...
      String [] nl = r.readNext()

      int rowctr = 0;

      while ( nl != null ) {
        try {
          KBComponent.withNewTransaction() {

            def candidate_identifiers = []

            if ( ( col_positions.'identifier.pissn' != -1 ) &&
                 ( nl[col_positions.'identifier.pissn']?.length() > 0 ) &&
                 ( nl[col_positions.'identifier.pissn'].toLowerCase() != 'null' ) ) {
              candidate_identifiers.add([type:'issn', value:nl[col_positions.'identifier.pissn']]);
            }

            if ( ( col_positions.'identifier.eissn' != -1 ) &&
                 ( nl[col_positions.'identifier.eissn']?.length() > 0 ) &&
                 ( nl[col_positions.'identifier.eissn'].toLowerCase() != 'null' ) ) {
              candidate_identifiers.add([type:'eissn', value:nl[col_positions.'identifier.eissn']]);
            }

            if ( candidate_identifiers.size() > 0 ) {
              log.debug("Looking up ${candidate_identifiers} - ${nl[col_positions.'title']}");
              def existing_component = titleLookupService.find (nl[col_positions.'title'], null, candidate_identifiers)
            }
            else {
              log.debug("No candidate identifiers: ${nl}");
            }
          }
        }
        catch ( Exception e ) {
          log.error("Unable to process..",e);
        }

        if ( rowctr++ > 100 ) {
          log.debug("CleanUpGorm..");
          rowctr = 0;
          cleanUpGorm()
        }
        nl = r.readNext()
        log.debug("Next row: ${nl}");
      }
    }
    log.debug("Done");
    redirect(action:'index');
  }

  private def cleanUpGorm() {
    log.debug("Clean up GORM");

    // Get the current session.
    def session = sessionFactory.currentSession

    // flush and clear the session.
    session.flush()
    session.clear()

    // Clear the property instance map.
    propertyInstanceMap.get().clear()
  }


}
