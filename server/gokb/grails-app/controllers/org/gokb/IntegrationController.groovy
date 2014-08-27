package org.gokb

import grails.converters.JSON
import org.gokb.cred.*
import grails.plugins.springsecurity.Secured
import org.gokb.cred.*
import org.gokb.GOKbTextUtils

class IntegrationController {

  def grailsApplication
  def springSecurityService
  def titleLookupService

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def index() {
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
            def normname = GOKbTextUtils.normaliseString(name)
            located_entries = KBComponent.findAllByNormname(normname)
            if ( located_entries?.size() == 0 ) {
              log.debug("No match on normalised name ${normname}.. Trying variant names");
              createJsonLDOrg(request.JSON);
            }
            else if ( located_entries?.size() == 1 ) {
               log.debug("Exact match on normalised name ${normname} - good enough");
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
      def identifier = Identifier.lookupOrCreateCanonicalIdentifier('global',said)
      new_org.ids.add(identifier)
    }

    request.JSON.'skos:altLabel'?.each { al ->
      println("checking alt label ${al}");
    }

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

  /**
   *  assertOrg()
   *  allow an authorized external component to send in a JSON structure following this template:
   *      [
   *         name:National Association of Corrosion Engineers, 
   *         description:National Association of Corrosion Engineers,
   *         parent:
   *         customIdentifers:[[identifierType:"idtype", identifierValue:"value"]], 
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

    try {
      def located_or_new_org = resolveOrgUsingPrivateIdentifiers(request.JSON.customIdentifers);

      if ( located_or_new_org == null ) {
        log.debug("Create new org with identifiers ${request.JSON.customIdentifers} name will be \"${request.JSON.name}\" (${request.JSON.name.length()})");
   
        located_or_new_org = new Org(name:request.JSON.name)

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
        
        // Add parent.
        if (request.JSON.parent) {
          def parentDef = request.JSON.parent;
          log.debug("Adding parent using ${parentDef.identifierType}:${parentDef.identifierValue}");
          def located_component = KBComponent.lookupByIO(parentDef.identifierType,parentDef.identifierValue)
          if (located_component) {
            located_or_new_org.parent = located_component
          }
        }
  
        def identifier_combo_type = RefdataCategory.lookupOrCreate('Combo.Type','Org.Ids');
        // Identifiers
        log.debug("Identifier processing ${request.JSON.customIdentifers}");
        request.JSON.customIdentifers.each { ci ->
          def canonical_identifier = Identifier.lookupOrCreateCanonicalIdentifier(ci.identifierType,ci.identifierValue)
          log.debug("adding identifier(${ci.identifierType},${ci.identifierValue})(${canonical_identifier.id})");
      located_or_new_org.ids.add(canonical_identifier)
        }
    
        // roles
        log.debug("Role Processing: ${request.JSON.flags}");
        request.JSON.roles.each { r ->
          log.debug("Adding role ${r}");
          def role = RefdataCategory.lookupOrCreate("Org.Role", r)
          located_or_new_org.addToRoles(
      role
          )
        }

        // flags
        log.debug("Flag Processing: ${request.JSON.flags}");
        request.JSON.flags.each { f ->
          log.debug("Adding flag ${f.flagType},${f.flagValue}");
          def flag = RefdataCategory.lookupOrCreate(f.flagType,f.flagValue).save()
          located_or_new_org.addToTags(
            flag
          )
        }

        log.debug("Combo processing: ${request.JSON.combos}");

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
        
        log.debug("Attempt to save - validate: ${located_or_new_org}");
        
        if ( located_or_new_org.save(failOnError : true) ) {
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

        result.msg="Created new org: ${located_or_new_org.id} ${located_or_new_org.name}";
      }
      else {
        log.debug("Located existing record..");
        result.msg="Located existing record: ${located_or_new_org.id} ${located_or_new_org.name}";
      }
    }
    catch ( Exception e ) {
      log.error("Unexpected error importing org",e)
      result.msg="ERROR: ${e}";
      result.status=false
    }
    render result as JSON
  }


  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  private resolveOrgUsingPrivateIdentifiers(idlist) {
    def located_or_new_org = null;

    // See if we can locate the item using any of the custom identifiers
    idlist.each { ci ->
      log.debug("Attempt lookup of ${ci.identifierType}:${ci.identifierValue}");
      if ( located_or_new_org ) {
        // We've already located an org for this identifier, the new identifier should be new (And therefore added to this org) or
        // resolve to this org. If it resolves to some other org, then there is a conflict and we fail!
        def located_component = KBComponent.lookupByIO(ci.identifierType,ci.identifierValue)
        if ( located_component ) {
          log.debug("Matched something...");
          if ( !located_or_new_org ) {
            located_or_new_org = located_component
          }
          else {
            if ( located_component.id == located_or_new_org.id ) {
              log.debug("Matched an identifier");
            }
            else {
              log.error("**CONFLICT**");
            }
          }
        }
        else {
          // No match.. candidate identifier
          log.debug("No match for ${ci.identifierType}:${ci.identifierValue}");
        }
      }
      else {
        located_or_new_org = KBComponent.lookupByIO(ci.identifierType,ci.identifierValue)
      }
    }
    located_or_new_org
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def registerVariantName() {
    def result=[:]
    log.debug("registerVariantName ${params} ${request.JSON}");

    // See if we can locate the variant name as a first class component
    
    def variant_org = null;
    if ( request.JSON.variantidns != null && request.JSON.variantidvalue != null ) {
      variant_org = KBComponent.lookupByIO(request.JSON.variantidns,request.JSON.variantidvalue)
      log.debug("Existing variant org[${request.JSON.variantidns}:${request.JSON.variantidvalue}]: ${variant_org}");
    }

    def org_to_update = KBComponent.lookupByIO(request.JSON.idns,request.JSON.idvalue)
    log.debug("Org to update[${request.JSON.idns}:${request.JSON.idvalue}]: ${org_to_update}");

    // Double check that the variant name is not already the primary name, or in the list of variants, if not, add it.
    if ( ( org_to_update ) && ( request.JSON.name?.length() > 0 ) ) {
      boolean found = false
      org_to_update.variantNames.each { vn ->
        if ( vn.variantName == request.JSON.name ) {
          found = true
        }
      }

      if ( !found ) {
        def new_variant_name = new KBComponentVariantName(variantName:request.JSON.name, owner:org_to_update)
        new_variant_name.save();
      }
    }

    // Update any combos that point to the variant so that they now point to the authorized entry

    // Delete any remaining variant org combox
    // Delete the variant org

    render result as JSON
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def crossReferenceTitle() {
    def result = [ 'result' : 'OK' ]

    log.debug("crossReferenceTitle()");

    User user = springSecurityService.currentUser
    def title = titleLookupService.find(request.JSON.title, request.JSON.publisher, request.JSON.identifiers, user)

    if ( title ) {
      log.debug("Looked up title...${title}");
      request.JSON.identifiers.each { id ->
        log.debug("Identifier entry: ${id}");
        def existing_id = title.identifiers.find { it -> it.namespace.value==id.type && it.value==id.value}
        if ( existing_id ) {
          log.debug("Found identifier for ${id.type} : ${id.value}");
        }
        else {
          log.debug("No identifier for ${id.type} : ${id.value}");
          def canonical_identifier = Identifier.lookupOrCreateCanonicalIdentifier(id.type,id.value);
          title.addToIds(canonical_identifier);
          title.save(flush:true);
        }
      }
      log.debug("Done iterating through identifiers");
    }
    else {
      log.debug("Unable to locate title");
    }

    

    render result as JSON
  }
}
