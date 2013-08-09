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
	  "class_one" 	: false,
	  "ids"			: [],
	  "matches"		: [] as Set
	]

	// Go through each of the class_one_ids and look for a match.
	ids.each { id_def ->

	  if (id_def.type && id_def.value) {

		// id_def is map with keys 'type' and 'value'
		Identifier the_id = Identifier.lookupOrCreateCanonicalIdentifier(id_def.type, id_def.value)

		// Add the id.
		result['ids'] << the_id

		// We only treat a component as a match if the matching Identifer
		// is a class 1 identifier.
		if (class_one_ids.contains(id_def.type)) {

		  // Flag class one is present.
		  result['class_one'] = true

		  // If we find an ID then lookup the components.
		  Set<KBComponent> comp = the_id.identifiedComponents
		  comp.each { KBComponent c ->

			// Only add if it's a title.
			if (c.isInstanceOf(TitleInstance) ) result['matches'] << c
		  }
		}
	  }
	}
	
	result
  }

  def find (String title, String publisher_name, def identifiers) {
	
	// The TitleInstance
	TitleInstance the_title = null
	
	// Create the normalised title.
	String norm_title = GOKbTextUtils.normaliseString(title)

	// Lookup any class 1 identifier matches
	def results = class_one_match (identifiers)

	// The matches.
	Set<KBComponent> matches = results['matches']

	switch (matches.size()) {
	  case 0 :
	    // No match behaviour.
		log.debug ("Title class one identifier lookup yielded no matches.")

	    // Check for presence of class one ID
		if (results['class_one']) {
		  log.debug ("One or more class 1 IDs supplied so must be a new TI.")

		  // Create the new TI.
		  the_title = new TitleInstance(name:title)

		} else {

		  // No class 1s supplied we should try and find a match on the title string.
		  log.debug ("No class 1 ids supplied.")
		  
		  // Lookup using title string match only.
		  the_title = attemptStringMatch (norm_title)
		  
		  // This match is classed as a partial.
		  partialMatch (title, the_title)
		}
		break;
	  case 1 :
	    // Single component match.
		log.debug ("Title class one identifier lookup yielded a single match.")

		the_title = singleTIMatch(title, norm_title, matches[0])

		break;
	  default :
	    // Multiple matches.
		log.debug ("Title class one identifier lookup yielded ${matches.size()} matches. This is a bad match. Ingest should skip this row.")
		break;
	}
	
	// If we have a title then lets set the publisher and ids...
	if (the_title) {
	  
	  // Lookup our publisher.
	  Org publisher = orgLookupService.lookupOrg(publisher_name)
  
	  // Add the publisher.
	  if (publisher) {
  
		// This is a new title and should therefore have no publisher set as of yet.
		the_title.publisher = publisher
	  }
  
	  // Add all the identifiers.
	  if (results['ids']) the_title.ids.addAll (results['ids'])
  
	  // Try and save the result now.
	  if ( the_title.save(failOnError:true,flush:true) ) {
		log.debug("New title: ${the_title.id}");
	  }
	  else {
		the_title.errors.each { e ->
		  log.error("Problem saving title: ${e}");
		}
	  }
	}
	
	the_title
  }
  
  private TitleInstance addPublisher (String publisher_name, TitleInstance ti) {
	
	// Lookup our publisher.
	Org publisher = orgLookupService.lookupOrg(publisher_name)

	// Add the publisher.
	if (!ti.getPublisher().contains(publisher)) {

	  // Add the new publisher.
	  the_title.publisher.add (publisher)
	  
	  // TODO: Add a review task.
	}
  }
  
  private TitleInstance attemptStringMatch (String norm_title) {
	
	// Default to return null.
	TitleInstance ti = null
	
	// Try and find a title by matching the norm string.
	// Default to the min threshold
	double best_distance = grailsApplication.config.cosine.good_threshold
	
	TitleInstance.list().each { TitleInstance t ->
	  
	  // Get the distance and then determine whether to add to the list or 
	  double distance = GOKbTextUtils.cosineSimilarity(norm_title, t.normname)
	  if (distance >= best_distance) {
		ti = t
	  }
	}
	
	// Return what we have found... If anything.
	ti
  }

  private TitleInstance singleTIMatch(String title, String norm_title, TitleInstance ti) {

	// The threshold for a good match.
	double threshold = grailsApplication.config.cosine.good_threshold

	// Work out the distance between the 2 title strings.
	double distance = GOKbTextUtils.cosineSimilarity(ti.normname, norm_title)

	// Check the distance.
	switch (distance) {
	  
	  case 1 :
	  
	    // Do nothing just continue using the TI.
	  	log.debug("Exact distance match for TI.")
	  	break
		  
	  case {it >= threshold} :
	  
	    // Good match. Need to add as alternate name.
	  	log.debug("Good distance match for TI. Add as variant.")
		ti.addVariantTitle(title)
		break
		
	  default :
	  
	    // Bad match...
		partialMatch(title, ti)
		break
	}
	
	ti
  }
  
  private TitleInstance partialMatch (String title, TitleInstance ti) {
	ti.addVariantTitle(title)
	
	// TODO: Need to create a task to review here....
  }
  
  private void doNotIngest (String title) {
	// TODO: Add as not ingested...
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
