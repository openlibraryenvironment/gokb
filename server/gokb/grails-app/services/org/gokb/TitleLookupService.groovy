package org.gokb

import org.gokb.cred.*;

class TitleLookupService {

    def find(title, issn, eissn) {
      find(title, issn, eissn, null, null)
    }

    def find(title, issn, eissn, extra_ids, publisher_name) {

      def result = null

      log.debug("find(${title},${issn},${eissn})");
      def issn_identifier = issn ? Identifier.lookupOrCreateCanonicalIdentifier('issn',issn) : null
      def eissn_identifier = eissn ? Identifier.lookupOrCreateCanonicalIdentifier('eissn',eissn) : null
      def publisher = Org.findOrSaveByName (publisher_name)

      def tq = ComboCriteria.createFor( TitleInstance.createCriteria() )
      def titles = tq.listDistinct {
		and {
    		tq.add('ids.identifier', 'in', [[issn_identifier,eissn_identifier]])
    		tq.add('publisher', 'eq', publisher)
		}
		
//        ids {
//          or {
//            'in'('identifier',[issn_identifier,eissn_identifier])
//            // eq('identifier',issn_identifier)
//            // eq('identifier',eissn_identifier)
//          }
//		  and {
//			add 
//		  }
//        }
      }

      if ( titles ) {
        switch ( titles.size() ) {
          case 0:
            log.error("Should not be here, this case should result in the outer else below");
            break;
          case 1:
            log.debug("Exact match");
            result = titles.get(0);
            break;
          default:
            log.debug("Duplicate matches.. error");
            throw new Exception("Unable to identify unique title based on the supplied identifiers... Not adding");
            break;
        }
      }
      else {
        log.debug("No result, create a new title")
        result = new TitleInstance(name:title).save()

        if ( ! result.ids )
          result.ids = []

        if ( issn_identifier )
//          new IdentifierOccurrence(identifier:issn_identifier, component:result).save(flush:true);
		
    		// Add a custom ID.	
			result.ids.add(
    		  new IdentifierOccurrence(identifier:issn_identifier).save()
    		)

        if ( eissn_identifier )
//          new IdentifierOccurrence(identifier:eissn_identifier, component:result).save(flush:true);
    		// Add a custom ID.
    		result.ids.add(
    		  new IdentifierOccurrence(identifier:eissn_identifier).save()
    		)

        extra_ids.each { ei ->
          def additional_identifier = Identifier.lookupOrCreateCanonicalIdentifier(ei.type,ei.value)
//          new IdentifierOccurrence(identifier:additional_identifier, component:result).save(flush:true);
		  result.ids.add(
			new IdentifierOccurrence(identifier:additional_identifier).save()
		  )
        }
		
		// Try and save the result now.
		if ( result.save() ) {
		  log.debug("New title: ${result.id}");
		}
		else {
		  result.errors.each { e ->
			log.error("Problem saving title: ${e}");
		  }
		}
      }

      // May double check with porter stemmer in the future.. see
      // https://svn.apache.org/repos/asf/lucene/dev/tags/lucene_solr_3_3/lucene/src/java/org/apache/lucene/analysis/PorterStemmer.java

      result
    }
}
