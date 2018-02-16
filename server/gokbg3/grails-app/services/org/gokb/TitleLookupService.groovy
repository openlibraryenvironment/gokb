package org.gokb

import org.gokb.cred.*

import com.k_int.ClassUtils

class TitleLookupService {

  def grailsApplication
  def componentLookupService
  def genericOIDService
  

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Init");
  }

  private Map class_one_match (def ids) {

    // Get the class 1 identifier namespaces.
    Set<String> class_one_ids = grailsApplication.config.identifiers.class_ones
    def xcheck = grailsApplication.config.identifiers.cross_checks

    // Return the list of class 1 identifiers we have found or created, as well as the
    // list of matches
    def result = [
      "class_one"         : false,
      "ids"               : [],
      "matches"           : [] as Set,
      "x_check_matches"   : [] as Set,
      "other_identifiers" : [] as Set
    ]

    // Go through each of the class_one_ids and look for a match.
    ids.each { id_def ->

      // We only treat a component as a match if the matching Identifer
      // is a class 1 identifier.
      if (id_def.type && id_def.value && class_one_ids.contains(id_def.type) ) {

        log.debug("Attempt match using component ${id_def}");

        // id_def is map with keys 'type' and 'value'
        Identifier the_id = Identifier.lookupOrCreateCanonicalIdentifier(id_def.type, id_def.value)

        // Add the id.
        result['ids'] << the_id

        // Flag class one is present.
        result['class_one'] = true

        // Flag for title match
        boolean title_match = false
        
        // If we find an ID then lookup the components.
        Set<KBComponent> comp = getComponentsForIdentifier(the_id)

        log.debug("Scanning ${comp.size()} components attached to identifier");
        comp.each { KBComponent c ->

          // Ensure we're not looking at a Hibernate Proxy class representation of the class
          KBComponent dproxied = ClassUtils.deproxy(c);

          // Only add if it's a title.
          if ( dproxied instanceof TitleInstance ) {
            title_match = true
            TitleInstance the_ti = (dproxied as TitleInstance)
            // Don't add repeated matches
            if ( result['matches'].contains(the_ti) ) {
              log.debug("Not adding duplicate");
            }
            else {
              log.debug("Adding ${the_ti} (title_match = ${title_match})");
              result['matches'] << the_ti
            }
          }
          else {
            log.debug("ID Doesn't point at a title, skipping");
          }
        }
        
        // Did the ID yield a Title match?
        log.debug("After class one matches (${id_def.type}:${id_def.value}, ${the_id.id}, title_match=${title_match}");

        if (!title_match) {
          
          // We should see if the current ID namespace should be cross checked with another.
          def other_ns = null
          for (int i=0; i<xcheck.size() && (!(other_ns)); i++) {
            Set<String> test = xcheck[i]
            
            if (test.contains(id_def.type)) {
              
              // Create the set then remove the matched instance to test teh remaining ones.
              other_ns = new HashSet<String>(test)
              
              // Remove the current namespace.
              other_ns.remove(id_def.type)
              log.debug ("Cross checking for ${id_def.type} in ${other_ns.join(", ")}")
              
              Identifier xc_id = null
              for (int j=0; j<other_ns.size() && !(xc_id); j++) {
                
                String ns = other_ns[j]
                
                IdentifierNamespace namespace = IdentifierNamespace.findByValue(ns)
                
                if (namespace) {
                  // Lookup the identifier namespace.
                  xc_id = Identifier.findByNamespaceAndValue(namespace, id_def.value)                  
                  log.debug ("Looking up ${ns}:${id_def.value} returned Identifier ${xc_id}");
                  
                  comp = xc_id?.identifiedComponents
                  
                  comp?.each { KBComponent c ->
        
                    // Ensure we're not looking at a Hibernate Proxy class representation of the class
                    KBComponent dproxied = ClassUtils.deproxy(c);
        
                    // Only add if it's a title.
                    if ( dproxied instanceof TitleInstance ) {
                      
                      log.debug ("Found ${id_def.value} in ${ns} namespace.")
                      
                      // Save details here so we can raise a review request, only if a single title was matched.
                      result['x_check_matches'] << [
                        "suppliedNS"  : id_def.type,
                        "foundNS"     : ns
                      ]

                      TitleInstance the_ti = (dproxied as TitleInstance)

                      // Don't add repeated matches
                      if ( result['matches'].contains(the_ti) ) {
                        log.debug("Title already in list of matched instances");
                      }
                      else {
                        result['matches'] << the_ti
                        log.debug("Adding cross check title to matches (Now ${result['matches'].size()} items)");
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      else {
        log.warn("Skipping problem ID ${id_def}");
        Identifier the_id = Identifier.lookupOrCreateCanonicalIdentifier(id_def.type, id_def.value)
        result['other_identifiers'] << the_id
      }
    }

    log.debug("At end of class_one_match, result['matches'].size == ${result['matches'].size()}");

    result
  }


  /**
   * @param title
   * @param publisher_name
   * @param identifiers : map [ [ type: 'idtype', value:'idvalue' ], [ type:'idtype', value:'idvalue' ] ]
   */
  def find (String title, 
            String publisher_name, 
            def identifiers, 
            def user = null, 
            def project = null,
            def newTitleClassName = 'org.gokb.cred.JournalInstance' ) {
    return find([title:title, publisher_name:publisher_name,identifiers:identifiers],user,project,newTitleClassName)
  }

  def find (Map metadata,
            def user = null, 
            def project = null,
            def newTitleClassName = 'org.gokb.cred.JournalInstance' ) {

    // The TitleInstance
    TitleInstance the_title = null

    if (metadata.title == null) {
      log.error("Request to look up title with no title");
      return null
    }

    // Lookup any class 1 identifier matches
    def results = class_one_match (metadata.identifiers)

    // The matches.
    List< KBComponent> matches = results['matches'] as List

    switch (matches.size()) {
      case 0 :
        // No match behaviour.
        log.debug ("Title class one identifier lookup yielded no matches.")

     
        // Check for presence of class one ID
        if (results['class_one']) {
          log.debug ("One or more class 1 IDs supplied so must be a new TI.")

          // Create the new TI.
          if ( newTitleClassName == null ) {
            the_title = new TitleInstance(name:metadata.title, normname:KBComponent.generateNormname(metadata.title),ids:[])
            the_title.normname = KBComponent.generateNormname(metadata.title);
          }
          else {
            def clazz = Class.forName(newTitleClassName)
            the_title = clazz.newInstance()
            the_title.name = metadata.title
            the_title.normname = KBComponent.generateNormname(metadata.title);
            // the_title.editStatus = 
            the_title.ids = []
          }

        } else {

          // No class 1s supplied we should try and find a match on the title string.
          log.debug ("No class 1 ids supplied. attempting string match")

          // The hash we use is constructed differently based on the type of items.
          // Serial hashes are based soley on the title, Monographs are based currently on title+primary author surname
          def target_hash = null;

          // Lookup using title string match only.
          the_title = attemptBucketMatch (metadata.title)

          if (the_title) {
            log.debug("TI ${the_title} matched by bucket.")

            if ( metadata.title != the_title.name ) {
              log.debug("bucket match but \"${metadata.title}\" != \"${the_title.name}\" so add as a variant");

              // Add the variant.
              the_title.addVariantTitle(metadata.title)

              // Raise a review request
              ReviewRequest.raise(
                  the_title,
                  "'${metadata.title}' added as a variant of '${the_title.name}'.",
                  "No 1st class ID supplied but reasonable match was made on the title name.",
                  user, project
                  )

              the_title.save(flush:true, failOnError:true);
            }

          } else {

            log.debug("No TI could be matched by name. New TI, flag for review.")

            // Could not match on title either.
            // Create a new TI but attach a Review request to it.

            if ( newTitleClassName == null ) {
              the_title = new TitleInstance(name:metadata.title, normname:KBComponent.generateNormname(metadata.title), ids:[])
            }
            else {
              def clazz = Class.forName(newTitleClassName)
              the_title = clazz.newInstance()
              the_title.name = metadata.title
              the_title.normname = KBComponent.generateNormname(metadata.title)
              the_title.ids = []
            }

            ReviewRequest.raise(
                the_title,
                "New TI created.",
                "No 1st class ID supplied and no match could be made on title name.",
                user, project
                )
          }
        }
        break;
      case 1 :
        // Single component match.
        log.debug ("Title class one identifier lookup yielded a single match.")
        
        // We should raise a review request here if the match was made by cross checking
        // different identifier namespaces.
        if (results['x_check_matches'].size() == 1 && results['x_check_matches'][0]['suppliedNS'] != 'issnl') {
          
          def data = results['x_check_matches'][0]
          
          // Fire the review request.
          ReviewRequest.raise(
            matches[0],
            "Identifier type mismatch.",
            "Ingest file ${data['suppliedNS']} matched an existing ${data['foundNS']}.",
            user,
            project
          )
        }

        // If one identifier matches, but all other class ones are different, it is probably not a real match.

        def id_mismatches = []

        results['ids'].each { rid ->
          matches[0].ids.each { mid ->
            if ( rid.namespace == mid.namespace && rid.value != mid.value ) {
              if ( !matches[0].ids.contains(rid) ) {
                id_mismatches.add(rid)
              }
            }
          }
        }

//         if( id_mismatches > 0 ) {
//           ReviewRequest.raise(
//             matches[0],
//             "Identifier mismatch.",
//             "Ingest identifier differs from existing one in the same namespace.",
//             user,
//             project
//           )
//         }
        
        // Take whatever we can get if what we have is an unknown title
        if ( metadata.title.startsWith("Unknown Title") || metadata.status == "Expected" ) {
          // Don't go through title matching if we don't have a real title
          the_title = matches[0]
        }
        else {
          if ( matches[0].name.startsWith("Unknown Title") || metadata.status == "Expected" ) {
            // If we have an unknown title in the db, and a real title, then take that
            // in preference
            log.debug("Found new Title ${metadata.name} for previously unknown title ${matches[0]} (${matches[0].name})")
            the_title = matches[0]
            the_title.name = metadata.title
            the_title.status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
          }
          else {
            if ( matches[0].name.equals(metadata.title) || matches[0].normname?.equals(KBComponent.generateNormname(metadata.title)) ) { 
              // Perfect match - do nothing
              the_title = matches[0]

              if( id_mismatches.size() > 0 ){
                ReviewRequest.raise(
                  matches[0],
                  "Identifier mismatch.",
                  "Title ${matches[0]} matched, but ingest identifiers ${id_mismatches} differ from existing ones in the same namespaces.",
                  user,
                  project
                )
              }
            }
            else {
              if( id_mismatches.size() > 0 ){
                // Another class one identifier of the matched title is different. This looks like a new title.

                if ( newTitleClassName == null ) {
                  the_title = new TitleInstance(name:metadata.title, normname:KBComponent.generateNormname(metadata.title), ids:[])
                }
                else {
                  def clazz = Class.forName(newTitleClassName)
                  the_title = clazz.newInstance()
                  the_title.name = metadata.title
                  the_title.normname = KBComponent.generateNormname(metadata.title)
                  the_title.ids = []
                }
                ReviewRequest.raise(
                  the_title,
                  "New TI created.",
                  "TitleInstance ${matches[0].id} was matched on one identifier, but at least one other ingest identifier differs from existing ones in the same namespace.",
                  user,
                  project
                )
              }else{
                // Now we can examine the text of the title.
                the_title = singleTIMatch(metadata.title,matches[0], user, project)
              }
            }
          }
        }
        break;

      default :
        // Multiple matches.
        log.debug ("Title class one identifier lookup yielded ${matches.size()} matches - ${matches.collect{it.id}}.")
        def all_matched = []

        matches.each { mti ->

          def full_match = true

          results['ids'].each { rid ->
            mti.ids.each { mid ->
              if ( rid.namespace == mid.namespace && rid.value != mid.value ) {
                if ( !mti.ids.contains(rid) ) {
                  full_match = false
                }
              }
            }
          }

          if ( full_match ) {
            all_matched.add(mti)
          }

        }

        switch ( all_matched.size() ) {
          case 0 :
            log.debug("Multiple matches for a single identifier. No matches for all class ones. Creating new TI!")

            if ( newTitleClassName == null ) {
              the_title = new TitleInstance(name:metadata.title, normname:KBComponent.generateNormname(metadata.title), ids:[])
            }
            else {
              def clazz = Class.forName(newTitleClassName)
              the_title = clazz.newInstance()
              the_title.name = metadata.title
              the_title.normname = KBComponent.generateNormname(metadata.title)
              the_title.ids = []
            }

            ReviewRequest.raise(
              the_title,
              "New TI created.",
              "Multiple TitleInstances ${matches} were matched on one identifier, but none matched for all given IDs.",
              user,
              project
            )
            break;

          case 1 :
            log.debug("One match for all identifiers")
            the_title = all_matched[0]

            if(!the_title.name.equals(metadata.title)){
              the_title.ensureVariantName(metadata.title)
            }
            break;

          default :
            log.debug("Multiple matches for given ingest identifiers. Trying to match by name..")

            def matched_with_name = []

            all_matched.each { mti ->
              if ( mti.name.equals(metadata.title) || mti.normname?.equals(KBComponent.generateNormname(metadata.title)) ) {
                matched_with_name.add(mti)
              }
            }

            if ( matched_with_name.size() == 1 ){
              log.debug("Only one matched TI (${matched_with_name[0]}) has the same name!")
              the_title = matched_with_name[0]
            }
            else {
              log.debug("Could not match a specific title. Skipping..")
            }
            break;
        }
        break;
    }

    // If we have a title then lets set the publisher and ids...
    if (the_title) {

      // Make sure we're all saved before looking up the publisher
      the_title.save(flush:true, failOnError:true);
      
      if(the_title.name.startsWith("Unknown Title")){
        the_title.status = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, 'Expected')
      }

      // Add the publisher.
      addPublisher(metadata.publisher_name, the_title, user, project)

      the_title.save(flush:true, failOnError:true);

      Set ids_to_add = []
      ids_to_add.addAll(results['ids'])
      ids_to_add.addAll(results['other_identifiers'])

      def id_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'KBComponent.Ids')

      ids_to_add.each {

        def dupes = Combo.executeQuery("Select c from Combo as c where c.toComponent.id = ? and c.fromComponent.id = ? and c.type.id = ? and c.fromComponent.status.value <> 'Deleted'",[it.id,the_title.id,id_combo_type.id]);

        if ( !dupes || dupes.size() == 0) {

//           log.debug("Titles ${the_title.id} does not already contain identifier ${it.id}. See if adding it would create a conflict, if not, add it");

          // Double check the identifier we are about to add does not already exist attached to another item in the system
          // Combo.Type : KBComponent.Ids

//           def existing_identifier = Combo.executeQuery("Select c from Combo as c where c.toComponent.id = ? and c.type.id = ? and c.fromComponent.status.value <> 'Deleted'",[it.id,id_combo_type.id]);

//           if ( existing_identifier.size() > 0 ) {
//             ReviewRequest.raise(
//               the_title,
//               "Identifier not unique",
//               "The ingest file suggested an identifier (${it.id}) for a title which is already connected with another record in the system (component ${existing_identifier[0].fromComponent})",
//               user,
//               project
//             )
//           }
          
          log.debug("Adding new identifier ${it} to title ${the_title}");
          Combo new_id = new Combo(toComponent:it, fromComponent:the_title, type:id_combo_type).save(flush:true, failOnError:true);
        }
        else {
          log.debug("Identifier ${it} is already connected to the title!");
        }
      }

      // Try and save the result now.
      if ( the_title.isDirty() ) {
        if ( the_title.save(failOnError:true, flush:true) ) {
          log.debug("Succesfully saved TI: ${the_title.name} (This may not change the db)")
        }
        else {
          the_title.errors.each { e ->
            log.error("Problem saving title: ${e}");
          }
        }
      }
    }

    the_title
  }

  private TitleInstance addPublisher (publisher_name, ti, user = null, project = null) {


    if ( ( publisher_name != null ) && 
         ( publisher_name.trim().length() > 0 ) ) {
         
      log.debug("Add publisher \"${publisher_name}\"")
      Org publisher = componentLookupService.lookupComponent(publisher_name)
      
      if ( !publisher ) {
        // Lookup using norm name.
        def norm_pub_name = Org.generateNormname(publisher_name);
        
        log.debug("Using normname \"${norm_pub_name}\" for lookup")
        publisher = Org.findByNormname(norm_pub_name)
      }      

      if ( !publisher || publisher.status.value == 'Deleted') {
        def variant_normname = GOKbTextUtils.normaliseString(publisher_name)
        def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status.value <> 'Deleted'",[variant_normname]);
        if ( candidate_orgs.size() == 1 ) {
          publisher = candidate_orgs[0]
        }
        else if ( candidate_orgs.size() == 0 ) {
          publisher = new Org(name:publisher_name, normname:norm_pub_name).save(flush:true, failOnError:true);
        }
        else {
          log.error("Unable to match unique pub");
        }
      }

      // Found a publisher.
      if (publisher) {
        log.debug("Found publisher ${publisher}");
        def orgs = ti.getPublisher()

        // Has the publisher ever existed in the list against this title.
        if (!orgs.contains(publisher)) {

          // First publisher added?
          boolean not_first = orgs.size() > 0

          // Added a publisher?
          ti.changePublisher (publisher)
        }
      }
    }

    ti
  }


  private TitleInstance attemptBucketMatch (String title) {
    def t = null;
    if ( title && ( title.length() > 0 ) ) {
      def nname = GOKbTextUtils.norm2(title);
      
      def bucket_hash = GOKbTextUtils.generateComponentHash([nname]);

      // def component_hash = GOKbTextUtils.generateComponentHash([nname, componentDiscriminator]);

      t = TitleInstance.findByBucketHash(bucket_hash);
      log.debug("Result of findByBucketHash(\"${bucket_hash}\") for title ${title} : ${t}");
    }

    return t;
  }

  private TitleInstance singleTIMatch(String title, TitleInstance ti, User user, project = null) {

    log.debug("singleTIMatch");

    String comparable_title = GOKbTextUtils.generateComparableKey(title)
    
    // The threshold for a good match.
    double threshold = grailsApplication.config.cosine.good_threshold

    // Work out the distance between the 2 title strings.
    double distance = GOKbTextUtils.cosineSimilarity(GOKbTextUtils.generateComparableKey(ti.getName()), comparable_title)

    // Check the distance.
    switch (distance) {

      case 1 :

        // Do nothing just continue using the TI.
        log.debug("Exact distance match for TI.")
        break

      case {
        ti.variantNames.find {alt ->
          GOKbTextUtils.cosineSimilarity(GOKbTextUtils.generateComparableKey(alt.variantName), comparable_title) >= threshold
        }}:
        // Good match on existing variant titles
        log.debug("Good match for TI on variant.")
        break

      case {it >= threshold} :

        // Good match. Need to add as alternate name.
        log.debug("Good distance match for TI. Add as variant.")
        ti.addVariantTitle(title)
        break

      default :
        // Bad match...
        ti.addVariantTitle(title)

        // Raise a review request
        ReviewRequest.raise(
            ti,
            "'${title}' added as a variant of '${ti.name}'.",
            "Match was made on 1st class identifier but title name seems to be very different.",
            user, project
            )
        break
    }

    ti
  }

  
  /**
   * @param ids should be a list of maps containing at least an ns and value key. 
   * @return
   */
  public def matchClassOnes (def ids) {
    def result = [] as Set
    
    // Get the class 1 identifier namespaces.
    Set<String> class_one_ids = grailsApplication.config.identifiers.class_ones

    def start_time = System.currentTimeMillis();
    
    ids.each { def id_def ->

      log.debug("Consider ${id_def}");

      // Class ones only.
      if ( id_def.value && 
           id_def.ns && 
           class_one_ids.contains(id_def.ns) ) {

        log.debug("looking up ${id_def}");
      
        def identifiers = Identifier.createCriteria().list(max: 5) {
          and { 
            namespace {
              inList "value", id_def.ns
            }
            
            eq "value", id_def.value
          }
        }

        log.debug("Attempt matchClassOnes on ${id_def}, processing ${identifiers.size()} candidates");

        if ( identifiers.size() > 4 ) {
          log.warn("matchClassOne for ${id_def} returned a high number of candidate records. This shouldn't be the case");
        }
        
        // Examine the identified components.
        identifiers?.each {
          log.debug("Handle ${it?.identifiedComponents.size()} components");
          it?.identifiedComponents.each {
            KBComponent comp = KBComponent.deproxy(it)
            if (comp instanceof TitleInstance) {
              // Add to the set.
              result << (TitleInstance)comp
            }
          }
        }
      }
    }

    def elapsed = System.currentTimeMillis() - start_time;
    if ( elapsed > 2000 ) {
      log.warn("matchClassOnes took much longer than expected to complete when processing ${ids}. Needs investigation");
    }
    
    result
  }

  public def matchClassOneComponentIds(def ids) {
    def result = null

    log.debug("matchClassOneComponentIds(${ids})");

    try {
      // Get the class 1 identifier namespaces.
      Set<String> class_one_ids = grailsApplication.config.identifiers.class_ones
  
      def start_time = System.currentTimeMillis();
  
      def bindvars = []
      StringWriter sw = new StringWriter()
      sw.write("select DISTINCT c.fromComponent.id from Combo as c where ( ")
  
  
      def ctr = 0;
      ids.each { def id_def ->
        // Class ones only.
        if ( id_def.value && id_def.ns && class_one_ids.contains(id_def.ns) ) { 
          def ns = IdentifierNamespace.findByValue(id_def.ns)
          if ( ns ) {
  
            def the_id = Identifier.executeQuery('select i from Identifier as i where i.value = ? and i.namespace = ?',[id_def.value, ns])
            if ( the_id.size() == 1 ) {
              if ( ctr++ ) {
                sw.write(" or ");
              }
  
              sw.write( "( c.toComponent = ? )" )
              bindvars.add(the_id[0])
            }
            if ( the_id.size() > 1 ) {
              // applicationEventService.publishApplicationEvent('CriticalSystemMessages', 'ERROR', [description:"Multiple Identifiers Matched on lookup id:${id_def}"])
              // event('DataProblem', [code:'MultipleIdentifierMatch', id:id_def], [ fork:false ] )
            }
          }
        }
      }
  
  
      if ( ctr > 0 ) {
        sw.write(" ) and c.type.value=?");
        bindvars.add('KBComponent.Ids');
        def qry = sw.toString();
        log.debug("Run: ${qry} ${bindvars}");
        result = TitleInstance.executeQuery(qry,bindvars);
      }
      else {
        log.warn("No class 1 identifiers(${class_one_ids}) in ${ids}");
      }
    }
    catch ( Exception e ) {
      log.error("unexpected error attempting to find title by identifiers",e);
    }

    log.debug("Returning Result of matchClassOneComponentIds(${ids}) : ${result}");
    result
  }

  def Object getTitleField(title_id, field_name) {
    def result = TitleInstance.executeQuery("select ti."+field_name+" from TitleInstance as ti where ti.id=?",title_id);
    return result.size() == 1 ? result[0] : null;
  }

  def Object getTitleFieldForIdentifier(ids, field_name) {
    def result = null
    def l = matchClassOneComponentIds(ids)
    if ( l && l.size() == 1 ) {
      result = TitleInstance.executeQuery("select ti."+field_name+" from TitleInstance as ti where ti.id=?",l[0])[0];
    }
    log.debug("getTitleFieldForIdentifier(${ids},${field_name} : ${result}");
    return result
  } 

  // A task will be created to remap a title instance by an update to that title which touches
  // any field that might change the Instance -> Work mapping. We have to wait for that update to
  // complete before processing
  def remapTitleInstance(oid) {
    try {
      TitleInstance.withNewTransaction {
        log.debug("remapTitleInstance::${oid}");
        def domain_object = genericOIDService.resolveOID(oid,true)
        if ( domain_object ) {
          log.debug("Calling ${domain_object}.remapWork()");
          domain_object.remapWork();
        }
        else {
          log.debug("Unable tyo locate domain object for ${oid}");
        }
      }
    }
    catch ( Exception e ) {
      log.error("Problem in remap work.",e);
    }
  }

  def getComponentsForIdentifier(identifier) {
    // was identifier.identifiedComponents
    KBComponent.executeQuery('select DISTINCT c.fromComponent from Combo as c where c.toComponent = :id and c.type.value = :tp',[id:identifier,tp:'KBComponent.Ids']);
  }
}
