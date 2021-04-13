package org.gokb

import org.gokb.cred.*

import com.k_int.ClassUtils
import grails.converters.JSON
import grails.gorm.transactions.*
import groovy.transform.Synchronized

import java.time.LocalDateTime
import java.time.ZoneId

@Transactional
class TitleLookupService {

  def grailsApplication
  def componentLookupService
  def genericOIDService
  def reviewRequestService


  @javax.annotation.PostConstruct
  def init() {
    log.debug("Init");
  }

  private Map class_one_match(def ids, ti_class, def fullsync = false) {

    // Get the class 1 identifier namespaces.
    Set<String> class_one_ids = grailsApplication.config.identifiers.class_ones
    def xcheck = grailsApplication.config.identifiers.cross_checks
    RefdataValue status_deleted = RefdataCategory.lookup(Combo.RD_STATUS, Combo.STATUS_DELETED)

    // Return the list of class 1 identifiers we have found or created, as well as the
    // list of matches
    def result = [
        "class_one"        : false,
        "ids"              : [],
        "matches"          : [] as Set,
        "other_matches"    : [] as Set,
        "other_types"      : [] as Set,
        "x_check_matches"  : [] as Set,
        "other_identifiers": [] as Set
    ]

    // Go through each of the class_one_ids and look for a match.
    ids.each { id_inc ->
      // We only treat a component as a match if the matching Identifer

      Identifier the_id = null
      def id_def = [:]

      if (id_inc instanceof Map) {
        def id_ns = id_inc.type ?: (id_inc.namespace ?: null)

        id_def.value = id_inc.value

        if (id_ns instanceof String) {
          log.debug("Default namespace handling for ${id_ns}..")
          id_def.type = id_ns
        }
        else if (id_ns) {
          log.debug("Handling namespace def ${id_ns}")
          id_def.type = IdentifierNamespace.get(id_ns).value
        }
      }
      else {
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

        if (!the_id) {
          log.error("Unable to look up ID")
          throw new RuntimeException("Unable to lookup Identifier for ${id_def}");
        }
        // Add the id.
        result['ids'] << the_id

        def match_type = "other_matches"

        // Flag class one is present.
        if (class_one_ids.contains(id_def.type)) {
          match_type = "matches"
          result['class_one'] = true
        }

        // Flag for title match
        boolean title_match = false

        // If we find an ID then lookup the components.
        Set<KBComponent> comp = getComponentsForIdentifier(the_id)

        log.debug("Scanning ${comp.size()} components attached to identifier");
        comp.each { KBComponent c ->

          // Ensure we're not looking at a Hibernate Proxy class representation of the class
          KBComponent dproxied = ClassUtils.deproxy(c);

          if (!fullsync || dproxied.status != status_deleted) {
            // Only add if it's a title.
            if (ti_class.isInstance(dproxied)) {
              title_match = true
              TitleInstance the_ti = (dproxied as TitleInstance)
              // Don't add repeated matches
              if (result[match_type].contains(the_ti)) {
                log.debug("Not adding duplicate");
              }
              else {
                log.debug("Adding ${the_ti} (title_match = ${title_match})");
                result[match_type] << the_ti
              }
            }
            else {
              log.debug("ID doesn't point at an item of the correct type, skipping");
            }
          }
          else {
            log.debug("Ignoring deleted item ..")
          }
        }

        // Did the ID yield a Title match?
        log.debug("After class one matches (${id_def.type}:${id_def.value}, ${the_id.id}, title_match=${title_match}");

        if (!title_match) {

          // We should see if the current ID namespace should be cross checked with another.
          def other_ns = null
          for (int i = 0; i < xcheck.size() && (!(other_ns)); i++) {
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
                  log.debug("Looking up ${ns}:${id_def.value} returned Identifier ${xc_id}");

                  comp = xc_id?.identifiedComponents

                  comp?.each { KBComponent c ->

                    // Ensure we're not looking at a Hibernate Proxy class representation of the class
                    def dproxied = ClassUtils.deproxy(c);

                    // Only add if it's a title.
                    if (dproxied.class.name == ti_class.name) {

                      log.debug("Found ${id_def.value} in ${ns} namespace.")

                      // Save details here so we can raise a review request, only if a single title was matched.
                      result['x_check_matches'] << [
                          "suppliedNS": id_def.type,
                          "foundNS"   : ns,
                          "value"     : id_def.value
                      ]

                      TitleInstance the_ti = (dproxied as TitleInstance)
                      def combo_active = Combo.executeQuery("from Combo as c where fromComponent = :ti and toComponent = :xcid and status != :sa", [ti: the_ti, xcid: xc_id, sa: status_deleted])

                      // Don't add repeated matches
                      if (result['matches'].contains(the_ti)) {
                        log.debug("Title already in list of matched instances");
                      }
                      else if (combo_active.size() == 0) {
                        log.debug("Matched combo has status 'Deleted'")
                      }
                      else {
                        result['matches'] << the_ti
                        log.debug("Adding cross check title to matches (Now ${result['matches'].size()} items)");
                      }
                    }
                    else if (dproxied.class.name != 'org.gokb.cred.TitleInstancePackagePlatform') {
                      log.debug("Found other linked component type: ${c} (${dproxied.class.name})")

                      if (result['other_types'].contains(c)) {
                        log.debug("Component already in list of matched instances")
                      }
                      else {
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
      else if (id_def.type.toLowerCase() != 'originediturl') {
        log.debug("Skipping problem ID ${id_def}");
        the_id = componentLookupService.lookupOrCreateCanonicalIdentifier(id_def.type, id_def.value)
        result['other_identifiers'] << the_id
      }
    }

    log.debug("At end of class_one_match, result['matches'].size == ${result['matches'].size()}");

    result
  }

  def find(String title,
           def publisher,
           def identifiers,
           def newTitleClassName
  ) {
    def result = [to_create: false, matches: []]
    TitleInstance the_title = null
    Class ti_class = Class.forName(newTitleClassName)

    // Lookup any class 1 identifier matches
    def results = class_one_match(identifiers, ti_class)

    // The matches.
    List<KBComponent> matches = results['matches'] as List

    switch (matches.size()) {
      case 0:
        // No match behaviour.
        log.debug("Title class one identifier lookup yielded no matches.")


        // Check for presence of class one ID
        if (results['class_one']) {
          result.to_create = true
        }
        else {

          // No class 1s supplied we should try and find a match on the title string.
          if (results['other_matches'].size() > 0) {
            if (results['other_matches'].size() == 1) {
              log.debug("Matched item by secondary ID ..")
              the_title = results['other_matches'][0]
              result.matches.add([object: the_title, conflicts: [], warnings: ['secondary']])
            }
            else if (results['other_matches'].size() > 1) {
              log.debug("Multiple matches by secondary ID!")
              results.other_matches.each { om ->
                result.matches.add([object: om, conflicts: [], warnings: ['secondary', 'other_matches']])
              }
            }
          }

          if (!the_title) {
            log.debug("No class 1 ids supplied. attempting string match")

            // The hash we use is constructed differently based on the type of items.
            // Serial hashes are based soley on the title, Monographs are based currently on title+primary author surname
            def target_hash = null;

            // Lookup using title string match only.
            def string_matched = attemptComponentMatch([title: title], newTitleClassName)

            if (string_matched) {
              log.debug("TI matched by bucket.")
              def title_match = [object: the_title, warnings: ['bucket']]

              if (title != the_title.name) {
                title_match.conflicts.add([message: "Found a title with a different primary name!", field: "name", value: title, matched: the_title.name])
              }

              result.matches.add(title_match)
            }

            result.to_create = true
          }
        }
        break;
      case 1:
        // Single component match.
        log.debug("Title class one identifier lookup yielded a single match.")
        def title_match = [object: matches[0], conflicts: [], warnings: []]

        // We should raise a review request here if the match was made by cross checking
        // different identifier namespaces.
        if (results['x_check_matches'].size() == 1 && results['x_check_matches'][0]['suppliedNS'] != 'issnl') {

          def data = results['x_check_matches'][0]

          title_match.conflicts << [
              message: "Title ${data['suppliedNS']} value ${data['value']} matched an existing ${data['foundNS']}",
              field  : "identifier.namespace",
              value  : "${data['suppliedNS']}:${data['value']}",
              matched: "${data['foundNS']}:${data['value']}"
          ]
        }

        // If one identifier matches, but all other class ones are different, it is probably not a real match.

        def id_mismatches = []

        results['ids'].each { rid ->
          matches[0].ids.each { mid ->
            if (rid.namespace == mid.namespace && rid.value != mid.value) {
              if (!matches[0].ids.contains(rid)) {
                id_mismatches.add([incoming: rid, matched: mid])
              }
            }
          }
        }


        // Take whatever we can get if what we have is an unknown title
        if (title.startsWith("Unknown Title")) {
          // Don't go through title matching if we don't have a real title
          title_match.warnings.add('title_ignored')
        }
        else {
          if (matches[0].name.startsWith("Unknown Title")) {
            // If we have an unknown title in the db, and a real title, then take that
            // in preference
            log.debug("Found new Title ${metadata.title} for previously unknown title ${matches[0]} (${matches[0].name})")
            title_match.warnings.add('title_placeholder')
          }
          else {
            if (matches[0].name.equals(title) || matches[0].normname?.equals(KBComponent.generateNormname(title))) {

            }
            else {
              title_match.conflicts << [
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
        def all_matched = []
        def partial = []
        RefdataValue status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')

        matches.each { mti ->

          def full_match = true
          def id_conflicts = []

          results['ids'].each { rid ->
            mti.ids.each { mid ->
              if (rid.namespace == mid.namespace && rid.value != mid.value) {
                if (!mti.ids.contains(rid)) {
                  full_match = false
                  id_conflicts.add([message: "Value ${rid.value} for namespace ${rid.namespace.value} conflicts with existing value ${mid.value}", field: "identifier.value", value: rid.value, matched: mid.value])
                }
              }
            }
          }

          if (full_match) {
            if (mti.status != status_deleted) {
              all_matched.add(mti)
            }
            else {
              log.debug("Skipping matched TI with status 'Deleted'!")
            }
          }
          else if (mti.status != status_deleted) {
            partial.add([object: mti, conflicts: id_conflicts, warnings: ['other_matches']])
          }

        }

        switch (all_matched.size()) {
          case 0:
            log.debug("Multiple matches for a single identifier. No matches for all class ones. Creating new TI!")
            result.to_create = true
            result.matches = partial
            break;

          case 1:
            log.debug("One match for all identifiers")
            def title_match = [object: all_matched[0], conflicts: [], warnings: ['other_matches']]

            if (!all_matched[0].name.equals(title)) {
              title_match.conflicts << [message: "Title name differs from matched value ${all_matched[0].name}", field: "name", value: title, matched: all_matched[0].name]
            }

            result.matches << title_match
            break;

          default:
            log.debug("Multiple matches for given ingest identifiers. Trying to match by name..")

            def matched_with_name = []

            all_matched.each { mti ->
              if (mti.name.equals(title) || mti.normname?.equals(KBComponent.generateNormname(title))) {
                matched_with_name.add(mti)
              }
            }

            if (matched_with_name.size() == 1) {
              log.debug("Only one matched TI (${matched_with_name[0]}) has the same name!")
              result.matches << [object: matched_with_name[0], conflicts: [], warnings: ['other_matches']]
            }
            else {
              log.debug("Could not match a specific title. Skipping..")

              all_matched.each {
                result.matches << [object: it, conflicts: [], warnings: ['duplicate']]
              }
            }
            break;
        }
        break;
    }
    result
  }


  /**
   * @param title
   * @param publisher_name
   * @param identifiers : map [ [ type: 'idtype', value:'idvalue' ], [ type:'idtype', value:'idvalue' ] ]
   */

  def findOrCreate(String title,
                   String publisher_name,
                   def identifiers,
                   def user = null,
                   def project = null,
                   def newTitleClassName = 'org.gokb.cred.JournalInstance',
                   def uuid = null,
                   def fullsync = false) {
    return findOrCreateTitle([title: title, publisher_name: publisher_name, identifiers: identifiers, uuid: uuid, fullsync: fullsync], user, project, newTitleClassName, fullsync)
  }

  private final findLock = new Object()

  @Synchronized("findLock")
  private def findOrCreateTitle(Map metadata,
                                def user = null,
                                def project = null,
                                def newTitleClassName = 'org.gokb.cred.JournalInstance',
                                def fullsync = false
  ) {

    // The TitleInstance
    TitleInstance the_title = null
    Class ti_class = Class.forName(newTitleClassName)
    def rr_map = [:]
    def title_created = false

    if (metadata.title == null) {
      log.error("Request to look up title with no title");
      return null
    }

    if (metadata.uuid) {
      the_title = TitleInstance.findByUuid(metadata.uuid)

      if (the_title) {
        log.debug("Found TitleInstance by Uuid, skipping Identifier matching ..")
        the_title = singleTIMatch(metadata.title, the_title, user, project)
        return the_title
      }
    }

    // Lookup any class 1 identifier matches
    def results = class_one_match(metadata.identifiers, ti_class, fullsync)

    // The matches.
    List<KBComponent> matches = results['matches'] as List

    switch (matches.size()) {
      case 0:
        // No match behaviour.
        log.debug("Title class one identifier lookup yielded no matches.")


        // Check for presence of class one ID
        if (results['class_one']) {
          log.debug("One or more class 1 IDs supplied so must be a new TI.")

          // Create the new TI.
          if (newTitleClassName == null) {
            the_title = new TitleInstance(name: metadata.title, ids: [])
            the_title.normname = KBComponent.generateNormname(metadata.title);
          }
          else {
            the_title = ti_class.newInstance()
            the_title.name = metadata.title
            the_title.normname = KBComponent.generateNormname(metadata.title);
            // the_title.editStatus =
            the_title.ids = []
          }

          if (metadata.uuid && metadata.uuid.trim().size() > 0) {
            the_title.uuid = metadata.uuid
          }
          title_created = true

        }
        else {

          // No class 1s supplied we should try and find a match on the title string.
          if (results['other_matches'].size() > 0) {
            if (results['other_matches'].size() == 1) {
              log.debug("Matched item by secondary ID ..")
              the_title = results['other_matches'][0]
            }
            else if (results['other_matches'].size() > 1) {
              log.debug("Multiple matches by secondary ID!")
            }
          }

          def string_match = null

          if (!the_title) {
            log.debug("No class 1 ids supplied. attempting string match")

            // The hash we use is constructed differently based on the type of items.
            // Serial hashes are based soley on the title, Monographs are based currently on title+primary author surname
            def target_hash = null;

            // Lookup using title string match only.
            string_match = attemptComponentMatch(metadata, newTitleClassName)

            if (results['other_identifiers']?.size() > 0) {
              log.debug("Skipping name match")
            }
            else {
              the_title = string_match
            }
          }

          if (the_title) {
            log.debug("TI ${the_title} matched by secondary ID.")

            if (metadata.title != the_title.name) {
              log.debug("bucket match but \"${metadata.title}\" != \"${the_title.name}\" so add as a variant");

              // Add the variant.
              def added = the_title.addVariantTitle(metadata.title)

              // Raise a review request

              if (added) {
                def additionalInfo = [:]
                def combo_ids = [the_title.id]

                additionalInfo.otherComponents = []

                results['other_matches'].each { tlm ->
                  additionalInfo.otherComponents.add([oid: "${tlm.logEntityId}", name: "${tlm.name ?: tlm.displayName}"])
                  combo_ids.add(tlm.id)
                }

                additionalInfo.cstring = combo_ids.sort().join('_')
                additionalInfo.vars = [metadata.title, the_title.name]

                rr_map = [
                  review: "'${metadata.title}' added as a variant of '${the_title.name}'.",
                  cause: "Title was matched via secondary id, but had a different name.",
                  additionalInfo: additionalInfo,
                  type: RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Name Mismatch')
                ]
              }

              if (the_title.validate()) {
                the_title = the_title.merge(flush: true, failOnError: true);
              }
            }

          }
          else {
            log.debug("No TI could be matched by name. New TI, flag for review.")

            // Could not match on title either.
            // Create a new TI but attach a Review request to it.

            if (newTitleClassName == null) {
              the_title = new TitleInstance(name: metadata.title, normname: KBComponent.generateNormname(metadata.title), ids: [])
            }
            else {
              the_title = ti_class.newInstance()
              the_title.name = metadata.title
              the_title.normname = KBComponent.generateNormname(metadata.title)
              the_title.ids = []
            }

            if (metadata.uuid && metadata.uuid.trim().size() > 0) {
              the_title.uuid = metadata.uuid
            }

            title_created = true

            if (string_match) {
              def additionalInfo = [:]
              def combo_ids = [the_title.id]

              additionalInfo.otherComponents = []

              matches.each { tlm ->
                additionalInfo.otherComponents.add([oid: "${tlm.logEntityId}", name: "${tlm.name ?: tlm.displayName}"])
                combo_ids.add(tlm.id)
              }

              additionalInfo.cstring = combo_ids.sort().join('_')

              rr_map = [
                review:  "New TI created.",
                cause:  "No matched components via IDs, but a title with a similar name already exists.",
                additionalInfo: additionalInfo,
                type: RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Name Similarity')
              ]
            }
          }
        }
        break;
      case 1:
        // Single component match.
        log.debug("Title class one identifier lookup yielded a single match.")

        // We should raise a review request here if the match was made by cross checking
        // different identifier namespaces.
        if (results['x_check_matches'].size() == 1 && results['x_check_matches'][0]['suppliedNS'] != 'issnl') {

          def data = results['x_check_matches'][0]

          def additionalInfo = [:]

          additionalInfo.vars = [data.suppliedNS, data.foundNS]
          additionalInfo.mismatches = ["${data.suppliedNS}": data.value]

          rr_map = [
            review:  "Identifier type mismatch.",
            cause:  "Ingest file ${data['suppliedNS']} matched an existing ${data['foundNS']}.",
            additionalInfo: additionalInfo,
            type: RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Namespace Mismatch')
          ]
        }

        // If one identifier matches, but all other class ones are different, it is probably not a real match.

        def id_mismatches = []
        def id_matches = []

        results['ids'].each { rid ->
          matches[0].ids?.each { mid ->
            if (rid.namespace == mid.namespace && rid.value != mid.value) {
              if (!matches[0].ids.contains(rid)) {
                id_mismatches.add(rid)
              }
              else {
                id_matches.add(rid)
              }
            }
          }
        }


        // Take whatever we can get if what we have is an unknown title
        if (metadata.title.startsWith("Unknown Title") || metadata.status == "Expected") {
          // Don't go through title matching if we don't have a real title
          the_title = matches[0]
        }
        else {
          if (matches[0].name.startsWith("Unknown Title") || metadata.status == "Expected") {
            // If we have an unknown title in the db, and a real title, then take that
            // in preference
            log.debug("Found new Title ${metadata.title} for previously unknown title ${matches[0]} (${matches[0].name})")
            the_title = matches[0]
            the_title.name = metadata.title
            the_title.status = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Current')
          }
          else {
            if (matches[0].name.equals(metadata.title) || matches[0].normname?.equals(KBComponent.generateNormname(metadata.title))) {
              // Perfect match - do nothing
              the_title = matches[0]

              if (id_mismatches.size() > 0) {

                def id_mm = []

                id_mismatches.each { mId ->
                  def id_map = [:]
                  id_map[mId.namespace?.value ?: "ns"] = mId.value

                  id_mm.add(id_map)
                }

                def id_pm = []

                id_matches.each { mId ->
                  def id_map = [:]
                  id_map[mId.namespace?.value ?: "ns"] = mId.value

                  id_pm.add(id_map)
                }

                def additionalInfo = [:]

                additionalInfo.cstring = the_title.id.toString()
                additionalInfo.matches = id_pm
                additionalInfo.mismatches = id_mm
                additionalInfo.vars = [the_title.name, id_mm]

                rr_map = [
                  review: "Identifier mismatch",
                  cause: "Title ${the_title} matched, but ingest identifiers ${id_mm} differ from existing ones in the same namespaces.",
                  additionalInfo: additionalInfo,
                  type: RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Minor Identifier Mismatch')
                ]
              }
            }
            else {
              if (id_mismatches.size() > 0) {
                // Another class one identifier of the matched title is different. This looks like a new title.

                if (newTitleClassName == null) {
                  the_title = new TitleInstance(name: metadata.title, normname: KBComponent.generateNormname(metadata.title), ids: [])
                }
                else {
                  the_title = ti_class.newInstance()
                  the_title.name = metadata.title
                  the_title.normname = KBComponent.generateNormname(metadata.title)
                  the_title.ids = []
                }

                if (metadata.uuid && metadata.uuid.trim().size() > 0) {
                  the_title.uuid = metadata.uuid
                }

                title_created = true

                def additionalInfo = [:]
                def combo_ids = [the_title.id]
                def id_mm = []

                id_mismatches.each { mId ->
                  def id_map = [:]
                  id_map[mId.namespace?.value ?: "ns"] = mId.value

                  id_mm.add(id_map)
                }

                def id_pm = []

                id_matches.each { mId ->
                  def id_map = [:]
                  id_map[mId.namespace?.value ?: "ns"] = mId.value

                  id_pm.add(id_map)
                }

                additionalInfo.otherComponents = []

                matches.each { tlm ->
                  additionalInfo.otherComponents.add([oid: "${tlm.logEntityId}", name: "${tlm.name ?: tlm.displayName}"])
                  combo_ids.add(tlm.id)
                }

                additionalInfo.cstring = combo_ids.sort().join('_')
                additionalInfo.matches = id_pm
                additionalInfo.mismatches = id_mm
                additionalInfo.vars = [matches[0].id, '(' + matches[0].name + ')']

                rr_map = [
                  review: "New TI created.",
                  cause: "TitleInstance ${matches[0].id} ${matches[0].name ? '(' + matches[0].name + ')' : ''} was matched on one identifier, but at least one other ingest identifier differs from existing ones in the same namespace.",
                  additionalInfo: additionalInfo,
                  type: RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Major Identifier Mismatch')
                ]
              }
              else {
                // Now we can examine the text of the title.
                the_title = singleTIMatch(metadata.title, matches[0], user, project)
              }
            }
          }
        }
        break;

      default:
        // Multiple matches.
        log.debug("Title class one identifier lookup yielded ${matches.size()} matches - ${matches}.")
        def all_matched = []
        RefdataValue status_deleted = RefdataCategory.lookupOrCreate('KBComponent.Status', 'Deleted')

        matches.each { mti ->

          def full_match = true

          results['ids'].each { rid ->
            mti.ids.each { mid ->
              if (rid.namespace == mid.namespace && rid.value != mid.value) {
                if (!mti.ids.contains(rid)) {
                  full_match = false
                }
              }
            }
          }

          if (full_match) {
            if (mti.status != status_deleted) {
              all_matched.add(mti)
            }
            else {
              log.debug("Skipping matched TI with status 'Deleted'!")
            }
          }

        }

        switch (all_matched.size()) {
          case 0:
            log.debug("Multiple matches for a single identifier. No matches for all class ones. Creating new TI!")

            if (newTitleClassName == null) {
              the_title = new TitleInstance(name: metadata.title, normname: KBComponent.generateNormname(metadata.title), ids: [])
            }
            else {
              the_title = ti_class.newInstance()
              the_title.name = metadata.title
              the_title.normname = KBComponent.generateNormname(metadata.title)
              the_title.ids = []
            }

            if (metadata.uuid && metadata.uuid.trim().size() > 0) {
              the_title.uuid = metadata.uuid
            }

            title_created = true

            def additionalInfo = [:]
            def combo_ids = [the_title]

            additionalInfo.otherComponents = []

            matches.each { tlm ->
              additionalInfo.otherComponents.add([oid: "${tlm.logEntityId}", name: "${tlm.name ?: tlm.displayName}"])
              combo_ids.add(tlm.id)
            }

            additionalInfo.cstring = combo_ids.sort().join('_')

            rr_map = [
              review: "New TI created.",
              cause: "Multiple TitleInstances were matched on one identifier, but none matched for all given IDs.",
              additionalInfo: additionalInfo,
              type: RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Multiple Matches')
            ]

            break;

          case 1:
            log.debug("One match for all identifiers")
            the_title = all_matched[0]

            if (!the_title.name.equals(metadata.title)) {
              the_title.ensureVariantName(metadata.title)
            }
            break;

          default:
            log.debug("Multiple matches for given ingest identifiers. Trying to match by name..")

            def matched_with_name = []

            all_matched.each { mti ->
              if (mti.name.equals(metadata.title) || mti.normname?.equals(KBComponent.generateNormname(metadata.title))) {
                matched_with_name.add(mti)
              }
            }

            if (matched_with_name.size() == 1) {
              log.debug("Only one matched TI (${matched_with_name[0]}) has the same name!")
              the_title = matched_with_name[0]
            }
            else {
              log.debug("Could not match a specific title. Selection needs review")
              def matched_sorted = matched_with_name?.size() > 0 ? matched_with_name.sort { it.id } : all_matched.sort { it.id }
              the_title = matched_sorted[0]
              matched_sorted.remove(0)

              def additionalInfo = [:]
              def combo_ids = [the_title.id]

              additionalInfo.otherComponents = []

              matched_sorted.each { tlm ->
                additionalInfo.otherComponents.add([oid: "${tlm.logEntityId}", name: "${tlm.name ?: tlm.displayName}"])
                combo_ids.add(tlm.id)
              }

              additionalInfo.cstring = combo_ids.sort().join('_')

              rr_map = [
                review: "Check titles for duplicates.",
                cause: "Multiple titles were matched on all identifiers.",
                additionalInfo: additionalInfo,
                type: RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Ambiguous Matches')
              ]

            }
            break;
        }
        break;
    }

    // If we have a title then lets set the publisher and ids...
    if (the_title) {

      // Make sure we're all saved before looking up the publisher
      if (the_title.validate()) {

        // addIdentifiers(results.ids, the_title)

        // addPublisher(metadata.publisher_name, the_title)

        if (the_title.name.startsWith("Unknown Title")) {
          the_title.status = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, 'Expected')
        }

        log.debug("${the_title.ids}")

        if (title_created) {
          the_title = the_title.save(flush: true)
        }
        else {
          the_title = the_title.merge(flush: true)
        }

        if (rr_map) {
          log.info("New RR for title ${the_title}")

          reviewRequestService.raise(
            the_title,
            rr_map.review,
            rr_map.cause,
            user,
            project,
            (rr_map.additionalInfo as JSON).toString(),
            rr_map.type
          )
        }

        if (results.other_types.size() > 0) {

          def additionalInfo = [:]
          def combo_ids = [the_title.id]

          additionalInfo.otherComponents = []

          results.other_types.each { tlm ->
            additionalInfo.otherComponents.add([oid: "${tlm.logEntityId}", name: "${tlm.name ?: tlm.displayName}"])
            combo_ids.add(tlm.id)
          }

          additionalInfo.cstring = combo_ids.sort().join('_')

          reviewRequestService.raise(
              the_title,
              "Identifier match.",
              "A provided identifier matched an existing component of another type!",
              user,
              project,
              (additionalInfo as JSON).toString(),
              RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Type Mismatch')
          )
        }
      }
      else {
        log.error("title validation failed for ${the_title}!")
      }
    }

    the_title
  }

  private TitleInstance addPublisher(publisher_name, ti, user = null, project = null) {


    if ((publisher_name != null) &&
        (publisher_name.trim().length() > 0)) {

      log.debug("Add publisher \"${publisher_name}\"")
      Org publisher = Org.findByName(publisher_name)
      def norm_pub_name = Org.generateNormname(publisher_name);
      def status_deleted = RefdataCategory.lookup("KBComponent.Status", "Deleted")

      if (!publisher) {
        // Lookup using norm name.

        log.debug("Using normname \"${norm_pub_name}\" for lookup")
        publisher = Org.findByNormname(norm_pub_name)
      }

      if (!publisher || publisher.status == status_deleted) {
        def variant_normname = GOKbTextUtils.normaliseString(publisher_name)
        def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status != ?", [variant_normname, status_deleted]);
        if (candidate_orgs.size() == 1) {
          publisher = candidate_orgs[0]
        }
        else {
          log.error("Unable to match unique pub");
        }
      }

      // Found a publisher.
      if (publisher) {
        log.debug("Found publisher ${publisher}");
        def orgs = ti.getPublisher()

        log.debug("Check for dupes in ${orgs}")

        // Has the publisher ever existed in the list against this title.
        if (!orgs.contains(publisher)) {

          // First publisher added?
          boolean not_first = orgs.size() > 0

          // Added a publisher?
          ti.publisher.add(publisher)
        }
      }
    }

    ti
  }

  public TitleInstance addPublisherHistory(TitleInstance ti, publishers) {
    if (publishers && ti) {
      Org.withNewSession {
        log.debug("Handling publisher history ..")

        def publisher_combos = []
        publisher_combos.addAll(ti.getCombosByPropertyName('publisher'))
        String propName = ti.isComboReverse('publisher') ? 'fromComponent' : 'toComponent'
        String tiPropName = ti.isComboReverse('publisher') ? 'toComponent' : 'fromComponent'

        // Go through each Org.
        for (def pub_to_add : publishers) {

          Org publisher = null
          // Lookup the publisher.
          if (pub_to_add.uuid) {
            publisher = Org.findByUuid(pub_to_add.uuid)
          }

          def norm_pub_name = KBComponent.generateNormname(pub_to_add.name)
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

          if (!publisher) {
            publisher = Org.findByNormname(norm_pub_name)
          }

          if (!publisher || publisher.status == status_deleted) {
            def variant_normname = GOKbTextUtils.normaliseString(pub_to_add.name)
            def candidate_orgs = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = ? and o.status <> ?", [variant_normname, status_deleted]);

            if (candidate_orgs.size() == 1) {
              publisher = candidate_orgs[0]
            }
          }

          if (publisher) {

            LocalDateTime parsedStart = GOKbTextUtils.completeDateString(pub_to_add.startDate)
            LocalDateTime parsedEnd = GOKbTextUtils.completeDateString(pub_to_add.endDate, false)
            Date pub_add_sd = parsedStart ? Date.from(parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null
            Date pub_add_ed = parsedEnd ? Date.from(parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null

            boolean found = false
            for (int i = 0; !found && i < publisher_combos.size(); i++) {
              Combo pc = publisher_combos[i]
              def idMatch = pc."${propName}".id == publisher.id

              if (idMatch) {
                if (pub_add_sd && pc.startDate && pub_add_sd != pc.startDate) {
                }
                else if (pub_add_ed && pc.endDate && pub_add_ed != pc.endDate) {
                }
                else {
                  found = true
                }
              }


            }

            // Only add if we havn't found anything.
            if (!found) {

              log.debug("Adding new combo for publisher ${publisher} (${propName}) to title ${ti} (${tiPropName})")

              RefdataValue type = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, ti.getComboTypeValue('publisher'))

              def combo = null

              if (propName == "toComponent") {
                combo = new Combo(
                    type: (type),
                    status: pub_to_add.status ? RefdataCategory.lookupOrCreate(Combo.RD_STATUS, pub_to_add.status) : DomainClassExtender.getComboStatusActive(),
                    startDate: pub_add_sd,
                    endDate: pub_add_ed,
                    toComponent: publisher,
                    fromComponent: ti
                )
              }
              else {
                combo = new Combo(
                    type: (type),
                    status: pub_to_add.status ? RefdataCategory.lookupOrCreate(Combo.RD_STATUS, pub_to_add.status) : DomainClassExtender.getComboStatusActive(),
                    startDate: pub_add_sd,
                    endDate: pub_add_ed,
                    fromComponent: publisher,
                    toComponent: ti
                )
              }

              if (combo) {
                combo.save(flush: true, failOnError: true)

                // Add the combo to our list to avoid adding duplicates.
                publisher_combos.add(combo)

                log.debug "Added publisher ${publisher.name} for '${ti.name}'" +
                    (combo.startDate ? ' from ' + combo.startDate : '') +
                    (combo.endDate ? ' to ' + combo.endDate : '')
              }
              else {
                log.error("Could not create publisher Combo..")
              }

            }
            else {
              log.debug "Publisher ${publisher.name} already set against '${ti.name}'"
            }

          }
          else {
            log.debug "Could not find org name: ${pub_to_add.name}, with normname: ${norm_pub_name}"
          }
        }
      }
    }
    ti
  }

  private TitleInstance addIdentifiers(ids, ti) {
    ids.each { new_id ->

      def existing_combo = Combo.executeQuery("from Combo where fromComponent = ? and toComponent = ?", [ti, new_id])
      if (existing_combo.size() == 0) {
        ti.ids.add(new_id)
      }
      else {
        log.debug("Not adding duplicate ID ${new_id}..")
      }
    }
    ti.save(flush: true)
    ti
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
    }
    else {
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
    double threshold = grailsApplication.config.cosine.good_threshold

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
          reviewRequestService.raise(
              ti,
              "'${title}' added as a variant of '${ti.name}'.",
              "Match was made on 1st class identifier but title name seems to be very different.",
              user, project
          )
        }
        break
    }

    ti
  }


  /**
   * @param ids should be a list of maps containing at least an ns and value key.
   * @return
   */
  public def matchClassOnes(def ids) {
    def result = [] as Set

    // Get the class 1 identifier namespaces.
    Set<String> class_one_ids = grailsApplication.config.identifiers.class_ones

    def start_time = System.currentTimeMillis();

    ids.each { def id_def ->

      log.debug("Consider ${id_def}");

      // Class ones only.
      if (id_def.value &&
          id_def.ns &&
          class_one_ids.contains(id_def.ns)) {

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

        if (identifiers.size() > 4) {
          log.warn("matchClassOne for ${id_def} returned a high number of candidate records. This shouldn't be the case");
        }

        // Examine the identified components.
        identifiers?.each {
          log.debug("Handle ${it?.identifiedComponents.size()} components");
          it?.identifiedComponents.each {
            KBComponent comp = KBComponent.deproxy(it)
            if (comp instanceof TitleInstance) {
              // Add to the set.
              result << (TitleInstance) comp
            }
          }
        }
      }
    }

    def elapsed = System.currentTimeMillis() - start_time;
    if (elapsed > 2000) {
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
      def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      def combo_id_type = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')

      def bindvars = []
      StringWriter sw = new StringWriter()
      sw.write("select DISTINCT c.fromComponent.id from Combo as c where ( ")


      def ctr = 0;
      ids.each { def id_def ->
        // Class ones only.
        if (id_def.value && id_def.ns && class_one_ids.contains(id_def.ns)) {
          def ns = IdentifierNamespace.findByValue(id_def.ns)
          if (ns) {

            def the_id = Identifier.executeQuery('select i from Identifier as i where i.value = ? and i.namespace = ?', [id_def.value, ns])
            if (the_id.size() == 1) {
              if (ctr++) {
                sw.write(" or ");
              }

              sw.write("( c.toComponent = ? )")
              bindvars.add(the_id[0])
            }
            if (the_id.size() > 1) {
              // applicationEventService.publishApplicationEvent('CriticalSystemMessages', 'ERROR', [description:"Multiple Identifiers Matched on lookup id:${id_def}"])
              // event('DataProblem', [code:'MultipleIdentifierMatch', id:id_def], [ fork:false ] )
            }
          }
        }
      }


      if (ctr > 0) {
        sw.write(" ) and c.type=? and c.fromComponent.status != ?");
        bindvars.add(combo_id_type);
        bindvars.add(status_deleted);
        def qry = sw.toString();
        log.debug("Run: ${qry} ${bindvars}");
        result = TitleInstance.executeQuery(qry, bindvars);
      }
      else {
        log.warn("No class 1 identifiers(${class_one_ids}) in ${ids}");
      }
    }
    catch (Exception e) {
      log.error("unexpected error attempting to find title by identifiers", e);
    }

    log.debug("Returning Result of matchClassOneComponentIds(${ids}) : ${result}");
    result
  }

  def Object getTitleField(title_id, field_name) {
    def result = TitleInstance.executeQuery("select ti." + field_name + " from TitleInstance as ti where ti.id=?", title_id);
    return result.size() == 1 ? result[0] : null;
  }

  def Object getTitleFieldForIdentifier(ids, field_name) {
    def result = null
    def l = matchClassOneComponentIds(ids)
    if (l && l.size() == 1) {
      result = TitleInstance.executeQuery("select ti." + field_name + " from TitleInstance as ti where ti.id=?", l[0])[0];
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
        def domain_object = genericOIDService.resolveOID(oid, true)
        if (domain_object) {
          log.debug("Calling ${domain_object}.remapWork()");
          domain_object.remapWork();
        }
        else {
          log.debug("Unable tyo locate domain object for ${oid}");
        }
      }
    }
    catch (Exception e) {
      log.error("Problem in remap work.", e);
    }
  }

  def getComponentsForIdentifier(identifier) {

    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    // was identifier.identifiedComponents
    KBComponent.executeQuery('select DISTINCT c.fromComponent from Combo as c where c.toComponent = :id and c.type.value = :tp and c.fromComponent.status <> :del', [id: identifier, tp: 'KBComponent.Ids', del: status_deleted]);
  }

  def compareIdentifierMaps(ids_one, ids_two) {
    def result = true

    ids_one.each { ido ->
      ids_two.each { idt ->
        if (ido.type == idt.type && ido.value != idt.value) {
          result = false
        }
      }
    }
    result
  }

  def determineTitleClass(titleObj) {
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
    }
    else {
      return null
    }
  }
}
