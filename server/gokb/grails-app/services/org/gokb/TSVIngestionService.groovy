package org.gokb

import java.util.Map;
import java.util.Set;
import java.util.GregorianCalendar;

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.bean.CsvToBean
import au.com.bytecode.opencsv.bean.HeaderColumnNameMappingStrategy
import au.com.bytecode.opencsv.bean.HeaderColumnNameTranslateMappingStrategy
import java.text.SimpleDateFormat

import com.k_int.ConcurrencyManagerService;
import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ClassUtils

import grails.transaction.Transactional

import org.gokb.cred.TitleInstance
// Only in ebooks branch -- import org.gokb.cred.BookInstance
import org.gokb.cred.ComponentPerson
import org.gokb.cred.ComponentSubject
import org.gokb.cred.IngestionProfile
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.KBComponent
import org.gokb.cred.KBComponentVariantName
// import org.gokb.cred.KBartRecord
import org.gokb.cred.Org
import org.gokb.cred.Package;
import org.gokb.cred.Person
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory;
import org.gokb.cred.RefdataValue;
import org.gokb.cred.ReviewRequest
import org.gokb.cred.Subject
import org.gokb.cred.TitleInstance;
import org.gokb.cred.TitleInstancePackagePlatform;
import org.gokb.cred.User;
import org.gokb.cred.DataFile;
import org.gokb.cred.IngestionProfile;
import org.gokb.exceptions.*;
import com.k_int.TextUtils
import grails.converters.JSON

@Transactional
class TSVIngestionService {

  def grailsApplication
  def titleLookupService
  def componentLookupService
  def refdataCategory
  def sessionFactory
  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP

  def possible_date_formats = [
    new SimpleDateFormat('yyyy-MM-dd'), // Default format Owen is pushing ATM.
    new SimpleDateFormat('yyyy/MM/dd'),
    new SimpleDateFormat('dd/MM/yyyy'),
    new SimpleDateFormat('dd/MM/yy'),
    new SimpleDateFormat('yyyy/MM'),
    new SimpleDateFormat('yyyy')
  ];



  /* This class is a BIG rip off of the TitleLookupService, it really should be
   * refactored to use inheritance of something!
   */

  private Map class_one_match (def ids) {
    // Get the class 1 identifier namespaces.
    Set<String> class_one_ids = grailsApplication.config.identifiers.class_ones
    def xcheck = grailsApplication.config.identifiers.cross_checks
    // Return the list of class 1 identifiers we have found or created, as well as the
    // list of matches
    def result = [
    "class_one"         : false,
    "ids"                : [],
    "matches"           : [] as Set,
    "x_check_matches"   : [] as Set
    ]

    // Go through each of the class_one_ids and look for a match.
    ids.each { id_def ->
      if (id_def.type && id_def.value) {
        log.debug("id_def.type")
  
        def id_value = id_def.value
        // id_def is map with keys 'type' and 'value'
        if ( grailsApplication.config.identifiers.formatters[id_def.type] ) {
          // If we have a formatter for this kind of identifier, call it here.
          id_value = grailsApplication.config.identifiers.formatters[id_def.type](id_value)
        }
  
        Identifier the_id = Identifier.lookupOrCreateCanonicalIdentifier(id_def.type, id_value)
        // Add the id.
        result['ids'] << the_id
        log.debug("class_one_match ids ${result['ids']}")
        // We only treat a component as a match if the matching Identifer
        // is a class 1 identifier.
        if (class_one_ids.contains(id_def.type)) {
          // Flag class one is present.
          result['class_one'] = true
          // Flag for title match
          boolean title_match = false
          // If we find an ID then lookup the components.
          Set<KBComponent> comp = the_id.identifiedComponents
          comp.each { KBComponent c ->
            // Ensure we're not looking at a Hibernate Proxy class representation of the class
            KBComponent dproxied = ClassUtils.deproxy(c);
            // Only add if it's a title.
            if ( dproxied instanceof TitleInstance ) {
              title_match = true
              result['matches'] << (dproxied as TitleInstance)
            }
          }
    
          // Did the ID yield a Title match?
          log.debug("title match for ${id_def.value} : ${title_match}")
    
          if (!title_match) {
            log.debug ("No class one ti match against ${id_def.type}:${id_def.value}. Cross-checking")
    
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
                    xc_id = Identifier.findByNamespaceAndValue(namespace, id_value)
                    log.debug ("Looking up ${ns}:${id_value} returned ${xc_id}.")
                    comp = xc_id?.identifiedComponents
                    comp?.each { KBComponent c ->
                      // Ensure we're not looking at a Hibernate Proxy class representation of the class
                      KBComponent dproxied = ClassUtils.deproxy(c);
                      // Only add if it's a title.
                      if ( dproxied instanceof TitleInstance ) {
                        // Save details here so we can raise a review request, only if a single title was matched.
                        result['x_check_matches'] << [
                        "suppliedNS"  : id_def.type,
                        "foundNS"     : ns
                        ]
                        result['matches'] << (dproxied as TitleInstance)
                      }
                    }
                  }
                }
              }
            }
          }
          else {
            // Hurrah! we got a title match
            log.debug ("Class one ti match against ${id_def.type}:${id_def.value}. Cross-checking")
          }
        }
      }
    }
    result
  }

  def lookupOrCreateTitle (String title,
                           def identifiers,
                           ingest_cfg,
                           def user = null,
                           def project = null) {
    // The TitleInstance
    TitleInstance the_title = null
    log.debug("lookup or create title :: ${title}(${ingest_cfg})")

    if (title == null) return null

    // Create the normalised title.
    String norm_title = GOKbTextUtils.generateComparableKey(title)
    // Lookup any class 1 identifier matches
    def results = class_one_match (identifiers)
    // The matches.
    List< KBComponent> matches = results['matches'] as List
    switch (matches.size()) {
    case 0 :
      // No match behaviour.
      // Check for presence of class one ID
      if (results['class_one']) {
        log.debug ("One or more class 1 IDs supplied so must be a new TI. Create instance of ${ingest_cfg.defaultType}")
        // Create the new TI.
        // the_title = new BookInstance(name:title)
        the_title = ingest_cfg.defaultType.newInstance()
        the_title.name=title
        the_title.ids=[]
      } else {
        // No class 1s supplied we should try and find a match on the title string.
        log.debug ("No class 1 ids supplied.")
        // Lookup using title string match only.
        the_title = attemptStringMatch (norm_title)
        if (the_title) {
          log.debug("TI ${the_title} matched by name. Partial match")
          // Add the variant.
          the_title.addVariantTitle(title)
          // Raise a review request
          ReviewRequest.raise(
            the_title,
            "'${title}' added as a variant of '${the_title.name}'.",
            "No 1st class ID supplied but reasonable match was made on the title name.",
            user, project
            )
        } else {
          log.debug("No TI could be matched by name. New TI, flag for review.")
          // Could not match on title either.
          // Create a new TI but attach a Review request to it.
          the_title = ingest_cfg.defaultType.newInstance()
          the_title.ids=[]
          the_title.name=title
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
      if (results['x_check_matches'].size() == 1) {
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

      // Now we can examine the text of the title.
      the_title = singleTIMatch(title, 
                                norm_title, 
                                matches[0], 
                                user, 
                                project, 
                                ingest_cfg.inconsistent_title_id_behavior,
                                identifiers)
      break;
    default :
      // Multiple matches.
      log.debug ("Title class one identifier lookup yielded ${matches.size()} matches. This is a bad match. Ingest should skip this row.")
      break;
    }

    // If we have a title then lets set the publisher and ids...
    if (the_title) {
      results['ids'].each {
        if ( ! the_title.ids.contains(it) ) {
          // We should **NOT** do this in the case where we are creating a new title because the publisher listed a title
          // in the title history group using an identifier in that group.
          the_title.ids.add(it);
        }
      }

      // Try and save the result now.
      if ( the_title.save(failOnError:true, flush:true) ) {
        // log.debug("Succesfully saved TI: ${the_title.name} ${the_title.id} (This may not change the db)")
      }
      else {
        log.error("**PROBLEM SAVING TITLE**");
        the_title.errors.each { e ->
          log.error("Problem saving title: ${e}");
        }
      }
    }

    log.debug("lookupOrCreateTitle(${title}.....) returning ${the_title?.id}");
    the_title
  }

  //for now, we can only do authors. (kbart limitation)
  def TitleInstance addPerson (person_name, role, ti, user=null, project = null) {
    if ( (person_name) && ( person_name.trim().length() > 0 ) ) {

      def norm_person_name = GOKbTextUtils.normaliseString(person_name)
      def person = org.gokb.cred.Person.findAllByNormname(norm_person_name)
      // log.debug("this was found for person: ${person}");
      switch(person.size()) {
        case 0:
          // log.debug("Person lookup yielded no matches.")
          def the_person = new Person(name:person_name)
            if (the_person.save(failOnError:true, flush:true)) {
            log.debug("saved ${the_person.name}")
            person << the_person
            ReviewRequest.raise(
            ti,
            "'${the_person}' added as ${role.value} of '${ti.name}'.",
             "This person did not exist before, so has been newly created",
            user, project)
            } else {
              the_person.errors.each { error ->
                log.error("problem saving ${the_person.name}:${error}")
              }
            }
        case 1:
          def people = ti.getPeople()?:[]
          // log.debug("ti.getPeople ${people}")
          // Has the person ever existed in the list against this title.
          boolean done=false;
          for (cp in people) {
            if (!done && (cp.person.id==person[0].id && cp.role.id==role.id) ) {
              done=true;
            }
          }

          if (!done) {
            def componentPerson = new ComponentPerson(component:ti, person:person, role:role)

            // log.debug("people did not contain this person")
            // First person added?

            boolean not_first = people.size() > 0
            boolean added = componentPerson.save(failOnError:true, flush:true);

            if (!added) {
              componentPerson.errors.each { error ->
              log.error("problem saving ${componentPerson}:${error}")
              }
            }

            // Raise a review request, if needed.
            if (not_first && added) {
              ReviewRequest.raise( ti,
                      "Added '${person.name}' as ${role.value} on '${ti.name}'.",
                      "Person supplied in ingested file is additional to any already present on BI.",
                      user, project)
            }
          }
      break
      default:
      // log.debug ("Person lookup yielded ${person.size()} matches. Not really sure which person to use, so not using any.")
      break
    }
    }
    ti
  }

  def TitleInstance addSubjects(the_subjects, the_title) {
    if (the_subjects) {
      for (the_subject in the_subjects) {

        def norm_subj_name = GOKbTextUtils.normaliseString(the_subject)
        def subject = Subject.findAllByNormname(norm_subj_name) //no alt names for subjects
        // log.debug("this was found for subject: ${subject}")
        if (!subject) {
          // log.debug("subject not found, creating a new one")
          subject = new Subject(name:the_subject)
          subject.save(failOnError:true, flush:true)
        }
        boolean done=false
        def componentSubjects = the_title.subjects?:[]
        for (cs in componentSubjects) {
          if (!done && (cs.subject.id==subject.id)) {
            done=true;
          }
        }
        if (!done) {
          def cs = new ComponentSubject(component:the_title, subject:subject);
          cs.save(failOnError:true, flush:true)
        }
      }
    }
    the_title
  }

  def TitleInstance addPublisher (publisher_name, ti, user = null, project = null) {

    def clean_pub_name  = publisher_name?.replaceAll('"','').replaceAll('\'','');

    if ( ( clean_pub_name != null ) && ( clean_pub_name.trim().length() > 0 ) ) {

      log.debug("Org lookup: ${clean_pub_name}");
      def norm_pub_name = GOKbTextUtils.normaliseString(clean_pub_name)
      def publisher = org.gokb.cred.Org.findAllByNormname(clean_pub_name)
      // log.debug("this was found for publisher: ${publisher}");
      // Found a publisher.
      switch (publisher.size()) {
        case 0:
          log.debug ("Publisher ${clean_pub_name} lookup yielded no matches.")
          Org.withNewTransaction {
            def the_publisher = new Org(name:clean_pub_name)
            if (the_publisher.save(failOnError:true, flush:true)) {
              log.debug("saved ${the_publisher.name}")
              ReviewRequest.raise(
                ti,
                "'${the_publisher}' added as a publisher of '${ti.name}'.",
                 "This publisher did not exist before, so has been newly created",
                user, project)
            } else {
              the_publisher.errors.each { error ->
                log.error("problem saving ${the_publisher.name}:${error}")
              }
            }
          }

        //carry on...
        case 1:
          // log.debug("found a publisher")
          def orgs = ti.getPublisher()
          // log.debug("ti.getPublisher ${orgs}")
          // Has the publisher ever existed in the list against this title.

          if (!orgs.contains(publisher[0])) {
            // log.debug("orgs did not contain this publisher")
            // First publisher added?
            boolean not_first = orgs.size() > 0
            // Added a publisher?
            // log.debug("calling changepublisher")
            boolean added = ti.changePublisher ( publisher[0], true)

            log.debug("Not first : ${not_first}")
            log.debug("Added: ${added}");

            // Raise a review request, if needed.
            if (not_first && added) {
              ReviewRequest.raise( ti, "Added '${publisher.name}' as a publisher on '${ti.name}'.",
                  "Publisher supplied in ingested file is different to any already present on TI.", user, project)
            }
          } //!orgs.contains(publisher)
          break
        default:
          log.debug ("Publisher lookup yielded ${publisher.size()} matches. Not really sure which publisher to use, so not using any.")
        break
      }  //switch
    } //publisher_name !=null
    ti
  }

  private TitleInstance attemptStringMatch (String norm_title) {

    // Default to return null.
    TitleInstance ti = null

    // Try and find a title by matching the norm string.
    // Default to the min threshold
    double best_distance = grailsApplication.config.cosine.good_threshold

    TitleInstance.list().each { TitleInstance t ->

    // Get the distance and then determine whether to add to the list or
    double distance = GOKbTextUtils.cosineSimilarity(norm_title, GOKbTextUtils.generateComparableKey(t.getName()))
    if (distance >= best_distance) {
      ti = t
      best_distance = distance
    }

    t.variantNames?.each { vn ->
      distance = GOKbTextUtils.cosineSimilarity(norm_title, vn.normVariantName)
      if (distance >= best_distance) {
      ti = t
      best_distance = distance
      }
    }
    }

    // Return what we have found... If anything.
    ti
  }

  private TitleInstance singleTIMatch(String title, 
                                      String norm_title, 
                                      TitleInstance ti, 
                                      User user, 
                                      project = null, 
                                      inconsistent_title_id_behaviour = 'add_as_variant',
                                      identifiers) {
    // The threshold for a good match.
    double threshold = grailsApplication.config.cosine.good_threshold
    // Work out the distance between the 2 title strings.
    double distance = GOKbTextUtils.cosineSimilarity(GOKbTextUtils.generateComparableKey(ti.getName()), norm_title)
    // Check the distance.

    def result = ti;

    switch (distance) {
      case 1 :
        // Do nothing just continue using the TI.
        // log.debug("Exact distance match for TI.")
        break
  
      case {
        ti.variantNames.find {alt ->
        GOKbTextUtils.cosineSimilarity(GOKbTextUtils.generateComparableKey(alt.variantName), norm_title) >= threshold
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

        // We've got an inconsistent title/id situation here -- It might be a legitimate variant, but more likely
        // the publisher is referring to an earlier title in the title history using a later identifier.

        // Check #1 see if this title appears somewhere else in the title hisory for the identified record
        def title_in_history = ti.findInTitleHistory(title)

        if ( title_in_history ) {
          log.debug("Using title string matched from title history ${ti} -> ${title_in_history}");
          result=title_in_history
          result.title_status_properties.matched_by='Title In Title History'
        }
        else {


          // NO match in title history -- depends what the import user wants us to do now.
          if ( inconsistent_title_id_behaviour == 'add_as_variant' ) {
            // Add as a variant title string to the identified title
            ti.addVariantTitle(title)
            // Raise a review request
            ReviewRequest.raise(
              ti,
              "'${title}' added as a variant of '${ti.name}'.",
              "Match was made on 1st class identifier but title name seems to be very different.",
              user, project
              )
          }
          else if ( inconsistent_title_id_behaviour == 'reject' ) {
            throw new InconsistentTitleIdentifierException("New title \"${title}\" matched via its identifiers ${identifiers} against title with internal ID [${ti.id}] but that title string is \"${ti.name}\". Radically different titles with the same identifier are usually different titles in the same title history group when the publisher has elected not to discover the correct identifier for a preceeding or succeeding item.",
                                                           title, identifiers, ti.id, ti.name)
          }
          else {
            // New title without an identifier linked to the title history for the originally identified title

          }
        }
        break
    }

    result
  }

  //these are now ingestions of profiles.
  def ingest(the_profile_id,
             datafile_id,
             job=null,
             ip_id=null,
             ingest_cfg=null) {

    if ( the_profile_id == null ) {
      log.error("No datafile ID passed in to ingest")
      return
    }

    def the_profile = IngestionProfile.get(the_profile_id)

    if ( the_profile == null )      {
      log.error("Unable to datafile for ID ${datafile_id}")
      return
    }

    ingest2(the_profile.packageType,
           the_profile.packageName,
           the_profile.platformUrl,
           the_profile.source,
           datafile_id,
           null,
           null,
           job,
           ip_id,
           ingest_cfg)
  }


  def ingest2(packageType,
             packageName,
             platformUrl,
             source,
             datafile_id,
             job=null,
             providerName=null,
             providerIdentifierNamespace=null,
             ip_id=null,
             ingest_cfg=null) {

    long start_time = System.currentTimeMillis();

    // Read does no dirty checking
    def datafile = DataFile.read(datafile_id)

    if ( ingest_cfg == null ) {
      ingest_cfg = [
                     defaultType:org.gokb.cred.TitleInstance.class,
                     identifierMap:[
                       'print_identifier':'issn',
                       'online_identifier':'eissn',
                     ],
                     defaultMedium:'Journal',
                     providerIdentifierNamespace:providerIdentifierNamespace,
                     inconsistent_title_id_behavior:'reject'
                   ]
    }

    try {

      def ingest_systime = start_time
      def ingest_date = new java.sql.Timestamp(start_time);

      job?.setProgress(0)

      def kbart_beans=[]
      def badrows=[]

      //we kind of assume that we need to convert to kbart
      if ("${packageType}"!='kbart2') {
        kbart_beans = convertToKbart(packageType, datafile)
      } else {
        kbart_beans = getKbartBeansFromKBartFile(datafile)
      }

      def the_package = null
      def the_package_id = null
      def author_role_id = null;
      def editor_role_id = null;

      def preflight_result = preflight( kbart_beans, ingest_cfg ) 
      if ( preflight_result.passed ) {

        log.debug("Passed preflight -- ingest");
  
        Package.withNewTransaction() {
          the_package=handlePackage(packageName,source,providerName)
          assert the_package != null
          the_package_id=the_package.id
          def author_role = RefdataCategory.lookupOrCreate(grailsApplication.config.kbart2.personCategory, grailsApplication.config.kbart2.authorRole)
          author_role_id = author_role.id
          def editor_role = RefdataCategory.lookupOrCreate(grailsApplication.config.kbart2.personCategory, grailsApplication.config.kbart2.editorRole)
          editor_role_id = editor_role.id
        }
  
  
        long startTime=System.currentTimeMillis()
  
        log.debug("Ingesting ${kbart_beans.size} rows. Package is ${the_package_id}")
        //now its converted, ingest it into the database.
  
        for (int x=0; x<kbart_beans.size;x++) {
  
          Package.withNewTransaction {
  
            def author_role = RefdataValue.get(author_role_id)
            def editor_role = RefdataValue.get(editor_role_id)
  
            log.debug("\n\n**Ingesting ${x} of ${kbart_beans.size} ${kbart_beans[x]}")
  
            long rowStartTime=System.currentTimeMillis()
  
            if ( validateRow(x, badrows, kbart_beans[x] ) ) {
              writeToDB(kbart_beans[x],
                        platformUrl,
                        source,
                        ingest_date,
                        ingest_systime,
                        author_role,
                        editor_role,
                        Package.get(the_package_id),
                        ingest_cfg,
                        badrows )
            }
  
            log.debug("ROW ELAPSED : ${System.currentTimeMillis()-rowStartTime}");
          }
  
          job?.setProgress( x , kbart_beans.size() )
  
          if ( x % 25 == 0 ) {
            cleanUpGorm()
          }
        }
  
        log.debug("Expunging old tipps [Tipps belonging to ${the_package_id} last seen prior to ${ingest_date}] - ${packageName}");
  
        TitleInstancePackagePlatform.withNewTransaction {
          try {
            // Find all tipps in this package which have a lastSeen before the ingest date
            def q = TitleInstancePackagePlatform.executeQuery('select tipp '+
                             'from TitleInstancePackagePlatform as tipp, Combo as c '+
                             'where c.fromComponent.id=:pkg and c.toComponent=tipp and tipp.lastSeen < :dt and tipp.accessEndDate is null',
                            [pkg:the_package_id,dt:ingest_systime]);
  
            q.each { tipp ->
              log.debug("Soft delete missing tipp ${tipp.id} - last seen was ${tipp.lastSeen}, ingest date was ${ingest_systime}");
              // tipp.deleteSoft()
              tipp.accessEndDate = new Date();
              tipp.save(failOnError:true,flush:true)
            }
            log.debug("Completed tipp cleanup")
          }
          catch ( Exception e ) {
            log.error("Problem",e)
          }
          finally {
            log.debug("Done")
          }
        }
  
        if ( badrows.size() > 0 ) {
          log.debug("There are ${badrows.size()} bad rows -- write to badfile and report")
        }
      }  
      else {
        // Preflight failed
        log.error("Failed preflight \n\n${preflight_result as JSON}");
      }
    }
    catch ( Exception e ) {
      log.error("Problem",e)
    }

    job?.setProgress(100)

    def elapsed = System.currentTimeMillis()-start_time;

    log.debug("ingest completed in ${elapsed}ms");
  }

  //this method does a lot of checking, and then tries to save the title to the DB.
  def writeToDB(the_kbart,
                platform_url,
                source,
                ingest_date,
                ingest_systime,
                author_role,
                editor_role,
                the_package,
                ingest_cfg,
                badrows) {

    //simplest method is to assume that everything is new.
    //however the golden rule is to check that something already exists and then
    //re-use it.
    log.debug("TSVINgestionService:writeToDB -- package id is ${the_package.id}")

    //first we need a platform:
    def platform = null; // handlePlatform(platform_url.host, source)

    log.debug("default platform via default platform URL ${platform_url}, ${platform_url?.class?.name} ${platform_url?.host} title_url:${the_kbart.title_url}")

    if ( the_kbart.title_url != null ) {

      log.debug("Extract host from ${the_kbart.title_url}");

      def title_url_host = null

      try {
        def title_url = new URL(the_kbart.title_url)
        log.debug("Parsed title_url : ${title_url}");
        title_url_host = title_url.getHost()
      }
      catch ( Exception e ) {
      }

      if ( title_url_host ) {
        log.debug("Got platform from title host :: ${title_url_host}")
        platform = handlePlatform(title_url_host, source)
        log.debug("Platform result : ${platform}");
      }
      else {
        log.debug("title_url_host::${title_url_host}");
      }
    }
    else {
      log.debug("No title url");
    }

    if ( platform == null ) {
      log.debug("Platform is still null - use the default");
      platform = handlePlatform(platform_url.host, source)
    }

    assert the_package != null

    if (platform!=null) {

        log.debug(the_kbart.online_identifier)

        def identifiers = []
        if ( the_kbart.online_identifier )
          identifiers << [type:ingest_cfg.identifierMap.online_identifier, value:the_kbart.online_identifier]

        if ( the_kbart.print_identifier )
          identifiers << [type:ingest_cfg.identifierMap.print_identifier, value:the_kbart.print_identifier]

        the_kbart.additional_isbns.each { identifier ->
          identifiers << [type: 'isbn', value:identifier]
        }

        if ( ( the_kbart.title_id ) && ( the_kbart.title_id ) ) {
          log.debug("title_id ${the_kbart.title_id}");
          if ( ingest_cfg.providerIdentifierNamespace ) {
            identifiers << [type:ingest_cfg.providerIdentifierNamespace, value:the_kbart.title_id]
          }
          else {
            identifiers << [type:'title_id', value:the_kbart.title_id]
          }
        }

        if ( identifiers.size() > 0 ) {
          def title = lookupOrCreateTitle(the_kbart.publication_title, identifiers, ingest_cfg)
          if ( title ) {
            title.source=source
          // log.debug("title found: for ${the_kbart.publication_title}:${title}")

          if (title) {
            addOtherFieldsToTitle(title, the_kbart, ingest_cfg)

              if ( the_kbart.publisher_name && the_kbart.publisher_name.length() > 0 )
                addPublisher(the_kbart.publisher_name, title)

              if ( the_kbart.first_author && the_kbart.first_author.trim().length() > 0 )
                addPerson(the_kbart.first_author, author_role, title);

              if ( the_kbart.first_editor && the_kbart.first_author.trim().length() > 0 )
                addPerson(the_kbart.first_editor, editor_role, title);

              addSubjects(the_kbart.subjects, title)

              the_kbart.additional_authors.each { author ->
                addPerson(author, author_role, title)
              }

              def pre_create_tipp_time = System.currentTimeMillis();
              createTIPP(source,
                         the_kbart,
                         the_package,
                         title,
                         platform,
                         ingest_date,
                         ingest_systime)
            } else {
               log.warn("problem getting the title...")
            }
          }
          else {
            badrows.add([rowdata:the_kbart, reasons: 'Unable to lookup or create title']);
          }
        }
        else {
          log.debug("Skipping row - no identifiers")
          badrows.add([rowdata:the_kbart, reasons: 'No usable identifiers']);
        }

    } else {
      log.warn("couldn't reslove platform - title not added.");
    }
  }

  def addOtherFieldsToTitle(title, the_kbart, ingest_cfg) {
    title.medium=RefdataCategory.lookupOrCreate("TitleInstance.Medium", ingest_cfg.defaultMedium ?: "eBook")
    title.editionNumber=the_kbart.monograph_edition
    title.dateFirstInPrint=parseDate(the_kbart.date_monograph_published_print)
    title.dateFirstOnline=parseDate(the_kbart.date_monograph_published_online)
    title.volumeNumber=the_kbart.monograph_volume
    title.save(failOnError:true,flush:true)
  }

  Date parseDate(String datestr) {
    def parsed_date = null;
    if ( datestr && ( datestr.length() > 0 ) ) {
      for(Iterator<SimpleDateFormat> i = possible_date_formats.iterator(); ( i.hasNext() && ( parsed_date == null ) ); ) {
        try {
          parsed_date = i.next().clone().parse(datestr.replaceAll('-','/'));
        }
        catch ( Exception e ) {
        }
      }
    }
    parsed_date
  }


  def createTIPP(the_source,
                 the_kbart,
                 the_package,
                 the_title,
                 the_platform,
                 ingest_date,
                 ingest_systime) {

    log.debug("TSVIngestionService::createTIPP with pkg:${the_package}, ti:${the_title}, plat:${the_platform}, date:${ingest_date}")

    assert the_package != null && the_title != null && the_platform != null

    //first, try to find the platform. all we have to go in the host of the url.
    def tipp_values = [
      url:the_kbart.title_url?:'',
      pkg:the_package,
      title:the_title,
      hostPlatform:the_platform,
      embargo:the_kbart.embargo_info?:'',
      coverageNote:the_kbart.coverage_depth?:'',
      notes:the_kbart.notes?:'',
      startDate:parseDate(the_kbart.date_first_issue_online),
      startVolume:the_kbart.num_first_vol_online,
      startIssue:the_kbart.num_first_issue_online,
      endDate:parseDate(the_kbart.date_last_issue_online),
      endVolume:the_kbart.num_last_vol_online,
      endIssue:the_kbart.num_last_issue_online,
      source:the_source,
      accessStartDate:ingest_date,
      lastSeen:ingest_systime
    ]

    def tipp=null

    // ToDo : This should be a query, not iterating through in memory - or it will be incrementally slower
    //tipp = the_title.getTipps().find { def the_tipp ->
      // Filter tipps for matching pkg and platform.
    //  boolean matched = the_tipp.pkg == the_package
    //  matched = matched && the_tipp.hostPlatform == the_platform
    //  matched
    // }
    def tipps = TitleInstance.executeQuery('select tipp from TitleInstancePackagePlatform as tipp, Combo as pkg_combo, Combo as title_combo, Combo as platform_combo  '+
                                           'where pkg_combo.toComponent=tipp and pkg_combo.fromComponent=?'+
                                           'and platform_combo.toComponent=tipp and platform_combo.fromComponent = ?'+
                                           'and title_combo.toComponent=tipp and title_combo.fromComponent = ?',
                                          [the_package,the_platform,the_title])
    if ( tipps.size() == 1 ) {
      tipp = tipps[0]
    }


    if (tipp==null) {
      log.debug("create a new tipp as at ${ingest_date}");

      // These are immutable for a TIPP - only set at creation time
      tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_values)

      log.debug("Created");

      // because pkg is not a real property, but a hasByCombo, passing the value in the map constuctor
      // won't actually get this set. So do it manually. Ditto the other fields
      tipp.pkg = the_package;
      tipp.title = the_title;
      tipp.hostPlatform = the_platform;
      tipp.source = the_source;
    } else {
      // log.debug("found a tipp to use")

      // Set all properties on the object.
      tipp_values.each { prop, value ->
        // Only update if we actually have a change to make
        if ( tipp."${prop}" != value ) {
          // Only set the property if we have a value.
          if (value != null && value != "") {
            tipp."${prop}" = value
          }
        }
      }
    }

    log.debug("Values updated, set lastSeen");

    if ( ingest_systime ) {
      log.debug("Update last seen on tipp ${tipp.id} - set to ${ingest_date}")
      tipp.lastSeen = ingest_systime;
    }

    log.debug("save tipp")
    tipp.save(failOnError:true, flush:true)
    log.debug("createTIPP returning")
  }

  //this is a lot more complex than this for journals. (which uses refine)
  //theres no notion in here of retiring packages for example.
  //for this v1, I've made this very simple - probably too simple.
  def handlePackage(packageName, source, providerName) {
    def result;
    def norm_pkg_name = GOKbTextUtils.normaliseString(packageName)
    // def packages=Package.findAllByNormname(norm_pkg_name);
    def packages=Package.executeQuery("select p from Package as p where p.normname=?",[norm_pkg_name],[readonly:false])
    switch (packages.size()) {
      case 0:
        //no match. create a new package!
        log.debug("Create new package");

        def newpkgid = null;

        def newpkg = new Package(name:packageName, source:source)
        if (newpkg.save(flush:true, failOnError:true)) {
          newpkgid = newpkg.id
          if ( providerName && providerName.length() > 0 ) {
            def norm_provider_name = GOKbTextUtils.normaliseString(providerName)
            def provider = org.gokb.cred.Org.findByNormname(norm_provider_name) ?: new Org(name:norm_provider_name).save(flush:true, failOnError:true);
            newpkg.provider = provider
            newpkg.save()
          }
        } else {
          for (error in result.errors) {
            log.error(error);
          }
        }


        log.debug("Created new package : ${newpkgid} in current session");
        result = Package.get(newpkgid);
        break;
      case 1:
        //found a match
        result=packages[0]
        log.debug("match package found: ${result}")
      break
      default:
        log.error("found multiple packages when looking for ${packageName}")
      break
    }

    log.debug("handlePackage returns ${result}");
    result;
  }

  def handlePlatform(host, the_source) {

    def result;
    // def platforms=Platform.findAllByPrimaryUrl(host);
    def platforms=Platform.executeQuery("select p from Platform as p where p.primaryUrl=?",[host],[readonly:false])


    switch (platforms.size()) {
      case 0:

        //no match. create a new platform!
        log.debug("Create new platform ${host}, ${host}, ${the_source}");

          result = new Platform( name:host, primaryUrl:host, source:the_source)
  
          // log.debug("Validate new platform");
          // result.validate();
  
          if ( result ) {
            if (result.save(flush:true, failOnError:true)) {
              log.debug("saved new platform: ${result}")
            } else {
              log.error("problem creating platform");
              for (error in result.errors) {
                log.error(error);
              }
            }
          }
          else {
            result.errors.allErrors.each {
              log.error("Problem creating platform : ${e}");
            }
            throw new RuntimeException('Error creating new platform')
          }
        break;
      case 1:
        //found a match
        result=platforms[0]
        log.debug("match platform found: ${result}")
        break
      default:
        log.error("found multiple platforms when looking for ${host}")
      break
    }

    assert result != null

    log.debug("handlePlatform returning ${result}");
    result;
  }

  //note- don't do the additional fields just yet, these will need to be mapped in
  def getKbartBeansFromKBartFile(the_data) {
    log.debug("kbart2 file, so use CSV to Bean") //except that this doesn't always work :(
    def results=[]
    //def ctb = new CsvToBean<KBartRecord>()
    //def hcnms = new HeaderColumnNameMappingStrategy<KBartRecord>()
    //hcnms.type = KBartRecord
    def charset = 'ISO-8859-1' // 'UTF-8'
    def csv = new CSVReader(new InputStreamReader(new ByteArrayInputStream(the_data.fileData),java.nio.charset.Charset.forName(charset)),'\t' as char,'\0' as char)
    //results=ctb.parse(hcnms, csv)
    //quick check that results aren't null...


    Map col_positions=[:]
    String[] header = csv.readNext()
    int ctr = 0
    header.each {
      //col_positions[it]=-1
      col_positions[it]=ctr++
    }
    log.debug("${col_positions}")
    String[] nl=csv.readNext()
    int rownum = 0;
    while(nl!=null) {
      Map result=[:]
      if ( nl.length > 0 ) {

        for (key in col_positions.keySet()) {
          log.debug("Checking \"${key}\" - key position is ${col_positions[key]}")
          if ( key && key.length() > 0 ) {
            //so, springer files seem to start with a dodgy character (int) 65279
            if (((int)key.toCharArray()[0])==65279) {
              def corrected_key=key.getAt(1..key.length()-1)
              //if ( ( col_positions[key] ) && ( nl.length < col_positions[key] ) ) {
                result[corrected_key]=nl[col_positions[key]]
              //}
            } else {
              //if ( ( col_positions[key] ) && ( nl.length < col_positions[key] ) ) {
                if ( ( col_positions[key] != null ) && ( col_positions[key] < nl.length ) ) {
                  result[key]=nl[col_positions[key]]
                }
                else {
                  log.error("Column references value not present in col ${col_positions[key]} row ${rownum}");
                }
              //}
            }
          }
        }
      }
      else {
        log.warn("Possible malformed last row");
      }

      log.debug(result)
      // log.debug(new KBartRecord(result))
      //this is a cheat cos I don't get why springer files don't work!
      // results<<new KBartRecord(result)
      results.add(result)
      nl=csv.readNext()
      rownum++
    }
    log.debug(results)
    results
  }

  def convertToKbart(packageType, data_file) {
    def results = []
    log.debug("in convert to Kbart2")
    log.debug("file package type is ${packageType}")

    //need to know the file type, then we need to create a new data structure for it
    //in the config, need to map the fields from the formats we support into kbart.

    def fileRules = grailsApplication.config.kbart2.mappings."${packageType}"

    if (fileRules==null) {
      throw new Exception("couldn't find file rules for ${packageType}")
    }

    //can you read a tsv file?
    def charset = 'ISO-8859-1' // 'UTF-8'
    CSVReader csv = new CSVReader(new InputStreamReader(new ByteArrayInputStream(data_file.fileData),
                                                        java.nio.charset.Charset.forName(charset)),'\t' as char,'\0' as char)

    Map col_positions=[:]
    fileRules.each { fileRule ->
      col_positions[fileRule.field]=-1;
    }
    String [] header = csv.readNext()

    int ctr = 0


    header.each {
      log.debug("Column ${ctr} == ${it}");
      col_positions [ it ] = ctr++
    }

    def mapped_cols = [] as ArrayList
    def unmapped_cols = 0..(header.size()) as ArrayList

    log.debug("Mapped columns: ${mapped_cols}");
    log.debug("UnMapped columns: ${unmapped_cols}");


    fileRules.each { fileRule ->
      if ( col_positions[fileRule.field] >= 0 ) {
        // Column is mapped
        unmapped_cols.remove(col_positions[fileRule.field] as Object);
        mapped_cols.add(col_positions[fileRule.field] as Object)
      }
    }

    log.debug("Mapped columns: ${mapped_cols}");
    log.debug("UnMapped columns: ${unmapped_cols}");

    String [] nl = csv.readNext()

    long row_counter = 0

    while ( nl != null ) {

      log.debug("** Process row:${row_counter++} ${nl}");

      // KBartRecord result = new KBartRecord()
      def result = [:]
      result.unmapped=[]

      fileRules.each { fileRule ->
        boolean done=false
        String data = nl[col_positions[fileRule.field]];
        if ( col_positions[fileRule.field] >= 0 ) {
          // log.debug("field : ${fileRule.field} ${col_positions[fileRule.field]} ${data}")
          if (fileRule.separator!=null && data.indexOf(fileRule.separator)>-1) {
            def parts = data.split(fileRule.separator)
            data=parts[0]
            if ( parts.size() > 1 && fileRule.additional!=null ) {
              for (int x=1; x<parts.size(); x++) {
                result[fileRule.additional] << parts[x]
              }
              done=true
            }
          }

          if (fileRule.additional!=null && !done) {
            result[fileRule.additional] << data
          } else {
            result[fileRule.kbart]=data
          }
        }
      }

      unmapped_cols.each { unmapped_col_idx ->
        if ( nl.size() < unmapped_col_idx ) {
          log.debug("Setting unmapped column idx ${unmapped_col_idx} ${header[unmapped_col_idx]} to ${nl[unmapped_col_idx]}");
          result.unmapped.add([header[unmapped_col_idx], nl[unmapped_col_idx]]);
        }
      }

      log.debug(result)
      results<<result;
      nl=csv.readNext()
    }

    log.debug("\n\n Convert to KBart completed cleanly");
    results
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");

    // Get the current session.
    def session = sessionFactory.currentSession

    // flush and clear the session.
    session.flush()
    session.clear()

    // Clear the property instance map.
    propertyInstanceMap.get().clear()
  }

  def makeBadFile() {
    def depositToken = java.util.UUID.randomUUID().toString();
    def baseUploadDir = grailsApplication.config.baseUploadDir ?: '.'
    def sub1 = deposit_token.substring(0,2);
    def sub2 = deposit_token.substring(2,4);
    validateUploadDir("${baseUploadDir}");
    validateUploadDir("${baseUploadDir}/${sub1}");
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}");
    def bad_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}";
    log.debug("makeBadFile... ${bad_file_name}");
    def bad_file = new File(bad_file_name);
    bad_file
  }

  private def validateUploadDir(path) {
    File f = new File(path);
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }

  def validateRow(rownum, badrows, row_data) {
    log.debug("Validate :: ${row_data}");
    def result = true
    def reasons = []
   
    // check the_kbart.date_first_issue_online is present and validates
    if ( row_data.date_first_issue_online != null ) {
      def parsed_start_date = parseDate(row_data.date_first_issue_online)
      if ( parsed_start_date == null ) {
        reasons.add("Row ${rownum} contains an invalid or unrecognised date format for date_first_issue_online :: ${row_data.date_first_issue_online}");
        result=false
      }
      else {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(parsed_start_date);
        Calendar rightNow = Calendar.getInstance();
        def this_year = rightNow.get(Calendar.YEAR)
        if ( calendar.get(Calendar.YEAR) > this_year+2 ) {  // Allow for some distance into the future.
          reasons.add("Row ${rownum} contains a suspect date/year for date_first_issue_online :: ${row_data.date_first_issue_online}");
          result=false
        }
      }
    }

    if ( !result ) {
      log.error("Recording bad row : ${reasons}");
      badrows.add([rowdata:row_data, reasons: reasons]);
    }

    return result
  }


  // Preflight works through a file adding and verifying titles and platforms, and posing questions which need to be resolved
  // before the ingest proper.
  def preflight( kbart_beans, ingest_cfg ) {
    log.debug("preflight");
   
    def result = [:]
    result.problems = []
    result.passed = true;
    
    // Iterate through -- create titles
    kbart_beans.each { the_kbart ->

      TitleInstance.withNewSession {
        
        def identifiers = []
  
        if ( the_kbart.online_identifier )
          identifiers << [type:ingest_cfg.identifierMap.online_identifier, value:the_kbart.online_identifier]
  
        if ( the_kbart.print_identifier )
          identifiers << [type:ingest_cfg.identifierMap.print_identifier, value:the_kbart.print_identifier]
  
        the_kbart.additional_isbns.each { identifier ->
          identifiers << [type: 'isbn', value:identifier]
        }
  
        if ( ( the_kbart.title_id ) && ( the_kbart.title_id ) ) {
          log.debug("title_id ${the_kbart.title_id}");
          if ( ingest_cfg.providerIdentifierNamespace ) {
            identifiers << [type:ingest_cfg.providerIdentifierNamespace, value:the_kbart.title_id]
          }
          else {
            identifiers << [type:'title_id', value:the_kbart.title_id]
          }
        }
  
        log.debug("Preflight ${the_kbart.publication_title} ${identifiers}");
  
        if ( identifiers.size() > 0 ) {
          try {
            def title = lookupOrCreateTitle(the_kbart.publication_title, identifiers, ingest_cfg)
            log.debug("Preflight title : ${title}");
          }
          catch ( InconsistentTitleIdentifierException itie ) {
            log.debug("Caught -- set passed to false",itie);
            result.passed = false;
            result.problems.add (
              [
                // itie.title itie.identifiers itie.matched_title_id itie.matched_title
                problem:itie.message,
                when: "provider='' && the_kbart.publication_title=='${itie.proposed_title}' && identifier_value=='${itie.identifiers}'",
                options:[
                  [
                    // It's something new, and optionally here's its real identifier
                    description:'The Identifier is for a later title in the title history group -- create a new title',
                    then: "createNewTitle('${the_kbart.publication_title}',${itie.matched_title_id})",
                    // Really we need to describe a form that will collect the parameters we need in order to do the right thing
                    // in this case.
                  ], 
                  [
                    // It's the same
                    description:'The Title really is a variant of the identified title - add it to the list of known variants',
                    then: "addVariant('${the_kbart.publication_title}',${itie.matched_title_id})"
                  ]
                ]
              ])
          }
        }
      }
    }

    log.debug("preflight returning ${result.passed}");
    result
  }
}
