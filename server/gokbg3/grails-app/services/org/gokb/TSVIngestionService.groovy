package org.gokb

import java.util.Map;
import java.util.Set;
import java.util.GregorianCalendar;

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.bean.CsvToBean
import au.com.bytecode.opencsv.bean.HeaderColumnNameMappingStrategy
import au.com.bytecode.opencsv.bean.HeaderColumnNameTranslateMappingStrategy
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId

import com.k_int.ConcurrencyManagerService;
import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ClassUtils

import grails.gorm.transactions.Transactional

import org.gokb.cred.TitleInstance
// Only in ebooks branch -- import org.gokb.cred.BookInstance
import org.gokb.cred.ComponentPerson
import org.gokb.cred.ComponentSubject
import org.gokb.cred.IngestionProfile
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.KBComponent
import org.gokb.cred.ComponentHistoryEvent
import org.gokb.cred.ComponentHistoryEventParticipant
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
import org.gokb.cred.Combo;
import org.gokb.cred.TitleInstancePackagePlatform;
import org.gokb.cred.User;
import org.gokb.cred.DataFile;
import org.gokb.cred.IngestionProfile;
import org.gokb.cred.CuratoryGroup;
import org.gokb.exceptions.*;
import com.k_int.TextUtils
import grails.converters.JSON
import org.apache.commons.io.ByteOrderMark

@Transactional
class TSVIngestionService {

  def grailsApplication
  def componentLookupService
  def componentUpdateService
  def reviewRequestService
  def titleLookupService
  def refdataCategory
  def sessionFactory

  def possible_date_formats = [
    new SimpleDateFormat('yyyy-MM-dd'), // Default format Owen is pushing ATM.
    new SimpleDateFormat('yyyy/MM/dd'),
    new SimpleDateFormat('dd/MM/yyyy'),
    new SimpleDateFormat('dd/MM/yy'),
    new SimpleDateFormat('yyyy/MM'),
    new SimpleDateFormat('yyyy')
  ];

  /**
   * Define package level properties.
   * Currently only defines one type of property - typeValueFunction where the package will provide
   * a setX(Y,V), getX(Y,V) method. In the price example below, a column like pkg.price.list would result
   * in a call to getPrice('list') and if the returned value was different to the input file, call
   * setPrice('list','value'). This method may be arbitrarily complex. In the price example, multiple
   * associated tracking events can happen.
   *
   * Structure of map a regex for matching, a type and a property.
   */
  static def packageProperties = [
    [ regex: ~/(pkg)\.(price)(\.(.*))?/, type:'typeValueFunction', prop:'Price' ],  // Match pkg.price and pkg.price.anything
    [ regex: ~/(pkg)\.(descriptionURL)/, type:'simpleProperty', prop:'descriptionURL']
  ]

  // Don't update the accessStartDate if we are seeing the tipp again in a file
  // already loaded.
  def tipp_properties_to_ignore_when_updating = [ 'accessStartDate' ]



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
      if (id_def.type && id_def.value && ( id_def.value.trim().length() > 0 ) ) {
        // log.debug("id_def.type")

        def id_value = id_def.value
        // id_def is map with keys 'type' and 'value'
        if ( grailsApplication.config.identifiers.formatters[id_def.type] ) {
          // If we have a formatter for this kind of identifier, call it here.
          id_value = grailsApplication.config.identifiers.formatters[id_def.type](id_value)
        }

        Identifier the_id = componentLookupService.lookupOrCreateCanonicalIdentifier(id_def.type, id_value)
        // Add the id.

        result['ids'] << the_id
        // log.debug("class_one_match ids ${result['ids']}")
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
              result['matches'] << (dproxied)
            }
            else {
              log.warn("Matched component ${dproxied.class.name}:${dproxied} but not added to matches because it's not a title");
            }
          }

          // Did the ID yield a Title match?
          // log.debug("title match for ${id_def.value} : ${title_match}")

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
                // log.debug ("Cross checking for ${id_def.type} in ${other_ns.join(", ")}")
                Identifier xc_id = null
                for (int j=0; j<other_ns.size() && !(xc_id); j++) {
                  String ns = other_ns[j]
                  IdentifierNamespace namespace = IdentifierNamespace.findByValue(ns)
                  if (namespace) {
                    // Lookup the identifier namespace.
                    xc_id = Identifier.findByNamespaceAndValue(namespace, id_value)
                    // log.debug ("Looking up ${ns}:${id_value} returned ${xc_id}.")
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
                        result['matches'] << dproxied
                      }
                    }
                  }
                }
              }
            }
          }
          else {
            // Hurrah! we got a title match
            // log.debug ("Class one ti match against ${id_def.type}:${id_def.value}. Cross-checking")
          }
        }
      }
    }
    result
  }

  def lookupOrCreateTitle (String title,
                           def identifiers,
                           ingest_cfg,
                           row_specific_config,
                           def user = null,
                           def project = null,
                           publication_type) {
    // The TitleInstance
    TitleInstance the_title = null

    if ((title == null)||(title.trim().length()==0)) return null

    // Create the normalised title.
    String norm_title = KBComponent.generateNormname(title) ?: title

    if ( ( norm_title == null )  || ( norm_title.length() == 0 ) ) {
      throw new RuntimeException("Null normalsed title based on title ${title}, Identifiers ${identifiers}");
    }

    // Lookup any class 1 identifier matches
    def results = class_one_match (identifiers)
    // The matches.
    List< KBComponent> matches = results['matches'] as List

    // log.debug("Title matches ${matches?.size()} existing entries");
    def type = row_specific_config.defaultTypeName
    if ( publication_type && publication_type.toLowerCase() == 'monograph' ) {
      type = "BookInstance"
    }

    def new_inst_clazz = Class.forName(type)

    switch (matches.size()) {
    case 0 :
      // No match behaviour.
      // Check for presence of class one ID
      if (results['class_one']) {
        // Create the new TI.
        // the_title = new BookInstance(name:title)
        log.debug("Creating new ${type} and setting title to ${title}. identifiers: ${identifiers}, ${row_specific_config}");

        the_title = new_inst_clazz.newInstance()
        the_title.name=title
        the_title.normname=norm_title
        the_title.ids=[]
      } else {
        // No class 1s supplied we should try and find a match on the title string.
        log.debug ("No class 1 ids supplied. attempt lookup using norm_title")
        // Lookup using title string match only.

        the_title == new_inst_clazz.findByNormname(norm_title)

        if (the_title) {
          // log.debug("TI ${the_title} matched by name. Partial match")
          // Add the variant.
          def added_variant = the_title.addVariantTitle(title)
          // Raise a review request

          if(added_variant) {
            ReviewRequest.raise(
              the_title,
              "'${title}' added as a variant of '${the_title.name}'.",
              "No 1st class ID supplied but reasonable match was made on the title name.",
              user, project
              )
          }
        } else {
          // log.debug("No TI could be matched by name. New TI, flag for review.")
          // Could not match on title either.
          // Create a new TI but attach a Review request to it.
          the_title = new_inst_clazz.newInstance()
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
      // log.debug ("Title class one identifier lookup yielded a single match.")
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

      // If we made a good match on a class one identifier, but the title in the DB starts with
      // Unknown title, then this is a title whos identifier has come from loading a file of identifiers
      // we should use the title given instead.
      if ( ( matches[0] ) &&
           ( ( matches[0].name?.startsWith('Unknown Title') && ( title?.length() > 0 ) ) || ( matches[0].name == null ) ) ) {
        log.debug("${matches[0].name} is an unknown title - updating to ${title}");
        the_title = matches[0]
        the_title.name = title;
      }
      else {
        log.debug("handling a matched title ${matches[0].name} ==? ${title}");
        // Now we can examine the text of the title.
        the_title = singleTIMatch(title,
                                  norm_title,
                                  matches[0],
                                  user,
                                  project,
                                  ingest_cfg.inconsistent_title_id_behavior,
                                  identifiers,
                                  row_specific_config)
      }

      break;
    default :
      // Multiple matches.
      log.warn ("Title class one identifier lookup yielded ${matches.size()} matches. This is a bad match. Ingest should skip this row.")
      break;
    }

    // If we have a title then lets set the publisher and ids...
    if (the_title) {
      log.debug("Got title - merge any other properties");

      results['ids'].each {
        if ( ! the_title.ids.contains(it) ) {
          // We should **NOT** do this in the case where we are creating a new title because the publisher listed a title
          // in the title history group using an identifier in that group.
          log.debug("Adding id ${it}");
          the_title.ids.add(it);
        }
        else {
          log.debug("Title already contains ${it}");
        }
      }

      log.debug("Saving title");
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

    // log.debug("lookupOrCreateTitle(${title}.....) returning ${the_title?.id}");
    the_title
  }

  //for now, we can only do authors. (kbart limitation)
  def TitleInstance addPerson (person_name, role, ti, user=null, project = null) {
    if ( (person_name) && ( person_name.trim().length() > 0 ) ) {

      def norm_person_name = KBComponent.generateNormname(person_name)
      def person = org.gokb.cred.Person.findAllByNormname(norm_person_name)
      // log.debug("this was found for person: ${person}");
      switch(person.size()) {
        case 0:
          // log.debug("Person lookup yielded no matches.")
          def the_person = new Person(name:person_name, normname:norm_person_name)
            if (the_person.save(failOnError:true, flush:true)) {
            // log.debug("saved ${the_person.name}")
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

        def norm_subj_name = KBComponent.generateNormname(the_subject)
        def subject = Subject.findAllByNormname(norm_subj_name) //no alt names for subjects
        // log.debug("this was found for subject: ${subject}")
        if (!subject) {
          // log.debug("subject not found, creating a new one")
          subject = new Subject(name:the_subject, normname:norm_subj_name)
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

    if ( ( publisher_name != null ) &&
         ( publisher_name.trim().length() > 0 ) ) {

      log.debug("Add publisher \"${publisher_name}\"")
      Org publisher = Org.findByName(publisher_name)
      def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      def norm_pub_name = Org.generateNormname(publisher_name);

      if ( !publisher ) {
        // Lookup using norm name.

        log.debug("Using normname \"${norm_pub_name}\" for lookup")
        publisher = Org.findByNormname(norm_pub_name)
      }

      if ( !publisher || publisher.status == status_deleted) {
        def variant_normname = GOKbTextUtils.normaliseString(publisher_name)
        def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status <> ?",[variant_normname, status_deleted]);
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

  /*
   *  Given 2 title strings, see if they might be the same. Used to detect title variants.
   */
  private TitleInstance singleTIMatch(String title,
                                      String norm_title,
                                      TitleInstance ti,
                                      User user,
                                      project = null,
                                      inconsistent_title_id_behaviour = 'add_as_variant',
                                      identifiers,
                                      row_specific_config) {

    // The threshold for a good match.
    double threshold = grailsApplication.config.cosine.good_threshold

    // Work out the distance between the 2 title strings.
    double distance = 0;

    // Don't f-about if the title exactly matches the one from the DB we are all-systems-go.
    if ( ti.getName().equalsIgnoreCase(title) ) {
      distance  = 1
    }
    else {
      // Otherwise -- work out if they are roughly close enough to warrant a good matcg
      // log.debug("Comparing ${ti.getName()} and ${norm_title}");
      distance = GOKbTextUtils.cosineSimilarity(KBComponent.generateNormname(ti.getName()), norm_title) ?: 0
    }

    def short_dist = null

    if ( distance < threshold && title.contains(': ') ) {
      def shortened_norm = KBComponent.generateNormname(title.split(':')[0])
      short_dist = GOKbTextUtils.cosineSimilarity(KBComponent.generateNormname(ti.getName()), shortened_norm) ?: 0

      if( short_dist > distance ) {
        distance = short_dist
      }
    }

    // Check the distance.

    def result = ti;

    log.debug("distance: ${distance} (threshold) ${threshold}");

    switch (distance) {
      case 1 :
        // Do nothing just continue using the TI.
        log.debug("Exact distance match for TI.")
        break

      // Try for exact match on variant name
      case {
        ti.variantNames.find {alt ->
          log.debug("Comparing ${alt.variantName} and ${norm_title}");
          GOKbTextUtils.cosineSimilarity(KBComponent.generateNormname(alt.variantName), norm_title) >= threshold
        }}:
        // Good match on existing variant titles
        // log.debug("Good match for TI on variant.")
        break

      case {it >= threshold} :
        // Good match. Need to add as alternate name.
        log.debug("Good distance match for TI. Add as variant.")
        def added_variant = ti.addVariantTitle(title)
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
            def added_variant = ti.addVariantTitle(title)
            // Raise a review request
            if(added_variant) {
              ReviewRequest.raise(
                ti,
                "'${title}' added as a variant of '${ti.name}'.",
                "Match was made on 1st class identifier but title name seems to be very different.",
                user, project
                )
            }
          }
          else if ( inconsistent_title_id_behaviour == 'reject' ) {
            throw new InconsistentTitleIdentifierException("New title \"${title}\" matched via its identifiers ${identifiers} against title with internal ID [${ti.id}] but that title string is \"${ti.name}\". Radically different titles with the same identifier are usually different titles in the same title history group when the publisher has elected not to discover the correct identifier for a preceeding or succeeding item.", title, identifiers, ti.id, ti.name)
          }
          else if ( inconsistent_title_id_behaviour == 'AddToTitleHistory' ) {
            log.debug("Creating title entry for history");
            // See if we can find the title by normalised name
            result = TitleInstance.findByNormname(norm_title)
            if ( result == null ) {
              def new_ti_clazz = Class.forName(row_specific_config.defaultTypeName);
              result = new_ti_clazz.newInstance()
              result.name=title;
              result.save(flush:true, failOnError:true)
            }
            def he = new ComponentHistoryEvent(eventDate:new Date()).save(flush:true, failOnError:true);
            def hep1 = new ComponentHistoryEventParticipant(event:he,participant:result,role:'in').save(flush:true, failOnError:true);
            def hep2 = new ComponentHistoryEventParticipant(event:he,participant:ti,role:'out').save(flush:true, failOnError:true);
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
             ingest_cfg=null,
             user=null) {

    if ( the_profile_id == null ) {
      log.error("No datafile ID passed in to ingest")
      return
    }

    def the_profile = IngestionProfile.get(the_profile_id)

    if ( the_profile == null )      {
      log.error("Unable to datafile for ID ${datafile_id}")
      return
    }

    return ingest2(the_profile.packageType,
                   the_profile.packageName,
                   the_profile.platformUrl,
                   the_profile.source,
                   datafile_id,
                   job,
                   null,
                   the_profile.providerNamespace,
                   ip_id,
                   ingest_cfg,
                   'N',
                   ['curatoryGroup':'Local'],
                   user)
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
             ingest_cfg=null,
             incremental=null,
             other_params = null,
             user=null) {

    log.debug("ingest2...");
    def result = [:]
    result.messages = []

    long start_time = System.currentTimeMillis();

    // Read does no dirty checking
    log.debug("Get Datafile ${datafile_id}");
    def datafile = DataFile.read(datafile_id)
    log.debug("Got Datafile");
    def src_id = source.id
    def user_id = user.id

    def kbart_cfg = grailsApplication.config.kbart2.mappings[packageType?.value.toString()]
    log.debug("Looking up config for ${packageType} ${packageType?.class.name} : ${kbart_cfg ? 'Found' : 'Not Found'}");

    if ( packageType.value.equals('kbart2') ) {
      log.debug("Processing as kbart2");
    }
    else if ( kbart_cfg == null ) {
      throw new RuntimeException("Unable to locate config information for package type ${packageType}. Registered types are ${grailsApplication.config.kbart2.mappings.keySet()}");
    }

    if ( ingest_cfg == null ) {
      ingest_cfg = [
        defaultTypeName: kbart_cfg?.defaultTypeName ?: 'org.gokb.cred.JournalInstance',
        identifierMap: kbart_cfg?.identifierMap ?: [ 'print_identifier':'issn', 'online_identifier':'eissn', ],
        defaultMedium: kbart_cfg?.defaultMedium ?: 'Journal',
        providerIdentifierNamespace:providerIdentifierNamespace?.value,
        inconsistent_title_id_behavior:'reject',
        quoteChar:'"',
        discriminatorColumn: kbart_cfg?.discriminatorColumn ?: 'publication_type',
        discriminatorFunction: kbart_cfg?.discriminatorFunction,
        polymorphicRows:kbart_cfg?.polymorphicRows
      ]

      if (!ingest_cfg.polymorphicRows) {
        ingest_cfg.polymorphicRows = [
          'Serial':[
            identifierMap:[ 'print_identifier':'issn', 'online_identifier':'eissn' ],
            defaultMedium:'Serial',
            defaultTypeName:'org.gokb.cred.JournalInstance'
          ],
          'Monograph':[
            identifierMap:[ 'print_identifier':'pisbn', 'online_identifier':'isbn' ],
            defaultMedium:'Book',
            defaultTypeName:'org.gokb.cred.BookInstance'
          ]
        ]
      }
    }

    try {
      log.debug("Initialise start time");

      def ingest_systime = start_time
      def ingest_date = new java.sql.Timestamp(start_time);

      log.debug("Set progress");
      job?.setProgress(0)

      def kbart_beans=[]
      def badrows=[]

      log.debug("Reading datafile");
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

      log.debug("Starting preflight");

      def preflight_result = preflight( kbart_beans, ingest_cfg, source, packageName, providerName )
      if ( preflight_result.passed ) {

        log.debug("Passed preflight -- ingest");

        Package.withNewTransaction() {
          the_package=handlePackage(packageName,source,providerName,other_params)
          assert the_package != null
          the_package_id=the_package.id
          def author_role = RefdataCategory.lookupOrCreate(grailsApplication.config.kbart2.personCategory, grailsApplication.config.kbart2.authorRole)
          author_role_id = author_role.id
          def editor_role = RefdataCategory.lookupOrCreate(grailsApplication.config.kbart2.personCategory, grailsApplication.config.kbart2.editorRole)
          editor_role_id = editor_role.id


          if ( other_params.curatoryGroup ) {
            the_package.addCuratoryGroupIfNotPresent(other_params.curatoryGroup)
            the_package.save(flush:true, failOnError:true);
          }
          else if ( grailsApplication.config.gokb?.defaultCuratoryGroup ) {
            the_package.addCuratoryGroupIfNotPresent(grailsApplication.config.gokb?.defaultCuratoryGroup)
            the_package.save(flush:true, failOnError:true);
          }
        }


        long startTime=System.currentTimeMillis()

        log.debug("Ingesting ${ingest_cfg.defaultMedium} ${kbart_beans.size}(cfg:${packageType?.value.toString()}) rows. Package is ${the_package_id}")
        //now its converted, ingest it into the database.

        for (int x=0; x<kbart_beans.size;x++) {

          Package.withNewTransaction {

            def author_role = RefdataValue.get(author_role_id)
            def editor_role = RefdataValue.get(editor_role_id)
            def pkg_src = org.gokb.cred.Source.get(src_id)
            def pkg_obj = Package.get(the_package_id)

            log.debug("**Ingesting ${x} of ${kbart_beans.size} ${kbart_beans[x]}")

            def row_specific_cfg = getRowSpecificCfg(ingest_cfg,kbart_beans[x]);

            long rowStartTime=System.currentTimeMillis()

            if ( validateRow(x, badrows, kbart_beans[x] ) ) {
              writeToDB(kbart_beans[x],
                        platformUrl,
                        pkg_src,
                        ingest_date,
                        ingest_systime,
                        author_role,
                        editor_role,
                        pkg_obj,
                        ingest_cfg,
                        badrows,
                        row_specific_cfg,
                        user_id)
            }

            log.debug("ROW ELAPSED : ${System.currentTimeMillis()-rowStartTime}");
          }

          job?.setProgress( x , kbart_beans.size() )

          if ( x % 25 == 0 ) {
            cleanUpGorm()
          }
        }


        if ( incremental=='Y' ) {
          log.debug("Incremental -- no expunge");
        }
        else {
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
        }

        if ( badrows.size() > 0 ) {
          def msg = "There are ${badrows.size()} bad rows -- write to badfile and report"
          job.message([timestam:System.currentTimeMillis(), message:msg, event:'BadRows', count:badrows.size()])
          badrows.each {
            job.message(it)
          }
        }

        long processing_elapsed = System.currentTimeMillis()-startTime
        def average_milliseconds_per_row = kbart_beans.size() > 0 ? processing_elapsed.intdiv(kbart_beans.size()) : 0;
        // 3600 seconds in an hour, * 1000ms in a second
        def average_per_hour = average_milliseconds_per_row > 0 ? 3600000.intdiv( average_milliseconds_per_row ) : 0;
        job.message([
                             timestamp:System.currentTimeMillis(),
                             event:'ProcessingComplete',
                             message:"Processing Complete : numRows:${kbart_beans.size()}, avgPerRow:${average_milliseconds_per_row}, avgPerHour:${average_per_hour}",
                             numRows:kbart_beans.size(),
                             averagePerRow:average_milliseconds_per_row,
                             averagePerHour:average_per_hour,
                             elapsed:processing_elapsed
                            ]);


        Package.withNewTransaction {
          try {
            def update_agent = User.findByUsername('IngestAgent')
            // insertBenchmark updateBenchmark
            def p = Package.lock(the_package_id)
            if ( p.insertBenchmark == null )
              p.insertBenchmark=processing_elapsed;
            p.lastUpdateComment="Direct ingest of file:${datafile.name}[${datafile.id}] completed in ${processing_elapsed}ms, avg per row=${average_milliseconds_per_row}, avg per hour=${average_per_hour}"
            p.lastUpdatedBy=update_agent
            p.updateBenchmark=processing_elapsed
            p.save(flush:true, failOnError:true)
          }
          catch ( Exception e ) {
            log.warn("Problem updating package stats",e);
          }
        }
      }
      else {

        preflight_result.source = src_id

        // Preflight failed
        job.message("Failed Preflight");

        // Raise a review request against the datafile
        def preflight_json = preflight_result as JSON
        DataFile.withNewTransaction {
          def writeable_datafile = DataFile.get(datafile.id)
          ReviewRequest req = new ReviewRequest (
              status	: RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open'),
              raisedBy : user,
              allocatedTo : user,
              descriptionOfCause : "Ingest of datafile ${datafile.id} / ${datafile.name} failed preflight",
              reviewRequest : "Generate rules to handle error cases.",
              refineProject: null,
              additionalInfo: preflight_json.toString(),
              componentToReview:writeable_datafile
              ).save(flush:true, failOnError:true)

          job.message([timestamp:System.currentTimeMillis(),event:'FailedPreflight',message:"Failed Preflight, see review request ${req.id}"]);
        }
      }

    }
    catch ( Exception e ) {
      job.message(e.toString());
      log.error("Problem",e)
    }

    job?.setProgress(100)


    def elapsed = System.currentTimeMillis()-start_time;

    job.message("Ingest completed after ${elapsed}ms");

    job.message("Ingest2 returning ${result}")
    result
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
                badrows,
                row_specific_config,
                user_id) {

    //simplest method is to assume that everything is new.
    //however the golden rule is to check that something already exists and then
    //re-use it.
    log.debug("TSVINgestionService:writeToDB -- package id is ${the_package.id}")

    //first we need a platform:
    def platform = null; // handlePlatform(platform_url.host, source)
    def user = User.get(user_id)

    log.debug("default platform via default platform URL ${platform_url}, ${platform_url?.class?.name} ${platform_url} title_url:${the_kbart.title_url}")

    if ( the_kbart.title_url != null ) {

      log.debug("Extract host from ${the_kbart.title_url}");

      def title_url_host = null
      def title_url_protocol = null

      try {
        def title_url = new URL(the_kbart.title_url)
        log.debug("Parsed title_url : ${title_url}");
        title_url_host = title_url.getHost()
        title_url_protocol = title_url.getProtocol()
      }
      catch ( Exception e ) {
      }

      if ( title_url_host ) {
        log.debug("Got platform from title host :: ${title_url_host}")
        platform = handlePlatform(title_url_host, title_url_protocol, source)
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
        if ( ( the_kbart.online_identifier ) && ( the_kbart.online_identifier.trim().length() > 0 ) )
          identifiers << [type:row_specific_config.identifierMap.online_identifier, value:the_kbart.online_identifier]

        if ( the_kbart.print_identifier && ( the_kbart.print_identifier.trim().length() > 0 ) )
          identifiers << [type:row_specific_config.identifierMap.print_identifier, value:the_kbart.print_identifier]

        the_kbart.additional_isbns.each { identifier ->
          if ( identifier.trim().length() > 0 ) {
            identifiers << [type: 'isbn', value:identifier]
          }
        }

        if ( ( the_kbart.title_id ) && ( the_kbart.title_id.trim().length() > 0 ) ) {
          log.debug("title_id ${the_kbart.title_id}");
          if ( ingest_cfg.providerIdentifierNamespace ) {
            identifiers << [type:ingest_cfg.providerIdentifierNamespace.value, value:the_kbart.title_id]
          }
          else {
            identifiers << [type:'title_id', value:the_kbart.title_id]
          }
        }

        the_kbart.each { k, v ->
          if (k.startsWith('identifier_')) {
            def ns_val = k.split('_', 2)[1]
            log.debug("Found potential additional namespace ${ns_val}")
            if (IdentifierNamespace.findByValue(ns_val)) {
              identifiers << [type: ns_val, value:v]
            }
            else {
              log.debug("Unknown additional identifier namespace ${ns_val}!")
            }
          }
        }

        if ( identifiers.size() > 0 ) {
          def title = titleLookupService.findOrCreate(the_kbart.publication_title, the_kbart.publisher_name, identifiers, user, null, row_specific_config.defaultTypeName)

          if ( title ) {

            log.debug("title found: for ${the_kbart.publication_title}:${title}")

            if (title) {
              def sync_obj = [
                'name': the_kbart.publication_title,
                'identifiers': identifiers
              ]

              componentUpdateService.ensureCoreData(title, sync_obj, false, user)

              // The title.save s are necessary as adding to the combos collection dirties the title object
              // These should be rewritten to manually create combo objects instead.

              log.debug("addOtherFieldsToTitle");
              addOtherFieldsToTitle(title, the_kbart, ingest_cfg)

              log.debug("Adding publisher");
              if ( the_kbart.publisher_name && the_kbart.publisher_name.length() > 0 ) {
                addPublisher(the_kbart.publisher_name, title)
              }

              log.debug("Adding first author");
              if ( the_kbart.first_author && the_kbart.first_author.trim().length() > 0 ) {
                addPerson(the_kbart.first_author, author_role, title);
              }

              log.debug("Adding Person");
              if ( the_kbart.first_editor && the_kbart.first_editor.trim().length() > 0 ) {
                addPerson(the_kbart.first_editor, editor_role, title);
              }

              log.debug("Adding subjects");
              addSubjects(the_kbart.subjects, title)

              log.debug("Adding additional authors");
              the_kbart.additional_authors.each { author ->
                addPerson(author, author_role, title)
              }

              title.source=source
              title.save(flush:true, failOnError:true);

              def pre_create_tipp_time = System.currentTimeMillis();
              manualUpsertTIPP(source,
                               the_kbart,
                               the_package,
                               title,
                               platform,
                               ingest_date,
                               ingest_systime,
                               identifiers)

            } else {
               log.warn("problem getting the title...")
            }
          }
          else {
            badrows.add([rowdata:the_kbart, message: 'Unable to lookup or create title']);
          }
        }
        else {
          log.debug("Skipping row - no identifiers")
          badrows.add([rowdata:the_kbart,message: 'No usable identifiers']);
        }

    } else {
      log.warn("couldn't reslove platform - title not added.");
    }
  }

  def addOtherFieldsToTitle(title, the_kbart, ingest_cfg) {
    title.medium=RefdataCategory.lookupOrCreate("TitleInstance.Medium", ingest_cfg.defaultMedium ?: "eBook")
    // title.editionNumber=the_kbart.monograph_edition
    // title.dateFirstInPrint=parseDate(the_kbart.date_monograph_published_print)
    // title.dateFirstOnline=parseDate(the_kbart.date_monograph_published_online)
    // title.volumeNumber=the_kbart.monograph_volume
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

  def manualUpsertTIPP(the_source,
                       the_kbart,
                       the_package,
                       the_title,
                       the_platform,
                       ingest_date,
                       ingest_systime,
                       identifiers) {

    log.debug("TSVIngestionService::manualUpsertTIPP with pkg:${the_package}, ti:${the_title}, plat:${the_platform}, date:${ingest_date}")

    assert the_package != null && the_title != null && the_platform != null

    def tipp_values = [
      url:the_kbart.title_url?:'',
      embargo:the_kbart.embargo_info?:'',
      coverageDepth:the_kbart.coverageDepth?:'',
      coverageNote:the_kbart.coverage_note?:'',
      startDate:the_kbart.date_first_issue_online,
      startVolume:the_kbart.num_first_vol_online,
      startIssue:the_kbart.num_first_issue_online,
      endDate:the_kbart.date_last_issue_online,
      endVolume:the_kbart.num_last_vol_online,
      endIssue:the_kbart.num_last_issue_online,
      source:the_source,
      accessStartDate:ingest_date,
      lastSeen:ingest_systime
    ]

    def tipp = null;

    log.debug("Lookup existing TIPP");
    def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
    def status_retired = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Retired')
    def tipps = TitleInstance.executeQuery('select tipp from TitleInstancePackagePlatform as tipp, Combo as pkg_combo, Combo as title_combo, Combo as platform_combo  '+
                                           'where pkg_combo.toComponent=tipp and pkg_combo.fromComponent=? '+
                                           'and platform_combo.toComponent=tipp and platform_combo.fromComponent = ? '+
                                           'and title_combo.toComponent=tipp and title_combo.fromComponent = ? ',
                                          [the_package,the_platform,the_title])
    if ( tipps.size() > 0 ) {
      switch (tipps.size()) {
        case 1:
          log.debug("found");

          if (the_kbart.title_url && the_kbart.title_url.size() > 0) {
            if (!tipps[0].url || tipps[0].url == trimmed_url) {
              tipp = tipps[0]
            } else {
              log.debug("matched tipp has a different url..")
            }
          } else {
            tipp = tipps[0]
          }
          break;
        case 0:
          log.debug("not found");

          break;
        default:
          if (the_kbart.title_url && the_kbart.title_url.size() > 0) {
            tipps = tipps.findAll { !it.url || it.url == trimmed_url };
            log.debug("found ${tipps.size()} tipps for URL ${trimmed_url}")
          }

          def cur_tipps = tipps.findAll { it.status == status_current };
          def ret_tipps = tipps.findAll { it.status == status_retired };

          if (cur_tipps.size() > 0) {
            tipp = cur_tipps[0]

            log.warn("found ${cur_tipps.size()} current TIPPs!")
          } else if (ret_tipps.size() > 0) {
            tipp = ret_tipps[0]

            log.warn("found ${ret_tipps.size()} retired TIPPs!")
          } else {
            log.debug("None of the matched TIPPs are 'Current' or 'Retired'!")
          }
          break;
      }
    }


    if (tipp==null) {
      log.debug("create a new tipp as at ${ingest_date}");

      // These are immutable for a TIPP - only set at creation time
      // We are going to create tipl objects at the end instead if per title inline.
      // tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_values)

      // Copy the new tipp_values from the file into our new object
      def tipp_fields = [
        pkg: the_package,
        title: the_title,
        hostPlatform: the_platform,
        url: the_kbart.title_url,
        name: the_kbart.publication_title
      ]

      tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_fields)
    }

    Set<String> ids = tipp.ids.collect { "${it.namespace?.value}|${it.value}".toString() }
    RefdataValue combo_active = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_ACTIVE)
    RefdataValue combo_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)
    RefdataValue combo_type_id = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')

    identifiers.each { ci ->
      def namespace_val = ci.type ?: ci.namespace
      String testKey = "${ci.type}|${ci.value}".toString()

      if (namespace_val && ci.value && ci.type.toLowerCase() != "originediturl") {

        if (!ids.contains(testKey)) {
          def canonical_identifier = componentLookupService.lookupOrCreateCanonicalIdentifier(namespace_val, ci.value)

          log.debug("Checking identifiers of component ${tipp.id}")
          if (canonical_identifier) {
            def duplicate = Combo.executeQuery("from Combo as c where c.toComponent = ? and c.fromComponent = ?", [canonical_identifier, tipp])

            if (duplicate.size() == 0) {
              log.debug("adding identifier(${namespace_val},${ci.value})(${canonical_identifier.id})")
              def new_id = new Combo(fromComponent: tipp, toComponent: canonical_identifier, status: combo_active, type: combo_type_id).save(flush: true, failOnError: true)
            } else if (duplicate.size() == 1 && duplicate[0].status == combo_deleted) {

              log.debug("Found a deleted identifier combo for ${canonical_identifier.value} -> ${component}")
              reviewRequestService.raise(
                component,
                "Review ID status.",
                "Identifier ${canonical_identifier} was previously connected to '${component}', but has since been manually removed.",
                user,
                null,
                null,
                RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Removed Identifier')
              )
            } else {
              log.debug("Identifier combo is already present, probably via titleLookupService.")
            }

            // Add the value for comparison.
            ids << testKey
          } else {
            log.debug("Could not find or create Identifier!")
          }
        }
      }
    }

    def parsedStart = GOKbTextUtils.completeDateString(tipp_values.startDate)
    def parsedEnd = GOKbTextUtils.completeDateString(tipp_values.endDate, false)

    def cs_match = false
    def conflict = false
    def startAsDate = (parsedStart ? Date.from( parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
    def endAsDate = (parsedEnd ? Date.from( parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)
    def conflicting_statements = []

    tipp.coverageStatements?.each { tcs ->
      if ( !cs_match ) {
        if (!tcs.endDate && !endAsDate) {
          conflict = true
        }
        else if (tcs.startVolume && tcs.startVolume == tipp_values.startVolume) {
          log.debug("Matched CoverageStatement by startVolume")
          cs_match = true
        }
        else if (tcs.startDate && tcs.startDate == startAsDate) {
          log.debug("Matched CoverageStatement by startDate")
          cs_match = true
        }
        else if (!tcs.startVolume && !tcs.startDate && !tcs.endVolume && !tcs.endDate) {
          log.debug("Matched CoverageStatement with unspecified values")
          cs_match = true
        }
        else if (tcs.startDate && tcs.endDate) {
          if (startAsDate && startAsDate > tcs.startDate && startAsDate < tcs.endDate) {
            conflict = true
          }
          else if (endAsDate && endAsDate > tcs.startDate && endAsDate < tcs.endDate) {
            conflict = true
          }
        }

        if (conflict) {
          conflicting_statements.add(tcs)
        }
        else if (cs_match) {
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startIssue', tipp_values.startIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startVolume', tipp_values.startVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endVolume', tipp_values.endVolume)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endIssue', tipp_values.endIssue)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'embargo', tipp_values.embargo)
          changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'coverageNote', tipp_values.coverageNote)
          changed |= com.k_int.ClassUtils.setDateIfPresent(parsedStart,tcs,'startDate')
          changed |= com.k_int.ClassUtils.setDateIfPresent(parsedEnd,tcs,'endDate')
          changed |= com.k_int.ClassUtils.setRefdataIfPresent(tipp_values.coverageDepth, tipp, 'coverageDepth', 'TIPPCoverageStatement.CoverageDepth')
        }
      }
      else {
        log.warn("Multiple coverage statements matched!")
      }
    }

    for (def cst : conflicting_statements) {
      tipp.removeFromCoverageStatements(cst)
    }

    if (!cs_match) {

      def cov_depth = null

      if (tipp_values.coverageDepth instanceof String) {
        cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', tipp_values.coverageDepth) ?: RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', "Fulltext")
      } else if (tipp_values.coverageDepth instanceof Integer) {
        cov_depth = RefdataValue.get(c.coverageDepth)
      } else if (tipp_values.coverageDepth instanceof Map) {
        if (tipp_values.coverageDepth.id) {
          cov_depth = RefdataValue.get(tipp_values.coverageDepth.id)
        } else {
          cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', (tipp_values.coverageDepth.name ?: tipp_values.coverageDepth.value))
        }
      }

      def new_tcs = [
        'startVolume': tipp_values.startVolume,
        'startIssue': tipp_values.startIssue,
        'endVolume': tipp_values.endVolume,
        'endIssue': tipp_values.endIssue,
        'embargo': tipp_values.embargo,
        'coverageDepth': cov_depth,
        'coverageNote': tipp_values.coverageNote,
        'startDate': startAsDate,
        'endDate': endAsDate
      ]

      tipp.addToCoverageStatements(new_tcs)
    }

    // log.debug("Values updated, set lastSeen");

    if ( ingest_systime ) {
      // log.debug("Update last seen on tipp ${tipp.id} - set to ${ingest_date}")
      tipp.lastSeen = ingest_systime;
    }

    // Allow columns like tipp.price, tipp.price.list, tipp.price.perpetual - Call the setPrice(type, value) for each
    setTypedProperties(tipp, the_kbart.unmapped, 'Price',  ~/(tipp)\.(price)(\.(.*))?/, 'currency');

    // Look through the field list for any tipp.custprop values
    log.debug("Checking for tipp custprops");
    addCustprops(tipp, the_kbart, 'tipp.custprops.');
    addUnmappedCustprops(tipp, the_kbart.unmapped, 'tipp.custprops.');

    log.debug("manualUpsertTIPP returning")
  }

  def setTypedProperties(tipp, props, field, regex, type) {
    log.debug("setTypedProperties(...${field},...)");

    props.each { up ->
      def prop = up.name
      if ( ( prop ==~ regex ) && ( up.value.trim().length() > 0 ) ) {
        def propname_groups = prop =~ regex
        def propname = propname_groups[0][2]
        def proptype = propname_groups[0][4]

        def current_value = tipp."get${field}"(proptype)
        def value_from_file = formatValueFromFile(up.value.trim(), type);

        log.debug("setTypedProperties - match regex on ${prop},type=${proptype},value_from_file=${value_from_file} current=${current_value}");


        // If we don't currently have a value OR we have a value which is not the same as the one supplied
        if ( ( current_value == null ) ||
             ( ! current_value.equals(value_from_file) ) ) {
          log.debug("${current_value} !=  ${value_from_file} so set...");
          tipp."set${field}"(proptype, value_from_file)
        }
      }
      else {
        // log.debug("${prop} does not match regex");
      }
    }
  }

  private String formatValueFromFile(String v, String t) {
    String result = null;
    switch ( t ) {
      case 'currency':
        // "1.24", "1 GBP", "11234.43", "3334", "3334.2", "2.3 USD" -> "1.24", "1.00 GBP", "11234.43", "3334.00", "3334.20", "2.30 USD"
        String[] currency_components = v.split(' ');
        if ( currency_components.length == 2 ) {
          result = String.format('%.2f',Float.parseFloat(currency_components[0]))+' '+currency_components[1]
        }
        else {
          result = String.format('%.2f',Float.parseFloat(currency_components[0]))
        }
        break;
      default:
        result = v.trim();
    }

    return result;
  }

  //this is a lot more complex than this for journals. (which uses refine)
  //theres no notion in here of retiring packages for example.
  //for this v1, I've made this very simple - probably too simple.
  def handlePackage(packageName, source, providerName,other_params) {
    def result;
    def norm_pkg_name = KBComponent.generateNormname(packageName)
    log.debug("Attempt package match by normalised name: ${norm_pkg_name}");
    def status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')
    def packages=Package.executeQuery("select p from Package as p where p.normname=? and p.status != ?",[norm_pkg_name, status_deleted],[readonly:false])

    switch (packages.size()) {
      case 0:
        //no match. create a new package!
        log.debug("Create new package(${packageName},${norm_pkg_name})");

        def newpkgid = null;

        def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
        def newpkg = new Package(
                                 name:packageName,
                                 normname:norm_pkg_name,
                                 source:source,
                                 status: status_current,
                                 description:other_params?.description)

        if (newpkg.save(flush:true, failOnError:true)) {
          newpkgid = newpkg.id
          if ( providerName && providerName.length() > 0 ) {
            def norm_provider_name = KBComponent.generateNormname(providerName)
            def provider = null;
            def providers = org.gokb.cred.Org.findAllByNormname(norm_provider_name)
            if ( providers.size() == 0 )
              provider = new Org(name:providerName, normname:norm_provider_name).save(flush:true, failOnError:true);
            else if ( providers.size() == 1 )
              provider = providers[0]
            else
              log.error("Multiple orgs with name ${providerName}/${norm_provider_name} -- unable to set package provider");

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
        // See if any properties have changed.
        if ( !result.description == other_params.description) {
          result.description = other_params.description;
          result.save(flush:true, failOnError:true);
        }
        break
      default:
        log.error("found multiple packages when looking for ${packageName}")
        break
    }

    // The request can now have additional package level properties that we need to process.
    // other_params can contain 'pkg.' properties.
    handlePackageProperties(result, other_params)

    log.debug("handlePackage returns ${result}");
    result;
  }

  def handlePackageProperties(pkg, props) {
    def package_changed = false;
    packageProperties.each { pp ->
      // consider See if pp.regex matches any of the properties
      props.keySet().grep(pp.regex).each { prop ->
        log.debug("Property ${prop} matched config ${pp}");
        switch ( pp.type ) {
          case 'typeValueFunction':
            // The property has a subtype eg price.list which should be mapped in a special way
            def propname_groups = prop =~ pp.regex
            def propname = propname_groups[0][2]
            def proptype = propname_groups[0][4]
            log.debug("Call getter object.${propname}(${proptype}) - value is ${props[prop]}");
            // If the value returned by the getter is not the same as the value we have, update
            def current_value = pkg."get${pp.prop}"(proptype)
            log.debug("current_value of ${prop} = ${current_value}");

            // If we don't currently have a value OR we have a value which is not the same as the one supplied
            if ( ( ( current_value == null ) && ( props[prop]?.trim().length() > 0 ) ) ||
                 ( ! current_value.equals(props[prop]) ) ) {
              log.debug("${current_value} != ${props[prop]} so set ${pp.prop}");
              pkg."set${pp.prop}"(proptype, props[prop])
              package_changed = true;
            }

            break;
          case 'simpleProperty':
            // A simple scalar property
            pkg[pp.prop] = props[prop]
            break;
          default:
            log.warn("Unhandled package property type ${pp.type} : ${pp}");
            break;
        }
      }
    }

    if ( package_changed ) {
      pkg.save(flush:true, failOnError:true);
    }
  }

  def handlePlatform(host, protocol, the_source) {

    def result;
    // def platforms=Platform.findAllByPrimaryUrl(host);

    def orig_host = host

    if(host.startsWith("www.")){
      host = host.substring(4)
    }

    def platforms=Platform.executeQuery("select p from Platform as p where p.primaryUrl like :host",['host': "%" + host + "%"],[readonly:false])


    switch (platforms.size()) {
      case 0:

        //no match. create a new platform!
        log.debug("Create new platform ${host}, ${host}, ${the_source}");

          def newUrl = protocol + "://" + orig_host

          result = new Platform( name:host, primaryUrl:newUrl, source:the_source)

          // log.debug("Validate new platform");
          // result.validate();

          if ( result ) {
            if (result.save(flush:true, failOnError:true)) {
              // log.debug("saved new platform: ${result}")
            } else {
              // log.error("problem creating platform");
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

    // log.debug("handlePlatform returning ${result}");
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

    def csv = new CSVReader(new InputStreamReader(
                              new org.apache.commons.io.input.BOMInputStream(
                                new ByteArrayInputStream(the_data.fileData),
                                ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE,ByteOrderMark.UTF_8),
                            java.nio.charset.Charset.forName(charset)),'\t' as char,'\0' as char)
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
                  if ( nl[col_positions[key]].length() > 4092 ) {
                    throw new RuntimeException("Unexpectedly long value in row ${rownum} -- Probable miscoded quote in line. Correct and resubmit");
                  }
                  else{
                    result[key]=nl[col_positions[key]]
                  }
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

      log.debug("${result}")
      // log.debug(new KBartRecord(result))
      //this is a cheat cos I don't get why springer files don't work!
      // results<<new KBartRecord(result)
      results.add(result)
      nl=csv.readNext()
      rownum++
    }
    log.debug("${results}")
    results
  }

  def convertToKbart(packageType, data_file) {
    def results = []
    log.debug("in convert to Kbart2")
    log.debug("file package type is ${packageType}")

    //need to know the file type, then we need to create a new data structure for it
    //in the config, need to map the fields from the formats we support into kbart.

    def kbart_cfg = grailsApplication.config.kbart2.mappings."${packageType}"

    //can you read a tsv file?
    def charset = 'ISO-8859-1' // 'UTF-8'
    if (kbart_cfg==null) {
      throw new Exception("couldn't find config for ${packageType}")
    }
    else {
      log.debug("Got config ${kbart_cfg}");
    }

    org.apache.commons.io.input.BOMInputStream b = new org.apache.commons.io.input.BOMInputStream( new ByteArrayInputStream(data_file.fileData),
                                                                                                   ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE,ByteOrderMark.UTF_8)

    def ingest_charset = kbart_cfg.charset?:'ISO-8859-1'

    if (b.hasBOM() == false) {
      // No BOM found
    } else if (b.hasBOM(ByteOrderMark.UTF_16LE)) {
      // has a UTF-16LE BOM
      ingest_charset = 'UTF-16LE'
    } else if (b.hasBOM(ByteOrderMark.UTF_16BE)) {
      // has a UTF-16BE BOM
      ingest_charset = 'UTF-16BE'
    }

    log.debug("Convert to kbart2 using charset ${ingest_charset}");

    CSVReader csv = new CSVReader(
                      new InputStreamReader( b, java.nio.charset.Charset.forName(ingest_charset)),
                      (kbart_cfg.separator?:'\t') as char,
                      (kbart_cfg.quoteChar?:'\0') as char)

    def fileRules = kbart_cfg.rules
    Map col_positions=[:]
    fileRules.each { fileRule ->
      col_positions[fileRule.field]=-1;
    }
    String [] header = csv.readNext()

    int ctr = 0

    if ( ( header == null ) || ( header.size() ==  0) ) {
      log.error("No header");
      results.add([message:"No header"]);
      return results;
    }


    log.debug("Processing column headers... count ${header?.length} items")
    header.each {
      log.debug("Column \"${ctr}\" == ${it} (${it.class.name})");
      col_positions [ it.toString().trim() ] = ctr++
    }

    def mapped_cols = [] as ArrayList
    def unmapped_cols = 0..(header.size()) as ArrayList

    log.debug("Col positions : ${col_positions}");
    log.debug("Before Mapped columns: ${mapped_cols}");
    log.debug("Before UnMapped columns: ${unmapped_cols}");


    fileRules.each { fileRule ->
      if ( col_positions[fileRule.field] >= 0 ) {
        // Column is mapped
        unmapped_cols.remove(col_positions[fileRule.field] as Object);
        mapped_cols.add(col_positions[fileRule.field] as Object)
      }
      else {
        log.debug("Mapping contains a definition for ${fileRule.field} but unable to find a column with that name in file headings : ${header}");
      }
    }

    log.debug("After Mapped columns: ${mapped_cols}");
    log.debug("After UnMapped columns: ${unmapped_cols}");

    String [] nl = csv.readNext()

    long row_counter = 0

    while ( nl != null ) {

      log.debug("** Process row:${row_counter++} ${nl}");

      // KBartRecord result = new KBartRecord()
      def result = [:]
      result.unmapped=[]

      fileRules.each { fileRule ->

        boolean done=false
        if ( nl.length > col_positions[fileRule.field] ) {
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
              if ( data ) {
                if ( result[fileRule.additional] == null )
                  result[fileRule.additional] = []

                result[fileRule.additional] << data
              }
            } else {
              result[fileRule.kbart]=data
            }
          }
        }
        else {
          log.warn("Missing column[${col_positions[fileRule.field]}]-${fileRule.field} in ingest file at line ${row_counter}");
        }
      }


      log.debug("Processing ${unmapped_cols.size()} unmapped columns");
      unmapped_cols.each { unmapped_col_idx ->
        if ( ( unmapped_col_idx < nl.size() ) && ( unmapped_col_idx < header.size() ) ) {
          log.debug("Setting unmapped column idx ${unmapped_col_idx} ${header[unmapped_col_idx]} to ${nl[unmapped_col_idx]}");
          // result.unmapped.add([header[unmapped_col_idx], nl[unmapped_col_idx]]);
          result.unmapped.add([
                                name:header[unmapped_col_idx],
                                value:nl[unmapped_col_idx],
                                index:unmapped_col_idx
                              ]);
        }
      }

      log.debug("${result}")
      results<<result;
      nl=csv.readNext()
    }

    log.debug("\n\n Convert to KBart completed cleanly");
    results
  }

  def cleanUpGorm() {
    // log.debug("Clean up GORM");

    // Get the current session.
    def session = sessionFactory.currentSession

    // flush and clear the session.
    session.flush()
    session.clear()
  }

  def makeBadFile() {
    def depositToken = java.util.UUID.randomUUID().toString();
    def baseUploadDir = grailsApplication.config.project_dir ?: '.'
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
    if ( ( row_data.date_first_issue_online != null )  && ( row_data.date_first_issue_online.trim() != '' ) ) {
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
      badrows.add([rowdata:row_data,message: reasons]);
    }

    return result
  }

  /**
   * Sometimes a row will have a discriminator that tells us to interpret the columns in different ways. for example,
   * KBart publication_type can be Serial or Monograph -- Depeneding on which we might need to do something different like
   * treat the print identifier as an isbn or an issn. This method looks at the config and the values for the row and
   * works out what the right bit of row specific config is. Example config looks like this
   * elsevier:[
   *           quoteChar:'"',
   *           // separator:',',
   *           charset:'UTF-8',
   *           defaultTypeName:'org.gokb.cred.BookInstance',
   *           identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
   *           defaultMedium:'Book',
   *           discriminatorColumn:'publication_type',
   *           polymorphicRows:[
   *             'Serial':[
   *               identifierMap:[ 'print_identifier':'issn', 'online_identifier':'issn' ],
   *               defaultMedium:'Serial',
   *               defaultTypeName:'org.gokb.cred.TitleInstance'
   *              ],
   *             'Monograph':[
   *               identifierMap:[ 'print_identifier':'isbn', 'online_identifier':'isbn' ],
   *               defaultMedium:'Book',
   *               defaultTypeName:'org.gokb.cred.BookInstance'
   *             ]
   *           ],
   *           // doDistanceMatch=true, // To enable full string title matching
   *           rules:[
   *             [field: 'publication_title', kbart: 'publication_title'],
   *             [field: 'print_identifier', kbart: 'print_iden..........
   */
  def getRowSpecificCfg(cfg, row) {
    def result = cfg
    log.debug("getRowSpecificCfg(${cfg.polymorphicRows},${cfg.discriminatorColumn},${row[cfg.discriminatorColumn]})");
    if ( cfg.polymorphicRows && cfg.discriminatorColumn ) {
      if ( row[cfg.discriminatorColumn] ) {
        def row_specific_cfg = cfg.polymorphicRows[row[cfg.discriminatorColumn]]
        if ( row_specific_cfg ) {
          result = row_specific_cfg
        }
      }
    }
    else if ( cfg.polymorphicRows && cfg.discriminatorFunction ) {
      log.debug("calling discriminatorFunction ${row}");
      def rowtype = cfg.discriminatorFunction.call(row)
      if ( rowtype ) {
        def row_specific_cfg = cfg.polymorphicRows[rowtype]
        if ( row_specific_cfg ) {
          result = row_specific_cfg
        }
      }
      log.debug("discriminatorFunction ${rowtype}, rowConfig=${result}");
    }
    result;
  }


  // Preflight works through a file adding and verifying titles and platforms, and posing questions which need to be resolved
  // before the ingest proper. We record parameters used so that after recording any corrections we can re-process the file.
  def preflight( kbart_beans,
                 ingest_cfg,
                 source,
                 packageName,
                 providerName  ) {
    log.debug("preflight");

    def result = [:]
    result.problems = []
    result.passed = true;
    result.probcount=0
    result.packageName = packageName
    result.providerName = providerName
    result.sourceName=source?.name
    result.sourceId=source?.id

    def preflight_counter = 0;

    def source_rules = null;
    if ( source.ruleset ) {
      log.debug("read source ruleset ${source.ruleset}");
      source_rules = JSON.parse(source.ruleset)
      source_rules.rules.keySet().each {
        log.debug("Rule Fingerprint : ${it}");
      }
    }

    // Iterate through -- create titles
    kbart_beans.each { the_kbart ->

      def row_specific_cfg = getRowSpecificCfg(ingest_cfg,the_kbart);

      TitleInstance.withNewTransaction {

        def identifiers = []

        if ( ( the_kbart.online_identifier ) && ( the_kbart.online_identifier.trim().length() > 0 ) )
          identifiers << [type:row_specific_cfg.identifierMap.online_identifier, value:the_kbart.online_identifier]

        if ( the_kbart.print_identifier && ( the_kbart.print_identifier.trim().length() > 0 ) )
          identifiers << [type:row_specific_cfg.identifierMap.print_identifier, value:the_kbart.print_identifier]

        the_kbart.additional_isbns.each { identifier ->
          if ( identifier && ( identifier.trim().length() > 0 ) )
          identifiers << [type: 'isbn', value:identifier]
        }

        if ( ( the_kbart.title_id ) && ( the_kbart.title_id.trim().length() > 0  ) ) {
          log.debug("title_id ${the_kbart.title_id}");
          if ( ingest_cfg.providerIdentifierNamespace ) {
            identifiers << [type:ingest_cfg.providerIdentifierNamespace.value, value:the_kbart.title_id]
          }
          else {
            identifiers << [type:'title_id', value:the_kbart.title_id]
          }
        }

        log.debug("Preflight [${packageName}:${preflight_counter++}] title:${the_kbart.publication_title} identifiers:${identifiers}");

        if ( identifiers.size() > 0 ) {
          try {
            def title = lookupOrCreateTitle(the_kbart.publication_title, identifiers, ingest_cfg, row_specific_cfg, the_kbart.publication_type)
            if ( title && the_kbart.title_image && ( the_kbart.title_image != title.coverImage) ) {
              title.coverImage = the_kbart.title_image;
              title.save(flush:true, failOnError:true)
            }

            log.debug("Identifier match Preflight title : ${title}");
          }
          catch ( InconsistentTitleIdentifierException itie ) {
            log.debug("Caught -- set passed to false",itie);

            // First thing to do is to see if we have a rule against this source for this case - if so, apply it,
            // If not, raise the problem so that we will know what to do next time around.
            identifiers.sort{it.value};
            def identifier_fingerprint_str = identifiers as JSON
            def rule_fingerprint = "InconsistentTitleIdentifierException:${the_kbart.publication_title}:${identifier_fingerprint_str}"

            if ( source_rules && source_rules.rules[rule_fingerprint] ) {
              log.debug("Matched rule : ${source_rules.rules[rule_fingerprint]}");
              switch ( source_rules.rules[rule_fingerprint].ruleResolution ) {
                case 'variantName':
                  log.debug("handle error case as variant name");
                  // exception properties:: proposed_title identifiers matched_title_id matched_title
                  def title = TitleInstance.get(itie.matched_title_id)
                  def added_variant = title.addVariantTitle(itie.proposed_title)
                  title.save(flush:true, failOnError:true)
                  break;
                case 'newTitleInTHG':
                  log.debug("handle error case as title in title history");
                  break;
                default:
                  log.error("Unhandled rule resolution : ${source_rules[rule_fingerprint].ruleResolution}");
                  break;
              }
            }
            else {
              log.debug("No matching rule for fingerprint ${rule_fingerprint}");

              result.passed = false;
              result.problems.add (
                [
                  // itie.title itie.identifiers itie.matched_title_id itie.matched_title
                  problemFingerprint:rule_fingerprint,
                  problemSequence:result.probcount++,
                  problemDescription:itie.message,
                  problemCode:'InconsistentTitleIdentifierException',
                  submittedTitle:the_kbart.publication_title,
                  submittedIdentifiers:identifiers,
                  matchedTitle:itie.matched_title_id
                ])
            }
          }
        }
        else {
          log.warn("${packageName}:${preflight_counter++}] No identifiers. Map:${ingest_cfg.identifierMap}, print_identifier:${the_kbart.print_identifier} online_identifier:${the_kbart.online_identifier}");
        }
      }
    }

    log.debug("preflight returning ${result.passed}");
    result
  }

  /**
   *  Add mapped custom properties to an object. Extensibility mechanism.
   *  Look through the properties passed for any that start with the given prefix. If any
   *  matches are found, add the remaining property name to obj as custom properties.
   *  Sometimes, a column is mapped into a custprop widget -> tipp.custprops.widget. This
   *  handles that case.
   *  @See KBComponent.additionalProperties
   */
  def addCustprops(obj, props, prefix) {
    boolean changed = false
    props.each { k,v ->
      if ( k.toString().startsWith(prefix) ) {
        log.debug("Got custprop match : ${k} = ${v}");
        def trimmed_name = m.name.substring(prefix.length());
        obj.appendToAdditionalProperty(trimmed_name, m.value);
        changed=true
      }
    }

    if ( changed ) {
      obj.save(flush:true, failOnError:true);
    }

    return;
  }

  /**
   *  Add mapped custom properties to an object. Extensibility mechanism.
   *  Look through any unmapped properties that start with the given prefix. If any
   *  matches are found, add the remaining property name to obj as custom properties.
   *  Sometimes, a column is mapped into a custprop widget -> tipp.custprops.widget. This
   *  handles that case.
   *  @See KBComponent.additionalProperties
   */
  def addUnmappedCustprops(obj, unmappedprops, prefix) {
    boolean changed = false
    unmappedprops.each { m ->
      if ( m.name.toString().startsWith(prefix) ) {
        log.debug("Got custprop match : ${m.name} = ${m.value}");
        def trimmed_name = m.name.substring(prefix.length());
        obj.appendToAdditionalProperty(trimmed_name, m.value);
        changed=true
      }
    }

    if ( changed ) {
      obj.save(flush:true, failOnError:true);
    }

    return;
  }
}
