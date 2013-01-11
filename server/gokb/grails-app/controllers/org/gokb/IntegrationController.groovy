package org.gokb

import grails.converters.JSON
import org.gokb.cred.*

class IntegrationController {

  /**
   *  assertOrg()
   *  allow an authorized external componet to send in a JSON structure following this template:
   *      [
   *         name:National Association of Corrosion Engineers, 
   *         description:National Association of Corrosion Engineers, 
   *         customIdentifers:[[identifierType:"idtype", identifierValue:"value"]], 
   *         combos:[[linkTo:[identifierType:"ncsu-internal", identifierValue:"ncsu:61929"], linkType:"HasParent"]], 
   *         flags:[[flagType:"Org Role", flagValue:"Content Provider"], 
   *                [flagType:"Org Role", flagValue:"Publisher"], 
   *                [flagType:"Authorized", flagValue:"N"]]
   *      ]
   *
   */
  def assertOrg() { 
    log.debug("assertOrg, request.json = ${request.JSON}");
    def result=[:]
    result.status = true;

    def located_or_new_org = resolveOrgUsingPrivateIdentifiers(request.JSON.customIdentifers);

    if ( located_or_new_org == null ) {
      log.debug("Create new org with identifiers ${request.JSON.customIdentifers} name will be \"${request.JSON.name}\" (${request.JSON.name.length()})");
      located_or_new_org = new Org(name:request.JSON.name)
      if ( located_or_new_org.save(flush:true) ) {
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

      // Identifiers
      request.JSON.customIdentifers.each { ci ->
        def canonical_identifier = Identifier.lookupOrCreateCanonicalIdentifier(ci.identifierType,ci.identifierValue)
        def id_occur = new IdentifierOccurrence(identifier:canonical_identifier, component:located_or_new_org).save(flush:true);
      }

      // flags
      request.JSON.flags.each { f ->
        def flag = RefdataCategory.lookupOrCreate(f.flagType,f.flagValue);
        located_or_new_org.tags.add(flag);
      }
      located_or_new_org.save(flush:true);

      // combos
      request.JSON.combos.each { c ->
        log.debug("lookup to item using ${c.linkTo.identifierType}:${c.linkTo.identifierValue}");
        def located_component = KBComponent.lookupByIdentifier(c.linkTo.identifierType,c.linkTo.identifierValue)
        // def reloaded_from = KBComponent.get(located_or_new_org.id)
        def reloaded_from = located_or_new_org.refresh();
        if ( ( located_component != null ) && ( reloaded_from != null ) ) {
          def combo_type = RefdataCategory.lookupOrCreate('ComboType',c.linkType);
          def combo = new Combo(from:reloaded_from,to:located_component,type:combo_type).save(flush:true);
        }
        else {
          log.error("Problem resolving from(${reloaded_from}) or to(${located_component}) org for combo");
        }
      }
    }

    render result as JSON
  }


  private resolveOrgUsingPrivateIdentifiers(idlist) {
    def located_or_new_org = null;

    // See if we can locate the item using any of the custom identifiers
    idlist.each { ci ->
      log.debug("Attempt lookup of ${ci.identifierType}:${ci.identifierValue}");
      if ( located_or_new_org ) {
        // We've already located an org for this identifier, the new identifier should be new (And therefore added to this org) or
        // resolve to this org. If it resolves to some other org, then there is a conflict and we fail!
        def located_component = KBComponent.lookupByIdentifier(ci.identifierType,ci.identifierValue)
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
        }
      }
      else {
      }
    }
    log.debug("Result of lookup for ${idlist} : ${located_or_new_org}");
    located_or_new_org
  }
}
