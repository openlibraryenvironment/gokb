package org.gokb

import org.gokb.cred.*;

class TitleLookupService {
  
  def grailsApplication  
  def orgLookupService

//  def find(title, issn, eissn) {
//    find(title, issn, eissn, null, null)
//  }
  
  private Map class_one_match (def ids) {
	
	// Get the class 1 identifier namespaces.
	Set<String> class_one_ids = grailsApplication.config.identifiers.class_ones
	
	// Return the list of class 1 identifiers we have found or created, as well as the
	// list of matches
	def result = [
	  "ids"	: [],
	  "matches" : []
	]
	
	// The list of matches.
	Set<KBComponent> matches = []
	
	// Go through each of the class_one_ids and look for a match.
	ids.each { id_def ->
	  
	  // id_def is map with keys 'type' and 'value'
	  Identifier the_id = Identifier.lookupOrCreateCanonicalIdentifier(id_def.type, id_def.value)
	  
	  // Add the id.
	  result['ids'] << the_id
	  
	  // We only treat a component as a match if the matching Identifer
	  // is a class 1 identifier.
	  if (class_one_ids.contains(the_id)) {
	  
    	  // If we find an ID then lookup the components.
    	  Set<KBComponent> comp = the_id.identifiedComponents
    	  comp.each { KBComponent c ->
    		
    		// Only add if it's a title.
    		if (c instanceof TitleInstance) result['matches'] << c
    	  }
	  }
	}
	
	return result
  }
  
  def find (String title, String publisher_name, def identifiers) {
	
	// Lookup any class 1 identifier matches 
	def results = class_one_match (identifiers)
	
	// The matches.
	List<KBComponent> matches = results['matches']
	
	switch (matches.size()) {
	  case 0 :
	  	// No match behaviour.
	  	log.debug ("Title class one identifier lookup yielded no matches. This is a new TI")
		  
		// Create the new TI.
		newTI (title, publisher_name, results['ids'])
	  	break;
	  case 1 :
	    // Single component match.
	  	log.debug ("Title class one identifier lookup yielded a single match.")
	  	break;
	  default :
	  	// Multiple matches.
	  	log.debug ("Title class one identifier lookup yielded ${matches.size()} matches. This is a bad match.")
	  	break;
	} 
  }
  
  private TitleInstance newTI (String title, String publisher_name, List<Identifier> ids) {
	
	// Create the new title
	TitleInstance ti = new TitleInstance(name:title)
	
	// Lookup our publisher.
	Org publisher = orgLookupService.lookupOrg(publisher_name)
	
	// Add the publisher.
	if (publisher) {
	  
	  // This is a new title and should therefore have no publisher set as of yet.
	  ti.publisher = publisher
	}
	
	// Add all the identifiers.
	ti.ids.addAll (ids)
	
	// Try and save the result now.
	if ( ti.save(failOnError:true,flush:true) ) {
	  log.debug("New title: ${ti.id}");
	}
	else {
	  ti.errors.each { e ->
		log.error("Problem saving title: ${e}");
	  }
	}
  }

//  def find(title, issn, eissn, extra_ids, publisher_name) {
//
//    def result = null
//
//    try {
//
//      log.debug("find Title (${title},${issn},${eissn},${publisher_name})");
//
//      // No publisher to start.
//      Org publisher = null
//      
//      // Publisher name
//      if (publisher_name) {
//      
//        // Locate a publisher for the supplied name if possible.
//        publisher = Org.findByNameIlike(publisher_name)
//  
//        // Create new publisher if needed.
//        if (!publisher) {
//          publisher = new Org ([name : publisher_name])
//          log.debug("No publisher found. Created new one with name ${publisher.name}")
//        } else {
//          log.debug("Found publisher with id ${publisher.id}")
//        }
//      }
//
//      // Use the ids to check for a TitleInstance.
//      Identifier issn_identifier = issn ? Identifier.lookupOrCreateCanonicalIdentifier('issn',issn) : null
//      Identifier eissn_identifier = eissn ? Identifier.lookupOrCreateCanonicalIdentifier('eissn',eissn) : null
//
//      def tq = ComboCriteria.createFor(TitleInstance.createCriteria())
//      def titles = tq.listDistinct {
//        outgoingCombos {
//          or {
//            eq ("toComponent", issn_identifier)
//            eq ("toComponent", eissn_identifier)
//          }
//        }
//      }
//
//      if ( titles ) {
//        switch ( titles.size() ) {
//          case 0:
//            log.error("Should not be here, this case should result in the outer else below");
//            break;
//          case 1:
//            log.debug("Exact match");
//            result = titles.get(0);
//            break;
//          default:
//            log.debug("Duplicate matches.. error");
//            throw new Exception("Unable to identify unique title based on the supplied identifiers... Not adding");
//            break;
//        }
//      }
//      else {
//        log.debug("No result, create a new title")
//        result = new TitleInstance(name:title)
//        
//        // Add the publisher here if needed.
//        if (publisher != null) {
//          // result.publisher = publisher
//          // Only add the publisher if not already in the list of publishers for this title
//          if ( result.publisher.contains(publisher) ) {
//            // Already present - Don't do anything
//          }
//          else {
//            result.publisher.add(publisher)
//          }
//        }
//
//        // Don't forget to add our IDs here.
//        if ( issn_identifier ) {
//          result.ids.add (issn_identifier)
//        }
//
//        if ( eissn_identifier ) {
//          result.ids.add (eissn_identifier)
//        }
//
//        extra_ids.each { ei ->
//          def additional_identifier = Identifier.lookupOrCreateCanonicalIdentifier(ei.type,ei.value)
//          result.ids.add (additional_identifier)
//        }
//
//        // Try and save the result now.
//        if ( result.save(failOnError:true,flush:true) ) {
//          log.debug("New title: ${result.id}");
//        }
//        else {
//          result.errors.each { e ->
//            log.error("Problem saving title: ${e}");
//          }
//        }
//      }
//
//      // May double check with porter stemmer in the future.. see
//      // https://svn.apache.org/repos/asf/lucene/dev/tags/lucene_solr_3_3/lucene/src/java/org/apache/lucene/analysis/PorterStemmer.java
//
//    }
//    catch ( Exception e ) {
//      log.error("Problem with title lookup",e);
//    }
//    finally {
//      log.debug("Title lookup completed");
//    }
//    result
//  }
}
