package org.gokb

import org.gokb.cred.*

import com.k_int.ClassUtils
import grails.converters.JSON
import grails.gorm.transactions.*
import groovy.transform.Synchronized

import java.time.LocalDateTime
import java.time.ZoneId

class TitleLookupService {

  def grailsApplication
  def componentLookupService
  def genericOIDService
  def reviewRequestService

  @Transactional
  private Map class_one_match(List ids, ti_class, boolean fullsync = false) {

    // Get the class 1 identifier namespaces.
    Set<String> class_one_ids = grailsApplication.config.getProperty('identifiers.class_ones', Set<String>)
    List xcheck = grailsApplication.config.getProperty('identifiers.cross_checks', List)
    RefdataValue ci_deleted = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_DELETED)
    RefdataValue status_deleted = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)

    // Return the list of class 1 identifiers we have found or created, as well as the
    // list of matches
    Map result = [
      "class_one": false,
      "ids": [],
      "invalid_ids": [],
      "matches": [] as Set,
      "other_matches": [] as Set,
      "other_types": [] as Set,
      "x_check_matches": [] as Set,
      "other_identifiers": [] as Set
    ]

    // Go through each of the class_one_ids and look for a match.
    ids.each { id_inc ->
      // We only treat a component as a match if the matching Identifer
      Identifier the_id
      Map id_def = [:]

      if (id_inc instanceof Map) {
        def id_ns = id_inc.type ?: (id_inc.namespace ?: null)

        id_def.value = id_inc.value

        if (id_ns instanceof String) {
          log.debug("Default namespace handling for ${id_ns}..")
          id_def.type = id_ns
        } else if (id_ns) {
          log.debug("Handling namespace def ${id_ns}")
          id_def.type = IdentifierNamespace.get(id_ns).value
        }
      } else {
        the_id = Identifier.get(id_inc)

        id_def.value = the_id.value
        id_def.type = the_id.namespace.value
      }

      // is a class 1 identifier.
      if (id_def.type && id_def.value) {
        log.debug("Attempt match using component ${id_def}");

        // id_def is map with keys 'type' and 'value'
        if (!the_id) {
          the_id = componentLookupService.lookupOrCreateCanonicalIdentifier(id_def.type, id_def.value)
        }

        if (the_id) {
          // Add the id.
          result['ids'] << the_id
          String match_type = "other_matches"

          // Flag class one is present.
          if (class_one_ids.contains(id_def.type)) {
            match_type = "matches"
            result['class_one'] = true
          }

          // Flag for title match
          boolean title_match = false

          // If we find an ID then lookup the components.
          Set<KBComponent> comp = getTitlesForIdentifier(the_id, ti_class)

          log.debug("Scanning ${comp.size()} components attached to identifier")
          comp.each { KBComponent c ->

            // Ensure we're not looking at a Hibernate Proxy class representation of the class
            KBComponent dproxied = ClassUtils.deproxy(c);

            if (fullsync || dproxied.status != status_deleted) {
              // Only add if it's a title.
              if (ti_class.isInstance(dproxied)) {
                title_match = true
                TitleInstance the_ti = (dproxied as TitleInstance)
                // Don't add repeated matches
                if (result[match_type].contains(the_ti)) {
                  log.debug("Not adding duplicate")
                } else {
                  log.debug("Adding ${the_ti} (title_match = ${title_match})")
                  result[match_type] << the_ti
                }
              } else {
                log.debug("ID doesn't point at an item of the correct type, skipping")
              }
            } else {
              log.debug("Ignoring deleted item ..")
            }
          }

          // Did the ID yield a Title match?
          log.debug("After class one matches (${id_def.type}:${id_def.value}, ${the_id.id}, title_match=${title_match}")

          if (!title_match) {

            // We should see if the current ID namespace should be cross checked with another.
            Set other_ns = new HashSet<String>()

            for (int i = 0; i < xcheck.size() && !(other_ns); i++) {
              Set<String> test = xcheck[i]

              if (test.contains(id_def.type)) {
                // Create the set then remove the matched instance to test teh remaining ones.
                other_ns = new HashSet<String>(test)

                // Remove the current namespace.
                other_ns.remove(id_def.type)
                log.debug("Cross checking for ${id_def.type} in ${other_ns.join(", ")}")

                Identifier xc_id = null

                for (int j = 0; j < other_ns.size() && !(xc_id); j++) {
                  String ns = other_ns[j]
                  IdentifierNamespace namespace = IdentifierNamespace.findByValue(ns)

                  if (namespace) {
                    // Lookup the identifier namespace.
                    xc_id = Identifier.findByNamespaceAndValue(namespace, id_def.value)
                    log.debug("Looking up ${ns}:${id_def.value} returned Identifier ${xc_id}")

                    comp = xc_id?.identifiedComponents

                    comp?.each { c ->

                      // Ensure we're not looking at a Hibernate Proxy class representation of the class
                      KBComponent dproxied = ClassUtils.deproxy(c)

                      // Only add if it's a title.
                      if (dproxied.class.name == ti_class.name && dproxied.status != status_deleted) {

                        log.debug("Found ${id_def.value} in ${ns} namespace.")

                        // Save details here so we can raise a review request, only if a single title was matched.
                        result['x_check_matches'] << [
                          "suppliedNS": id_def.type,
                          "foundNS"   : ns,
                          "value"     : id_def.value
                        ]

                        TitleInstance the_ti = (dproxied as TitleInstance)
                        List ci_active = ComponentIdentifier.executeQuery('''from ComponentIdentifier as c
                                                                                where component = :ti
                                                                                and identifier = :xcid
                                                                                and status != :sa''',
                                                                                [ti: the_ti, xcid: xc_id, sa: ci_deleted])

                        // Don't add repeated matches
                        if (result['matches'].contains(the_ti)) {
                          log.debug("Title already in list of matched instances");
                        } else if (ci_active.size() == 0) {
                          log.debug("Matched component link has status 'Deleted'")
                        } else {
                          result['matches'] << the_ti
                          log.debug("Adding cross check title to matches (Now ${result['matches'].size()} items)");
                        }
                      } else if (dproxied.class.name != 'org.gokb.cred.TitleInstancePackagePlatform') {
                        log.debug("Found other linked component type: ${c} (${dproxied.class.name})")

                        if (result['other_types'].contains(c)) {
                          log.debug("Component already in list of matched instances")
                        } else {
                          result['other_types'].add(c)
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
          result['invalid_ids'] << id_def
        }
      } else if (id_def.type?.toLowerCase() != 'originediturl') {
        log.debug("Skipping problem ID ${id_def}");
        the_id = componentLookupService.lookupOrCreateCanonicalIdentifier(id_def.type, id_def.value)
        result['other_identifiers'] << the_id
      }
    }

    log.debug("At end of class_one_match, result['matches'].size == ${result['matches'].size()}");

    result
  }

  public Map find(String title, String publisher, List identifiers, String newTitleClassName) {
    Map result = [
      to_create: false,
      matches: [],
      conflicts: [],
      invalid: []
    ]
    TitleInstance the_title = null
    Class ti_class = Class.forName(newTitleClassName)
    RefdataValue status_active = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_ACTIVE)
    Set<String> class_one_ids = grailsApplication.config.getProperty('identifiers.class_ones', Set<String>)

    // Lookup any class 1 identifier matches
    Map results = class_one_match(identifiers, ti_class)

    if (results.invalid_ids) {
      result.invalid = results.invalid_ids
    }

    // The matches.
    List<KBComponent> matches = results['matches'] as List

    switch (matches.size()) {
      case 0:
        // No match behaviour.
        log.debug("Title class one identifier lookup yielded no matches.")


        // Check for presence of class one ID
        if (results['class_one']) {
          result.to_create = true
        } else {

          // No class 1s supplied we should try and find a match on the title string.
          if (results['other_matches'].size() > 0) {
            if (results['other_matches'].size() == 1) {
              log.debug("Matched item by secondary ID ..")
              the_title = results['other_matches'][0]
              result.matches.add([
                object: the_title,
                conflicts: [],
                warnings: ['secondary']
              ])
            } else if (results['other_matches'].size() > 1) {
              log.debug("Multiple matches by secondary ID!")
              TitleInstance string_matched = attemptComponentMatch([title: title], newTitleClassName)

              if (string_matched && results.other_matches.contains(string_matched)) {
                result.matches << [object: string_matched, warnings: ['bucket', 'secondary']]
              }
              else {
                results.other_matches.each { om ->
                  Map match_object = [
                    object: om,
                    conflicts: [],
                    warnings: ['secondary', 'other_matches']
                  ]

                  result.matches.add(match_object)
                }
              }
            }
          }
          else {
            log.debug("No class 1 ids supplied. attempting string match")

            // The hash we use is constructed differently based on the type of items.
            // Serial hashes are based soley on the title, Monographs are based currently on title+primary author surname
            String target_hash = null

            // Lookup using title string match only.
            TitleInstance string_matched = attemptComponentMatch([title: title], newTitleClassName)

            if (string_matched) {
              log.debug("TI matched by bucket.")
              Map title_match = [
                object: string_matched,
                warnings: ['bucket']
              ]

              // this seems odd, as the_title is null and therefore has no field 'name'
              /* if (title != the_title.name) {
                title_match.conflicts.add([message: "Found a title with a different primary name!", field: "name", value: title, matched: the_title.name])
              }
              */

              if (!result.matches.contains(string_matched)) {
                result.matches.add(title_match)
              }
            }

            result.to_create = true
          }
        }
        break;
      case 1:
        // Single component match.
        log.debug("Title class one identifier lookup yielded a single match.")
        Map title_match = [
          object: matches[0],
          conflicts: [],
          warnings: []
        ]

        // We should raise a review request here if the match was made by cross checking
        // different identifier namespaces.
        if (results['x_check_matches'].size() == 1 && results['x_check_matches'][0]['suppliedNS'] != 'issnl') {
          Map data = results['x_check_matches'][0]

          title_match.conflicts << [
            message: "Title ${data['suppliedNS']} value ${data['value']} matched an existing ${data['foundNS']}",
            field  : "identifier.namespace",
            value  : "${data['suppliedNS']}:${data['value']}",
            matched: "${data['foundNS']}:${data['value']}"
          ]
        }

        // If one identifier matches, but all other class ones are different, it is probably not a real match.
        List id_mismatches = []
        List active_ids = Identifier.executeQuery('''from Identifier as i
                                                      where exists (
                                                        select 1 from ComponentIdentifier
                                                        where identifier = i
                                                        and component = :title
                                                        and status = :ca
                                                      )''',
                                                      [title: matches[0], ca: status_active])

        results['ids'].each { rid ->
          active_ids.each { mid ->
            if (mid.namespace.value in class_one_ids && rid.namespace == mid.namespace && rid.value != mid.value) {
              if (!active_ids.contains(rid)) {
                id_mismatches.add([incoming: rid, matched: mid])
              }
            }
          }
        }


        // Take whatever we can get if what we have is an unknown title
        if (title?.startsWith("Unknown Title")) {
          // Don't go through title matching if we don't have a real title
          title_match.warnings.add('title_ignored')
        } else {
          if (matches[0].name.startsWith("Unknown Title")) {
            // If we have an unknown title in the db, and a real title, then take that
            // in preference
            log.debug("Found new Title ${metadata.title} for previously unknown title ${matches[0]} (${matches[0].name})")
            title_match.warnings.add('title_placeholder')
          } else {
            if (!title || matches[0].name.equals(title) || matches[0].normname?.equals(KBComponent.generateNormname(title))) {
              log.debug("Matched title has a similar name.")
            } else {
              title_match.warnings << [
                message: "Ingest name differs from that of the matched title!",
                field  : "name",
                value  : title,
                matched: matches[0].name
              ]

              if (id_mismatches.size() > 0) {
                result.to_create = true
              }
            }

            if (id_mismatches.size() > 0) {
              id_mismatches.each {
                title_match.conflicts << [
                  message: "Value ${it.incoming.value} for namespace ${it.incoming.namespace.value} conflicts with existing value ${it.matched.value}",
                  field  : "identifier.value",
                  namespace: it.incoming.namespace.value,
                  value  : it.incoming.value,
                  matched: it.matched.value
                ]
              }
            }
          }
        }
        result.matches << title_match
        break;

      default:
        // Multiple matches.
        log.debug("Title class one identifier lookup yielded ${matches.size()} matches - ${matches}.")
        Set all_matched = new HashSet<TitleInstance>()
        List partial = []
        RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

        matches.each { mti ->
          boolean full_match = true
          List id_conflicts = []
          List active_ids = Identifier.executeQuery('''from Identifier as i
                                                        where exists (
                                                          select 1 from ComponentIdentifier
                                                          where identifier = i
                                                          and component = :title
                                                          and status = :ca
                                                        )''',
                                                        [title: mti, ca: status_active])

          results['ids'].each { rid ->
            active_ids.each { mid ->
              if (mid.namespace.value in class_one_ids && rid.namespace == mid.namespace && rid.value != mid.value) {
                if (!active_ids.contains(rid)) {
                  full_match = false
                  id_conflicts.add([
                    message: "Value ${rid.value} for namespace ${rid.namespace.value} conflicts with existing value ${mid.value}",
                    field: "identifier.value",
                    namespace: rid.namespace.value,
                    value: rid.value,
                    matched: mid.value
                  ])
                }
              }
            }
          }

          if (full_match) {
            if (mti.status != status_deleted) {
              all_matched.add(mti)
            } else {
              log.debug("Skipping matched TI with status 'Deleted'!")
            }
          } else if (mti.status != status_deleted) {
            partial.add([
              object: mti,
              conflicts: id_conflicts,
              warnings: ['other_matches']
            ])
          }

        }

        switch (all_matched.size()) {
          case 0:
            log.debug("Multiple matches for a single identifier. No matches for all class ones. Creating new TI!")
            result.to_create = true
            result.matches = partial
            break

          case 1:
            log.debug("One match for all identifiers")
            Map title_match = [
              object: all_matched[0],
              conflicts: [],
              warnings: ['other_matches']
            ]

            if (all_matched[0].normname != KBComponent.generateNormname(title)) {
              title_match.conflicts << [
                message: "Title name differs from matched value ${all_matched[0].name}",
                field: "name",
                value: title,
                matched: all_matched[0].name
              ]
            }

            result.matches << title_match
            break

          default:
            log.debug("Multiple matches for given ingest identifiers. Trying to match by name..")

            Set matched_with_name = new HashSet<TitleInstance>()

            all_matched.each { mti ->
              if (mti.name.equals(title) || mti.normname?.equals(KBComponent.generateNormname(title))) {
                matched_with_name.add(mti)
              }
            }

            if (matched_with_name.size() == 1) {
              log.debug("Only one matched TI (${matched_with_name[0]}) has the same name!")
              result.matches << [
                object: matched_with_name[0],
                conflicts: [],
                warnings: ['other_matches']
              ]
            } else {
              log.debug("Could not match a specific title. Skipping..")

              all_matched.each {
                result.matches << [
                  object: it,
                  conflicts: [],
                  warnings: ['duplicate']
                ]
              }
            }
            break
        }
        break
    }
    result
  }

  private TitleInstance attemptBucketMatch(String title) {
    def t = null;
    if (title && (title.length() > 0)) {
      def nname = GOKbTextUtils.norm2(title);

      def bucket_hash = GOKbTextUtils.generateComponentHash([nname]);

      // def component_hash = GOKbTextUtils.generateComponentHash([nname, componentDiscriminator]);

      t = TitleInstance.findByBucketHash(bucket_hash);
      log.debug("Result of findByBucketHash(\"${bucket_hash}\") for title ${title} : ${t}");
    }

    return t;
  }

  private TitleInstance attemptComponentMatch(def metadata, String className) {
    def t = null;
    def descriminator = null;
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    Class cl = null;

    if (className) {
      cl = Class.forName(className)
    } else {
      cl = Class.forName('org.gokb.cred.TitleInstance')
    }

    if (metadata.title && (metadata.title.length() > 0)) {
      def nname = GOKbTextUtils.norm2(metadata.title);

      if (className == 'org.gokb.cred.BookInstance') {
        descriminator = BookInstance.generateBookDiscriminator(metadata)
      }

      def component_hash = GOKbTextUtils.generateComponentHash([nname, descriminator]);

      if (descriminator) {
        t = cl.findByComponentHashAndStatusNotEqual(component_hash, status_deleted);
      }

      if (!t) {
        t = cl.findByBucketHashAndStatusNotEqual(component_hash, status_deleted);
      }

      log.debug("Result of attempComponentMatch(\"${component_hash}\") for title ${metadata.title} : ${t}");
    }

    return t;
  }

  private TitleInstance singleTIMatch(String title, TitleInstance ti, User user, project = null) {

    log.debug("singleTIMatch");

    String comparable_title = GOKbTextUtils.generateComparableKey(title)

    // The threshold for a good match.
    double threshold = grailsApplication.config.getProperty('cosine.good_threshold', Double, 0.75)

    // Work out the distance between the 2 title strings.
    double distance = GOKbTextUtils.cosineSimilarity(GOKbTextUtils.generateComparableKey(ti.name), comparable_title)

    // Check the distance.
    switch (distance) {

      case 1:

        // Do nothing just continue using the TI.
        log.debug("Exact distance match for TI.")
        break

      case {
        ti.variantNames.find { alt ->
          GOKbTextUtils.cosineSimilarity(GOKbTextUtils.generateComparableKey(alt.variantName), comparable_title) >= threshold
        }
      }:
        // Good match on existing variant titles
        log.debug("Good match for TI on variant.")
        break

      case { it >= threshold }:

        // Good match. Need to add as alternate name.
        log.debug("Good distance match for TI. Add as variant.")
        def added = ti.ensureVariantName(title)
        break

      default:
        // Bad match...
        log.debug("Bad distance match for TI. Add variant and review.")
        def added = ti.ensureVariantName(title)

        // Raise a review request
        if (added) {
          def additionalInfo = [:]
          def linked_component_ids = [ti.id]

          additionalInfo.cstring = linked_component_ids.sort().join('_')
          additionalInfo.vars = [title, ti.name]

          reviewRequestService.raise(
            ti,
            "'${title}' added as a variant of '${ti.name}'.",
            "Match was made on 1st class identifier but title name seems to be very different.",
            user,
            project,
            (additionalInfo as JSON).toString(),
            RefdataCategory.lookup('ReviewRequest.StdDesc', 'Name Mismatch'),
            componentLookupService.findCuratoryGroupOfInterest(ti, user)
          )
        }
        break
    }

    ti
  }

  public List matchClassOneComponentIds(List ids) {
    List result = []

    log.debug("matchClassOneComponentIds(${ids})")

    try {
      // Get the class 1 identifier namespaces.
      Set<String> class_one_ids = grailsApplication.config.getProperty('identifiers.class_ones', Set<String>)
      RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      Map bindvars = [cd: status_deleted]

      int ctr = 0
      List id_list = []

      ids.each { id_def ->
        // Class ones only.
        if (id_def.value && id_def.ns && class_one_ids.contains(id_def.ns)) {
          IdentifierNamespace ns = IdentifierNamespace.findByValue(id_def.ns)
          String normval = Identifier.generateNormname(id_def.value)

          if (ns) {
            List the_id = Identifier.executeQuery('from Identifier as i where i.normname = :val and i.namespace = :ns', [val: normval, ns: ns])

            if (the_id.size() == 1) {
              id_list.add(the_id[0])
            }
            if (the_id.size() > 1) {
              log.error("Found multiple IDs (${the_id}) for ${id_def}!")
            }
          }
        }
      }

      if (ctr > 0) {
        bindvars['il'] = id_list
        result = TitleInstance.executeQuery("select id from TitleInstance as ti where status != :cd and exists (select 1 from ComponentIdentifier as c where c.identifier in (:il) and c.component = ti)", bindvars)
      } else {
        log.warn("No class 1 identifiers(${class_one_ids}) in ${ids}")
      }
    }
    catch (Exception e) {
      log.error("unexpected error attempting to find title by identifiers", e)
    }

    log.debug("Returning Result of matchClassOneComponentIds(${ids}) : ${result}")

    result
  }

  def Object getTitleField(title_id, field_name) {
    def result = TitleInstance.executeQuery("select ti." + field_name + " from TitleInstance as ti where ti.id = :ti", [ti: title_id])
    return result.size() == 1 ? result[0] : null;
  }

  def Object getTitleFieldForIdentifier(ids, field_name) {
    def result = null
    def l = matchClassOneComponentIds(ids)
    if (l && l.size() == 1) {
      result = TitleInstance.executeQuery("select ti." + field_name + " from TitleInstance as ti where ti.id = :ti", [ti: l[0]])[0]
    }
    log.debug("getTitleFieldForIdentifier(${ids},${field_name} : ${result}")
    return result
  }

  // A task will be created to remap a title instance by an update to that title which touches
  // any field that might change the Instance -> Work mapping. We have to wait for that update to
  // complete before processing
  public void remapTitleInstance(oid) {
    try {
      TitleInstance.withNewTransaction {
        log.debug("remapTitleInstance::${oid}")

        TitleInstance domain_object = genericOIDService.resolveOID(oid, true)

        if (domain_object) {
          log.debug("Calling ${domain_object}.remapWork()");
          domain_object.remapWork();
        } else {
          log.debug("Unable to locate domain object for ${oid}")
        }
      }
    }
    catch (Exception e) {
      log.error("Problem in remap work.", e);
    }
  }

  public List getTitlesForIdentifier(identifier, ti_class) {
    log.debug("Get components for ${identifier}")
    Set result = []
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    identifier.activeIdentifiedComponents.each { idc ->
      if (ti_class.isInstance(ClassUtils.deproxy(idc)) && idc.status != status_deleted) {
        result << idc
      }
    }

    result
  }

  public boolean compareIdentifierMaps(ids_one, ids_two) {
    boolean result = true

    ids_one.each { ido ->
      ids_two.each { idt ->
        if (ido.type == idt.type && ido.value != idt.value) {
          result = false
        }
      }
    }
    result
  }

  public String determineTitleClass(titleObj) {
    if (titleObj.type) {
      switch (titleObj.type) {
        case "serial":
        case "Serial":
        case "Journal":
        case "journal":
          return "org.gokb.cred.JournalInstance"
          break;
        case "monograph":
        case "Monograph":
        case "Book":
        case "book":
          return "org.gokb.cred.BookInstance"
          break;
        case "Database":
        case "database":
          return "org.gokb.cred.DatabaseInstance"
          break;
        case "Other":
        case "other":
          return "org.gokb.cred.OtherInstance"
          break;
        default:
          return null
          break;
      }
    } else {
      return null
    }
  }
}
