package org.gokb

import org.gokb.refine.*;
import org.gokb.cred.*;


class TitleAugmentService {

  def grailsApplication
  def edinaPublicationsAPIService

    def augment(titleInstance) {
      // If the title does have a suncat-id
      if ( titleInstance.getIdentifierValue('SUNCAT' ) == null ) {
        def lookupResult = edinaPublicationsAPIService.lookup(titleInstance.title)
        if ( lookupResult ) {
          def record = lookupResult.records.record
          if ( record ) {
            boolean matched = false;
            def suncat_identifier = null;
            record.modsCollection.mods.identifier.each { id ->
              if ( id.text().equalsIgnoreCase(titleInstance.getIdentifierValue('ISSN')) || id.text().equalsIgnoreCase(titleInstance.getIdentifierValue('eISSN'))  ) {
                matched = true
              }

              if ( id.@type == 'suncat' ) {
                suncat_identifier = id.text();
              }
            }
            if ( matched && suncat_identifier ) {
              log.debug("set suncat identifier to ${suncat_identifier}");
              def canonical_identifier = Identifier.lookupOrCreateCanonicalIdentifier('SUNCAT',suncat_identifier);
              titleInstance.ids.add(new IdentifierOccurrence(identifier:canonical_identifier, ti:ti));
              titleInstance.save(flush:true);
            }
            else {
              log.debug("No match for title ${titleInstance.title}, ${titleInstance.id}");
            }
          }
          else {
          }
        }
        else {
        }
      }
    }

}
